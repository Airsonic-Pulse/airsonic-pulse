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
import org.airsonic.player.domain.User;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.SecurityService;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.context.request.ServletWebRequest;
import org.subsonic.restapi.Line;
import org.subsonic.restapi.Lyrics;
import org.subsonic.restapi.LyricsList;
import org.subsonic.restapi.Response;
import org.subsonic.restapi.StructuredLyrics;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

@Controller
// Maps /rest only, unlike the other Subsonic controllers' {"/rest", "/ext"}: the wrapped
// binary controllers self-register their /ext/* routes (StreamController maps /ext/stream,
// HLSController /ext/hls/**), and a second /ext/stream registration here made Spring throw
// "Ambiguous handler methods mapped" on every JWT stream request — external players, UPnP,
// Sonos, shares (#325). No server-issued JWT URL targets /ext/<subsonic-name>, so nothing
// legitimate resolves through an /ext prefix on this controller.
@RequestMapping(value = "/rest", method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicMediaController extends AbstractSubsonicController {

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

    @RequestMapping({"/getLyricsBySongId", "/getLyricsBySongId.view"})
    public void getLyricsBySongId(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);

        int id = ServletRequestUtils.getRequiredIntParameter(request, "id");

        MediaFile mediaFile = mediaFileService.getMediaFile(id);
        if (mediaFile == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Media file " + id + " not found.");
            return;
        }

        LyricsList result = new LyricsList();

        org.airsonic.player.domain.Lyrics lyrics = lyricsService.getLyricsFromMediaFile(mediaFile);
        StructuredLyrics structured = buildStructuredLyrics(mediaFile.getArtist(), mediaFile.getTitle(), lyrics);
        if (structured != null) {
            result.getStructuredLyrics().add(structured);
        }

        Response res = createResponse();
        res.setLyricsList(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    /**
     * Builds the OpenSubsonic {@link StructuredLyrics} element for a resolved {@code lyrics} row,
     * or {@code null} when there are no lyrics to emit. Synced (LRC) lyrics (#140) emit one
     * {@link Line} per stored structured line with its {@code start} (ms) and {@code synced=true};
     * otherwise the flat text blob is split into unsynced lines ({@code synced=false}), preserving
     * the original #131 behavior for the embedded-tag and legacy-cache tiers.
     */
    static StructuredLyrics buildStructuredLyrics(String displayArtist, String displayTitle,
            org.airsonic.player.domain.Lyrics lyrics) {
        if (lyrics == null || lyrics.getLyrics() == null || lyrics.getLyrics().isBlank()) {
            return null;
        }
        StructuredLyrics structured = new StructuredLyrics();
        structured.setDisplayArtist(displayArtist);
        structured.setDisplayTitle(displayTitle);
        structured.setLang("xxx");

        if (lyrics.isSynced() && !lyrics.getLines().isEmpty()) {
            // Synced LRC lyrics (#140): emit structured lines with per-line start (ms).
            structured.setSynced(true);
            for (org.airsonic.player.domain.StructuredLyricsLine sourceLine : lyrics.getLines()) {
                Line line = new Line();
                line.setValue(sourceLine.getText());
                line.setStart(sourceLine.getStartMs());
                structured.getLine().add(line);
            }
        } else {
            // Unsynced fallback (pre-#140 cached sidecar, or embedded tag): split the flat blob.
            structured.setSynced(false);
            for (String lineText : lyrics.getLyrics().split("\\R")) {
                Line line = new Line();
                line.setValue(lineText);
                structured.getLine().add(line);
            }
        }
        return structured;
    }

    @RequestMapping({"/getCaptions", "/getCaptions.view"})
    public void getCaptions(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        error(request, response, SubsonicRESTController.ErrorCode.GENERIC, "getCaptions is not yet implemented");
    }

}
