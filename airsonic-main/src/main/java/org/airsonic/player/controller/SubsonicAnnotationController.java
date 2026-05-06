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
import org.airsonic.player.domain.PlayStatus;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.ArtistService;
import org.airsonic.player.service.AudioScrobblerService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.RatingService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.StatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.Starred;
import org.subsonic.restapi.Starred2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.springframework.web.bind.ServletRequestUtils.getBooleanParameter;
import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getIntParameters;
import static org.springframework.web.bind.ServletRequestUtils.getLongParameters;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameters;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicAnnotationController extends AbstractSubsonicController {

    private static final Logger LOG = LoggerFactory.getLogger(SubsonicAnnotationController.class);

    @Autowired
    private AlbumService albumService;
    @Autowired
    private ArtistService artistService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private RatingService ratingService;
    @Autowired
    private StatusService statusService;
    @Autowired
    private AudioScrobblerService audioScrobblerService;
    @Autowired
    private JaxbContentService jaxbContentService;
    @Autowired
    private SecurityService securityService;

    @RequestMapping({"/scrobble", "/scrobble.view"})
    public void scrobble(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        boolean submission = getBooleanParameter(request, "submission", true);
        int[] ids = getRequiredIntParameters(request, "id");
        long[] times = getLongParameters(request, "time");
        if (times.length > 0 && times.length != ids.length) {
            error(request, response, SubsonicRESTController.ErrorCode.GENERIC, "Wrong number of timestamps: " + times.length);
            return;
        }

        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            MediaFile file = mediaFileService.getMediaFile(id);
            if (file == null) {
                LOG.warn("File to scrobble not found: " + id);
                continue;
            }
            Instant time = times.length == 0 ? null : Instant.ofEpochMilli(times[i]);

            statusService.addRemotePlay(new PlayStatus(UUID.randomUUID(), file, player, time == null ? Instant.now() : time));
            mediaFileService.incrementPlayCount(player, file);
            audioScrobblerService.register(file, player.getUsername(), submission, time);
        }

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/star", "/star.view"})
    public void star(HttpServletRequest request, HttpServletResponse response) {
        starOrUnstar(request, response, true);
    }

    @RequestMapping({"/unstar", "/unstar.view"})
    public void unstar(HttpServletRequest request, HttpServletResponse response) {
        starOrUnstar(request, response, false);
    }

    private void starOrUnstar(HttpServletRequest request, HttpServletResponse response, boolean star) {
        request = wrapRequest(request);

        String username = securityService.getCurrentUser(request).getUsername();
        for (int id : getIntParameters(request, "id")) {
            MediaFile mediaFile = mediaFileService.getMediaFile(id);
            if (mediaFile == null) {
                error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Media file not found: " + id);
                return;
            }
            if (star) {
                mediaFileService.starMediaFiles(List.of(id), username);
            } else {
                mediaFileService.unstarMediaFiles(List.of(id), username);
            }
        }
        for (int albumId : getIntParameters(request, "albumId")) {
            if (!albumService.starOrUnstar(albumId, username, star)) {
                error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Album not found: " + albumId);
                return;
            }
        }
        for (int artistId : getIntParameters(request, "artistId")) {
            if (!artistService.starOrUnstar(artistId, username, star)) {
                error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Artist not found: " + artistId);
                return;
            }
        }
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getStarred", "/getStarred.view"})
    public void getStarred(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        Starred result = new Starred();
        for (MediaFile artist : mediaFileService.getStarredArtists(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getArtist().add(jaxbContentService.createJaxbArtist(artist, username));
        }
        for (MediaFile album : mediaFileService.getStarredAlbums(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getAlbum().add(jaxbContentService.createJaxbChild(player, album, username));
        }
        for (MediaFile song : mediaFileService.getStarredSongs(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, song, username));
        }
        Response res = createResponse();
        res.setStarred(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getStarred2", "/getStarred2.view"})
    public void getStarred2(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        Integer musicFolderId = getIntParameter(request, "musicFolderId");
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username, musicFolderId);

        Starred2 result = new Starred2();
        for (org.airsonic.player.domain.Artist artist : artistService.getStarredArtists(username, musicFolders)) {
            result.getArtist().add(jaxbContentService.createJaxbArtist(new ArtistID3(), artist, username));
        }
        for (Album album : albumService.getStarredAlbums(username, musicFolders)) {
            result.getAlbum().add(jaxbContentService.createJaxbAlbum(new AlbumID3(), album, username));
        }
        for (MediaFile song : mediaFileService.getStarredSongs(0, Integer.MAX_VALUE, username, musicFolders)) {
            result.getSong().add(jaxbContentService.createJaxbChild(player, song, username));
        }
        Response res = createResponse();
        res.setStarred2(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/setRating", "/setRating.view"})
    public void setRating(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Integer rating = getRequiredIntParameter(request, "rating");
        if (rating == 0) {
            rating = null;
        }

        int id = getRequiredIntParameter(request, "id");
        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "File not found: " + id);
            return;
        }

        String username = securityService.getCurrentUsername(request);
        ratingService.setRatingForUser(username, mediaFile, rating);

        writeEmptyResponse(request, response);
    }

    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeResponse(request, response, createResponse());
    }
}
