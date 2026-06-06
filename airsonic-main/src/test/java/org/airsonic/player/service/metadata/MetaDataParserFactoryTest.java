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
 */
package org.airsonic.player.service.metadata;

import com.google.common.io.Resources;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Routing tests for {@link MetaDataParserFactory#getParser(Path)}. Pins the parser-selection
 * contract by extension so a future change to either parser's {@code isApplicable} or to the
 * {@code @Order} hierarchy can't silently shift a format off the path users expect.
 */
public class MetaDataParserFactoryTest {

    @TempDir
    Path tmp;

    private MetaDataParserFactory factory;

    @BeforeEach
    public void setUp() {
        // Order mirrors Spring's @Order ascending: JaudiotaggerParser (0) first,
        // then FFmpegParser (100) as the unconditional regular-file fallback.
        // DefaultMetaDataParser (200) is intentionally excluded — it never wins
        // selection in production because FFmpegParser already accepts every regular file.
        // The null MediaFolderService is intentional: only isApplicable and getRawMetaData
        // are exercised here, neither of which touches the folder service.
        factory = new MetaDataParserFactory(List.of(
                new JaudiotaggerParser(null),
                new FFmpegParser()));
    }

    private Path file(String name) throws Exception {
        Path p = tmp.resolve(name);
        Files.createFile(p);
        return p;
    }

    // ----- positive routing: extensions that must select JaudiotaggerParser -----

    @Test
    public void testMp3RoutesToJaudiotagger() throws Exception {
        assertEquals(JaudiotaggerParser.class, factory.getParser(file("a.mp3")).getClass());
    }

    @Test
    public void testFlacRoutesToJaudiotagger() throws Exception {
        assertEquals(JaudiotaggerParser.class, factory.getParser(file("a.flac")).getClass());
    }

    @Test
    public void testOggRoutesToJaudiotagger() throws Exception {
        assertEquals(JaudiotaggerParser.class, factory.getParser(file("a.ogg")).getClass());
    }

    @Test
    public void testM4aRoutesToJaudiotagger() throws Exception {
        assertEquals(JaudiotaggerParser.class, factory.getParser(file("a.m4a")).getClass());
    }

    /**
     * The fix for #257 — promoting {@code m4b} to {@code JaudiotaggerParser.applicableFormats}.
     * Before this change, .m4b audiobooks routed to FFmpegParser and missed every 13.2.x
     * JaudiotaggerParser improvement (sortName, contributors, MP4 atom RG, etc.).
     */
    @Test
    public void testM4bRoutesToJaudiotagger() throws Exception {
        assertEquals(JaudiotaggerParser.class, factory.getParser(file("a.m4b")).getClass());
    }

    // ----- negative routing: extensions that must NOT select JaudiotaggerParser -----

    @Test
    public void testMpcStaysOnFFmpeg() throws Exception {
        assertEquals(FFmpegParser.class, factory.getParser(file("a.mpc")).getClass());
    }

    /**
     * Lock test: {@code .opus} must continue to route through {@link FFmpegParser}, NOT
     * {@link JaudiotaggerParser}. The seed for #257 conjectured that jaudiotagger 3.0.1
     * could read Opus because it reads Ogg; empirical probing (see #258 and the analysis
     * doc) showed that {@code AudioFileIO.read(File)} throws
     * {@code CannotReadException: No Reader associated with this extension:opus} because
     * jaudiotagger 3.0.1 has no OPUS entry in {@code SupportedFileFormat} and no reader
     * registered for the suffix. Even when forced past the extension check, the
     * underlying {@code OggInfoReader} rejects Opus content because it validates the
     * Vorbis capture pattern. Adding {@code "opus"} to {@code applicableFormats} would
     * therefore break .opus scanning rather than fix it. Opus R128 reaches users through
     * FFmpegParser instead — tracked in #258, resolved via #226 PR1.
     *
     * <p>If this test ever fails because a future diff added {@code "opus"} to
     * {@code applicableFormats}, revisit #258 first — the upstream gap must be closed
     * (or the library replaced) before that promotion is safe.
     */
    @Test
    public void testOpusStaysOnFFmpegBecauseJaudiotagger301HasNoOpusReader_issue258() throws Exception {
        assertEquals(FFmpegParser.class, factory.getParser(file("a.opus")).getClass(),
                "opus must route to FFmpegParser — jaudiotagger 3.0.1 has no Opus reader (see #258)");
    }

    // ----- general contract -----

    @Test
    public void testRegularFileWithUnknownExtensionFallsThroughToFFmpeg() throws Exception {
        // Any regular file Jaudiotagger does not claim must reach FFmpegParser — confirms
        // the @Order(100) catch-all is intact.
        assertEquals(FFmpegParser.class, factory.getParser(file("a.unknown")).getClass());
    }

    @Test
    public void testNonExistentFileReturnsNull() {
        // Neither parser is applicable to a non-existent path; getParser returns null
        // rather than picking arbitrarily. Both isApplicable predicates require
        // Files.isRegularFile, so a missing path falls off the chain entirely.
        Path missing = tmp.resolve("nope.mp3");
        assertFalse(Files.exists(missing));
        assertNull(factory.getParser(missing));
    }

    // ----- end-to-end: the m4b fixture parses through Jaudiotagger after the fix -----

    /**
     * End-to-end confirmation that the m4b promotion actually flows: the project's
     * {@code MEDIAS/m4baudiobook/m4btest.m4b} fixture, which previously routed to
     * FFmpegParser, must now route to JaudiotaggerParser AND produce the iTunes-style
     * MP4 tag values the underlying reader exposes.
     */
    @Test
    public void testM4bFixtureParsesThroughJaudiotaggerAfterPromotion() throws Exception {
        // Classpath-resolved so the test passes equally under `mvnd test`, an IDE run,
        // or anywhere the working directory isn't airsonic-main/ — mirrors the loader
        // pattern used elsewhere (see MediaScannerServiceTestCase, PipeStreamsTest).
        Path fixture = Paths.get(Resources.getResource("MEDIAS/m4baudiobook/m4btest.m4b").toURI());
        assertTrue(Files.isRegularFile(fixture),
                "fixture missing: " + fixture.toAbsolutePath());

        MetaDataParser parser = factory.getParser(fixture);
        assertNotNull(parser, "factory must return a parser for the m4b fixture");
        assertEquals(JaudiotaggerParser.class, parser.getClass(),
                "m4b fixture must now route through JaudiotaggerParser, not FFmpegParser");

        // getRawMetaData() avoids the path-guessing fallbacks in getMetaData() and so
        // does not require a MediaFolderService — null is safe in the constructor above.
        MetaData metaData = parser.getRawMetaData(fixture);
        assertNotNull(metaData);
        assertEquals("m4btestartist", metaData.getArtist());
        assertEquals("m4btestbook", metaData.getTitle());
        assertEquals("m4btest", metaData.getAlbumName());
    }
}
