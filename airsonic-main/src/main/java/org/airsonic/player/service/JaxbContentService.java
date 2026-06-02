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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.service;

import org.airsonic.player.controller.CoverArtController;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Contributors;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.Genres;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.util.StringUtil;
import org.springframework.stereotype.Service;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.Child;
import org.subsonic.restapi.Contributor;
import org.subsonic.restapi.DiscTitle;
import org.subsonic.restapi.ItemDate;
import org.subsonic.restapi.ItemGenre;
import org.subsonic.restapi.RecordLabel;
import org.subsonic.restapi.ReplayGain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

@Service
public class JaxbContentService {

    private final JAXBWriter jaxbWriter;
    private final ArtistService artistService;
    private final CoverArtService coverArtService;
    private final PlaylistService playlistService;
    private final AlbumService albumService;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final TranscodingService transcodingService;
    private final RatingService ratingService;
    private final SettingsService settingsService;

    JaxbContentService(
            JAXBWriter jaxbWriter,
            ArtistService artistService,
            CoverArtService coverArtService,
            PlaylistService playlistService,
            AlbumService albumService,
            MediaFileService mediaFileService,
            MediaFolderService mediaFolderService,
            TranscodingService transcodingService,
            RatingService ratingService,
            SettingsService settingsService) {
        this.jaxbWriter = jaxbWriter;
        this.artistService = artistService;
        this.coverArtService = coverArtService;
        this.playlistService = playlistService;
        this.albumService = albumService;
        this.mediaFileService = mediaFileService;
        this.mediaFolderService = mediaFolderService;
        this.transcodingService = transcodingService;
        this.ratingService = ratingService;
        this.settingsService = settingsService;
    }

    public <T extends ArtistID3> T createJaxbArtist(T jaxbArtist, org.airsonic.player.domain.Artist artist, String username) {
        jaxbArtist.setId(String.valueOf(artist.getId()));
        jaxbArtist.setName(artist.getName());
        jaxbArtist.setStarred(jaxbWriter.convertDate(artistService.getStarredDate(artist.getId(), username)));
        jaxbArtist.setAlbumCount(artist.getAlbumCount());
        if (!CoverArt.NULL_ART.equals(coverArtService.getArtistArt(artist.getId()))) {
            jaxbArtist.setCoverArt(CoverArtController.ARTIST_COVERART_PREFIX + artist.getId());
        }
        jaxbArtist.setMediaType("artist");
        jaxbArtist.setSortName(artist.getSortName());
        jaxbArtist.setMusicBrainzId(artist.getMusicBrainzArtistId());
        // Ratings key on MediaFile id, so resolve the artist's directory MediaFile (the
        // physical row that carries rating-eligible ids). Virtual artists derived from
        // albumArtist tags have no directory and resolve to null; RatingService treats a
        // null MediaFile as "no rating", so userRating and averageRating come back null
        // and JAXB omits the attributes — matching the #201 (#179) convention on the
        // MediaFile-keyed overload.
        MediaFile artistMediaFile = mediaFileService.getArtistByName(artist.getName(),
                mediaFolderService.getMusicFoldersForUser(username));
        jaxbArtist.setUserRating(ratingService.getRatingForUser(username, artistMediaFile));
        jaxbArtist.setAverageRating(ratingService.getAverageRating(artistMediaFile));
        return jaxbArtist;
    }

    public org.subsonic.restapi.Artist createJaxbArtist(MediaFile artist, String username) {
        org.subsonic.restapi.Artist result = new org.subsonic.restapi.Artist();
        result.setId(String.valueOf(artist.getId()));
        result.setName(artist.getTitle() != null ? artist.getTitle() : artist.getArtist());
        Instant starred = mediaFileService.getMediaFileStarredDate(artist, username);
        result.setStarred(jaxbWriter.convertDate(starred));
        result.setUserRating(ratingService.getRatingForUser(username, artist));
        result.setAverageRating(ratingService.getAverageRating(artist));
        return result;
    }

    public <T extends AlbumID3> T createJaxbAlbum(T jaxbAlbum, Album album, String username) {
        // The 3-arg overload is used by list endpoints (getAlbumList2, getStarred2, getArtist,
        // search). Passing null tracks here keeps those paths free of per-album track fetches —
        // discTitles only populates when the caller already has the album's songs in hand.
        return createJaxbAlbum(jaxbAlbum, album, username, null);
    }

    public <T extends AlbumID3> T createJaxbAlbum(T jaxbAlbum, Album album, String username, List<MediaFile> albumTracks) {
        jaxbAlbum.setId(String.valueOf(album.getId()));
        jaxbAlbum.setName(album.getName());
        if (album.getArtist() != null) {
            jaxbAlbum.setArtist(album.getArtist());
            org.airsonic.player.domain.Artist artist = artistService.getArtist(album.getArtist());
            if (artist != null) {
                jaxbAlbum.setArtistId(String.valueOf(artist.getId()));
            }
        }
        if (!CoverArt.NULL_ART.equals(coverArtService.getAlbumArt(album.getId()))) {
            jaxbAlbum.setCoverArt(CoverArtController.ALBUM_COVERART_PREFIX + album.getId());
        }
        jaxbAlbum.setSongCount(album.getSongCount());
        jaxbAlbum.setDuration((int) Math.round(album.getDuration()));
        jaxbAlbum.setCreated(jaxbWriter.convertDate(album.getCreated()));
        jaxbAlbum.setStarred(jaxbWriter.convertDate(albumService.getAlbumStarredDate(album.getId(), username)));
        jaxbAlbum.setPlayCount((long) album.getPlayCount());
        jaxbAlbum.setYear(album.getYear());
        jaxbAlbum.setGenre(album.getGenre());
        jaxbAlbum.setPlayed(jaxbWriter.convertDate(album.getLastPlayed()));
        jaxbAlbum.setMusicBrainzId(album.getMusicBrainzReleaseId());
        jaxbAlbum.setDisplayArtist(album.getArtist());
        jaxbAlbum.setSortName(album.getSortName());
        jaxbAlbum.setIsCompilation(album.getCompilation());
        jaxbAlbum.setOriginalReleaseDate(parseItemDate(album.getOriginalReleaseDate()));
        jaxbAlbum.setReleaseDate(parseItemDate(album.getReleaseDate()));
        for (String releaseType : splitMultiValue(album.getReleaseTypes())) {
            jaxbAlbum.getReleaseTypes().add(releaseType);
        }
        for (String labelName : splitMultiValue(album.getRecordLabels())) {
            RecordLabel label = new RecordLabel();
            label.setName(labelName);
            jaxbAlbum.getRecordLabels().add(label);
        }
        for (DiscTitle discTitle : buildDiscTitles(albumTracks)) {
            jaxbAlbum.getDiscTitles().add(discTitle);
        }
        return jaxbAlbum;
    }

    /**
     * Builds the album's disc-title list by grouping its tracks by disc number and taking the
     * first non-blank {@code disc_subtitle} per disc, sorted by disc ascending. Discs without
     * any subtitled track are skipped. Returns an empty list when no tracks are provided or
     * none carry subtitles — callers naturally omit the {@code <discTitles>} elements then.
     * <p>
     * Called only by the 4-arg {@code createJaxbAlbum} overload, so list endpoints never pay
     * for the grouping (they pass {@code null} via the 3-arg overload).
     */
    static List<DiscTitle> buildDiscTitles(List<MediaFile> albumTracks) {
        if (albumTracks == null || albumTracks.isEmpty()) {
            return List.of();
        }
        Map<Integer, String> subtitleByDisc = new TreeMap<>();
        for (MediaFile track : albumTracks) {
            Integer disc = track.getDiscNumber();
            if (disc == null || subtitleByDisc.containsKey(disc)) {
                continue;
            }
            String subtitle = track.getDiscSubtitle();
            if (subtitle == null) {
                continue;
            }
            String trimmed = subtitle.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            subtitleByDisc.put(disc, trimmed);
        }
        List<DiscTitle> result = new ArrayList<>(subtitleByDisc.size());
        for (Map.Entry<Integer, String> entry : subtitleByDisc.entrySet()) {
            DiscTitle dt = new DiscTitle();
            dt.setDisc(entry.getKey());
            dt.setTitle(entry.getValue());
            result.add(dt);
        }
        return result;
    }

    /**
     * Internal delimiter for packed multi-value AlbumID3 columns (releaseTypes, recordLabels).
     * Must match the constant of the same name in {@link MediaFileService}.
     */
    static final String MULTI_VALUE_DELIMITER = "\n";

    /**
     * Splits a packed multi-value column back into a list of trimmed, non-blank values for
     * response emission. Returns an empty list (not null) so callers can iterate without
     * null-checking; an empty list naturally omits the repeated element entirely.
     */
    static List<String> splitMultiValue(String packed) {
        if (packed == null || packed.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (String token : packed.split(MULTI_VALUE_DELIMITER, -1)) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static final java.util.regex.Pattern ITEM_DATE_PATTERN =
            java.util.regex.Pattern.compile("^(\\d{4})(?:-(\\d{1,2})(?:-(\\d{1,2}))?)?$");

    /**
     * Parses a raw tag date string into an {@link ItemDate}. Accepts {@code YYYY},
     * {@code YYYY-MM}, or {@code YYYY-MM-DD}; tolerates a trailing time component by taking
     * only the date part before {@code T} or whitespace. Returns {@code null} when the input
     * is blank or doesn't match the expected shape, so the element is omitted from the
     * response rather than emitted with an empty/garbage date.
     */
    static ItemDate parseItemDate(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        // Strip an optional trailing time component: "2003-10-12T00:00:00" -> "2003-10-12".
        int splitAt = s.length();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == 'T' || c == ' ') {
                splitAt = i;
                break;
            }
        }
        s = s.substring(0, splitAt);
        java.util.regex.Matcher m = ITEM_DATE_PATTERN.matcher(s);
        if (!m.matches()) {
            return null;
        }
        ItemDate date = new ItemDate();
        date.setYear(Integer.valueOf(m.group(1)));
        if (m.group(2) != null) {
            date.setMonth(Integer.valueOf(m.group(2)));
        }
        if (m.group(3) != null) {
            date.setDay(Integer.valueOf(m.group(3)));
        }
        return date;
    }

    public <T extends org.subsonic.restapi.Playlist> T createJaxbPlaylist(T jaxbPlaylist, Playlist playlist) {
        jaxbPlaylist.setId(String.valueOf(playlist.getId()));
        jaxbPlaylist.setName(playlist.getName());
        jaxbPlaylist.setComment(playlist.getComment());
        jaxbPlaylist.setOwner(playlist.getUsername());
        jaxbPlaylist.setPublic(playlist.getShared());
        jaxbPlaylist.setSongCount(playlist.getFileCount());
        jaxbPlaylist.setDuration((int) Math.round(playlist.getDuration()));
        jaxbPlaylist.setCreated(jaxbWriter.convertDate(playlist.getCreated()));
        jaxbPlaylist.setChanged(jaxbWriter.convertDate(playlist.getChanged()));
        jaxbPlaylist.setCoverArt(CoverArtController.PLAYLIST_COVERART_PREFIX + playlist.getId());

        for (String username : playlistService.getPlaylistUsers(playlist.getId())) {
            jaxbPlaylist.getAllowedUser().add(username);
        }
        return jaxbPlaylist;
    }

    public Child createJaxbChild(Player player, MediaFile mediaFile, String username) {
        return createJaxbChild(new Child(), player, mediaFile, username);
    }

    public <T extends Child> T createJaxbChild(T child, Player player, MediaFile mediaFile, String username) {
        MediaFile parent = mediaFileService.getParentOf(mediaFile);
        child.setId(String.valueOf(mediaFile.getId()));
        try {
            if (Objects.nonNull(parent) && !mediaFileService.isRoot(parent)) {
                child.setParent(String.valueOf(parent.getId()));
            }
        } catch (SecurityException x) {
            // Ignored.
        }
        child.setTitle(mediaFile.getName());
        child.setSortName(mediaFile.getSortName());
        child.setAlbum(mediaFile.getAlbumName());
        child.setArtist(mediaFile.getArtist());
        child.setIsDir(mediaFile.isDirectory());
        child.setCoverArt(findCoverArt(mediaFile, parent));
        child.setYear(mediaFile.getYear());
        child.setBpm(mediaFile.getBpm());
        child.setGenre(mediaFile.getGenre());
        for (String genreName : Genres.split(mediaFile.getGenres(), settingsService.getGenreSeparators())) {
            ItemGenre itemGenre = new ItemGenre();
            itemGenre.setName(genreName);
            child.getGenres().add(itemGenre);
        }
        child.setCreated(jaxbWriter.convertDate(mediaFile.getCreated()));
        child.setStarred(jaxbWriter.convertDate(mediaFileService.getMediaFileStarredDate(mediaFile, username)));
        child.setUserRating(ratingService.getRatingForUser(username, mediaFile));
        child.setAverageRating(ratingService.getAverageRating(mediaFile));
        child.setPlayCount((long) mediaFile.getPlayCount());
        child.setPlayed(jaxbWriter.convertDate(mediaFile.getLastPlayed()));
        child.setMusicBrainzId(mediaFile.getMusicBrainzRecordingId());
        child.setDisplayArtist(mediaFile.getArtist());
        child.setDisplayAlbumArtist(mediaFile.getAlbumArtist());
        child.setReplayGain(buildReplayGain(mediaFile));
        for (Contributor contributor : buildContributors(mediaFile)) {
            child.getContributors().add(contributor);
        }
        if (mediaFile.getMediaType() != null) {
            child.setMediaType(mediaFile.getMediaType().name().toLowerCase());
        }

        if (mediaFile.isFile()) {
            Double mediaFileDuration = mediaFile.getDuration();
            child.setDuration((int) Math.round(mediaFileDuration == null ? 0 : mediaFileDuration));
            child.setBitRate(mediaFile.getBitRate());
            child.setTrack(mediaFile.getTrackNumber());
            child.setDiscNumber(mediaFile.getDiscNumber());
            child.setSize(mediaFile.getFileSize());
            String suffix = mediaFile.getFormat();
            child.setSuffix(suffix);
            child.setContentType(StringUtil.getMimeType(suffix));
            child.setIsVideo(mediaFile.isVideo());
            child.setPath(mediaFile.getPath());

            Album album = albumService.getAlbumByMediaFile(mediaFile);

            if (album != null) {
                child.setAlbumId(String.valueOf(album.getId()));
            }
            org.airsonic.player.domain.Artist artist = artistService.getArtist(mediaFile.getArtist());
            if (artist != null) {
                child.setArtistId(String.valueOf(artist.getId()));
            }
            switch (mediaFile.getMediaType()) {
                case MUSIC -> child.setType(org.subsonic.restapi.MediaType.MUSIC);
                case PODCAST -> child.setType(org.subsonic.restapi.MediaType.PODCAST);
                case AUDIOBOOK -> child.setType(org.subsonic.restapi.MediaType.AUDIOBOOK);
                case VIDEO -> {
                    child.setType(org.subsonic.restapi.MediaType.VIDEO);
                    child.setOriginalWidth(mediaFile.getWidth());
                    child.setOriginalHeight(mediaFile.getHeight());
                }
                default -> { }
            }

            if (transcodingService.isTranscodingRequired(mediaFile, player)) {
                String transcodedSuffix = transcodingService.getSuffix(player, mediaFile, null);
                child.setTranscodedSuffix(transcodedSuffix);
                child.setTranscodedContentType(StringUtil.getMimeType(transcodedSuffix));
            }
        }
        return child;
    }

    List<Contributor> buildContributors(MediaFile mediaFile) {
        List<org.airsonic.player.domain.Contributor> records = Contributors.split(mediaFile.getContributors());
        if (records.isEmpty()) {
            return List.of();
        }
        List<Contributor> result = new ArrayList<>(records.size());
        for (org.airsonic.player.domain.Contributor record : records) {
            Contributor jaxb = new Contributor();
            jaxb.setRole(record.role());
            jaxb.setSubRole(record.subRole());
            jaxb.setArtist(createJaxbArtistByName(record.name()));
            result.add(jaxb);
        }
        return result;
    }

    ArtistID3 createJaxbArtistByName(String name) {
        org.airsonic.player.domain.Artist artist = artistService.getArtist(name);
        if (artist != null) {
            ArtistID3 jaxb = new ArtistID3();
            jaxb.setId(String.valueOf(artist.getId()));
            jaxb.setName(artist.getName());
            return jaxb;
        }
        // Uncatalogued tag-derived contributor: no Airsonic Artist record matches this name,
        // so there is no resolvable id under the local XSD's required-id rule. Emit a "" id
        // sentinel with the raw tag name; a stable synthetic id is a possible future refinement
        // if clients struggle with the empty value.
        ArtistID3 jaxb = new ArtistID3();
        jaxb.setId("");
        jaxb.setName(name);
        jaxb.setAlbumCount(0);
        return jaxb;
    }

    private ReplayGain buildReplayGain(MediaFile mediaFile) {
        Double trackGain = mediaFile.getReplayGainTrackGain();
        Double albumGain = mediaFile.getReplayGainAlbumGain();
        Double trackPeak = mediaFile.getReplayGainTrackPeak();
        Double albumPeak = mediaFile.getReplayGainAlbumPeak();
        // Operator-supplied fallback emitted on every replayGain element when configured —
        // clients are expected to apply it only when the per-track values are absent. When
        // unset (the default), the attribute is omitted and there is no behavior change.
        Double fallbackGain = settingsService.getReplayGainFallback();
        if (trackGain == null && albumGain == null && trackPeak == null && albumPeak == null
                && fallbackGain == null) {
            return null;
        }
        ReplayGain replayGain = new ReplayGain();
        replayGain.setTrackGain(trackGain);
        replayGain.setAlbumGain(albumGain);
        replayGain.setTrackPeak(trackPeak);
        replayGain.setAlbumPeak(albumPeak);
        replayGain.setFallbackGain(fallbackGain);
        return replayGain;
    }

    private String findCoverArt(MediaFile mediaFile, MediaFile parent) {
        MediaFile dir = mediaFile.isDirectory() ? mediaFile : parent;
        if (dir != null && !CoverArt.NULL_ART.equals(coverArtService.getMediaFileArt(dir.getId()))) {
            return String.valueOf(dir.getId());
        }
        return null;
    }
}
