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
package org.airsonic.player.parser.lyrics;

import org.airsonic.player.util.MusicFolderTestData;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies the real jaudiotagger read in {@link EmbeddedLyricsParser}. The lyrics tag is written
 * programmatically into a copy of a real fixture mp3 (mirroring the in-test tag construction used
 * by the JaudiotaggerParser contributor tests) so no binary fixture needs committing.
 */
class EmbeddedLyricsParserTest {

    @TempDir
    private Path tempDir;

    private final EmbeddedLyricsParser parser = new EmbeddedLyricsParser();

    private Path copyFixture(String name) throws Exception {
        Path src = MusicFolderTestData.resolveBaseMediaPath().resolve("piano.mp3");
        Path dest = tempDir.resolve(name);
        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        return dest;
    }

    @Test
    void getLyrics_readsEmbeddedLyricsTag() throws Exception {
        Path file = copyFixture("embedded.mp3");
        AudioFile audioFile = AudioFileIO.read(file.toFile());
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.LYRICS, "line one\nline two");
        audioFile.commit();

        assertEquals("line one\nline two", parser.getLyrics(file));
    }

    @Test
    void getLyrics_returnsNullWhenNoLyricsTag() throws Exception {
        Path file = copyFixture("nolyrics.mp3");
        AudioFile audioFile = AudioFileIO.read(file.toFile());
        Tag tag = audioFile.getTag();
        if (tag != null) {
            tag.deleteField(FieldKey.LYRICS);
            audioFile.commit();
        }

        assertNull(parser.getLyrics(file));
    }

    @Test
    void getLyrics_returnsNullForBlankLyricsTag() throws Exception {
        // A present-but-blank LYRICS frame must read as null (trimToNull), so LyricsService never
        // caches an empty lyrics string.
        Path file = copyFixture("blanklyrics.mp3");
        AudioFile audioFile = AudioFileIO.read(file.toFile());
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        tag.setField(FieldKey.LYRICS, "   ");
        audioFile.commit();

        assertNull(parser.getLyrics(file));
    }

    @Test
    void getLyrics_returnsNullForNullPath() {
        assertNull(parser.getLyrics(null));
    }

    @Test
    void getLyrics_returnsNullForUnreadableFile() throws Exception {
        Path notAudio = tempDir.resolve("notaudio.mp3");
        Files.writeString(notAudio, "this is not an audio file");
        assertNull(parser.getLyrics(notAudio));
    }
}
