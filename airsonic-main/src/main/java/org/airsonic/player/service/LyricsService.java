package org.airsonic.player.service;

import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.parser.lyrics.EmbeddedLyricsParser;
import org.airsonic.player.parser.lyrics.LrcFile;
import org.airsonic.player.parser.lyrics.LrcParser;
import org.airsonic.player.parser.lyrics.LyricsLine;
import org.airsonic.player.repository.LyricsRepository;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.transaction.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class LyricsService {

    private final static Logger LOG = LoggerFactory.getLogger(LyricsService.class);

    private final LrcParser lrcParser;
    private final LyricsRepository lyricsRepository;
    private final MediaFileService mediaFileService;
    private final EmbeddedLyricsParser embeddedLyricsParser;

    public LyricsService(LyricsRepository lyricsRepository, MediaFileService mediaFileService,
            EmbeddedLyricsParser embeddedLyricsParser) {
        this.lrcParser = new LrcParser();
        this.lyricsRepository = lyricsRepository;
        this.mediaFileService = mediaFileService;
        this.embeddedLyricsParser = embeddedLyricsParser;
    }

    /**
     * Resolves lyrics for a MediaFile, consulting sources in precedence order:
     * DB cache → LRC sidecar → embedded tag → null. The DB cache wins outright. On a cache miss,
     * an LRC sidecar ({@code *.lrc}/{@code *.LRC} beside the audio file) is preferred over the
     * embedded tag — a sidecar is a deliberate user override of whatever is baked into the file.
     * Only when both miss is the embedded {@link org.jaudiotagger.tag.FieldKey#LYRICS} tag read
     * (so files with cached or sidecar lyrics never pay the per-request file open). Sidecar and
     * embedded hits are both cached to the repository, tagged {@code "file"} and {@code "tag"}
     * respectively.
     *
     * @param mediaFile the MediaFile object from which to resolve lyrics
     *                  (should not be a directory or indexed track)
     * @return the resolved Lyrics object, or null if the MediaFile is not valid or has no lyrics
     */
    @Nullable
    @Transactional
    public Lyrics getLyricsFromMediaFile(@Nonnull MediaFile mediaFile) {

        // Check if the MediaFile is a valid file type
        if (mediaFile.isDirectory()) {
            LOG.info("MediaFile is a directory, does not support lyrics");
            return null;
        }

        // get existing lyrics if available
        return lyricsRepository.findByMediaFileId(mediaFile.getId()).orElseGet(() -> {
            if (mediaFile.isIndexedTrack()) {
                LOG.info("MediaFile is an indexed track, does not support lyrics");
                return null;
            }
            // generate LRC file path
            Path filePath = mediaFile.getFullPath();
            String baseName = FilenameUtils.removeExtension(filePath.toString());
            Path lrcPath = Path.of(baseName + ".lrc");
            Path lrcPathUpper = Path.of(baseName + ".LRC");

            Path targetLrcPath = null;
            if (Files.exists(lrcPath)) {
                targetLrcPath = lrcPath;
            } else if (Files.exists(lrcPathUpper)) {
                targetLrcPath = lrcPathUpper;
            } else {
                // No sidecar — fall back to the embedded unsynced lyrics tag (#158). This is the
                // only place the file is opened for an embedded read, and only on a cache+sidecar
                // miss, so songs with cached or sidecar lyrics never pay the per-request open.
                LOG.debug("LRC sidecar does not exist for media: {}, trying embedded lyrics tag", mediaFile.getId());
                String embedded = embeddedLyricsParser.getLyrics(filePath);
                if (embedded == null) {
                    LOG.debug("No embedded lyrics tag for media: {}", mediaFile.getId());
                    return null;
                }
                Lyrics newLyrics = new Lyrics(embedded, mediaFile.getId(), "tag");
                return lyricsRepository.save(newLyrics);
            }
            LrcFile lrcFile = lrcParser.parse(targetLrcPath);
            if (lrcFile == null || lrcFile.getLyricsLines().isEmpty()) {
                LOG.debug("No lyrics found in LRC file: {}", targetLrcPath);
                return null;
            }
            StringBuilder lyricsText = new StringBuilder();
            for (LyricsLine line : lrcFile.getLyricsLines()) {
                lyricsText.append(line.getText()).append("\n");
            }
            Lyrics newLyrics = new Lyrics(lyricsText.toString(), mediaFile.getId(), "file");
            return lyricsRepository.save(newLyrics);
        });

    }

    /**
     * Fetch lyrics based on artist and title.
     * This method retrieves the MediaFile associated with the given artist and title,
     * and then calls getLyricsFromMediaFile to get the lyrics.
     *
     * @param artist The artist name.
     * @param title The title of the song.
     * @param mediaFolders The list of media folders to search in.
     * @return The Lyrics object if found, or null if not found or if the artist or title is null.
     */
    @Nullable
    @Transactional
    public Lyrics getLyricsFromArtistAndTitle(@Nullable String artist, @Nullable String title, @Nullable List<MusicFolder> mediaFolders) {
        if (artist == null || title == null) {
            LOG.warn("Artist or title is null, cannot fetch lyrics");
            return null;
        }
        // Fetch lyrics based on artist and title
        MediaFile mediaFile = mediaFileService.getSongByArtistAndTitle(artist, title, mediaFolders);
        if (mediaFile == null) {
            LOG.warn("No media file found for artist: {}, title: {}", artist, title);
            return null;
        }
        return getLyricsFromMediaFile(mediaFile);
    }

    @Transactional
    public boolean saveLyricsForMediaFile(@Nonnull MediaFile mediaFile, @Nonnull String lyricsText, @Nonnull String source) {

        if (mediaFile.isDirectory()) {
            LOG.warn("MediaFile is a directory, cannot save lyrics");
            return false;
        }

        Lyrics lyrics = lyricsRepository.findByMediaFileId(mediaFile.getId())
                .orElse(new Lyrics("", mediaFile.getId(), source));
        lyrics.setLyrics(lyricsText);
        lyrics.setSource(source);
        lyricsRepository.save(lyrics);
        return true;

    }

    @Transactional
    public void deleteLyricsForMediaFile(@Nonnull MediaFile mediaFile) {
        lyricsRepository.findByMediaFileId(mediaFile.getId())
                .ifPresent(lyrics -> {
                    lyricsRepository.delete(lyrics);
                });
    }
}

