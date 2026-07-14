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

import java.nio.file.Paths;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of {@link MediaFile#equals(Object)} and its null tolerance.
 *
 * <p>equals compares path, folder and startPosition. hashCode is null-safe (Objects.hash), so
 * equals must tolerate a null folder or startPosition rather than throwing — otherwise any
 * collection operation that compares MediaFiles (contains, indexOf, dedup) can blow up on a
 * value hashCode happily accepts.
 */
public class MediaFileTestCase {

    private static final MusicFolder FOLDER = new MusicFolder(1, Paths.get("/music"), "Music",
            MusicFolder.Type.MEDIA, true, Instant.EPOCH);

    private static MediaFile mediaFile(String path, MusicFolder folder, Double startPosition) {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setPath(path);
        mediaFile.setFolder(folder);
        mediaFile.setStartPosition(startPosition);
        return mediaFile;
    }

    @Test
    public void testEqualsWithNullFolderOnEitherSide() {
        MediaFile nullFolder = mediaFile("a.mp3", null, MediaFile.NOT_INDEXED);
        MediaFile withFolder = mediaFile("a.mp3", FOLDER, MediaFile.NOT_INDEXED);

        assertDoesNotThrow(() -> nullFolder.equals(withFolder));
        assertDoesNotThrow(() -> withFolder.equals(nullFolder));

        assertFalse(nullFolder.equals(withFolder));
        assertFalse(withFolder.equals(nullFolder));
    }

    @Test
    public void testEqualsWithNullStartPositionOnEitherSide() {
        MediaFile nullStart = mediaFile("a.mp3", FOLDER, null);
        MediaFile withStart = mediaFile("a.mp3", FOLDER, MediaFile.NOT_INDEXED);

        assertDoesNotThrow(() -> nullStart.equals(withStart));
        assertDoesNotThrow(() -> withStart.equals(nullStart));

        assertFalse(nullStart.equals(withStart));
        assertFalse(withStart.equals(nullStart));
    }

    @Test
    public void testEqualsWhenBothFoldersAreNull() {
        MediaFile a = mediaFile("a.mp3", null, MediaFile.NOT_INDEXED);
        MediaFile b = mediaFile("a.mp3", null, MediaFile.NOT_INDEXED);

        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testEqualsWhenBothStartPositionsAreNull() {
        MediaFile a = mediaFile("a.mp3", FOLDER, null);
        MediaFile b = mediaFile("a.mp3", FOLDER, null);

        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testEqualsWhenAllComparedFieldsAreNull() {
        MediaFile a = mediaFile(null, null, null);
        MediaFile b = mediaFile(null, null, null);

        assertDoesNotThrow(() -> a.equals(b));
        assertTrue(a.equals(b));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testEqualsWithIdenticalFields() {
        MediaFile a = mediaFile("a.mp3", FOLDER, 12.5);
        MediaFile b = mediaFile("a.mp3", FOLDER, 12.5);

        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    public void testEqualsWithDifferingFields() {
        MediaFile base = mediaFile("a.mp3", FOLDER, MediaFile.NOT_INDEXED);
        MusicFolder otherFolder = new MusicFolder(2, Paths.get("/other"), "Other",
                MusicFolder.Type.MEDIA, true, Instant.EPOCH);

        assertFalse(base.equals(mediaFile("b.mp3", FOLDER, MediaFile.NOT_INDEXED)));
        assertFalse(base.equals(mediaFile("a.mp3", otherFolder, MediaFile.NOT_INDEXED)));
        assertFalse(base.equals(mediaFile("a.mp3", FOLDER, 12.5)));
    }
}
