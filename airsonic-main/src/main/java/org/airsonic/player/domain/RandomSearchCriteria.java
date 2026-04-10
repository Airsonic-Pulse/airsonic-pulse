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

import org.airsonic.player.service.SearchService;

import java.time.Instant;
import java.util.List;

/**
 * Defines criteria used when generating random playlists.
 *
 * @author Sindre Mehus
 * @see SearchService#getRandomSongs
 */
public record RandomSearchCriteria(
        int count,
        String genre,
        Integer fromYear,
        Integer toYear,
        List<MusicFolder> musicFolders,
        Instant minLastPlayedDate,
        Instant maxLastPlayedDate,
        Integer minAlbumRating,
        Integer maxAlbumRating,
        Integer minPlayCount,
        Integer maxPlayCount,
        boolean showStarredSongs,
        boolean showUnstarredSongs,
        String format) {

    /**
     * Creates a new instance with default optional parameters.
     *
     * @param count        Maximum number of songs to return.
     * @param genre        Only return songs of the given genre. May be <code>null</code>.
     * @param fromYear     Only return songs released after (or in) this year. May be <code>null</code>.
     * @param toYear       Only return songs released before (or in) this year. May be <code>null</code>.
     * @param musicFolders Only return songs from these music folder. May NOT be <code>null</code>.
     */
    public RandomSearchCriteria(int count, String genre, Integer fromYear, Integer toYear, List<MusicFolder> musicFolders) {
        this(count, genre, fromYear, toYear, musicFolders, null, null, null, null, null, null, true, true, null);
    }
}
