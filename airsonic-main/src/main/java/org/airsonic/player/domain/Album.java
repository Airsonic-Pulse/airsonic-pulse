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

import com.google.common.util.concurrent.AtomicDouble;
import org.airsonic.player.domain.entity.StarredAlbum;
import org.airsonic.player.repository.AtomicDoubleConverter;
import org.airsonic.player.repository.AtomicIntegerConverter;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * @author Sindre Mehus
 * @version $Id$
 */
@Entity
@Table(name = "album", uniqueConstraints = @UniqueConstraint(columnNames = {"artist","name"}))
public class Album {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "path", nullable = false)
    private String path;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_name", nullable = true)
    private String sortName;

    @Column(name = "artist", nullable = false)
    private String artist;

    @Column(name = "song_count")
    @Convert(converter = AtomicIntegerConverter.class)
    private final AtomicInteger songCount = new AtomicInteger(0);

    @Column(name = "duration")
    @Convert(converter = AtomicDoubleConverter.class)
    private final AtomicDouble duration = new AtomicDouble(0);

    @Column(name = "year", nullable = true)
    private Integer year;

    @Column(name = "release_date", nullable = true)
    private String releaseDate;

    @Column(name = "original_release_date", nullable = true)
    private String originalReleaseDate;

    @Column(name = "is_compilation", nullable = true)
    private Boolean compilation;

    @Column(name = "release_types", nullable = true)
    private String releaseTypes;

    @Column(name = "record_labels", nullable = true)
    private String recordLabels;

    @Column(name = "genre", nullable = true)
    private String genre;

    @Column(name = "play_count")
    @Convert(converter = AtomicIntegerConverter.class)
    private final AtomicInteger playCount = new AtomicInteger(0);

    @Column(name = "last_played", nullable = true)
    private Instant lastPlayed;

    @Column(name = "comment", nullable = true)
    private String comment;

    @Column(name = "created")
    private Instant created;

    @Column(name = "last_scanned")
    private Instant lastScanned;

    @Column(name = "present")
    private boolean present;

    @OneToMany(mappedBy = "album")
    private List<StarredAlbum> starredAlbums = new ArrayList<>();

    @ManyToOne
    @JoinColumn(name = "folder_id", referencedColumnName = "id")
    private MusicFolder folder;

    @Column(name = "mb_release_id", nullable = true)
    private String musicBrainzReleaseId;

    public Album() {
    }

    public Album(String path, String name, String artist, Instant created, Instant lastScanned, boolean present, MusicFolder folder) {
        this.path = path;
        this.name = name;
        this.artist = artist;
        this.created = created;
        this.lastScanned = lastScanned;
        this.present = present;
        this.folder = folder;
    }

    public Album(Integer id, String path, String name, String artist, int songCount, double duration,
            Integer year, String genre, int playCount, Instant lastPlayed, String comment, Instant created, Instant lastScanned,
            boolean present, MusicFolder folder, String musicBrainzReleaseId) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.artist = artist;
        this.songCount.set(songCount);
        this.duration.set(duration);
        this.year = year;
        this.genre = genre;
        this.playCount.set(playCount);
        this.lastPlayed = lastPlayed;
        this.comment = comment;
        this.created = created;
        this.lastScanned = lastScanned;
        this.folder = folder;
        this.present = present;
        this.musicBrainzReleaseId = musicBrainzReleaseId;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
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

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public int getSongCount() {
        return songCount.get();
    }

    public void setSongCount(int songCount) {
        this.songCount.set(songCount);
    }

    public void incrementSongCount() {
        this.songCount.incrementAndGet();
    }

    public double getDuration() {
        return duration.get();
    }

    public void setDuration(double duration) {
        this.duration.set(duration);
    }

    public void incrementDuration(double duration) {
        this.duration.addAndGet(duration);
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getOriginalReleaseDate() {
        return originalReleaseDate;
    }

    public void setOriginalReleaseDate(String originalReleaseDate) {
        this.originalReleaseDate = originalReleaseDate;
    }

    public Boolean getCompilation() {
        return compilation;
    }

    public void setCompilation(Boolean compilation) {
        this.compilation = compilation;
    }

    public String getReleaseTypes() {
        return releaseTypes;
    }

    public void setReleaseTypes(String releaseTypes) {
        this.releaseTypes = releaseTypes;
    }

    public String getRecordLabels() {
        return recordLabels;
    }

    public void setRecordLabels(String recordLabels) {
        this.recordLabels = recordLabels;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public int getPlayCount() {
        return playCount.get();
    }

    public void setPlayCount(int playCount) {
        this.playCount.set(playCount);
    }

    public void incrementPlayCount() {
        this.playCount.incrementAndGet();
    }

    public Instant getLastPlayed() {
        return lastPlayed;
    }

    public void setLastPlayed(Instant lastPlayed) {
        this.lastPlayed = lastPlayed;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
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

    public String getMusicBrainzReleaseId() {
        return musicBrainzReleaseId;
    }

    public void setMusicBrainzReleaseId(String musicBrainzReleaseId) {
        this.musicBrainzReleaseId = musicBrainzReleaseId;
    }

    public List<StarredAlbum> getStarredAlbums() {
        return starredAlbums;
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
        return Objects.hash(path, folder);
    }

    @Override
    public boolean equals(Object obj) {

        if (!(obj instanceof Album other)) {
            return false;
        }

        return Objects.equals(path, other.path) && Objects.equals(folder, other.folder);
    }

}
