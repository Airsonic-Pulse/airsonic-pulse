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
package org.airsonic.player.domain;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Represents a list of genres.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.2 $ $Date: 2005/12/25 13:48:46 $
 */
public class Genres {

    private final Map<String, Genre> genres = new ConcurrentHashMap<>();

    /**
     * Increments the album count for a single, already-split genre token. Callers are expected
     * to feed pre-resolved tokens (typically via {@link #split(String, String)} on a packed
     * multi-value column); blank tokens are ignored so callers do not have to filter.
     */
    public void incrementAlbumCount(String genreName) {
        if (StringUtils.isBlank(genreName)) {
            return;
        }
        genres.computeIfAbsent(genreName, Genre::new).incrementAlbumCount();
    }

    /**
     * Increments the song count for a single, already-split genre token. Callers are expected
     * to feed pre-resolved tokens (typically via {@link #split(String, String)} on a packed
     * multi-value column); blank tokens are ignored so callers do not have to filter.
     */
    public void incrementSongCount(String genreName) {
        if (StringUtils.isBlank(genreName)) {
            return;
        }
        genres.computeIfAbsent(genreName, Genre::new).incrementSongCount();
    }

    public List<Genre> getGenres() {
        return new ArrayList<Genre>(genres.values());
    }

    /**
     * Splits a (possibly multi-valued) genre string into individual, trimmed, de-duplicated
     * genre names using the given separator characters. Mirrors the splitting the count-table
     * builder applies, so a single separator setting governs both counts and the genres list.
     * <p>
     * The internal {@code .distinct()} dedups within a single input string; callers that flatMap
     * this across multiple raw tag values typically apply another {@code .distinct()} downstream
     * to dedup across frames as well.
     */
    public static List<String> split(String genre, String separators) {
        if (StringUtils.isBlank(genre)) {
            return List.of();
        }
        return Stream.of(StringUtils.split(genre, separators))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }
}
