
package org.airsonic.player.service.search;

import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.domain.SearchCriteria;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.WildcardQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test case for QueryFactory.
 * These cases have the purpose of observing the current situation
 * and observing the impact of upgrading Lucene.
 */

@ExtendWith(SpringExtension.class)
@Import({ QueryFactory.class, AnalyzerFactory.class })
public class QueryFactoryTestCase {

    @Autowired
    private QueryFactory queryFactory;

    private static final String QUERY_ENG_ONLY = "ABC DEF";

    private static final String SEPA = System.getProperty("file.separator");

    private static final String PATH1 = SEPA + "var" + SEPA + "music1";
    private static final String PATH2 = SEPA + "var" + SEPA + "music2";

    private static final int FID1 = 10;
    private static final int FID2 = 20;

    private static final MusicFolder MUSIC_FOLDER1 =
            new MusicFolder(FID1, Paths.get(PATH1), "music1", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
    private static final MusicFolder MUSIC_FOLDER2 =
            new MusicFolder(FID2, Paths.get(PATH2), "music2", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));

    private static final List<MusicFolder> SINGLE_FOLDERS = Arrays.asList(MUSIC_FOLDER1);
    private static final List<MusicFolder> MULTI_FOLDERS = Arrays.asList(MUSIC_FOLDER1, MUSIC_FOLDER2);


    /*
     * XXX 3.x -> 8.x :
     * It does not change the basic functional requirements for the query.
     * However, some minor improvements are included.
     *
     *  - Use 'Or' instead of 'SpanOr'.
     *    This is suitable for 8.x document definition and query grammar.
     *    A more rigorous comparison.
     *
     *  - Removed comparison of input value and path from condition of search.
     *    It causes a false search that the user can not imagine.
     *    Originally unnecessary.
     *
     *  - mediaType and genre changed to raw string key comparison.
     *    Currently, these are "key" strings, both in the requirements and in the implementation.
     *    The legacy "normalize" is dirty code that compensates for the incomplete analytics implementation
     *    and is not necessary as long as proper key comparison can be done.
     *
     *    => Treating these strictly as keys enables DB reference.
     *       For example, can support multi-genre by creating a new genre field that implements another Tokenizer.
     *
     *  - The method for comparing ranges of numbers has changed.
     *    This is suitable for 8.x.
     */

    /*
     * The expected query strings below reflect the boost-aware output of
     * createMultiFieldWildQuery in QueryFactory: each token contributes BOTH a wildcard
     * (token*) and a 0.5×-boosted bare term, with field-specific multipliers applied when
     * IndexType declares a boost (TITLE 1.1, ALBUM 1.1). For ARTIST/ARTIST_ID3 (no
     * field-boost) the wildcard appears unboosted and the term carries (artist:abc)^0.5.
     */

    @Test
    public void testSearchArtist() throws IOException {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOffset(10);
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setQuery(QUERY_ENG_ONLY);

        Query query = queryFactory.search(criteria, SINGLE_FOLDERS, IndexType.ARTIST);
        assertEquals(
                "+((artist:abc*) ((artist:abc)^0.5) (artist:def*) ((artist:def)^0.5)) +(folder:" + PATH1 + ")",
                query.toString(), "SearchArtist");

        query = queryFactory.search(criteria, MULTI_FOLDERS, IndexType.ARTIST);
        assertEquals(
                "+((artist:abc*) ((artist:abc)^0.5) (artist:def*) ((artist:def)^0.5)) +(folder:" + PATH1
                        + " folder:" + PATH2 + ")",
                query.toString(), "SearchArtist");
    }

    @Test
    public void testSearchAlbum() throws IOException {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOffset(10);
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setQuery(QUERY_ENG_ONLY);

        Query query = queryFactory.search(criteria, SINGLE_FOLDERS, IndexType.ALBUM);
        assertEquals(
                "+(((album:abc)^0.55 artist:abc*) ((album:abc*)^1.1 (artist:abc)^0.5) "
                        + "((album:def)^0.55 artist:def*) ((album:def*)^1.1 (artist:def)^0.5)) "
                        + "+(folder:" + PATH1 + ")",
                query.toString(), "SearchAlbum");

        query = queryFactory.search(criteria, MULTI_FOLDERS, IndexType.ALBUM);
        assertEquals(
                "+(((album:abc)^0.55 artist:abc*) ((album:abc*)^1.1 (artist:abc)^0.5) "
                        + "((album:def)^0.55 artist:def*) ((album:def*)^1.1 (artist:def)^0.5)) "
                        + "+(folder:" + PATH1 + " folder:" + PATH2 + ")",
                query.toString(), "SearchAlbum");
    }

    @Test
    public void testSearchSong() throws IOException {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOffset(10);
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setQuery(QUERY_ENG_ONLY);

        Query query = queryFactory.search(criteria, SINGLE_FOLDERS, IndexType.SONG);
        assertEquals(
                "+(((title:abc)^0.55 artist:abc*) ((title:abc*)^1.1 (artist:abc)^0.5) "
                        + "((title:def)^0.55 artist:def*) ((title:def*)^1.1 (artist:def)^0.5)) "
                        + "+(folder:" + PATH1 + ")",
                query.toString(), "SearchSong");

        query = queryFactory.search(criteria, MULTI_FOLDERS, IndexType.SONG);
        assertEquals(
                "+(((title:abc)^0.55 artist:abc*) ((title:abc*)^1.1 (artist:abc)^0.5) "
                        + "((title:def)^0.55 artist:def*) ((title:def*)^1.1 (artist:def)^0.5)) "
                        + "+(folder:" + PATH1 + " folder:" + PATH2 + ")",
                query.toString(), "SearchSong");
    }

    @Test
    public void testSearchArtistId3() throws IOException {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOffset(10);
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setQuery(QUERY_ENG_ONLY);

        Query query = queryFactory.search(criteria, SINGLE_FOLDERS, IndexType.ARTIST_ID3);
        assertEquals(
                "+((artist:abc*) ((artist:abc)^0.5) (artist:def*) ((artist:def)^0.5)) +(folderId:" + FID1 + ")",
                query.toString(), "SearchArtistId3");

        query = queryFactory.search(criteria, MULTI_FOLDERS, IndexType.ARTIST_ID3);
        assertEquals(
                "+((artist:abc*) ((artist:abc)^0.5) (artist:def*) ((artist:def)^0.5)) +(folderId:" + FID1
                        + " folderId:" + FID2 + ")",
                query.toString(), "SearchArtistId3");
    }

    @Test
    public void testSearchAlbumId3() throws IOException {
        SearchCriteria criteria = new SearchCriteria();
        criteria.setOffset(10);
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setQuery(QUERY_ENG_ONLY);

        Query query = queryFactory.search(criteria, SINGLE_FOLDERS, IndexType.ALBUM_ID3);
        assertEquals(
                "+(((album:abc)^0.55 artist:abc*) ((album:abc*)^1.1 (artist:abc)^0.5) "
                        + "((album:def)^0.55 artist:def*) ((album:def*)^1.1 (artist:def)^0.5)) "
                        + "+(folderId:" + FID1 + ")",
                query.toString(), "SearchAlbumId3");

        query = queryFactory.search(criteria, MULTI_FOLDERS, IndexType.ALBUM_ID3);
        assertEquals(
                "+(((album:abc)^0.55 artist:abc*) ((album:abc*)^1.1 (artist:abc)^0.5) "
                        + "((album:def)^0.55 artist:def*) ((album:def*)^1.1 (artist:def)^0.5)) "
                        + "+(folderId:" + FID1 + " folderId:" + FID2 + ")",
                query.toString(), "SearchAlbumId3");
    }

    @Test
    public void testSearchByNameArtist() throws IOException {
        Query query = queryFactory.searchByName(FieldNames.ARTIST, QUERY_ENG_ONLY);
        assertEquals("artist:abc artist:def*", query.toString(), "SearchByNameArtist");
    }

    @Test
    public void testSearchByNameAlbum() throws IOException {
        Query query = queryFactory.searchByName(FieldNames.ALBUM, QUERY_ENG_ONLY);
        assertEquals("album:abc album:def*", query.toString(), "SearchByNameAlbum");
    }

    @Test
    public void testSearchByNameTitle() throws IOException {
        Query query = queryFactory.searchByName(FieldNames.TITLE, QUERY_ENG_ONLY);
        assertEquals("title:abc title:def*", query.toString(),"SearchByNameTitle");
    }

    @Test
    public void testGetRandomSongs() throws IOException {
        RandomSearchCriteria criteria = new RandomSearchCriteria(50, "Classic Rock",
                1900, 2000, SINGLE_FOLDERS);

        Query query = queryFactory.getRandomSongs(criteria);
        assertEquals(
                "+mediaType:MUSIC +genre:Classic Rock +year:[1900 TO 2000] +(folder:" + PATH1 + ")",
                query.toString(), ToStringBuilder.reflectionToString(criteria));

        criteria = new RandomSearchCriteria(50, "Classic Rock", 1900,
                2000, MULTI_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals(
                "+mediaType:MUSIC +genre:Classic Rock +year:[1900 TO 2000] +(folder:" + PATH1 + " folder:" + PATH2
                        + ")",
                query.toString(), ToStringBuilder.reflectionToString(criteria));

        criteria = new RandomSearchCriteria(50, "Classic Rock", null, null, MULTI_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals(
                "+mediaType:MUSIC +genre:Classic Rock +(folder:" + PATH1 + " folder:" + PATH2 + ")",
                query.toString(), ToStringBuilder.reflectionToString(criteria));

        criteria = new RandomSearchCriteria(50, "Classic Rock", 1900, null,
                MULTI_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals(
                "+mediaType:MUSIC +genre:Classic Rock +year:[1900 TO 2147483647] +(folder:" + PATH1 + " folder:" + PATH2
                        + ")",
                query.toString(), ToStringBuilder.reflectionToString(criteria));

        criteria = new RandomSearchCriteria(50, "Classic Rock", null, 2000,
                MULTI_FOLDERS);
        query = queryFactory.getRandomSongs(criteria);
        assertEquals(
                "+mediaType:MUSIC +genre:Classic Rock +year:[-2147483648 TO 2000] +(folder:" + PATH1 + " folder:" + PATH2
                        + ")",
                query.toString(), ToStringBuilder.reflectionToString(criteria));
    }

    @Test
    public void testGetRandomAlbums() {
        Query query = queryFactory.getRandomAlbums(SINGLE_FOLDERS);
        assertEquals(
                "(folder:" + PATH1 + ")", query.toString(), SINGLE_FOLDERS.stream().map(f -> f.toString()).collect(Collectors.joining(",")));

        query = queryFactory.getRandomAlbums(MULTI_FOLDERS);
        assertEquals(
                "(folder:" + PATH1 + " folder:" + PATH2 + ")", query.toString(), MULTI_FOLDERS.stream().map(f -> f.toString()).collect(Collectors.joining(",")));
    }

    @Test
    public void testGetRandomAlbumsId3() {
        Query query = queryFactory.getRandomAlbumsId3(SINGLE_FOLDERS);
        assertEquals(
                "(folderId:" + FID1 + ")", query.toString(), SINGLE_FOLDERS.stream().map(f -> f.toString()).collect(Collectors.joining(",")));

        query = queryFactory.getRandomAlbumsId3(MULTI_FOLDERS);
        assertEquals(
                "(folderId:" + FID1 + " folderId:"
                        + FID2 + ")",
                query.toString(), MULTI_FOLDERS.stream().map(f -> f.toString()).collect(Collectors.joining(",")));
    }

    // ---------------------------------------------------------------------------------------
    // #178 structural-defense contract lock (rider on #262). QueryFactory builds queries
    // structurally (BooleanQuery / BoostQuery / TermQuery / WildcardQuery) — there is no
    // QueryParser, so Lucene query-syntax operators in user input are NOT interpreted. These
    // tests feed the exact adversarial inputs #178 was about (+folder:1, *, field:value) and
    // assert the produced query is composed ONLY of those structural types. A contributor
    // re-introducing QueryParser (reopening #178) would produce a different node type and fail.
    // ---------------------------------------------------------------------------------------

    private static final List<Class<? extends Query>> ALLOWED_QUERY_TYPES = List.of(
            BooleanQuery.class, BoostQuery.class, TermQuery.class, WildcardQuery.class);

    private void assertOnlyStructuralTypes(Query query) {
        assertTrue(ALLOWED_QUERY_TYPES.contains(query.getClass()),
                "unexpected (non-structural) query node — possible QueryParser reintroduction: "
                        + query.getClass().getName() + " => " + query);
        if (query instanceof BooleanQuery bq) {
            for (BooleanClause clause : bq.clauses()) {
                assertOnlyStructuralTypes(clause.query());
            }
        } else if (query instanceof BoostQuery boost) {
            assertOnlyStructuralTypes(boost.getQuery());
        }
    }

    private SearchCriteria criteria(String query) {
        SearchCriteria c = new SearchCriteria();
        c.setOffset(0);
        c.setCount(20);
        c.setQuery(query);
        return c;
    }

    @Test
    public void testSearchStructuralForAdversarialInput() throws IOException {
        for (String adversarial : List.of("+folder:1", "*", "field:value")) {
            for (IndexType type : List.of(IndexType.ARTIST, IndexType.ALBUM, IndexType.SONG)) {
                Query query = queryFactory.search(criteria(adversarial), SINGLE_FOLDERS, type);
                assertOnlyStructuralTypes(query);
            }
        }
    }

    @Test
    public void testSearchByNameStructuralForAdversarialInput() throws IOException {
        for (String adversarial : List.of("+folder:1", "*", "field:value")) {
            assertOnlyStructuralTypes(queryFactory.searchByName(FieldNames.ARTIST, adversarial));
        }
    }

    @Test
    public void testGetRandomSongsStructuralForAdversarialGenre() throws IOException {
        for (String adversarial : List.of("+folder:1", "*", "field:value")) {
            RandomSearchCriteria rc = new RandomSearchCriteria(20, adversarial, null, null, SINGLE_FOLDERS);
            assertOnlyStructuralTypes(queryFactory.getRandomSongs(rc));
        }
    }

}
