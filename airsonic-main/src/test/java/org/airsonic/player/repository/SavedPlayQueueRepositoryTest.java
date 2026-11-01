package org.airsonic.player.repository;

import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.sql.Sql;
import liquibase.sqlgenerator.SqlGeneratorFactory;
import liquibase.statement.SqlStatement;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.SavedPlayQueue;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.spring.migrations.PlayQueueFilePositionBackfill;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import javax.sql.DataSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence tests for the saved play queue's explicit ordering (#214): the position column,
 * the Liquibase backfill, and the @OrderColumn mapping that reads and writes it.
 */
@EnableConfigurationProperties({ AirsonicHomeConfig.class })
@SpringBootTest(properties = {
    "spring.jpa.properties.hibernate.session_factory.statement_inspector="
            + "org.airsonic.player.repository.SavedPlayQueueRepositoryTest$RecordingStatementInspector" })
public class SavedPlayQueueRepositoryTest {

    private static final String USER_A = "pq214_user_a";
    private static final String USER_B = "pq214_user_b";

    @Autowired
    private SavedPlayQueueRepository savedPlayQueueRepository;
    @Autowired
    private MediaFileRepository mediaFileRepository;
    @Autowired
    private MusicFolderRepository musicFolderRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private PlayQueueService playQueueService;
    @Autowired
    private EntityManager entityManager;

    @TempDir
    private static Path tempAirsonicDir;

    @TempDir
    private Path tempMusicDir;

    private MusicFolder testFolder;
    private MediaFile song1;
    private MediaFile song2;
    private MediaFile song3;

    @BeforeAll
    public static void setUp() {
        System.setProperty("airsonic.home", tempAirsonicDir.toString());
    }

    @AfterAll
    public static void cleanUp() {
        System.clearProperty("airsonic.home");
    }

    @BeforeEach
    public void createFixtures() {
        userRepository.save(new User(USER_A, "pq214a@example.com"));
        userRepository.save(new User(USER_B, "pq214b@example.com"));
        testFolder = new MusicFolder(tempMusicDir, "pq214folder", Type.MEDIA, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);
        song1 = createSong("pq214-1.mp3");
        song2 = createSong("pq214-2.mp3");
        song3 = createSong("pq214-3.mp3");
    }

    @AfterEach
    public void removeFixtures() {
        // The @Transactional tests roll these deletes back together with the fixtures; the
        // non-transactional backfill test relies on them (deleting a user cascades its
        // play_queue, which cascades play_queue_file).
        jdbcTemplate.update("DELETE FROM users WHERE username LIKE 'pq214%'");
        mediaFileRepository.deleteAll(List.of(song1, song2, song3));
        musicFolderRepository.delete(testFolder);
    }

    private MediaFile createSong(String path) {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFolder(testFolder);
        mediaFile.setPath(path);
        mediaFile.setMediaType(MediaType.MUSIC);
        mediaFile.setStartPosition(MediaFile.NOT_INDEXED);
        mediaFile.setCreated(Instant.now());
        mediaFile.setChanged(Instant.now());
        mediaFile.setLastScanned(Instant.now());
        mediaFile.setChildrenLastUpdated(Instant.now());
        return mediaFileRepository.save(mediaFile);
    }

    private SavedPlayQueue newQueue(String username, List<MediaFile> files) {
        SavedPlayQueue queue = new SavedPlayQueue(username);
        queue.setMediaFiles(new ArrayList<>(files));
        queue.setChanged(Instant.now().truncatedTo(ChronoUnit.MICROS));
        queue.setChangedBy(username);
        return queue;
    }

    private List<Integer> mediaFileIds(SavedPlayQueue queue) {
        return queue.getMediaFiles().stream().map(MediaFile::getId).toList();
    }

    // ---------------------------------------------------------------------------------------
    // Backfill: position is assigned 0..n-1 per queue in ascending id order, per-queue
    // independent, only for rows whose position is NULL.
    // ---------------------------------------------------------------------------------------

    @Test
    public void backfillAssignsPositionsPerQueueFromIdOrder() throws Exception {
        // The live schema has position NOT NULL (post-migration state), so pre-migration rows
        // are simulated by relaxing the constraint for the duration of the test.
        setPositionNullable(true);
        try {
            jdbcTemplate.update("INSERT INTO play_queue (username, changed, changed_by) VALUES (?, ?, ?)",
                    USER_A, java.sql.Timestamp.from(Instant.now()), USER_A);
            jdbcTemplate.update("INSERT INTO play_queue (username, changed, changed_by) VALUES (?, ?, ?)",
                    USER_B, java.sql.Timestamp.from(Instant.now()), USER_B);
            Integer queueA = jdbcTemplate.queryForObject(
                    "SELECT id FROM play_queue WHERE username = ?", Integer.class, USER_A);
            Integer queueB = jdbcTemplate.queryForObject(
                    "SELECT id FROM play_queue WHERE username = ?", Integer.class, USER_B);

            // Interleave the two queues so per-queue numbering has to be independent.
            insertQueueFileRow(queueA, song1.getId());
            insertQueueFileRow(queueB, song2.getId());
            insertQueueFileRow(queueA, song3.getId());
            insertQueueFileRow(queueB, song1.getId());
            insertQueueFileRow(queueA, song2.getId());

            runBackfill();

            List<Integer> positionsA = jdbcTemplate.queryForList(
                    "SELECT position FROM play_queue_file WHERE play_queue_id = ? ORDER BY id", Integer.class, queueA);
            List<Integer> positionsB = jdbcTemplate.queryForList(
                    "SELECT position FROM play_queue_file WHERE play_queue_id = ? ORDER BY id", Integer.class, queueB);

            assertEquals(List.of(0, 1, 2), positionsA, "positions follow id order, contiguous from 0");
            assertEquals(List.of(0, 1), positionsB, "second queue numbered independently");
        } finally {
            jdbcTemplate.update("DELETE FROM play_queue WHERE username LIKE 'pq214%'");
            setPositionNullable(false);
        }
    }

    private void insertQueueFileRow(Integer queueId, Integer mediaFileId) {
        jdbcTemplate.update(
                "INSERT INTO play_queue_file (play_queue_id, media_file_id, position) VALUES (?, ?, NULL)",
                queueId, mediaFileId);
    }

    /** Runs the Liquibase customChange against the test datasource, the way the changeset does. */
    private void runBackfill() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            SqlStatement[] statements = new PlayQueueFilePositionBackfill().generateStatements(database);
            for (SqlStatement statement : statements) {
                for (Sql sql : SqlGeneratorFactory.getInstance().generateSql(statement, database)) {
                    jdbcTemplate.execute(sql.toSql());
                }
            }
        }
    }

    /** Toggles the NOT NULL constraint on position; tries each dialect's syntax in turn. */
    private void setPositionNullable(boolean nullable) {
        List<String> attempts = nullable
                ? List.of("ALTER TABLE play_queue_file ALTER COLUMN position SET NULL",
                        "ALTER TABLE play_queue_file ALTER COLUMN position DROP NOT NULL",
                        "ALTER TABLE play_queue_file MODIFY position INT NULL")
                : List.of("ALTER TABLE play_queue_file ALTER COLUMN position SET NOT NULL",
                        "ALTER TABLE play_queue_file ALTER COLUMN position SET NOT NULL",
                        "ALTER TABLE play_queue_file MODIFY position INT NOT NULL");
        for (String sql : attempts) {
            try {
                jdbcTemplate.execute(sql);
                return;
            } catch (Exception e) {
                // try the next dialect's syntax
            }
        }
        throw new IllegalStateException("No dialect accepted the position nullability toggle");
    }

    // ---------------------------------------------------------------------------------------
    // @OrderColumn mapping: exact order through save/reload, reorder, currentIndex mapping.
    // ---------------------------------------------------------------------------------------

    @Test
    @Transactional
    public void savedQueueKeepsExactOrderThroughReload() {
        savedPlayQueueRepository.save(newQueue(USER_A, List.of(song3, song1, song2)));
        entityManager.flush();
        entityManager.clear();

        SavedPlayQueue reloaded = savedPlayQueueRepository.findByUsername(USER_A).orElseThrow();
        assertEquals(List.of(song3.getId(), song1.getId(), song2.getId()), mediaFileIds(reloaded),
                "saved order must survive the reload exactly");

        reloaded.setMediaFiles(new ArrayList<>(List.of(song2, song3, song1)));
        savedPlayQueueRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        SavedPlayQueue reordered = savedPlayQueueRepository.findByUsername(USER_A).orElseThrow();
        assertEquals(List.of(song2.getId(), song3.getId(), song1.getId()), mediaFileIds(reordered),
                "a replaced collection must persist its new order");
    }

    @Test
    @Transactional
    public void savePlayQueueByIndexMapsCurrentIndexForReorderedQueue() {
        // Duplicate track at positions 0 and 2 — only list semantics can address the second
        // copy; the stored currentIndex must point at it after the round-trip.
        List<Integer> ids = List.of(song2.getId(), song1.getId(), song2.getId());
        playQueueService.savePlayQueueByIndex(USER_A, ids, 2, 1234L, USER_A);
        entityManager.flush();
        entityManager.clear();

        SavedPlayQueue reloaded = playQueueService.loadSavedPlayQueueForRest(USER_A);
        assertNotNull(reloaded);
        assertEquals(ids, mediaFileIds(reloaded), "duplicate-bearing order must survive");
        assertEquals(2, reloaded.getCurrentIndex(), "currentIndex addresses the second copy");
        assertEquals(song2.getId(), reloaded.getMediaFiles().get(reloaded.getCurrentIndex()).getId());
    }

    @Test
    @Transactional
    public void collectionFetchReadsPositionColumn() {
        // The mapping-level proof (#214 two-state): the SELECT that loads the collection must
        // carry the position column — with @OrderColumn removed it does not, even though the
        // order-through-reload test above would still pass on HSQLDB by id-order accident.
        savedPlayQueueRepository.save(newQueue(USER_A, List.of(song1, song2, song3)));
        entityManager.flush();
        entityManager.clear();

        RecordingStatementInspector.STATEMENTS.clear();
        SavedPlayQueue reloaded = savedPlayQueueRepository.findByUsername(USER_A).orElseThrow();
        assertEquals(3, reloaded.getMediaFiles().size());

        List<String> joinTableSelects = RecordingStatementInspector.STATEMENTS.stream()
                .filter(sql -> sql.startsWith("select") && sql.contains("play_queue_file"))
                .toList();
        assertTrue(!joinTableSelects.isEmpty(), "the collection load must query play_queue_file");
        assertTrue(joinTableSelects.stream().anyMatch(sql -> sql.contains("position")),
                "the collection load must read the position column (@OrderColumn drives the order); got: "
                        + joinTableSelects);
    }

    /** Captures every SQL statement Hibernate issues, for the mapping assertions. */
    public static class RecordingStatementInspector implements StatementInspector {

        static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

        @Override
        public String inspect(String sql) {
            STATEMENTS.add(sql.toLowerCase());
            return sql;
        }
    }
}
