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

import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.VersionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link LoginController} exposes the display version to the pre-authentication login
 * view only when the show-version-on-login toggle is on (#246). The display version already embeds
 * the build timestamp, so no separate build date is exposed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LoginControllerTest {

    @Mock
    private SecurityService securityService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private VersionService versionService;

    @InjectMocks
    private LoginController controller;

    @SuppressWarnings("unchecked")
    private Map<String, Object> loginModel() {
        ModelAndView mav = controller.login(new MockHttpServletRequest(), new MockHttpServletResponse());
        return (Map<String, Object>) mav.getModel().get("model");
    }

    @Test
    void modelExposesVersionWhenToggleOn() {
        when(settingsService.isShowVersionOnLogin()).thenReturn(true);
        when(versionService.getDisplayVersion()).thenReturn("13.3.0-SNAPSHOT.20260718141619");

        Map<String, Object> model = loginModel();

        assertEquals(true, model.get("showVersion"));
        // the display version already embeds the build timestamp — no separate build date is exposed
        assertEquals("13.3.0-SNAPSHOT.20260718141619", model.get("displayVersion"));
    }

    @Test
    void modelOmitsVersionWhenToggleOff() {
        when(settingsService.isShowVersionOnLogin()).thenReturn(false);

        Map<String, Object> model = loginModel();

        assertEquals(false, model.get("showVersion"));
        assertNull(model.get("displayVersion"));
        // the version is not merely hidden by the template — it is never read from the service
        verify(versionService, never()).getDisplayVersion();
    }

    @Test
    void showVersionDefaultsToWhateverSettingsServiceReports() {
        // SettingsService.isShowVersionOnLogin() applies the default (true when unset); the
        // controller must pass it through untouched rather than defaulting on its own.
        when(settingsService.isShowVersionOnLogin()).thenReturn(true);
        when(versionService.getDisplayVersion()).thenReturn("13.3.0");

        assertTrue((Boolean) loginModel().get("showVersion"));

        when(settingsService.isShowVersionOnLogin()).thenReturn(false);
        assertFalse((Boolean) loginModel().get("showVersion"));
    }
}
