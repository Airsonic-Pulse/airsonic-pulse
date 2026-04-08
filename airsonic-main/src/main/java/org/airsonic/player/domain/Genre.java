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

 Copyright 2023 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.domain;

import org.airsonic.player.repository.AtomicIntegerConverter;

import jakarta.persistence.*;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a musical genre.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.2 $ $Date: 2005/12/25 13:48:46 $
 */
@Entity
@Table(name = "genre")
public class Genre {

    @Id
    @Column(name = "name")
    private String name;

    @Column(name = "song_count")
    @Convert(converter = AtomicIntegerConverter.class)
    private AtomicInteger songCount = new AtomicInteger(0);

    @Column(name = "album_count")
    @Convert(converter = AtomicIntegerConverter.class)
    private AtomicInteger albumCount = new AtomicInteger(0);

    public Genre() {
    }

    public Genre(String name) {
        this.name = name;
    }

    public Genre(String name, int songCount, int albumCount) {
        this.name = name;
        this.songCount.set(songCount);
        this.albumCount.set(albumCount);
    }

    public String getName() {
        return name;
    }

    public int getSongCount() {
        return songCount.get();
    }

    public int getAlbumCount() {
        return albumCount.get();
    }

    public void incrementAlbumCount() {
        albumCount.incrementAndGet();
    }

    public void incrementSongCount() {
        songCount.incrementAndGet();
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof Genre other)) {
            return false;
        }
        return Objects.equals(name, other.name);
    }
}