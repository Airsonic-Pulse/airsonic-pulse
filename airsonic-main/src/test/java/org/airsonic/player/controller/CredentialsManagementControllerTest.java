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
package org.airsonic.player.controller;

import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import java.security.Principal;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for the self-service {@code password_auth_enabled} toggle endpoint (#278). The endpoint
 * takes the username from the authenticated {@link Principal} and has no username request parameter,
 * so a user can only ever change their own flag (no IDOR). The {@code @RequestParam(defaultValue =
 * "false")} binding turns an unchecked checkbox (which submits nothing) into {@code false}, so
 * disabling persists rather than silently no-opping.
 */
@ExtendWith(MockitoExtension.class)
class CredentialsManagementControllerTest {

    @Mock
    private SecurityService securityService;

    @InjectMocks
    private CredentialsManagementController controller;

    @Test
    void updatePasswordAuth_disablesForThePrincipalOnly() {
        Principal alice = () -> "alice";

        controller.updatePasswordAuth(alice, false, new RedirectAttributesModelMap());

        // Username is the principal's own name, never a request parameter — no IDOR is possible,
        // and the unchecked checkbox (enabled=false) is persisted.
        verify(securityService).updatePasswordAuthEnabled("alice", false);
    }

    @Test
    void updatePasswordAuth_enablesForThePrincipal() {
        Principal alice = () -> "alice";

        controller.updatePasswordAuth(alice, true, new RedirectAttributesModelMap());

        verify(securityService).updatePasswordAuthEnabled("alice", true);
    }

    @Test
    void updatePasswordAuth_aForgedUsernameCannotTargetAnotherUser() {
        // The endpoint signature exposes no username binding; even if a caller forges one in the
        // form body it is ignored — the write always targets the principal's own account.
        Principal bob = () -> "bob";

        controller.updatePasswordAuth(bob, false, new RedirectAttributesModelMap());

        verify(securityService).updatePasswordAuthEnabled("bob", false);
    }
}
