/*
 This file is part of Airsonic.

 Airsonic is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2023 (C) Y.Tory
 */
package org.airsonic.player.repository;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.StructuredLyricsLine;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.transaction.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


@EnableConfigurationProperties({ AirsonicHomeConfig.class })
@Transactional
@SpringBootTest
public class LyricsRepositoryTest {

    @Autowired
    private LyricsRepository lyricsRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @TempDir
    private static Path tempAirsonicDir;

    @TempDir
    private Path tempMusicDir;

    private MusicFolder testFolder;

    private MediaFile testMediaFile;

    @BeforeAll
    public static void setUp() {
        System.setProperty("airsonic.home", tempAirsonicDir.toString());
    }

    @AfterAll
    public static void cleanUp() {
        System.clearProperty("airsonic.home");
    }

    @BeforeEach
    public void cleanUpBefore() {
        testFolder = new MusicFolder(tempMusicDir, "name", Type.MEDIA, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);
        // media file
        testMediaFile = new MediaFile();
        testMediaFile.setFolder(testFolder);
        testMediaFile.setPath("bookmark.wav");
        testMediaFile.setMediaType(MediaType.MUSIC);
        testMediaFile.setIndexPath("test.cue");
        testMediaFile.setStartPosition(MediaFile.NOT_INDEXED);
        testMediaFile.setCreated(Instant.now());
        testMediaFile.setChanged(Instant.now());
        testMediaFile.setLastScanned(Instant.now());
        testMediaFile.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(testMediaFile);

    }

    @Test
    public void testCreateLyrics() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);

        Lyrics newLyrics = lyricsRepository.findAll().get(0);
        assertEquals(lyrics.getLyrics(), newLyrics.getLyrics());
        assertEquals(lyrics.getCreated(), newLyrics.getCreated());
        assertEquals(lyrics.getUpdated(), newLyrics.getUpdated());
    }

    @Test
    public void testUpdateLyrics() {
        Lyrics lyrics = new Lyrics("Initial lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);
        lyrics = lyricsRepository.findAll().get(0);
        lyrics.setLyrics("Updated lyrics text");
        lyricsRepository.save(lyrics);
        Lyrics updatedLyrics = lyricsRepository.findAll().get(0);
        assertEquals("Updated lyrics text", updatedLyrics.getLyrics());
        assertEquals(lyrics.getCreated(), updatedLyrics.getCreated());
        assertEquals(lyrics.getUpdated(), updatedLyrics.getUpdated());
    }

    @Test
    public void testDeleteLyrics() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);
        assertEquals(1, lyricsRepository.count());
        lyricsRepository.deleteById(lyrics.getId());
        assertEquals(0, lyricsRepository.count());
    }

    @Test
    public void testErrorIfInvalidMediaFileId() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", 9999, "file"); // Assuming 9999 is an invalid ID
        assertThrows(DataIntegrityViolationException.class, () -> lyricsRepository.save(lyrics));
    }

    @Test
    public void testFindByMediaFileId() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);
        Lyrics foundLyrics = lyricsRepository.findByMediaFileId(testMediaFile.getId()).orElse(null);
        assertEquals(lyrics.getLyrics(), foundLyrics.getLyrics());
        assertEquals(lyrics.getMediaFileId(), foundLyrics.getMediaFileId());
        assertNotNull(foundLyrics.getCreated());
        assertNotNull(foundLyrics.getUpdated());
    }

    @Test
    public void testDeleteByCascadeDelete() {
        Lyrics lyrics = new Lyrics("Sample lyrics text", testMediaFile.getId(), "file");
        lyricsRepository.save(lyrics);
        assertEquals(1, lyricsRepository.count());

        mediaFileRepository.delete(testMediaFile);
        mediaFileRepository.flush();
        assertEquals(0, lyricsRepository.count(), "Lyrics should be deleted when MediaFile is deleted");
    }

    @Test
    public void testSyncedLyricsLinesRoundTrip() {
        // Synced LRC lyrics (#140): structured lines persist as a child collection and reload
        // ordered by position with their start_ms intact, across the real DDL.
        Lyrics lyrics = new Lyrics("Line A\nLine B\nLine C", testMediaFile.getId(), "file");
        lyrics.setSynced(true);
        lyrics.getLines().add(new StructuredLyricsLine(lyrics, 0, 1000L, "Line A"));
        lyrics.getLines().add(new StructuredLyricsLine(lyrics, 1, 2500L, "Line B"));
        lyrics.getLines().add(new StructuredLyricsLine(lyrics, 2, 4200L, "Line C"));
        lyricsRepository.save(lyrics);

        Lyrics reloaded = lyricsRepository.findByMediaFileId(testMediaFile.getId()).orElseThrow();
        assertTrue(reloaded.isSynced());
        assertEquals(3, reloaded.getLines().size());
        // @OrderBy("position ASC") guarantees deterministic order across HSQLDB/Postgres/MariaDB.
        assertEquals(0, reloaded.getLines().get(0).getPosition());
        assertEquals(1000L, reloaded.getLines().get(0).getStartMs());
        assertEquals("Line A", reloaded.getLines().get(0).getText());
        assertEquals(2500L, reloaded.getLines().get(1).getStartMs());
        assertEquals(4200L, reloaded.getLines().get(2).getStartMs());
        assertEquals("Line C", reloaded.getLines().get(2).getText());
    }

    @Test
    public void testSyncedLinesCascadeDeleteWithLyrics() {
        Lyrics lyrics = new Lyrics("Line A\nLine B", testMediaFile.getId(), "file");
        lyrics.setSynced(true);
        lyrics.getLines().add(new StructuredLyricsLine(lyrics, 0, 0L, "Line A"));
        lyrics.getLines().add(new StructuredLyricsLine(lyrics, 1, 1500L, "Line B"));
        lyricsRepository.save(lyrics);
        lyricsRepository.flush();

        assertEquals(2, countStructuredLyricsLines());

        // Deleting the parent lyrics row removes its child lines (orphanRemoval / cascade).
        lyricsRepository.deleteById(lyrics.getId());
        lyricsRepository.flush();
        assertEquals(0, countStructuredLyricsLines(),
                "structured_lyrics_line rows must be removed with their parent lyrics");
    }

    private int countStructuredLyricsLines() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM structured_lyrics_line", Integer.class);
    }

}
