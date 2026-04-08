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

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.annotation.Nonnull;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Sindre Mehus
 */
@Entity
@Table(name = "playlist")
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "username", nullable = false)
    private String username;
    @Column(name = "is_public")
    private boolean shared;
    @Column(name = "name", nullable = false)
    private String name;
    @Column(name = "comment")
    private String comment;
    @Column(name = "file_count")
    private int fileCount;
    @Column(name = "duration")
    private double duration;
    @Column(name = "created")
    private Instant created;
    @Column(name = "changed")
    private Instant changed;
    @Column(name = "imported_from")
    private String importedFrom;

    @ManyToMany
    @JoinTable(name = "playlist_user",
            joinColumns = @JoinColumn(name = "playlist_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "username"))
    private List<User> sharedUsers;

    @OrderBy("orderIndex ASC")
    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PlaylistMediaFile> mediaFiles;

    public Playlist() {
    }

    public Playlist(String username, boolean shared, String name, String comment, int fileCount,
                    double duration, Instant created, Instant changed, String importedFrom) {
        this.username = username;
        this.shared = shared;
        this.name = name;
        this.comment = comment;
        this.fileCount = fileCount;
        this.duration = duration;
        this.created = created;
        this.changed = changed;
        this.importedFrom = importedFrom;
    }

    public Playlist(Playlist p) {
        this(p.getUsername(), p.getShared(), p.getName(), p.getComment(), p.getFileCount(), p.getDuration(),
                p.getCreated(), p.getChanged(), p.getImportedFrom());
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public boolean getShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public int getFileCount() {
        return fileCount;
    }

    public void setFileCount(int fileCount) {
        this.fileCount = fileCount;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getChanged() {
        return changed;
    }

    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public String getImportedFrom() {
        return importedFrom;
    }

    public void setImportedFrom(String importedFrom) {
        this.importedFrom = importedFrom;
    }

    @JsonIgnore
    public List<User> getSharedUsers() {
        return sharedUsers;
    }

    public void setSharedUsers(List<User> sharedUsers) {
        if (this.sharedUsers == null) {
            this.sharedUsers = new ArrayList<>();
        } else {
            this.sharedUsers.clear();
        }
        this.sharedUsers.addAll(sharedUsers);
    }

    public void addSharedUser(User sharedUser) {
        this.sharedUsers.add(sharedUser);
    }

    public void removeSharedUser(User sharedUser) {
        this.sharedUsers.remove(sharedUser);
    }

    public void removeSharedUserByUsername(String username) {
        this.sharedUsers.removeIf(su -> su.getUsername().equals(username));
    }

    @JsonIgnore
    @Nonnull
    public List<PlaylistMediaFile> getPlaylistMediaFiles() {
        if (this.mediaFiles == null) {
            this.mediaFiles = new ArrayList<>();
        }
        return this.mediaFiles;
    }

    public void setPlaylistMediaFiles(List<PlaylistMediaFile> mediaFiles) {
        if (this.mediaFiles == null) {
            this.mediaFiles = new ArrayList<>();
        } else {
            this.mediaFiles.clear();
        }
        if (mediaFiles != null) {
            this.mediaFiles.addAll(mediaFiles);
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object o) {

        if (this == o) {
            return true;
        }

        if (!(o instanceof Playlist p)) {
            return false;
        }
        return Objects.equals(id, p.id);
    }

}
