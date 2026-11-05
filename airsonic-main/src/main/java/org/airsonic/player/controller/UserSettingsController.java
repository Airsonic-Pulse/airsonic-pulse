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

import org.airsonic.player.command.UserSettingsCommand;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.TranscodingService;
import org.airsonic.player.service.UserService;
import org.airsonic.player.util.Util;
import org.airsonic.player.validator.UserSettingsValidator;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.ServletRequestUtils;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * Controller for the page used to administrate users.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/userSettings", "/userSettings.view"})
public class UserSettingsController {

    @Autowired
    private SecurityService securityService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private TranscodingService transcodingService;
    @Autowired
    private AirsonicHomeConfig homeConfig;
    @Autowired
    private PersonalSettingsService personalSettingsService;
    @Autowired
    private UserService userService;

    private static final Logger LOG = LoggerFactory.getLogger(UserSettingsController.class);

    @InitBinder("command")
    protected void initBinder(WebDataBinder binder, HttpServletRequest request) {
        binder.addValidators(new UserSettingsValidator(securityService, settingsService, request));
    }

    @GetMapping
    protected String displayForm(HttpServletRequest request, Model model) throws Exception {
        UserSettingsCommand command;
        if (!model.containsAttribute("command")) {
            command = new UserSettingsCommand();

            User user = getUser(request);
            if (user != null) {
                command.setUser(user);
                command.setEmail(user.getEmail());
                UserSettings userSettings = personalSettingsService.getUserSettings(user.getUsername());
                command.setTranscodeSchemeName(userSettings.getTranscodeScheme().name());
                command.setAllowedMusicFolderIds(Util.toIntArray(getAllowedMusicFolderIds(user)));
                command.setCurrentUser(securityService.getCurrentUser(request).getUsername().equals(user.getUsername()));
            } else {
                command.setNewUser(true);
                command.setStreamRole(true);
                command.setSettingsRole(true);
            }

        } else {
            command = (UserSettingsCommand) model.asMap().get("command");
        }
        command.setUsers(userService.getAllUsers());
        command.setTranscodingSupported(transcodingService.isDownsamplingSupported(null));
        command.setTranscodeDirectory(homeConfig.getTranscodeDirectory().toString());
        command.setTranscodeSchemes(TranscodeScheme.values());
        command.setLdapEnabled(settingsService.isLdapEnabled());
        command.setAllMusicFolders(mediaFolderService.getAllMusicFolders());
        model.addAttribute("command", command);
        return "userSettings";
    }

    private User getUser(HttpServletRequest request) throws ServletRequestBindingException {
        Integer userIndex = ServletRequestUtils.getIntParameter(request, "userIndex");
        if (userIndex != null) {
            List<User> allUsers = userService.getAllUsers();
            if (userIndex >= 0 && userIndex < allUsers.size()) {
                return allUsers.get(userIndex);
            }
        }
        return null;
    }

    private List<Integer> getAllowedMusicFolderIds(User user) {
        List<Integer> result = new ArrayList<Integer>();
        List<MusicFolder> allowedMusicFolders = user == null
                ? mediaFolderService.getAllMusicFolders()
                : mediaFolderService.getMusicFoldersForUser(user.getUsername());

        for (MusicFolder musicFolder : allowedMusicFolders) {
            result.add(musicFolder.getId());
        }
        return result;
    }

    @PostMapping
    protected String doSubmitAction(
            @ModelAttribute("command") @Validated UserSettingsCommand command,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes,
            HttpServletRequest request) {

        if (!bindingResult.hasErrors()) {
            if (command.isDeleteUser()) {
                deleteUser(command, request);
            } else if (command.isNewUser()) {
                createUser(command);
            } else {
                updateUser(command);
            }
            redirectAttributes.addFlashAttribute("settings_reload", true);
            redirectAttributes.addFlashAttribute("settings_toast", true);
        } else {
            redirectAttributes.addFlashAttribute("command", command);
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.command", bindingResult);
            redirectAttributes.addFlashAttribute("userIndex", getUserIndex(command));
        }

        return "redirect:userSettings.view";
    }

    private Integer getUserIndex(UserSettingsCommand command) {
        List<User> allUsers = userService.getAllUsers();
        for (int i = 0; i < allUsers.size(); i++) {
            if (StringUtils.equalsIgnoreCase(allUsers.get(i).getUsername(), command.getUsername())) {
                return i;
            }
        }
        return null;
    }

    private void deleteUser(UserSettingsCommand command, HttpServletRequest request) {
        String currentUsername = securityService.getCurrentUsername(request);
        securityService.deleteUser(command.getUsername(), currentUsername);
    }

    public void createUser(UserSettingsCommand command) {
        User user = new User(command.getUsername(), StringUtils.trimToNull(command.getEmail()));
        user.setLdapAuthenticated(command.isLdapAuthenticated());
        securityService.createUser(user, command.getPassword(), "Created for new user");
        updateUser(command);
    }

    public void updateUser(UserSettingsCommand command) {
        User user = securityService.updateUserByUserSettingsCommand(command);

        if (user == null) {
            LOG.warn("User {} not found", command.getUsername());
            return;
        }

        Set<Integer> allowedMusicFolderIds = new HashSet<>();
        if (command.isPodcastRole()) {
            allowedMusicFolderIds.addAll(mediaFolderService.getAllMusicFolders().stream()
                    .filter(mf -> mf.getType() == Type.PODCAST).map(mf -> mf.getId()).collect(toSet()));
        }

        personalSettingsService.updateTranscodeScheme(command.getUsername(), TranscodeScheme.valueOf(command.getTranscodeSchemeName()));

        // NOTE: This can happen if none of the configured media directories exist or if none are enabled.
        //       Primitive arrays are still behind a pointer technically, and that pointer is null if not initialized.
        if (command.getAllowedMusicFolderIds() != null) {
            Arrays.stream(command.getAllowedMusicFolderIds()).forEach(allowedMusicFolderIds::add);
        }
        mediaFolderService.setMusicFoldersForUser(command.getUsername(), allowedMusicFolderIds);
    }

}
