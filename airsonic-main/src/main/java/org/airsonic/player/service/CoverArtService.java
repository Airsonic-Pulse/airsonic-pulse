package org.airsonic.player.service;

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.CoverArt.EntityType;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.repository.CoverArtRepository;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.service.cache.CoverArtCache;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

@Service
public class CoverArtService {
    @Autowired
    MediaFolderService mediaFolderService;
    @Autowired
    private CoverArtRepository coverArtRepository;
    @Autowired
    private MediaFileRepository mediaFileRepository;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private CoverArtCache coverArtCache;

    private static final Logger LOG = LoggerFactory.getLogger(CoverArtService.class);

    @Transactional
    public void upsert(CoverArt art) {
        coverArtCache.removeCoverArt(art);
        coverArtRepository.save(art);
    }

    /**
     * Persists the cover art of the media file if it is not already persisted.
     *
     * @param mediaFile the media file
     */
    @Transactional
    public void persistIfNeeded(MediaFile mediaFile) {
        CoverArt mediaFileArt = mediaFile.getArt();
        if (mediaFileArt != null && !CoverArt.NULL_ART.equals(mediaFileArt)) {
            CoverArt art = coverArtRepository.findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, mediaFile.getId()).orElse(CoverArt.NULL_ART);
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                mediaFileArt.setEntityId(mediaFile.getId());
                mediaFileArt.setEntityType(EntityType.MEDIA_FILE);
                upsert(mediaFileArt);
            }
            mediaFile.setArt(null);
        }
    }

    /**
     * Persists the cover art of the album if it is not already persisted.
     *
     * @param album the album
     */
    @Transactional
    public void persistIfNeeded(Album album) {
        CoverArt albumArt = album.getArt();
        if (albumArt != null && !CoverArt.NULL_ART.equals(albumArt)) {
            CoverArt art = coverArtRepository.findByEntityTypeAndEntityId(EntityType.ALBUM, album.getId()).orElse(CoverArt.NULL_ART);
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                albumArt.setEntityId(album.getId());
                albumArt.setEntityType(EntityType.ALBUM);
                upsert(albumArt);
            }
            album.setArt(null);
        }
    }

    /**
     * Persists the cover art of the artist if it is not already persisted.
     *
     * @param artist
     */
    @Transactional
    public void persistIfNeeded(Artist artist) {
        CoverArt artistArt = artist.getArt();
        if (artistArt != null && !CoverArt.NULL_ART.equals(artistArt)) {
            CoverArt art = coverArtRepository.findByEntityTypeAndEntityId(EntityType.ARTIST, artist.getId()).orElse(CoverArt.NULL_ART);
            if (CoverArt.NULL_ART.equals(art) || !art.getOverridden()) {
                artistArt.setEntityId(artist.getId());
                artistArt.setEntityType(EntityType.ARTIST);
                upsert(artistArt);
            }
            artist.setArt(null);
        }
    }

    public CoverArt getAlbumArt(Integer id) {
        CoverArt art = coverArtCache.getCoverArt(EntityType.ALBUM, id);
        if (art != null) {
            return art;
        }
        art = coverArtRepository.findByEntityTypeAndEntityId(EntityType.ALBUM, id).orElse(CoverArt.NULL_ART);
        coverArtCache.putCoverArt(art);
        return art;
    }

    public CoverArt getArtistArt(Integer id) {
        CoverArt art = coverArtCache.getCoverArt(EntityType.ARTIST, id);
        if (art != null) {
            return art;
        }
        art = coverArtRepository.findByEntityTypeAndEntityId(EntityType.ARTIST, id).orElse(CoverArt.NULL_ART);
        coverArtCache.putCoverArt(art);
        return art;
    }

    public CoverArt getMediaFileArt(@Param("id") int id) {
        CoverArt art = coverArtCache.getCoverArt(EntityType.MEDIA_FILE, id);
        if (art != null) {
            return art;
        }
        art = coverArtRepository.findByEntityTypeAndEntityId(EntityType.MEDIA_FILE, id).orElse(CoverArt.NULL_ART);
        coverArtCache.putCoverArt(art);
        return art;
    }

    @Nullable
    public Path getMediaFileArtPath(int id) {
        CoverArt art = getMediaFileArt(id);
        return getFullPath(art);
    }

    @Nullable
    public Path getFullPath(@Nullable CoverArt art) {
        if (art != null && !CoverArt.NULL_ART.equals(art)) {
            return art.getFullPath();
        }
        return null;
    }

    @Transactional
    public void delete(EntityType type, Integer id) {
        coverArtCache.removeCoverArt(type, id);
        coverArtRepository.deleteByEntityTypeAndEntityId(type, id);
    }

    @Transactional
    public void expunge() {
        coverArtCache.clear();
        List<CoverArt> expungeCoverArts = coverArtRepository.findAll().stream()
            .filter(art -> {
                switch (art.getEntityType()) {
                    case ALBUM:
                        return art.getAlbum() == null || !art.getAlbum().isPresent();
                    case ARTIST:
                        return art.getArtist() == null || !art.getArtist().isPresent();
                    case MEDIA_FILE:
                        return art.getMediaFile() == null || !art.getMediaFile().isPresent();
                    default:
                        return false;
                }
            })
            .toList();
        coverArtRepository.deleteAll(expungeCoverArts);
    }

    /**
     * Sets the cover art image for the given media file.
     *
     * @param mediaFileId the media file id
     * @param url         the url of the image
     * @throws Exception if the image could not be saved
     */
    public void setCoverArtImageFromUrl(Integer mediaFileId, String url) throws Exception {

        MediaFile dir = mediaFileRepository.findById(mediaFileId).orElseGet(
            () -> {
                LOG.warn("Could not find media file with id {}", mediaFileId);
                return null;
            });
        saveCoverArt(dir, url);
    }

    private void saveCoverArt(MediaFile dir, String url) throws Exception {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(20 * 1000) // 20 seconds
                .setSocketTimeout(20 * 1000) // 20 seconds
                .build();
        HttpGet method = new HttpGet(url);
        method.setConfig(requestConfig);

        // Attempt to resolve proper suffix.
        String suffix = "jpg";
        if (url.toLowerCase().endsWith(".gif")) {
            suffix = "gif";
        } else if (url.toLowerCase().endsWith(".png")) {
            suffix = "png";
        }

        // Check permissions.
        MusicFolder folder = dir.getFolder();
        Path fullPath = dir.getFullPath();
        Path newCoverFile = fullPath.resolve("cover." + suffix);
        if (!securityService.isWriteAllowed(folder.getPath().relativize(newCoverFile), folder)) {
            throw new SecurityException("Permission denied: " + StringEscapeUtils.escapeHtml(newCoverFile.toString()));
        }

        try (CloseableHttpClient client = HttpClients.createDefault();
                CloseableHttpResponse response = client.execute(method);
                InputStream input = response.getEntity().getContent()) {

            // If file exists, create a backup.
            backupCoverArt(newCoverFile, fullPath.resolve("cover." + suffix + ".backup"));

            // Write file.
            Files.copy(input, newCoverFile, StandardCopyOption.REPLACE_EXISTING);
        }

        CoverArt coverArt = new CoverArt(dir.getId(), EntityType.MEDIA_FILE,
                folder.getPath().relativize(newCoverFile).toString(), dir.getFolder(), true);
        upsert(coverArt);
    }

    private void backupCoverArt(Path newCoverFile, Path backup) {
        if (Files.exists(newCoverFile)) {
            try {
                Files.move(newCoverFile, backup, StandardCopyOption.REPLACE_EXISTING);
                LOG.info("Backed up old image file to {}", backup);
            } catch (IOException e) {
                LOG.warn("Failed to create image file backup {}", backup, e);
            }
        }
    }
}
