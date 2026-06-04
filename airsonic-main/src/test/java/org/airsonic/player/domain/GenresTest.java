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

    @Test
    public void testIncrementSongCountAccumulatesPerToken() {
        Genres g = new Genres();
        g.incrementSongCount("Rock");
        g.incrementSongCount("Rock");
        g.incrementSongCount("Metal");

        Genre rock = findGenre(g, "Rock");
        Genre metal = findGenre(g, "Metal");
        assertEquals(2, rock.getSongCount());
        assertEquals(0, rock.getAlbumCount());
        assertEquals(1, metal.getSongCount());
        assertEquals(0, metal.getAlbumCount());
    }

    @Test
    public void testIncrementAlbumCountAccumulatesPerToken() {
        Genres g = new Genres();
        g.incrementAlbumCount("Rock");
        g.incrementAlbumCount("Rock");
        g.incrementAlbumCount("Metal");

        Genre rock = findGenre(g, "Rock");
        Genre metal = findGenre(g, "Metal");
        assertEquals(2, rock.getAlbumCount());
        assertEquals(0, rock.getSongCount());
        assertEquals(1, metal.getAlbumCount());
        assertEquals(0, metal.getSongCount());
    }

    @Test
    public void testIncrementIgnoresBlankAndNullTokens() {
        Genres g = new Genres();
        g.incrementSongCount(null);
        g.incrementSongCount("");
        g.incrementSongCount("   ");
        g.incrementAlbumCount(null);
        g.incrementAlbumCount("");
        g.incrementAlbumCount("   ");

        assertTrue(g.getGenres().isEmpty(),
                "blank/null tokens must not create rows");
    }

    private static Genre findGenre(Genres g, String name) {
        return g.getGenres().stream()
                .filter(x -> name.equals(x.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("genre row missing: " + name));
    }
}
