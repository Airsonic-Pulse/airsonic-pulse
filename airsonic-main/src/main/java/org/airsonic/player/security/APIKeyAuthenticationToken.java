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
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

/**
 * Authentication token for OpenSubsonic apiKey requests. Extends {@link AbstractAuthenticationToken}
 * directly (not {@code UsernamePasswordAuthenticationToken}) so the inherited
 * {@code supports()} of UPAT-matching providers ({@code DaoAuthenticationProvider},
 * {@code AbstractLdapAuthenticationProvider}, etc.) does not match this token — apiKey requests
 * route only to {@code APIKeyAuthenticationProvider} and don't incur a stray
 * {@code loadUserByUsername("")} call on every other UPAT provider in the chain.
 * <p>
 * The resolved {@link ApiKey} is carried on a dedicated {@code final apiKey} field so it
 * survives {@code ProviderManager.eraseCredentialsAfterAuthentication} — the filter needs it
 * for the throttled {@code last_used} update without re-querying the DB. The raw key is never
 * logged or echoed.
 */
public class APIKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final Object principal;
    private Object credentials;
    private final ApiKey apiKey;

    /**
     * Unauthenticated form — holds the raw key as credentials and a null principal; the
     * authenticated form is produced by {@link APIKeyAuthenticationProvider#authenticate}.
     */
    public APIKeyAuthenticationToken(Object principal, Object credentials) {
        super(null);
        this.principal = principal;
        this.credentials = credentials;
        this.apiKey = null;
        setAuthenticated(false);
    }

    /**
     * Authenticated form — built by the provider. Credentials are expected to be {@code null}
     * (no need to retain the raw key after authentication); the {@link ApiKey} entity lives on
     * its dedicated field.
     */
    public APIKeyAuthenticationToken(Object principal, Object credentials,
            Collection<? extends GrantedAuthority> authorities, ApiKey apiKey) {
        super(authorities);
        this.principal = principal;
        this.credentials = credentials;
        this.apiKey = apiKey;
        super.setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    public ApiKey getApiKey() {
        return apiKey;
    }

    /**
     * Mirror {@link org.springframework.security.authentication.UsernamePasswordAuthenticationToken#eraseCredentials}:
     * blanks the credentials reference so the raw key (if any is still held) is dropped.
     */
    @Override
    public void eraseCredentials() {
        super.eraseCredentials();
        this.credentials = null;
    }
}
