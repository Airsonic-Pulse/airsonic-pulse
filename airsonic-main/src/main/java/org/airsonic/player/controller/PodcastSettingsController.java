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

import org.airsonic.player.command.PodcastSettingsCommand;
import org.airsonic.player.command.PodcastSettingsCommand.PodcastRule;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastChannelRule;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PodcastManagementService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

/**
 * Controller for the page used to administrate the Podcast receiver.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/podcastSettings", "/podcastSettings.view"})
public class PodcastSettingsController {

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private PodcastPersistenceService podcastPersistenceService;

    @Autowired
    private PodcastManagementService podcastManagementService;

    @Autowired
    private MediaFolderService mediaFolderService;

    @GetMapping
    protected String formBackingObject(Model model) {
        PodcastSettingsCommand command = new PodcastSettingsCommand();

        command.setFolderId(mediaFolderService.getAllMusicFolders().stream()
                .filter(f -> f.getType() == Type.PODCAST)
                .findFirst()
                .map(f -> f.getId())
                .orElse(null));

        command.setFolders(mediaFolderService.getAllMusicFolders(true, true)
                .stream()
                .filter(f -> f.getType() == Type.PODCAST)
                .collect(toMap(f -> f.getId(), f -> f.getId() + " - " + f.getName())));

        List<PodcastChannel> channels = podcastPersistenceService.getAllChannels();
        List<PodcastChannelRule> rules = podcastPersistenceService.getAllChannelRules();
        // A synthetic "DEFAULT" rule (channel id -1) carrying the global podcast settings is
        // appended after the per-channel rules. Built in a single immutable pass via Stream.concat
        // rather than a build-then-add, since Stream.toList() is unmodifiable.
        PodcastRule defaultRule = new PodcastRule(new PodcastChannelRule(-1,
                settingsService.getPodcastUpdateInterval(),
                settingsService.getPodcastEpisodeRetentionCount(),
                settingsService.getPodcastEpisodeDownloadCount()), "DEFAULT");
        command.setRules(Stream.concat(
                rules.stream()
                    .map(cr -> new PodcastRule(
                            cr,
                            channels.stream()
                                .filter(c -> c.getId().equals(cr.getId()))
                                .findFirst()
                                .map(PodcastChannel::getTitle).orElse(null))),
                Stream.of(defaultRule))
                .toList());

        command.setNewRule(new PodcastRule());
        command.setNoRuleChannels(channels.parallelStream()
                .filter(c -> rules.stream().noneMatch(r -> r.getId().equals(c.getId())))
                .map(c -> new PodcastRule(c.getId(), c.getTitle()))
                .toList());

        model.addAttribute("command", command);
        return "podcastSettings";
    }

    @PostMapping
    protected String doSubmitAction(@ModelAttribute PodcastSettingsCommand command, RedirectAttributes redirectAttributes) {
        PodcastRule defaultRule = command.getRules().stream().filter(r -> r.getId().equals(-1)).findFirst().get();
        settingsService.setPodcastUpdateInterval(defaultRule.getInterval());
        settingsService.setPodcastEpisodeRetentionCount(defaultRule.getEpisodeRetentionCount());
        settingsService.setPodcastEpisodeDownloadCount(defaultRule.getEpisodeDownloadCount());
        settingsService.save();
        podcastManagementService.scheduleDefault();

        boolean success = mediaFolderService.enablePodcastFolder(command.getFolderId());

        command.getRules().stream().filter(r -> !r.getId().equals(-1)).forEach(r -> {
            if (r.getDelete()) {
                podcastManagementService.deleteChannelRule(r.getId());
            } else {
                podcastManagementService.createOrUpdateChannelRuleByCommand(r);
            }
        });



        Optional.ofNullable(command.getNewRule())
                .ifPresent(podcastManagementService::createOrUpdateChannelRuleByCommand);

        redirectAttributes.addFlashAttribute("settings_toast", success);
        return "redirect:podcastSettings.view";
    }
}
