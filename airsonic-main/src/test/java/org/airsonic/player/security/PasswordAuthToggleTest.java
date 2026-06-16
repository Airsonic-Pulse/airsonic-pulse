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
package org.airsonic.player.security;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.User;
import org.airsonic.player.repository.UserRepository;
import org.airsonic.player.service.ApiKeyService;
import org.airsonic.player.service.cache.UserCache;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the per-user {@code password_auth_enabled} gate (#233). Exercises the full
 * security chain against a real database with the {@code admin}/{@code admin} seed user, proving:
 * the default (flag enabled) leaves legacy {@code u/p} and {@code t/s} auth untouched; flipping the
 * flag off rejects both legacy methods with {@code PASSWORD_AUTH_NOT_SUPPORTED(42)}; and the gate
 * does NOT over-reach — apiKey auth and form-login session auth succeed regardless of the flag, so
 * an admin who disables their own legacy auth is never locked out of the UI where they would re-enable
 * it.
 */
@AutoConfigureMockMvc
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class PasswordAuthToggleTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String API_VERSION = "1.16.1";

    @TempDir
    private static Path tempDir;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private UserCache userCache;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @AfterEach
    public void resetFlag() {
        // The seed user is shared across the class; restore the default so tests don't leak state.
        setPasswordAuthEnabled(true);
    }

    private void setPasswordAuthEnabled(boolean enabled) {
        User user = userRepository.findByUsername(USERNAME).orElseThrow();
        user.setPasswordAuthEnabled(enabled);
        userRepository.saveAndFlush(user);
        // The gate resolves the user via SecurityService.getUserByName, which is UserCache-backed;
        // a raw repository write does not evict it. Evict so the gate sees the new flag value.
        // (The deferred settings-UI write path must do the same — tracked in the follow-up.)
        userCache.removeUser(USERNAME);
    }

    /** Builds a Subsonic salted-token pair (t = md5(password + salt)) for the given salt. */
    private static String saltedToken(String password, String salt) {
        return DigestUtils.md5Hex(password + salt);
    }

    // ---------------------------------------------------------------------------------------
    // default ON — legacy auth unchanged (regression guard)
    // ---------------------------------------------------------------------------------------

    @Test
    public void legacyPassword_succeeds_whenFlagEnabledByDefault() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("u", USERNAME).param("p", PASSWORD)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void legacyToken_succeeds_whenFlagEnabledByDefault() throws Exception {
        String salt = "saltsalt";
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("u", USERNAME).param("s", salt).param("t", saltedToken(PASSWORD, salt))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    // ---------------------------------------------------------------------------------------
    // flag OFF — legacy auth rejected with code 42 (the enforcement core)
    // ---------------------------------------------------------------------------------------

    @Test
    public void legacyPassword_rejectedWith42_whenFlagDisabled() throws Exception {
        setPasswordAuthEnabled(false);
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("u", USERNAME).param("p", PASSWORD)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(42));
    }

    @Test
    public void legacyToken_rejectedWith42_whenFlagDisabled() throws Exception {
        setPasswordAuthEnabled(false);
        String salt = "saltsalt";
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("u", USERNAME).param("s", salt).param("t", saltedToken(PASSWORD, salt))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(42));
    }

    // ---------------------------------------------------------------------------------------
    // no over-reach — apiKey auth succeeds regardless of the flag
    // ---------------------------------------------------------------------------------------

    @Test
    public void apiKey_succeeds_whenFlagEnabled() throws Exception {
        String key = apiKeyService.generate(USERNAME, "toggle-on", null).rawKey();
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void apiKey_succeeds_whenFlagDisabled() throws Exception {
        // The over-reach guard: disabling legacy password auth must NOT disable apiKey auth.
        setPasswordAuthEnabled(false);
        String key = apiKeyService.generate(USERNAME, "toggle-off", null).rawKey();
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("apiKey", key)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    // ---------------------------------------------------------------------------------------
    // no admin lockout — form-login session auth succeeds regardless of the flag
    // ---------------------------------------------------------------------------------------

    @Test
    public void formLogin_succeeds_whenFlagDisabled() throws Exception {
        // The admin-lockout guard: the web UI uses form-login session auth on a different filter,
        // so disabling legacy REST auth must leave the path to re-enable it reachable.
        setPasswordAuthEnabled(false);
        mvc.perform(formLogin("/login")
                .user("j_username", USERNAME).password("j_password", PASSWORD))
                .andExpect(authenticated().withUsername(USERNAME));
    }

    // ---------------------------------------------------------------------------------------
    // persistence + default value
    // ---------------------------------------------------------------------------------------

    @Test
    public void passwordAuthEnabled_persistsRoundTrip() throws Exception {
        setPasswordAuthEnabled(false);
        assertFalse(userRepository.findByUsername(USERNAME).orElseThrow().isPasswordAuthEnabled(),
                "flag must survive a save + reload round-trip");
        setPasswordAuthEnabled(true);
        assertTrue(userRepository.findByUsername(USERNAME).orElseThrow().isPasswordAuthEnabled());
    }

    @Test
    public void seedUser_defaultsToPasswordAuthEnabled() {
        // The column default (TRUE) and the entity field default must agree: existing/new accounts
        // keep legacy auth until they explicitly opt out.
        assertTrue(userRepository.findByUsername(USERNAME).orElseThrow().isPasswordAuthEnabled(),
                "seed user must default to password auth enabled (non-disruptive on upgrade)");
    }
}
