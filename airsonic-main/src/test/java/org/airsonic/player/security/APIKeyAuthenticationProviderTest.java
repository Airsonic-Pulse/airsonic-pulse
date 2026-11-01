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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.airsonic.player.domain.ApiKey;
import org.airsonic.player.service.ApiKeyService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class APIKeyAuthenticationProviderTest {

    @Mock
    private ApiKeyService apiKeyService;
    @Mock
    private SecurityService securityService;

    @InjectMocks
    private APIKeyAuthenticationProvider provider;

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void attachAppender() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(APIKeyAuthenticationProvider.class);
        previousLevel = logger.getLevel();
        // Ensure INFO audit events reach the appender regardless of the ambient log config.
        logger.setLevel(Level.INFO);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        logger.setLevel(previousLevel);
    }

    private List<String> infoMessages() {
        return appender.list.stream()
                .filter(e -> e.getLevel() == Level.INFO)
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
    }

    private UserDetails activeUser() {
        return new User("alice", "n/a", true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private ApiKey aliceKey() {
        ApiKey k = new ApiKey("alice", "h", "phone", Instant.now(), null);
        k.setId(7);
        return k;
    }

    private ApiKey keyWithHash(String keyHash) {
        // A distinctive hash value so the no-key-material log assertions have something
        // recognisable to assert the absence of.
        ApiKey k = new ApiKey("alice", keyHash, "phone", Instant.now(), null);
        k.setId(7);
        return k;
    }

    @Test
    void supports_onlyAPIKeyToken() {
        assertTrue(provider.supports(APIKeyAuthenticationToken.class));
        assertTrue(!provider.supports(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void authenticate_validKey_returnsAuthenticatedTokenWithUserAuthorities() {
        ApiKey apiKey = aliceKey();
        when(apiKeyService.resolveWithReason("ap_alpha"))
                .thenReturn(new ApiKeyService.ResolveResult.Resolved(apiKey));
        when(securityService.loadUserByUsername("alice")).thenReturn(activeUser());

        Authentication result = provider.authenticate(new APIKeyAuthenticationToken(null, "ap_alpha"));

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result).isInstanceOf(APIKeyAuthenticationToken.class);
        assertSame(apiKey, ((APIKeyAuthenticationToken) result).getApiKey(),
                "ApiKey must be carried on the dedicated field so it survives credential erasure");
        assertThat(result.getAuthorities()).extracting(Object::toString).contains("ROLE_USER");
        assertThat(((UserDetails) result.getPrincipal()).getUsername()).isEqualTo("alice");
    }

    @Test
    void authenticate_unknownKey_collapsesToBadCredentials() {
        when(apiKeyService.resolveWithReason("ap_unknown"))
                .thenReturn(new ApiKeyService.ResolveResult.Unknown());

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_unknown")));
    }

    @Test
    void authenticate_disabledOrExpiredKey_collapsesToBadCredentials() {
        // #284: the provider now sees the distinct Disabled / Expired results, but both must
        // still collapse to the identical opaque exception.
        when(apiKeyService.resolveWithReason("ap_disabled"))
                .thenReturn(new ApiKeyService.ResolveResult.Disabled(aliceKey()));
        when(apiKeyService.resolveWithReason("ap_expired"))
                .thenReturn(new ApiKeyService.ResolveResult.Expired(aliceKey()));

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_disabled")));
        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_expired")));
    }

    @Test
    void authenticate_userGone_collapsesToBadCredentials() {
        // Enumeration-oracle defence: a key pointing at a deleted user must look identical
        // to "no such key" from the outside.
        when(apiKeyService.resolveWithReason("ap_orphaned"))
                .thenReturn(new ApiKeyService.ResolveResult.Resolved(aliceKey()));
        when(securityService.loadUserByUsername("alice"))
                .thenThrow(new UsernameNotFoundException("alice"));

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_orphaned")));
    }

    @Test
    void authenticate_disabledUser_collapsesToBadCredentials() {
        when(apiKeyService.resolveWithReason("ap_alpha"))
                .thenReturn(new ApiKeyService.ResolveResult.Resolved(aliceKey()));
        UserDetails locked = new User("alice", "n/a", false, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(securityService.loadUserByUsername("alice")).thenReturn(locked);

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_alpha")));
    }

    @Test
    void authenticate_blankOrNullCredentials_rejected() {
        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, null)));
        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "")));
        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "   ")));
    }

    // ---------------------------------------------------------------------------------------
    // Operator audit log (#237): each failure path logs the reason (+ apiKeyId where a key
    // resolved) at INFO, while the thrown response stays opaque across every mode.
    // ---------------------------------------------------------------------------------------

    @Test
    void auditLog_blankCredentials_logsReasonNoId() {
        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "   ")));

        assertThat(infoMessages()).hasSize(1);
        assertThat(infoMessages().get(0)).contains("blank or absent credentials");
        // No id exists on this path, and no key material may appear.
        assertThat(infoMessages().get(0)).doesNotContain("apiKeyId=");
    }

    @Test
    void auditLog_unknownKey_logsReasonNoIdAndNoRawKey() {
        when(apiKeyService.resolveWithReason("ap_unknown_secret"))
                .thenReturn(new ApiKeyService.ResolveResult.Unknown());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_unknown_secret")));

        // The response stays opaque — same class, message, and (absent) cause as every mode.
        assertThat(ex.getMessage()).isEqualTo("Invalid API key");
        assertThat(ex.getCause()).isNull();
        assertThat(infoMessages()).hasSize(1);
        assertThat(infoMessages().get(0)).contains("unknown API key");
        assertThat(infoMessages().get(0)).doesNotContain("apiKeyId=");
        // The presented raw key must never reach any log sink.
        assertThat(infoMessages().get(0)).doesNotContain("ap_unknown_secret");
    }

    @Test
    void auditLog_disabledKey_logsReasonWithApiKeyIdAndNoKeyMaterial() {
        // #284: a disabled key matched a row, so the audit line carries the key id — but the
        // raw key and the stored hash must stay out of the log, and the throw stays opaque.
        ApiKey disabled = keyWithHash("0123456789abcdef_disabled_hash");
        when(apiKeyService.resolveWithReason("ap_disabled_secret"))
                .thenReturn(new ApiKeyService.ResolveResult.Disabled(disabled));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_disabled_secret")));

        assertThat(ex.getMessage()).isEqualTo("Invalid API key");
        assertThat(ex.getCause()).isNull();
        assertThat(infoMessages()).hasSize(1);
        assertThat(infoMessages().get(0)).contains("key is disabled");
        assertThat(infoMessages().get(0)).contains("apiKeyId=7");
        assertThat(infoMessages().get(0)).doesNotContain("ap_disabled_secret");
        assertThat(infoMessages().get(0)).doesNotContain("0123456789abcdef_disabled_hash");
    }

    @Test
    void auditLog_expiredKey_logsReasonWithApiKeyIdAndNoKeyMaterial() {
        ApiKey expired = keyWithHash("0123456789abcdef_expired_hash");
        when(apiKeyService.resolveWithReason("ap_expired_secret"))
                .thenReturn(new ApiKeyService.ResolveResult.Expired(expired));

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_expired_secret")));

        assertThat(ex.getMessage()).isEqualTo("Invalid API key");
        assertThat(ex.getCause()).isNull();
        assertThat(infoMessages()).hasSize(1);
        assertThat(infoMessages().get(0)).contains("key has expired");
        assertThat(infoMessages().get(0)).contains("apiKeyId=7");
        assertThat(infoMessages().get(0)).doesNotContain("ap_expired_secret");
        assertThat(infoMessages().get(0)).doesNotContain("0123456789abcdef_expired_hash");
    }

    @Test
    void auditLog_userGone_logsReasonWithApiKeyId() {
        when(apiKeyService.resolveWithReason("ap_orphaned"))
                .thenReturn(new ApiKeyService.ResolveResult.Resolved(aliceKey()));
        when(securityService.loadUserByUsername("alice"))
                .thenThrow(new UsernameNotFoundException("alice"));

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_orphaned")));

        assertThat(infoMessages()).hasSize(1);
        assertThat(infoMessages().get(0)).contains("user that no longer exists");
        assertThat(infoMessages().get(0)).contains("apiKeyId=7");
    }

    @Test
    void auditLog_disabledUser_logsReasonWithApiKeyId() {
        when(apiKeyService.resolveWithReason("ap_alpha"))
                .thenReturn(new ApiKeyService.ResolveResult.Resolved(aliceKey()));
        UserDetails locked = new User("alice", "n/a", false, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(securityService.loadUserByUsername("alice")).thenReturn(locked);

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_alpha")));

        assertThat(infoMessages()).hasSize(1);
        assertThat(infoMessages().get(0)).contains("disabled, locked, or expired");
        assertThat(infoMessages().get(0)).contains("apiKeyId=7");
    }

    @Test
    void responseStaysOpaque_acrossAllFailureModes() {
        // The enumeration-oracle defence: every failure mode must throw the identical opaque
        // exception — same type, same message, no distinguishing cause — regardless of the
        // (distinguishing) audit log. This is the test that proves the defence wasn't weakened.
        when(apiKeyService.resolveWithReason("ap_unknown"))
                .thenReturn(new ApiKeyService.ResolveResult.Unknown());
        when(apiKeyService.resolveWithReason("ap_orphaned"))
                .thenReturn(new ApiKeyService.ResolveResult.Resolved(aliceKey()));
        when(apiKeyService.resolveWithReason("ap_disabled_user"))
                .thenReturn(new ApiKeyService.ResolveResult.Resolved(aliceKey()));
        UserDetails lockedUser = new User("bob", "n/a", false, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        when(securityService.loadUserByUsername("alice"))
                .thenThrow(new UsernameNotFoundException("alice"))
                .thenReturn(lockedUser);

        List<APIKeyAuthenticationToken> failingTokens = List.of(
                new APIKeyAuthenticationToken(null, "   "),
                new APIKeyAuthenticationToken(null, "ap_unknown"),
                new APIKeyAuthenticationToken(null, "ap_orphaned"),
                new APIKeyAuthenticationToken(null, "ap_disabled_user"));

        for (APIKeyAuthenticationToken token : failingTokens) {
            BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                    () -> provider.authenticate(token));
            assertThat(ex.getMessage()).isEqualTo("Invalid API key");
            assertThat(ex.getCause()).isNull();
        }
    }

    @Test
    void auditLog_successPath_emitsNoFailureAudit() {
        when(apiKeyService.resolveWithReason("ap_alpha"))
                .thenReturn(new ApiKeyService.ResolveResult.Resolved(aliceKey()));
        when(securityService.loadUserByUsername("alice")).thenReturn(activeUser());

        Authentication result = provider.authenticate(new APIKeyAuthenticationToken(null, "ap_alpha"));

        assertTrue(result.isAuthenticated());
        assertThat(infoMessages()).isEmpty();
    }
}
