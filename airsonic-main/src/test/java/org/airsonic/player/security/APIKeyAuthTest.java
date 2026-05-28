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
import org.airsonic.player.domain.ApiKey;
import org.airsonic.player.repository.ApiKeyRepository;
import org.airsonic.player.service.ApiKeyService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the apiKey auth wiring — covers the full request flow through the
 * security chain (the apiKey filter + the legacy REST filter + HTTP Basic) against a real
 * database, the downgrade-attack prevention, the Bearer-vs-query precedence, the byte-unchanged
 * legacy paths, and the throttled {@code last_used} update.
 */
@AutoConfigureMockMvc
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class APIKeyAuthTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String API_VERSION = "1.16.1";

    @TempDir
    private static Path tempDir;

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    private String aliveKey;
    private Integer aliveKeyId;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void setUp() {
        apiKeyRepository.deleteAll();
        ApiKeyService.GeneratedApiKey g = apiKeyService.generate(USERNAME, "test-key", null);
        this.aliveKey = g.rawKey();
        this.aliveKeyId = g.persisted().getId();
    }

    // ---------------------------------------------------------------------------------------
    // happy paths
    // ---------------------------------------------------------------------------------------

    @Test
    public void apiKeyQueryParam_authenticates() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", aliveKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void apiKeyBearerHeader_authenticates() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .header("Authorization", "Bearer " + aliveKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void bearerHeaderWins_overQueryParam() throws Exception {
        // Header has the valid key; query has a bogus one — header wins, request succeeds.
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", "ap_bogus_query")
                .header("Authorization", "Bearer " + aliveKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    // ---------------------------------------------------------------------------------------
    // downgrade-attack prevention (the security core)
    // ---------------------------------------------------------------------------------------

    @Test
    public void apiKeyPlusU_returns43_neverFallsThroughToLegacy() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", "ap_anything")
                .param("u", "victim")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(43));
    }

    @Test
    public void apiKeyPlusP_returns43_neverFallsThroughToLegacy() throws Exception {
        // The literal downgrade-attack shape: apiKey=guess + p=stolen.
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", "ap_guess")
                .param("p", "stolen_password")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(43));
    }

    @Test
    public void apiKeyPlusTAndS_returns43_neverFallsThroughToLegacy() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", aliveKey)
                .param("t", "ignored")
                .param("s", "ignored")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(43));
    }

    @Test
    public void bearerPlusLegacyParam_returns43() throws Exception {
        // Conflict detection covers the Bearer transport too.
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("u", "victim")
                .header("Authorization", "Bearer " + aliveKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(43));
    }

    @Test
    public void apiKeyHeaderAndQueryTogether_isNotAConflict() throws Exception {
        // Both transports of the SAME auth method (apiKey) — not a conflict, header wins.
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", aliveKey)
                .header("Authorization", "Bearer " + aliveKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    // ---------------------------------------------------------------------------------------
    // invalid keys → 40 (same as legacy wrong-creds)
    // ---------------------------------------------------------------------------------------

    @Test
    public void unknownApiKey_returns40() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", "ap_definitely_not_real")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));
    }

    @Test
    public void keyWithoutApPrefix_returns40() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", "wrong-prefix-key")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));
    }

    @Test
    public void disabledKey_returns40() throws Exception {
        apiKeyService.setEnabled(aliveKeyId, false);

        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", aliveKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));
    }

    @Test
    public void expiredKey_returns40() throws Exception {
        // Issue a key already expired; resolve filters it out → BadCredentials → 40.
        ApiKeyService.GeneratedApiKey expired = apiKeyService.generate(USERNAME, "expired",
                Instant.now().minus(Duration.ofMinutes(1)));

        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", expired.rawKey())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));
    }

    // ---------------------------------------------------------------------------------------
    // legacy auth must remain byte-unchanged
    // ---------------------------------------------------------------------------------------

    @Test
    public void legacyUsernamePassword_stillAuthenticates() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("u", USERNAME)
                .param("p", PASSWORD)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void legacyUsernamePasswordWrongPassword_stillReturns40() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("u", USERNAME)
                .param("p", "wrong"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));
    }

    @Test
    public void basicAuth_stillAuthenticates() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void basicAuthFailure_stillReturns401() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .with(httpBasic(USERNAME, "wrong"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------------------------------------------
    // last_used write — round-trips against the real DB, locks the throttle behavior
    // ---------------------------------------------------------------------------------------

    @Test
    public void successfulAuth_updatesLastUsedInDb() throws Exception {
        // First auth — no prior last_used, should write.
        assertEquals(null, apiKeyRepository.findById(aliveKeyId).orElseThrow().getLastUsed());

        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("apiKey", aliveKey)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        ApiKey reloaded = apiKeyRepository.findById(aliveKeyId).orElseThrow();
        assertNotNull(reloaded.getLastUsed(),
                "first successful auth must persist last_used on a real DB round-trip");
    }

    @Test
    public void repeatedAuthWithinThrottle_doesNotRewriteLastUsed() throws Exception {
        // First auth populates last_used.
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("apiKey", aliveKey))
                .andExpect(status().isOk());
        Instant firstWrite = apiKeyRepository.findById(aliveKeyId).orElseThrow().getLastUsed();
        assertNotNull(firstWrite);

        // Second auth within the 5-minute throttle window — last_used must NOT advance.
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("apiKey", aliveKey))
                .andExpect(status().isOk());
        Instant secondRead = apiKeyRepository.findById(aliveKeyId).orElseThrow().getLastUsed();

        assertEquals(firstWrite, secondRead,
                "throttle must prevent a DB write on every authenticated request");
    }

    @Test
    public void staleLastUsed_updatesOnNextAuth() throws Exception {
        // Force a stale lastUsed (older than the throttle); next auth should advance it.
        ApiKey key = apiKeyRepository.findById(aliveKeyId).orElseThrow();
        Instant stale = Instant.now().minus(Duration.ofMinutes(10));
        key.setLastUsed(stale);
        apiKeyRepository.saveAndFlush(key);

        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION).param("c", "test").param("f", "json")
                .param("apiKey", aliveKey))
                .andExpect(status().isOk());

        Instant after = apiKeyRepository.findById(aliveKeyId).orElseThrow().getLastUsed();
        assertTrue(after.isAfter(stale),
                "stale last_used must be advanced on the next authenticated request");
    }

    // ---------------------------------------------------------------------------------------
    // extension advert
    // ---------------------------------------------------------------------------------------

    @Test
    public void extensionsListAdvertisesApiKeyAuthenticationV1() throws Exception {
        mvc.perform(get("/rest/getOpenSubsonicExtensions")
                .param("f", "json")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.openSubsonicExtensions.openSubsonicExtension[*].name")
                        .value(org.hamcrest.Matchers.hasItem("apiKeyAuthentication")))
                .andExpect(jsonPath(
                        "$.subsonic-response.openSubsonicExtensions.openSubsonicExtension[?(@.name=='apiKeyAuthentication')].versions[0]")
                        .value(org.hamcrest.Matchers.hasItem(1)));
    }
}
