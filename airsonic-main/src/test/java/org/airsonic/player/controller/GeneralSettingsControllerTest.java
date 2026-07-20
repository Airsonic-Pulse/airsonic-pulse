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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.controller;

import org.airsonic.player.command.GeneralSettingsCommand;
import org.airsonic.player.domain.Theme;
import org.airsonic.player.service.PlaylistFileService;
import org.airsonic.player.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link GeneralSettingsController} binds the show-version-on-login toggle (#246) in
 * both directions — read into the form-backing command on GET, written back to
 * {@link SettingsService} on POST.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GeneralSettingsControllerTest {

    @Mock
    private SettingsService settingsService;
    @Mock
    private PlaylistFileService playlistFileService;

    @InjectMocks
    private GeneralSettingsController controller;

    private void stubThemesAndLocales() {
        when(settingsService.getAvailableThemes()).thenReturn(new Theme[] {new Theme("default", "Default")});
        when(settingsService.getThemeId()).thenReturn("default");
        when(settingsService.getAvailableLocales()).thenReturn(new Locale[] {Locale.ENGLISH});
        when(settingsService.getLocale()).thenReturn(Locale.ENGLISH);
    }

    @Test
    void formBackingObjectReadsShowVersionOnLogin() {
        stubThemesAndLocales();
        when(settingsService.isShowVersionOnLogin()).thenReturn(true);

        Model model = new ExtendedModelMap();
        controller.formBackingObject(model);

        GeneralSettingsCommand command = (GeneralSettingsCommand) model.getAttribute("command");
        assertTrue(command.isShowVersionOnLogin());
    }

    @Test
    void formBackingObjectReadsShowVersionOnLoginWhenOff() {
        stubThemesAndLocales();
        when(settingsService.isShowVersionOnLogin()).thenReturn(false);

        Model model = new ExtendedModelMap();
        controller.formBackingObject(model);

        GeneralSettingsCommand command = (GeneralSettingsCommand) model.getAttribute("command");
        assertFalse(command.isShowVersionOnLogin());
    }

    @Test
    void doSubmitActionWritesShowVersionOnLogin() {
        stubThemesAndLocales();

        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setShowVersionOnLogin(false);

        controller.doSubmitAction(command, new RedirectAttributesModelMap());

        verify(settingsService).setShowVersionOnLogin(false);
    }
}
