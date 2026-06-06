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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test of {@link FFmpegParser}'s scalar field extraction. Tests feed synthesized
 * ffprobe JSON into {@link FFmpegParser#populateFromJson} directly to avoid the subprocess
 * dependency — the JSON shape mirrors what {@code ffprobe -print_format json -show_format
 * -show_streams} produces. Resolves the #258 premise: confirms the 13.2.x parser-fed
 * OpenSubsonic fields (sortName, bpm, ReplayGain four-field, Opus R128 fallback, compilation,
 * releaseDate, originalReleaseDate, discSubtitle, MusicBrainz IDs) all populate via the
 * FFmpeg path that .opus and other Jaudiotagger-unsupported audio formats route through.
 */
public class FFmpegParserTestCase {

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Builds an ffprobe-shaped JsonNode with the given {@code /format/tags/*} entries.
     * Insertion order is preserved so tests can observe which key wins in fallback chains
     * (Jackson's tree treats sibling keys as a LinkedHashMap-backed ObjectNode).
     */
    private JsonNode probeJson(Map<String, String> formatTags) {
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> format = new LinkedHashMap<>();
        format.put("duration", "180.0");
        format.put("bit_rate", "192000");
        format.put("tags", formatTags);
        root.put("format", format);
        return mapper.valueToTree(root);
    }

    private static Map<String, String> tags(String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("kv must be (key, value)+");
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    private MetaData parse(Map<String, String> formatTags) {
        MetaData metaData = new MetaData();
        new FFmpegParser().populateFromJson(probeJson(formatTags), metaData);
        return metaData;
    }

    // ----- sortName trio -----

    @Test
    public void testSortNameFromId3HyphenatedKey() {
        // ID3v2 TSOT → ffprobe surfaces as "title-sort"
        assertEquals("Aria", parse(tags("title-sort", "Aria")).getSortName());
    }

    @Test
    public void testSortNameFromVorbisTitleSort() {
        assertEquals("Aria", parse(tags("TITLESORT", "Aria")).getSortName());
    }

    @Test
    public void testAlbumSortNameFromAlbumSort() {
        assertEquals("Goldberg", parse(tags("album-sort", "Goldberg")).getAlbumSortName());
    }

    @Test
    public void testArtistSortNameFromHyphenated() {
        assertEquals("Bach", parse(tags("album-artist-sort", "Bach")).getArtistSortName());
    }

    @Test
    public void testArtistSortNameFromUnderscoreUnderscoreVariant() {
        assertEquals("Bach", parse(tags("album_artist_sort", "Bach")).getArtistSortName());
    }

    @Test
    public void testSortNamesAbsentReturnNull() {
        MetaData m = parse(tags());
        assertNull(m.getSortName());
        assertNull(m.getAlbumSortName());
        assertNull(m.getArtistSortName());
    }

    // ----- bpm -----

    @Test
    public void testBpmFromTbpm() {
        assertEquals(Integer.valueOf(128), parse(tags("TBPM", "128")).getBpm());
    }

    @Test
    public void testBpmFromVorbisBpm() {
        assertEquals(Integer.valueOf(128), parse(tags("BPM", "128")).getBpm());
    }

    @Test
    public void testBpmAbsentReturnsNull() {
        assertNull(parse(tags()).getBpm());
    }

    // ----- compilation -----

    @Test
    public void testCompilationFromIs1() {
        assertEquals(Boolean.TRUE, parse(tags("compilation", "1")).getCompilation());
    }

    @Test
    public void testCompilationFromTcmp() {
        assertEquals(Boolean.TRUE, parse(tags("TCMP", "1")).getCompilation());
    }

    @Test
    public void testCompilationFromIs0() {
        assertEquals(Boolean.FALSE, parse(tags("compilation", "0")).getCompilation());
    }

    @Test
    public void testCompilationAbsentReturnsNull() {
        assertNull(parse(tags()).getCompilation());
    }

    // ----- releaseDate vs year -----

    @Test
    public void testReleaseDateFromDate() {
        // Year-only date both populates year (existing behavior) AND surfaces as raw releaseDate.
        MetaData m = parse(tags("date", "2004"));
        assertEquals("2004", m.getReleaseDate());
        assertEquals(Integer.valueOf(2004), m.getYear());
    }

    @Test
    public void testReleaseDateCarriesFullDate() {
        // YYYY-MM-DD: releaseDate keeps the raw value; year is the leading 4 digits via
        // parseYear (shared with JaudiotaggerParser). No parser drift for full-date tags.
        MetaData m = parse(tags("date", "2004-03-15"));
        assertEquals("2004-03-15", m.getReleaseDate());
        assertEquals(Integer.valueOf(2004), m.getYear());
    }

    @Test
    public void testReleaseDateAbsentReturnsNull() {
        MetaData m = parse(tags());
        assertNull(m.getReleaseDate());
        assertNull(m.getYear());
    }

    // ----- originalReleaseDate -----

    @Test
    public void testOriginalReleaseDateFromOriginalreleasedate() {
        assertEquals("1981", parse(tags("originalreleasedate", "1981")).getOriginalReleaseDate());
    }

    @Test
    public void testOriginalReleaseDateFallsBackToOriginalyear() {
        assertEquals("1981", parse(tags("originalyear", "1981")).getOriginalReleaseDate());
    }

    @Test
    public void testOriginalReleaseDateFallsBackToTdor() {
        assertEquals("1981", parse(tags("TDOR", "1981")).getOriginalReleaseDate());
    }

    @Test
    public void testOriginalReleaseDateFallsBackToTory() {
        assertEquals("1981", parse(tags("TORY", "1981")).getOriginalReleaseDate());
    }

    @Test
    public void testOriginalReleaseDatePrefersFirstInChain() {
        // originalreleasedate beats originalyear beats TDOR beats TORY.
        assertEquals("1981", parse(tags(
                "originalreleasedate", "1981",
                "originalyear", "1982",
                "TDOR", "1983",
                "TORY", "1984")).getOriginalReleaseDate());
    }

    @Test
    public void testOriginalReleaseDateAbsentReturnsNull() {
        assertNull(parse(tags()).getOriginalReleaseDate());
    }

    // ----- discSubtitle -----

    @Test
    public void testDiscSubtitleFromVorbisKey() {
        assertEquals("Disc 1: Aria", parse(tags("DISCSUBTITLE", "Disc 1: Aria")).getDiscSubtitle());
    }

    @Test
    public void testDiscSubtitleFromTsst() {
        assertEquals("Side B", parse(tags("TSST", "Side B")).getDiscSubtitle());
    }

    @Test
    public void testDiscSubtitleAbsentReturnsNull() {
        assertNull(parse(tags()).getDiscSubtitle());
    }

    // ----- MusicBrainz IDs -----

    @Test
    public void testMusicBrainzReleaseIdFromUnderscoredUpper() {
        assertEquals("uuid-album", parse(tags("MUSICBRAINZ_ALBUMID", "uuid-album")).getMusicBrainzReleaseId());
    }

    @Test
    public void testMusicBrainzReleaseIdFromSpacedKey() {
        assertEquals("uuid-album", parse(tags("MusicBrainz Album Id", "uuid-album")).getMusicBrainzReleaseId());
    }

    @Test
    public void testMusicBrainzRecordingIdFromUnderscoredUpper() {
        assertEquals("uuid-track", parse(tags("MUSICBRAINZ_TRACKID", "uuid-track")).getMusicBrainzRecordingId());
    }

    @Test
    public void testMusicBrainzArtistIdFromUnderscoredUpper() {
        assertEquals("uuid-artist", parse(tags("MUSICBRAINZ_ALBUMARTISTID", "uuid-artist")).getMusicBrainzArtistId());
    }

    @Test
    public void testMusicBrainzIdsAbsentReturnNull() {
        MetaData m = parse(tags());
        assertNull(m.getMusicBrainzReleaseId());
        assertNull(m.getMusicBrainzRecordingId());
        assertNull(m.getMusicBrainzArtistId());
    }

    // ----- ReplayGain four-field (gains via parseTrackGain/parseAlbumGain; peaks direct) -----

    @Test
    public void testReplayGainFourFieldPopulates() {
        MetaData m = parse(tags(
                "REPLAYGAIN_TRACK_GAIN", "-7.20 dB",
                "REPLAYGAIN_ALBUM_GAIN", "-5.10 dB",
                "REPLAYGAIN_TRACK_PEAK", "0.988553",
                "REPLAYGAIN_ALBUM_PEAK", "0.995123"));
        assertEquals(Double.valueOf(-7.2), m.getReplayGainTrackGain());
        assertEquals(Double.valueOf(-5.1), m.getReplayGainAlbumGain());
        assertEquals(Double.valueOf(0.988553), m.getReplayGainTrackPeak());
        assertEquals(Double.valueOf(0.995123), m.getReplayGainAlbumPeak());
    }

    @Test
    public void testReplayGainAbsentReturnsAllNull() {
        MetaData m = parse(tags());
        assertNull(m.getReplayGainTrackGain());
        assertNull(m.getReplayGainAlbumGain());
        assertNull(m.getReplayGainTrackPeak());
        assertNull(m.getReplayGainAlbumPeak());
    }

    // ----- RG-then-R128 precedence (mirrors #205 PR-A's JaudiotaggerParser semantics) -----

    @Test
    public void testTrackGainRgOnly() {
        // REPLAYGAIN_TRACK_GAIN present, R128_TRACK_GAIN absent → trackGain from RG, no conversion.
        MetaData m = parse(tags("REPLAYGAIN_TRACK_GAIN", "-7.20 dB"));
        assertEquals(Double.valueOf(-7.2), m.getReplayGainTrackGain());
    }

    @Test
    public void testTrackGainR128OnlyAppliesShift() {
        // R128_TRACK_GAIN = 0 (Q7.8) → 5.0 dB after the +5 reference shift. The Opus path.
        MetaData m = parse(tags("R128_TRACK_GAIN", "0"));
        assertEquals(Double.valueOf(5.0), m.getReplayGainTrackGain());
    }

    @Test
    public void testTrackGainR128PositiveAppliesShift() {
        // 256 Q7.8 = 1.0 dB raw → 6.0 dB after shift.
        MetaData m = parse(tags("R128_TRACK_GAIN", "256"));
        assertEquals(Double.valueOf(6.0), m.getReplayGainTrackGain());
    }

    @Test
    public void testTrackGainRgWinsWhenBothPresent() {
        // RG present AND R128 present → RG wins; R128 ignored.
        MetaData m = parse(tags(
                "REPLAYGAIN_TRACK_GAIN", "-6.50 dB",
                "R128_TRACK_GAIN", "256"));     // would be 6.0 dB after shift
        assertEquals(Double.valueOf(-6.5), m.getReplayGainTrackGain());
    }

    @Test
    public void testTrackGainMalformedRgDoesNotFallThroughToR128() {
        // Present-but-unparseable RG returns null without R128 consultation. The operator's
        // authored RG tag takes precedence even when it's broken.
        MetaData m = parse(tags(
                "REPLAYGAIN_TRACK_GAIN", "loud",
                "R128_TRACK_GAIN", "256"));
        assertNull(m.getReplayGainTrackGain());
    }

    @Test
    public void testAlbumGainRgOnly() {
        MetaData m = parse(tags("REPLAYGAIN_ALBUM_GAIN", "-4.25 dB"));
        assertEquals(Double.valueOf(-4.25), m.getReplayGainAlbumGain());
    }

    @Test
    public void testAlbumGainR128OnlyAppliesShift() {
        // -512 Q7.8 = -2.0 dB raw → 3.0 dB after shift.
        MetaData m = parse(tags("R128_ALBUM_GAIN", "-512"));
        assertEquals(Double.valueOf(3.0), m.getReplayGainAlbumGain());
    }

    @Test
    public void testAlbumGainRgWinsWhenBothPresent() {
        MetaData m = parse(tags(
                "REPLAYGAIN_ALBUM_GAIN", "-4.25 dB",
                "R128_ALBUM_GAIN", "256"));
        assertEquals(Double.valueOf(-4.25), m.getReplayGainAlbumGain());
    }

    @Test
    public void testAlbumGainMalformedRgDoesNotFallThroughToR128() {
        MetaData m = parse(tags(
                "REPLAYGAIN_ALBUM_GAIN", "loud",
                "R128_ALBUM_GAIN", "256"));
        assertNull(m.getReplayGainAlbumGain());
    }

    // ----- Opus end-to-end (closes #258 premise) -----

    /**
     * Synthesized ffprobe output for an Opus file with R128 tags. Mirrors the shape produced by
     * {@code ffprobe -v quiet -print_format json -show_format -show_streams} against a real
     * tagged .opus file: Opus codec stream, Vorbis-comment-flavored tag keys in
     * {@code /format/tags}, R128_TRACK_GAIN and R128_ALBUM_GAIN as Q7.8 integers. This is
     * exactly the case that #258 documents as dead code through JaudiotaggerParser — the
     * test asserts that all the 13.2.x fields populate via FFmpegParser, including the
     * R128-derived gains.
     */
    @Test
    public void testOpusFileWithR128AndMusicBrainzTagsEndToEnd() {
        // Note: the existing FFmpegParser reads albumArtist via getData("album_artist") with
        // lower/upper/capitalize variations — it does NOT match the Vorbis-canonical
        // "ALBUMARTIST" form (no separator). Real .opus files typically use ALBUMARTIST, so
        // the existing albumArtist extraction has a Vorbis blind spot. That's a PRE-EXISTING
        // gap (out of scope for #226 PR1, which is about the new 13.2.x fields); this test
        // uses "ALBUM_ARTIST" so albumArtist still populates and the test stays focused on
        // the R128 / new-field surface.
        MetaData m = parse(tags(
                "ARTIST", "Daft Punk",
                "ALBUM", "Discovery",
                "TITLE", "One More Time",
                "ALBUM_ARTIST", "Daft Punk",
                "DATE", "2001",
                "GENRE", "Electronic",
                "TBPM", "123",
                "TITLESORT", "One More Time",
                "ALBUMSORT", "Discovery",
                "DISCSUBTITLE", "Vinyl Side A",
                "MUSICBRAINZ_ALBUMID", "alb-uuid",
                "MUSICBRAINZ_TRACKID", "trk-uuid",
                "MUSICBRAINZ_ALBUMARTISTID", "art-uuid",
                "R128_TRACK_GAIN", "256",     // 6.0 dB after shift
                "R128_ALBUM_GAIN", "-512",    // 3.0 dB after shift
                "REPLAYGAIN_TRACK_PEAK", "0.998"));
        // Standard fields that already worked
        assertEquals("Daft Punk", m.getArtist());
        assertEquals("Discovery", m.getAlbumName());
        assertEquals("One More Time", m.getTitle());
        assertEquals("Daft Punk", m.getAlbumArtist());
        assertEquals("Electronic", m.getGenre());
        // New 13.2.x fields, all from this PR
        assertEquals(Integer.valueOf(123), m.getBpm());
        assertEquals("One More Time", m.getSortName());
        assertEquals("Discovery", m.getAlbumSortName());
        assertEquals("Vinyl Side A", m.getDiscSubtitle());
        assertEquals("alb-uuid", m.getMusicBrainzReleaseId());
        assertEquals("trk-uuid", m.getMusicBrainzRecordingId());
        assertEquals("art-uuid", m.getMusicBrainzArtistId());
        // R128 → RG-equivalent dB, the actual #258 resolution
        assertEquals(Double.valueOf(6.0), m.getReplayGainTrackGain());
        assertEquals(Double.valueOf(3.0), m.getReplayGainAlbumGain());
        assertEquals(Double.valueOf(0.998), m.getReplayGainTrackPeak());
        // releaseDate raw + year integer both from "DATE"
        assertEquals("2001", m.getReleaseDate());
        assertEquals(Integer.valueOf(2001), m.getYear());
    }

    // ----- getDataAny — first non-null wins -----

    @Test
    public void testGetDataAnyReturnsFirstNonNull() {
        JsonNode node = probeJson(tags("k2", "v2", "k3", "v3"));
        assertEquals("v2", FFmpegParser.getDataAny(node, "k1", "k2", "k3"));
    }

    @Test
    public void testGetDataAnyAllAbsentReturnsNull() {
        JsonNode node = probeJson(tags());
        assertNull(FFmpegParser.getDataAny(node, "k1", "k2"));
    }
}
