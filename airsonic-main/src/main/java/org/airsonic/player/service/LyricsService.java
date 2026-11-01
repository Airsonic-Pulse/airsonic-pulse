package org.airsonic.player.service;

import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.StructuredLyricsLine;
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
     * DB cache → LRC sidecar → embedded tag → null. A cached row wins only while it is still
     * fresh against its source (#281); a stale row is re-resolved through the same tiers and
     * replaced in place. On a cache miss or stale hit, an LRC sidecar ({@code *.lrc}/{@code *.LRC}
     * beside the audio file) is preferred over the embedded tag — a sidecar is a deliberate user
     * override of whatever is baked into the file. Only when both miss is the embedded
     * {@link org.jaudiotagger.tag.FieldKey#LYRICS} tag read (so files with fresh cached or sidecar
     * lyrics never pay the per-request file open). Sidecar and embedded hits are cached tagged
     * {@code "file"} and {@code "tag"} respectively, stamped with the source signal the next
     * request's freshness check compares against.
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

        // get existing lyrics and return them while they still reflect their source (#281)
        Lyrics existing = lyricsRepository.findByMediaFileId(mediaFile.getId()).orElse(null);
        if (existing != null && isFresh(existing, mediaFile)) {
            return existing;
        }
        return resolveAndCache(mediaFile, existing);
    }

    /**
     * Decides whether a cached row still reflects the source it was built from (#281).
     */
    private boolean isFresh(@Nonnull Lyrics existing, @Nonnull MediaFile mediaFile) {
        // Freshness is decided per source. A "tag" row compares against media_file.changed: a
        // file re-tagged in place becomes visible to this tier as soon as that signal refreshes
        // — at request time via checkLastModified when Fast Cache is off, otherwise at the next
        // scan. A "file" row stats the sidecar directly, so sidecar edits need no scan at all.
        // "user" and "chartlyrics.com" rows are never compared: a manual override or external
        // fetch is not derived from the file and must not be clobbered automatically (their
        // signal is stored as null). A null signal on a file/tag row (pre-#281 cache) compares
        // unequal, so such rows refresh exactly once and get stamped.
        return switch (existing.getSource()) {
            case "file" -> {
                Path sidecar = findSidecarPath(mediaFile.getFullPath());
                // A vanished sidecar is stale: resolution falls through the normal tiers
                // (possibly landing on the embedded tag, possibly deleting the row).
                yield sidecar != null && sameInstant(existing.getSourceLastModified(), fileLastModified(sidecar));
            }
            case "tag" -> sameInstant(existing.getSourceLastModified(), mediaFile.getChanged());
            default -> true;
        };
    }

    /**
     * Resolves the source tiers (LRC sidecar, then embedded tag — precedence unchanged) and
     * replaces or creates the cached row, stamping the source signal (#281). When no source
     * has lyrics any more, a stale row is deleted so the cache keeps reflecting reality.
     */
    @Nullable
    private Lyrics resolveAndCache(@Nonnull MediaFile mediaFile, @Nullable Lyrics existing) {
        if (mediaFile.isIndexedTrack()) {
            LOG.info("MediaFile is an indexed track, does not support lyrics");
            return null;
        }
        Path filePath = mediaFile.getFullPath();
        Path targetLrcPath = findSidecarPath(filePath);
        if (targetLrcPath == null) {
            // No sidecar — fall back to the embedded unsynced lyrics tag (#158). This is the
            // only place the file is opened for an embedded read, and only on a cache miss or
            // a stale row, so songs with fresh cached or sidecar lyrics never pay the
            // per-request open.
            LOG.debug("LRC sidecar does not exist for media: {}, trying embedded lyrics tag", mediaFile.getId());
            String embedded = embeddedLyricsParser.getLyrics(filePath);
            if (embedded == null) {
                LOG.debug("No embedded lyrics tag for media: {}", mediaFile.getId());
                return deleteStaleRow(existing);
            }
            // #281: a tag row is stamped with media_file.changed — the audio file's own change
            // marker, refreshed by the scan (and by request-time checkLastModified when Fast
            // Cache is off) whenever the file's mtime moves.
            return replaceRow(existing, mediaFile.getId(), embedded, "tag", false, List.of(),
                    truncate(mediaFile.getChanged()));
        }
        LrcFile lrcFile = lrcParser.parse(targetLrcPath);
        if (lrcFile == null || lrcFile.getLyricsLines().isEmpty()) {
            LOG.debug("No lyrics found in LRC file: {}", targetLrcPath);
            return deleteStaleRow(existing);
        }
        // Preserve the per-line LRC timestamps (#140): populate the flat text blob (still used
        // by the legacy /getLyrics endpoint and the unsynced split) AND the structured timed
        // lines, marking the row synced. LrcParser only emits timed lines, so a non-empty parse
        // is synced by construction.
        StringBuilder lyricsText = new StringBuilder();
        for (LyricsLine line : lrcFile.getLyricsLines()) {
            lyricsText.append(line.getText()).append("\n");
        }
        // #281: a file row is stamped with the sidecar mtime LrcParser read at parse time
        // (LrcFile.lastModified — computed since #140, stored since #281).
        return replaceRow(existing, mediaFile.getId(), lyricsText.toString(), "file", true,
                lrcFile.getLyricsLines(), lrcFile.getLastModified());
    }

    /**
     * Replaces the content of an existing cached row — or creates one — in a single place:
     * text, source, synced flag, the #140 structured lines (cleared and re-inserted, never
     * left stale), the #281 source signal, and {@code updated} = now. Manual and external
     * saves pass a null signal, which exempts the row from freshness comparison.
     */
    private Lyrics replaceRow(@Nullable Lyrics existing, Integer mediaFileId, String text, String source,
            boolean synced, List<LyricsLine> parsedLines, @Nullable Instant sourceLastModified) {
        Lyrics row = existing != null ? existing : new Lyrics("", mediaFileId, source);
        row.setLyrics(text);
        row.setSource(source);
        row.setSynced(synced);
        row.setSourceLastModified(sourceLastModified);
        row.setUpdated(Instant.now().truncatedTo(ChronoUnit.MICROS));
        // Mutate the managed collection in place (clear + add) so orphanRemoval deletes the
        // replaced lines; swapping in a new list would detach them instead.
        row.getLines().clear();
        int position = 0;
        for (LyricsLine line : parsedLines) {
            row.getLines().add(new StructuredLyricsLine(row, position++, line.getTime(), line.getText()));
        }
        return lyricsRepository.save(row);
    }

    @Nullable
    private Lyrics deleteStaleRow(@Nullable Lyrics existing) {
        if (existing != null) {
            // The source vanished (sidecar removed, tag gone): drop the stale row so the cache
            // keeps reflecting reality instead of serving removed lyrics forever.
            lyricsRepository.delete(existing);
        }
        return null;
    }

    @Nullable
    private Path findSidecarPath(@Nonnull Path filePath) {
        String baseName = FilenameUtils.removeExtension(filePath.toString());
        Path lrcPath = Path.of(baseName + ".lrc");
        if (Files.exists(lrcPath)) {
            return lrcPath;
        }
        Path lrcPathUpper = Path.of(baseName + ".LRC");
        if (Files.exists(lrcPathUpper)) {
            return lrcPathUpper;
        }
        return null;
    }

    @Nullable
    private Instant fileLastModified(@Nonnull Path path) {
        try {
            return Files.getLastModifiedTime(path).toInstant();
        } catch (IOException e) {
            LOG.warn("Could not read last-modified time of {}", path);
            return null;
        }
    }

    private static boolean sameInstant(@Nullable Instant a, @Nullable Instant b) {
        return a != null && b != null
                && a.truncatedTo(ChronoUnit.MICROS).equals(b.truncatedTo(ChronoUnit.MICROS));
    }

    @Nullable
    private static Instant truncate(@Nullable Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MICROS);
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

        Lyrics existing = lyricsRepository.findByMediaFileId(mediaFile.getId()).orElse(null);
        // A manual ("user") or externally fetched ("chartlyrics.com") save is not derived from
        // the file: it stores a null signal, which exempts the row from freshness comparison
        // (#281). Going through replaceRow also clears any structured lines left behind by a
        // previously synced row and refreshes updated.
        replaceRow(existing, mediaFile.getId(), lyricsText, source, false, List.of(), null);
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

