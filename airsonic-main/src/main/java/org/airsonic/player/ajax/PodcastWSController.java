package org.airsonic.player.ajax;

import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.service.PodcastManagementService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.podcast.PodcastIndexService;
import org.airsonic.player.service.podcast.PodcastIndexService.PodcastIndexResponse.PodcastIndexResult;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.List;


@Controller
@MessageMapping("/podcasts")
public class PodcastWSController {
    @Autowired
    PodcastPersistenceService podcastPersistenceService;

    @Autowired
    PodcastManagementService podcastManagementService;

    @Autowired
    PodcastIndexService podcastIndexService;

    @SubscribeMapping("all")
    public List<PodcastChannelInfo> getAllPodcastChannels() {
        return podcastPersistenceService.getAllChannels().stream().map(this::wrap).toList();
    }

    @MessageMapping("channel")
    @SendToUser(broadcast = false)
    public PodcastChannelInfo getPodcastChannel(Integer channelId) {
        return wrap(podcastPersistenceService.getChannel(channelId));
    }

    private PodcastChannelInfo wrap(PodcastChannel channel) {
        List<PodcastEpisode> episodes = podcastPersistenceService.getEpisodes(channel.getId());

        return new PodcastChannelInfo(channel, episodes.size(), (int) episodes.stream().filter(e -> e.getStatus() == PodcastStatus.COMPLETED).count());
    }

    @MessageMapping("create")
    public void createChannel(String url) {
        podcastManagementService.createChannel(StringUtils.trimToNull(url));
    }

    @MessageMapping("delete")
    public void deleteChannels(List<Integer> ids) {
        ids.forEach(id -> {
            podcastManagementService.deleteChannel(id);
        });
    }

    @MessageMapping("refresh")
    public void refreshChannels(List<Integer> ids) {
        podcastManagementService.refreshChannelIds(ids, true);
    }

    @SubscribeMapping("episodes/newest")
    public List<PodcastEpisode> newestEpisodes() {
        return podcastPersistenceService.getNewestEpisodes(10);
    }

    @MessageMapping("search")
    @SendToUser(broadcast = false)
    public List<PodcastIndexResult> search(Principal user, String query) throws Exception {
        return podcastIndexService.search(user.getName(), query);
    }

    public static class PodcastChannelInfo extends PodcastChannel {
        private int fileCount;
        private int downloadedCount;
        private Integer mediaFileId;

        public PodcastChannelInfo(PodcastChannel channel, int fileCount, int downloadedCount) {
            super(channel.getId(), channel.getUrl(), channel.getTitle(), channel.getDescription(),
                    channel.getImageUrl(), channel.getStatus(), channel.getErrorMessage(), channel.getMediaFile());
            this.fileCount = fileCount;
            this.downloadedCount = downloadedCount;
            this.mediaFileId = channel.getMediaFile() != null ? channel.getMediaFile().getId() : null;
        }

        public Integer getMediaFileId() {
            return mediaFileId;
        }

        public void setMediaFileId(Integer mediaFileId) {
            this.mediaFileId = mediaFileId;
        }

        public int getFileCount() {
            return fileCount;
        }

        public void setFileCount(int fileCount) {
            this.fileCount = fileCount;
        }

        public int getDownloadedCount() {
            return downloadedCount;
        }

        public void setDownloadedCount(int downloadedCount) {
            this.downloadedCount = downloadedCount;
        }

    }

}
