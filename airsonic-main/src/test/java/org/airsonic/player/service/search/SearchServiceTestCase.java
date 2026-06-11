
package org.airsonic.player.service.search;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.ParamSearchResult;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.domain.SearchCriteria;
import org.airsonic.player.domain.SearchResult;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.service.AlbumService;
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
import org.subsonic.restapi.ArtistID3;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@EnableConfigurationProperties
public class SearchServiceTestCase {

    @Autowired
    private AlbumService albumService;
    private final MetricRegistry metrics = new MetricRegistry();
    @Autowired
    private MusicFolderRepository musicFolderRepository;
    @Autowired
    private SearchService searchService;
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

    // Holds the persisted MusicFolder references so cleanup can use the IDs JPA assigned at
    // persist time. The original code called MusicFolderTestData.getTestMusicFolders() in
    // @AfterEach too, which returned fresh id-less instances; deleteMusicFolder(null) is a
    // no-op, so the rows leaked across test classes and broke any subsequent INSERT against
    // the same path on matrix DBs with idx_music_folder_path unique constraint.
    private List<MusicFolder> persistedFolders;

    @BeforeEach
    public void setup() {
        persistedFolders = new ArrayList<>();
        for (MusicFolder musicFolder : MusicFolderTestData.getTestMusicFolders()) {
            mediaFolderService.createMusicFolder(musicFolder);
            persistedFolders.add(musicFolder);
        }
        TestCaseUtils.execScan(mediaScannerService);
    }

    @AfterEach
    public void cleanup() {
        for (MusicFolder folder : persistedFolders) {
            mediaFolderService.deleteMusicFolder(folder.getId());
        }
        mediaFolderService.expunge();
    }

    @Test
    public void testSearchTypical() {

        /*
         * A simple test that is expected to easily detect API syntax differences when
         * updating lucene.
         * Complete route coverage and data coverage in this case alone are not
         * conscious.
         */

        List<MusicFolder> allMusicFolders = musicFolderRepository.findByDeleted(false);
        assertEquals(4, allMusicFolders.size());

        // *** testSearch() ***

        String query = "Sarah Walker";
        final SearchCriteria searchCriteria = new SearchCriteria();
        searchCriteria.setQuery(query);
        searchCriteria.setCount(Integer.MAX_VALUE);
        searchCriteria.setOffset(0);

        /*
         * _ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble
         * Should find any version of Lucene.
         */
        SearchResult result = searchService.search(searchCriteria, allMusicFolders,
                IndexType.ALBUM);
        assertEquals(1,
                result.getTotalHits(), "(0) Specify '" + query + "' as query, total Hits is");
        assertEquals(0,
                result.getArtists().size(),
                "(1) Specify artist '" + query + "' as query. Artist SIZE is");
        assertEquals(0,
                result.getAlbums().size(),
                "(2) Specify artist '" + query + "' as query. Album SIZE is");
        assertEquals(1,
                result.getMediaFiles().size(),
                "(3) Specify artist '" + query + "' as query, MediaFile SIZE is");
        assertEquals(MediaType.ALBUM, result.getMediaFiles().get(0).getMediaType(), "(4) ");
        assertEquals(
                "_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble",
                result.getMediaFiles().get(0).getArtist(),
                "(5) Specify artist '" + query + "' as query, and get a album. Name is ");
        assertEquals(
                "_ID3_ALBUM_ Ravel - Chamber Music With Voice",
                result.getMediaFiles().get(0).getAlbumName(),
                "(6) Specify artist '" + query + "' as query, and get a album. Name is ");

        /*
         * _ID3_ALBUM_ Ravel - Chamber Music With Voice
         * Should find any version of Lucene.
         */
        query = "music";
        searchCriteria.setQuery(query);
        result = searchService.search(searchCriteria, allMusicFolders, IndexType.ALBUM_ID3);
        assertEquals(1,
                result.getTotalHits(), "Specify '" + query + "' as query, total Hits is");
        assertEquals(
                0, result.getArtists().size(),
                "(7) Specify '" + query + "' as query, and get a song. Artist SIZE is ");
        assertEquals(
                1, result.getAlbums().size(),
                "(8) Specify '" + query + "' as query, and get a song. Album SIZE is ");
        assertEquals(
                0,
                result.getMediaFiles().size(),
                "(9) Specify '" + query + "' as query, and get a song. MediaFile SIZE is ");
        assertEquals(
                "_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble",
                result.getAlbums().get(0).getArtist(),
                "(9) Specify '" + query + "' as query, and get a album. Name is ");
        assertEquals(
                "_ID3_ALBUM_ Ravel - Chamber Music With Voice",
                result.getAlbums().get(0).getName(),
                "(10) Specify '" + query + "' as query, and get a album. Name is ");

        /*
         * _ID3_ALBUM_ Ravel - Chamber Music With Voice
         * Should find any version of Lucene.
         */
        query = "Ravel - Chamber Music";
        searchCriteria.setQuery(query);
        result = searchService.search(searchCriteria, allMusicFolders, IndexType.SONG);
        assertEquals(2,
                result.getTotalHits(), "(11) Specify album '" + query + "' as query, total Hits is");
        assertEquals(0,
                result.getArtists().size(),
                "(12) Specify album '" + query + "', and get a song. Artist SIZE is");
        assertEquals(0,
                result.getAlbums().size(),
                "(13) Specify album '" + query + "', and get a song. Album SIZE is");
        assertEquals(
                2, result.getMediaFiles().size(),
                "(14) Specify album '" + query + "', and get a song. MediaFile SIZE is");

        /*
         * The result is not sort, so the album can be arrive in any order. So we didn't
         * have AssertJ or Hamcrest.
         * We use a test before test, but we really use hamcrest!
         */
        if (result.getMediaFiles().get(0).getTitle().startsWith("01")) {
            assertEquals("01 - Gaspard de la Nuit - i. Ondine",
                    result.getMediaFiles().get(0).getTitle(),
                    "(15) Specify album '" + query + "', and get songs. The first song is ");
            assertEquals("02 - Gaspard de la Nuit - ii. Le Gibet",
                    result.getMediaFiles().get(1).getTitle(),
                    "(16) Specify album '" + query + "', and get songs. The second song is ");

            // else we test in reverse order.
        } else {
            assertEquals("01 - Gaspard de la Nuit - i. Ondine",
                    result.getMediaFiles().get(1).getTitle(),
                    "(15) Specify album '" + query + "', and get songs. The first song is ");
            assertEquals(
                    "02 - Gaspard de la Nuit - ii. Le Gibet",
                    result.getMediaFiles().get(0).getTitle(),
                    "(16) Specify album '" + query + "', and get songs. The second song is ");

        }
        // *** testSearchByName() ***

        /*
         * _ID3_ALBUM_ Sackcloth 'n' Ashes
         * Should be 1 in Lucene 3.0(Because Single quate is not a delimiter).
         */
        query = "Sackcloth 'n' Ashes";
        ParamSearchResult<Album> albumResult = searchService.searchByName(query, 0,
                Integer.MAX_VALUE, allMusicFolders, Album.class);
        assertEquals(
                1,
                albumResult.getItems().size(),
                "(17) Specify album name '" + query + "' as the name, and get an album.");
        assertEquals(
                "_ID3_ALBUM_ Sackcloth 'n' Ashes", albumResult.getItems().get(0).getName(),
                "(18) Specify '" + query + "' as the name, The album name is ");
        assertEquals(
                1L,
                albumResult.getItems().stream()
                        .filter(r -> "_ID3_ALBUM_ Sackcloth \'n\' Ashes".equals(r.getName()))
                        .count(),
                "(19) Whether the acquired album contains data of the specified album name");

        /*
         * Should be 0 in Lucene 3.0(Since the slash is not a delimiter).
         */
        query = "lker/Nash";
        ParamSearchResult<ArtistID3> artistId3Result = searchService.searchByName(query, 0,
                Integer.MAX_VALUE, allMusicFolders, ArtistID3.class);
        assertEquals(0,
                artistId3Result.getItems().size(),
                "(20) Specify '" + query + "' as the name, and get an artist.");
        ParamSearchResult<Artist> artistResult = searchService.searchByName(query, 0,
                Integer.MAX_VALUE, allMusicFolders, Artist.class);

        /*
         * // XXX 3.x -> 8.x :
         * Hit 'Nash*' as ​​the slash becomes a delimiter.
         */
        assertEquals(1,
                artistResult.getItems().size(),
                "(21) Specify '" + query + "' as the name, and get an artist.");

        // *** testGetRandomSongs() ***

        /*
         * Regardless of the Lucene version,
         * RandomSearchCriteria can specify null and means the maximum range.
         * 11 should be obtainable.
         */
        RandomSearchCriteria randomSearchCriteria = new RandomSearchCriteria(Integer.MAX_VALUE, // count
                null, // genre,
                null, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );
        List<MediaFile> allRandomSongs = searchService.getRandomSongs(randomSearchCriteria);
        assertEquals(
                11,
                allRandomSongs.size(),
                "(22) Specify MAX_VALUE as the upper limit, and randomly acquire songs.");

        /*
         * Regardless of the Lucene version,
         * 7 should be obtainable.
         */
        randomSearchCriteria = new RandomSearchCriteria(Integer.MAX_VALUE, // count
                null, // genre,
                1900, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );
        allRandomSongs = searchService.getRandomSongs(randomSearchCriteria);
        assertEquals(7, allRandomSongs.size(),
                "(23) Specify 1900 as 'fromYear', and randomly acquire songs.");

        /*
         * Regardless of the Lucene version,
         * It should be 0 because it is a non-existent genre.
         */
        randomSearchCriteria = new RandomSearchCriteria(Integer.MAX_VALUE, // count
                "Chamber Music", // genre,
                null, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );
        allRandomSongs = searchService.getRandomSongs(randomSearchCriteria);
        assertEquals(0, allRandomSongs.size(),
                "(24) Specify music as 'genre', and randomly acquire songs.");

        /*
         * Genre including blank.
         * Regardless of the Lucene version, It should be 2.
         */
        randomSearchCriteria = new RandomSearchCriteria(Integer.MAX_VALUE, // count
                "Baroque Instrumental", // genre,
                null, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );
        allRandomSongs = searchService.getRandomSongs(randomSearchCriteria);
        assertEquals(2, allRandomSongs.size(),
                "(25) Search by specifying genres including spaces and hyphens.");

        // *** testGetRandomAlbums() ***

        /*
         * Acquisition of maximum number(5).
         */
        List<Album> allAlbums = albumService.getAlphabeticalAlbums(true, true, allMusicFolders);
        assertEquals(5, allAlbums.size(), "(26) Get all albums with Dao.");
        List<MediaFile> allRandomAlbums = searchService.getRandomAlbums(Integer.MAX_VALUE,
                allMusicFolders);
        assertEquals(5, allRandomAlbums.size(),
                "(27) Specify Integer.MAX_VALUE as the upper limit, and randomly acquire albums(file struct).");

        /*
         * Acquisition of maximum number(5).
         */
        List<Album> allRandomAlbumsId3 = searchService.getRandomAlbumsId3(Integer.MAX_VALUE,
                allMusicFolders);
        assertEquals(5, allRandomAlbumsId3.size(),
                "(28) Specify Integer.MAX_VALUE as the upper limit, and randomly acquire albums(ID3).");

        /*
         * Total is 4.
         */
        query = "ID 3 ARTIST";
        searchCriteria.setQuery(query);
        result = searchService.search(searchCriteria, allMusicFolders, IndexType.ARTIST_ID3);
        assertEquals(4, result.getTotalHits(), "(29) Specify '" + query + "', total Hits is");
        assertEquals(4, result.getArtists().size(),
                "(30) Specify '" + query + "', and get an artists. Artist SIZE is ");
        assertEquals(0, result.getAlbums().size(),
                "(31) Specify '" + query + "', and get a artists. Album SIZE is ");
        assertEquals(0, result.getMediaFiles().size(),
                "(32) Specify '" + query + "', and get a artists. MediaFile SIZE is ");

        /*
         * Three hits to the artist.
         * ALBUMARTIST is not registered with these.
         * Therefore, the registered value of ARTIST is substituted in ALBUMARTIST.
         */
        long count = result.getArtists().stream()
                .filter(a -> a.getName().startsWith("_ID3_ARTIST_")).count();
        assertEquals(3L, count, "(33) Artist whose name contains \\\"_ID3_ARTIST_\\\" is 3 records.");

        /*
         * The structure of "01 - Sonata Violin & Cello I. Allegro.ogg"
         * ARTIST -> _ID3_ARTIST_ Sarah Walker/Nash Ensemble
         * ALBUMARTIST -> _ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble
         * (The result must not contain duplicates. And ALBUMARTIST must be returned
         * correctly.)
         */
        count = result.getArtists().stream()
                .filter(a -> a.getName().startsWith("_ID3_ALBUMARTIST_")).count();
        assertEquals(1L, count, "(34) Artist whose name is \"_ID3_ARTIST_\" is 1 records.");

        /*
         * Below is a simple loop test.
         * How long is the total time?
         */
        int countForEachMethod = 500;
        String[] randomWords4Search = createRandomWords(countForEachMethod);
        String[] randomWords4SearchByName = createRandomWords(countForEachMethod);

        Timer globalTimer = metrics
                .timer(MetricRegistry.name(SearchServiceTestCase.class, "Timer.global"));
        final Timer.Context globalTimerContext = globalTimer.time();

        System.out.println("--- Random search (" + countForEachMethod * 5 + " times) ---");

        // testSearch()
        Arrays.stream(randomWords4Search).forEach(w -> {
            searchCriteria.setQuery(w);
            searchService.search(searchCriteria, allMusicFolders, IndexType.ALBUM);
        });

        // testSearchByName()
        Arrays.stream(randomWords4SearchByName).forEach(w -> {
            searchService.searchByName(w, 0, Integer.MAX_VALUE, allMusicFolders, Artist.class);
        });

        // testGetRandomSongs()
        RandomSearchCriteria criteria = new RandomSearchCriteria(Integer.MAX_VALUE, // count
                null, // genre,
                null, // fromYear
                null, // toYear
                allMusicFolders // musicFolders
        );
        for (int i = 0; i < countForEachMethod; i++) {
            searchService.getRandomSongs(criteria);
        }

        // testGetRandomAlbums()
        for (int i = 0; i < countForEachMethod; i++) {
            searchService.getRandomAlbums(Integer.MAX_VALUE, allMusicFolders);
        }

        // testGetRandomAlbumsId3()
        for (int i = 0; i < countForEachMethod; i++) {
            searchService.getRandomAlbumsId3(Integer.MAX_VALUE, allMusicFolders);
        }

        globalTimerContext.stop();

        /*
         * Whether or not IndexReader is exhausted.
         */
        query = "Sarah Walker";
        searchCriteria.setQuery(query);
        result = searchService.search(searchCriteria, allMusicFolders, IndexType.ALBUM);
        assertEquals(0,
                result.getArtists().size(), "(35) Can the normal case be implemented.");
        assertEquals(0,
                result.getAlbums().size(), "(36) Can the normal case be implemented.");
        assertEquals(1,
                result.getMediaFiles().size(), "(37) Can the normal case be implemented.");
        assertEquals(MediaType.ALBUM,
                result.getMediaFiles().get(0).getMediaType(),
                "(38) Can the normal case be implemented.");
        assertEquals(
                "_ID3_ALBUMARTIST_ Sarah Walker/Nash Ensemble",
                result.getMediaFiles().get(0).getArtist(),
                "(39) Can the normal case be implemented.");

        System.out.println("--- SUCCESS ---");

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS).convertDurationsTo(TimeUnit.MILLISECONDS).build();
        reporter.report();

        System.out.println("End. ");
    }

    private static String[] createRandomWords(int count) {
        String[] randomStrings = new String[count];
        Random random = new Random();
        for (int i = 0; i < count; i++) {
            char[] word = new char[random.nextInt(8) + 3];
            for (int j = 0; j < word.length; j++) {
                word[j] = (char) ('a' + random.nextInt(26));
            }
            randomStrings[i] = new String(word);
        }
        return randomStrings;
    }

}
