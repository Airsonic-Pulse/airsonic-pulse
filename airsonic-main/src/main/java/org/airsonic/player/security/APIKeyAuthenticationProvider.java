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
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

/**
 * Resolves an {@link APIKeyAuthenticationToken} against the apiKey storage layer. Returns a
 * fully-authenticated token carrying the user's principal + authorities, or throws
 * {@link BadCredentialsException} for any failure (unknown key, disabled key, expired key,
 * unknown user). All failure paths collapse to the same generic exception — no enumeration
 * oracle distinguishes "no such key" from "disabled key" from "expired key" from "user gone".
 * The raw key is never logged or included in exception messages.
 */
public class APIKeyAuthenticationProvider implements AuthenticationProvider {

    private final ApiKeyService apiKeyService;
    private final SecurityService securityService;

    public APIKeyAuthenticationProvider(ApiKeyService apiKeyService, SecurityService securityService) {
        this.apiKeyService = apiKeyService;
        this.securityService = securityService;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        APIKeyAuthenticationToken token = (APIKeyAuthenticationToken) authentication;
        Object credentials = token.getCredentials();
        if (!(credentials instanceof String rawKey) || rawKey.isBlank()) {
            throw new BadCredentialsException("Invalid API key");
        }

        Optional<ApiKey> resolved = apiKeyService.resolve(rawKey);
        if (resolved.isEmpty()) {
            throw new BadCredentialsException("Invalid API key");
        }
        ApiKey apiKey = resolved.get();

        UserDetails user;
        try {
            user = securityService.loadUserByUsername(apiKey.getUsername());
        } catch (UsernameNotFoundException x) {
            // Key references a user that no longer exists. Collapse to the same generic error so
            // an attacker cannot distinguish "user gone" from "no such key".
            throw new BadCredentialsException("Invalid API key");
        }
        if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) {
            throw new BadCredentialsException("Invalid API key");
        }

        // ApiKey goes on the dedicated field rather than credentials/details so it survives
        // ProviderManager.eraseCredentialsAfterAuthentication (which clears credentials on
        // success by default). The filter reads it for the throttled last_used update.
        // Credentials passed as null — the raw key is not needed after authentication and
        // shouldn't be retained on the authenticated token even briefly.
        APIKeyAuthenticationToken authenticated =
                new APIKeyAuthenticationToken(user, null, user.getAuthorities(), apiKey);
        authenticated.setDetails(token.getDetails());
        return authenticated;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return APIKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
