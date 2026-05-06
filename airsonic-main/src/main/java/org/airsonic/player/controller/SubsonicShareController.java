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
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.ShareService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.Shares;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.web.bind.ServletRequestUtils.getLongParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameters;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicShareController extends AbstractSubsonicController {

    @Autowired
    private ShareService shareService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private JaxbContentService jaxbContentService;

    @RequestMapping({"/getShares", "/getShares.view"})
    public void getShares(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        Shares result = new Shares();
        for (org.airsonic.player.domain.entity.Share share : shareService.getSharesForUser(user)) {
            org.subsonic.restapi.Share s = createJaxbShare(request, share);
            result.getShare().add(s);

            for (MediaFile mediaFile : shareService.getSharedFiles(share.getId(), musicFolders)) {
                s.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
            }
        }
        Response res = createResponse();
        res.setShares(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/createShare", "/createShare.view"})
    public void createShare(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isShareRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to share media.");
            return;
        }

        List<MediaFile> files = new ArrayList<MediaFile>();
        for (int id : getRequiredIntParameters(request, "id")) {
            files.add(mediaFileService.getMediaFile(id));
        }

        org.airsonic.player.domain.entity.Share share = shareService.createShare(username, files);
        share.setDescription(request.getParameter("description"));
        long expires = getLongParameter(request, "expires", 0L);
        if (expires != 0) {
            share.setExpires(Instant.ofEpochMilli(expires));
        }
        shareService.updateShare(share);

        Shares result = new Shares();
        org.subsonic.restapi.Share s = createJaxbShare(request, share);
        result.getShare().add(s);

        List<org.airsonic.player.domain.MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);

        for (MediaFile mediaFile : shareService.getSharedFiles(share.getId(), musicFolders)) {
            s.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
        }

        Response res = createResponse();
        res.setShares(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/deleteShare", "/deleteShare.view"})
    public void deleteShare(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        int id = getRequiredIntParameter(request, "id");

        org.airsonic.player.domain.entity.Share share = shareService.getShareById(id);
        if (share == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Shared media not found.");
            return;
        }
        if (!user.isAdminRole() && !share.getUsername().equals(user.getUsername())) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, "Not authorized to delete shared media.");
            return;
        }

        shareService.deleteShare(id);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/updateShare", "/updateShare.view"})
    public void updateShare(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        int id = getRequiredIntParameter(request, "id");

        org.airsonic.player.domain.entity.Share share = shareService.getShareById(id);
        if (share == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Shared media not found.");
            return;
        }
        if (!user.isAdminRole() && !share.getUsername().equals(user.getUsername())) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, "Not authorized to modify shared media.");
            return;
        }

        share.setDescription(request.getParameter("description"));
        String expiresString = request.getParameter("expires");
        if (expiresString != null) {
            long expires = Long.parseLong(expiresString);
            share.setExpires(expires == 0L ? null : Instant.ofEpochMilli(expires));
        }
        shareService.updateShare(share);
        writeEmptyResponse(request, response);
    }

    private org.subsonic.restapi.Share createJaxbShare(HttpServletRequest request, org.airsonic.player.domain.entity.Share share) {
        org.subsonic.restapi.Share result = new org.subsonic.restapi.Share();
        result.setId(String.valueOf(share.getId()));
        result.setUrl(shareService.getShareUrl(request, share));
        result.setUsername(share.getUsername());
        result.setCreated(jaxbWriter.convertDate(share.getCreated()));
        result.setVisitCount(share.getVisitCount());
        result.setDescription(share.getDescription());
        result.setExpires(jaxbWriter.convertDate(share.getExpires()));
        result.setLastVisited(jaxbWriter.convertDate(share.getLastVisited()));
        return result;
    }

    private void writeEmptyResponse(HttpServletRequest request, HttpServletResponse response) {
        jaxbWriter.writeResponse(request, response, createResponse());
    }
}
