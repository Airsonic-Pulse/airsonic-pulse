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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.ServletWebRequest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Map.Entry;

/**
 * Centralized exception handling for the Subsonic REST API controllers.
 * <p>
 * Catches {@link SubsonicRESTController.APIException} and
 * {@link MissingServletRequestParameterException} thrown from any of the Subsonic
 * domain controllers, and serializes the error as a Subsonic JAXB envelope via
 * {@link JAXBWriter}.
 * <p>
 * Scoped via {@code assignableTypes} to the explicit list of Subsonic controllers
 * so it does not intercept exceptions from non-Subsonic controllers.
 */
@ControllerAdvice(assignableTypes = {
    SubsonicRESTController.class,
    SubsonicSystemController.class,
    SubsonicBrowsingController.class,
    SubsonicID3Controller.class,
    SubsonicSearchController.class,
    SubsonicAnnotationController.class,
    SubsonicPlayQueueController.class,
    SubsonicBookmarkController.class,
    SubsonicShareController.class,
    SubsonicPodcastController.class,
    SubsonicRadioController.class,
    SubsonicJukeboxController.class,
    SubsonicPlaylistController.class,
    SubsonicArtistInfoController.class,
    SubsonicUserController.class,
    SubsonicMediaController.class
})
public class SubsonicApiExceptionAdvice {

    private static final Logger LOG = LoggerFactory.getLogger(SubsonicApiExceptionAdvice.class);

    @Autowired
    private JAXBWriter jaxbWriter;

    @ExceptionHandler(SubsonicRESTController.APIException.class)
    public ResponseEntity<String> apiException(ServletWebRequest swr,
                                               HttpServletResponse response,
                                               SubsonicRESTController.APIException exception) {
        if (response.isCommitted()) {
            LOG.warn("APIException after response committed; cannot write error envelope", exception);
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        Entry<String, String> exceptionResponse = jaxbWriter.serializeForType(swr.getRequest(),
                jaxbWriter.createErrorResponse(exception));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exceptionResponse.getKey()))
                .body(exceptionResponse.getValue());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void handleMissingRequestParam(HttpServletRequest request,
                                          HttpServletResponse response,
                                          MissingServletRequestParameterException exception) {
        jaxbWriter.writeErrorResponse(request, response,
                SubsonicRESTController.ErrorCode.MISSING_PARAMETER,
                "Required param (" + exception.getParameterName() + ") is missing");
    }
}
