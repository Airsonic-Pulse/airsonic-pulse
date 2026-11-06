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
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.airsonic.player.domain.entity.UserRating;
import org.airsonic.player.util.FixtureTableGuardExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import jakarta.validation.ConstraintViolationException;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ExtendWith(FixtureTableGuardExtension.class)
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class UserRatingRepositoryTest {

    @Autowired
    private UserRatingRepository userRatingRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @TempDir
    private static Path tempDir;

    @TempDir
    private Path musicFolderDir;

    private MediaFile mediaFile;

    private MusicFolder testFolder;

    private final String TEST_USER_NAME = "testUserForRating";
    private final String TEST_USER_NAME_2 = "testUserForRating2";

    @BeforeAll
    public static void init() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void setup() {
        jdbcTemplate.execute("delete from user_rating");
        jdbcTemplate.execute("delete from media_file");

        // music folder
        testFolder = new MusicFolder(musicFolderDir, "name", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolderRepository.save(testFolder);

        // media file
        MediaFile baseFile = new MediaFile();
        baseFile.setFolder(testFolder);
        baseFile.setPath("userrating.wav");
        baseFile.setMediaType(MediaType.MUSIC);
        baseFile.setIndexPath("test.cue");
        baseFile.setStartPosition(MediaFile.NOT_INDEXED);
        baseFile.setCreated(Instant.now());
        baseFile.setChanged(Instant.now());
        baseFile.setLastScanned(Instant.now());
        baseFile.setChildrenLastUpdated(Instant.now());
        mediaFileRepository.save(baseFile);
        MediaFile base2File = new MediaFile();
        base2File.setFolder(testFolder);
        base2File.setMediaType(MediaType.MUSIC);
        base2File.setStartPosition(MediaFile.NOT_INDEXED);
        base2File.setCreated(Instant.now());
        base2File.setChanged(Instant.now());
        base2File.setLastScanned(Instant.now());
        base2File.setChildrenLastUpdated(Instant.now());
        base2File.setPath("userrating2.wav");
        base2File.setIndexPath("test2.cue");
        mediaFileRepository.save(base2File);
        mediaFile = mediaFileRepository.findByFolderAndPath(testFolder,"userrating.wav").get(0);

        // user
        User user = new User(TEST_USER_NAME, "rating@activeobjects.no", false, 1000L, 2000L, 3000L, Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
        userRepository.saveAndFlush(user);

        User user2 = new User(TEST_USER_NAME_2, "rating2@activeobjects.no", false, 1000L, 2000L, 3000L, Set.of(Role.ADMIN, Role.COMMENT, Role.COVERART, Role.PLAYLIST, Role.PODCAST, Role.STREAM, Role.JUKEBOX, Role.SETTINGS));
        userRepository.saveAndFlush(user2);
    }

    @AfterEach
    public void tearDown() {
        jdbcTemplate.execute("delete from user_rating");
        jdbcTemplate.execute("delete from media_file");
        musicFolderRepository.delete(testFolder);
        userRepository.deleteById(TEST_USER_NAME);
        userRepository.deleteById(TEST_USER_NAME_2);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    public void testSave(int rating) {
        UserRating userRating = new UserRating();
        userRating.setMediaFileId(mediaFile.getId());
        userRating.setUsername(TEST_USER_NAME);
        userRating.setRating(rating);
        userRatingRepository.saveAndFlush(userRating);
        assertEquals(1, userRatingRepository.count());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 6, 100})
    public void testInvalidRating(int rating) {
        UserRating userRating = new UserRating();
        userRating.setMediaFileId(mediaFile.getId());
        userRating.setUsername(TEST_USER_NAME);
        userRating.setRating(rating);
        assertThrows(ConstraintViolationException.class, () -> userRatingRepository.saveAndFlush(userRating));
        assertEquals(0, userRatingRepository.count());
    }

    @Test
    public void testDuplicateRating() {
        UserRating userRating = new UserRating();
        userRating.setMediaFileId(mediaFile.getId());
        userRating.setUsername(TEST_USER_NAME);
        userRating.setRating(1);
        userRatingRepository.saveAndFlush(userRating);
        assertEquals(1, userRatingRepository.count());
        Optional<UserRating> optUserRating = userRatingRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFile.getId());
        assertTrue(optUserRating.isPresent());
        assertEquals(1, optUserRating.get().getRating());

        UserRating userRating2 = new UserRating();
        userRating2.setMediaFileId(mediaFile.getId());
        userRating2.setUsername(TEST_USER_NAME);
        userRating2.setRating(2);
        userRatingRepository.saveAndFlush(userRating2);
        assertEquals(1, userRatingRepository.count());
        optUserRating = userRatingRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFile.getId());
        assertTrue(optUserRating.isPresent());
        assertEquals(2, optUserRating.get().getRating());
    }

    @Test
    public void testDelete() {
        UserRating userRating = new UserRating();
        userRating.setMediaFileId(mediaFile.getId());
        userRating.setUsername(TEST_USER_NAME);
        userRating.setRating(1);
        userRatingRepository.saveAndFlush(userRating);
        assertEquals(1, userRatingRepository.count());
        Optional<UserRating> optUserRating = userRatingRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFile.getId());
        assertTrue(optUserRating.isPresent());
        assertEquals(1, optUserRating.get().getRating());

        userRatingRepository.deleteByUsernameAndMediaFileId(TEST_USER_NAME, mediaFile.getId());
        assertEquals(0, userRatingRepository.count());
        optUserRating = userRatingRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFile.getId());
        assertFalse(optUserRating.isPresent());
    }

    @Test
    public void testAverageRating() {
        UserRating userRating = new UserRating();
        userRating.setMediaFileId(mediaFile.getId());
        userRating.setUsername(TEST_USER_NAME);
        userRating.setRating(1);
        userRatingRepository.saveAndFlush(userRating);
        assertEquals(1, userRatingRepository.count());
        Optional<UserRating> optUserRating = userRatingRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME, mediaFile.getId());
        assertTrue(optUserRating.isPresent());
        assertEquals(1, optUserRating.get().getRating());

        UserRating userRating2 = new UserRating();
        userRating2.setMediaFileId(mediaFile.getId());
        userRating2.setUsername(TEST_USER_NAME_2);
        userRating2.setRating(2);
        userRatingRepository.saveAndFlush(userRating2);
        assertEquals(2, userRatingRepository.count());
        optUserRating = userRatingRepository.findOptByUsernameAndMediaFileId(TEST_USER_NAME_2, mediaFile.getId());
        assertTrue(optUserRating.isPresent());
        assertEquals(2, optUserRating.get().getRating());

        assertEquals(1.5, userRatingRepository.getAverageRatingByMediaFileId(mediaFile.getId()));
    }

    @Test
    public void testAverageRatingNoRatings() {
        assertEquals(0, userRatingRepository.count());
        assertNull(userRatingRepository.getAverageRatingByMediaFileId(mediaFile.getId()));
    }

}
