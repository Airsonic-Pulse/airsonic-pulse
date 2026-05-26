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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Storage layer for the OpenSubsonic {@code apiKeyAuthentication} extension (#145).
 * <p>
 * Keys are issued from {@link SecureRandom}, formatted as {@code ap_<43-char-base64url>}, and
 * persisted ONLY as {@code HMAC-SHA-256(rawKey, serverPepper)} hex. The raw key is returned
 * exactly once at issuance ({@link #generate}); after that, neither the database nor the logs
 * contain anything that can re-derive it. Lookup ({@link #resolve}) recomputes the HMAC and
 * queries by the unique {@code key_hash} column — constant-time equality is guaranteed by the
 * database UNIQUE index and the deterministic HMAC.
 * <p>
 * The pepper is a server secret bootstrapped on first use into {@code airsonic.properties}
 * (key {@code ApiKeyPepper}), mirroring the {@code JWTKey} pattern. It is intentionally NOT
 * stored in the database — a DB exfiltration alone must not be enough to brute-force keys.
 * Losing the pepper invalidates all keys; this is documented in the release notes for the PR
 * that wires this service into the filter chain.
 * <p>
 * Authentication (PR-B) and key-management UI (PR-C) consume this surface; PR-A intentionally
 * does NOT update {@code last_used} (that's the throttled hot-path concern of PR-B).
 */
@Service
public class ApiKeyService {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeyService.class);

    static final String KEY_PREFIX = "ap_";
    private static final int KEY_RANDOM_BYTES = 32;
    private static final int PEPPER_BYTES = 32;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final SettingsService settingsService;
    private final ApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    private volatile byte[] pepper;

    public ApiKeyService(SettingsService settingsService, ApiKeyRepository apiKeyRepository) {
        this.settingsService = settingsService;
        this.apiKeyRepository = apiKeyRepository;
    }

    @PostConstruct
    void bootstrapPepper() {
        String stored = settingsService.getApiKeyPepper();
        if (stored == null || stored.isBlank()) {
            byte[] generated = new byte[PEPPER_BYTES];
            secureRandom.nextBytes(generated);
            // Set the in-memory pepper BEFORE attempting persistence so the service stays
            // usable for this boot even if save() fails — the next boot would regenerate and
            // invalidate any keys issued during this boot, which is the documented operational
            // risk, but resolve() / generate() still work in-process rather than NPE-ing.
            this.pepper = generated;
            String encoded = Base64.getEncoder().encodeToString(generated);
            settingsService.setApiKeyPepper(encoded);
            settingsService.save();
            LOG.info("Generated new API key pepper.");
            return;
        }
        this.pepper = Base64.getDecoder().decode(stored);
    }

    /**
     * Generate and persist a new API key for the given user. Returns the raw key as the caller
     * must show it exactly once — neither this method nor any downstream code stores or logs it.
     * The persisted {@link ApiKey} carries only the HMAC.
     */
    @Transactional
    public GeneratedApiKey generate(String username, String name, Instant expiresAt) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        byte[] random = new byte[KEY_RANDOM_BYTES];
        secureRandom.nextBytes(random);
        String rawKey = KEY_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String hash = hash(rawKey);
        ApiKey entity = new ApiKey(username, hash, name.trim(), Instant.now(), expiresAt);
        apiKeyRepository.save(entity);
        return new GeneratedApiKey(rawKey, entity);
    }

    /**
     * Resolve a presented raw key to the persisted {@link ApiKey}, if the hash exists, the key
     * is enabled, and (when present) has not expired. Read-only — does NOT update last_used.
     */
    @Transactional(readOnly = true)
    public Optional<ApiKey> resolve(String rawKey) {
        if (rawKey == null || rawKey.isBlank() || !rawKey.startsWith(KEY_PREFIX)) {
            return Optional.empty();
        }
        String hash;
        try {
            hash = hash(rawKey);
        } catch (IllegalStateException x) {
            // HMAC misconfiguration (e.g. pepper not initialised) — log so operators can
            // diagnose; the raw key is not part of the exception's data so logging the
            // exception is safe.
            LOG.warn("Failed to HMAC API key candidate; check pepper configuration", x);
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHash(hash)
                .filter(ApiKey::isEnabled)
                .filter(k -> k.getExpiresAt() == null || k.getExpiresAt().isAfter(Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<ApiKey> list(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        return apiKeyRepository.findByUsernameOrderByCreatedAsc(username);
    }

    @Transactional
    public void revoke(Integer id) {
        if (id == null) {
            return;
        }
        apiKeyRepository.deleteById(id);
    }

    @Transactional
    public Optional<ApiKey> setEnabled(Integer id, boolean enabled) {
        if (id == null) {
            return Optional.empty();
        }
        return apiKeyRepository.findById(id).map(k -> {
            k.setEnabled(enabled);
            return apiKeyRepository.save(k);
        });
    }

    String hash(String rawKey) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(pepper, HMAC_ALGORITHM));
            byte[] digest = mac.doFinal(rawKey.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException x) {
            // HmacSHA256 is a JCE standard required to be present in every JDK. If it isn't,
            // the runtime is broken in a way that has nothing to do with this code.
            throw new IllegalStateException("HMAC-SHA-256 unavailable", x);
        }
    }

    /**
     * Pair returned by {@link #generate}: the raw key (caller's responsibility to show once)
     * and the persisted entity (id, name, created, etc. — but NOT the raw key).
     */
    public record GeneratedApiKey(String rawKey, ApiKey persisted) {
    }
}
