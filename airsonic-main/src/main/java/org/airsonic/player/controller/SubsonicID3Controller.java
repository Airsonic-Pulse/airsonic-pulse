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

import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicIndex;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.ArtistService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MusicIndexService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.AlbumList2;
import org.subsonic.restapi.AlbumWithSongsID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.ArtistWithAlbumsID3;
import org.subsonic.restapi.ArtistsID3;
import org.subsonic.restapi.IndexID3;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.Map;
import java.util.SortedMap;

import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredStringParameter;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicID3Controller extends AbstractSubsonicController {

    @Autowired
    private ArtistService artistService;
    @Autowired
    private AlbumService albumService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private MusicIndexService musicIndexService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private JaxbContentService jaxbContentService;
    @Autowired
    private SettingsService settingsService;

    @RequestMapping({"/getArtists", "/getArtists.view"})
    public void getArtists(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        ArtistsID3 result = new ArtistsID3();
        result.setIgnoredArticles(settingsService.getIgnoredArticles());
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        List<org.airsonic.player.domain.Artist> artists = artistService.getAlphabeticalArtists(musicFolders);
        SortedMap<MusicIndex, List<MusicIndex.SortableArtistWithArtist>> indexedArtists = musicIndexService.getIndexedArtists(artists);
        for (Map.Entry<MusicIndex, List<MusicIndex.SortableArtistWithArtist>> entry : indexedArtists.entrySet()) {
            IndexID3 index = new IndexID3();
            result.getIndex().add(index);
            index.setName(entry.getKey().getIndex());
            for (MusicIndex.SortableArtistWithArtist sortableArtist : entry.getValue()) {
                index.getArtist().add(jaxbContentService.createJaxbArtist(new ArtistID3(), sortableArtist.getArtist(), username));
            }
        }

        Response res = createResponse();
        res.setArtists(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getArtist", "/getArtist.view"})
    public void getArtist(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = securityService.getCurrentUsername(request);
        int id = getRequiredIntParameter(request, "id");
        org.airsonic.player.domain.Artist artist = artistService.getArtist(id);
        if (artist == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        ArtistWithAlbumsID3 result = jaxbContentService.createJaxbArtist(new ArtistWithAlbumsID3(), artist, username);
        for (Album album : albumService.getAlbumsByArtist(artist.getName(), musicFolders)) {
            result.getAlbum().add(jaxbContentService.createJaxbAlbum(new AlbumID3(), album, username));
        }

        Response res = createResponse();
        res.setArtist(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getAlbum", "/getAlbum.view"})
    public void getAlbum(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int id = getRequiredIntParameter(request, "id");
        Album album = albumService.getAlbum(id);
        if (album == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Album not found.");
            return;
        }

        // Load the album's songs once, reuse them for both discTitles (built inside the 4-arg
        // createJaxbAlbum overload) and the <song> entries below — single getSongsForAlbum
        // fetch, no per-album duplicate.
        List<MediaFile> albumSongs = mediaFileService.getSongsForAlbum(album.getArtist(), album.getName());
        AlbumWithSongsID3 result = jaxbContentService.createJaxbAlbum(new AlbumWithSongsID3(), album, username, albumSongs);
        for (MediaFile mediaFile : albumSongs) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setAlbum(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getSong", "/getSong.view"})
    public void getSong(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int id = getRequiredIntParameter(request, "id");
        MediaFile song = mediaFileService.getMediaFile(id);
        if (song == null || song.isDirectory()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Song not found.");
            return;
        }
        if (!securityService.isFolderAccessAllowed(song, username)) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, "Access denied");
            return;
        }

        Response res = createResponse();
        res.setSong(jaxbContentService.createJaxbChild(player, song, username));
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getAlbumList2", "/getAlbumList2.view"})
    public void getAlbumList2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int size = getIntParameter(request, "size", 10);
        int offset = getIntParameter(request, "offset", 0);
        size = Math.max(0, Math.min(size, 500));
        String type = getRequiredStringParameter(request, "type");
        String username = securityService.getCurrentUsername(request);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        List<Album> albums;
        if ("frequent".equals(type)) {
            albums = albumService.getMostFrequentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("recent".equals(type)) {
            albums = albumService.getMostResentlyPlayedAlbums(offset, size, musicFolders);
        } else if ("newest".equals(type)) {
            albums = albumService.getRecentlyAddedAlbums(offset, size, musicFolders);
        } else if ("alphabeticalByArtist".equals(type)) {
            albums = albumService.getAlphabeticalAlbums(offset, size, true, false, musicFolders);
        } else if ("alphabeticalByName".equals(type)) {
            albums = albumService.getAlphabeticalAlbums(offset, size, false, false, musicFolders);
        } else if ("byGenre".equals(type)) {
            albums = albumService.getAlbumsByGenre(offset, size, getRequiredStringParameter(request, "genre"), musicFolders);
        } else if ("byYear".equals(type)) {
            albums = albumService.getAlbumsByYear(offset, size, getRequiredIntParameter(request, "fromYear"),
                                              getRequiredIntParameter(request, "toYear"), musicFolders);
        } else if ("starred".equals(type)) {
            albums = albumService.getStarredAlbums(offset, size, username, musicFolders);
        } else if ("random".equals(type)) {
            albums = searchService.getRandomAlbumsId3(size, musicFolders);
        } else {
            throw new SubsonicRESTController.APIException(SubsonicRESTController.ErrorCode.GENERIC, "Invalid list type: " + type);
        }
        AlbumList2 result = new AlbumList2();
        for (Album album : albums) {
            result.getAlbum().add(jaxbContentService.createJaxbAlbum(new AlbumID3(), album, username));
        }
        Response res = createResponse();
        res.setAlbumList2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

}
