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
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.ConfigurationBuilder;
import org.ehcache.config.builders.ExpiryPolicyBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.EntryUnit;
import org.ehcache.jsr107.EhcacheCachingProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.cache.CacheManager;
import javax.cache.Caching;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Real-EhCache integration test for {@link LegacyAuthWarningCache}: drives a tiny
 * heap-only cache with the same key/value types and TTL semantics the production
 * {@link CacheConfiguration} declares, then exercises the throttle and verifies the
 * WARN log content via a logback {@link ch.qos.logback.core.read.ListAppender}.
 *
 * The full-suite test suite already covers the cache wiring through Spring; this test
 * focuses on the behavior: first call logs, second call inside the window suppresses,
 * and the log message contains none of password / token / salt / IP / User-Agent.
 */
class LegacyAuthWarningCacheTest {

    private CacheManager cacheManager;
    private LegacyAuthWarningCache cache;
    private ch.qos.logback.classic.Logger logger;
    private ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        EhcacheCachingProvider provider =
                (EhcacheCachingProvider) Caching.getCachingProvider("org.ehcache.jsr107.EhcacheCachingProvider");
        ResourcePoolsBuilder pools = ResourcePoolsBuilder.newResourcePoolsBuilder()
                .heap(100L, EntryUnit.ENTRIES);
        cacheManager = provider.getCacheManager(
                provider.getDefaultURI(),
                ConfigurationBuilder.newConfigurationBuilder()
                        .withCache(CacheConfiguration.LEGACY_AUTH_WARNING_CACHE,
                                CacheConfigurationBuilder.newCacheConfigurationBuilder(
                                                String.class, Boolean.class, pools)
                                        .withExpiry(ExpiryPolicyBuilder.timeToLiveExpiration(Duration.ofDays(1))))
                        .build());
        cache = new LegacyAuthWarningCache(cacheManager);

        logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(LegacyAuthWarningCache.class);
        appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        cacheManager.close();
    }

    private List<String> warnMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN)
                .map(ch.qos.logback.classic.spi.ILoggingEvent::getFormattedMessage)
                .toList();
    }

    @Test
    void firstCall_emitsWarn() {
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertThat(warnMessages()).hasSize(1);
    }

    @Test
    void secondCallSameKey_isSuppressed() {
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertThat(warnMessages()).hasSize(1);
    }

    @Test
    void differentClient_emitsAgain() {
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertTrue(cache.warnIfFirstSeen("alice", "Symfonium", "legacy username/password"));
        assertThat(warnMessages()).hasSize(2);
    }

    @Test
    void differentUser_emitsAgain() {
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertTrue(cache.warnIfFirstSeen("bob", "DSub", "legacy username/password"));
        assertThat(warnMessages()).hasSize(2);
    }

    @Test
    void differentMethod_emitsAgain() {
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy token+salt"));
        assertThat(warnMessages()).hasSize(2);
    }

    @Test
    void nullOrBlankInputs_doNothing() {
        assertFalse(cache.warnIfFirstSeen(null, "DSub", "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("", "DSub", "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("   ", "DSub", "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("alice", null, "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("alice", "", "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("alice", "DSub", null));
        assertThat(warnMessages()).isEmpty();
    }

    @Test
    void clear_allowsImmediateReEmit() {
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        cache.clear();
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertThat(warnMessages()).hasSize(2);
    }

    @Test
    void warnMessage_scrubsControlCharsToPreventLogInjection() {
        // A username containing CR+LF + a fake-WARN payload must NOT produce a synthetic log
        // line. The sanitizer replaces every ISO-control character with U+FFFD before SLF4J
        // substitution.
        String evil = "alice\r\n2026-05-29 12:00:00 WARN Fake-line-injected";
        cache.warnIfFirstSeen(evil, "DSub", "legacy username/password");
        assertThat(warnMessages()).hasSize(1);
        String message = warnMessages().get(0);
        assertFalse(message.contains("\r"), "CR must be scrubbed");
        assertFalse(message.contains("\n"), "LF must be scrubbed");
        // The literal payload text survives (we don't strip data, only control chars), but
        // it's now all one line, so a downstream log parser sees it as a single entry whose
        // username contains junk — not as a separate WARN line.
        assertEquals(1, message.split("\n", -1).length, "must remain a single log line");
        // The scrubbed char appears, so the operator can see something was substituted.
        assertThat(message).contains("�");
    }

    @Test
    void cacheKey_doesNotCollideOnU001fInUsername() {
        // Without sanitization, alice + U+001F + DSub + ... would collide with alice|DSub|...
        // The sanitizer replaces U+001F with U+FFFD before key construction, eliminating
        // the collision and making both calls fresh.
        String evil = "aliceDSublegacy username/password";
        assertTrue(cache.warnIfFirstSeen(evil, "x", "legacy username/password"));
        assertTrue(cache.warnIfFirstSeen("alice", "DSub", "legacy username/password"));
        assertThat(warnMessages()).hasSize(2);
    }

    @Test
    void overlongInputs_areTreatedAsAlreadySeen() {
        String tooLong = "x".repeat(LegacyAuthWarningCache.MAX_FIELD_LENGTH + 1);
        assertFalse(cache.warnIfFirstSeen(tooLong, "DSub", "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("alice", tooLong, "legacy username/password"));
        assertFalse(cache.warnIfFirstSeen("alice", "DSub", tooLong));
        assertThat(warnMessages()).isEmpty();
    }

    @Test
    void warnMessage_containsNoSecrets() {
        // Caller would never pass the password/token/salt/IP/UA to this method — the API doesn't
        // accept them. This test locks the message template so a future refactor that starts
        // including more context doesn't accidentally leak credentials or surveilling metadata.
        String password = "hunter2";
        String token = "abc123token";
        String salt = "saltsalt";
        String ip = "203.0.113.99";
        String userAgent = "DSub/1.0 (Linux; Android 9)";

        cache.warnIfFirstSeen("alice", "DSub", "legacy username/password");
        cache.warnIfFirstSeen("bob", "Symfonium", "legacy token+salt");

        for (String message : warnMessages()) {
            assertFalse(message.contains(password), "WARN message must not contain a password");
            assertFalse(message.contains(token), "WARN message must not contain a token");
            assertFalse(message.contains(salt), "WARN message must not contain a salt");
            assertFalse(message.contains(ip), "WARN message must not contain an IP");
            assertFalse(message.contains(userAgent), "WARN message must not contain a User-Agent");
        }
        // Sanity: the message does name the deprecation and references apiKey + #56, and
        // states the 13.3.x removal target with the "or a later 13.x.x release" softener
        // to match the release-notes Highlights wording.
        assertThat(warnMessages()).allMatch(m -> m.contains("apiKey")
                && m.contains("#56")
                && m.contains("13.3.x")
                && m.contains("or a later 13.x.x release")
                && (m.contains("legacy username/password") || m.contains("legacy token+salt")));
    }
}
