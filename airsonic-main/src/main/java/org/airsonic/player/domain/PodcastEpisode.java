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

import jakarta.persistence.*;

import java.time.Instant;
import java.util.Objects;

/**
 * A Podcast episode belonging to a channel.
 *
 * @author Sindre Mehus
 * @see PodcastChannel
 */

@Entity
@Table(name = "podcast_episode")
public class PodcastEpisode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    @OneToOne
    @JoinColumn(name = "media_file_id", referencedColumnName = "id")
    private MediaFile mediaFile;

    @ManyToOne()
    @JoinColumn(name = "channel_id", referencedColumnName = "id")
    private PodcastChannel channel;

    @Column(name = "episode_guid")
    private String episodeGuid;

    @Column(name = "url", nullable = false)
    private String url;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "publish_date")
    private Instant publishDate;

    @Column(name = "duration")
    private String duration;

    @Column(name = "bytes_total")
    private Long bytesTotal;

    @Column(name = "bytes_downloaded")
    private Long bytesDownloaded;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private PodcastStatus status;

    @Column(name = "locked")
    private boolean locked;

    @Column(name = "error_message")
    private String errorMessage;

    public PodcastEpisode() {
    }

    public PodcastEpisode(Integer id, PodcastChannel channel, String episodeGuid, String url, MediaFile mediaFile,
            String title,
            String description, Instant publishDate, String duration, Long length, Long bytesDownloaded,
            PodcastStatus status, String errorMessage) {
        this.id = id;
        this.channel = channel;
        this.episodeGuid = episodeGuid;
        this.url = url;
        this.mediaFile = mediaFile;
        this.title = title;
        this.description = description;
        this.publishDate = publishDate;
        this.duration = duration;
        this.bytesTotal = length;
        this.bytesDownloaded = bytesDownloaded;
        this.status = status;
        this.errorMessage = errorMessage;
    }

    public Integer getId() {
        return id;
    }

    public PodcastChannel getChannel() {
        return channel;
    }

    public void setChannel(PodcastChannel channel) {
        this.channel = channel;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getPublishDate() {
        return publishDate;
    }

    public void setPublishDate(Instant publishDate) {
        this.publishDate = publishDate;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public Long getBytesTotal() {
        return bytesTotal;
    }

    public void setBytesTotal(Long bytesTotal) {
        this.bytesTotal = bytesTotal;
    }

    public Long getBytesDownloaded() {
        return bytesDownloaded;
    }

    public Double getCompletionRate() {
        if (bytesTotal == null || bytesTotal == 0) {
            return null;
        }
        if (bytesDownloaded == null) {
            return 0.0;
        }

        double d = bytesDownloaded;
        double t = bytesTotal;
        return d / t;
    }

    public void setBytesDownloaded(Long bytesDownloaded) {
        this.bytesDownloaded = bytesDownloaded;
    }

    public PodcastStatus getStatus() {
        return status;
    }

    public void setStatus(PodcastStatus status) {
        this.status = status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public MediaFile getMediaFile() {
        return mediaFile;
    }

    public void setMediaFile(MediaFile mediaFile) {
        this.mediaFile = mediaFile;
    }

    public String getEpisodeGuid() {
        return episodeGuid;
    }

    public void setEpisodeGuid(String episodeGuid) {
        this.episodeGuid = episodeGuid;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    @Override
    public int hashCode() {
        return Objects.hash(url, episodeGuid, channel);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        if (!(obj instanceof PodcastEpisode other)) {
            return false;
        }
        return Objects.equals(channel, other.channel)
                && Objects.equals(episodeGuid, other.episodeGuid)
                && Objects.equals(url, other.url);
    }

}
