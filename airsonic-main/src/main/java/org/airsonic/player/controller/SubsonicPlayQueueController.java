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
import org.airsonic.player.domain.SavedPlayQueue;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getIntParameters;
import static org.springframework.web.bind.ServletRequestUtils.getLongParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredStringParameter;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicPlayQueueController extends AbstractSubsonicController {

    @Autowired
    private PlayQueueService playQueueService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private JaxbContentService jaxbContentService;

    @RequestMapping({"/getPlayQueue", "/getPlayQueue.view"})
    public void getPlayQueue(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        SavedPlayQueue playQueue = playQueueService.loadSavedPlayQueueForRest(username);
        if (playQueue == null) {
            writeEmptyResponse(request, response);
            return;
        }

        org.subsonic.restapi.PlayQueue restPlayQueue = new org.subsonic.restapi.PlayQueue();
        restPlayQueue.setUsername(playQueue.getUsername());
        restPlayQueue.setCurrent(Optional.ofNullable(playQueue.getCurrentMediaFile()).map(MediaFile::getId).orElse(null));
        restPlayQueue.setPosition(playQueue.getPositionMillis());
        restPlayQueue.setChanged(jaxbWriter.convertDate(playQueue.getChanged()));
        restPlayQueue.setChangedBy(playQueue.getChangedBy());

        for (MediaFile mediaFile : playQueue.getMediaFiles()) {
            if (mediaFile != null) {
                restPlayQueue.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
            }
        }

        Response res = createResponse();
        res.setPlayQueue(restPlayQueue);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/savePlayQueue", "/savePlayQueue.view"})
    public void savePlayQueue(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        List<Integer> mediaFileIds = Arrays.stream(getIntParameters(request, "id")).boxed().toList();
        Integer current = getIntParameter(request, "current");
        Long position = getLongParameter(request, "position");
        String changedBy = getRequiredStringParameter(request, "c");

        if (!mediaFileIds.contains(current)) {
            error(request, response, SubsonicRESTController.ErrorCode.GENERIC, "Current track is not included in play queue");
            return;
        }

        playQueueService.savePlayQueue(username, mediaFileIds, current, position, changedBy);

        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/getPlayQueueByIndex", "/getPlayQueueByIndex.view"})
    public void getPlayQueueByIndex(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        SavedPlayQueue playQueue = playQueueService.loadSavedPlayQueueForRest(username);
        if (playQueue == null) {
            writeEmptyResponse(request, response);
            return;
        }

        org.subsonic.restapi.PlayQueueByIndex restPlayQueue = new org.subsonic.restapi.PlayQueueByIndex();
        restPlayQueue.setUsername(playQueue.getUsername());
        // Prefer the explicitly stored currentIndex (savePlayQueueByIndex path); for queues
        // saved id-based via the legacy savePlayQueue, fall back to indexOf(currentMediaFile)
        // — first occurrence, the same imprecision id-based saves inherently have.
        Integer currentIndex = playQueue.getCurrentIndex();
        if (currentIndex == null && playQueue.getCurrentMediaFile() != null) {
            int idx = playQueue.getMediaFiles().indexOf(playQueue.getCurrentMediaFile());
            currentIndex = idx >= 0 ? idx : null;
        }
        restPlayQueue.setCurrentIndex(currentIndex);
        restPlayQueue.setPosition(playQueue.getPositionMillis());
        restPlayQueue.setChanged(jaxbWriter.convertDate(playQueue.getChanged()));
        restPlayQueue.setChangedBy(playQueue.getChangedBy());

        for (MediaFile mediaFile : playQueue.getMediaFiles()) {
            if (mediaFile != null) {
                restPlayQueue.getEntry().add(jaxbContentService.createJaxbChild(player, mediaFile, username));
            }
        }

        Response res = createResponse();
        res.setPlayQueueByIndex(restPlayQueue);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/savePlayQueueByIndex", "/savePlayQueueByIndex.view"})
    public void savePlayQueueByIndex(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        List<Integer> mediaFileIds = Arrays.stream(getIntParameters(request, "id")).boxed().toList();
        Integer currentIndex = getIntParameter(request, "currentIndex");
        Long position = getLongParameter(request, "position");
        String changedBy = getRequiredStringParameter(request, "c");

        boolean indexOutOfRange = currentIndex != null && (currentIndex < 0 || currentIndex >= mediaFileIds.size());
        if (indexOutOfRange) {
            error(request, response, SubsonicRESTController.ErrorCode.GENERIC, "currentIndex is out of range");
            return;
        }

        playQueueService.savePlayQueueByIndex(username, mediaFileIds, currentIndex, position, changedBy);

        writeEmptyResponse(request, response);
    }

}
