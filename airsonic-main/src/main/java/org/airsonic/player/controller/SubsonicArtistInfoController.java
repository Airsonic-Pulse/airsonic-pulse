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
import org.airsonic.player.domain.Player;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.ArtistService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.NetworkUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.AlbumInfo;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.ArtistInfo;
import org.subsonic.restapi.ArtistInfo2;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.SimilarSongs;
import org.subsonic.restapi.SimilarSongs2;
import org.subsonic.restapi.TopSongs;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredStringParameter;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicArtistInfoController extends AbstractSubsonicController {

    @Autowired
    private LastFmService lastFmService;
    @Autowired
    private ArtistService artistService;
    @Autowired
    private AlbumService albumService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private JaxbContentService jaxbContentService;
    @Autowired
    private LocaleResolver localeResolver;

    @RequestMapping({"/getSimilarSongs", "/getSimilarSongs.view"})
    public void getSimilarSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 50);

        SimilarSongs result = new SimilarSongs();

        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> similarSongs = lastFmService.getSimilarSongsByMediaFile(mediaFile, count, musicFolders);
        Player player = playerService.getPlayer(request, response, username);
        for (MediaFile similarSong : similarSongs) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, similarSong, username));
        }

        Response res = createResponse();
        res.setSimilarSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getSimilarSongs2", "/getSimilarSongs2.view"})
    public void getSimilarSongs2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 50);

        SimilarSongs2 result = new SimilarSongs2();

        org.airsonic.player.domain.Artist artist = artistService.getArtist(id);
        if (artist == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> similarSongs = lastFmService.getSimilarSongs(artist, count, musicFolders);
        Player player = playerService.getPlayer(request, response, username);
        for (MediaFile similarSong : similarSongs) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, similarSong, username));
        }

        Response res = createResponse();
        res.setSimilarSongs2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getTopSongs", "/getTopSongs.view"})
    public void getTopSongs(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        String artist = getRequiredStringParameter(request, "artist");
        int count = getIntParameter(request, "count", 50);

        TopSongs result = new TopSongs();

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> topSongs = lastFmService.getTopSongs(artist, count, musicFolders);
        Player player = playerService.getPlayer(request, response, username);
        for (MediaFile topSong : topSongs) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, topSong, username));
        }

        Response res = createResponse();
        res.setTopSongs(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getArtistInfo", "/getArtistInfo.view"})
    public void getArtistInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 20);
        boolean includeNotPresent = ServletRequestUtils.getBooleanParameter(request, "includeNotPresent", false);

        ArtistInfo result = new ArtistInfo();

        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> similarArtists = lastFmService.getSimilarArtistsByMediaFile(mediaFile, count, includeNotPresent, musicFolders);
        for (MediaFile similarArtist : similarArtists) {
            result.getSimilarArtist().add(jaxbContentService.createJaxbArtist(similarArtist, username));
        }
        org.airsonic.player.domain.ArtistBio artistBio = lastFmService.getArtistBioByMediaFile(mediaFile, localeResolver.resolveLocale(request));
        if (artistBio != null) {
            result.setBiography(artistBio.biography());
            result.setMusicBrainzId(artistBio.musicBrainzId());
            result.setLastFmUrl(artistBio.lastFmUrl());
        }
        // extract base url
        String baseUrl = NetworkUtil.getBaseUrl(request);
        result.setSmallImageUrl(artistService.getArtistImageUrlByMediaFile(baseUrl, mediaFile, 34, username));
        result.setMediumImageUrl(artistService.getArtistImageUrlByMediaFile(baseUrl, mediaFile, 64, username));
        result.setLargeImageUrl(artistService.getArtistImageUrlByMediaFile(baseUrl, mediaFile, 300, username));

        Response res = createResponse();
        res.setArtistInfo(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getArtistInfo2", "/getArtistInfo2.view"})
    public void getArtistInfo2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);

        int id = getRequiredIntParameter(request, "id");
        int count = getIntParameter(request, "count", 20);
        boolean includeNotPresent = ServletRequestUtils.getBooleanParameter(request, "includeNotPresent", false);

        ArtistInfo2 result = new ArtistInfo2();

        org.airsonic.player.domain.Artist artist = artistService.getArtist(id);
        if (artist == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Artist not found.");
            return;
        }

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<org.airsonic.player.domain.Artist> similarArtists = lastFmService.getSimilarArtists(artist, count, includeNotPresent, musicFolders);
        for (org.airsonic.player.domain.Artist similarArtist : similarArtists) {
            result.getSimilarArtist().add(jaxbContentService.createJaxbArtist(new ArtistID3(), similarArtist, username));
        }
        org.airsonic.player.domain.ArtistBio artistBio = lastFmService.getArtistBio(artist, localeResolver.resolveLocale(request));
        if (artistBio != null) {
            result.setBiography(artistBio.biography());
            result.setMusicBrainzId(artistBio.musicBrainzId());
            result.setLastFmUrl(artistBio.lastFmUrl());
        }
        String baseUrl = NetworkUtil.getBaseUrl(request);
        result.setSmallImageUrl(artistService.getArtistImageURL(baseUrl, artist.getName(), 34, username));
        result.setMediumImageUrl(artistService.getArtistImageURL(baseUrl, artist.getName(), 64, username));
        result.setLargeImageUrl(artistService.getArtistImageURL(baseUrl, artist.getName(), 300, username));
        Response res = createResponse();
        res.setArtistInfo2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getAlbumInfo", "/getAlbumInfo.view"})
    public void getAlbumInfo(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");

        MediaFile mediaFile = this.mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Media file not found.");
            return;
        }
        org.airsonic.player.domain.AlbumNotes albumNotes = this.lastFmService.getAlbumNotesByMediaFile(mediaFile);

        AlbumInfo result = getAlbumInfoInternal(albumNotes);
        Response res = createResponse();
        res.setAlbumInfo(result);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getAlbumInfo2", "/getAlbumInfo2.view"})
    public void getAlbumInfo2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");

        Album album = albumService.getAlbum(id);
        if (album == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Album not found.");
            return;
        }
        org.airsonic.player.domain.AlbumNotes albumNotes = this.lastFmService.getAlbumNotesByAlbum(album);

        AlbumInfo result = getAlbumInfoInternal(albumNotes);
        Response res = createResponse();
        res.setAlbumInfo(result);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    private AlbumInfo getAlbumInfoInternal(org.airsonic.player.domain.AlbumNotes albumNotes) {
        AlbumInfo result = new AlbumInfo();
        if (albumNotes != null) {
            result.setNotes(albumNotes.notes());
            result.setMusicBrainzId(albumNotes.musicBrainzId());
            result.setLastFmUrl(albumNotes.lastFmUrl());
            result.setSmallImageUrl(albumNotes.smallImageUrl());
            result.setMediumImageUrl(albumNotes.mediumImageUrl());
            result.setLargeImageUrl(albumNotes.largeImageUrl());
        }
        return result;
    }

}
