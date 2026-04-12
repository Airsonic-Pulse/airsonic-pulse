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

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.*;
import org.airsonic.player.domain.dto.CoverArtRequest;
import org.airsonic.player.domain.dto.PlaylistCoverArtRequest;
import org.airsonic.player.domain.dto.VideoCoverArtRequest;
import org.airsonic.player.service.*;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.ImageUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Controller which produces cover art images.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({ "/coverArt", "/ext/coverArt", "/coverArt.view", "/ext/coverArt.view" })
public class CoverArtController {

    public static final String ALBUM_COVERART_PREFIX = "al-";
    public static final String ARTIST_COVERART_PREFIX = "ar-";
    public static final String PLAYLIST_COVERART_PREFIX = "pl-";
    public static final String PODCAST_COVERART_PREFIX = "pod-";

    // Version of the cover art generation algorithm.
    // Increment this if the algorithm changes.
    private static final int COVERART_VERSION = 1;

    static final Logger LOG = LoggerFactory.getLogger(CoverArtController.class);

    @Autowired
    MediaFileService mediaFileService;
    @Autowired
    CoverArtService coverArtService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    PlaylistService playlistService;
    @Autowired
    private CoverArtCreateService coverArtCreateService;
    @Autowired
    private AirsonicHomeConfig homeConfig;

    private Semaphore semaphore;
    private final ConcurrentHashMap<String, ReentrantLock> coverArtLocks = new ConcurrentHashMap<>();
    private final ReentrantLock cacheDirectoryLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        semaphore = new Semaphore(settingsService.getCoverArtConcurrency());
    }

    /**
     * get last modified time epoch millisecond
     *
     * @param coverArtRequest target coverArtRequest
     * @return last modified time in epoch milliseconds. if coverArtRequest id null,
     *         then return -1L
     */
    /*
    private long getLastModifiedMiili(CoverArtRequest coverArtRequest) {
        if (coverArtRequest == null) {
            return -1L;
        }
        return coverArtRequest.lastModified().toEpochMilli();
    }
    */

    @GetMapping
    public void get(
            @RequestParam(name = "id", required = false) String id,
            @RequestParam(name = "size", required = false) Integer size,
            @RequestParam(name = "offset", defaultValue = "60") int offset,
            HttpServletRequest request,
            HttpServletResponse response) throws Exception {

        CoverArtRequest coverArtRequest = createCoverArtRequest(id, offset);
        LOG.trace("handleRequest - {}", coverArtRequest);

        // check modified by last modified
        // if((new ServletWebRequest(request, response)).checkNotModified(getLastModifiedMiili(coverArtRequest))) return;

        // Send fallback image if no ID is given. (No need to cache it, since it will be cached in browser.)
        if (coverArtRequest == null) {
            sendFallback(size, response);
            return;
        }

        try {
            // Optimize if no scaling is required.
            if (size == null && coverArtRequest.getCoverArt() != null) {
                LOG.trace("sendUnscaled - {}", coverArtRequest);
                sendUnscaled(coverArtRequest, response);
                return;
            }

            // Send cached image, creating it if necessary.
            if (size == null) {
                size = CoverArtScheme.LARGE.getSize() * 2;
            }
            Path cachedImage = getCachedImage(coverArtRequest, size);
            sendImage(cachedImage, response);
        } catch (Exception e) {
            LOG.debug("Sending fallback as an exception was encountered during normal cover art processing", e);
            sendFallback(size, response);
        }

    }

    private CoverArtRequest createCoverArtRequest(String id, int offset) {
        if (id == null) {
            return null;
        }

        if (id.startsWith(ALBUM_COVERART_PREFIX)) {
            return coverArtCreateService.createAlbumCoverArtRequest(Integer.valueOf(id.replace(ALBUM_COVERART_PREFIX, "")));
        }
        if (id.startsWith(ARTIST_COVERART_PREFIX)) {
            return coverArtCreateService.createArtistCoverArtRequest(Integer.valueOf(id.replace(ARTIST_COVERART_PREFIX, "")));
        }
        if (id.startsWith(PLAYLIST_COVERART_PREFIX)) {
            return coverArtCreateService.createPlaylistCoverArtRequest(Integer.valueOf(id.replace(PLAYLIST_COVERART_PREFIX, "")));
        }
        if (id.startsWith(PODCAST_COVERART_PREFIX)) {
            return coverArtCreateService.createPodcastCoverArtRequest(Integer.valueOf(id.replace(PODCAST_COVERART_PREFIX, "")), offset);
        }
        return coverArtCreateService.createMediaFileCoverArtRequest(Integer.valueOf(id), offset);
    }


    private void sendImage(Path file, HttpServletResponse response) throws IOException {
        response.setContentType(StringUtil.getMimeType(FilenameUtils.getExtension(file.toString())));
        Files.copy(file, response.getOutputStream());
    }

    private void sendFallback(Integer size, HttpServletResponse response) throws IOException {
        if (response.getContentType() == null) {
            response.setContentType(StringUtil.getMimeType("jpeg"));
        }
        try (InputStream in = getClass().getResourceAsStream("default_cover.jpg")) {
            BufferedImage image = ImageIO.read(in);
            if (size != null) {
                image = ImageUtil.scale(image, size, size);
            }
            ImageIO.write(image, "jpeg", response.getOutputStream());
        }
    }

    private void sendUnscaled(CoverArtRequest coverArtRequest, HttpServletResponse response) throws IOException {
        Pair<InputStream, String> imageInputStreamWithType = coverArtCreateService.getImageInputStreamWithType(
                coverArtRequest.getCoverArt().getFullPath());

        try (InputStream in = imageInputStreamWithType.getLeft()) {
            if (in == null) {
                throw new FileNotFoundException("Cover art not found");
            }
            response.setContentType(imageInputStreamWithType.getRight());
            IOUtils.copy(in, response.getOutputStream());
        }
    }

    private Path getCachedImage(CoverArtRequest request, int size) throws IOException {
        String hash = DigestUtils.md5Hex(request.getKey() + "-" + COVERART_VERSION);
        String encoding = request.getCoverArt() != null ? "jpeg" : "png";
        Path cachedImage = getImageCacheDirectory(size).resolve(hash + "." + encoding);

        // Use per-hash lock so different cover art hashes don't block each other.
        ReentrantLock hashLock = this.coverArtLocks.computeIfAbsent(hash, k -> new ReentrantLock());
        hashLock.lock();
        try {

            // Is cache missing or obsolete?
            if (!Files.exists(cachedImage) || request.lastModified().isAfter(FileUtil.lastModified(cachedImage))) {
//                LOG.info("Cache MISS - " + request + " (" + size + ")");
                ImageWriter writer = null;

                try (OutputStream os = Files.newOutputStream(cachedImage);
                        BufferedOutputStream bos = new BufferedOutputStream(os);
                        ImageOutputStream out = ImageIO.createImageOutputStream(bos)) {
                    semaphore.acquire();
                    BufferedImage image = null;
                    if (request instanceof PlaylistCoverArtRequest pr) {
                        image = coverArtCreateService.createPlaylistImage(pr, size);
                    } else if (request instanceof VideoCoverArtRequest vr) {
                        image = coverArtCreateService.createVideoImage(vr, size);
                    } else {
                        image = coverArtCreateService.createImage(request, size);
                    }
                    if (image == null) {
                        throw new Exception("Unable to decode image.");
                    }
                    writer = ImageIO.getImageWritersByFormatName(encoding).next();

                    float quality = (float) (settingsService.getCoverArtQuality() / 100.0);
                    ImageWriteParam params = writer.getDefaultWriteParam();
                    params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    params.setCompressionQuality(quality); // default is 0.75

                    writer.setOutput(out);
                    writer.write(null, new IIOImage(image, null, null), params);

                } catch (Throwable x) {
                    // Delete corrupt (probably empty) thumbnail cache.
                    LOG.warn("Failed to create thumbnail for {}", request, x);
                    FileUtil.delete(cachedImage);
                    throw new IOException("Failed to create thumbnail for " + request + ". " + x.getMessage());
                } finally {
                    if (writer != null) {
                        writer.dispose();
                        writer = null;
                    }
                    semaphore.release();
                }
            } else {
//                LOG.info("Cache HIT - " + request + " (" + size + ")");
            }
            return cachedImage;
        } finally {
            hashLock.unlock();
        }
    }

    private Path getImageCacheDirectory(int size) {
        this.cacheDirectoryLock.lock();
        try {
            Path dir = homeConfig.getAirsonicHome().resolve("thumbs").resolve(String.valueOf(size));
            if (!Files.exists(dir)) {
                try {
                    dir = Files.createDirectories(dir);
                    LOG.info("Created thumbnail cache {}", dir);
                } catch (Exception e) {
                    LOG.error("Failed to create thumbnail cache {}", dir, e);
                }
            }

            return dir;
        } finally {
            this.cacheDirectoryLock.unlock();
        }
    }
}
