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

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.service.cache.LegacyAuthWarningCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Drives {@link RESTRequestParameterProcessingFilter#maybeWarnLegacyAuth} directly with a
 * mock {@link LegacyAuthWarningCache} so the test concentrates on the legacy-auth-detection
 * logic without running the full {@link org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter}
 * machinery. Spring-Security guarantees the hook fires after any successful authentication;
 * the responsibility of this filter's hook is to identify the legacy-Subsonic case and emit
 * the deprecation warn for it alone.
 */
@ExtendWith(MockitoExtension.class)
class RESTRequestParameterProcessingFilterDeprecationTest {

    @Mock
    private JAXBWriter jaxbWriter;

    @Mock
    private LegacyAuthWarningCache cache;

    private RESTRequestParameterProcessingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RESTRequestParameterProcessingFilter(jaxbWriter);
        filter.setLegacyAuthWarningCache(cache);
    }

    private Authentication authFor(String name) {
        return new UsernamePasswordAuthenticationToken(name, null, List.of());
    }

    @Test
    void legacyPasswordAuth_emitsDeprecationWarn() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("u", "alice");
        request.setParameter("p", "secret");
        request.setParameter("c", "DSub");

        filter.maybeWarnLegacyAuth(request, authFor("alice"));

        verify(cache).warnIfFirstSeen(eq("alice"), eq("DSub"),
                eq(RESTRequestParameterProcessingFilter.LEGACY_METHOD_PASSWORD));
    }

    @Test
    void legacySaltedTokenAuth_emitsDeprecationWarn() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("u", "alice");
        request.setParameter("t", "abc123");
        request.setParameter("s", "saltsalt");
        request.setParameter("c", "Symfonium");

        filter.maybeWarnLegacyAuth(request, authFor("alice"));

        verify(cache).warnIfFirstSeen(eq("alice"), eq("Symfonium"),
                eq(RESTRequestParameterProcessingFilter.LEGACY_METHOD_SALTED_TOKEN));
    }

    @Test
    void apiKeyRequest_doesNotEmitDeprecationWarn() {
        // Pure apiKey request: no u/p/t/s params; only c= is present (clients identify
        // themselves with c= regardless of auth method).
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("c", "Symfonium");
        request.addHeader("Authorization", "Bearer ap_some_key");

        filter.maybeWarnLegacyAuth(request, authFor("alice"));

        verifyNoInteractions(cache);
    }

    @Test
    void basicAuthOnRestPath_doesNotEmitDeprecationWarn() {
        // HTTP Basic on a /rest/ URL: BasicAuthenticationFilter has already populated the
        // SecurityContext; legacy filter early-exits with the existing auth. No u/p/t/s
        // in the request, so the hook stays silent.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("c", "DSub");
        request.addHeader("Authorization", "Basic YWxpY2U6c2VjcmV0");

        filter.maybeWarnLegacyAuth(request, authFor("alice"));

        verifyNoInteractions(cache);
    }

    @Test
    void requestWithUsernameButNoCredentials_doesNotEmitDeprecationWarn() {
        // u= present but no p, t, or s. This should not happen in practice because
        // attemptAuthentication rejects with MISSING_PARAMETER, but the hook must be
        // robust.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("u", "alice");
        request.setParameter("c", "DSub");

        filter.maybeWarnLegacyAuth(request, authFor("alice"));

        verifyNoInteractions(cache);
    }

    @Test
    void nullAuthResult_doesNotEmitDeprecationWarn() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("u", "alice");
        request.setParameter("p", "secret");
        request.setParameter("c", "DSub");

        filter.maybeWarnLegacyAuth(request, null);

        verifyNoInteractions(cache);
    }

    @Test
    void missingClient_doesNotEmitDeprecationWarn() {
        // No c= — RESTRequestParameterProcessingFilter.attemptAuthentication rejects this
        // with MISSING_PARAMETER before successfulAuthentication can fire, but the hook
        // guards against the case defensively. (Throttle key needs a client.)
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("u", "alice");
        request.setParameter("p", "secret");

        filter.maybeWarnLegacyAuth(request, authFor("alice"));

        verifyNoInteractions(cache);
    }

    @Test
    void cacheUnset_isANoOp() {
        // setLegacyAuthWarningCache was never called → no NPE on the hook.
        RESTRequestParameterProcessingFilter unwiredFilter =
                new RESTRequestParameterProcessingFilter(jaxbWriter);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("u", "alice");
        request.setParameter("p", "secret");
        request.setParameter("c", "DSub");

        // Should not throw and should not (cannot) touch the cache.
        unwiredFilter.maybeWarnLegacyAuth(request, authFor("alice"));
        verify(cache, never()).warnIfFirstSeen(any(), any(), any());
    }

    @Test
    void blankUsername_doesNotEmitDeprecationWarn() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("u", "alice");
        request.setParameter("p", "secret");
        request.setParameter("c", "DSub");

        filter.maybeWarnLegacyAuth(request, authFor("   "));

        verifyNoInteractions(cache);
    }

    @Test
    void identifyLegacyAuthMethod_classifies_passwordAndSaltedToken() {
        MockHttpServletRequest pw = new MockHttpServletRequest();
        pw.setParameter("u", "alice");
        pw.setParameter("p", "secret");
        org.junit.jupiter.api.Assertions.assertEquals(
                RESTRequestParameterProcessingFilter.LEGACY_METHOD_PASSWORD,
                RESTRequestParameterProcessingFilter.identifyLegacyAuthMethod(pw));

        MockHttpServletRequest ts = new MockHttpServletRequest();
        ts.setParameter("u", "alice");
        ts.setParameter("t", "abc");
        ts.setParameter("s", "salt");
        org.junit.jupiter.api.Assertions.assertEquals(
                RESTRequestParameterProcessingFilter.LEGACY_METHOD_SALTED_TOKEN,
                RESTRequestParameterProcessingFilter.identifyLegacyAuthMethod(ts));

        // t+s preferred when both present alongside p (matches attemptAuthentication's order)
        MockHttpServletRequest both = new MockHttpServletRequest();
        both.setParameter("u", "alice");
        both.setParameter("p", "secret");
        both.setParameter("t", "abc");
        both.setParameter("s", "salt");
        org.junit.jupiter.api.Assertions.assertEquals(
                RESTRequestParameterProcessingFilter.LEGACY_METHOD_SALTED_TOKEN,
                RESTRequestParameterProcessingFilter.identifyLegacyAuthMethod(both));
    }
}
