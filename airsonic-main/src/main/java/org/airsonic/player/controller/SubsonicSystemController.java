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
 */
package org.airsonic.player.controller;

import org.airsonic.player.service.MediaScannerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.License;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.ScanStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.datatype.XMLGregorianCalendar;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicSystemController extends AbstractSubsonicController {

    @Autowired
    private MediaScannerService mediaScannerService;

    @RequestMapping({"/ping", "/ping.view"})
    public void ping(HttpServletRequest request, HttpServletResponse response) {
        Response res = createResponse();
        this.jaxbWriter.writeResponse(request, response, res);
    }

    /**
     * CAUTION : this method is required by mobile applications and must not be removed.
     */
    @RequestMapping({"/getLicense", "/getLicense.view"})
    public void getLicense(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        License license = new License();

        license.setEmail("airsonic@github.com");
        license.setValid(true);
        XMLGregorianCalendar farFuture = this.jaxbWriter.convertDate(Instant.now().plus(ChronoUnit.YEARS.getDuration().multipliedBy(100)));
        license.setLicenseExpires(farFuture);
        license.setTrialExpires(farFuture);

        Response res = createResponse();
        res.setLicense(license);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/startScan", "/startScan.view"})
    public void startScan(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        this.mediaScannerService.scanLibrary();
        getScanStatus(request, response);
    }

    @RequestMapping({"/getScanStatus", "/getScanStatus.view"})
    public void getScanStatus(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        ScanStatus scanStatus = new ScanStatus();
        scanStatus.setScanning(this.mediaScannerService.isScanning());
        scanStatus.setCount((long) this.mediaScannerService.getScanCount());

        Response res = createResponse();
        res.setScanStatus(scanStatus);
        this.jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getChatMessages", "/getChatMessages.view"})
    public void getChatMessages(HttpServletRequest request, HttpServletResponse response) throws SubsonicRESTController.APIException {
        throw new SubsonicRESTController.APIException(SubsonicRESTController.ErrorCode.NOT_FOUND,
                "Chat messages are not supported on this server");
    }

    @RequestMapping({"/addChatMessage", "/addChatMessage.view"})
    public void addChatMessage(HttpServletRequest request, HttpServletResponse response) throws SubsonicRESTController.APIException {
        throw new SubsonicRESTController.APIException(SubsonicRESTController.ErrorCode.NOT_FOUND,
                "Posting chat messages is not supported on this server");
    }

}
