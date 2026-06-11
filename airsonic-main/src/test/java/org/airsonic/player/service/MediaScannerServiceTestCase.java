package org.airsonic.player.service;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.io.Resources;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * A unit test class to test the MediaScannerService.
 * <p>
 * This class uses the Spring application context configuration present in the
 * /org/airsonic/player/service/mediaScannerServiceTestCase/ directory.
 * <p>
 * The media library is found in the /MEDIAS directory.
 * It is composed of 2 musicFolders (Music and Music2) and several little weight audio files.
 * <p>
 * At runtime, the subsonic_home dir is set to target/test-classes/org/airsonic/player/service/mediaScannerServiceTestCase.
 * An empty database is created on the fly.
 */
@TestPropertySource(properties = {
    "airsonic.cue.enabled=true"
})
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class MediaScannerServiceTestCase {

    private static final Logger LOG = LoggerFactory.getLogger(MediaScannerServiceTestCase.class);

    @TempDir
    private static Path tempDir;

    private final MetricRegistry metrics = new MetricRegistry();

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @MockitoSpyBean
    private MediaFileService mediaFileService;

    @Autowired
    private ArtistService artistService;

    @Autowired
    private AlbumService albumService;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private SettingsService settingsService;

    @TempDir
    private Path temporaryFolder;

    private List<MusicFolder> testFolders = new ArrayList<>();

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("DELETE FROM media_file");
        jdbcTemplate.execute("DELETE FROM album");
        jdbcTemplate.execute("DELETE FROM artist");
        // Defensive sweep against leaked music_folder rows from earlier test classes whose
        // @AfterEach failed to delete by the JPA-assigned ID. The shared MEDIAS/Music and
        // MEDIAS/Music2 paths conflict with idx_music_folder_path on matrix DBs (Postgres,
        // MariaDB) and crash the saveAll below as DataIntegrityViolation; HSQLDB tolerates
        // the residue. The wider multi-offender cleanup antipattern is tracked in #266.
        Path baseMediaPath = MusicFolderTestData.resolveBaseMediaPath();
        musicFolderRepository.deleteAll(
            musicFolderRepository.findAll().stream()
                .filter(f -> f.getPath().startsWith(baseMediaPath))
                .toList());
        TestCaseUtils.waitForScanFinish(mediaScannerService);
        mediaFolderService.clearMusicFolderCache();
        mediaFolderService.clearMediaFileCache();
        testFolders = new ArrayList<>();
    }

    @AfterEach
    public void cleanup() {
        testFolders.forEach(f -> musicFolderRepository.delete(f));
        testFolders.clear();
    }

    /**
     * Tests the MediaScannerService by scanning the test media library into an empty database.
     */
    @Test
    public void testScanLibrary() {
        LOG.info("start testScanLibrary");
        Timer globalTimer = metrics.timer(MetricRegistry.name(MediaScannerServiceTestCase.class, "Timer.global"));

        Timer.Context globalTimerContext = globalTimer.time();
        testFolders = MusicFolderTestData.getTestMusicFolders();
        musicFolderRepository.saveAll(testFolders);
        mediaFolderService.clearMusicFolderCache();
        TestCaseUtils.execScan(mediaScannerService);

        globalTimerContext.stop();

        // Music Folder Music must have 3 children
        List<MediaFile> listeMusicChildren = mediaFileRepository.findByFolderAndParentPath(testFolders.get(0), "", Sort.by("startPosition"));
        assertEquals(3, listeMusicChildren.size());
        // Music Folder Music2 must have 1 children
        List<MediaFile> listeMusic2Children = mediaFileRepository.findByFolderAndParentPath(testFolders.get(1), "", Sort.by("startPosition"));
        assertEquals(1, listeMusic2Children.size());

        System.out.println("--- List of all artists ---");
        System.out.println("artistName#albumCount");
        List<Artist> allArtists = artistService.getAlphabeticalArtists(testFolders);
        allArtists.forEach(artist -> System.out.println(artist.getName() + "#" + artist.getAlbumCount()));
        System.out.println("--- *********************** ---");

        System.out.println("--- List of all albums ---");
        System.out.println("name#artist");
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, testFolders);
        allAlbums.forEach(album -> System.out.println(album.getName() + "#" + album.getArtist()));
        assertEquals(5, allAlbums.size());
        System.out.println("--- *********************** ---");

        List<MediaFile> listeSongs = mediaFileService.getSongsByGenre(0, Integer.MAX_VALUE, "Baroque Instrumental", testFolders);
        assertEquals(2, listeSongs.size());

        // display out metrics report
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.report();

        System.out.print("End");
    }


    @Test
    public void testSpecialCharactersInFilename() throws Exception {
        LOG.info("start testSpecialCharactersInFilename");
        String directoryName = "Muff1nman\u2019s \uFF0FMusic";
        String fileName = "Muff1nman\u2019s\uFF0FPiano.mp3";
        Path artistDir = temporaryFolder.resolve(directoryName);
        Path musicFile = artistDir.resolve(fileName);
        Files.createDirectories(artistDir);
        Files.copy(Paths.get(Resources.getResource("MEDIAS/piano.mp3").toURI()), musicFile);

        MusicFolder musicFolder = new MusicFolder(temporaryFolder, "MusicSpecial", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        MediaFile mediaFile = mediaFileService.getMediaFile(musicFile);
        assertEquals(mediaFile.getRelativePath(), temporaryFolder.relativize(musicFile));
        assertThat(mediaFile.getFolder().getId()).isEqualTo(musicFolder.getId());
        MediaFile relativeMediaFile = mediaFileService.getMediaFile(temporaryFolder.relativize(musicFile), musicFolder);
        assertEquals(relativeMediaFile.getRelativePath(), mediaFile.getRelativePath());
    }

    @Test
    public void testNeverScanned() {
        LOG.info("start testNeverScanned");
        mediaScannerService.neverScanned();
    }

    @Test
    public void testMusicBrainzReleaseIdTag() {
        LOG.info("start testMusicBrainzReleaseIdTag");

        // Add the "Music3" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusic3FolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "Music3", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Music3" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        assertEquals("TestMusic3Artist", artist.getName());
        assertEquals(1, artist.getAlbumCount());

        // Test that the album is correctly imported, along with its MusicBrainz release ID
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("TestAlbum", album.getName());
        assertEquals("TestMusic3Artist", album.getArtist());
        assertEquals(1, album.getSongCount());
        assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", album.getMusicBrainzReleaseId());
        assertEquals("TestAlbum", album.getPath());

        // Test that the music file is correctly imported, along with its MusicBrainz release ID and recording ID
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("Aria", file.getTitle());
        assertEquals("flac", file.getFormat());
        assertEquals("TestAlbum", file.getAlbumName());
        assertEquals("TestMusic3Artist", file.getArtist());
        assertEquals("TestMusic3Artist", file.getAlbumArtist());
        assertEquals(1, (long)file.getTrackNumber());
        assertEquals(2001, (long)file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("01 - Aria.flac").toString(), file.getPath());
        assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", file.getMusicBrainzReleaseId());
        assertEquals("831586f4-56f9-4785-ac91-447ae20af633", file.getMusicBrainzRecordingId());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }

    // The album sort name (FieldKey.ALBUM_SORT) is carried on each track's MediaFile and
    // aggregated onto the Album in the private updateAlbum(), mirroring the mb_release_id
    // pattern. These tests drive that aggregation step directly so the set / null-guard /
    // last-write-wins semantics can be covered without audio fixtures tagged with ALBUM_SORT.

    private static final Instant ALBUM_SORT_SCAN_TIME = Instant.now().truncatedTo(ChronoUnit.MICROS);

    private Album newSortNameTestAlbum() {
        Album album = new Album();
        album.setName("TestAlbum");
        album.setArtist("TestArtist");
        album.setPath("TestAlbum");
        // Match the scan time so updateAlbum treats this as a repeat encounter and skips
        // the persistence/index branch — keeping these tests free of DB side effects.
        album.setLastScanned(ALBUM_SORT_SCAN_TIME);
        return album;
    }

    private void aggregateTrack(Map<String, Album> albums, String albumSortName) {
        MediaFile file = new MediaFile();
        file.setMediaType(MediaFile.MediaType.MUSIC);
        file.setAlbumName("TestAlbum");
        file.setArtist("TestArtist");
        file.setAlbumArtist("TestArtist");
        file.setParentPath("TestAlbum");
        file.setAlbumSortName(albumSortName);
        ReflectionTestUtils.invokeMethod(mediaScannerService, "updateAlbum",
            null, file, null, ALBUM_SORT_SCAN_TIME,
            new HashMap<String, AtomicInteger>(), albums, new HashSet<Integer>());
    }

    @Test
    public void testAlbumSortNameSetFromTrack() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateTrack(albums, "Beatles, The");

        assertEquals("Beatles, The", album.getSortName());
    }

    @Test
    public void testAlbumSortNameNullDoesNotClobber() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateTrack(albums, "Beatles, The");
        aggregateTrack(albums, null);

        assertEquals("Beatles, The", album.getSortName());
    }

    @Test
    public void testAlbumSortNameLastWriteWins() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateTrack(albums, "First Sort");
        aggregateTrack(albums, "Second Sort");

        assertEquals("Second Sort", album.getSortName());
    }

    // The isCompilation flag and the two raw-string date carriers
    // (original_release_date, release_date) follow the same carrier-then-aggregate pattern
    // as album_sort_name: last-write-wins, null-guarded so a later track with no value does
    // not clobber a previously-set one.

    private void aggregateScalarsAndDates(Map<String, Album> albums, Boolean compilation,
                                          String originalReleaseDate, String releaseDate) {
        MediaFile file = new MediaFile();
        file.setMediaType(MediaFile.MediaType.MUSIC);
        file.setAlbumName("TestAlbum");
        file.setArtist("TestArtist");
        file.setAlbumArtist("TestArtist");
        file.setParentPath("TestAlbum");
        file.setCompilation(compilation);
        file.setOriginalReleaseDate(originalReleaseDate);
        file.setReleaseDate(releaseDate);
        ReflectionTestUtils.invokeMethod(mediaScannerService, "updateAlbum",
            null, file, null, ALBUM_SORT_SCAN_TIME,
            new HashMap<String, AtomicInteger>(), albums, new HashSet<Integer>());
    }

    @Test
    public void testAlbumIsCompilationSetAndNullDoesNotClobber() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateScalarsAndDates(albums, Boolean.TRUE, null, null);
        assertEquals(Boolean.TRUE, album.getCompilation());

        aggregateScalarsAndDates(albums, null, null, null);
        assertEquals(Boolean.TRUE, album.getCompilation());
    }

    @Test
    public void testAlbumOriginalReleaseDateSetAndNullDoesNotClobber() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateScalarsAndDates(albums, null, "2003-10-12", null);
        assertEquals("2003-10-12", album.getOriginalReleaseDate());

        aggregateScalarsAndDates(albums, null, null, null);
        assertEquals("2003-10-12", album.getOriginalReleaseDate());
    }

    @Test
    public void testAlbumReleaseDateSetAndNullDoesNotClobber() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateScalarsAndDates(albums, null, null, "2020-05");
        assertEquals("2020-05", album.getReleaseDate());

        aggregateScalarsAndDates(albums, null, null, null);
        assertEquals("2020-05", album.getReleaseDate());
    }

    // The releaseTypes and recordLabels packed carriers follow the same null-guarded
    // last-write-wins pattern as the Batch A scalars / #136 album_sort_name.

    private void aggregateMultiValue(Map<String, Album> albums, String releaseTypes, String recordLabels) {
        MediaFile file = new MediaFile();
        file.setMediaType(MediaFile.MediaType.MUSIC);
        file.setAlbumName("TestAlbum");
        file.setArtist("TestArtist");
        file.setAlbumArtist("TestArtist");
        file.setParentPath("TestAlbum");
        file.setReleaseTypes(releaseTypes);
        file.setRecordLabels(recordLabels);
        ReflectionTestUtils.invokeMethod(mediaScannerService, "updateAlbum",
            null, file, null, ALBUM_SORT_SCAN_TIME,
            new HashMap<String, AtomicInteger>(), albums, new HashSet<Integer>());
    }

    @Test
    public void testAlbumReleaseTypesSetAndNullDoesNotClobber() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateMultiValue(albums, "Album\nCompilation", null);
        assertEquals("Album\nCompilation", album.getReleaseTypes());

        aggregateMultiValue(albums, null, null);
        assertEquals("Album\nCompilation", album.getReleaseTypes());
    }

    @Test
    public void testAlbumRecordLabelsSetAndNullDoesNotClobber() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateMultiValue(albums, null, "Sony/BMG; Columbia\nWarner");
        assertEquals("Sony/BMG; Columbia\nWarner", album.getRecordLabels());

        aggregateMultiValue(albums, null, null);
        assertEquals("Sony/BMG; Columbia\nWarner", album.getRecordLabels());
    }

    // updateGenres() reads the genre count table source — prefers media_file.genres (PR #134's
    // packed multi-value column, populated alongside the scalar on audio file rows), falls back
    // to splitting the scalar media_file.genre on the same separators when the packed column is
    // absent (album folder rows; pre-#134 legacy rows). These tests drive the private feeder so
    // the multi-frame, fallback, and no-genre semantics can be covered without audio fixtures.

    private org.airsonic.player.domain.Genres invokeUpdateGenres(MediaFile file, String separators) {
        // Stub locally per call so changes don't leak to unrelated tests; the spy bean's other
        // methods (mostly DB-backed property reads) continue to pass through unchanged.
        when(settingsService.getGenreSeparators()).thenReturn(separators);
        org.airsonic.player.domain.Genres bin = new org.airsonic.player.domain.Genres();
        ReflectionTestUtils.invokeMethod(mediaScannerService, "updateGenres", file, bin);
        return bin;
    }

    private MediaFile audioFile(String genre, String packedGenres) {
        MediaFile file = new MediaFile();
        file.setMediaType(MediaFile.MediaType.MUSIC);
        file.setGenre(genre);
        file.setGenres(packedGenres);
        return file;
    }

    private MediaFile albumFolder(String genre) {
        MediaFile file = new MediaFile();
        file.setMediaType(MediaFile.MediaType.ALBUM);
        file.setGenre(genre);
        // genres[] is intentionally not set — album folders never get the packed column populated.
        return file;
    }

    private int songCountOf(org.airsonic.player.domain.Genres g, String name) {
        return g.getGenres().stream()
                .filter(x -> name.equals(x.getName()))
                .findFirst()
                .map(org.airsonic.player.domain.Genre::getSongCount)
                .orElse(0);
    }

    private int albumCountOf(org.airsonic.player.domain.Genres g, String name) {
        return g.getGenres().stream()
                .filter(x -> name.equals(x.getName()))
                .findFirst()
                .map(org.airsonic.player.domain.Genre::getAlbumCount)
                .orElse(0);
    }

    @Test
    public void testUpdateGenresMultiFrameAudioFileCountsAllGenres() {
        // PR #134's packed column carries every genre frame; the feeder must increment all rows.
        MediaFile file = audioFile("Rock", "Rock;Pop;Indie");
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";");
        assertEquals(1, songCountOf(g, "Rock"));
        assertEquals(1, songCountOf(g, "Pop"));
        assertEquals(1, songCountOf(g, "Indie"));
        assertEquals(3, g.getGenres().size());
    }

    @Test
    public void testUpdateGenresSingleGenreAudioFileIncrementsOnce() {
        MediaFile file = audioFile("Rock", "Rock");
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";");
        assertEquals(1, songCountOf(g, "Rock"));
        assertEquals(1, g.getGenres().size());
    }

    @Test
    public void testUpdateGenresAudioFileFallsBackToScalarWhenPackedNull() {
        // Mirrors pre-#134 legacy rows where genres[] was never written: the scalar still flows.
        MediaFile file = audioFile("Rock", null);
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";");
        assertEquals(1, songCountOf(g, "Rock"));
        assertEquals(1, g.getGenres().size());
    }

    @Test
    public void testUpdateGenresScalarFallbackSplitsOnSeparators() {
        // Legacy scalar tag containing a separator gets split via the same Genres.split idiom,
        // so the fallback path produces the same row set as a properly packed column would.
        MediaFile file = audioFile("Rock; Pop", null);
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";");
        assertEquals(1, songCountOf(g, "Rock"));
        assertEquals(1, songCountOf(g, "Pop"));
        assertEquals(2, g.getGenres().size());
    }

    @Test
    public void testUpdateGenresNoGenreLeavesTableUntouched() {
        MediaFile file = audioFile(null, null);
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";");
        assertTrue(g.getGenres().isEmpty());
    }

    @Test
    public void testUpdateGenresBlankGenreLeavesTableUntouched() {
        MediaFile file = audioFile("   ", null);
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";");
        assertTrue(g.getGenres().isEmpty());
    }

    @Test
    public void testUpdateGenresAlbumFolderIncrementsAlbumCountViaScalar() {
        // Album folders are written by MediaFileService without the packed column, so they hit
        // the scalar fallback. Verifies album_count increments and song_count stays zero.
        MediaFile file = albumFolder("Rock");
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";");
        assertEquals(1, albumCountOf(g, "Rock"));
        assertEquals(0, songCountOf(g, "Rock"));
        assertEquals(1, g.getGenres().size());
    }

    @Test
    public void testUpdateGenresAlbumFolderScalarSplitsOnSeparators() {
        // Same as the audio scalar split, but exercised through the isAlbum() branch.
        MediaFile file = albumFolder("Rock; Metal");
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";");
        assertEquals(1, albumCountOf(g, "Rock"));
        assertEquals(1, albumCountOf(g, "Metal"));
        assertEquals(2, g.getGenres().size());
    }

    @Test
    public void testUpdateGenresMultipleSeparatorCharsHonoured() {
        // The getGenreSeparators setting accepts a charset string ("'; ,'") — Genres.split honours
        // every char. Lock that in so a future change to the default doesn't silently regress the
        // multi-separator behaviour of the count-table feeder.
        MediaFile file = audioFile("Rock", "Rock;Pop,Indie");
        org.airsonic.player.domain.Genres g = invokeUpdateGenres(file, ";,");
        assertEquals(1, songCountOf(g, "Rock"));
        assertEquals(1, songCountOf(g, "Pop"));
        assertEquals(1, songCountOf(g, "Indie"));
        assertEquals(3, g.getGenres().size());
    }

    // Album-level ReplayGain (rg_album_gain / rg_album_peak) is aggregated from each track's
    // own rg_album_* columns during scan. Same null-guarded last-write-wins idiom as the
    // scalars/dates: a non-null track value persists; a later track with null does not clobber.
    // trackGain/trackPeak are per-track only and don't aggregate.

    private void aggregateAlbumReplayGain(Map<String, Album> albums, Double albumGain, Double albumPeak) {
        MediaFile file = new MediaFile();
        file.setMediaType(MediaFile.MediaType.MUSIC);
        file.setAlbumName("TestAlbum");
        file.setArtist("TestArtist");
        file.setAlbumArtist("TestArtist");
        file.setParentPath("TestAlbum");
        file.setReplayGainAlbumGain(albumGain);
        file.setReplayGainAlbumPeak(albumPeak);
        ReflectionTestUtils.invokeMethod(mediaScannerService, "updateAlbum",
            null, file, null, ALBUM_SORT_SCAN_TIME,
            new HashMap<String, AtomicInteger>(), albums, new HashSet<Integer>());
    }

    @Test
    public void testAlbumReplayGainSetFromTrack() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateAlbumReplayGain(albums, -7.50, 0.988);

        assertEquals(-7.50, album.getReplayGainAlbumGain());
        assertEquals(0.988, album.getReplayGainAlbumPeak());
    }

    @Test
    public void testAlbumReplayGainNullDoesNotClobber() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateAlbumReplayGain(albums, -7.50, 0.988);
        aggregateAlbumReplayGain(albums, null, null);

        assertEquals(-7.50, album.getReplayGainAlbumGain());
        assertEquals(0.988, album.getReplayGainAlbumPeak());
    }

    @Test
    public void testAlbumReplayGainLastWriteWins() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateAlbumReplayGain(albums, -7.50, 0.988);
        aggregateAlbumReplayGain(albums, -9.25, 0.751);

        assertEquals(-9.25, album.getReplayGainAlbumGain());
        assertEquals(0.751, album.getReplayGainAlbumPeak());
    }

    @Test
    public void testAlbumReplayGainAbsentWhenNoTracksTagged() {
        Album album = newSortNameTestAlbum();
        Map<String, Album> albums = new HashMap<>();
        albums.put("TestAlbum|TestArtist", album);

        aggregateAlbumReplayGain(albums, null, null);

        assertNull(album.getReplayGainAlbumGain());
        assertNull(album.getReplayGainAlbumPeak());
    }

    // The artist MB id (FieldKey.MUSICBRAINZ_RELEASEARTISTID) and sort name
    // (FieldKey.ALBUM_ARTIST_SORT) are carried on each track's MediaFile and aggregated onto
    // the Artist in the private updateArtist(). These tests drive that aggregation step
    // directly to cover the set / null-guard / last-write-wins semantics without audio fixtures.

    private static final Instant ARTIST_AGG_SCAN_TIME = Instant.now().truncatedTo(ChronoUnit.MICROS);

    private Artist newFieldsTestArtist() {
        Artist artist = new Artist("TestArtist");
        // Match the scan time so updateArtist treats this as a repeat encounter and skips
        // the persistence/index branch — keeping these tests free of DB side effects.
        artist.setLastScanned(ARTIST_AGG_SCAN_TIME);
        return artist;
    }

    private void aggregateArtistTrack(Map<String, Artist> artists, String mbArtistId, String artistSortName) {
        MediaFile file = new MediaFile();
        file.setMediaType(MediaFile.MediaType.MUSIC);
        file.setAlbumArtist("TestArtist");
        file.setMusicBrainzArtistId(mbArtistId);
        file.setArtistSortName(artistSortName);
        ReflectionTestUtils.invokeMethod(mediaScannerService, "updateArtist",
            null, file, null, ARTIST_AGG_SCAN_TIME,
            new HashMap<String, AtomicInteger>(), artists);
    }

    @Test
    public void testArtistFieldsSetFromTrack() {
        Artist artist = newFieldsTestArtist();
        Map<String, Artist> artists = new HashMap<>();
        artists.put("TestArtist", artist);

        aggregateArtistTrack(artists, "mb-artist-1", "Beatles, The");

        assertEquals("mb-artist-1", artist.getMusicBrainzArtistId());
        assertEquals("Beatles, The", artist.getSortName());
    }

    @Test
    public void testArtistFieldsNullDoesNotClobber() {
        Artist artist = newFieldsTestArtist();
        Map<String, Artist> artists = new HashMap<>();
        artists.put("TestArtist", artist);

        aggregateArtistTrack(artists, "mb-artist-1", "Beatles, The");
        aggregateArtistTrack(artists, null, null);

        assertEquals("mb-artist-1", artist.getMusicBrainzArtistId());
        assertEquals("Beatles, The", artist.getSortName());
    }

    @Test
    public void testArtistFieldsLastWriteWins() {
        Artist artist = newFieldsTestArtist();
        Map<String, Artist> artists = new HashMap<>();
        artists.put("TestArtist", artist);

        aggregateArtistTrack(artists, "mb-first", "First Sort");
        aggregateArtistTrack(artists, "mb-second", "Second Sort");

        assertEquals("mb-second", artist.getMusicBrainzArtistId());
        assertEquals("Second Sort", artist.getSortName());
    }

    @Test
    public void testMusicCue() {
        LOG.info("start testMusicCue");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicCueFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "Cue", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.cue"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("wav", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicCueWithBOM() {
        LOG.info("start testMusicCueWithBOM");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicCueWithBOMFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "Cue", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.cue"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("wav", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicCueWithDisableCueIndexing() {
        LOG.info("start testMusicCueWithDisableCueIndexing");

        when(settingsService.getEnableCueIndexing()).thenReturn(false);

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicDisableCueFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "CueDisabled", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(0, allArtists.size());

        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(musicFolder, "", Sort.by("startPosition"));
        assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

    }



    @Test
    public void testMusicInvalidCueWithLengthError() {
        LOG.info("start testMusicInvalidCueWithLengthError");

        when(settingsService.getEnableCueIndexing()).thenReturn(true);

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicInvalidCue2FolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "InvalidCue2", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(0, allArtists.size());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(folders.get(0), "", Sort.by("startPosition"));
        assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }


    @Test
    public void testMusicInvalidCueWithWarning() {
        LOG.info("start testMusicInvalidCueWithWarning");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicInvalidCue3FolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "InvalidCue3", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        assertEquals(0, allArtists.size());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(folders.get(0), "", Sort.by("startPosition"));
        assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }




    @Test
    public void testMusicWithCommmaFolderAndDuplicateBasenameAudio() {
        LOG.info("start testMusicWithCommmaFolderAndDuplicateBasenameAudio");
        // Add the "Music4" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusic4FolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "Music4", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Music4" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        List<MediaFile> listMusicChildren = mediaFileRepository.findByFolderAndParentPath(musicFolder, "", Sort.by("startPosition"));
        assertEquals(2, listMusicChildren.size());

        List<MediaFile> listDuplicateBaseNameFiles = mediaFileRepository.findByFolderAndParentPath(musicFolder, "a", Sort.by("startPosition"));
        assertEquals(2, listDuplicateBaseNameFiles.size());


    }

    @Test
    public void testMpcAudioTest() {
        LOG.info("start testMpcAudioTest");

        // Add the "MusicMpc" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicMpcFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "mpc", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Music4" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        List<MediaFile> listMusicChildren = mediaFileRepository.findByFolderAndParentPath(musicFolder, "", Sort.by("startPosition"));
        assertEquals(1, listMusicChildren.size());

        assertTrue(listMusicChildren.get(0).getDuration() > 0.0);
    }

    @Test
    public void testM4bAudioTest() {
        LOG.info("start testM4bAudioTest");

        Path m4bAudioFile = MusicFolderTestData.resolveM4bAudioPath();
        MusicFolder musicFolder = new MusicFolder(m4bAudioFile, "m4b", Type.MEDIA, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        List<MediaFile> listMusicChildren = mediaFileRepository.findByFolderAndParentPath(musicFolder, "",
                Sort.by("startPosition"));
        assertEquals(3, listMusicChildren.size());
        MediaFile base = listMusicChildren.get(0);
        assertEquals(-1.0d, base.getStartPosition(), 0.01);
        assertEquals("m4btestbook", base.getTitle());
        assertEquals("m4btestartist", base.getArtist());
        assertEquals("m4btestartist", base.getAlbumArtist());
        assertEquals("m4btest", base.getAlbumName());

        MediaFile chapter1 = listMusicChildren.get(1);
        assertEquals(0.0d, chapter1.getStartPosition(), 0.01);
        assertEquals(2.665d, chapter1.getDuration(), 0.01);
        assertEquals(" Chapter 001  - 00:00:02", chapter1.getTitle());
        assertEquals("m4btest", chapter1.getAlbumName());

        MediaFile chapter2 = listMusicChildren.get(2);
        assertEquals(2.665d, chapter2.getStartPosition(), 0.01);
        assertEquals(3.715d, chapter2.getDuration(), 0.01);
        assertEquals(" Chapter 002  - 00:00:03", chapter2.getTitle());
        assertEquals("m4btest", chapter2.getAlbumName());

    }

    @Test
    public void testMusicCueAndFlac() {
        LOG.info("start testMusicCueAndFlac");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicCueAndFlacFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "CueAndFlac", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("flac", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.flac").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.cue"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("flac", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.flac").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicFlacWithCue() {
        LOG.info("start testMusicFlacWithCue");

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicFlacWithCueFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicFolderFile, "FlacWithCue", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(musicFolder);
        musicFolderRepository.saveAll(testFolders);
        TestCaseUtils.execScan(mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderRepository.findById(musicFolder.getId()).get();
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistService.getAlphabeticalArtists(folders);
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileRepository.findByFolderAndParentPath(allAlbums.get(0).getFolder(), allAlbums.get(0).getPath(), Sort.by("startPosition"));
        assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("flac", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.flac").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.flac"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("flac", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.flac").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

}
