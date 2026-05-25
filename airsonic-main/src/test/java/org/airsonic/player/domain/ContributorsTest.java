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
package org.airsonic.player.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for {@link Contributors#pack(List)} and {@link Contributors#split(String)} —
 * the encoding that backs the {@code media_file.contributors} column. Locks the
 * collision-safety contract: the field separator ({@code U+001F}) and the record separator
 * ({@code \n}) must never re-emerge from a packed value even when contributor names or
 * roles contain those characters themselves.
 */
public class ContributorsTest {

    private static final String FS = String.valueOf(Contributors.FIELD_SEPARATOR);
    private static final String RS = String.valueOf(Contributors.RECORD_SEPARATOR);

    @Test
    public void testPackNullReturnsNull() {
        assertNull(Contributors.pack(null));
    }

    @Test
    public void testPackEmptyReturnsNull() {
        assertNull(Contributors.pack(List.of()));
    }

    @Test
    public void testSplitNullReturnsEmpty() {
        assertTrue(Contributors.split(null).isEmpty());
    }

    @Test
    public void testSplitBlankReturnsEmpty() {
        assertTrue(Contributors.split("").isEmpty());
        assertTrue(Contributors.split("   ").isEmpty());
    }

    @Test
    public void testRoundTripSingleContributorWithoutSubRole() {
        List<Contributor> source = List.of(new Contributor("composer", null, "John Williams"));
        assertEquals(source, Contributors.split(Contributors.pack(source)));
    }

    @Test
    public void testRoundTripMultipleContributors() {
        List<Contributor> source = List.of(
                new Contributor("composer", null, "John Williams"),
                new Contributor("lyricist", null, "Bernie Taupin"),
                new Contributor("performer", "guitar", "Jimi Hendrix"));
        assertEquals(source, Contributors.split(Contributors.pack(source)));
    }

    @Test
    public void testRoundTripWithSubRoleEmptyStringNormalizesToNull() {
        // Pack writes empty subRole as empty field; split trims empty → null. The round-trip
        // therefore canonicalizes "" → null, matching how callers should read subRole.
        List<Contributor> source = List.of(new Contributor("composer", "", "John Williams"));
        List<Contributor> expected = List.of(new Contributor("composer", null, "John Williams"));
        assertEquals(expected, Contributors.split(Contributors.pack(source)));
    }

    @Test
    public void testPackSkipsContributorWithNullRole() {
        List<Contributor> source = List.of(
                new Contributor(null, null, "John Williams"),
                new Contributor("lyricist", null, "Bernie Taupin"));
        assertEquals(List.of(new Contributor("lyricist", null, "Bernie Taupin")),
                Contributors.split(Contributors.pack(source)));
    }

    @Test
    public void testPackSkipsContributorWithNullName() {
        List<Contributor> source = List.of(
                new Contributor("composer", null, null),
                new Contributor("lyricist", null, "Bernie Taupin"));
        assertEquals(List.of(new Contributor("lyricist", null, "Bernie Taupin")),
                Contributors.split(Contributors.pack(source)));
    }

    @Test
    public void testPackSanitizesNameContainingRecordSeparator() {
        // Pathological name with embedded newline must not corrupt the encoding.
        String packed = Contributors.pack(List.of(
                new Contributor("composer", null, "Foo" + RS + "Bar"),
                new Contributor("lyricist", null, "Baz")));
        List<Contributor> roundTripped = Contributors.split(packed);
        assertEquals(List.of(
                new Contributor("composer", null, "Foo Bar"),
                new Contributor("lyricist", null, "Baz")), roundTripped);
    }

    @Test
    public void testPackSanitizesNameContainingFieldSeparator() {
        // Pathological name with embedded unit separator must not corrupt the encoding.
        String packed = Contributors.pack(List.of(
                new Contributor("performer", "guitar", "Foo" + FS + "Bar")));
        List<Contributor> roundTripped = Contributors.split(packed);
        assertEquals(List.of(new Contributor("performer", "guitar", "Foo Bar")), roundTripped);
    }

    @Test
    public void testPackSanitizesRoleAndSubRoleContainingSeparators() {
        // Roles and subRoles are also sanitized — defending all three field positions
        // keeps the encoding self-delimiting against any input.
        String packed = Contributors.pack(List.of(
                new Contributor("comp" + FS + "oser", "lead" + RS + "guitar", "Hendrix")));
        List<Contributor> roundTripped = Contributors.split(packed);
        assertEquals(List.of(new Contributor("comp oser", "lead guitar", "Hendrix")), roundTripped);
    }

    @Test
    public void testSplitSkipsMalformedRecordWithWrongFieldCount() {
        String malformed = "composer" + FS + "John Williams";  // only 2 fields, not 3
        String wellFormed = "lyricist" + FS + FS + "Bernie Taupin";
        String packed = malformed + RS + wellFormed;
        assertEquals(List.of(new Contributor("lyricist", null, "Bernie Taupin")),
                Contributors.split(packed));
    }

    @Test
    public void testSplitTrimsFields() {
        String packed = "  composer  " + FS + "  " + FS + "  John Williams  ";
        assertEquals(List.of(new Contributor("composer", null, "John Williams")),
                Contributors.split(packed));
    }

    @Test
    public void testPackPreservesContributorOrder() {
        // Multiple contributors for the same role keep their declared order — important
        // when the spec emits them as a list (e.g. two composers credited Foo, Bar).
        List<Contributor> source = List.of(
                new Contributor("composer", null, "Foo"),
                new Contributor("composer", null, "Bar"),
                new Contributor("lyricist", null, "Baz"));
        assertEquals(source, Contributors.split(Contributors.pack(source)));
    }
}
