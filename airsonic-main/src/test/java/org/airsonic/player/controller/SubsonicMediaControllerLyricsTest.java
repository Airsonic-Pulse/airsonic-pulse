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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.StructuredLyricsLine;
import org.junit.jupiter.api.Test;
import org.subsonic.restapi.StructuredLyrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link SubsonicMediaController#buildStructuredLyrics} — the getLyricsBySongId
 * response mapping (#140): synced lyrics emit per-line {@code start} (ms) with {@code synced=true},
 * unsynced lyrics split the flat blob with {@code synced=false}.
 */
class SubsonicMediaControllerLyricsTest {

    @Test
    void buildStructuredLyrics_emitsSyncedLinesWithStart() {
        Lyrics lyrics = new Lyrics("Line A\nLine B", 1, "file");
        lyrics.setSynced(true);
        lyrics.getLines().add(new StructuredLyricsLine(lyrics, 0, 1000L, "Line A"));
        lyrics.getLines().add(new StructuredLyricsLine(lyrics, 1, 2500L, "Line B"));

        StructuredLyrics result = SubsonicMediaController.buildStructuredLyrics("Artist", "Title", lyrics);

        assertTrue(result.isSynced());
        assertEquals("Artist", result.getDisplayArtist());
        assertEquals("Title", result.getDisplayTitle());
        assertEquals(2, result.getLine().size());
        assertEquals("Line A", result.getLine().get(0).getValue());
        assertEquals(1000L, result.getLine().get(0).getStart());
        assertEquals("Line B", result.getLine().get(1).getValue());
        assertEquals(2500L, result.getLine().get(1).getStart());
    }

    @Test
    void buildStructuredLyrics_unsyncedSplitsBlobWithoutStart() {
        // Unsynced (embedded tag / legacy cache): synced=false, lines split from the blob, no start.
        Lyrics lyrics = new Lyrics("First line\nSecond line", 2, "tag");

        StructuredLyrics result = SubsonicMediaController.buildStructuredLyrics("A", "T", lyrics);

        assertFalse(result.isSynced());
        assertEquals(2, result.getLine().size());
        assertEquals("First line", result.getLine().get(0).getValue());
        assertNull(result.getLine().get(0).getStart());
        assertNull(result.getLine().get(1).getStart());
    }

    @Test
    void buildStructuredLyrics_syncedFlagButNoLinesFallsBackToBlob() {
        // Defensive: a synced flag with an empty line list must not emit an empty structured block;
        // it falls back to the unsynced blob split.
        Lyrics lyrics = new Lyrics("Only blob", 3, "file");
        lyrics.setSynced(true);

        StructuredLyrics result = SubsonicMediaController.buildStructuredLyrics("A", "T", lyrics);

        assertFalse(result.isSynced());
        assertEquals(1, result.getLine().size());
        assertEquals("Only blob", result.getLine().get(0).getValue());
    }

    @Test
    void buildStructuredLyrics_returnsNullForBlankOrMissing() {
        assertNull(SubsonicMediaController.buildStructuredLyrics("A", "T", null));
        assertNull(SubsonicMediaController.buildStructuredLyrics("A", "T", new Lyrics("   ", 4, "file")));
    }
}
