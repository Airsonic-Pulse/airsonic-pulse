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

 Copyright 2025 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.theme;

import org.airsonic.player.domain.Theme;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashSet;
import java.util.Set;

/**
 * Theme resolver implementation which returns the theme selected in the settings.
 *
 * @author Sindre Mehus
 */
@Service
public class ThemeService {

    @Autowired
    private SecurityService securityService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private PersonalSettingsService personalSettingsService;
    private Set<String> themeIds;

    /**
    * Resolve the current theme name via the given request.
    *
    * @param request Request to be used for resolution
    * @return The current theme name
    */
    @Transactional
    public String getCurrentTheme(HttpServletRequest request) {
        String themeId = (String) request.getAttribute("airsonic.theme");
        if (themeId != null) {
            return themeId;
        }

        // Optimization: Cache theme in the request.
        themeId = doResolveThemeName(request);
        request.setAttribute("airsonic.theme", themeId);

        return themeId;
    }

    private String doResolveThemeName(HttpServletRequest request) {
        String themeId = null;

        // Look for user-specific theme.
        String username = securityService.getCurrentUsername(request);
        if (username != null) {
            UserSettings userSettings = personalSettingsService.getUserSettings(username);
            if (userSettings != null) {
                themeId = userSettings.getThemeId();
            }
        }

        if (themeId != null && themeExists(themeId)) {
            return themeId;
        }

        // Return system theme.
        themeId = settingsService.getThemeId();
        return themeExists(themeId) ? themeId : "default";
    }

    /**
     * Returns whether the theme with the given ID exists.
     * @param themeId The theme ID.
     * @return Whether the theme with the given ID exists.
     */
    private synchronized boolean themeExists(String themeId) {
        // Lazily create set of theme IDs.
        if (themeIds == null) {
            themeIds = new HashSet<String>();
            Theme[] themes = settingsService.getAvailableThemes();
            for (Theme theme : themes) {
                themeIds.add(theme.id());
            }
        }

        return themeIds.contains(themeId);
    }
}
