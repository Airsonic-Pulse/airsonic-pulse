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

import org.airsonic.player.domain.InternetRadio;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.InternetRadioService;
import org.airsonic.player.service.SecurityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.InternetRadioStation;
import org.subsonic.restapi.InternetRadioStations;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredStringParameter;
import static org.springframework.web.bind.ServletRequestUtils.getStringParameter;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicRadioController extends AbstractSubsonicController {

    @Autowired
    private InternetRadioService internetRadioService;
    @Autowired
    private SecurityService securityService;

    @RequestMapping({"/getInternetRadioStations", "/getInternetRadioStations.view"})
    public void getInternetRadioStations(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);

        InternetRadioStations result = new InternetRadioStations();
        for (InternetRadio radio : internetRadioService.getEnabledInternetRadios()) {
            InternetRadioStation i = new InternetRadioStation();
            i.setId(String.valueOf(radio.getId()));
            i.setName(radio.getName());
            i.setStreamUrl(radio.getStreamUrl());
            i.setHomePageUrl(radio.getHomepageUrl());
            result.getInternetRadioStation().add(i);
        }
        Response res = createResponse();
        res.setInternetRadioStations(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/createInternetRadioStation", "/createInternetRadioStation.view"})
    public void createInternetRadioStation(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        User user = securityService.getCurrentUser(request);
        if (!user.isAdminRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED,
                  user.getUsername() + " is not authorized to manage internet radio stations.");
            return;
        }

        String streamUrl = getRequiredStringParameter(request, "streamUrl");
        String name = getRequiredStringParameter(request, "name");
        String homepageUrl = getStringParameter(request, "homepageUrl", null);

        internetRadioService.createInternetRadio(name, streamUrl, homepageUrl, true);

        Response res = createResponse();
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/updateInternetRadioStation", "/updateInternetRadioStation.view"})
    public void updateInternetRadioStation(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        User user = securityService.getCurrentUser(request);
        if (!user.isAdminRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED,
                  user.getUsername() + " is not authorized to manage internet radio stations.");
            return;
        }

        Integer id = getRequiredIntParameter(request, "id");
        InternetRadio radio = internetRadioService.getInternetRadioById(id);
        if (radio == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND,
                  "Internet radio station not found: " + id);
            return;
        }

        String streamUrl = getRequiredStringParameter(request, "streamUrl");
        String name = getRequiredStringParameter(request, "name");
        String homepageUrl = getStringParameter(request, "homepageUrl", null);

        internetRadioService.updateInternetRadio(id, name, streamUrl, homepageUrl, radio.isEnabled());

        Response res = createResponse();
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/deleteInternetRadioStation", "/deleteInternetRadioStation.view"})
    public void deleteInternetRadioStation(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        User user = securityService.getCurrentUser(request);
        if (!user.isAdminRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED,
                  user.getUsername() + " is not authorized to manage internet radio stations.");
            return;
        }

        Integer id = getRequiredIntParameter(request, "id");
        InternetRadio radio = internetRadioService.getInternetRadioById(id);
        if (radio == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND,
                  "Internet radio station not found: " + id);
            return;
        }

        internetRadioService.deleteInternetRadioById(id);

        Response res = createResponse();
        jaxbWriter.writeResponse(request, response, res);
    }

}
