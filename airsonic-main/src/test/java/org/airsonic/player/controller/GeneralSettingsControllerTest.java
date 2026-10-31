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
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link GeneralSettingsController} binds the show-version-on-login toggle (#246) and
 * the ReplayGain fallback (#252) in both directions — read into the form-backing command on GET,
 * written back to {@link SettingsService} on POST — and that an out-of-range or non-finite
 * fallback produces a field error and persists nothing.
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

        controller.doSubmitAction(command, new BeanPropertyBindingResult(command, "command"), new RedirectAttributesModelMap());

        verify(settingsService).setShowVersionOnLogin(false);
    }

    @Test
    void formBackingObjectReadsReplayGainFallback() {
        stubThemesAndLocales();
        when(settingsService.getReplayGainFallback()).thenReturn(3.5);

        Model model = new ExtendedModelMap();
        controller.formBackingObject(model);

        GeneralSettingsCommand command = (GeneralSettingsCommand) model.getAttribute("command");
        assertEquals(3.5, command.getReplayGainFallback());
    }

    @Test
    void formBackingObjectReadsReplayGainFallbackWhenUnset() {
        stubThemesAndLocales();
        when(settingsService.getReplayGainFallback()).thenReturn(null);

        Model model = new ExtendedModelMap();
        controller.formBackingObject(model);

        GeneralSettingsCommand command = (GeneralSettingsCommand) model.getAttribute("command");
        assertNull(command.getReplayGainFallback());
    }

    @Test
    void formBackingObjectKeepsFlashedCommand() {
        GeneralSettingsCommand flashed = new GeneralSettingsCommand();
        flashed.setReplayGainFallback(13.0);
        Model model = new ExtendedModelMap();
        model.addAttribute("command", flashed);

        controller.formBackingObject(model);

        assertEquals(flashed, model.getAttribute("command"));
    }

    @Test
    void doSubmitActionWritesReplayGainFallback() {
        stubThemesAndLocales();

        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setReplayGainFallback(3.5);

        controller.doSubmitAction(command, new BeanPropertyBindingResult(command, "command"), new RedirectAttributesModelMap());

        verify(settingsService).setReplayGainFallback(3.5);
    }

    @Test
    void doSubmitActionClearsReplayGainFallbackWhenEmpty() {
        stubThemesAndLocales();

        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setReplayGainFallback(null);

        controller.doSubmitAction(command, new BeanPropertyBindingResult(command, "command"), new RedirectAttributesModelMap());

        verify(settingsService).setReplayGainFallback(null);
    }

    @Test
    void doSubmitActionRejectsOutOfRangeReplayGainFallback() {
        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setReplayGainFallback(13.0);
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "command");

        String view = controller.doSubmitAction(command, bindingResult, new RedirectAttributesModelMap());

        assertTrue(bindingResult.hasFieldErrors("replayGainFallback"));
        assertEquals("redirect:generalSettings.view", view);
        verify(settingsService, never()).setReplayGainFallback(any());
        verify(settingsService, never()).save();
    }

    @Test
    void formBackingObjectReadsScanTimeouts() {
        stubThemesAndLocales();
        when(settingsService.getScanTimeoutSeconds()).thenReturn(600);
        when(settingsService.getFullScanTimeoutSeconds()).thenReturn(7200);

        Model model = new ExtendedModelMap();
        controller.formBackingObject(model);

        GeneralSettingsCommand command = (GeneralSettingsCommand) model.getAttribute("command");
        assertEquals(600, command.getScanTimeoutSeconds());
        assertEquals(7200, command.getFullScanTimeoutSeconds());
    }

    @Test
    void formBackingObjectReadsScanTimeoutsWhenUnset() {
        stubThemesAndLocales();
        when(settingsService.getScanTimeoutSeconds()).thenReturn(null);
        when(settingsService.getFullScanTimeoutSeconds()).thenReturn(null);

        Model model = new ExtendedModelMap();
        controller.formBackingObject(model);

        GeneralSettingsCommand command = (GeneralSettingsCommand) model.getAttribute("command");
        assertNull(command.getScanTimeoutSeconds());
        assertNull(command.getFullScanTimeoutSeconds());
    }

    @Test
    void doSubmitActionWritesScanTimeouts() {
        stubThemesAndLocales();

        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setScanTimeoutSeconds(600);
        command.setFullScanTimeoutSeconds(7200);

        controller.doSubmitAction(command, new BeanPropertyBindingResult(command, "command"), new RedirectAttributesModelMap());

        verify(settingsService).setScanTimeoutSeconds(600);
        verify(settingsService).setFullScanTimeoutSeconds(7200);
    }

    @Test
    void doSubmitActionClearsScanTimeoutsWhenEmpty() {
        stubThemesAndLocales();

        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setScanTimeoutSeconds(null);
        command.setFullScanTimeoutSeconds(null);

        controller.doSubmitAction(command, new BeanPropertyBindingResult(command, "command"), new RedirectAttributesModelMap());

        verify(settingsService).setScanTimeoutSeconds(null);
        verify(settingsService).setFullScanTimeoutSeconds(null);
    }

    @Test
    void doSubmitActionRejectsOutOfRangeScanTimeout() {
        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setScanTimeoutSeconds(30);
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "command");

        String view = controller.doSubmitAction(command, bindingResult, new RedirectAttributesModelMap());

        assertTrue(bindingResult.hasFieldErrors("scanTimeoutSeconds"));
        assertEquals("redirect:generalSettings.view", view);
        verify(settingsService, never()).setScanTimeoutSeconds(any());
        verify(settingsService, never()).setFullScanTimeoutSeconds(any());
        verify(settingsService, never()).save();
    }

    @Test
    void doSubmitActionRejectsOutOfRangeFullScanTimeout() {
        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setFullScanTimeoutSeconds(90000);
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "command");

        controller.doSubmitAction(command, bindingResult, new RedirectAttributesModelMap());

        assertTrue(bindingResult.hasFieldErrors("fullScanTimeoutSeconds"));
        assertEquals("generalsettings.fullscantimeout.invalid", bindingResult.getFieldError("fullScanTimeoutSeconds").getCode());
        verify(settingsService, never()).setFullScanTimeoutSeconds(any());
    }

    @Test
    void doSubmitActionRejectsFullScanTimeoutBelowScanTimeout() {
        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setScanTimeoutSeconds(3600);
        command.setFullScanTimeoutSeconds(600);
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "command");

        controller.doSubmitAction(command, bindingResult, new RedirectAttributesModelMap());

        assertTrue(bindingResult.hasFieldErrors("fullScanTimeoutSeconds"));
        assertEquals("generalsettings.fullscantimeout.lessthanscan", bindingResult.getFieldError("fullScanTimeoutSeconds").getCode());
        verify(settingsService, never()).setScanTimeoutSeconds(any());
        verify(settingsService, never()).setFullScanTimeoutSeconds(any());
        verify(settingsService, never()).save();
    }

    @Test
    void doSubmitActionRejectsNonFiniteReplayGainFallback() {
        GeneralSettingsCommand command = new GeneralSettingsCommand();
        command.setThemeIndex("0");
        command.setLocaleIndex("0");
        command.setReplayGainFallback(Double.NaN);
        BindingResult bindingResult = new BeanPropertyBindingResult(command, "command");

        controller.doSubmitAction(command, bindingResult, new RedirectAttributesModelMap());

        assertTrue(bindingResult.hasFieldErrors("replayGainFallback"));
        verify(settingsService, never()).setReplayGainFallback(any());
    }
}
