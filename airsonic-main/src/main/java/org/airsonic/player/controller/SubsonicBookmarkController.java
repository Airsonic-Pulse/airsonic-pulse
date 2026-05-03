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

import org.airsonic.player.domain.Bookmark;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PlayerTechnology;
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Bookmarks;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredLongParameter;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicBookmarkController {

    @Autowired
    private JAXBWriter jaxbWriter;
    @Autowired
    private BookmarkService bookmarkService;
    @Autowired
    private MediaFileService mediaFileService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private JaxbContentService jaxbContentService;

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void handleMissingRequestParam(HttpServletRequest request,
                                          HttpServletResponse response,
                                          MissingServletRequestParameterException exception) {
        error(request, response, SubsonicRESTController.ErrorCode.MISSING_PARAMETER,
                "Required param (" + exception.getParameterName() + ") is missing");
    }

    @RequestMapping({"/getBookmarks", "/getBookmarks.view"})
    public void getBookmarks(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        Bookmarks result = new Bookmarks();
        for (Bookmark bookmark : bookmarkService.getBookmarks(username)) {
            org.subsonic.restapi.Bookmark b = new org.subsonic.restapi.Bookmark();
            result.getBookmark().add(b);
            b.setPosition(bookmark.getPositionMillis());
            b.setUsername(bookmark.getUsername());
            b.setComment(bookmark.getComment());
            b.setCreated(jaxbWriter.convertDate(bookmark.getCreated()));
            b.setChanged(jaxbWriter.convertDate(bookmark.getChanged()));

            MediaFile mediaFile = mediaFileService.getMediaFile(bookmark.getMediaFileId());
            b.setEntry(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setBookmarks(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/createBookmark", "/createBookmark.view"})
    public void createBookmark(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        int mediaFileId = getRequiredIntParameter(request, "id");
        long position = getRequiredLongParameter(request, "position");
        String comment = request.getParameter("comment");

        bookmarkService.setBookmark(username, mediaFileId, position, comment);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/deleteBookmark", "/deleteBookmark.view"})
    public void deleteBookmark(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        String username = securityService.getCurrentUsername(request);
        int mediaFileId = getRequiredIntParameter(request, "id");
        bookmarkService.deleteBookmark(username, mediaFileId);

        writeEmptyResponse(request, response);
    }

    private Response createResponse() {
        return jaxbWriter.createResponse(true);
    }

    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeResponse(request, response, createResponse());
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
