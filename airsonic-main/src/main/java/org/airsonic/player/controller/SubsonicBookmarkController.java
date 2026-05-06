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
import org.airsonic.player.service.BookmarkService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Bookmarks;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredLongParameter;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicBookmarkController extends AbstractSubsonicController {

    @Autowired
    private BookmarkService bookmarkService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private JaxbContentService jaxbContentService;

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

    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeResponse(request, response, createResponse());
    }
}
