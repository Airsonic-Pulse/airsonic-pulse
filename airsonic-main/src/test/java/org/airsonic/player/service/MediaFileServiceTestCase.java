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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.service;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.File;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link MediaFileService#getRandomSongsForParent} against a real scanned
 * tree (#351): the pick must be subtree-scoped, bounded to the requested count, exclude videos
 * and directories, and act without replacement when the count exceeds the subtree.
 */
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class MediaFileServiceTestCase {

    @TempDir
    private static Path tempDir;

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private PlayQueueService playQueueService;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        // same defensive sweep as MediaScannerServiceTestCase: leaked music_folder rows on the
        // shared MEDIAS paths conflict with idx_music_folder_path on matrix DBs (#266)
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

    private MusicFolder scanFolder(Path folderPath, String name) {
        MusicFolder folder = new MusicFolder(folderPath, name, Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        testFolders.add(folder);
        musicFolderRepository.save(folder);
        mediaFolderService.clearMusicFolderCache();
        TestCaseUtils.execScan(mediaScannerService);
        return folder;
    }

    private MediaFile getByPath(MusicFolder folder, String path) {
        List<MediaFile> matches = mediaFileRepository.findByFolderAndPath(folder, path);
        assertEquals(1, matches.size(), "expected exactly one media_file row for path '" + path + "'");
        return matches.get(0);
    }

    /** The rows the old descendant walk would have offered to the shuffle. */
    private Set<Integer> eligibleAudioIds(MusicFolder folder, String parentPathPrefix) {
        return mediaFileRepository.findByFolderAndPresentTrue(folder).stream()
                .filter(MediaFile::isAudio)
                .filter(m -> m.getIndexPath() == null || m.getStartPosition() >= 0)
                .filter(m -> parentPathPrefix.isEmpty() || m.getPath().startsWith(parentPathPrefix))
                .map(MediaFile::getId)
                .collect(Collectors.toSet());
    }

    @Test
    public void getRandomSongsForParentOnRootIsBoundedAndWithoutReplacement() {
        MusicFolder folder = scanFolder(MusicFolderTestData.resolveMusicFolderPath(), "MusicShuffleTest");
        MediaFile root = getByPath(folder, "");
        Set<Integer> eligible = eligibleAudioIds(folder, "");
        assertTrue(eligible.size() >= 3, "fixture should contain several audio files");

        // bounded: N smaller than the subtree returns exactly N distinct eligible songs
        List<MediaFile> bounded = mediaFileService.getRandomSongsForParent(root, 2);
        assertEquals(2, bounded.size());
        assertEquals(2, bounded.stream().map(MediaFile::getId).distinct().count());
        bounded.forEach(song -> {
            assertTrue(eligible.contains(song.getId()), "unexpected song: " + song.getPath());
            assertTrue(song.isAudio());
            assertFalse(song.isVideo());
            assertFalse(song.isDirectory());
            assertTrue(song.isPresent());
        });

        // without replacement: N larger than the subtree returns every eligible song exactly once
        List<MediaFile> all = mediaFileService.getRandomSongsForParent(root, eligible.size() + 10);
        assertEquals(eligible.size(), all.size());
        assertEquals(eligible, all.stream().map(MediaFile::getId).collect(Collectors.toSet()));
    }

    @Test
    public void getRandomSongsForParentOnSubFolderStaysInsideTheSubtree() {
        MusicFolder folder = scanFolder(MusicFolderTestData.resolveMusicFolderPath(), "MusicShuffleTest");
        MediaFile parent = getByPath(folder, "_DIR_ Ravel");
        Set<Integer> eligible = eligibleAudioIds(folder, "_DIR_ Ravel" + File.separator);
        assertTrue(eligible.size() >= 2, "fixture should contain audio files below _DIR_ Ravel");

        List<MediaFile> songs = mediaFileService.getRandomSongsForParent(parent, eligible.size() + 10);

        // multi-level: grand-children from both sub-albums, nothing from outside the subtree
        assertEquals(eligible, songs.stream().map(MediaFile::getId).collect(Collectors.toSet()));
        songs.forEach(song -> assertTrue(song.getPath().startsWith("_DIR_ Ravel" + File.separator),
                "song outside the subtree: " + song.getPath()));
    }

    @Test
    public void getRandomSongsForParentDoesNotLeakIntoSiblingSharingNamePrefix() {
        // MEDIAS/Music4 holds the sibling directories "a" and "a.b" — a naive path-prefix match
        // on "a" would also pick up "a.b"'s song; the separator guard must keep it out
        MusicFolder folder = scanFolder(MusicFolderTestData.resolveMusic4FolderPath(), "Music4ShuffleTest");
        MediaFile parent = getByPath(folder, "a");

        List<MediaFile> songs = mediaFileService.getRandomSongsForParent(parent, 10);

        assertFalse(songs.isEmpty());
        songs.forEach(song -> assertTrue(song.getPath().startsWith("a" + File.separator),
                "song outside 'a': " + song.getPath()));
        Set<Integer> eligible = eligibleAudioIds(folder, "a" + File.separator);
        assertEquals(eligible, songs.stream().map(MediaFile::getId).collect(Collectors.toSet()));
    }

    @Test
    public void playRandomOnSubFolderPopulatesWebPlayerQueue() {
        MusicFolder folder = scanFolder(MusicFolderTestData.resolveMusicFolderPath(), "MusicShuffleTest");
        MediaFile parent = getByPath(folder, "_DIR_ Ravel");

        Player player = new Player(); // technology defaults to WEB, the path that mutates the list
        player.setId(42);
        player.setUsername("admin");
        player.setPlayQueue(new PlayQueue());

        // the UI path behind "play random" on a folder: doPlay runs removeVideoFiles on the list
        // getRandomSongsForParent returns, so that list must be mutable (deployed-instance defect:
        // UnsupportedOperationException at Collections$UnmodifiableCollection.removeIf)
        assertDoesNotThrow(() -> playQueueService.playRandom(player, parent.getId(), 3, "test-session"));

        assertEquals(3, player.getPlayQueue().getFiles().size());
    }
}
