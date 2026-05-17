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

 Copyright 2025 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolderContent;
import org.airsonic.player.domain.MusicIndex;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.RandomSearchCriteria;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.LibraryStatusService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MusicIndexService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.StatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.AlbumList;
import org.subsonic.restapi.Directory;
import org.subsonic.restapi.Index;
import org.subsonic.restapi.Indexes;
import org.subsonic.restapi.MusicFolders;
import org.subsonic.restapi.NowPlaying;
import org.subsonic.restapi.NowPlayingEntry;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.Songs;
import org.subsonic.restapi.Videos;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getLongParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredStringParameter;
import static org.springframework.web.bind.ServletRequestUtils.getStringParameter;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicBrowsingController extends AbstractSubsonicController {

    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private MusicIndexService musicIndexService;
    @Autowired
    private RatingService ratingService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private StatusService statusService;
    @Autowired
    private JaxbContentService jaxbContentService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private LibraryStatusService libraryStatusService;

    @RequestMapping({"/getMusicFolders", "/getMusicFolders.view"})
    public void getMusicFolders(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);

        MusicFolders musicFolders = new MusicFolders();
        String username = securityService.getCurrentUsername(request);
        for (org.airsonic.player.domain.MusicFolder musicFolder : mediaFolderService.getMusicFoldersForUser(username)) {
            org.subsonic.restapi.MusicFolder mf = new org.subsonic.restapi.MusicFolder();
            mf.setId(musicFolder.getId());
            mf.setName(musicFolder.getName());
            musicFolders.getMusicFolder().add(mf);
        }
        Response res = createResponse();
        res.setMusicFolders(musicFolders);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getIndexes", "/getIndexes.view"})
    public void getIndexes(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Response res = createResponse();
        String username = securityService.getCurrentUser(request).getUsername();
        Integer musicFolderId = getIntParameter(request, "musicFolderId");

        long ifModifiedSince = getLongParameter(request, "ifModifiedSince", 0L);
        long lastModified = libraryStatusService.getLastModified(username, musicFolderId);

        if (lastModified <= ifModifiedSince) {
            jaxbWriter.writeResponse(request, response, res);
            return;
        }

        Indexes indexes = new Indexes();
        indexes.setLastModified(lastModified);
        indexes.setIgnoredArticles(settingsService.getIgnoredArticles());

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        if (musicFolderId != null) {
            for (org.airsonic.player.domain.MusicFolder musicFolder : musicFolders) {
                if (musicFolderId.equals(musicFolder.getId())) {
                    musicFolders = Collections.singletonList(musicFolder);
                    break;
                }
            }
        }

        for (MediaFile shortcut : musicIndexService.getShortcuts(musicFolders)) {
            indexes.getShortcut().add(jaxbContentService.createJaxbArtist(shortcut, username));
        }

        MusicFolderContent musicFolderContent = musicIndexService.getMusicFolderContent(musicFolders, false);

        for (Map.Entry<MusicIndex, List<MusicIndex.SortableArtistWithMediaFiles>> entry : musicFolderContent.indexedArtists().entrySet()) {
            Index index = new Index();
            indexes.getIndex().add(index);
            index.setName(entry.getKey().getIndex());

            for (MusicIndex.SortableArtistWithMediaFiles artist : entry.getValue()) {
                for (MediaFile mediaFile : artist.getMediaFiles()) {
                    if (mediaFile.isDirectory()) {
                        Instant starredDate = mediaFileService.getMediaFileStarredDate(mediaFile, username);
                        org.subsonic.restapi.Artist a = new org.subsonic.restapi.Artist();
                        index.getArtist().add(a);
                        a.setId(String.valueOf(mediaFile.getId()));
                        a.setName(artist.getName());
                        a.setStarred(jaxbWriter.convertDate(starredDate));

                        if (mediaFile.isAlbum()) {
                            a.setAverageRating(ratingService.getAverageRating(mediaFile));
                            a.setUserRating(ratingService.getRatingForUser(username, mediaFile));
                        }
                    }
                }
            }
        }

        // Add children
        Player player = playerService.getPlayer(request, response, username);

        for (MediaFile singleSong : musicFolderContent.singleSongs()) {
            indexes.getChild().add(jaxbContentService.createJaxbChild(player, singleSong, username));
        }

        res.setIndexes(indexes);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getMusicDirectory", "/getMusicDirectory.view"})
    public void getMusicDirectory(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int id = getRequiredIntParameter(request, "id");
        MediaFile dir = mediaFileService.getMediaFile(id);
        if (dir == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Directory not found");
            return;
        }
        if (!securityService.isFolderAccessAllowed(dir, username)) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, "Access denied");
            return;
        }

        MediaFile parent = mediaFileService.getParentOf(dir);
        Directory directory = new Directory();
        directory.setId(String.valueOf(id));
        try {
            if (Objects.nonNull(parent) && !mediaFileService.isRoot(parent)) {
                directory.setParent(String.valueOf(parent.getId()));
            }
        } catch (SecurityException x) {
            // Ignored.
        }
        directory.setName(dir.getName());
        directory.setStarred(jaxbWriter.convertDate(mediaFileService.getMediaFileStarredDate(dir, username)));
        directory.setPlayCount((long) dir.getPlayCount());

        if (dir.isAlbum()) {
            directory.setAverageRating(ratingService.getAverageRating(dir));
            directory.setUserRating(ratingService.getRatingForUser(username, dir));
        }

        for (MediaFile child : mediaFileService.getVisibleChildrenOf(dir, true, true)) {
            directory.getChild().add(jaxbContentService.createJaxbChild(player, child, username));
        }

        Response res = createResponse();
        res.setDirectory(directory);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getGenres", "/getGenres.view"})
    public void getGenres(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        org.subsonic.restapi.Genres genres = new org.subsonic.restapi.Genres();

        for (org.airsonic.player.domain.Genre genre : mediaFileService.getGenres(false)) {
            org.subsonic.restapi.Genre g = new org.subsonic.restapi.Genre();
            genres.getGenre().add(g);
            g.setContent(genre.getName());
            g.setAlbumCount(genre.getAlbumCount());
            g.setSongCount(genre.getSongCount());
        }
        Response res = createResponse();
        res.setGenres(genres);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getVideos", "/getVideos.view"})
    public void getVideos(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int size = getIntParameter(request, "size", Integer.MAX_VALUE);
        int offset = getIntParameter(request, "offset", 0);
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        Videos result = new Videos();
        for (MediaFile mediaFile : mediaFileService.getVideos(musicFolders, size, offset)) {
            result.getVideo().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setVideos(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getVideoInfo", "/getVideoInfo.view"})
    public void getVideoInfo(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        error(request, response, SubsonicRESTController.ErrorCode.GENERIC, "getVideoInfo is not yet implemented");
    }

    @RequestMapping({"/getNowPlaying", "/getNowPlaying.view"})
    public void getNowPlaying(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        NowPlaying result = new NowPlaying();

        Stream.concat(statusService.getActivePlayStatuses().stream(),
                statusService.getInactivePlayStatuses().stream())
            .forEach(s -> {
                NowPlayingEntry entry = new NowPlayingEntry();
                entry.setUsername(s.getPlayer().getUsername());
                entry.setPlayerId(s.getPlayer().getId());
                entry.setPlayerName(s.getPlayer().getName());
                entry.setMinutesAgo((int) s.getMinutesAgo());
                result.getEntry().add(jaxbContentService.createJaxbChild(entry, s.getPlayer(), s.getMediaFile(), entry.getUsername()));
            });

        Response res = createResponse();
        res.setNowPlaying(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getAlbumList", "/getAlbumList.view"})
    public void getAlbumList(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int size = getIntParameter(request, "size", 10);
        int offset = getIntParameter(request, "offset", 0);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        size = Math.max(0, Math.min(size, 500));
        String type = getRequiredStringParameter(request, "type");

        List<MediaFile> albums;
        if ("highest".equals(type)) {
            albums = ratingService.getHighestRatedAlbums(offset, size, musicFolders);
        } else if ("frequent".equals(type)) {
            albums = mediaFileService.getMostFrequentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("recent".equals(type)) {
            albums = mediaFileService.getMostRecentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("newest".equals(type)) {
            albums = mediaFileService.getNewestAlbums(offset, size, musicFolders);
        } else if ("starred".equals(type)) {
            albums = mediaFileService.getStarredAlbums(offset, size, username, musicFolders);
        } else if ("alphabeticalByArtist".equals(type)) {
            albums = mediaFileService.getAlphabeticalAlbums(offset, size, true, musicFolders);
        } else if ("alphabeticalByName".equals(type)) {
            albums = mediaFileService.getAlphabeticalAlbums(offset, size, false, musicFolders);
        } else if ("byGenre".equals(type)) {
            albums = mediaFileService.getAlbumsByGenre(offset, size, getRequiredStringParameter(request, "genre"), musicFolders);
        } else if ("byYear".equals(type)) {
            albums = mediaFileService.getAlbumsByYear(offset, size, getRequiredIntParameter(request, "fromYear"),
                    getRequiredIntParameter(request, "toYear"), musicFolders);
        } else if ("random".equals(type)) {
            albums = searchService.getRandomAlbums(size, musicFolders);
        } else {
            throw new SubsonicRESTController.APIException(SubsonicRESTController.ErrorCode.GENERIC, "Invalid list type: " + type);
        }

        AlbumList result = new AlbumList();
        for (MediaFile album : albums) {
            result.getAlbum().add(jaxbContentService.createJaxbChild(player, album, username));
        }

        Response res = createResponse();
        res.setAlbumList(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getRandomSongs", "/getRandomSongs.view"})
    public void getRandomSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int size = getIntParameter(request, "size", 10);
        size = Math.max(0, Math.min(size, 500));
        String genre = getStringParameter(request, "genre");
        Integer fromYear = getIntParameter(request, "fromYear");
        Integer toYear = getIntParameter(request, "toYear");
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);
        RandomSearchCriteria criteria = new RandomSearchCriteria(size, genre, fromYear, toYear, musicFolders);

        Songs result = new Songs();
        for (MediaFile mediaFile : searchService.getRandomSongs(criteria)) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setRandomSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getSongsByGenre", "/getSongsByGenre.view"})
    public void getSongsByGenre(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        Songs songs = new Songs();

        String genre = getRequiredStringParameter(request, "genre");
        int offset = getIntParameter(request, "offset", 0);
        int count = getIntParameter(request, "count", 10);
        count = Math.max(0, Math.min(count, 500));
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        for (MediaFile mediaFile : mediaFileService.getSongsByGenre(offset, count, genre, musicFolders)) {
            songs.getSong().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }
        Response res = createResponse();
        res.setSongsByGenre(songs);
        jaxbWriter.writeResponse(request, response, res);
    }

}
