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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.security;

import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.controller.SubsonicRESTController;
import org.airsonic.player.controller.SubsonicRESTController.APIException;
import org.airsonic.player.controller.SubsonicRESTController.ErrorCode;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.Version;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.cache.LegacyAuthWarningCache;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.util.matcher.AndRequestMatcher;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.NegatedRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Performs authentication based on credentials being present in the HTTP request parameters. Also checks
 * API versions and license information.
 * <p/>
 * The username should be set in parameter "u", and the password should be set in parameter "p".
 * The REST protocol version should be set in parameter "v".
 * <p/>
 * The password can either be in plain text or be UTF-8 hexencoded preceded by "enc:".
 *
 * @author Sindre Mehus
 */
public class RESTRequestParameterProcessingFilter extends AbstractAuthenticationProcessingFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RESTRequestParameterProcessingFilter.class);

    private static final RequestMatcher requiresAuthenticationRequestMatcher = new AndRequestMatcher(
        new RegexRequestMatcher("/rest/.+", null),
        new NegatedRequestMatcher(new AntPathRequestMatcher("/rest/getOpenSubsonicExtensions*"))
    );
    private static final Version serverVersion = new Version(JAXBWriter.getRestProtocolVersion());

    static final String LEGACY_METHOD_PASSWORD = "legacy username/password";
    static final String LEGACY_METHOD_SALTED_TOKEN = "legacy token+salt";

    private LegacyAuthWarningCache legacyAuthWarningCache;
    private SecurityService securityService;

    protected RESTRequestParameterProcessingFilter(RequestMatcher requiresAuthenticationRequestMatcher, JAXBWriter jaxbWriter) {
        super(requiresAuthenticationRequestMatcher);
        setAuthenticationFailureHandler(new RESTAuthenticationFailureHandler(jaxbWriter));
        setAuthenticationSuccessHandler((req, res, auth) -> {
        });
    }

    public RESTRequestParameterProcessingFilter(JAXBWriter jaxbWriter) {
        this(requiresAuthenticationRequestMatcher, jaxbWriter);
    }

    /**
     * Wire the deprecation-warning throttle. Optional — when unset, the post-auth hook
     * silently skips the warning step (used by tests that exercise only the auth path).
     */
    public void setLegacyAuthWarningCache(LegacyAuthWarningCache legacyAuthWarningCache) {
        this.legacyAuthWarningCache = legacyAuthWarningCache;
    }

    /**
     * Wire the per-user legacy-auth gate (#233). Optional — when unset, the gate is skipped and
     * legacy auth behaves as before (used by tests that exercise only the raw auth path). When
     * set, a successful legacy {@code u/p} or {@code t/s} authentication is rejected if the
     * resolved user has opted out of password auth ({@code password_auth_enabled = false}).
     */
    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException, ServletException {
        String username = StringUtils.trimToNull(request.getParameter("u"));
        String password = decrypt(StringUtils.trimToNull(request.getParameter("p")));
        String salt = StringUtils.trimToNull(request.getParameter("s"));
        String token = StringUtils.trimToNull(request.getParameter("t"));
        String version = StringUtils.trimToNull(request.getParameter("v"));
        String client = StringUtils.trimToNull(request.getParameter("c"));

        // The username and credentials parameters are not required if the user
        // was previously authenticated, for example using Basic Auth.
        Authentication previousAuth = SecurityContextHolder.getContext().getAuthentication();
        if (previousAuth != null && previousAuth.isAuthenticated()) {
            return previousAuth;
        }

        boolean passwordOrTokenPresent = password != null || (salt != null && token != null);
        boolean missingCredentials = (username == null || !passwordOrTokenPresent);
        if (missingCredentials || version == null || client == null) {
            throw new AuthenticationServiceException("", new APIException(ErrorCode.MISSING_PARAMETER));
        }

        checkAPIVersion(version);

        UsernamePasswordAuthenticationToken authRequest = null;
        if (salt != null && token != null) {
            authRequest = new UsernameSaltedTokenAuthenticationToken(username, salt, token);
        } else if (password != null) {
            authRequest = new UsernamePasswordAuthenticationToken(username, password);
        } else {
            throw new AuthenticationServiceException("", new APIException(ErrorCode.MISSING_PARAMETER));
        }

        authRequest.setDetails(authenticationDetailsSource.buildDetails(request));

        Authentication result = this.getAuthenticationManager().authenticate(authRequest);
        rejectIfPasswordAuthDisabled(result);
        return result;
    }

    /**
     * Per-user legacy-auth gate (#233). Reached only for genuine legacy {@code u/p} or {@code t/s}
     * attempts: an apiKey- or Basic-pre-authenticated request returns early via the
     * {@code previousAuth} short-circuit in {@link #attemptAuthentication} and never builds an
     * {@code authRequest}, so it never reaches here. Form-login / session auth uses a different
     * filter entirely. The check runs <em>after</em> the credentials have been validated, so it
     * never reveals the flag to a caller without valid credentials (no enumeration oracle). When
     * the resolved user has disabled password auth, the attempt is rejected with the same generic
     * {@code PASSWORD_AUTH_NOT_SUPPORTED} envelope the apiKey work (#145) already defines.
     */
    private void rejectIfPasswordAuthDisabled(Authentication result) {
        if (this.securityService == null || result == null) {
            return;
        }
        String username = StringUtils.trimToNull(result.getName());
        if (username == null) {
            return;
        }
        // Fail open on an unresolved user: the principal already passed credential validation, so
        // a null lookup here (effectively impossible) must not gate auth. Do NOT tighten this into
        // a reject — that would turn a transient lookup miss into an account lockout.
        User user = this.securityService.getUserByName(username);
        if (user != null && !user.isPasswordAuthEnabled()) {
            LOG.debug("Rejected legacy password/token auth for user {}: password auth disabled for this account", username);
            throw new AuthenticationServiceException("", new APIException(ErrorCode.PASSWORD_AUTH_NOT_SUPPORTED));
        }
    }

    private void checkAPIVersion(String version) {
        Version clientVersion = new Version(version);

        try {
            if (serverVersion.getMajor() > clientVersion.getMajor()) {
                throw new APIException(ErrorCode.PROTOCOL_MISMATCH_CLIENT_TOO_OLD);
            } else if (serverVersion.getMajor() < clientVersion.getMajor()) {
                throw new APIException(ErrorCode.PROTOCOL_MISMATCH_SERVER_TOO_OLD);
            } else if (serverVersion.getMinor() < clientVersion.getMinor()) {
                throw new APIException(ErrorCode.PROTOCOL_MISMATCH_SERVER_TOO_OLD);
            }
        } catch (APIException e) {
            throw new AuthenticationServiceException("", e);
        }
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain chain,
            Authentication authResult) throws IOException, ServletException {
        super.successfulAuthentication(request, response, chain, authResult);
        maybeWarnLegacyAuth(request, authResult);
        // carry on with the request
        chain.doFilter(request, response);
    }

    /**
     * Identify legacy {@code u/p} or {@code t/s} use by inspecting the request parameters
     * directly. The token class is not a reliable signal: this method also runs when
     * {@link #attemptAuthentication} short-circuits to a previously-authenticated context
     * (apiKey or HTTP Basic), in which case {@code authResult} is whatever token the prior
     * filter installed — not a legacy token. The legacy-Subsonic params, however, are only
     * present when the caller is actually using {@code u/p} or {@code t/s}.
     */
    void maybeWarnLegacyAuth(HttpServletRequest request, Authentication authResult) {
        if (legacyAuthWarningCache == null || authResult == null) {
            return;
        }
        String method = identifyLegacyAuthMethod(request);
        if (method == null) {
            return;
        }
        String username = StringUtils.trimToNull(authResult.getName());
        if (username == null) {
            return;
        }
        String client = StringUtils.trimToNull(request.getParameter("c"));
        if (client == null) {
            return;
        }
        legacyAuthWarningCache.warnIfFirstSeen(username, client, method);
    }

    static String identifyLegacyAuthMethod(HttpServletRequest request) {
        if (StringUtils.trimToNull(request.getParameter("u")) == null) {
            return null;
        }
        String t = StringUtils.trimToNull(request.getParameter("t"));
        String s = StringUtils.trimToNull(request.getParameter("s"));
        if (t != null && s != null) {
            return LEGACY_METHOD_SALTED_TOKEN;
        }
        if (StringUtils.trimToNull(request.getParameter("p")) != null) {
            return LEGACY_METHOD_PASSWORD;
        }
        return null;
    }

    public static String decrypt(String s) {
        if (s == null) {
            return null;
        }
        if (!s.startsWith("enc:")) {
            return s;
        }
        try {
            return StringUtil.utf8HexDecode(s.substring(4));
        } catch (Exception e) {
            return s;
        }
    }

    public static class RESTAuthenticationFailureHandler implements AuthenticationFailureHandler {
        private final JAXBWriter jaxbWriter;

        public RESTAuthenticationFailureHandler(JAXBWriter jaxbWriter) {
            this.jaxbWriter = jaxbWriter;
        }

        @Override
        public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                AuthenticationException exception) throws IOException, ServletException {
            ErrorCode errorCode = null;
            if (exception.getCause() instanceof APIException) {
                errorCode = ((APIException) exception.getCause()).getError();
            } else {
                errorCode = ErrorCode.NOT_AUTHENTICATED;
            }

            sendErrorXml(request, response, errorCode);
        }

        private void sendErrorXml(HttpServletRequest request, HttpServletResponse response,
                SubsonicRESTController.ErrorCode errorCode) {
            try {
                jaxbWriter.writeErrorResponse(request, response, errorCode, errorCode.getMessage());
            } catch (Exception e) {
                LOG.error("Failed to send error response.", e);
            }
        }
    }
}
