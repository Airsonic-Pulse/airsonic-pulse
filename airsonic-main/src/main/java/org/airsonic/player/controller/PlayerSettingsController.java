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

 Copyright 2023 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.command.PlayerSettingsCommand;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.TranscodingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
 * Controller for the player settings page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/playerSettings", "/playerSettings.view"})
public class PlayerSettingsController {

    @Autowired
    private PlayerService playerService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private PlayQueueService playQueueService;
    @Autowired
    private AirsonicHomeConfig homeConfig;

    @GetMapping
    protected String displayForm() {
        return "playerSettings";
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model) throws Exception {

        handleRequestParameters(request);
        User user = securityService.getCurrentUser(request);
        List<Player> players = getPlayers(user);

        PlayerSettingsCommand command = new PlayerSettingsCommand();
        Player player = null;
        Integer playerId = ServletRequestUtils.getIntParameter(request, "id");
        if (playerId != null) {
            player = playerService.getPlayerById(playerId);
        } else if (!players.isEmpty()) {
            player = players.getFirst();
        }

        if (player != null) {
            command.setPlayerId(player.getId());
            command.setName(player.getName());
            command.setDescription(player.toString());
            command.setType(player.getType());
            command.setLastSeen(player.getLastSeen());
            command.setDynamicIp(player.getDynamicIp());
            command.setAutoControlEnabled(player.getAutoControlEnabled());
            command.setM3uBomEnabled(player.getM3uBomEnabled());
            command.setTranscodeSchemeName(player.getTranscodeScheme().name());
            command.setTechnologyName(player.getTechnology().name());
            command.setAllTranscodings(transcodingService.getAllTranscodings());
            List<Transcoding> activeTranscodings = transcodingService.getTranscodingsForPlayer(player);
            command.setActiveTranscodingIds(activeTranscodings.stream().map(Transcoding::getId).toList());
        }

        command.setTranscodingSupported(transcodingService.isDownsamplingSupported(null));
        command.setTranscodeDirectory(homeConfig.getTranscodeDirectory().toString());
        command.setTranscodeSchemes(TranscodeScheme.values());
        command.setTechnologies(PlayerTechnology.values());
        command.setPlayers(players.toArray(new Player[players.size()]));
        command.setAdmin(user.isAdminRole());

        model.addAttribute("command",command);
    }

    @PostMapping
    protected String doSubmitAction(@ModelAttribute("command") PlayerSettingsCommand command, RedirectAttributes redirectAttributes) {
        Player original = playerService.getPlayerById(command.getPlayerId());
        Player updated = playerService.updateByCommand(command);
        if (original != null && updated != null) {
            boolean stopped = false;
            if (original.getTranscodeScheme() != updated.getTranscodeScheme()) {
                if (!stopped) {
                    playQueueService.stop(original);
                    stopped = true;
                }
            }
            if (original.getTechnology() != updated.getTechnology()) {
                if (!stopped) {
                    playQueueService.stop(original);
                    stopped = true;
                }
            }
            redirectAttributes.addFlashAttribute("settings_toast", true);
            return "redirect:playerSettings.view?id=" + command.getPlayerId();
        } else {
            return "redirect:notFound";
        }
    }

    private List<Player> getPlayers(User user) {
        String username = user.getUsername();
        if (user.isAdminRole()) {
            return playerService.getAllPlayers();
        } else {
            return playerService.getPlayersForUser(username);
        }
    }

    private void handleRequestParameters(HttpServletRequest request) throws Exception {
        if (request.getParameter("delete") != null) {
            playerService.removePlayerById(ServletRequestUtils.getIntParameter(request, "delete"));
        } else if (request.getParameter("clone") != null) {
            playerService.clonePlayer(ServletRequestUtils.getIntParameter(request, "clone"));
        }
    }

}
