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
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of {@link MediaFileDao}.
 *
 * @author Y.Tory
 */
@SpringBootTest
@EnableConfigurationProperties(AirsonicHomeConfig.class)
@ContextConfiguration(initializers = ConfigDataApplicationContextInitializer.class)
public class MediaFileRepositoryTest {

    @Autowired
    MediaFileRepository mediaFileRepository;

    @Autowired
    MusicFolderRepository musicFolderRepository;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @TempDir
    private static Path tempAirsonicDir;

    @TempDir
    private Path tempMusicDir;

    private MusicFolder testFolder;

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
        testFolder = new MusicFolder(tempMusicDir, "name", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);
    }

    @AfterEach
    public void cleanUpAfter() {
        jdbcTemplate.execute("DELETE FROM media_file");
        musicFolderRepository.delete(testFolder);
    }

    @Test
    public void testGetMediaFilesByRelativePathAndFolderId() {
        //prepare
        MediaFile baseFile = new MediaFile();
        baseFile.setFolder(testFolder);
        baseFile.setPath("test.wav");
        baseFile.setMediaType(MediaType.MUSIC);
        baseFile.setIndexPath("test.cue");
        baseFile.setStartPosition(MediaFile.NOT_INDEXED);
        baseFile.setCreated(Instant.now());
        baseFile.setChanged(Instant.now());
        baseFile.setLastScanned(Instant.now());
        baseFile.setChildrenLastUpdated(Instant.now());
        // save
        mediaFileRepository.save(baseFile);

        // assert
        List<MediaFile> registeredTracks = mediaFileRepository.findByFolderAndPath(testFolder, "test.wav");
        assertEquals(1, registeredTracks.size());

        // update
        MediaFile mediaFile = new MediaFile();
        mediaFile.setFolder(testFolder);
        mediaFile.setPath("test.wav");
        mediaFile.setMediaType(MediaType.MUSIC);
        mediaFile.setStartPosition(10.0);
        mediaFile.setCreated(Instant.now());
        mediaFile.setChanged(Instant.now());
        mediaFile.setLastScanned(Instant.now());
        mediaFile.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(mediaFile);

        // assertion
        registeredTracks = mediaFileRepository.findByFolderAndPath(testFolder, "test.wav");
        assertEquals(2, registeredTracks.size());
        registeredTracks.forEach(t -> assertEquals("test.wav",t.getPath()));

        MusicFolder wrongFolder = new MusicFolder(tempMusicDir, "wrong", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        wrongFolder.setId(testFolder.getId() + 1);

        List<MediaFile> wrongFolderTracks = mediaFileRepository.findByFolderAndPath(wrongFolder, "test.wav");
        assertEquals(0, wrongFolderTracks.size());

        List<MediaFile> wrongPathTracks = mediaFileRepository.findByFolderAndPath(testFolder, "wrong.wav");
        assertEquals(0, wrongPathTracks.size());
    }

    // ----------------------------------------------------------------------------------------
    // Genre filter — packed `genres` column with delimiter-aware LIKE + scalar `genre` fallback
    // for legacy rows where `genres IS NULL`. Covers both repository methods (singular and
    // plural MediaType). Fixes #256.
    // ----------------------------------------------------------------------------------------

    private MediaFile saveGenreFixture(String name, MediaType mediaType, String scalarGenre, String packedGenres) {
        MediaFile file = new MediaFile();
        file.setFolder(testFolder);
        file.setPath(name);
        file.setMediaType(mediaType);
        file.setGenre(scalarGenre);
        file.setGenres(packedGenres);
        file.setPresent(true);
        file.setStartPosition(MediaFile.NOT_INDEXED);
        Instant now = Instant.now();
        file.setCreated(now);
        file.setChanged(now);
        file.setLastScanned(now);
        file.setChildrenLastUpdated(now);
        return mediaFileRepository.save(file);
    }

    private List<String> namesOf(List<MediaFile> rows) {
        return rows.stream().map(MediaFile::getPath).sorted().collect(Collectors.toList());
    }

    @Test
    public void testFindByGenreSongsMatchesMultiFramePositions() {
        // PR #256: the packed `genres` column carries every frame. The four-way LIKE must
        // match the queried token at any position (sole / first / middle / last) and at no
        // substring-only position (Heavy Metal, Metalcore must not match Metal).
        saveGenreFixture("sole.mp3", MediaType.MUSIC, "Metal", "Metal");
        saveGenreFixture("first.mp3", MediaType.MUSIC, "Metal", "Metal;Rock");
        saveGenreFixture("middle.mp3", MediaType.MUSIC, "Rock", "Rock;Metal;Indie");
        saveGenreFixture("last.mp3", MediaType.MUSIC, "Rock", "Rock;Metal");
        saveGenreFixture("heavy.mp3", MediaType.MUSIC, "Heavy Metal", "Heavy Metal");
        saveGenreFixture("core.mp3", MediaType.MUSIC, "Metalcore", "Metalcore");
        saveGenreFixture("unrelated.mp3", MediaType.MUSIC, "Rock", "Rock;Pop");

        Pageable page = PageRequest.of(0, 100);
        List<MediaFile> hits = mediaFileRepository
                .findByFolderInAndMediaTypeInAndGenreAndPresentTrue(
                        List.of(testFolder), List.of(MediaType.MUSIC), "Metal", page);

        assertEquals(List.of("first.mp3", "last.mp3", "middle.mp3", "sole.mp3"), namesOf(hits));
    }

    @Test
    public void testFindByGenreSongsFallsBackToScalarWhenPackedNull() {
        // Legacy / un-rescanned rows have `genres IS NULL`. The fallback arm must keep them
        // queryable via the scalar `genre` column so the fix doesn't make pre-fix data invisible
        // until the next rescan populates the packed column.
        saveGenreFixture("legacy.mp3", MediaType.MUSIC, "Metal", null);
        saveGenreFixture("modern.mp3", MediaType.MUSIC, "Rock", "Rock;Metal");

        Pageable page = PageRequest.of(0, 100);
        List<MediaFile> hits = mediaFileRepository
                .findByFolderInAndMediaTypeInAndGenreAndPresentTrue(
                        List.of(testFolder), List.of(MediaType.MUSIC), "Metal", page);

        assertEquals(List.of("legacy.mp3", "modern.mp3"), namesOf(hits));
    }

    @Test
    public void testFindByGenreSongsExcludesNonMatches() {
        // Negative: a row whose packed value lacks the queried token and whose scalar (only
        // consulted when packed IS NULL) also lacks it must not appear in the result.
        saveGenreFixture("only-rock.mp3", MediaType.MUSIC, "Rock", "Rock;Pop");

        Pageable page = PageRequest.of(0, 100);
        List<MediaFile> hits = mediaFileRepository
                .findByFolderInAndMediaTypeInAndGenreAndPresentTrue(
                        List.of(testFolder), List.of(MediaType.MUSIC), "Metal", page);

        assertTrue(hits.isEmpty());
    }

    @Test
    public void testFindByGenreAlbumsUsesSamePredicate() {
        // The singular-MediaType variant powers getAlbumsByGenre (folder rows). Post-#255 the
        // packed column is populated on album folders too, so the same multi-frame fan-out must
        // apply. Mirror the positional cases plus a substring guard.
        saveGenreFixture("album-sole", MediaType.ALBUM, "Metal", "Metal");
        saveGenreFixture("album-first", MediaType.ALBUM, "Metal", "Metal;Rock");
        saveGenreFixture("album-middle", MediaType.ALBUM, "Rock", "Rock;Metal;Indie");
        saveGenreFixture("album-last", MediaType.ALBUM, "Rock", "Rock;Metal");
        saveGenreFixture("album-heavy", MediaType.ALBUM, "Heavy Metal", "Heavy Metal");
        saveGenreFixture("album-legacy", MediaType.ALBUM, "Metal", null);
        saveGenreFixture("album-other", MediaType.ALBUM, "Pop", "Pop;Indie");

        Pageable page = PageRequest.of(0, 100);
        List<MediaFile> hits = mediaFileRepository
                .findByFolderInAndMediaTypeAndGenreAndPresentTrue(
                        List.of(testFolder), MediaType.ALBUM, "Metal", page);

        assertEquals(
                List.of("album-first", "album-last", "album-legacy", "album-middle", "album-sole"),
                namesOf(hits));
    }

}