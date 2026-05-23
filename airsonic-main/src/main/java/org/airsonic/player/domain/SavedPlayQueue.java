/*
 * This file is part of Airsonic.
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Airsonic is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Copyright 2024 (C) Y.Tory
 *  Copyright 2015 (C) Sindre Mehus
 */

package org.airsonic.player.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Used to save the play queue state for a user.
 * <p/>
 * Can be used to share the play queue (including currently playing track and position within
 * that track) across client apps.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
@Entity
@Table(name = "play_queue", uniqueConstraints = @UniqueConstraint(columnNames = {"username"}))
public class SavedPlayQueue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @Column(name = "username", nullable = false)
    private String username;
    @ManyToMany
    @JoinTable(name = "play_queue_file",
        joinColumns = @JoinColumn(name = "play_queue_id", referencedColumnName = "id"),
        inverseJoinColumns = @JoinColumn(name = "media_file_id", referencedColumnName = "id"))
    private List<MediaFile> mediaFiles = new ArrayList<>();
    @OneToOne
    @JoinColumn(name = "current_media_file_id", referencedColumnName = "id")
    private MediaFile currentMediaFile;
    @Column(name = "current_index")
    private Integer currentIndex;
    @Column(name = "position_millis")
    private Long positionMillis;
    @Column(name = "changed")
    private Instant changed;
    @Column(name = "changed_by")
    private String changedBy;

    public SavedPlayQueue() {
    }

    public SavedPlayQueue(String username) {
        this.username = username;
    }

    public SavedPlayQueue(Integer id, String username, List<MediaFile> mediaFiles, MediaFile currentMediaFile,
                          Long positionMillis, Instant changed, String changedBy) {
        this.id = id;
        this.username = username;
        this.mediaFiles = mediaFiles;
        this.currentMediaFile = currentMediaFile;
        this.positionMillis = positionMillis;
        this.changed = changed;
        this.changedBy = changedBy;
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

    public List<MediaFile> getMediaFiles() {
        return mediaFiles;
    }

    public void setMediaFiles(List<MediaFile> mediaFiles) {
        this.mediaFiles = mediaFiles;
    }

    public MediaFile getCurrentMediaFile() {
        return currentMediaFile;
    }

    public void setCurrentMediaFile(MediaFile currentMediaFile) {
        this.currentMediaFile = currentMediaFile;
    }

    public Integer getCurrentIndex() {
        return currentIndex;
    }

    public void setCurrentIndex(Integer currentIndex) {
        this.currentIndex = currentIndex;
    }

    public Long getPositionMillis() {
        return positionMillis;
    }

    public void setPositionMillis(Long positionMillis) {
        this.positionMillis = positionMillis;
    }

    public Instant getChanged() {
        return changed;
    }

    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object other) {

        if (this == other) {
            return true;
        }

        if (!(other instanceof SavedPlayQueue otherQueue)) {
            return false;
        }
        return Objects.equals(id, otherQueue.id);
    }
}
