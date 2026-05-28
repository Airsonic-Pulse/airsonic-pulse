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
import org.airsonic.player.service.ApiKeyService;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;

/**
 * OpenSubsonic {@code apiKeyAuthentication} extension — extracts the apiKey from either the
 * {@code Authorization: Bearer} header (preferred) or the {@code apiKey} query parameter,
 * authenticates it via {@link APIKeyAuthenticationProvider}, and writes the result into the
 * {@link SecurityContextHolder}. Composes ahead of the legacy
 * {@code RESTRequestParameterProcessingFilter} via {@code addFilterBefore} in chain 2 —
 * a successful apiKey context makes the legacy filter's already-authenticated short-circuit
 * trigger so the legacy {@code u/p/t/s} reads are skipped untouched.
 * <p>
 * <b>Downgrade-attack prevention.</b> If an apiKey is presented alongside any legacy auth
 * parameter ({@code u}/{@code p}/{@code t}/{@code s}), the filter responds with error
 * {@link ErrorCode#CONFLICTING_AUTH_PARAMS} (43) and stops the chain. This prevents
 * {@code apiKey=guess&u=victim&p=stolen} from falling through to legacy auth on an apiKey miss.
 * Both transports of the apiKey (Bearer header AND query parameter) is NOT a conflict — they're
 * the same auth method, header wins.
 * <p>
 * The filter only consumes {@code Authorization: Bearer}; the existing
 * {@link org.springframework.security.web.authentication.www.BasicAuthenticationFilter} keeps
 * handling {@code Authorization: Basic} untouched.
 */
public class APIKeyRequestParameterProcessingFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger(APIKeyRequestParameterProcessingFilter.class);

    public static final String API_KEY_PARAM = "apiKey";
    public static final String BEARER_PREFIX = "Bearer ";

    /** Throttle for the {@code last_used} write — bounded DB writes on the auth hot path. */
    static final Duration LAST_USED_THROTTLE = Duration.ofMinutes(5);

    private static final String[] LEGACY_AUTH_PARAMS = {"u", "p", "t", "s"};

    private final AuthenticationManager authenticationManager;
    private final ApiKeyService apiKeyService;
    private final JAXBWriter jaxbWriter;

    public APIKeyRequestParameterProcessingFilter(AuthenticationManager authenticationManager,
            ApiKeyService apiKeyService, JAXBWriter jaxbWriter) {
        this.authenticationManager = authenticationManager;
        this.apiKeyService = apiKeyService;
        this.jaxbWriter = jaxbWriter;
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) resp;

        String rawKey = extractApiKey(request);
        if (rawKey == null) {
            // No apiKey presented — defer to the legacy REST filter (u/p/t/s) and HTTP Basic
            // exactly as before. Filter is purely additive.
            chain.doFilter(req, resp);
            return;
        }

        if (hasAnyLegacyAuthParam(request)) {
            // Downgrade-attack prevention: any of u/p/t/s alongside apiKey → fail-fast.
            // Without this, apiKey=guess&u=victim&p=stolen would resolve-miss on apiKey then
            // fall through to legacy and authenticate via the stolen password.
            jaxbWriter.writeErrorResponse(request, response,
                    ErrorCode.CONFLICTING_AUTH_PARAMS, ErrorCode.CONFLICTING_AUTH_PARAMS.getMessage());
            return;
        }

        Authentication authResult;
        try {
            authResult = authenticationManager.authenticate(new APIKeyAuthenticationToken(null, rawKey));
        } catch (AuthenticationException failed) {
            // No raw key, no key_hash, no internal state in the response. Same error code 40
            // legacy auth produces so clients see a uniform "wrong credentials" outcome.
            SecurityContextHolder.clearContext();
            jaxbWriter.writeErrorResponse(request, response,
                    ErrorCode.NOT_AUTHENTICATED, ErrorCode.NOT_AUTHENTICATED.getMessage());
            return;
        }

        SecurityContextHolder.getContext().setAuthentication(authResult);
        if (authResult instanceof APIKeyAuthenticationToken apiKeyAuth && apiKeyAuth.getApiKey() != null) {
            try {
                apiKeyService.markUsed(apiKeyAuth.getApiKey(), LAST_USED_THROTTLE);
            } catch (Exception x) {
                // last_used is a courtesy timestamp; a write failure must not break the request.
                LOG.warn("Failed to update last_used for API key", x);
            }
        }
        chain.doFilter(request, response);
    }

    /**
     * Bearer header wins; falls back to the {@code apiKey} query parameter. Both forms are the
     * same auth method (apiKey), so presenting both is not a conflict — header simply takes
     * precedence. Returns {@code null} when neither is present.
     */
    private static String extractApiKey(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String candidate = StringUtils.trimToNull(auth.substring(BEARER_PREFIX.length()));
            if (candidate != null) {
                return candidate;
            }
        }
        return StringUtils.trimToNull(request.getParameter(API_KEY_PARAM));
    }

    private static boolean hasAnyLegacyAuthParam(HttpServletRequest request) {
        for (String param : LEGACY_AUTH_PARAMS) {
            if (StringUtils.trimToNull(request.getParameter(param)) != null) {
                return true;
            }
        }
        return false;
    }
}
