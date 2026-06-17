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

import org.apache.commons.lang.StringUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

import java.nio.file.Path;

/**
 * Reads the embedded <em>unsynced</em> lyrics tag ({@link FieldKey#LYRICS}) from an audio file —
 * the ID3v2 {@code USLT}, Vorbis {@code LYRICS}, or MP4 {@code ©lyr} frame as mapped by
 * jaudiotagger. This is the request-time fallback source consulted by
 * {@link org.airsonic.player.service.LyricsService} only when both the DB cache and the LRC
 * sidecar miss. Synced lyrics ({@code SYLT} / LRC timing) are out of scope here.
 */
@Component
public class EmbeddedLyricsParser {

    private static final Logger LOG = LoggerFactory.getLogger(EmbeddedLyricsParser.class);

    /**
     * Returns the embedded unsynced lyrics for the given audio file, or {@code null} when the file
     * has no readable lyrics tag (or cannot be read at all). Any jaudiotagger failure is swallowed
     * and treated as "no lyrics" so a single malformed file never breaks a lyrics request.
     *
     * @param file the audio file to read.
     * @return the trimmed lyrics text, or {@code null} if absent/unreadable.
     */
    @Nullable
    public String getLyrics(@Nullable Path file) {
        if (file == null) {
            return null;
        }
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTag();
            if (tag == null) {
                return null;
            }
            return StringUtils.trimToNull(tag.getFirst(FieldKey.LYRICS));
        } catch (Exception e) {
            LOG.debug("Could not read embedded lyrics from {}", file, e);
            return null;
        }
    }
}
