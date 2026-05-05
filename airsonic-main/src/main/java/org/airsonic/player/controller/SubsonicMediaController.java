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
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayerTechnology;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;
import org.subsonic.restapi.Lyrics;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.security.Principal;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicMediaController {

    @Autowired
    private JAXBWriter jaxbWriter;
    @Autowired
    private StreamController streamController;
    @Autowired
    private HLSController hlsController;
    @Autowired
    private DownloadController downloadController;
    @Autowired
    private CoverArtController coverArtController;
    @Autowired
    private AvatarController avatarController;
    @Autowired
    private LyricsService lyricsService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private MediaFileService mediaFileService;

    @ExceptionHandler(SubsonicRESTController.APIException.class)
    public ResponseEntity<String> apiException(ServletWebRequest swr, SubsonicRESTController.APIException exception) {
        Entry<String, String> exceptionResponse = jaxbWriter.serializeForType(swr.getRequest(),
                jaxbWriter.createErrorResponse(exception));
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType(exceptionResponse.getKey()))
                .body(exceptionResponse.getValue());
    }

    @RequestMapping({"/download", "/download.view"})
    public ResponseEntity<Resource> download(Principal p,
            @RequestParam(required = false, name = "id") String id,
            @RequestParam(required = false, name = "playlist") Integer playlist,
            @RequestParam(required = false, name = "player") Integer player,
            @RequestParam(required = false, name = "i") List<Integer> indices,
            ServletWebRequest swr) throws Exception {
        HttpServletRequest request = wrapRequest(swr.getRequest());
        final Integer playerId = Optional.ofNullable(request.getParameter("player")).map(Integer::valueOf).orElse(null);
        Optional<Integer> idInt = Optional.ofNullable(id).map(this::mapId).filter(StringUtils::isNumeric).map(Integer::valueOf);

        User user = securityService.getUserByName(p.getName());
        if (!user.isDownloadRole()) {
            throw new SubsonicRESTController.APIException(SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to download files.");
        }
        return downloadController.handleRequest(p, idInt, playlist, playerId, indices,
                new ServletWebRequest(request, swr.getResponse()));
    }

    @RequestMapping({"/stream", "/stream.view"})
    public ResponseEntity<Resource> stream(Authentication authentication,
            @RequestParam(required = false, name = "playlist") Integer playlist,
            @RequestParam(required = false, name = "format") String format,
            @RequestParam(required = false, name = "suffix") String suffix,
            @RequestParam("maxBitRate") Optional<Integer> maxBitRate,
            @RequestParam("id") Optional<Integer> id,
            @RequestParam("path") Optional<String> path,
            @RequestParam(required = false, name = "timeOffset") Double timeOffset,
            ServletWebRequest swr) throws Exception {
        HttpServletRequest request = wrapRequest(swr.getRequest());
        User user = securityService.getUserByName(authentication.getName());
        if (!user.isStreamRole()) {
            throw new SubsonicRESTController.APIException(SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to play files.");
        }

        return streamController.handleRequest(authentication, playlist, format, suffix, maxBitRate, id, path,
                timeOffset, new ServletWebRequest(request, swr.getResponse()));
    }

    @RequestMapping({"/hls", "/hls.view"})
    public void hls(Authentication authentication, @RequestParam Integer id, HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isStreamRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to play files.");
            return;
        }

        hlsController.handleHlsRequest(authentication, id, request, response);
    }

    @RequestMapping({"/getCoverArt", "/getCoverArt.view"})
    public void getCoverArt(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        coverArtController.get(
                ServletRequestUtils.getStringParameter(request, "id"),
                ServletRequestUtils.getIntParameter(request, "size"),
                ServletRequestUtils.getIntParameter(request, "offset", 60),
                request, response);
    }

    @RequestMapping({"/getAvatar", "/getAvatar.view"})
    public void getAvatar(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        Integer id = ServletRequestUtils.getIntParameter(request, "id");
        String username = ServletRequestUtils.getStringParameter(request, "username");
        boolean forceCustom = ServletRequestUtils.getBooleanParameter(request, "forceCustom", false);
        avatarController.handleRequest(id, username, forceCustom, response);
    }

    @RequestMapping({"/getLyrics", "/getLyrics.view"})
    public void getLyrics(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        String artist = request.getParameter("artist");
        String title = request.getParameter("title");

        String username = securityService.getCurrentUsername(request);
        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);


        Lyrics result = new Lyrics();
        result.setArtist(artist);
        result.setTitle(title);
        org.airsonic.player.domain.Lyrics lyrics = lyricsService.getLyricsFromArtistAndTitle(artist, title, musicFolders);
        if (lyrics != null) {
            result.setContent(lyrics.getLyrics());
        }

        Response res = createResponse();
        res.setLyrics(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getCaptions", "/getCaptions.view"})
    public void getCaptions(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        error(request, response, SubsonicRESTController.ErrorCode.GENERIC, "getCaptions is not yet implemented");
    }

    private Response createResponse() {
        return jaxbWriter.createResponse(true);
    }

    private void error(HttpServletRequest request, HttpServletResponse response,
                       SubsonicRESTController.ErrorCode code, String message) {
        jaxbWriter.writeErrorResponse(request, response, code, message);
    }

    private HttpServletRequest wrapRequest(HttpServletRequest request) {
        return wrapRequest(request, false);
    }

    private HttpServletRequest wrapRequest(final HttpServletRequest request, boolean jukebox) {
        final Integer playerId = createPlayerIfNecessary(request, jukebox);
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getParameter(String name) {
                // Returns the correct player to be used in PlayerService.getPlayer()
                if ("player".equals(name)) {
                    return playerId == null ? null : String.valueOf(playerId);
                }

                // Support old style ID parameters.
                if ("id".equals(name)) {
                    return mapId(request.getParameter("id"));
                }

                return super.getParameter(name);
            }
        };
    }

    private String mapId(String id) {
        if (id == null || id.startsWith(CoverArtController.ALBUM_COVERART_PREFIX) ||
                id.startsWith(CoverArtController.ARTIST_COVERART_PREFIX) || StringUtils.isNumeric(id)) {
            return id;
        }

        try {
            String path = StringUtil.utf8HexDecode(id);
            MediaFile mediaFile = mediaFileService.getMediaFile(path);
            return String.valueOf(mediaFile.getId());
        } catch (Exception x) {
            return id;
        }
    }

    private Integer createPlayerIfNecessary(HttpServletRequest request, boolean jukebox) {
        String username = request.getRemoteUser();
        String clientId = request.getParameter("c");
        if (jukebox) {
            clientId += "-jukebox";
        }

        List<Player> players = playerService.getPlayersForUserAndClientId(username, clientId);

        // If not found, create it.
        if (players.isEmpty()) {
            Player player = new Player();
            player.setIpAddress(request.getRemoteAddr());
            player.setUsername(username);
            player.setClientId(clientId);
            player.setName(clientId);
            player.setTechnology(jukebox ? PlayerTechnology.JUKEBOX : PlayerTechnology.EXTERNAL_WITH_PLAYLIST);
            playerService.createPlayer(player);
            players = playerService.getPlayersForUserAndClientId(username, clientId);
        }

        // Return the player ID.
        return !players.isEmpty() ? players.get(0).getId() : null;
    }
}
