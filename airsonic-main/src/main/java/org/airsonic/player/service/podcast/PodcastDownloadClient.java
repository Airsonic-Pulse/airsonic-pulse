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
package org.airsonic.player.service.podcast;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.VersionService;
import org.airsonic.player.service.metadata.MetaData;
import org.airsonic.player.service.metadata.MetaDataParser;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.PodcastUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;

@Service
@EnableAsync(mode = AdviceMode.ASPECTJ)
public class PodcastDownloadClient {

    private final Logger LOG = LoggerFactory.getLogger(PodcastDownloadClient.class);
    private final ReentrantLock createEpisodeLock = new ReentrantLock();

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private PodcastPersistenceService podcastPersistenceService;

    @Autowired
    private MetaDataParserFactory metaDataParserFactory;

    @Autowired
    private VersionService versionService;

    @Autowired
    private SecurityService securityService;

    @Async("PodcastDownloadThreadPool")
    public CompletableFuture<Void> downloadEpisode(Integer episodeId) {

        CompletableFuture<Void> result = CompletableFuture.completedFuture(null);
        if (episodeId == null) {
            LOG.info("Episode id is null");
            return result;
        }

        PodcastEpisode episode = podcastPersistenceService.prepareDownloadEpisode(episodeId);
        if (episode != null && episode.getUrl() != null) {
            LOG.info("Starting to download Podcast from {}", episode.getUrl());

            PodcastChannel channel = episode.getChannel();
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(2 * 60 * 1000) // 2 minutes
                    .setSocketTimeout(10 * 60 * 1000) // 10 minutes
                    // Workaround HttpClient circular redirects, which some feeds use (with query
                    // parameters)
                    .setCircularRedirectsAllowed(true)
                    // Workaround HttpClient not understanding latest RFC-compliant cookie 'expires'
                    // attributes
                    .setCookieSpec(CookieSpecs.STANDARD)
                    .build();
            HttpGet method = new HttpGet(episode.getUrl());
            method.setConfig(requestConfig);
            method.addHeader("User-Agent", "Airsonic/" + versionService.getLocalVersion());
            Pair<Path, MusicFolder> episodeFile = createEpisodeFile(channel, episode);
            Path relativeFile = episodeFile.getLeft();
            MusicFolder folder = episodeFile.getRight();
            Path filePath = folder.getPath().resolve(relativeFile);

            try (CloseableHttpClient client = HttpClients.createDefault();
                    CloseableHttpResponse response = client.execute(method);
                    InputStream in = response.getEntity().getContent();
                    OutputStream out = new BufferedOutputStream(Files.newOutputStream(filePath))) {

                // Check if the server responded with a 200 OK status code.
                if (response.getStatusLine().getStatusCode() != HttpStatus.OK.value()) {
                    throw new IOException("Failed to download Podcast from " + episode.getUrl() + ". Status code: "
                            + response.getStatusLine().getStatusCode());
                }

                episode.setBytesDownloaded(0L);
                episode.setErrorMessage(null);
                podcastPersistenceService.updateEpisode(episode);

                long bytesDownloaded = 0L;
                byte[] buffer = new byte[8192];
                int n;
                long nextLogCount = 30000L;

                while ((n = in.read(buffer)) != -1) {
                    out.write(buffer, 0, n);
                    bytesDownloaded += n;

                    if (bytesDownloaded > nextLogCount) {
                        // Abort download if episode was deleted by user.
                        if (podcastPersistenceService.isEpisodeDeleted(episodeId)) {
                            break;
                        }
                        episode.setBytesDownloaded(bytesDownloaded);
                        podcastPersistenceService.updateEpisode(episode);
                        nextLogCount += 30000L;
                    }
                }
                episode.setBytesDownloaded(bytesDownloaded);
                LOG.info("Downloaded {} bytes from Podcast {}", bytesDownloaded, episode.getUrl());
            } catch (Exception x) {
                LOG.warn("Failed to download Podcast from {}", episode.getUrl(), x);
                episode.setStatus(PodcastStatus.ERROR);
                episode.setErrorMessage(PodcastUtil.getErrorMessage(x));
                podcastPersistenceService.updateEpisode(episode);
                return result;
            }

            // Abort download if episode was deleted by user.
            if (podcastPersistenceService.isEpisodeDeleted(episodeId)) {
                LOG.info("Podcast {} was deleted. Aborting download.", episode.getUrl());
                FileUtil.delete(filePath);
            } else {
                MediaFile file = mediaFileService.getMediaFile(relativeFile, folder);
                episode.setMediaFile(file);
                // Parser may not be able to determine duration for some formats.
                if (file.getDuration() == null) {
                    String errorMessage = "Failed to get duration for " + file;
                    LOG.warn(errorMessage);
                    episode.setStatus(PodcastStatus.ERROR);
                    episode.setErrorMessage(errorMessage);
                    podcastPersistenceService.updateEpisode(episode);
                } else {
                    updateTags(file, episode);
                    episode.setStatus(PodcastStatus.COMPLETED);
                    podcastPersistenceService.updateEpisode(episode);
                    podcastPersistenceService.deleteObsoleteEpisodes(channel);
                }
            }
        } else {
            LOG.info("Episode with id {} not found", episodeId);
        }
        return result;
    }

    private void updateTags(MediaFile file, PodcastEpisode episode) {
        try {
            Path fullPath = file.getFullPath();
            if (StringUtils.isNotBlank(episode.getTitle())) {
                MetaDataParser parser = metaDataParserFactory.getParser(fullPath);
                if (!parser.isEditingSupported()) {
                    return;
                }
                MetaData metaData = parser.getRawMetaData(fullPath);
                metaData.setTitle(episode.getTitle());
                parser.setMetaData(file, metaData);
                mediaFileService.refreshMediaFile(file);
            }
        } catch (Exception x) {
            LOG.warn("Failed to update tags for podcast {}", episode.getUrl(), x);
        }
    }


    private Pair<Path, MusicFolder> createEpisodeFile(PodcastChannel channel, PodcastEpisode episode) {
        this.createEpisodeLock.lock();
        try {
            String filename = StringUtil.getUrlFile(PodcastUtil.sanitizeUrl(episode.getUrl(), true));
            if (filename == null) {
                filename = episode.getTitle();
            }
            filename = StringUtil.fileSystemSafe(filename);
            String extension = FilenameUtils.getExtension(filename);
            filename = FilenameUtils.removeExtension(filename);
            if (StringUtils.isBlank(extension)) {
                extension = "mp3";
            }

            MediaFile channelMediaFile = channel.getMediaFile();
            MusicFolder folder = channelMediaFile.getFolder();
            Path channelDir = channelMediaFile.getFullPath();

            Path file = channelDir.resolve(filename + "." + extension);
            for (int i = 0; Files.exists(file); i++) {
                file = channelDir.resolve(filename + i + "." + extension);
            }
            Path relativeFile = folder.getPath().relativize(file);
            if (!securityService.isWriteAllowed(relativeFile, folder)) {
                throw new SecurityException("Access denied to file " + file);
            }
            try {
                Files.createFile(file);
            } catch (IOException e) {
                throw new RuntimeException("Failed to create file " + file, e);
            }

            return Pair.of(relativeFile, folder);
        } finally {
            this.createEpisodeLock.unlock();
        }
    }

}
