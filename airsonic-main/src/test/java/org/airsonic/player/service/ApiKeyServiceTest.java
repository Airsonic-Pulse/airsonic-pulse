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
package org.airsonic.player.service;

import org.airsonic.player.domain.ApiKey;
import org.airsonic.player.repository.ApiKeyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private SettingsService settingsService;
    @Mock
    private ApiKeyRepository apiKeyRepository;

    private ApiKeyService service;

    /**
     * Simulates {@code airsonic.properties}: getApiKeyPepper / setApiKeyPepper round-trip
     * through this in-memory map so we can observe the bootstrap effects without a real
     * properties file.
     */
    private final Map<String, String> propStore = new HashMap<>();

    @BeforeEach
    void setUp() {
        // Lenient — not every test exercises the pepper path (list/revoke/setEnabled don't).
        lenient().when(settingsService.getApiKeyPepper())
                .thenAnswer(inv -> propStore.get("ApiKeyPepper"));
        service = new ApiKeyService(settingsService, apiKeyRepository);
    }

    private void wireSetterCapture() {
        org.mockito.stubbing.Answer<Void> store = inv -> {
            propStore.put("ApiKeyPepper", inv.getArgument(0));
            return null;
        };
        lenient().doAnswer(store).when(settingsService).setApiKeyPepper(any());
    }

    @Test
    void bootstrapPepper_generatesAndPersistsWhenAbsent() {
        wireSetterCapture();
        service.bootstrapPepper();

        assertNotNull(propStore.get("ApiKeyPepper"), "pepper should be persisted on first boot");
        verify(settingsService).save();
    }

    @Test
    void bootstrapPepper_reusesPersistedPepperOnSecondBoot() {
        // First boot generates and persists.
        wireSetterCapture();
        service.bootstrapPepper();
        String firstPepper = propStore.get("ApiKeyPepper");

        // Second boot: a fresh service instance reads the same pepper from props, no regenerate.
        ApiKeyService second = new ApiKeyService(settingsService, apiKeyRepository);
        second.bootstrapPepper();
        assertEquals(firstPepper, propStore.get("ApiKeyPepper"),
                "pepper must survive across boots");

        // Same raw key hashes to the same hash under both instances → pepper is the same.
        String hash1 = service.hash("ap_canary");
        String hash2 = second.hash("ap_canary");
        assertEquals(hash1, hash2);
    }

    @Test
    void hash_isDeterministicForSamePepperAndKey() {
        wireSetterCapture();
        service.bootstrapPepper();
        assertEquals(service.hash("ap_canary"), service.hash("ap_canary"));
    }

    @Test
    void hash_differsForDifferentKeys() {
        wireSetterCapture();
        service.bootstrapPepper();
        assertNotEquals(service.hash("ap_one"), service.hash("ap_two"));
    }

    @Test
    void hash_outputIs64HexChars() {
        // SHA-256 → 32 bytes → 64 lowercase hex chars. Locks the encoding choice.
        wireSetterCapture();
        service.bootstrapPepper();
        String hash = service.hash("ap_anything");
        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]{64}"));
    }

    @Test
    void generate_returnsApPrefixedRawKey() {
        wireSetterCapture();
        service.bootstrapPepper();

        ApiKeyService.GeneratedApiKey result = service.generate("alice", "Symfonium phone", null);

        assertTrue(result.rawKey().startsWith("ap_"),
                "raw key must carry the ap_ prefix for secret-scanning");
        // ap_ + 43 base64url chars (32 bytes → 43 unpadded base64 chars).
        assertEquals(3 + 43, result.rawKey().length());
        assertTrue(result.rawKey().substring(3).matches("[A-Za-z0-9_-]+"),
                "base64url alphabet — no +, /, = ");
    }

    @Test
    void generate_persistsHashNeverRawKey() {
        wireSetterCapture();
        service.bootstrapPepper();
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.GeneratedApiKey result = service.generate("alice", "phone", null);

        verify(apiKeyRepository).save(captor.capture());
        ApiKey persisted = captor.getValue();
        assertNotEquals(result.rawKey(), persisted.getKeyHash(),
                "raw key must never appear in the persisted row");
        assertEquals(service.hash(result.rawKey()), persisted.getKeyHash(),
                "persisted hash must equal HMAC of the raw key");
        assertEquals("alice", persisted.getUsername());
        assertEquals("phone", persisted.getName());
        assertNotNull(persisted.getCreated());
        assertTrue(persisted.isEnabled());
    }

    @Test
    void generate_keyHashHasExpectedLengthAndFormat() {
        wireSetterCapture();
        service.bootstrapPepper();
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyService.GeneratedApiKey result = service.generate("alice", "phone", null);

        assertThat(result.persisted().getKeyHash()).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void generate_uniqueRawKeyEachCall() {
        // Two generates must produce two different raw keys (SecureRandom entropy).
        wireSetterCapture();
        service.bootstrapPepper();
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        String one = service.generate("alice", "k1", null).rawKey();
        String two = service.generate("alice", "k2", null).rawKey();
        assertNotEquals(one, two);
    }

    @Test
    void generate_acceptsExpiresAt() {
        wireSetterCapture();
        service.bootstrapPepper();
        Instant tomorrow = Instant.now().plus(1, ChronoUnit.DAYS);
        ArgumentCaptor<ApiKey> captor = ArgumentCaptor.forClass(ApiKey.class);
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.generate("alice", "phone", tomorrow);

        verify(apiKeyRepository).save(captor.capture());
        assertEquals(tomorrow, captor.getValue().getExpiresAt());
    }

    @Test
    void generate_rejectsBlankUsername() {
        wireSetterCapture();
        service.bootstrapPepper();
        assertThrows(IllegalArgumentException.class, () -> service.generate(null, "n", null));
        assertThrows(IllegalArgumentException.class, () -> service.generate("", "n", null));
        assertThrows(IllegalArgumentException.class, () -> service.generate("  ", "n", null));
    }

    @Test
    void generate_rejectsBlankName() {
        wireSetterCapture();
        service.bootstrapPepper();
        assertThrows(IllegalArgumentException.class, () -> service.generate("alice", null, null));
        assertThrows(IllegalArgumentException.class, () -> service.generate("alice", "", null));
        assertThrows(IllegalArgumentException.class, () -> service.generate("alice", "  ", null));
    }

    @Test
    void resolve_returnsApiKeyWhenHashMatchesAndKeyIsValid() {
        wireSetterCapture();
        service.bootstrapPepper();
        // Build an entity whose key_hash is HMAC of a known raw key.
        String raw = "ap_canary";
        ApiKey entity = new ApiKey("alice", service.hash(raw), "phone", Instant.now(), null);
        entity.setId(1);
        when(apiKeyRepository.findByKeyHash(service.hash(raw))).thenReturn(Optional.of(entity));

        Optional<ApiKey> resolved = service.resolve(raw);

        assertTrue(resolved.isPresent());
        assertSame(entity, resolved.get());
    }

    @Test
    void resolve_returnsEmptyForUnknownKey() {
        wireSetterCapture();
        service.bootstrapPepper();
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.empty());

        assertTrue(service.resolve("ap_no_match").isEmpty());
    }

    @Test
    void resolve_returnsEmptyForDisabledKey() {
        wireSetterCapture();
        service.bootstrapPepper();
        String raw = "ap_disabled";
        ApiKey entity = new ApiKey("alice", service.hash(raw), "phone", Instant.now(), null);
        entity.setEnabled(false);
        when(apiKeyRepository.findByKeyHash(service.hash(raw))).thenReturn(Optional.of(entity));

        assertTrue(service.resolve(raw).isEmpty());
    }

    @Test
    void resolve_returnsEmptyForExpiredKey() {
        wireSetterCapture();
        service.bootstrapPepper();
        String raw = "ap_expired";
        ApiKey entity = new ApiKey("alice", service.hash(raw), "phone", Instant.now(),
                Instant.now().minus(1, ChronoUnit.DAYS));
        when(apiKeyRepository.findByKeyHash(service.hash(raw))).thenReturn(Optional.of(entity));

        assertTrue(service.resolve(raw).isEmpty());
    }

    @Test
    void resolve_returnsKeyWithFutureExpiration() {
        wireSetterCapture();
        service.bootstrapPepper();
        String raw = "ap_future";
        ApiKey entity = new ApiKey("alice", service.hash(raw), "phone", Instant.now(),
                Instant.now().plus(1, ChronoUnit.DAYS));
        when(apiKeyRepository.findByKeyHash(service.hash(raw))).thenReturn(Optional.of(entity));

        assertTrue(service.resolve(raw).isPresent());
    }

    @Test
    void resolve_rejectsKeyWithoutPrefix() {
        // Without the prefix, do not even hash — early exit prevents leaking timing about
        // whether some bare value hashes to a real row.
        wireSetterCapture();
        service.bootstrapPepper();

        assertTrue(service.resolve("no-prefix-here").isEmpty());
        verify(apiKeyRepository, never()).findByKeyHash(any());
    }

    @Test
    void resolve_rejectsBlankInput() {
        wireSetterCapture();
        service.bootstrapPepper();
        assertTrue(service.resolve(null).isEmpty());
        assertTrue(service.resolve("").isEmpty());
        assertTrue(service.resolve("   ").isEmpty());
        verify(apiKeyRepository, never()).findByKeyHash(any());
    }

    @Test
    void resolve_doesNotWriteLastUsed() {
        // PR-A's contract: resolve is read-only. last_used is the throttled concern of PR-B
        // (writing per-request would N+1 the auth hot path).
        wireSetterCapture();
        service.bootstrapPepper();
        String raw = "ap_x";
        ApiKey entity = new ApiKey("alice", service.hash(raw), "phone", Instant.now(), null);
        when(apiKeyRepository.findByKeyHash(any())).thenReturn(Optional.of(entity));

        service.resolve(raw);

        verify(apiKeyRepository, never()).save(any(ApiKey.class));
    }

    @Test
    void list_delegatesToRepositoryOrderedByCreated() {
        ApiKey a = new ApiKey("alice", "h1", "k1", Instant.now(), null);
        ApiKey b = new ApiKey("alice", "h2", "k2", Instant.now(), null);
        when(apiKeyRepository.findByUsernameOrderByCreatedAsc("alice")).thenReturn(List.of(a, b));

        assertEquals(List.of(a, b), service.list("alice"));
    }

    @Test
    void list_blankUsernameReturnsEmpty() {
        assertTrue(service.list(null).isEmpty());
        assertTrue(service.list("").isEmpty());
        verify(apiKeyRepository, never()).findByUsernameOrderByCreatedAsc(any());
    }

    @Test
    void revoke_callsRepositoryDelete() {
        service.revoke(42);
        verify(apiKeyRepository).deleteById(42);
    }

    @Test
    void revoke_nullIdIsNoOp() {
        service.revoke(null);
        verify(apiKeyRepository, never()).deleteById(any());
    }

    @Test
    void setEnabled_updatesFlag() {
        ApiKey entity = new ApiKey("alice", "h", "k", Instant.now(), null);
        entity.setId(7);
        entity.setEnabled(true);
        when(apiKeyRepository.findById(7)).thenReturn(Optional.of(entity));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<ApiKey> result = service.setEnabled(7, false);

        assertTrue(result.isPresent());
        assertFalse(result.get().isEnabled());
        verify(apiKeyRepository).save(entity);
    }

    @Test
    void setEnabled_missingIdReturnsEmpty() {
        when(apiKeyRepository.findById(99)).thenReturn(Optional.empty());
        assertTrue(service.setEnabled(99, true).isEmpty());
        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void generate_doesNotLogTheRawKey() {
        // Hard contract — never log the raw key. This test is a smoke check: capture the
        // ApiKeyService logger and assert no message contains the rawKey substring.
        wireSetterCapture();
        service.bootstrapPepper();
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(ApiKeyService.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ApiKeyService.GeneratedApiKey result = service.generate("alice", "phone", null);
            for (ch.qos.logback.classic.spi.ILoggingEvent event : appender.list) {
                assertFalse(event.getFormattedMessage().contains(result.rawKey()),
                        "raw key must not appear in any log message");
                assertFalse(event.getFormattedMessage().contains(result.rawKey().substring(3)),
                        "the random portion of the raw key must not appear in any log message");
            }
        } finally {
            logger.detachAppender(appender);
        }
        // Sanity: at least we tested some generation path
        verify(apiKeyRepository, times(1)).save(any(ApiKey.class));
    }

    @Test
    void markUsed_nullLastUsed_writes() {
        ApiKey k = new ApiKey("alice", "h", "k", Instant.now(), null);
        k.setId(7);
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markUsed(k, Duration.ofMinutes(5));

        verify(apiKeyRepository).save(k);
        assertNotNull(k.getLastUsed());
    }

    @Test
    void markUsed_lastUsedOlderThanThreshold_writes() {
        ApiKey k = new ApiKey("alice", "h", "k", Instant.now(), null);
        k.setId(7);
        k.setLastUsed(Instant.now().minus(10, ChronoUnit.MINUTES));
        Instant before = k.getLastUsed();
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        service.markUsed(k, Duration.ofMinutes(5));

        verify(apiKeyRepository).save(k);
        assertTrue(k.getLastUsed().isAfter(before));
    }

    @Test
    void markUsed_lastUsedWithinThreshold_doesNotWrite() {
        // The load-bearing throttle assertion — repeated auth hits within the throttle window
        // must NOT incur a DB write per request.
        ApiKey k = new ApiKey("alice", "h", "k", Instant.now(), null);
        k.setId(7);
        k.setLastUsed(Instant.now().minus(30, ChronoUnit.SECONDS));

        service.markUsed(k, Duration.ofMinutes(5));

        verify(apiKeyRepository, never()).save(any());
    }

    @Test
    void markUsed_nullEntityOrThrottle_isNoOp() {
        service.markUsed(null, Duration.ofMinutes(5));
        service.markUsed(new ApiKey(), null);
        verifyNoInteractions(apiKeyRepository);
    }

    @Test
    void markUsed_entityWithoutId_isNoOp() {
        // Defensive — should never happen via the filter path, but guard anyway.
        ApiKey unsaved = new ApiKey("alice", "h", "k", Instant.now(), null);
        service.markUsed(unsaved, Duration.ofMinutes(5));
        verifyNoInteractions(apiKeyRepository);
    }
}
