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

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.ApiKey;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.ApiKeyService;
import org.airsonic.player.service.ApiKeyService.GeneratedApiKey;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@ExtendWith(MockitoExtension.class)
@WebMvcTest
@ContextConfiguration(classes = {ApiKeySettingsController.class}, initializers = ConfigDataApplicationContextInitializer.class)
@EnableConfigurationProperties(AirsonicHomeConfig.class)
class ApiKeySettingsControllerTest {

    private static final String ALICE = "alice";
    private static final String BOB = "bob";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApiKeyService apiKeyService;

    @MockitoBean
    private SecurityService securityService;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    static void setUpHome() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @AfterAll
    static void tearDownHome() {
        System.clearProperty("airsonic.home");
    }

    @BeforeEach
    void setUp() {
        // Default (empty) role set → isAdminRole() = false, which exercises the
        // restricted nav path in the template.
        User alice = new User(ALICE, "alice@example.com");
        when(securityService.getUserByName(ALICE)).thenReturn(alice);
    }

    private static ApiKey key(Integer id, String username, String name) {
        ApiKey k = new ApiKey(username, "hash-" + id, name, Instant.now(), null);
        k.setId(id);
        return k;
    }

    // ---------- GET / list scoping ----------

    @Test
    @WithMockUser(username = ALICE)
    void displayForm_authenticated_returnsView() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of());

        mockMvc.perform(get("/apikeySettings"))
                .andExpect(status().isOk())
                .andExpect(view().name("apiKeySettings"))
                .andExpect(model().attribute("username", ALICE))
                .andExpect(model().attribute("keys", List.of()));
    }

    @Test
    @WithMockUser(username = ALICE)
    void displayForm_listsOnlyOwnKeys() throws Exception {
        ApiKey aliceKey = key(1, ALICE, "phone");
        when(apiKeyService.list(ALICE)).thenReturn(List.of(aliceKey));

        MvcResult result = mockMvc.perform(get("/apikeySettings"))
                .andExpect(status().isOk())
                .andReturn();

        @SuppressWarnings("unchecked")
        List<ApiKeySettingsController.ApiKeyView> keys =
                (List<ApiKeySettingsController.ApiKeyView>) result.getModelAndView().getModel().get("keys");
        assertThat(keys).hasSize(1);
        assertThat(keys.get(0).id()).isEqualTo(1);
        assertThat(keys.get(0).name()).isEqualTo("phone");
        // Verify the controller asked the service for THIS user's keys only.
        verify(apiKeyService).list(ALICE);
        verify(apiKeyService, never()).list(BOB);
    }

    @Test
    @WithMockUser(username = ALICE)
    void apiKeyView_doesNotExposeUsernameOrHash() {
        ApiKey raw = key(7, ALICE, "phone");
        ApiKeySettingsController.ApiKeyView view = ApiKeySettingsController.ApiKeyView.from(raw);
        // The DTO is the only thing reaching the template. Verify by record components that
        // username and keyHash are not present.
        assertThat(view.getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactlyInAnyOrder("id", "name", "created", "lastUsed", "enabled", "expiresAt");
    }

    // ---------- generate ----------

    @Test
    @WithMockUser(username = ALICE)
    void generate_validName_callsServiceAndFlashesShowOnceKey() throws Exception {
        ApiKey persisted = key(42, ALICE, "phone");
        GeneratedApiKey generated = new GeneratedApiKey("ap_RAW_KEY_XYZ", persisted);
        when(apiKeyService.list(ALICE)).thenReturn(List.of());
        when(apiKeyService.generate(eq(ALICE), eq("phone"), eq(null))).thenReturn(generated);

        mockMvc.perform(post("/apikeySettings/generate").param("name", "phone").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apikeySettings.view"))
                .andExpect(flash().attribute("freshlyGeneratedKey", "ap_RAW_KEY_XYZ"))
                .andExpect(flash().attribute("freshlyGeneratedName", "phone"))
                .andExpect(flash().attribute("settings_toast", true));

        verify(apiKeyService).generate(ALICE, "phone", null);
    }

    @Test
    @WithMockUser(username = ALICE)
    void generate_trimsNameWhitespace() throws Exception {
        ApiKey persisted = key(43, ALICE, "phone");
        when(apiKeyService.list(ALICE)).thenReturn(List.of());
        when(apiKeyService.generate(eq(ALICE), eq("phone"), eq(null)))
                .thenReturn(new GeneratedApiKey("ap_X", persisted));

        mockMvc.perform(post("/apikeySettings/generate").param("name", "  phone  ").with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(apiKeyService).generate(ALICE, "phone", null);
    }

    @Test
    @WithMockUser(username = ALICE)
    void generate_blankName_doesNotCallServiceAndFlashesError() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of());

        mockMvc.perform(post("/apikeySettings/generate").param("name", "   ").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apikeySettings.view"))
                .andExpect(flash().attribute("settings_toast", false))
                .andExpect(flash().attribute("apikey_error", "apikeysettings.error.nameRequired"));

        verify(apiKeyService, never()).generate(any(), any(), any());
    }

    @Test
    @WithMockUser(username = ALICE)
    void generate_overlongName_doesNotCallServiceAndFlashesError() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of());
        String tooLong = "x".repeat(ApiKeySettingsController.MAX_NAME_LENGTH + 1);

        mockMvc.perform(post("/apikeySettings/generate").param("name", tooLong).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apikeySettings.view"))
                .andExpect(flash().attribute("settings_toast", false))
                .andExpect(flash().attribute("apikey_error", "apikeysettings.error.nameTooLong"));

        verify(apiKeyService, never()).generate(any(), any(), any());
    }

    @Test
    @WithMockUser(username = ALICE)
    void generate_pastExpiresAt_doesNotCallServiceAndFlashesError() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of());

        mockMvc.perform(post("/apikeySettings/generate")
                        .param("name", "phone")
                        .param("expiresAt", "1970-01-01T00:00")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("settings_toast", false))
                .andExpect(flash().attribute("apikey_error", "apikeysettings.error.expiresInPast"));

        verify(apiKeyService, never()).generate(any(), any(), any());
    }

    @Test
    @WithMockUser(username = ALICE)
    void generate_invalidExpiresAt_doesNotCallServiceAndFlashesError() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of());

        mockMvc.perform(post("/apikeySettings/generate")
                        .param("name", "phone")
                        .param("expiresAt", "not-a-date")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("settings_toast", false))
                .andExpect(flash().attribute("apikey_error", "apikeysettings.error.expiresInvalid"));

        verify(apiKeyService, never()).generate(any(), any(), any());
    }

    @Test
    @WithMockUser(username = ALICE)
    void generate_withValidExpiresAt_passesInstantToService() throws Exception {
        ApiKey persisted = key(44, ALICE, "phone");
        when(apiKeyService.list(ALICE)).thenReturn(List.of());
        when(apiKeyService.generate(eq(ALICE), eq("phone"), any(Instant.class)))
                .thenReturn(new GeneratedApiKey("ap_X", persisted));

        mockMvc.perform(post("/apikeySettings/generate")
                        .param("name", "phone")
                        .param("expiresAt", "2099-01-01T00:00")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<Instant> instantCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(apiKeyService).generate(eq(ALICE), eq("phone"), instantCaptor.capture());
        assertThat(instantCaptor.getValue()).isNotNull();
    }

    // ---------- revoke / IDOR ----------

    @Test
    @WithMockUser(username = ALICE)
    void revoke_ownedId_callsService() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of(key(5, ALICE, "phone")));

        mockMvc.perform(post("/apikeySettings/revoke").param("id", "5").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apikeySettings.view"))
                .andExpect(flash().attribute("settings_toast", true));

        verify(apiKeyService).revoke(5);
    }

    @Test
    @WithMockUser(username = ALICE)
    void revoke_notOwnedId_doesNotCallService_idorBlocked() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of(key(5, ALICE, "phone")));

        mockMvc.perform(post("/apikeySettings/revoke").param("id", "99").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/apikeySettings.view"))
                .andExpect(flash().attribute("settings_toast", false));

        verify(apiKeyService, never()).revoke(any());
    }

    // ---------- setEnabled / IDOR ----------

    @Test
    @WithMockUser(username = ALICE)
    void setEnabled_ownedId_callsService() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of(key(5, ALICE, "phone")));

        mockMvc.perform(post("/apikeySettings/setEnabled")
                        .param("id", "5")
                        .param("enabled", "false")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("settings_toast", true));

        verify(apiKeyService).setEnabled(5, false);
    }

    @Test
    @WithMockUser(username = ALICE)
    void setEnabled_notOwnedId_doesNotCallService_idorBlocked() throws Exception {
        when(apiKeyService.list(ALICE)).thenReturn(List.of(key(5, ALICE, "phone")));

        mockMvc.perform(post("/apikeySettings/setEnabled")
                        .param("id", "99")
                        .param("enabled", "false")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("settings_toast", false));

        verify(apiKeyService, never()).setEnabled(any(), eq(false));
        verify(apiKeyService, never()).setEnabled(any(), eq(true));
    }

    // ---------- CSRF ----------

    @Test
    @WithMockUser(username = ALICE)
    void generate_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/apikeySettings/generate").param("name", "phone"))
                .andExpect(status().isForbidden());

        verify(apiKeyService, never()).generate(any(), any(), any());
    }

    @Test
    @WithMockUser(username = ALICE)
    void revoke_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/apikeySettings/revoke").param("id", "5"))
                .andExpect(status().isForbidden());

        verify(apiKeyService, never()).revoke(any());
    }

    @Test
    @WithMockUser(username = ALICE)
    void setEnabled_withoutCsrfToken_isForbidden() throws Exception {
        mockMvc.perform(post("/apikeySettings/setEnabled")
                        .param("id", "5")
                        .param("enabled", "false"))
                .andExpect(status().isForbidden());

        verify(apiKeyService, never()).setEnabled(any(), eq(false));
        verify(apiKeyService, never()).setEnabled(any(), eq(true));
    }

    // ---------- Show-once flash semantics ----------

    @Test
    @WithMockUser(username = ALICE)
    void freshlyGeneratedKey_reachesNextGetThenIsGone() throws Exception {
        ApiKey persisted = key(50, ALICE, "phone");
        when(apiKeyService.list(ALICE)).thenReturn(List.of(persisted));
        when(apiKeyService.generate(eq(ALICE), eq("phone"), eq(null)))
                .thenReturn(new GeneratedApiKey("ap_SECRET", persisted));

        // POST seeds the flash...
        MvcResult post = mockMvc.perform(post("/apikeySettings/generate")
                        .param("name", "phone").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // ...next GET sees it as a model attribute (auto-pulled from FlashMap)...
        MvcResult firstGet = mockMvc.perform(get("/apikeySettings.view")
                        .session((org.springframework.mock.web.MockHttpSession) post.getRequest().getSession()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("freshlyGeneratedKey", "ap_SECRET"))
                .andReturn();

        // ...and the GET after that does NOT (Spring removes the flash after consumption).
        Map<String, Object> secondGetModel = mockMvc.perform(get("/apikeySettings.view")
                        .session((org.springframework.mock.web.MockHttpSession) firstGet.getRequest().getSession()))
                .andExpect(status().isOk())
                .andReturn().getModelAndView().getModel();
        assertThat(secondGetModel).doesNotContainKey("freshlyGeneratedKey");
        assertThat(secondGetModel).doesNotContainKey("freshlyGeneratedName");
    }
}
