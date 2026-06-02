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

import org.airsonic.player.domain.Contributor;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTMCL;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.id3.valuepair.TextEncoding;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test of the ReplayGain and multi-value tag-field extraction in {@link JaudiotaggerParser}.
 */
public class JaudiotaggerParserTestCase {

    private static ID3v24Tag tagWithTxxx(String description, String value) {
        ID3v24Tag tag = new ID3v24Tag();
        ID3v24Frame frame = new ID3v24Frame("TXXX");
        frame.setBody(new FrameBodyTXXX(TextEncoding.ISO_8859_1, description, value));
        tag.addFrame(frame);
        return tag;
    }

    @Test
    public void testGetReplayGainFieldFromId3v2Txxx() {
        ID3v24Tag tag = tagWithTxxx("REPLAYGAIN_TRACK_GAIN", "-7.20 dB");
        assertEquals("-7.20 dB", JaudiotaggerParser.getReplayGainField(tag, JaudiotaggerParser.RG_TRACK_GAIN));
    }

    @Test
    public void testGetReplayGainFieldDescriptionMatchIsCaseInsensitive() {
        ID3v24Tag tag = tagWithTxxx("replaygain_track_gain", "-6.50 dB");
        assertEquals("-6.50 dB", JaudiotaggerParser.getReplayGainField(tag, JaudiotaggerParser.RG_TRACK_GAIN));
    }

    @Test
    public void testGetReplayGainFieldMissingFrameReturnsNull() {
        ID3v24Tag tag = tagWithTxxx("REPLAYGAIN_TRACK_GAIN", "-7.20 dB");
        assertNull(JaudiotaggerParser.getReplayGainField(tag, JaudiotaggerParser.RG_ALBUM_PEAK));
    }

    @Test
    public void testGetReplayGainFieldFromVorbisComment() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.setField("REPLAYGAIN_TRACK_GAIN", "-7.50 dB");
        assertEquals("-7.50 dB", JaudiotaggerParser.getReplayGainField(tag, JaudiotaggerParser.RG_TRACK_GAIN));
    }

    private static Mp4Tag mp4TagWithItunesFreeform(String descriptorLower, String value) {
        Mp4Tag tag = new Mp4Tag();
        // jaudiotagger's 4-string Mp4TagReverseDnsField(id, issuer, descriptor, content) sets
        // the field's id directly to the first argument (the iTunes reverse-DNS atom name);
        // that's what Mp4Tag.getFields(String) matches against and what
        // JaudiotaggerParser.getReplayGainField composes its lookup key for.
        String id = JaudiotaggerParser.MP4_ITUNES_FREEFORM_PREFIX + descriptorLower;
        Mp4TagReverseDnsField field = new Mp4TagReverseDnsField(id, "com.apple.iTunes",
                descriptorLower, value);
        tag.addField(field);
        return tag;
    }

    @Test
    public void testGetReplayGainFieldFromMp4FreeformAtom() {
        Mp4Tag tag = mp4TagWithItunesFreeform("replaygain_track_gain", "-7.50 dB");
        assertEquals("-7.50 dB",
                JaudiotaggerParser.getReplayGainField(tag, JaudiotaggerParser.RG_TRACK_GAIN));
    }

    @Test
    public void testGetReplayGainFieldMp4MissingAtomReturnsNull() {
        Mp4Tag tag = mp4TagWithItunesFreeform("replaygain_track_gain", "-7.50 dB");
        assertNull(JaudiotaggerParser.getReplayGainField(tag, JaudiotaggerParser.RG_ALBUM_PEAK));
    }

    @Test
    public void testParseR128GainQ78ReferenceShiftAt0() {
        // Q7.8 of 0 → 0/256 + 5 = 5.0 dB (a track already at -23 LUFS reads as +5 dB in RG terms)
        assertEquals(Double.valueOf(5.0), JaudiotaggerParser.parseR128GainQ78("0"));
    }

    @Test
    public void testParseR128GainQ78PositiveValues() {
        // Q7.8 of 256 = 1 dB raw → 1 + 5 = 6 dB; -512 = -2 raw → -2 + 5 = 3 dB
        assertEquals(Double.valueOf(6.0), JaudiotaggerParser.parseR128GainQ78("256"));
        assertEquals(Double.valueOf(3.0), JaudiotaggerParser.parseR128GainQ78("-512"));
        // Trimming
        assertEquals(Double.valueOf(6.0), JaudiotaggerParser.parseR128GainQ78("  256  "));
    }

    @Test
    public void testParseR128GainQ78MalformedReturnsNull() {
        assertNull(JaudiotaggerParser.parseR128GainQ78(null));
        assertNull(JaudiotaggerParser.parseR128GainQ78(""));
        assertNull(JaudiotaggerParser.parseR128GainQ78("not-an-int"));
        assertNull(JaudiotaggerParser.parseR128GainQ78("-7.50 dB"));
    }

    @Test
    public void testParseR128GainQ78OutOfIntRangeReturnsNull() {
        // Integer.parseInt rejects values outside [Integer.MIN_VALUE, Integer.MAX_VALUE] with
        // NumberFormatException — the catch in parseR128GainQ78 normalizes that to null.
        assertNull(JaudiotaggerParser.parseR128GainQ78("999999999999"));
        assertNull(JaudiotaggerParser.parseR128GainQ78("-999999999999"));
    }

    @Test
    public void testParseTrackGainPrefersRgWhenBothPresent() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.setField(JaudiotaggerParser.RG_TRACK_GAIN, "-6.50 dB");
        tag.setField(JaudiotaggerParser.R128_TRACK_GAIN, "256"); // would be 6.0 dB after shift
        JaudiotaggerParser parser = new JaudiotaggerParser(null);
        assertEquals(Double.valueOf(-6.5), parser.parseTrackGain(tag));
    }

    @Test
    public void testParseTrackGainFallsBackToR128WhenOnlyR128() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.setField(JaudiotaggerParser.R128_TRACK_GAIN, "0"); // 5.0 dB after shift
        JaudiotaggerParser parser = new JaudiotaggerParser(null);
        assertEquals(Double.valueOf(5.0), parser.parseTrackGain(tag));
    }

    @Test
    public void testParseAlbumGainPrefersRgWhenBothPresent() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.setField(JaudiotaggerParser.RG_ALBUM_GAIN, "-4.25 dB");
        tag.setField(JaudiotaggerParser.R128_ALBUM_GAIN, "256");
        JaudiotaggerParser parser = new JaudiotaggerParser(null);
        assertEquals(Double.valueOf(-4.25), parser.parseAlbumGain(tag));
    }

    @Test
    public void testParseAlbumGainFallsBackToR128WhenOnlyR128() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.setField(JaudiotaggerParser.R128_ALBUM_GAIN, "-512"); // 3.0 dB after shift
        JaudiotaggerParser parser = new JaudiotaggerParser(null);
        assertEquals(Double.valueOf(3.0), parser.parseAlbumGain(tag));
    }

    @Test
    public void testParseTrackGainNeitherTagPresentReturnsNull() {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        JaudiotaggerParser parser = new JaudiotaggerParser(null);
        assertNull(parser.parseTrackGain(tag));
        assertNull(parser.parseAlbumGain(tag));
    }

    @Test
    public void testGetAllTagFieldsReturnsMultipleGenreValues() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.addField(FieldKey.GENRE, "Rock");
        tag.addField(FieldKey.GENRE, "Metal");
        assertEquals(List.of("Rock", "Metal"), JaudiotaggerParser.getAllTagFields(tag, FieldKey.GENRE));
    }

    @Test
    public void testGetAllTagFieldsAbsentReturnsEmpty() {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        assertEquals(List.of(), JaudiotaggerParser.getAllTagFields(tag, FieldKey.GENRE));
    }

    @Test
    public void testGetContributorsExtractsSingleRoleFromVorbis() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.addField(FieldKey.COMPOSER, "John Williams");
        assertEquals(List.of(new Contributor("composer", null, "John Williams")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsExtractsMultipleRolesFromVorbis() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.addField(FieldKey.COMPOSER, "John Williams");
        tag.addField(FieldKey.LYRICIST, "Bernie Taupin");
        tag.addField(FieldKey.CONDUCTOR, "Leonard Bernstein");
        tag.addField(FieldKey.PRODUCER, "Rick Rubin");
        tag.addField(FieldKey.MIXER, "Andy Wallace");
        // Asserts both presence and the table-declaration order: composer, lyricist,
        // conductor are followed by producer, mixer because that's the order in
        // JaudiotaggerParser.CONTRIBUTOR_ROLES.
        assertEquals(List.of(
                new Contributor("composer", null, "John Williams"),
                new Contributor("lyricist", null, "Bernie Taupin"),
                new Contributor("conductor", null, "Leonard Bernstein"),
                new Contributor("producer", null, "Rick Rubin"),
                new Contributor("mixer", null, "Andy Wallace")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsMultipleValuesForOneRole() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.addField(FieldKey.COMPOSER, "John Williams");
        tag.addField(FieldKey.COMPOSER, "Hans Zimmer");
        assertEquals(List.of(
                new Contributor("composer", null, "John Williams"),
                new Contributor("composer", null, "Hans Zimmer")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsLeavesSubRoleNullForCleanFieldKeys() throws Exception {
        // Clean-FieldKey roles (composer, lyricist, conductor, …) never carry an instrument —
        // subRole is exclusive to performer credits sourced from TMCL / Vorbis PERFORMER.
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.addField(FieldKey.COMPOSER, "John Williams");
        tag.addField(FieldKey.LYRICIST, "Bernie Taupin");
        List<Contributor> contributors = JaudiotaggerParser.getContributors(tag);
        assertEquals(2, contributors.size());
        assertNull(contributors.get(0).subRole());
        assertNull(contributors.get(1).subRole());
    }

    @Test
    public void testGetContributorsAbsentReturnsEmpty() {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        assertEquals(List.of(), JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsExtractsFromId3v24() {
        ID3v24Tag tag = new ID3v24Tag();
        try {
            tag.setField(FieldKey.COMPOSER, "John Williams");
            tag.setField(FieldKey.LYRICIST, "Bernie Taupin");
        } catch (Exception x) {
            throw new AssertionError(x);
        }
        assertEquals(List.of(
                new Contributor("composer", null, "John Williams"),
                new Contributor("lyricist", null, "Bernie Taupin")),
                JaudiotaggerParser.getContributors(tag));
    }

    private static ID3v24Tag tagWithTmcl(String... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("pairs must be (instrument, name)+");
        }
        ID3v24Tag tag = new ID3v24Tag();
        FrameBodyTMCL body = new FrameBodyTMCL();
        for (int i = 0; i < pairs.length; i += 2) {
            body.addPair(pairs[i], pairs[i + 1]);
        }
        ID3v24Frame frame = new ID3v24Frame("TMCL");
        frame.setBody(body);
        tag.addFrame(frame);
        return tag;
    }

    @Test
    public void testGetContributorsExtractsPerformersFromId3v2Tmcl() {
        ID3v24Tag tag = tagWithTmcl("Guitar", "Jimi Hendrix", "Bass", "Noel Redding");
        assertEquals(List.of(
                new Contributor("performer", "Guitar", "Jimi Hendrix"),
                new Contributor("performer", "Bass", "Noel Redding")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsSplitsCommaDelimitedPerformersForOneInstrument() {
        // ID3v2.4 spec allows the value of a TMCL pair to be a comma-delimited list of
        // performers all sharing the same instrument.
        ID3v24Tag tag = tagWithTmcl("Vocals", "John Lennon, Paul McCartney");
        assertEquals(List.of(
                new Contributor("performer", "Vocals", "John Lennon"),
                new Contributor("performer", "Vocals", "Paul McCartney")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsAlongsidePerformersOnId3v24() {
        // Both clean-FieldKey roles AND TMCL performers populate on the same tag — clean
        // roles emit first (in CONTRIBUTOR_ROLES order), performers appended after.
        ID3v24Tag tag = tagWithTmcl("Guitar", "Eric Clapton");
        try {
            tag.setField(FieldKey.COMPOSER, "George Harrison");
        } catch (Exception x) {
            throw new AssertionError(x);
        }
        assertEquals(List.of(
                new Contributor("composer", null, "George Harrison"),
                new Contributor("performer", "Guitar", "Eric Clapton")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsId3v24WithoutTmclEmitsNoPerformers() {
        ID3v24Tag tag = new ID3v24Tag();
        try {
            tag.setField(FieldKey.COMPOSER, "George Harrison");
        } catch (Exception x) {
            throw new AssertionError(x);
        }
        assertEquals(List.of(new Contributor("composer", null, "George Harrison")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsParsesNameInstrumentFromVorbisPerformer() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.addField(FieldKey.PERFORMER, "Eric Clapton (Guitar)");
        assertEquals(List.of(new Contributor("performer", "Guitar", "Eric Clapton")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsBareVorbisPerformerHasNullSubRole() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.addField(FieldKey.PERFORMER, "Eric Clapton");
        assertEquals(List.of(new Contributor("performer", null, "Eric Clapton")),
                JaudiotaggerParser.getContributors(tag));
    }

    @Test
    public void testGetContributorsExtractsMultipleVorbisPerformers() throws Exception {
        VorbisCommentTag tag = VorbisCommentTag.createNewTag();
        tag.addField(FieldKey.PERFORMER, "Eric Clapton (Guitar)");
        tag.addField(FieldKey.PERFORMER, "Jack Bruce (Bass)");
        tag.addField(FieldKey.PERFORMER, "Ginger Baker (Drums)");
        assertEquals(List.of(
                new Contributor("performer", "Guitar", "Eric Clapton"),
                new Contributor("performer", "Bass", "Jack Bruce"),
                new Contributor("performer", "Drums", "Ginger Baker")),
                JaudiotaggerParser.getContributors(tag));
    }
}
