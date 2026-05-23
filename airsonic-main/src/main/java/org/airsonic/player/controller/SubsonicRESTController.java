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

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.OpenSubsonicExtension;
import org.subsonic.restapi.OpenSubsonicExtensions;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.List;

/**
 * Multi-controller used for the REST API.
 * <p/>
 * For documentation, please refer to api.jsp.
 * <p/>
 * Note: Exceptions thrown from the methods are intercepted by RESTFilter.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicRESTController extends AbstractSubsonicController {

    private static final Logger LOG = LoggerFactory.getLogger(SubsonicRESTController.class);

    @RequestMapping({"/getOpenSubsonicExtensions", "/getOpenSubsonicExtensions.view"})
    public void getOpenSubsonicExtensions(HttpServletRequest request, HttpServletResponse response) {
        OpenSubsonicExtensions container = new OpenSubsonicExtensions();
        container.getOpenSubsonicExtension().addAll(OPENSUBSONIC_EXTENSIONS);
        Response res = createResponse();
        res.setOpenSubsonicExtensions(container);
        jaxbWriter.writeResponse(request, response, res);
    }

    private static final List<OpenSubsonicExtension> OPENSUBSONIC_EXTENSIONS = buildExtensionList();

    private static List<OpenSubsonicExtension> buildExtensionList() {
        return List.of(
            buildExtension("formPost", 1),
            buildExtension("transcodeOffset", 1),
            buildExtension("songLyrics", 1),
            buildExtension("indexBasedQueue", 1)
        );
    }

    private static OpenSubsonicExtension buildExtension(String name, int... versions) {
        OpenSubsonicExtension ext = new OpenSubsonicExtension();
        ext.setName(name);
        for (int v : versions) {
            ext.getVersions().add(v);
        }
        return ext;
    }



    public static class APIException extends Exception {
        private String message;
        private ErrorCode error;

        public APIException(ErrorCode error, String message) {
            this.message = message;
            this.error = error;
        }

        public APIException(ErrorCode error) {
            this(error, error.getMessage());
        }

        @Override
        public String getMessage() {
            return message;
        }

        public ErrorCode getError() {
            return error;
        }
    }

    public enum ErrorCode {

        GENERIC(0, "A generic error."),
        MISSING_PARAMETER(10, "Required parameter is missing."),
        PROTOCOL_MISMATCH_CLIENT_TOO_OLD(20, "Incompatible Airsonic-Pulse REST protocol version. Client must upgrade."),
        PROTOCOL_MISMATCH_SERVER_TOO_OLD(30, "Incompatible Airsonic-Pulse REST protocol version. Server must upgrade."),
        NOT_AUTHENTICATED(40, "Wrong username or password."),
        NOT_AUTHENTICATED_UPGRADE_TO_NON_HASHED(41, "Wrong username or password, but try authenticating via non-hashed password."),
        NOT_AUTHORIZED(50, "User is not authorized for the given operation."),
        NOT_FOUND(70, "Requested data was not found.");

        private final int code;
        private final String message;

        ErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        public int getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }
    }
}
