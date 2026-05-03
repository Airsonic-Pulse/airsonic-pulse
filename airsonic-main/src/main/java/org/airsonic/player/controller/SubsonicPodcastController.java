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
import org.airsonic.player.domain.PlayerTechnology;
import org.airsonic.player.domain.PodcastExportOPML;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PodcastManagementService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.podcast.PodcastDownloadClient;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.subsonic.restapi.NewestPodcasts;
import org.subsonic.restapi.PodcastStatus;
import org.subsonic.restapi.Podcasts;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.springframework.web.bind.ServletRequestUtils.getBooleanParameter;
import static org.springframework.web.bind.ServletRequestUtils.getIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredIntParameter;
import static org.springframework.web.bind.ServletRequestUtils.getRequiredStringParameter;

@Controller
@RequestMapping(value = {"/rest", "/ext"}, method = {RequestMethod.GET, RequestMethod.POST})
public class SubsonicPodcastController {

    @Autowired
    private JAXBWriter jaxbWriter;
    @Autowired
    private PodcastPersistenceService podcastPersistenceService;
    @Autowired
    private PodcastManagementService podcastManagementService;
    @Autowired
    private PodcastDownloadClient podcastDownloadClient;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private JaxbContentService jaxbContentService;
    @Autowired
    private MediaFileService mediaFileService;

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public void handleMissingRequestParam(HttpServletRequest request,
                                          HttpServletResponse response,
                                          MissingServletRequestParameterException exception) {
        error(request, response, SubsonicRESTController.ErrorCode.MISSING_PARAMETER,
                "Required param (" + exception.getParameterName() + ") is missing");
    }

    @RequestMapping({"/getPodcasts", "/getPodcasts.view"})
    public void getPodcasts(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);
        boolean includeEpisodes = getBooleanParameter(request, "includeEpisodes", true);
        Integer channelId = getIntParameter(request, "id");

        Podcasts result = new Podcasts();

        for (org.airsonic.player.domain.PodcastChannel channel : podcastPersistenceService.getAllChannels()) {
            if (channelId == null || channelId.equals(channel.getId())) {

                org.subsonic.restapi.PodcastChannel c = new org.subsonic.restapi.PodcastChannel();
                result.getChannel().add(c);

                c.setId(String.valueOf(channel.getId()));
                c.setUrl(channel.getUrl());
                c.setStatus(PodcastStatus.valueOf(channel.getStatus().name()));
                c.setTitle(channel.getTitle());
                c.setDescription(channel.getDescription());
                c.setCoverArt(CoverArtController.PODCAST_COVERART_PREFIX + channel.getId());
                c.setOriginalImageUrl(channel.getImageUrl());
                c.setErrorMessage(channel.getErrorMessage());

                if (includeEpisodes) {
                    List<org.airsonic.player.domain.PodcastEpisode> episodes = podcastPersistenceService.getEpisodes(channel.getId());
                    for (org.airsonic.player.domain.PodcastEpisode episode : episodes) {
                        c.getEpisode().add(createJaxbPodcastEpisode(player, username, episode));
                    }
                }
            }
        }
        Response res = createResponse();
        res.setPodcasts(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    @RequestMapping({"/getNewestPodcasts", "/getNewestPodcasts.view"})
    public void getNewestPodcasts(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        String username = securityService.getCurrentUsername(request);
        Player player = playerService.getPlayer(request, response, username);

        int count = getIntParameter(request, "count", 20);
        NewestPodcasts result = new NewestPodcasts();

        for (org.airsonic.player.domain.PodcastEpisode episode : podcastPersistenceService.getNewestEpisodes(count)) {
            result.getEpisode().add(createJaxbPodcastEpisode(player, username, episode));
        }

        Response res = createResponse();
        res.setNewestPodcasts(result);
        jaxbWriter.writeResponse(request, response, res);
    }

    private org.subsonic.restapi.PodcastEpisode createJaxbPodcastEpisode(Player player, String username, org.airsonic.player.domain.PodcastEpisode episode) {
        org.subsonic.restapi.PodcastEpisode e = new org.subsonic.restapi.PodcastEpisode();

        if (episode.getMediaFile() != null) {
            MediaFile mediaFile = episode.getMediaFile();
            e = jaxbContentService.createJaxbChild(new org.subsonic.restapi.PodcastEpisode(), player, mediaFile, username);
            e.setStreamId(String.valueOf(mediaFile.getId()));
        }

        e.setId(String.valueOf(episode.getId()));  // Overwrites the previous "id" attribute.
        e.setChannelId(String.valueOf(episode.getChannel().getId()));
        e.setStatus(PodcastStatus.valueOf(episode.getStatus().name()));
        e.setTitle(episode.getTitle());
        e.setDescription(episode.getDescription());
        e.setPublishDate(jaxbWriter.convertDate(episode.getPublishDate()));
        return e;
    }

    @RequestMapping({"/refreshPodcasts", "/refreshPodcasts.view"})
    public void refreshPodcasts(HttpServletRequest request, HttpServletResponse response) {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }
        podcastManagementService.refreshAllChannels(true);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/createPodcastChannel", "/createPodcastChannel.view"})
    public void createPodcastChannel(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }

        String url = getRequiredStringParameter(request, "url");
        podcastManagementService.createChannel(url);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/deletePodcastChannel", "/deletePodcastChannel.view"})
    public void deletePodcastChannel(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }

        int id = getRequiredIntParameter(request, "id");
        podcastManagementService.deleteChannel(id);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/deletePodcastEpisode", "/deletePodcastEpisode.view"})
    public void deletePodcastEpisode(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }

        int id = getRequiredIntParameter(request, "id");
        podcastPersistenceService.deleteEpisode(id, true);
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/downloadPodcastEpisode", "/downloadPodcastEpisode.view"})
    public void downloadPodcastEpisode(HttpServletRequest request, HttpServletResponse response) throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED, user.getUsername() + " is not authorized to administrate podcasts.");
            return;
        }

        int id = getRequiredIntParameter(request, "id");
        org.airsonic.player.domain.PodcastEpisode episode = podcastPersistenceService.getEpisode(id, true);
        if (episode == null) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_FOUND, "Podcast episode " + id + " not found.");
            return;
        }

        podcastDownloadClient.downloadEpisode(episode.getId());
        writeEmptyResponse(request, response);
    }

    @RequestMapping({"/exportPodcasts/opml", "/exportPodcasts/opml.view"})
    public ResponseEntity<PodcastExportOPML> exportPodcastOPML(HttpServletRequest request, HttpServletResponse response)
            throws Exception {
        request = wrapRequest(request);
        org.airsonic.player.domain.User user = securityService.getCurrentUser(request);
        if (!user.isPodcastRole()) {
            error(request, response, SubsonicRESTController.ErrorCode.NOT_AUTHORIZED,
                    user.getUsername() + " is not authorized to administrate podcasts.");
            return null;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.builder("attachment").filename("airsonic.opml", StandardCharsets.UTF_8).build());
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_XML);

        return ResponseEntity.ok().headers(headers).body(podcastPersistenceService.exportAllChannels());
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
