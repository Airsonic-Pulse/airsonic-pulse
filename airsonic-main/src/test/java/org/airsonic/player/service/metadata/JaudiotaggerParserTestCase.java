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

import org.jaudiotagger.tag.id3.ID3v24Frame;
import org.jaudiotagger.tag.id3.ID3v24Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.id3.valuepair.TextEncoding;
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test of the ReplayGain extraction in {@link JaudiotaggerParser}.
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
}
