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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.service.PodcastManagementService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.podcast.PodcastDownloadClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;

import java.util.List;

/**
 * Controller for the "Podcast receiver" page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/podcastReceiverAdmin", "/podcastReceiverAdmin.view"})
public class PodcastReceiverAdminController {

    @Autowired
    private PodcastPersistenceService podcastPersistenceService;

    @Autowired
    private PodcastManagementService podcastManagementService;

    @Autowired
    private PodcastDownloadClient podcastDownloadClient;

    @RequestMapping(method = { RequestMethod.POST, RequestMethod.GET })
    protected ModelAndView handleRequestInternal(
            @RequestParam(required = false, name = "channelId") Integer channelId,
            @RequestParam(required = false, name = "add") String add,
            @RequestParam(required = false, name = "downloadEpisode") List<Integer> downloadEpisode,
            @RequestParam(required = false, name = "deleteChannel") String deleteChannel,
            @RequestParam(required = false, name = "deleteEpisode") List<Integer> deleteEpisode,
            @RequestParam(required = false, name = "refresh") String refresh) throws Exception {

        if (add != null) {
            String url = StringUtils.trim(add);
            podcastManagementService.createChannel(url);
            return new ModelAndView(new RedirectView("podcastChannels.view"));
        }
        if (downloadEpisode != null && channelId != null) {
            downloadEpisode.parallelStream()
                    .map(e -> podcastPersistenceService.getEpisode(e, false))
                    .filter(episode -> episode != null && episode.getUrl() != null
                            && (episode.getStatus() == PodcastStatus.NEW || episode.getStatus() == PodcastStatus.ERROR
                                    || episode.getStatus() == PodcastStatus.SKIPPED))
                    .map(PodcastEpisode::getId)
                    .forEach(podcastDownloadClient::downloadEpisode);
            return new ModelAndView(new RedirectView("podcastChannel.view?id=" + channelId));
        }
        if (deleteChannel != null && channelId != null) {
            podcastManagementService.deleteChannel(channelId);
            return new ModelAndView(new RedirectView("podcastChannels.view"));
        }
        if (deleteEpisode != null) {
            deleteEpisode.forEach(episodeId -> podcastPersistenceService.deleteEpisode(episodeId, true));
            return new ModelAndView(new RedirectView("podcastChannel.view?id=" + channelId));
        }
        if (refresh != null) {
            if (channelId != null) {
                podcastManagementService.refreshChannel(channelId, true);
                return new ModelAndView(new RedirectView("podcastChannel.view?id=" + channelId));
            } else {
                podcastManagementService.refreshAllChannels(true);
                return new ModelAndView(new RedirectView("podcastChannels.view"));
            }
        }

        return new ModelAndView(new RedirectView("podcastChannels.view"));
    }
}
