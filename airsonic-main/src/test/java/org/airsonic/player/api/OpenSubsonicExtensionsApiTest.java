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

 Copyright 2025 (C) Airsonic Authors
 */
package org.airsonic.player.api;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import jakarta.transaction.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
public class OpenSubsonicExtensionsApiTest extends AbstractRESTTest {

    private static final String CLIENT_NAME = "openSubsonicExtensionsApiTest";

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getOpenSubsonicExtensions", "/rest/getOpenSubsonicExtensions.view"})
    void getOpenSubsonicExtensions_returnsFormPostExtension(String endpoint) throws Exception {
        mvc.perform(get(endpoint)
            .param("v", AIRSONIC_API_VERSION)
            .param("c", CLIENT_NAME)
            .param("u", AIRSONIC_USER)
            .param("p", AIRSONIC_PASSWORD)
            .param("f", EXPECTED_FORMAT)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.openSubsonic").value(true))
            .andExpect(jsonPath("$.subsonic-response.openSubsonicExtensions.openSubsonicExtension[*].name").value(hasItem("formPost")))
            .andExpect(jsonPath("$.subsonic-response.openSubsonicExtensions.openSubsonicExtension[?(@.name=='formPost')].versions[0]").value(hasItem(1)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getOpenSubsonicExtensions", "/rest/getOpenSubsonicExtensions.view"})
    void getOpenSubsonicExtensions_accessibleWithoutAuth(String endpoint) throws Exception {
        mvc.perform(get(endpoint)
            .param("f", EXPECTED_FORMAT)
            .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.openSubsonicExtensions").exists());
    }
}
