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

import org.airsonic.player.domain.ApiKey;
import org.airsonic.player.service.ApiKeyService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    private UserDetails activeUser() {
        return new User("alice", "n/a", true, true, true, true,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private ApiKey aliceKey() {
        ApiKey k = new ApiKey("alice", "h", "phone", Instant.now(), null);
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
        when(apiKeyService.resolve("ap_alpha")).thenReturn(Optional.of(apiKey));
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
        when(apiKeyService.resolve("ap_unknown")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_unknown")));
    }

    @Test
    void authenticate_disabledOrExpiredKey_collapsesToBadCredentials() {
        // ApiKeyService.resolve already filters disabled + expired — provider sees Optional.empty.
        when(apiKeyService.resolve("ap_disabled")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_disabled")));
    }

    @Test
    void authenticate_userGone_collapsesToBadCredentials() {
        // Enumeration-oracle defence: a key pointing at a deleted user must look identical
        // to "no such key" from the outside.
        when(apiKeyService.resolve("ap_orphaned")).thenReturn(Optional.of(aliceKey()));
        when(securityService.loadUserByUsername("alice"))
                .thenThrow(new UsernameNotFoundException("alice"));

        assertThrows(BadCredentialsException.class,
                () -> provider.authenticate(new APIKeyAuthenticationToken(null, "ap_orphaned")));
    }

    @Test
    void authenticate_disabledUser_collapsesToBadCredentials() {
        when(apiKeyService.resolve("ap_alpha")).thenReturn(Optional.of(aliceKey()));
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
}
