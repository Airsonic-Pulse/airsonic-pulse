package org.airsonic.player.domain;

import jakarta.persistence.*;

import java.util.Objects;

@Entity
@Table(name = "podcast_channel_rules")
public class PodcastChannelRule {

    @Id
    private Integer id;

    @Column(name = "check_interval")
    private Integer checkInterval;

    @Column(name = "retention_count")
    private Integer retentionCount;

    @Column(name = "download_count")
    private Integer downloadCount;

    public PodcastChannelRule() {
        super();
    }

    public PodcastChannelRule(Integer id) {
        super();
        this.id = id;
    }

    public PodcastChannelRule(Integer id, Integer checkInterval, Integer retentionCount, Integer downloadCount) {
        super();
        this.id = id;
        this.checkInterval = checkInterval;
        this.retentionCount = retentionCount;
        this.downloadCount = downloadCount;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getCheckInterval() {
        return checkInterval;
    }

    public void setCheckInterval(Integer checkInterval) {
        this.checkInterval = checkInterval;
    }

    public Integer getRetentionCount() {
        return retentionCount;
    }

    public void setRetentionCount(Integer retentionCount) {
        this.retentionCount = retentionCount;
    }

    public Integer getDownloadCount() {
        return downloadCount;
    }

    public void setDownloadCount(Integer downloadCount) {
        this.downloadCount = downloadCount;
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

        if (!(other instanceof PodcastChannelRule otherRule)) {
            return false;
        }
        return Objects.equals(id, otherRule.id);
    }

}
