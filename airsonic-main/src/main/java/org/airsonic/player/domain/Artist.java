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

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Sindre Mehus
 * @version $Id$
 */
@Entity
@Table(name = "artist")
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(unique = true, nullable = false)
    private String name;
    @Column(name = "sort_name", nullable = true)
    private String sortName;
    @Column(name = "mb_artist_id", nullable = true)
    private String musicBrainzArtistId;
    @Column(name = "album_count")
    @Convert(converter = AtomicIntegerConverter.class)
    private final AtomicInteger albumCount = new AtomicInteger();
    @Column(name = "last_scanned", nullable = false)
    private Instant lastScanned;
    @Column(name = "present")
    private boolean present = true;
    @ManyToOne
    @JoinColumn(name = "folder_id", referencedColumnName = "id")
    private MusicFolder folder;

    public Artist() {
    }

    public Artist(String name) {
        this.name = name;
    }

    public Artist(int id, String name, int albumCount, Instant lastScanned, boolean present, MusicFolder folder) {
        this.id = id;
        this.name = name;
        this.albumCount.set(albumCount);
        this.lastScanned = lastScanned;
        this.present = present;
        this.folder = folder;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSortName() {
        return sortName;
    }

    public void setSortName(String sortName) {
        this.sortName = sortName;
    }

    public String getMusicBrainzArtistId() {
        return musicBrainzArtistId;
    }

    public void setMusicBrainzArtistId(String musicBrainzArtistId) {
        this.musicBrainzArtistId = musicBrainzArtistId;
    }

    public int getAlbumCount() {
        return albumCount.get();
    }

    public void setAlbumCount(int albumCount) {
        this.albumCount.set(albumCount);
    }

    public Instant getLastScanned() {
        return lastScanned;
    }

    public void setLastScanned(Instant lastScanned) {
        this.lastScanned = lastScanned;
    }

    public boolean isPresent() {
        return present;
    }

    public void setPresent(boolean present) {
        this.present = present;
    }

    public void setFolder(MusicFolder folder) {
        this.folder = folder;
    }

    public MusicFolder getFolder() {
        return folder;
    }

    // placeholder for persistence later
    @Transient
    private CoverArt art;

    public CoverArt getArt() {
        return art;
    }

    public void setArt(CoverArt art) {
        this.art = art;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof Artist other)) {
            return false;
        }
        return Objects.equals(name, other.name);
    }
}
