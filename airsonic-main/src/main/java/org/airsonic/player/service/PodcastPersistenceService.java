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
package org.airsonic.player.service;

import org.airsonic.player.command.PodcastSettingsCommand.PodcastRule;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastChannelRule;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastExportOPML;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.repository.PodcastChannelRepository;
import org.airsonic.player.repository.PodcastEpisodeRepository;
import org.airsonic.player.repository.PodcastRuleRepository;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.NetworkUtil;
import org.airsonic.player.util.PodcastUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;

import static java.util.stream.Collectors.toSet;

/**
 * Provides services for Podcast reception.
 *
 * @author Sindre Mehus
 */
@Service
public class PodcastPersistenceService {

    private static final Logger LOG = LoggerFactory.getLogger(PodcastPersistenceService.class);

    private static final Namespace[] ITUNES_NAMESPACES = {
            Namespace.getNamespace("http://www.itunes.com/DTDs/Podcast-1.0.dtd"),
            Namespace.getNamespace("http://www.itunes.com/dtds/podcast-1.0.dtd") };


    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final PodcastChannelRepository podcastChannelRepository;
    private final PodcastEpisodeRepository podcastEpisodeRepository;
    private final PodcastRuleRepository podcastRuleRepository;
    private final SecurityService securityService;
    private final SettingsService settingsService;

    private Predicate<PodcastEpisode> filterAllowed;
    private final ReentrantLock deleteObsoleteLock = new ReentrantLock();

    public PodcastPersistenceService(
        SecurityService securityService,
        MediaFileService mediaFileService,
        MediaFolderService mediaFolderService,
        AsyncWebSocketClient asyncSocketClient,
        PodcastChannelRepository podcastChannelRepository,
        PodcastEpisodeRepository podcastEpisodeRepository,
        PodcastRuleRepository podcastRuleRepository,
        SettingsService settingsService
    ) {
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.podcastChannelRepository = podcastChannelRepository;
        this.podcastEpisodeRepository = podcastEpisodeRepository;
        this.podcastRuleRepository = podcastRuleRepository;
        this.securityService = securityService;
        this.settingsService = settingsService;
        filterAllowed = episode -> episode.getMediaFile() == null
            || this.securityService.isReadAllowed(episode.getMediaFile(), false);
    }


    /**
     * clean up partial downloads and reset channel status
     */
    public void cleanDownloadingEpisodes() {
        podcastChannelRepository.findAll()
            .stream()
            .flatMap(c -> podcastEpisodeRepository.findByChannelAndStatus(c, PodcastStatus.DOWNLOADING).stream())
            .filter(filterAllowed)
            .forEach(e -> {
                deleteEpisode(e, false);
                LOG.info("Deleted Podcast episode '{}' since download was interrupted.", e.getTitle());
            });

    }

    /**
     * reset channel status
     *
     * @param status status to reset
     * @return list of channel ids that were reset
     */
    @Transactional
    public List<Integer> resetChannelStatus(PodcastStatus status) {
        return podcastChannelRepository.findByStatus(status).stream()
            .map(c -> {
                c.setStatus(PodcastStatus.COMPLETED);
                podcastChannelRepository.save(c);
                LOG.info("Reset channel status '{}' since refresh was interrupted.", c.getTitle());
                return c.getId();
            }).toList();
    }

    /**
     * prepare channel for refresh
     *
     * @param channelId channel id to refresh
     * @return channel if refresh is prepared, null if channel is already refreshing or not found
     */
    @Transactional
    public PodcastChannel prepareRefreshChannel(Integer channelId) {
        return podcastChannelRepository.findById(channelId).map(channel -> {
            if (channel.getStatus() == PodcastStatus.DOWNLOADING) {
                LOG.warn("Channel '{}' already refreshing", channel.getTitle());
                return null;
            }
            channel.setStatus(PodcastStatus.DOWNLOADING);
            channel.setErrorMessage(null);
            podcastChannelRepository.save(channel);
            return channel;
        }).orElseGet(() -> {
            LOG.warn("Podcast channel with id {} not found", channelId);
            return null;
        });
    }


    /**
     * update channel by element and return updated channel
     *
     * @param channel channel to update
     * @param element element to update from
     * @return updated channel or original channel if element is null
     */
    @Nullable
    @Transactional
    public PodcastChannel updateChannelByElement(@Nonnull PodcastChannel channel, @Nullable Element element) {
        if (element == null) {
            return channel;
        }
        String channelTitle = StringUtil.removeMarkup(element.getChildTextTrim("title"));
        MediaFile mediaFile = createChannelDirectory(channelTitle);
        channel.setTitle(channelTitle);
        channel.setDescription(StringUtil.removeMarkup(element.getChildTextTrim("description")));
        channel.setImageUrl(PodcastUtil.sanitizeUrl(getChannelImageUrl(element), false));
        channel.setErrorMessage(null);
        channel.setMediaFile(mediaFile);
        return podcastChannelRepository.save(channel);
    };

    /**
     * set error status to channel and save
     *
     * @param channel channel to set error
     * @param errorMessage error message
     */
    @Transactional
    public void setChannelError(PodcastChannel channel, String errorMessage) {
        podcastChannelRepository.findById(channel.getId()).ifPresent(
            c -> {
                c.setStatus(PodcastStatus.ERROR);
                c.setErrorMessage(errorMessage);
                podcastChannelRepository.save(c);
            }
        );
    }

    /**
     * set completed status to channel and save
     *
     * @param channel channel to set completed
     */
    @Transactional
    public void setChannelCompleted(PodcastChannel channel) {
        podcastChannelRepository.findById(channel.getId()).ifPresent(
            c -> {
                c.setStatus(PodcastStatus.COMPLETED);
                podcastChannelRepository.save(c);
            }
        );
    }

    /**
     * Get channels without rule
     *
     * @return channels without rule
     */
    public List<PodcastChannel> getChannelsWithoutRule() {
        Set<Integer> ruleIds = podcastRuleRepository.findAll().parallelStream().map(r -> r.getId()).collect(toSet());
        return podcastChannelRepository.findAll().parallelStream().filter(c -> !ruleIds.contains(c.getId())).toList();
    }

    /**
     * Create podcast channel rule by command
     *
     * @param command command
     * @return PodcastChannelRule
     */
    @Transactional
    public PodcastChannelRule createOrUpdateChannelRuleByCommand(PodcastRule command) {

        if (command == null || !command.isValid()) {
            return null;
        }

        return podcastChannelRepository.findById(command.getId()).map(
            channel -> {
                PodcastChannelRule rule = podcastRuleRepository.findById(command.getId()).orElse(new PodcastChannelRule(command.getId()));
                rule.setDownloadCount(command.getEpisodeDownloadCount());
                rule.setRetentionCount(command.getEpisodeRetentionCount());
                rule.setCheckInterval(command.getInterval());
                podcastRuleRepository.save(rule);
                return rule;
            }
        ).orElseGet(
            () -> {
                LOG.warn("Podcast channel with id {} not found", command.getId());
                return null;
            }
        );
    }



    /**
     * Create channel directory
     *
     * @param channel Channel to create directory for
     * @return MediaFile for channel directory
     */
    private MediaFile createChannelDirectory(String channelTitle) {
        MusicFolder podcastFolder = mediaFolderService.getAllMusicFolders().stream()
                .filter(f -> f.getType() == Type.PODCAST).findFirst().orElse(null);

        if (podcastFolder == null || !Files.isWritable(podcastFolder.getPath())) {
            throw new RuntimeException("The podcasts directory " + podcastFolder + " isn't enabled or writeable.");
        }

        String relativeChannelDir = channelTitle != null ? StringUtil.fileSystemSafe(channelTitle)
                : RandomStringUtils.insecure().nextAlphanumeric(10);

        Path channelDir = podcastFolder.getPath().resolve(relativeChannelDir);

        if (!Files.exists(channelDir)) {
            try {
                Files.createDirectories(channelDir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create directory " + channelDir, e);
            }
        }

        return mediaFileService.getMediaFile(relativeChannelDir, podcastFolder);
    }

    private String getChannelImageUrl(Element channelElement) {
        String result = getITunesAttribute(channelElement, "image", "href");
        if (result == null) {
            Element imageElement = channelElement.getChild("image");
            if (imageElement != null) {
                result = imageElement.getChildTextTrim("url");
            }
        }
        return result;
    }

    private String getITunesAttribute(Element element, String childName, String attributeName) {
        for (Namespace ns : ITUNES_NAMESPACES) {
            Element elem = element.getChild(childName, ns);
            if (elem != null) {
                return StringUtils.trimToNull(elem.getAttributeValue(attributeName));
            }
        }
        return null;
    }

    /**
     * Delete podcast channel rule by id
     *
     * @param id id of podcast channel rule
     * @param deleteChannel delete channel or not
     */
    @Transactional
    public boolean deleteChannelRule(Integer id) {
        return podcastRuleRepository.findById(id).map(rule -> {
            podcastRuleRepository.delete(rule);
            return true;
        }).orElseGet(
            () -> {
                LOG.warn("Podcast channel rule with id {} not found", id);
                return false;
            }
        );
    }

    public PodcastChannelRule getChannelRule(Integer id) {
        return podcastRuleRepository.findById(id).orElse(null);
    }

    public List<PodcastChannelRule> getAllChannelRules() {
        return podcastRuleRepository.findAll();
    }

    @Transactional
    public PodcastChannel createChannel(String url) {
        if (!NetworkUtil.isValidUrl(url)) {
            LOG.warn("Invalid Podcast URL: {}", url);
            return null;
        }
        PodcastChannel channel = new PodcastChannel(PodcastUtil.sanitizeUrl(url, false));
        podcastChannelRepository.save(channel);
        return channel;
    }

    /**
     * Returns a single Podcast channel.
     */
    public PodcastChannel getChannel(Integer channelId) {
        if (Objects.isNull(channelId)) return null;
        return podcastChannelRepository.findById(channelId).orElse(null);
    }

    /**
     * Returns all Podcast channels.
     *
     * @return Possibly empty list of all Podcast channels.
     */
    public List<PodcastChannel> getAllChannels() {
        return podcastChannelRepository.findAll();
    }

    /**
     * Returns all Podcast episodes for a given channel.
     *
     * @param channelId      The Podcast channel ID.
     * @return Possibly empty list of all Podcast episodes for the given channel, sorted in
     *         reverse chronological order (newest episode first).
     */
    @Transactional
    public List<PodcastEpisode> getEpisodes(Integer channelId) {
        if (Objects.isNull(channelId)) return new ArrayList<>();
        return podcastChannelRepository.findById(channelId).map(channel -> {
            return podcastEpisodeRepository.findByChannel(channel).stream()
                .filter(filterAllowed)
                .map(ep -> {
                    MediaFile mediaFile = ep.getMediaFile();
                    if (mediaFile != null) {
                        // Refresh media file to check if it still exists
                        mediaFileService.refreshMediaFile(mediaFile);
                        if (!mediaFile.isPresent() && ep.getStatus() != PodcastStatus.DELETED && !ep.isLocked()) {
                            LOG.info("Media file '{}' for episode '{}' is not present anymore. Setting episode status to deleted.", mediaFile.getId(), ep.getTitle());
                            // If media file is not present anymore, set episode status to deleted
                            ep.setStatus(PodcastStatus.DELETED);
                            ep.setErrorMessage(null);
                            podcastEpisodeRepository.save(ep);
                        }
                    }
                    return ep;
                })
                .sorted(Comparator.comparing(PodcastEpisode::getPublishDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        }).orElseGet(() -> {
            LOG.warn("Podcast channel with id {} not found", channelId);
            return new ArrayList<>();
        });
    }

    @Transactional
    public Page<PodcastEpisode> getEpisodes(Integer channelId, Pageable pageable) {
        List<PodcastEpisode> episodes = getEpisodes(channelId);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), episodes.size());

        List<PodcastEpisode> result = episodes.size() > start ? episodes.subList(start, end) : Collections.emptyList();

        return new PageImpl<>(result, pageable, episodes.size());
    }

    /**
     * Returns the N newest episodes.
     *
     * @return Possibly empty list of the newest Podcast episodes, sorted in
     *         reverse chronological order (newest episode first).
     */
    public List<PodcastEpisode> getNewestEpisodes(int count) {
        List<PodcastEpisode> episodes = podcastEpisodeRepository.findByStatusAndPublishDateNotNullAndMediaFilePresentTrueOrderByPublishDateDesc(PodcastStatus.COMPLETED);
        if (count > episodes.size()) {
            return episodes;
        }
        return episodes.subList(0, count);
    }


    /**
     * Returns a single Podcast episode.
     *
     * @param episodeId     The Podcast episode ID.
     * @param includeDeleted include deleted episodes
     * @return The Podcast episode, or null if not found.
     */
    @Transactional
    public PodcastEpisode getEpisode(int episodeId, boolean includeDeleted) {
        return podcastEpisodeRepository.findById(episodeId)
                .map(ep -> {
                    MediaFile mediaFile = ep.getMediaFile();
                    if (mediaFile != null) {
                        // Refresh media file to check if it still exists
                        mediaFileService.refreshMediaFile(mediaFile);
                        if (!mediaFile.isPresent() && ep.getStatus() != PodcastStatus.DELETED && !ep.isLocked()) {
                            deleteEpisode(ep, true);
                        }
                    }
                    return ep;
                })
                .filter(episode -> includeDeleted || episode.getStatus() != PodcastStatus.DELETED)
                .orElse(null);
    }

    /**
     * export all channels to OPML
     *
     * @return PodcastExportOPML with all channels
     */
    public PodcastExportOPML exportAllChannels() {
        PodcastExportOPML opml = new PodcastExportOPML();
        podcastChannelRepository.findAll().forEach(c -> {
            PodcastExportOPML.Outline outline = new PodcastExportOPML.Outline();
            outline.setText(c.getTitle());
            outline.setXmlUrl(c.getUrl());
            opml.getBody().getOutline().get(0).getOutline().add(outline);
        });

        return opml;
    }

    /**
     * Prepare episode for download
     *
     * @param episodeId episode id to prepare
     * @return episode if download is prepared, null if episode is already downloading or not found
     */
    @Transactional
    public PodcastEpisode prepareDownloadEpisode(Integer episodeId) {
        return podcastEpisodeRepository.findById(episodeId).map(episode -> {
            if (episode.getStatus() == PodcastStatus.DELETED) {
                LOG.info("Episode '{}' was already deleted. Aborting download", episode.getUrl());
                return null;
            }
            if (episode.getStatus() == PodcastStatus.DOWNLOADING
                || episode.getStatus() == PodcastStatus.COMPLETED) {
                LOG.info("Episode '{}' is already (being) downloaded. Aborting download.", episode.getTitle());
                return null;
            }
            episode.setStatus(PodcastStatus.DOWNLOADING);
            podcastEpisodeRepository.save(episode);
            return episode;
        }).orElseGet(() -> {
            LOG.info("Podcast episode with id {} not found", episodeId);
            return null;
        });
    }


    public boolean isEpisodeDeleted(Integer episodeId) {
        return podcastEpisodeRepository.findById(episodeId)
            .filter(ep -> ep.getStatus() != PodcastStatus.DELETED)
            .isEmpty();
    }

    /**
     * Create episode from fields
     *
     * @param channel channel to create episode for
     * @param guid episode guid
     * @param url episode url
     * @param title episode title
     * @param description episode description
     * @param date episode publish date
     * @param duration episode duration
     * @param length episode length
     * @return created episode
     */
    @Transactional
    public PodcastEpisode createEpisode(PodcastChannel channel, String guid, String url, String title, String description, Instant date, String duration, Long length) {
        PodcastEpisode episode = new PodcastEpisode(null, channel, guid, url, null, title, description,
                        date,
                        duration, length, 0L, PodcastStatus.NEW, null);
        LOG.info("Created Podcast episode {}", title);
        return podcastEpisodeRepository.save(episode);
    }

    /**
     * update episode by element and return updated episode
     *
     * @param episode episode to update
     * @return updated episode or original episode if element is null
     */
    @Transactional
    public PodcastEpisode updateEpisode(PodcastEpisode episode) {
        return podcastEpisodeRepository.findById(episode.getId()).map(ep -> {
            ep.setTitle(episode.getTitle());
            ep.setDescription(episode.getDescription());
            ep.setPublishDate(episode.getPublishDate());
            ep.setUrl(episode.getUrl());
            ep.setMediaFile(episode.getMediaFile());
            ep.setBytesDownloaded(episode.getBytesDownloaded());
            ep.setErrorMessage(episode.getErrorMessage());
            ep.setStatus(episode.getStatus());
            ep.setLocked(episode.isLocked());
            return podcastEpisodeRepository.save(ep);
        }).orElseGet(() -> {
            LOG.warn("Podcast episode with id {} not found", episode.getId());
            return null;
        });
    }

    /**
     * Deletes the Podcast channel with the given ID.
     *
     * @param channelId The Podcast channel ID.
     * @return Whether the channel was deleted.
     */
    @Transactional
    public boolean deleteChannel(int channelId) {
        // Delete all associated episodes (in case they have files that need to be deleted).
        return podcastChannelRepository.findById(channelId).map(channel -> {
            // Sequential on purpose: transactions are thread-bound, so parallel workers would run
            // mediaFileService.delete in their own transactions and commit mid-delete, colliding
            // with this transaction's flush (#330 — MariaDB snapshot isolation rejects the write).
            podcastEpisodeRepository.findByChannel(channel).stream()
                .filter(filterAllowed)
                .forEach(episode -> {
                    MediaFile mediaFile = episode.getMediaFile();
                    if (mediaFile != null) {
                        FileUtil.delete(mediaFile.getFullPath());
                        mediaFileService.delete(mediaFile);
                    }
                });
            MediaFile mediaFile = channel.getMediaFile();
            if (mediaFile != null) {
                FileUtil.delete(mediaFile.getFullPath());
                mediaFileService.delete(mediaFile);
            }
            podcastChannelRepository.delete(channel);
            return true;
        }).orElse(false);

    }

    /**
     * Deletes the Podcast episode with the given ID.
     *
     * @param episodeId     The Podcast episode ID.
     * @param logicalDelete Whether to perform a logical delete by setting the
     *                      episode status to {@link PodcastStatus#DELETED}.
     */
    @Transactional
    public void deleteEpisode(int episodeId, boolean logicalDelete) {
        podcastEpisodeRepository.findById(episodeId).ifPresentOrElse(e -> {
            deleteEpisode(e, logicalDelete);
        }, () -> {
                LOG.warn("Podcast episode with id {} not found", episodeId);
            });
    }

    private void deleteEpisode(@Nullable PodcastEpisode episode, boolean logicalDelete) {
        if (episode == null) {
            return;
        }

        // Check if episode is locked
        if (episode.getStatus() == PodcastStatus.COMPLETED && episode.isLocked()) {
            LOG.info("Episode '{}' is locked and cannot be deleted", episode.getTitle());
            return;
        }

        // Delete file and update mediaFile
        MediaFile file = episode.getMediaFile();
        if (file != null) {
            FileUtil.delete(file.getFullPath());
            mediaFileService.delete(file);
        }

        if (logicalDelete) {
            episode.setStatus(PodcastStatus.DELETED);
            episode.setErrorMessage(null);
            podcastEpisodeRepository.save(episode);
        } else {
            podcastEpisodeRepository.delete(episode);
        }
    }


    public PodcastEpisode getEpisodeByUrl(PodcastChannel channel, String url) {
        return podcastEpisodeRepository.findByChannelAndUrl(channel, url)
                .filter(filterAllowed)
                .orElse(null);
    }

    public PodcastEpisode getEpisodeByGuid(PodcastChannel channel, String guid) {
        return podcastEpisodeRepository.findByChannelAndEpisodeGuid(channel, guid)
                .filter(filterAllowed)
                .orElse(null);
    }

    public PodcastEpisode getEpisodeByTitleAndDate(PodcastChannel channel, String title, Instant date) {
        return podcastEpisodeRepository.findByChannelAndTitleAndPublishDate(channel, title, date)
                .filter(filterAllowed)
                .orElse(null);
    }

    /**
     * Deletes obsolete episodes for a given channel.
     *
     * @param channel The Podcast channel.
     */
    @Transactional
    public void deleteObsoleteEpisodes(@Nullable PodcastChannel channel) {
        this.deleteObsoleteLock.lock();
        try {
            int episodeCount = Optional.ofNullable(channel)
                    .flatMap(ch -> podcastRuleRepository.findById(ch.getId()))
                    .map(cr -> cr.getRetentionCount())
                    .orElse(settingsService.getPodcastEpisodeRetentionCount());
            if (episodeCount == -1) {
                return;
            }

            // Get all unlocked episodes of the channel
            List<PodcastEpisode> episodes = getUnlockedEpisodes(channel);

            // Don't do anything if other episodes of the same channel is currently
            // downloading.
            if (episodes.parallelStream().anyMatch(episode -> episode.getStatus() == PodcastStatus.DOWNLOADING)) {
                return;
            }

            int numEpisodes = episodes.size();
            int episodesToDelete = Math.max(0, numEpisodes - episodeCount);
            // Delete in reverse to get chronological order (oldest episodes first).
            for (int i = 0; i < episodesToDelete; i++) {
                deleteEpisode(episodes.get(numEpisodes - 1 - i), true);
                LOG.info("Deleted old Podcast episode {}", episodes.get(numEpisodes - 1 - i).getUrl());
            }
        } finally {
            this.deleteObsoleteLock.unlock();
        }
    }

    /**
     * Returns Unlocked Podcast episodes for a given channel.
     *
     * @param channel      The Podcast channel
     * @return Possibly empty list of Unlocked Podcast episodes for the given channel, sorted in
     *         reverse chronological order (newest episode first).
     */
    @Nonnull
    private List<PodcastEpisode> getUnlockedEpisodes(PodcastChannel channel) {
        if (Objects.isNull(channel)) return new ArrayList<>();
        return podcastEpisodeRepository.findByChannelAndLockedFalse(channel).stream()
            .filter(filterAllowed)
            .sorted(Comparator.comparing(PodcastEpisode::getPublishDate, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();
    }

    /**
     * Lock episode
     *
     * @param episodeId episode id to lock
     */
    @Transactional
    public void lockEpisode(int episodeId) {
        LOG.info("Locking episode with id {}", episodeId);
        podcastEpisodeRepository.findById(episodeId).ifPresentOrElse(episode -> {
            episode.setLocked(true);
            podcastEpisodeRepository.save(episode);
        }, () -> {
            LOG.warn("Podcast episode with id {} not found", episodeId);
        });
    }

    /**
     * Unlock episode
     *
     * @param episodeId episode id to unlock
     */
    @Transactional
    public void unlockEpisode(int episodeId) {
        LOG.info("Unlocking episode with id {}", episodeId);
        podcastEpisodeRepository.findById(episodeId).ifPresentOrElse(episode -> {
            episode.setLocked(false);
            podcastEpisodeRepository.save(episode);
        }, () -> {
            LOG.warn("Podcast episode with id {} not found", episodeId);
        });
    }


    @Transactional
    public void resetEpisode(Integer episodeId) {
        podcastEpisodeRepository.findById(episodeId).ifPresentOrElse(episode -> {
            switch (episode.getStatus()) {
                case DELETED -> {
                    episode.setLocked(true); // prevent to be deleted again
                    episode.setStatus(PodcastStatus.NEW);
                    episode.setErrorMessage(null);
                    podcastEpisodeRepository.save(episode);
                }
                case COMPLETED -> {
                    episode.setStatus(PodcastStatus.NEW);
                    episode.setErrorMessage(null);
                    podcastEpisodeRepository.save(episode);
                }
                default -> LOG.warn("Episode '{}' is not in a state that can be reset", episode.getTitle());
            }
        }, () -> {
            LOG.warn("Podcast episode with id {} not found", episodeId);
        });
    }
}
