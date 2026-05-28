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
import org.airsonic.player.controller.SubsonicRESTController.ErrorCode;
import org.airsonic.player.domain.ApiKey;
import org.airsonic.player.service.ApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the apiKey filter — fast, no Spring context. The downgrade-attack prevention
 * (apiKey + any legacy param → 43) is the security core; the rest enforces transport precedence
 * (Bearer wins), the absent-key pass-through (legacy stays untouched), and the failure mapping
 * (invalid key → 40).
 */
@ExtendWith(MockitoExtension.class)
class APIKeyRequestParameterProcessingFilterTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private ApiKeyService apiKeyService;
    @Mock
    private JAXBWriter jaxbWriter;

    private APIKeyRequestParameterProcessingFilter filter;

    @BeforeEach
    void setUp() {
        filter = new APIKeyRequestParameterProcessingFilter(authenticationManager, apiKeyService, jaxbWriter);
        SecurityContextHolder.clearContext();
    }

    private Authentication successfulAuth(ApiKey apiKey) {
        // Mirror what APIKeyAuthenticationProvider returns: an APIKeyAuthenticationToken whose
        // dedicated apiKey field carries the resolved entity (survives
        // ProviderManager.eraseCredentialsAfterAuthentication).
        return new APIKeyAuthenticationToken(
                "alice", "ap_raw", List.of(new SimpleGrantedAuthority("ROLE_USER")), apiKey);
    }

    private static ApiKey newKey() {
        ApiKey k = new ApiKey("alice", "deadbeef", "phone", Instant.now(), null);
        k.setId(7);
        return k;
    }

    @Test
    void doFilter_noApiKey_passesThroughUntouched() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotNull(chain.getRequest(), "chain.doFilter must be called when no apiKey is present");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(authenticationManager, apiKeyService, jaxbWriter);
    }

    @Test
    void doFilter_apiKeyQueryParam_authenticatesAndChains() throws Exception {
        ApiKey apiKey = newKey();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.setParameter("apiKey", "ap_alpha");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationManager.authenticate(any(APIKeyAuthenticationToken.class)))
                .thenReturn(successfulAuth(apiKey));

        filter.doFilter(req, res, chain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertNotNull(chain.getRequest());
        verify(apiKeyService).markUsed(eq(apiKey), eq(APIKeyRequestParameterProcessingFilter.LAST_USED_THROTTLE));
    }

    @Test
    void doFilter_authorizationBearer_isPreferredOverQueryParam() throws Exception {
        ApiKey apiKey = newKey();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.addHeader("Authorization", "Bearer ap_header_wins");
        req.setParameter("apiKey", "ap_query_loses");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        ArgumentCaptor<APIKeyAuthenticationToken> captor = ArgumentCaptor.forClass(APIKeyAuthenticationToken.class);
        when(authenticationManager.authenticate(captor.capture())).thenReturn(successfulAuth(apiKey));

        filter.doFilter(req, res, chain);

        assertEquals("ap_header_wins", captor.getValue().getCredentials(),
                "header value must win over the query parameter");
    }

    @Test
    void doFilter_bearerSchemeIsCaseInsensitive() throws Exception {
        ApiKey apiKey = newKey();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        // RFC 7235: auth scheme is case-insensitive.
        req.addHeader("Authorization", "bearer ap_lowercase");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        ArgumentCaptor<APIKeyAuthenticationToken> captor = ArgumentCaptor.forClass(APIKeyAuthenticationToken.class);
        when(authenticationManager.authenticate(captor.capture())).thenReturn(successfulAuth(apiKey));

        filter.doFilter(req, res, chain);

        assertEquals("ap_lowercase", captor.getValue().getCredentials());
    }

    @Test
    void doFilter_basicSchemeIsIgnoredByApiKeyFilter() throws Exception {
        // HTTP Basic must keep flowing to the existing BasicAuthenticationFilter — the apiKey
        // filter consumes only Bearer.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotNull(chain.getRequest());
        verifyNoInteractions(authenticationManager, apiKeyService, jaxbWriter);
    }

    @Test
    void doFilter_conflictWithUsernameParam_returns43AndDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.setParameter("apiKey", "ap_alpha");
        req.setParameter("u", "victim");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(jaxbWriter).writeErrorResponse(any(), any(),
                eq(ErrorCode.CONFLICTING_AUTH_PARAMS),
                eq(ErrorCode.CONFLICTING_AUTH_PARAMS.getMessage()));
        assertNull(chain.getRequest(), "the chain must be stopped on conflict");
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void doFilter_conflictWithPasswordParam_returns43() throws Exception {
        // The actual downgrade-attack shape — apiKey=guess + p=stolen would historically have
        // fallen through to legacy on an apiKey miss. This test locks the fail-fast.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.addHeader("Authorization", "Bearer ap_guess");
        req.setParameter("p", "stolen_password");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(jaxbWriter).writeErrorResponse(any(), any(),
                eq(ErrorCode.CONFLICTING_AUTH_PARAMS), any());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void doFilter_conflictWithSaltAndToken_returns43() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.setParameter("apiKey", "ap_alpha");
        req.setParameter("s", "salt");
        req.setParameter("t", "token");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verify(jaxbWriter).writeErrorResponse(any(), any(),
                eq(ErrorCode.CONFLICTING_AUTH_PARAMS), any());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void doFilter_apiKeyInBothHeaderAndQuery_isNotAConflict() throws Exception {
        // Same auth method (apiKey), two transports — header wins, no 43.
        ApiKey apiKey = newKey();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.addHeader("Authorization", "Bearer ap_header");
        req.setParameter("apiKey", "ap_query");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationManager.authenticate(any(APIKeyAuthenticationToken.class)))
                .thenReturn(successfulAuth(apiKey));

        filter.doFilter(req, res, chain);

        verify(jaxbWriter, never()).writeErrorResponse(any(), any(), any(), any());
        verify(authenticationManager, times(1)).authenticate(any());
    }

    @Test
    void doFilter_invalidKey_returns40() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.setParameter("apiKey", "ap_invalid");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationManager.authenticate(any(APIKeyAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid API key"));

        filter.doFilter(req, res, chain);

        verify(jaxbWriter).writeErrorResponse(any(), any(),
                eq(ErrorCode.NOT_AUTHENTICATED),
                eq(ErrorCode.NOT_AUTHENTICATED.getMessage()));
        assertNull(chain.getRequest(), "the chain must be stopped on failure so legacy doesn't run");
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_blankBearerToken_isTreatedAsNoApiKey() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.addHeader("Authorization", "Bearer    ");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotNull(chain.getRequest());
        verifyNoInteractions(authenticationManager, apiKeyService, jaxbWriter);
    }

    @Test
    void doFilter_markUsedFailureDoesNotBreakRequest() throws Exception {
        ApiKey apiKey = newKey();
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.setParameter("apiKey", "ap_alpha");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationManager.authenticate(any(APIKeyAuthenticationToken.class)))
                .thenReturn(successfulAuth(apiKey));
        org.mockito.Mockito.doThrow(new RuntimeException("DB write failed"))
                .when(apiKeyService).markUsed(any(), any());

        filter.doFilter(req, res, chain);

        // Request still completes — last_used is a courtesy timestamp, not a gate.
        assertNotNull(chain.getRequest());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void doFilter_emptyApiKeyQueryParam_passesThroughToLegacy() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.setParameter("apiKey", "");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertNotNull(chain.getRequest());
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void doFilter_validKeyTriggersLastUsedUpdate_thresholdRespected() throws Exception {
        // Stale entity: lastUsed older than the throttle → markUsed should perform the write.
        ApiKey apiKey = new ApiKey("alice", "h", "k", Instant.now(), null);
        apiKey.setId(7);
        apiKey.setLastUsed(Instant.now().minus(10, ChronoUnit.MINUTES));
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/rest/ping");
        req.setParameter("apiKey", "ap_x");
        MockHttpServletResponse res = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authenticationManager.authenticate(any(APIKeyAuthenticationToken.class)))
                .thenReturn(successfulAuth(apiKey));

        filter.doFilter(req, res, chain);

        // The decision (write vs throttle) lives inside ApiKeyService.markUsed; from the filter's
        // perspective we just verify markUsed was invoked with the same entity + throttle.
        verify(apiKeyService).markUsed(eq(apiKey), eq(APIKeyRequestParameterProcessingFilter.LAST_USED_THROTTLE));
    }

    @Test
    void lastUsedThrottle_isFiveMinutes() {
        // Lock the documented threshold so the constant can't quietly drift.
        assertTrue(APIKeyRequestParameterProcessingFilter.LAST_USED_THROTTLE.toMinutes() == 5);
    }
}
