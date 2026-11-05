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

import org.airsonic.player.domain.User;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.ServletWebRequest;

import java.security.Principal;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the download id handling across the commons-lang 2.x to lang3 migration (#180): lang3's
 * {@code isNumeric("")} is {@code false}, so an empty id parameter is filtered out and treated
 * as absent. Under 2.x the empty id passed the filter and {@code Integer.valueOf("")} threw an
 * uncaught {@link NumberFormatException}; the lang3 behaviour is kept deliberately.
 */
class SubsonicMediaControllerDownloadTest {

    private SubsonicMediaController controller;
    private DownloadController downloadController;

    @BeforeEach
    void setUp() throws Exception {
        this.controller = new SubsonicMediaController();
        this.controller.playerService = mock(PlayerService.class);
        this.downloadController = mock(DownloadController.class);
        SecurityService securityService = mock(SecurityService.class);
        ReflectionTestUtils.setField(this.controller, "downloadController", this.downloadController);
        ReflectionTestUtils.setField(this.controller, "securityService", securityService);

        User user = new User("alice", "alice@example.com");
        user.setRoles(Set.of(User.Role.DOWNLOAD));
        when(securityService.getUserByName("alice")).thenReturn(user);
        when(this.downloadController.handleRequest(any(), any(), any(), any(), any(), any()))
                .thenReturn(ResponseEntity.ok().build());
    }

    @SuppressWarnings("unchecked")
    private Optional<Integer> downloadedIdFor(String id) throws Exception {
        Principal principal = () -> "alice";
        ServletWebRequest swr = new ServletWebRequest(new MockHttpServletRequest(), new MockHttpServletResponse());
        this.controller.download(principal, id, null, null, null, swr);

        ArgumentCaptor<Optional<Integer>> idCaptor = ArgumentCaptor.forClass(Optional.class);
        verify(this.downloadController).handleRequest(any(), idCaptor.capture(), any(), any(), any(), any());
        return idCaptor.getValue();
    }

    @Test
    void downloadWithEmptyIdIsTreatedAsAbsentInsteadOfThrowing() throws Exception {
        assertTrue(downloadedIdFor("").isEmpty());
    }

    @Test
    void downloadWithNumericIdPassesItThrough() throws Exception {
        assertEquals(Optional.of(42), downloadedIdFor("42"));
    }
}
