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
package org.airsonic.player.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of {@link Genres#split(String, String)} — the shared splitter used by both the
 * media_file.genres packing path and the Child.genres[] response wiring.
 */
public class GenresTest {

    @Test
    public void testSplitMultiValueWithSemicolon() {
        assertEquals(List.of("Rock", "Metal"), Genres.split("Rock; Metal", ";"));
    }

    @Test
    public void testSplitTrimsAndFiltersBlanks() {
        assertEquals(List.of("Rock", "Metal"), Genres.split("Rock;  ; Metal;", ";"));
    }

    @Test
    public void testSplitDeduplicatesPreservingOrder() {
        assertEquals(List.of("Rock", "Metal"), Genres.split("Rock; Metal; Rock", ";"));
    }

    @Test
    public void testSplitSingleValueReturnsSingleton() {
        assertEquals(List.of("Pop"), Genres.split("Pop", ";"));
    }

    @Test
    public void testSplitHonoursMultipleSeparatorChars() {
        assertEquals(List.of("Rock", "Metal", "Jazz"), Genres.split("Rock; Metal,Jazz", ";,"));
    }

    @Test
    public void testSplitNullOrBlankReturnsEmpty() {
        assertTrue(Genres.split(null, ";").isEmpty());
        assertTrue(Genres.split("", ";").isEmpty());
        assertTrue(Genres.split("   ", ";").isEmpty());
        assertTrue(Genres.split(";;;", ";").isEmpty());
    }

    @Test
    public void testJoinThenSplitRoundTrip() {
        List<String> source = List.of("Rock", "Metal", "Jazz");
        String packed = String.join(";", source);
        assertEquals(source, Genres.split(packed, ";"));
    }
}
