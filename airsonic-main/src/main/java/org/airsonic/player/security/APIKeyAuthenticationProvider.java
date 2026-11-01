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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

/**
 * Resolves an {@link APIKeyAuthenticationToken} against the apiKey storage layer. Returns a
 * fully-authenticated token carrying the user's principal + authorities, or throws
 * {@link BadCredentialsException} for any failure (unknown key, disabled key, expired key,
 * unknown user). All failure paths collapse to the same generic exception — no enumeration
 * oracle distinguishes "no such key" from "disabled key" from "expired key" from "user gone".
 * The raw key is never logged or included in exception messages.
 * <p>
 * Each failure path additionally emits an operator-only audit log at INFO recording the actual
 * reason (#237, refined by #284 to distinguish unknown, disabled, and expired keys). The audit
 * log is keyed on {@link ApiKey#getId()} wherever a key row was matched (disabled key, expired
 * key, user-gone, user-disabled); the paths with no matched row (blank credentials, unknown key)
 * log the reason with no key material — never the raw key or its hash. The audit log
 * distinguishes the modes for forensics; the thrown exception stays opaque so the client-facing
 * response reveals nothing (the enumeration-oracle defence).
 */
public class APIKeyAuthenticationProvider implements AuthenticationProvider {

    private static final Logger LOG = LoggerFactory.getLogger(APIKeyAuthenticationProvider.class);

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
            // No ApiKey resolved — no id exists; log the reason only, never the raw value.
            LOG.info("API key authentication failed: blank or absent credentials");
            throw new BadCredentialsException("Invalid API key");
        }

        ApiKeyService.ResolveResult result = apiKeyService.resolveWithReason(rawKey);
        if (!(result instanceof ApiKeyService.ResolveResult.Resolved(ApiKey resolvedKey))) {
            // #284: the audit sink distinguishes the modes; the client response must not. Each
            // mode logs its reason (keyed on the key id where a row matched, never the raw key
            // or its hash), then every mode falls through to the one identical throw below.
            switch (result) {
                case ApiKeyService.ResolveResult.Unknown ignored ->
                    LOG.info("API key authentication failed: unknown API key (no issued key matches; probe, typo, or a key from a different pepper)");
                case ApiKeyService.ResolveResult.Disabled(ApiKey disabledKey) ->
                    LOG.info("API key authentication failed: key is disabled but still being presented [apiKeyId={}]", disabledKey.getId());
                case ApiKeyService.ResolveResult.Expired(ApiKey expiredKey) ->
                    LOG.info("API key authentication failed: key has expired and needs rotation [apiKeyId={}]", expiredKey.getId());
                case ApiKeyService.ResolveResult.Resolved ignored -> {
                    // Unreachable — the resolved case is handled above; present for exhaustiveness.
                }
            }
            throw new BadCredentialsException("Invalid API key");
        }
        ApiKey apiKey = resolvedKey;

        UserDetails user;
        try {
            user = securityService.loadUserByUsername(apiKey.getUsername());
        } catch (UsernameNotFoundException x) {
            // Key references a user that no longer exists. Collapse to the same generic error so
            // an attacker cannot distinguish "user gone" from "no such key".
            LOG.info("API key authentication failed: key references a user that no longer exists [apiKeyId={}]", apiKey.getId());
            throw new BadCredentialsException("Invalid API key");
        }
        if (!user.isEnabled() || !user.isAccountNonLocked() || !user.isAccountNonExpired()
                || !user.isCredentialsNonExpired()) {
            LOG.info("API key authentication failed: user account is disabled, locked, or expired [apiKeyId={}]", apiKey.getId());
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
