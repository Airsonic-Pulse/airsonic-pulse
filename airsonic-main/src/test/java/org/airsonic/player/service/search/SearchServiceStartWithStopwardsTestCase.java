
package org.airsonic.player.service.search;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.SearchCriteria;
import org.airsonic.player.domain.SearchResult;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.util.FixtureTableGuardExtension;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.util.ObjectUtils.isEmpty;

/*
 * Test cases related to #1142.
 * The filter is not properly applied when analyzing the query,
 *
 * In the process of hardening the Analyzer implementation,
 * this problem is solved side by side.
 */
@SpringBootTest
@EnableConfigurationProperties
@ExtendWith(FixtureTableGuardExtension.class)
public class SearchServiceStartWithStopwardsTestCase {

    private List<MusicFolder> musicFolders;

    @Autowired
    private SearchService searchService;

    @TempDir
    private static Path airsonicHome;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MediaScannerService mediaScannerService;

    @BeforeAll
    public static void setupAll() {
        System.setProperty("airsonic.home", airsonicHome.toString());
    }

    @BeforeEach
    public void setup() {
        MusicFolderTestData.createMusicFolders(mediaFolderService, getMusicFolders());
        TestCaseUtils.execScan(mediaScannerService);
    }

    @AfterEach
    public void tearDown() {
        // artist/album rows have no FK to music_folder; delete the scan's rows for this
        // class's folder ids before the folders themselves (#266).
        MusicFolderTestData.deleteScannedArtistsAndAlbums(jdbcTemplate, getMusicFolders());
        for (MusicFolder musicFolder : getMusicFolders()) {
            mediaFolderService.deleteMusicFolder(musicFolder.getId());
        }
        // deleteMusicFolder only soft-deletes a scanned folder; expunge hard-deletes it and
        // cascades its media_file rows (#266 — this class previously left both behind).
        mediaFolderService.expunge();
        musicFolders.clear();
    }

    private List<MusicFolder> getMusicFolders() {
        if (isEmpty(musicFolders)) {
            musicFolders = new ArrayList<>();
            Path musicDir = MusicFolderTestData.resolveBaseMediaPath().resolve("Search").resolve("StartWithStopwards");
            musicFolders.add(new MusicFolder(null, musicDir, "accessible", Type.MEDIA, true,
                    Instant.now().truncatedTo(ChronoUnit.MICROS)));
        }
        return musicFolders;
    }

    @Test
    public void testStartWithStopwards() {

        List<MusicFolder> folders = getMusicFolders();

        final SearchCriteria criteria = new SearchCriteria();
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setOffset(0);

        criteria.setQuery("will");
        SearchResult result = searchService.search(criteria, folders, IndexType.ARTIST_ID3);
        // Will hit because Airsonic's stopword is defined(#1235)
        assertEquals(1, result.getTotalHits(), "Williams hit by \"will\" ");

        criteria.setQuery("the");
        result = searchService.search(criteria, folders, IndexType.SONG);
        // XXX 3.x -> 8.x : The filter is properly applied to the input(Stopward)
        assertEquals(0, result.getTotalHits(), "Theater hit by \"the\" ");

        criteria.setQuery("willi");
        result = searchService.search(criteria, folders, IndexType.ARTIST_ID3);
        // XXX 3.x -> 8.x : Normal forward matching
        assertEquals(1, result.getTotalHits(), "Williams hit by \"Williams\" ");

        criteria.setQuery("thea");
        result = searchService.search(criteria, folders, IndexType.SONG);
        // XXX 3.x -> 8.x : Normal forward matching
        assertEquals(1, result.getTotalHits(), "Theater hit by \"thea\" ");

    }

}
