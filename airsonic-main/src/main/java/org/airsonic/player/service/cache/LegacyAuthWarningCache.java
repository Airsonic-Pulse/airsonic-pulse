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
package org.airsonic.player.service.cache;

import org.airsonic.player.spring.CacheConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.cache.CacheManager;

/**
 * Throttle + emitter for the legacy-authentication deprecation warning (issue #145).
 *
 * When a /rest/ request successfully authenticates via legacy {@code u}/{@code p} or
 * {@code t}/{@code s} parameters, the filter calls {@link #warnIfFirstSeen} with the
 * authenticated username, the Subsonic {@code c=} client identifier, and the method label.
 * The first call for a given {@code (username, client, method)} within the cache's 24h TTL
 * emits a WARN log; subsequent calls inside the window are suppressed.
 *
 * The throttle key intentionally excludes IP and User-Agent so the log message can be
 * produced without those fields ever entering scope, and so the cache keyspace stays bounded
 * by the realistic count of distinct {@code (user, client, method)} tuples a deployment sees
 * in a day. The bounded heap on the underlying EhCache entry (10000 entries) is the DoS guard
 * against a client cycling its {@code c=} value.
 */
@Component
public class LegacyAuthWarningCache {

    private static final Logger LOG = LoggerFactory.getLogger(LegacyAuthWarningCache.class);

    // Unit Separator (U+001F). Inputs are sanitized to strip ALL ISO controls before key
    // construction, so the separator is guaranteed not to appear inside any component and
    // the (user, client, method) tuple cannot be ambiguously decoded.
    private static final char SEPARATOR = 0x1f;

    // Cap per-field length before concatenating the cache key. The realistic upper bound for
    // a Subsonic username or client string is well under 256; an authenticated attacker who
    // can submit pathologically long values cannot inflate per-entry footprint past this.
    static final int MAX_FIELD_LENGTH = 256;

    private final CacheManager cacheManager;

    public LegacyAuthWarningCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * Emit a single throttled WARN for the given (username, client, method) tuple. Returns
     * {@code true} if a log line was emitted, {@code false} if the tuple was already cached
     * (suppressed). The message names the method and points at the apiKey replacement with
     * {@code #56} as the targeted-removal reference; it intentionally contains no password,
     * token, salt, IP, or User-Agent.
     */
    public boolean warnIfFirstSeen(String username, String client, String method) {
        if (username == null || username.isBlank() || client == null || client.isBlank() || method == null) {
            return false;
        }
        // Treat pathological inputs as already-throttled: an authenticated attacker can submit
        // arbitrarily long u=/c= values up to Tomcat's header limit; clamping here keeps per-
        // entry footprint bounded regardless of the LRU cap.
        if (username.length() > MAX_FIELD_LENGTH || client.length() > MAX_FIELD_LENGTH
                || method.length() > MAX_FIELD_LENGTH) {
            return false;
        }
        // Sanitize before BOTH key construction and log emission so a CRLF or U+001F in the
        // raw input cannot inject log lines or collide cache keys.
        String safeUsername = sanitize(username);
        String safeClient = sanitize(client);
        String safeMethod = sanitize(method);
        String key = safeUsername + SEPARATOR + safeClient + SEPARATOR + safeMethod;
        javax.cache.Cache<String, Boolean> cache = cacheManager.getCache(
                CacheConfiguration.LEGACY_AUTH_WARNING_CACHE, String.class, Boolean.class);
        if (cache == null) {
            // Cache manager unavailable (test paths that don't wire it). Emit the warn — the
            // throttle is a courtesy, not a correctness guarantee, and skipping it here is
            // safer than silently dropping the deprecation notice.
            logWarn(safeMethod, safeUsername, safeClient);
            return true;
        }
        if (cache.putIfAbsent(key, Boolean.TRUE)) {
            logWarn(safeMethod, safeUsername, safeClient);
            return true;
        }
        return false;
    }

    /**
     * Test-only: clear the throttle so a previously-suppressed tuple emits again on the next
     * call. Package-private so no admin/controller surface can reset suppression at runtime.
     */
    void clear() {
        javax.cache.Cache<String, Boolean> cache = cacheManager.getCache(
                CacheConfiguration.LEGACY_AUTH_WARNING_CACHE, String.class, Boolean.class);
        if (cache != null) {
            cache.clear();
        }
    }

    /**
     * Replace every ISO control character (CR, LF, NUL, TAB, U+001F, ...) with U+FFFD so a
     * malicious value cannot inject a synthetic log line into the operator log or collide a
     * cache key by embedding the {@link #SEPARATOR}. The substitution character is preserved
     * verbatim so the operator can see something was scrubbed.
     */
    static String sanitize(String s) {
        if (s == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(Character.isISOControl(c) ? '�' : c);
        }
        return sb.toString();
    }

    private static void logWarn(String method, String username, String client) {
        LOG.warn("Deprecated authentication method used: {} (user={}, client={}). "
                + "Generate an API key in Personal Settings -> API Keys and switch the client "
                + "to the OpenSubsonic apiKey extension. Legacy u/p and t+s authentication "
                + "is targeted for removal in 13.3.x (see #56) or a later 13.x.x release.",
                method, username, client);
    }
}
