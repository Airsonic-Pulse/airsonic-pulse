
package org.airsonic.player.service.search;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.util.ObjectUtils.isEmpty;

/*
 * Test cases related to #1139.
 * Confirming whether shuffle search can be performed correctly in MusicFolder containing special strings.
 *
 * (Since the query of getRandomAlbums consists of folder paths only,
 * this verification is easy to perform.)
 *
 * This test case is a FalsePattern for search,
 * but there may be problems with the data flow prior to creating the search index.
 */
// Default web environment (MOCK) is required: GlobalSecurityConfig.extSecurityFilterChain
// uses MvcRequestMatcher, which needs mvcHandlerMappingIntrospector — only available when a
// servlet context is bootstrapped. WebEnvironment.NONE fails ApplicationContext load with
// "No bean named '... mvcHandlerMappingIntrospector ...' available". The siblings
// IndexManagerTestCase and SearchServiceTestCase already rely on default-MOCK for the
// same reason.
@SpringBootTest
@EnableConfigurationProperties
public class SearchServiceSpecialPathTestCase {

    private List<MusicFolder> musicFolders;

    @Autowired
    private SearchService searchService;

    private List<MusicFolder> getMusicFolders() {
        if (isEmpty(musicFolders)) {
            // Use the no-id MusicFolder constructor so JPA's IDENTITY generator assigns
            // unique IDs at persist time. The original hardcoded ids (1, 2, 3) collided
            // with the music_folder rows seeded by Liquibase, producing an
            // ObjectOptimisticLockingFailure when createMusicFolder tried to write back
            // to an existing row.
            musicFolders = new ArrayList<>();
            Path basePath = MusicFolderTestData.resolveBaseMediaPath().resolve("Search").resolve("SpecialPath");
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            musicFolders.add(new MusicFolder(basePath.resolve("accessible"), "accessible", Type.MEDIA, true, now));
            musicFolders.add(new MusicFolder(basePath.resolve("accessible's"), "accessible's", Type.MEDIA, true, now));
            musicFolders.add(new MusicFolder(basePath.resolve("accessible+s"), "accessible+s", Type.MEDIA, true, now));
        }
        return musicFolders;
    }

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private MediaScannerService mediaScannerService;

    @TempDir
    private static Path airsonicHome;

    @BeforeAll
    public static void setupAll() {
        System.setProperty("airsonic.home", airsonicHome.toString());
    }

    @BeforeEach
    public void setup() {
        for (MusicFolder musicFolder : getMusicFolders()) {
            mediaFolderService.createMusicFolder(musicFolder);
        }
        TestCaseUtils.execScan(mediaScannerService);
    }

    @AfterEach
    public void tearDown() {
        for (MusicFolder musicFolder : getMusicFolders()) {
            mediaFolderService.deleteMusicFolder(musicFolder.getId());
        }
        musicFolders.clear();
        mediaFolderService.expunge();
    }

    @Test
    public void testSpecialCharactersInDirName() {

        List<MusicFolder> folders = getMusicFolders();

        // ALL Songs
        List<MediaFile> randomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE, folders);
        assertEquals(3, randomAlbums.size(), "ALL Albums ");

        // dir - accessible
        List<MusicFolder> folder01 = folders.stream()
                .filter(m -> "accessible".equals(m.getName()))
                .collect(Collectors.toList());
        randomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE, folder01);
        assertEquals(1, randomAlbums.size(), "Albums in \"accessible\" ");

        // dir - accessible's
        List<MusicFolder> folder02 = folders.stream()
                .filter(m -> "accessible's".equals(m.getName()))
                .collect(Collectors.toList());
        randomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE, folder02);
        assertEquals(1, randomAlbums.size(), "Albums in \"accessible's\" ");

        // dir - accessible+s
        List<MusicFolder> folder03 = folders.stream()
                .filter(m -> "accessible+s".equals(m.getName()))
                .collect(Collectors.toList());
        randomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE, folder03);
        assertEquals(1, randomAlbums.size(), "Albums in \"accessible+s\" ");

    }

}
