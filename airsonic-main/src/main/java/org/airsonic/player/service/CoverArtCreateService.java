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

package org.airsonic.player.service;

import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.Transcoding;
import org.airsonic.player.domain.VideoTranscodingSettings;
import org.airsonic.player.domain.dto.AlbumCoverArtRequest;
import org.airsonic.player.domain.dto.ArtistCoverArtRequest;
import org.airsonic.player.domain.dto.AutoCover;
import org.airsonic.player.domain.dto.CoverArtRequest;
import org.airsonic.player.domain.dto.MediaFileCoverArtRequest;
import org.airsonic.player.domain.dto.PlaylistCoverArtRequest;
import org.airsonic.player.domain.dto.PodcastCoverArtRequest;
import org.airsonic.player.domain.dto.VideoCoverArtRequest;
import org.airsonic.player.repository.AlbumRepository;
import org.airsonic.player.repository.ArtistRepository;
import org.airsonic.player.service.metadata.JaudiotaggerParser;
import org.airsonic.player.util.ImageUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import javax.imageio.IIOException;
import javax.imageio.ImageIO;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class CoverArtCreateService {

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private CoverArtService coverArtService;

    @Autowired
    private PlaylistService playlistService;

    @Autowired
    private PodcastPersistenceService podcastService;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private TranscodingService transcodingService;

    private static final Logger LOG = LoggerFactory.getLogger(CoverArtCreateService.class);

    /**
     * Create a cover art request for an album.
     *
     * @param albumId the album ID
     * @return the cover art request
     */
    @Nullable
    public CoverArtRequest createAlbumCoverArtRequest(Integer albumId) {
        if (albumId == null) {
            LOG.warn("Album ID is null");
            return null;
        }
        return albumRepository.findById(albumId).map(album -> {
            CoverArt coverArt = coverArtService.getAlbumArt(albumId);
            return new AlbumCoverArtRequest(coverArt, album);
        }).orElse(null);
    }

    @Nullable
    public CoverArtRequest createArtistCoverArtRequest(Integer artistId) {
        if (artistId == null) {
            LOG.warn("Artist ID is null");
            return null;
        }
        return artistRepository.findById(artistId).map(artist -> {
            CoverArt coverArt = coverArtService.getArtistArt(artistId);
            return new ArtistCoverArtRequest(coverArt, artist);
        }).orElse(null);
    }

    /**
     * Create a cover art request for a playlist.
     *
     * @param playlistId the playlist ID
     * @return the cover art request
     */
    @Nullable
    public CoverArtRequest createPlaylistCoverArtRequest(Integer playlistId) {
        if (playlistId == null) {
            LOG.warn("Playlist ID is null");
            return null;
        }
        Playlist playlist = playlistService.getPlaylist(playlistId);
        if (playlist == null) {
            LOG.warn("Playlist {} not found", playlistId);
            return null;
        }
        return new PlaylistCoverArtRequest(null, playlist);
    }

    /**
     * Create a cover art request for a podcast.
     *
     * @param podcastId the podcast ID
     * @param offset the offset
     * @return the cover art request
     */
    @Nullable
    public CoverArtRequest createPodcastCoverArtRequest(Integer podcastId, int offset) {
        if (podcastId == null) {
            LOG.warn("Podcast ID is null");
            return null;
        }
        PodcastChannel channel = podcastService.getChannel(podcastId);
        if (channel == null) {
            LOG.warn("Podcast {} not found", podcastId);
            return null;
        }

        MediaFile mediaFile = channel.getMediaFile();

        if (mediaFile == null) {
            return new PodcastCoverArtRequest(channel);
        }

        return createMediaFileCoverArtRequest(mediaFile, offset);
    }

    /**
     * Create a cover art request for a media file.
     *
     * @param mediaFileId the media file ID
     * @param offset the offset for video files. if mediaFile is not a video file, this parameter is ignored
     * @return the cover art request
     */
    @Nullable
    public CoverArtRequest createMediaFileCoverArtRequest(int mediaFileId, int offset) {
        MediaFile mediaFile = mediaFileService.getMediaFile(mediaFileId);
        return createMediaFileCoverArtRequest(mediaFile, offset);
    }

    /**
     * Create a cover art request for a media file.
     *
     * @param mediaFile the media file
     * @param offset the offset for video files. if mediaFile is not a video file, this parameter is ignored
     * @return the cover art request
     */
    @Nullable
    public CoverArtRequest createMediaFileCoverArtRequest(@Nullable MediaFile mediaFile, int offset) {
        if (mediaFile == null) {
            return null;
        }
        if (mediaFile.isVideo()) {
            return new VideoCoverArtRequest(mediaFile, offset);
        }
        MediaFile dir = mediaFile.isDirectory() ? mediaFile : mediaFileService.getParentOf(mediaFile);
        if (dir == null || !dir.isExist()) {
            return null;
        }
        CoverArt coverArt = coverArtService.getMediaFileArt(dir.getId());
        return new MediaFileCoverArtRequest(coverArt, dir, mediaFile.isDirectory() ? null : mediaFile.getId());
    }

    private BufferedImage createAutoCover(CoverArtRequest request, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        AutoCover autoCover = new AutoCover(graphics, request.getKey(), request.getArtist(), request.getAlbum(), width, height);
        autoCover.paintCover();
        graphics.dispose();
        return image;
    }


    public BufferedImage createImage(CoverArtRequest coverArtRequest, int size) {
        CoverArt coverArt = coverArtRequest.getCoverArt();
        if (coverArt != null) {
            try (InputStream in = this.getImageInputStream(coverArt)) {
                String reason = null;
                if (in == null) {
                    reason = "getImageInputStream";
                } else {
                    BufferedImage bimg = ImageIO.read(in);
                    if (bimg == null) {
                        reason = "ImageIO.read";
                    } else {
                        return ImageUtil.scaleToSquare(bimg, size);
                    }
                }
                LOG.warn("Failed to process cover art {}: {} failed", coverArt.getFullPath(), reason);
            } catch (IIOException x) {
                LOG.warn("Failed to process cover art {}: {}", coverArt.getFullPath(), "Bad image file");
                LOG.debug(x.getMessage(), x);
            } catch (Throwable x) {
                LOG.warn("Failed to process cover art {}", coverArt.getFullPath());
                LOG.debug(x.getMessage(), x);
            }
        }
        return createAutoCover(coverArtRequest, size, size);
    }

    /**
     * Create a playlist image.
     *
     * @param request the request for the playlist
     * @param size the size of the image
     * @return the image
     */
    @Nonnull
    public BufferedImage createPlaylistImage(PlaylistCoverArtRequest request, int size) {
        List<MediaFile> albums = playlistService.getFilesInPlaylist(request.getPlaylist().getId())
                .parallelStream()
                .map(mediaFileService::getParentOf)
                .filter(album -> album != null && !mediaFileService.isRoot(album))
                .distinct()
                .toList();

        if (albums.isEmpty()) {
            return createAutoCover(request, size, size);
        }
        if (albums.size() < 4) {
            return createImage(createMediaFileCoverArtRequest(albums.get(0), 0), size);
        }

        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();

        int half = size / 2;
        graphics.drawImage(createImage(createMediaFileCoverArtRequest(albums.get(0), 0), half), null, 0, 0);
        graphics.drawImage(createImage(createMediaFileCoverArtRequest(albums.get(1), 0), half), null, half, 0);
        graphics.drawImage(createImage(createMediaFileCoverArtRequest(albums.get(2), 0), half), null, 0, half);
        graphics.drawImage(createImage(createMediaFileCoverArtRequest(albums.get(3), 0), half), null, half, half);
        graphics.dispose();
        return image;
    }

    /**
     * Create a Video image.
     *
     * @param request the request for the video
     * @param size    the size of the image
     * @return the image
     */
    @Nonnull
    public BufferedImage createVideoImage(VideoCoverArtRequest request, int size) {
        int height = size;
        int width = height * 16 / 9;
        MediaFile mediaFile = request.getMediaFile();
        try (InputStream in = getImageInputStreamForVideo(mediaFile, width, height, request.getOffset())) {
            BufferedImage result = ImageIO.read(in);
            if (result != null) {
                return result;
            }
            LOG.warn("Failed to process cover art for {}: {}", mediaFile, result);
        } catch (Throwable x) {
            LOG.warn("Failed to process cover art for {}", mediaFile, x);
        }
        return createAutoCover(request, width, height);
    }

    /**
     * Returns an input stream to the image in the given file. If the file is an
     * audio file,
     * the embedded album art is returned.
     */
    @Nullable
    private InputStream getImageInputStream(CoverArt art) throws IOException {
        return getImageInputStreamWithType(art.getFullPath()).getLeft();
    }

    /**
     * Returns an input stream to the image in the given file. If the file is an
     * audio file,
     * the embedded album art is returned. In addition returns the mime type
     */
    public Pair<InputStream, String> getImageInputStreamWithType(Path file) throws IOException {
        try {
            if (JaudiotaggerParser.isImageAvailable(file)) {
                LOG.trace("Using Jaudio Tagger for reading artwork from {}", file);
                Artwork artwork = JaudiotaggerParser.getArtwork(file);
                return Pair.of(new ByteArrayInputStream(artwork.getBinaryData()), artwork.getMimeType());
            } else {
                LOG.trace("Reading artwork from file {}", file);
                return Pair.of(
                    new BufferedInputStream(Files.newInputStream(file)),
                    StringUtil.getMimeType(FilenameUtils.getExtension(file.toString()))
                );
            }
        } catch (Exception e) {
            LOG.debug("Could not read artwork from file {}", file);
            return Pair.of(null, null);
        }
    }

    private InputStream getImageInputStreamForVideo(MediaFile mediaFile, int width, int height, int offset)
            throws Exception {
        VideoTranscodingSettings videoSettings = new VideoTranscodingSettings(width, height, offset, 0);
        TranscodingService.Parameters parameters = new TranscodingService.Parameters(mediaFile, videoSettings);
        String command = settingsService.getVideoImageCommand();
        parameters.setTranscoding(new Transcoding(null, null, null, null, command, null, null, false));
        return transcodingService.getTranscodedInputStream(parameters);
    }
}
