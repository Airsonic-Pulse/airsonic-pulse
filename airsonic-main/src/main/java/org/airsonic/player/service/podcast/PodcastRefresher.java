package org.airsonic.player.service.podcast;

import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.domain.entity.CoverArtKey;
import org.airsonic.player.repository.CoverArtRepository;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.VersionService;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.airsonic.player.util.PodcastUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.airsonic.player.util.XMLUtil.createSAXBuilder;

@Service
@EnableAsync(mode = AdviceMode.ASPECTJ)
public class PodcastRefresher {
    private static final Logger LOG = LoggerFactory.getLogger(PodcastRefresher.class);

    private static final Namespace[] ITUNES_NAMESPACES = {
            Namespace.getNamespace("http://www.itunes.com/DTDs/Podcast-1.0.dtd"),
            Namespace.getNamespace("http://www.itunes.com/dtds/podcast-1.0.dtd") };

    private static final DateTimeFormatter ALTERNATIVE_RSS_DATE_FORMAT = DateTimeFormatter
            .ofPattern("[E, ]d MMM y HH:mm[:ss] z");

    @Autowired
    private PodcastPersistenceService podcastPersistenceService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private VersionService versionService;

    @Autowired
    private CoverArtRepository coverArtRepository;

    @Autowired
    private AsyncWebSocketClient asyncWebSocketClient;

    /**
     * refresh channel
     *
     * @param channelId        channel id to refresh
     * @param downloadEpisodes whether to download episodes. if false, channel
     *                         status will be set to completed after refresh.
     *                         otherwise, it will be set to downloading
     * @return true if channel was refreshed successfully
     */
    @Async("PodcastRefreshThreadPool")
    public CompletableFuture<Boolean> refresh(Integer channelId, boolean downloadEpisodes) {
        PodcastChannel channel = podcastPersistenceService.prepareRefreshChannel(channelId);
        if (channel == null) {
            LOG.warn("Channel {} not found", channelId);
            return CompletableFuture.completedFuture(false);
        }
        asyncWebSocketClient.send("/topic/podcasts/updated", channel.getId());
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(2 * 60 * 1000) // 2 minutes
                .setSocketTimeout(10 * 60 * 1000) // 10 minutes
                .build();
        HttpGet method = new HttpGet(channel.getUrl());
        method.setConfig(requestConfig);
        method.addHeader("User-Agent", "Airsonic/" + versionService.getLocalVersion());
        try (CloseableHttpClient client = HttpClients.createDefault();
                CloseableHttpResponse response = client.execute(method);
                InputStream in = response.getEntity().getContent()) {

            Document document = createSAXBuilder().build(in);
            Element channelElement = document.getRootElement().getChild("channel");

            podcastPersistenceService.updateChannelByElement(channel, channelElement);
            asyncWebSocketClient.send("/topic/podcasts/updated", channel.getId());
            downloadImage(channel);
            refreshEpisodes(channel, channelElement.getChildren("item"));
        } catch (Exception x) {
            LOG.warn("Failed to get/parse RSS file for Podcast channel {}", channel.getUrl(), x);
            podcastPersistenceService.setChannelError(channel, PodcastUtil.getErrorMessage(x));
            asyncWebSocketClient.send("/topic/podcasts/updated", channel.getId());
            return CompletableFuture.completedFuture(false);
        }

        if (!downloadEpisodes) {
            podcastPersistenceService.setChannelCompleted(channel);
            asyncWebSocketClient.send("/topic/podcasts/updated", channel.getId());
        }
        return CompletableFuture.completedFuture(true);
    }

    private String formatDuration(String duration) {
        if (duration == null)
            return null;
        if (duration.matches("^\\d+$")) {
            long seconds = Long.valueOf(duration);
            return StringUtil.formatDuration(seconds * 1000);
        } else {
            return duration;
        }
    }

    private String getITunesElement(Element element, String childName) {
        for (Namespace ns : ITUNES_NAMESPACES) {
            String value = element.getChildTextTrim(childName, ns);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private void downloadImage(PodcastChannel channel) {
        String imageUrl = channel.getImageUrl();
        if (imageUrl == null) {
            return;
        }

        MediaFile channelMediaFile = channel.getMediaFile();

        if (channelMediaFile == null
                || coverArtRepository.existsById(new CoverArtKey(channelMediaFile.getId(), EntityType.MEDIA_FILE))) {
            // if its already there, no need to download it again
            return;
        }

        MusicFolder folder = channelMediaFile.getFolder();
        Path channelDir = channelMediaFile.getFullPath();

        HttpGet method = new HttpGet(imageUrl);
        method.addHeader("User-Agent", "Airsonic/" + versionService.getLocalVersion());
        try (CloseableHttpClient client = HttpClients.createDefault();
                CloseableHttpResponse response = client.execute(method);
                InputStream in = response.getEntity().getContent()) {
            Path filePath = channelDir.resolve("cover." + getCoverArtSuffix(response));
            Files.copy(in, filePath, StandardCopyOption.REPLACE_EXISTING);
            CoverArt coverArt = new CoverArt(channelMediaFile.getId(), EntityType.MEDIA_FILE,
                    folder.getPath().relativize(filePath).toString(), channelMediaFile.getFolder(), false);
            coverArtRepository.save(coverArt);
        } catch (Exception x) {
            LOG.warn("Failed to download cover art for podcast channel '{}'", channel.getTitle(), x);
        }
    }

    private String getCoverArtSuffix(HttpResponse response) {
        String result = null;
        Header contentTypeHeader = response.getEntity().getContentType();
        if (contentTypeHeader != null && contentTypeHeader.getValue() != null) {
            ContentType contentType = ContentType.parse(contentTypeHeader.getValue());
            String mimeType = contentType.getMimeType();
            result = StringUtil.getSuffix(mimeType);
        }
        return result == null ? "jpeg" : result;
    }

    private void refreshEpisodes(PodcastChannel channel, List<Element> episodeElements) {
        // Create episodes in database, skipping the proper number of episodes.
        int downloadCount = Optional.ofNullable(channel).map(ch -> podcastPersistenceService.getChannelRule(ch.getId()))
                .map(cr -> cr.getDownloadCount())
                .orElse(settingsService.getPodcastEpisodeDownloadCount());
        if (downloadCount == -1) {
            downloadCount = Integer.MAX_VALUE;
        }

        AtomicInteger counter = new AtomicInteger(downloadCount);

        // Sequential on purpose: transactions are thread-bound, so parallel workers would run
        // updateEpisode/createEpisode in their own transactions on ForkJoin threads while sharing
        // the detached channel and episode entities across threads (#353 — same class of defect
        // as the #345 delete loop). The work here is DB-bound dedup only; the network-bound feed
        // fetch happens before this loop, and channel-level concurrency stays on the refresh pool.
        episodeElements.stream()
                .map(episodeElement -> {
                    String title = StringUtil.removeMarkup(episodeElement.getChildTextTrim("title"));
                    String guid = StringUtil.removeMarkup(episodeElement.getChildTextTrim("guid"));
                    Instant date = parseDate(episodeElement.getChildTextTrim("pubDate"));

                    Element enclosure = episodeElement.getChild("enclosure");
                    if (enclosure == null) {
                        LOG.info("No enclosure found for episode {}", title);
                        return null;
                    }

                    String url = PodcastUtil.sanitizeUrl(enclosure.getAttributeValue("url"), false);
                    if (url == null) {
                        LOG.info("No enclosure URL found for episode {}", title);
                        return null;
                    }

                    // make sure episode with same guid doesn't exist
                    if (StringUtils.isNotBlank(guid)) {
                        if (podcastPersistenceService.getEpisodeByGuid(channel, guid) != null) {
                            LOG.info("Episode already exists for episode {} by guid {}", title, guid);
                            return null;
                        }
                    }

                    // make sure episode with same title and pub date doesn't exist
                    if (StringUtils.isNotBlank(title) && date != null) {
                        PodcastEpisode oldEpisode = podcastPersistenceService.getEpisodeByTitleAndDate(channel, title, date);
                        if (oldEpisode != null) {
                            // backfill
                            if (StringUtils.isBlank(oldEpisode.getEpisodeGuid()) && StringUtils.isNotBlank(guid)) {
                                oldEpisode.setEpisodeGuid(guid);
                                podcastPersistenceService.updateEpisode(oldEpisode);
                            }
                            LOG.info("Episode already exists for episode {} by title and pubdate {}", title, date);
                            return null;
                        }
                    }

                    // make sure episode with same url doesn't exist
                    PodcastEpisode oldEpisode = podcastPersistenceService.getEpisodeByUrl(channel, url);
                    if (oldEpisode != null) {
                        // backfill
                        if (StringUtils.isBlank(oldEpisode.getEpisodeGuid()) && StringUtils.isNotBlank(guid)) {
                            oldEpisode.setEpisodeGuid(guid);
                            podcastPersistenceService.updateEpisode(oldEpisode);
                        }
                        LOG.info("Episode already exists for episode {} by url {}", title, url);
                        return null;
                    }

                    String duration = formatDuration(getITunesElement(episodeElement, "duration"));
                    String description = StringUtil.removeMarkup(episodeElement.getChildTextTrim("description"));
                    if (StringUtils.isBlank(description)) {
                        description = getITunesElement(episodeElement, "summary");
                    }

                    Long length = null;
                    try {
                        length = Long.valueOf(enclosure.getAttributeValue("length"));
                    } catch (Exception x) {
                        LOG.warn("Failed to parse enclosure length.", x);
                    }
                    return podcastPersistenceService.createEpisode(channel, guid, url, title, description, date, duration, length);
                })
                .filter(Objects::nonNull)
                // Sort episode in reverse chronological order (newest first)
                .sorted(Comparator.comparing((PodcastEpisode episode) -> episode.getPublishDate()).reversed())
                .forEachOrdered(episode -> {
                    if (counter.decrementAndGet() < 0) {
                        episode.setStatus(PodcastStatus.SKIPPED);
                    }
                    podcastPersistenceService.updateEpisode(episode);
                });
    }

    private Instant parseDate(String s) {
        try {
            return OffsetDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (Exception x) {
            try {
                return ZonedDateTime.parse(s, ALTERNATIVE_RSS_DATE_FORMAT).toInstant();
            } catch (Exception e) {
                LOG.warn("Failed to parse publish date: {}", s);
                return null;
            }
        }
    }


}
