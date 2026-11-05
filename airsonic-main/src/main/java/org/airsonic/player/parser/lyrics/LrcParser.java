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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.parser.lyrics;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.Nullable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class LrcParser {

    private static final Logger LOG = LoggerFactory.getLogger(LrcParser.class);

    /**
     * Parses a LRC file and returns a list of LyricsLine objects.
     *
     * @param lrcFilePath The path to the LRC file.
     * @return A list of LyricsLine objects parsed from the LRC file.
     * @throws IOException If an error occurs while reading the file.
     */
    @Nullable
    public LrcFile parse(String lrcFilePath) {
        try {
            if (StringUtils.isBlank(lrcFilePath)) {
                LOG.warn("LRC file path is blank");
                return null;
            }
            Path path = Path.of(lrcFilePath);
            return parse(path);
        } catch (InvalidPathException e) {
            LOG.error("Invalid LRC file path: {}", lrcFilePath, e);
            return null;
        }
    }

    /**
     * Parses a LRC file and returns a list of LyricsLine objects.
     *
     * @param lrcFilePath The path to the LRC file.
     * @return A list of LyricsLine objects parsed from the LRC file.
     */
    public LrcFile parse(Path lrcFilePath) {
        if (lrcFilePath == null || !lrcFilePath.toFile().exists()) {
            LOG.warn("LRC file does not exist: {}", lrcFilePath);
            return null;
        }
        List<LyricsLine> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(lrcFilePath.toFile()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Ignore extended format meta information lines
                if (line.matches("\\[\\w+?:.*\\]")) {
                    continue;
                }
                // Remove A2 Extension part (e.g., <...> after time tag)
                line = line.replaceAll("(^\\[\\d{2}:\\d{2}(\\.\\d{2,3})?\\])\\s*<.*?>", "$1");
                // Also remove any <...> that may appear after the time tag
                line = line.replaceAll("\\s<.*?>", "");

                String[] parts = line.split("]");
                for (int i = 0; i < parts.length - 1; i++) {
                    String timeTag = parts[i].replace("[", "").trim();
                    String text = parts[parts.length - 1].trim();
                    long time = parseTimeTag(timeTag);
                    lines.add(new LyricsLine(time, text));
                }
            }
            return new LrcFile(lines, Files.getLastModifiedTime(lrcFilePath).toInstant().truncatedTo(ChronoUnit.MICROS));
        } catch (IOException e) {
            LOG.warn("Error reading LRC file: {}", lrcFilePath);
            return null;
        } catch (Exception e) {
            LOG.warn("Unexpected error while parsing LRC file: {}", lrcFilePath);
            return null;
        }
    }

    private long parseTimeTag(String tag) {
        // tag format: mm:ss.xx or mm:ss
        String[] minSec = tag.split(":");
        if (minSec.length != 2) {
            LOG.warn("Invalid LRC time tag format: {}", tag);
            throw new IllegalArgumentException("Invalid LRC time tag format: " + tag);
        }
        int min = Integer.parseInt(minSec[0]);
        String[] secHundredths = minSec[1].split("\\.");
        int sec = Integer.parseInt(secHundredths[0]);
        int ms = 0;
        if (secHundredths.length > 1) {
            // xx is hundredths of a second, so multiply by 10 to get milliseconds
            int hundredths = Integer.parseInt((secHundredths[1] + "0").substring(0, 2));
            ms = hundredths * 10;
        }
        return min * 60_000 + sec * 1000 + ms;
    }

}
