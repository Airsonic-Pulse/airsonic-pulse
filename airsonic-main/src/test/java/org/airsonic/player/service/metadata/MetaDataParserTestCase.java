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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service.metadata;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFolderService;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test of {@link MetaDataParser}.
 *
 * @author Sindre Mehus
 */
public class MetaDataParserTestCase {

    @Test
    public void testRemoveTrackNumberFromTitle() {

        MetaDataParser parser = new MetaDataParser() {
            @Override
            public MetaData getRawMetaData(Path file) {
                return null;
            }

            @Override
            public void setMetaData(MediaFile file, MetaData metaData) {
            }

            @Override
            public boolean isEditingSupported() {
                return false;
            }

            @Override
            MediaFolderService getMediaFolderService() {
                return null;
            }

            @Override
            public boolean isApplicable(Path path) {
                return false;
            }
        };

        assertEquals("", parser.removeTrackNumberFromTitle("", null));
        assertEquals("kokos", parser.removeTrackNumberFromTitle("kokos", null));
        assertEquals("01 kokos", parser.removeTrackNumberFromTitle("01 kokos", null));
        assertEquals("01 - kokos", parser.removeTrackNumberFromTitle("01 - kokos", null));
        assertEquals("01-kokos", parser.removeTrackNumberFromTitle("01-kokos", null));
        assertEquals("01 - kokos", parser.removeTrackNumberFromTitle("01 - kokos", null));
        assertEquals("99 - kokos", parser.removeTrackNumberFromTitle("99 - kokos", null));
        assertEquals("99.- kokos", parser.removeTrackNumberFromTitle("99.- kokos", null));
        assertEquals("01 kokos", parser.removeTrackNumberFromTitle(" 01 kokos", null));
        assertEquals("400 years", parser.removeTrackNumberFromTitle("400 years", null));
        assertEquals("49ers", parser.removeTrackNumberFromTitle("49ers", null));
        assertEquals("01", parser.removeTrackNumberFromTitle("01", null));
        assertEquals("01", parser.removeTrackNumberFromTitle("01 ", null));
        assertEquals("01", parser.removeTrackNumberFromTitle(" 01 ", null));
        assertEquals("01", parser.removeTrackNumberFromTitle(" 01", null));

        assertEquals("", parser.removeTrackNumberFromTitle("", 1));
        assertEquals("kokos", parser.removeTrackNumberFromTitle("01 kokos", 1));
        assertEquals("kokos", parser.removeTrackNumberFromTitle("01 - kokos", 1));
        assertEquals("kokos", parser.removeTrackNumberFromTitle("01-kokos", 1));
        assertEquals("kokos", parser.removeTrackNumberFromTitle("99 - kokos", 99));
        assertEquals("kokos", parser.removeTrackNumberFromTitle("99.- kokos", 99));
        assertEquals("01 kokos", parser.removeTrackNumberFromTitle("01 kokos", 2));
        assertEquals("1 kokos", parser.removeTrackNumberFromTitle("1 kokos", 2));
        assertEquals("50 years", parser.removeTrackNumberFromTitle("50 years", 1));
        assertEquals("years", parser.removeTrackNumberFromTitle("50 years", 50));
        assertEquals("15 Step", parser.removeTrackNumberFromTitle("15 Step", 1));
        assertEquals("Step", parser.removeTrackNumberFromTitle("15 Step", 15));

        assertEquals("49ers", parser.removeTrackNumberFromTitle("49ers", 1));
        assertEquals("49ers", parser.removeTrackNumberFromTitle("49ers", 49));
        assertEquals("01", parser.removeTrackNumberFromTitle("01", 1));
        assertEquals("01", parser.removeTrackNumberFromTitle("01 ", 1));
        assertEquals("01", parser.removeTrackNumberFromTitle(" 01 ", 1));
        assertEquals("01", parser.removeTrackNumberFromTitle(" 01", 1));
        assertEquals("01", parser.removeTrackNumberFromTitle("01", 2));
        assertEquals("01", parser.removeTrackNumberFromTitle("01 ", 2));
        assertEquals("01", parser.removeTrackNumberFromTitle(" 01 ", 2));
        assertEquals("01", parser.removeTrackNumberFromTitle(" 01", 2));
    }

    @Test
    public void testParseBpm() {

        MetaDataParser parser = new MetaDataParser() {
            @Override
            public MetaData getRawMetaData(Path file) {
                return null;
            }

            @Override
            public void setMetaData(MediaFile file, MetaData metaData) {
            }

            @Override
            public boolean isEditingSupported() {
                return false;
            }

            @Override
            MediaFolderService getMediaFolderService() {
                return null;
            }

            @Override
            public boolean isApplicable(Path path) {
                return false;
            }
        };

        assertEquals(Integer.valueOf(120), parser.parseBpm("120"));
        assertEquals(Integer.valueOf(121), parser.parseBpm("120.6"));
        assertEquals(Integer.valueOf(120), parser.parseBpm("120.4"));
        assertEquals(Integer.valueOf(98), parser.parseBpm(" 98 "));
        assertNull(parser.parseBpm(""));
        assertNull(parser.parseBpm("   "));
        assertNull(parser.parseBpm("fast"));
        assertNull(parser.parseBpm(null));
        assertNull(parser.parseBpm("0"));
        assertNull(parser.parseBpm("-5"));
        assertNull(parser.parseBpm("NaN"));
        assertNull(parser.parseBpm("Infinity"));
        assertNull(parser.parseBpm("99999999999"));
    }

    @Test
    public void testParseReplayGain() {

        MetaDataParser parser = new MetaDataParser() {
            @Override
            public MetaData getRawMetaData(Path file) {
                return null;
            }

            @Override
            public void setMetaData(MediaFile file, MetaData metaData) {
            }

            @Override
            public boolean isEditingSupported() {
                return false;
            }

            @Override
            MediaFolderService getMediaFolderService() {
                return null;
            }

            @Override
            public boolean isApplicable(Path path) {
                return false;
            }
        };

        assertEquals(Double.valueOf(-6.5), parser.parseReplayGain("-6.50 dB"));
        assertEquals(Double.valueOf(-7.2), parser.parseReplayGain("-7.20"));
        assertEquals(Double.valueOf(3.4), parser.parseReplayGain("3.40 DB"));
        assertEquals(Double.valueOf(0.988553), parser.parseReplayGain("0.988553"));
        assertNull(parser.parseReplayGain("NaN"));
        assertNull(parser.parseReplayGain("Infinity"));
        assertNull(parser.parseReplayGain(""));
        assertNull(parser.parseReplayGain("   "));
        assertNull(parser.parseReplayGain("loud"));
        assertNull(parser.parseReplayGain(null));
        assertNull(parser.parseReplayGain("dB"));
        assertNull(parser.parseReplayGain(" dB "));
    }
}