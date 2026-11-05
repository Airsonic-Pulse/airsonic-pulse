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
package org.airsonic.player.util;

import org.apache.commons.text.StringEscapeUtils;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test of {@link StringUtil}.
 *
 * @author Sindre Mehus
 */
public class StringUtilTestCase {

    @Test
    public void testToHtml() {
        assertEquals(null, StringEscapeUtils.escapeHtml4(null));
        assertEquals("", StringEscapeUtils.escapeHtml4(""));
        assertEquals(" ", StringEscapeUtils.escapeHtml4(" "));
        assertEquals("q &amp; a", StringEscapeUtils.escapeHtml4("q & a"));
        assertEquals("q &amp; a &lt;&gt; b", StringEscapeUtils.escapeHtml4("q & a <> b"));
    }

    @Test
    public void testGetMimeType() {
        assertEquals("audio/mpeg", StringUtil.getMimeType("mp3"), "Error in getMimeType()");
        assertEquals("audio/mpeg", StringUtil.getMimeType(".mp3"), "Error in getMimeType()");
        assertEquals("audio/mpeg", StringUtil.getMimeType(".MP3"), "Error in getMimeType()");
        assertEquals("image/webp", StringUtil.getMimeType(".webp"), "Error in getMimeType()");
        assertEquals("application/octet-stream", StringUtil.getMimeType("koko"), "Error in getMimeType()");
        assertEquals("application/octet-stream", StringUtil.getMimeType(""), "Error in getMimeType()");
        assertEquals("application/octet-stream", StringUtil.getMimeType(null), "Error in getMimeType()");
    }

    @Test
    public void testFormatBytes() {
        Locale locale = Locale.ENGLISH;
        assertEquals("918 B", StringUtil.formatBytes(918L, locale), "Error in formatBytes()");
        assertEquals("1023 B", StringUtil.formatBytes(1023L, locale), "Error in formatBytes()");
        assertEquals("1 KB", StringUtil.formatBytes(1024L, locale), "Error in formatBytes()");
        assertEquals("96 KB", StringUtil.formatBytes(98765L, locale), "Error in formatBytes()");
        assertEquals("1024 KB", StringUtil.formatBytes(1048575L, locale), "Error in formatBytes()");
        assertEquals("1.2 MB", StringUtil.formatBytes(1238476L, locale), "Error in formatBytes()");
        assertEquals("3.50 GB", StringUtil.formatBytes(3758096384L, locale), "Error in formatBytes()");
        assertEquals("410.00 TB", StringUtil.formatBytes(450799767388160L, locale), "Error in formatBytes()");
        assertEquals("4413.43 TB", StringUtil.formatBytes(4852617603375432L, locale), "Error in formatBytes()");

        locale = new Locale("no", "", "");
        assertEquals("918 B", StringUtil.formatBytes(918L, locale), "Error in formatBytes()");
        assertEquals("1023 B", StringUtil.formatBytes(1023L, locale), "Error in formatBytes()");
        assertEquals("1 KB", StringUtil.formatBytes(1024L, locale), "Error in formatBytes()");
        assertEquals("96 KB", StringUtil.formatBytes(98765L, locale), "Error in formatBytes()");
        assertEquals("1024 KB", StringUtil.formatBytes(1048575L, locale), "Error in formatBytes()");
        assertEquals("1,2 MB", StringUtil.formatBytes(1238476L, locale), "Error in formatBytes()");
        assertEquals("3,50 GB", StringUtil.formatBytes(3758096384L, locale), "Error in formatBytes()");
        assertEquals("410,00 TB", StringUtil.formatBytes(450799767388160L, locale), "Error in formatBytes()");
        assertEquals("4413,43 TB", StringUtil.formatBytes(4852617603375432L, locale), "Error in formatBytes()");
    }

    @Test
    public void testFormatDuration() {
        assertEquals("0:00", StringUtil.formatDuration(0), "Error in formatDuration()");
        assertEquals("0:05", StringUtil.formatDuration(5000), "Error in formatDuration()");
        assertEquals("0:05.300", StringUtil.formatDuration(5300), "Error in formatDuration()");
        assertEquals("0:10", StringUtil.formatDuration(10000), "Error in formatDuration()");
        assertEquals("0:59", StringUtil.formatDuration(59000), "Error in formatDuration()");
        assertEquals("1:00", StringUtil.formatDuration(60000), "Error in formatDuration()");
        assertEquals("1:01", StringUtil.formatDuration(61000), "Error in formatDuration()");
        assertEquals("1:10", StringUtil.formatDuration(70000), "Error in formatDuration()");
        assertEquals("10:00", StringUtil.formatDuration(600000), "Error in formatDuration()");
        assertEquals("45:50", StringUtil.formatDuration(2750000), "Error in formatDuration()");
        assertEquals("1:23:45", StringUtil.formatDuration(5025000), "Error in formatDuration()");
        assertEquals("83:45", StringUtil.formatDuration(5025000, false), "Error in formatDuration()");
        assertEquals("2:01:40", StringUtil.formatDuration(7300000), "Error in formatDuration()");
        assertEquals("121:40.001", StringUtil.formatDuration(7300001, false), "Error in formatDuration()");
        assertEquals("2:01:40.002", StringUtil.formatDuration(7300002), "Error in formatDuration()");
        assertEquals("2:01:40.040", StringUtil.formatDuration(7300040), "Error in formatDuration()");
    }

    @Test
    public void testSplit() {
        doTestSplit("u2 rem \"greatest hits\"", "u2", "rem", "greatest hits");
        doTestSplit("u2", "u2");
        doTestSplit("u2 rem", "u2", "rem");
        doTestSplit(" u2  \t rem ", "u2", "rem");
        doTestSplit("u2 \"rem\"", "u2", "rem");
        doTestSplit("u2 \"rem", "u2", "\"rem");
        doTestSplit("\"", "\"");

        assertEquals(0, StringUtil.split("").length);
        assertEquals(0, StringUtil.split(" ").length);
        assertEquals(0, StringUtil.split(null).length);
    }

    private void doTestSplit(String input, String... expected) {
        String[] actual = StringUtil.split(input);
        assertEquals(expected.length, actual.length, "Wrong number of elements.");

        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i], "Wrong criteria.");
        }
    }

    @Test
    public void testParseLocale() {
        assertEquals(new Locale("en"), StringUtil.parseLocale("en"), "Error in parseLocale()");
        assertEquals(new Locale("en"), StringUtil.parseLocale("en_"), "Error in parseLocale()");
        assertEquals(new Locale("en"), StringUtil.parseLocale("en__"), "Error in parseLocale()");
        assertEquals(new Locale("en", "US"), StringUtil.parseLocale("en_US"), "Error in parseLocale()");
        assertEquals(new Locale("en", "US", "WIN"), StringUtil.parseLocale("en_US_WIN"), "Error in parseLocale()");
        assertEquals(new Locale("en", "", "WIN"), StringUtil.parseLocale("en__WIN"), "Error in parseLocale()");
    }

    @Test
    public void testUtf8Hex() throws Exception {
        doTestUtf8Hex(null);
        doTestUtf8Hex("");
        doTestUtf8Hex("a");
        doTestUtf8Hex("abcdefg");
        doTestUtf8Hex("abc������");
        doTestUtf8Hex("NRK P3 � FK Fotball");
    }

    private void doTestUtf8Hex(String s) throws Exception {
        assertEquals(s, StringUtil.utf8HexDecode(StringUtil.utf8HexEncode(s)), "Error in utf8hex.");
    }

    @Test
    public void testGetUrlFile() {
        assertEquals("foo.mp3", StringUtil.getUrlFile("http://www.asdf.com/foo.mp3"), "Error in getUrlFile().");
        assertEquals("foo.mp3", StringUtil.getUrlFile("http://www.asdf.com/bar/foo.mp3"), "Error in getUrlFile().");
        assertEquals("foo", StringUtil.getUrlFile("http://www.asdf.com/bar/foo"), "Error in getUrlFile().");
        assertEquals("foo.mp3", StringUtil.getUrlFile("http://www.asdf.com/bar/foo.mp3?a=1&b=2"),
                "Error in getUrlFile().");
        assertNull(StringUtil.getUrlFile("not a url"), "Error in getUrlFile().");
        assertNull(StringUtil.getUrlFile("http://www.asdf.com"), "Error in getUrlFile().");
        assertNull(StringUtil.getUrlFile("http://www.asdf.com/"), "Error in getUrlFile().");
        assertNull(StringUtil.getUrlFile("http://www.asdf.com/foo/"), "Error in getUrlFile().");
    }

    @Test
    public void testFileSystemSafe() {
        assertEquals("foo", StringUtil.fileSystemSafe("foo"), "Error in fileSystemSafe()");
        assertEquals("foo.mp3", StringUtil.fileSystemSafe("foo.mp3"), "Error in fileSystemSafe()");
        assertEquals("foo-bar", StringUtil.fileSystemSafe("foo/bar"), "Error in fileSystemSafe()");
        assertEquals("foo-bar", StringUtil.fileSystemSafe("foo\\bar"), "Error in fileSystemSafe()");
        assertEquals("foo-bar", StringUtil.fileSystemSafe("foo:bar"), "Error in fileSystemSafe()");
    }

    @Test
    public void testRemoveMarkup() {
        assertEquals("foo", StringUtil.removeMarkup("<b>foo</b>"), "Error in removeMarkup()");
        assertEquals("foobar", StringUtil.removeMarkup("<b>foo</b>bar"), "Error in removeMarkup()");
        assertEquals("foo", StringUtil.removeMarkup("foo"), "Error in removeMarkup()");
        assertEquals("foo", StringUtil.removeMarkup("<b>foo"), "Error in removeMarkup()");
        assertEquals(null, StringUtil.removeMarkup(null), "Error in removeMarkup()");
    }

}
