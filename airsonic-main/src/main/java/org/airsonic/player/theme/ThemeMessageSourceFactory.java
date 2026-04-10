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
import org.airsonic.player.service.SettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.stereotype.Component;

import jakarta.annotation.Nullable;

/**
 * Theme source implementation which uses two resource bundles: the
 * theme specific (e.g., barents.properties), and the default (default.properties).
 *
 * @author Sindre Mehus
 */
@Component
public class ThemeMessageSourceFactory {

    private static final String BASENAME_PREFIX = "org.airsonic.player.theme.";

    @Autowired
    private SettingsService settingsService;

    /**
     * Creates a message source for the specified theme ID.
     *
     * @param themeId The ID of the theme for which to create the message source.
     * @return A MessageSource for the specified theme.
     */
    public MessageSource createThemeMessageSource(String themeId) {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        String basename = BASENAME_PREFIX + themeId;
        messageSource.setBasename(basename);
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);

        Theme theme = findTheme(themeId);
        if (theme != null && theme.parent() != null) {
            MessageSource parentMessageSource = createThemeMessageSource(theme.parent());
            if (parentMessageSource != null) {
                messageSource.setParentMessageSource(parentMessageSource);
            }
        }
        return messageSource;
    }

    /**
     * Finds the theme by its ID.
     * @param themeId
     * @return The Theme object if found, otherwise null.
     */
    @Nullable
    private Theme findTheme(String themeId) {
        for (Theme theme : settingsService.getAvailableThemes()) {
            if (theme.id().equals(themeId)) {
                return theme;
            }
        }
        return null;
    }
}
