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

import org.airsonic.player.command.PersonalSettingsCommand;
import org.airsonic.player.domain.*;
import org.airsonic.player.domain.UserCredential.App;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Locale;
import java.util.Map;

/**
 * Controller for the page used to administrate per-user settings.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/personalSettings", "/personalSettings.view"})
public class PersonalSettingsController {

    @Autowired
    private SettingsService settingsService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private PersonalSettingsService personalSettingsService;

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request,Model model) {
        PersonalSettingsCommand command = new PersonalSettingsCommand();

        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = personalSettingsService.getUserSettings(user.getUsername());

        command.setUser(user);
        command.setLocaleIndex("-1");
        command.setThemeIndex("-1");
        command.setAlbumLists(AlbumListType.values());
        command.setAlbumListId(userSettings.getDefaultAlbumList().getId());
        command.setAvatars(personalSettingsService.getSystemAvatars());
        command.setCustomAvatar(personalSettingsService.getCustomAvatar(user.getUsername()));
        command.setAvatarId(getAvatarId(userSettings));
        command.setPartyModeEnabled(userSettings.getPartyModeEnabled());
        command.setQueueFollowingSongs(userSettings.getQueueFollowingSongs());
        command.setShowNowPlayingEnabled(userSettings.getShowNowPlayingEnabled());
        command.setShowArtistInfoEnabled(userSettings.getShowArtistInfoEnabled());
        command.setNowPlayingAllowed(userSettings.getNowPlayingAllowed());
        command.setMainVisibility(userSettings.getMainVisibility());
        command.setPlaylistVisibility(userSettings.getPlaylistVisibility());
        command.setPlayqueueVisibility(userSettings.getPlayqueueVisibility());
        command.setFinalVersionNotificationEnabled(userSettings.getFinalVersionNotificationEnabled());
        command.setBetaVersionNotificationEnabled(userSettings.getBetaVersionNotificationEnabled());
        command.setSongNotificationEnabled(userSettings.getSongNotificationEnabled());
        command.setAutoHidePlayQueue(userSettings.getAutoHidePlayQueue());
        command.setKeyboardShortcutsEnabled(userSettings.getKeyboardShortcutsEnabled());
        command.setLastFmEnabled(userSettings.getLastFmEnabled());
        command.setListenBrainzEnabled(userSettings.getListenBrainzEnabled());
        command.setListenBrainzUrl(userSettings.getListenBrainzUrl());
        command.setPodcastIndexEnabled(userSettings.getPodcastIndexEnabled());
        command.setPodcastIndexUrl(userSettings.getPodcastIndexUrl());
        command.setPaginationSizeFiles(userSettings.getPaginationSizeFiles());
        command.setPaginationSizeFolders(userSettings.getPaginationSizeFolders());
        command.setPaginationSizePlaylist(userSettings.getPaginationSizePlaylist());
        command.setPaginationSizePlayqueue(userSettings.getPaginationSizePlayqueue());
        command.setPaginationSizeBookmarks(userSettings.getPaginationSizeBookmarks());
        command.setAutoBookmark(userSettings.getAutoBookmark());
        command.setAudioBookmarkFrequency(userSettings.getAudioBookmarkFrequency());
        command.setVideoBookmarkFrequency(userSettings.getVideoBookmarkFrequency());
        command.setSearchCount(userSettings.getSearchCount());

        Locale currentLocale = userSettings.getLocale();
        Locale[] locales = settingsService.getAvailableLocales();
        String[] localeStrings = new String[locales.length];
        for (int i = 0; i < locales.length; i++) {
            localeStrings[i] = locales[i].getDisplayName(locales[i]);
            if (locales[i].equals(currentLocale)) {
                command.setLocaleIndex(String.valueOf(i));
            }
        }
        command.setLocales(localeStrings);

        String currentThemeId = userSettings.getThemeId();
        Theme[] themes = settingsService.getAvailableThemes();
        command.setThemes(themes);
        for (int i = 0; i < themes.length; i++) {
            if (themes[i].id().equals(currentThemeId)) {
                command.setThemeIndex(String.valueOf(i));
                break;
            }
        }

        model.addAttribute("command", command);

        Map<App, UserCredential> thirdPartyCreds = securityService.getDecodableCredsForApps(user.getUsername(), App.LASTFM, App.LISTENBRAINZ, App.PODCASTINDEX);

        model.addAttribute("lastfmCredsAbsent", thirdPartyCreds.get(App.LASTFM) == null);
        model.addAttribute("listenBrainzCredsAbsent", thirdPartyCreds.get(App.LISTENBRAINZ) == null);
        model.addAttribute("podcastIndexCredsAbsent", thirdPartyCreds.get(App.PODCASTINDEX) == null);
    }

    @GetMapping
    protected String displayForm() {
        return "personalSettings";
    }

    @PostMapping
    protected String doSubmitAction(@ModelAttribute("command") PersonalSettingsCommand command, RedirectAttributes redirectAttributes) {

        int localeIndex = Integer.parseInt(command.getLocaleIndex());
        Locale locale = null;
        if (localeIndex != -1) {
            locale = settingsService.getAvailableLocales()[localeIndex];
        }

        int themeIndex = Integer.parseInt(command.getThemeIndex());
        String themeId = null;
        if (themeIndex != -1) {
            themeId = settingsService.getAvailableThemes()[themeIndex].id();
        }

        String username = command.getUser().getUsername();
        personalSettingsService.updateByCommand(username, locale, themeId, command);

        redirectAttributes.addFlashAttribute("settings_reload", true);
        redirectAttributes.addFlashAttribute("settings_toast", true);

        return "redirect:personalSettings.view";
    }

    private int getAvatarId(UserSettings userSettings) {
        AvatarScheme avatarScheme = userSettings.getAvatarScheme();
        return avatarScheme == AvatarScheme.SYSTEM ? userSettings.getSystemAvatarId() : avatarScheme.getCode();
    }



}
