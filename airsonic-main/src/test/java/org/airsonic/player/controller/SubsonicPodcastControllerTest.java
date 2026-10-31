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

import org.airsonic.player.domain.Player;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.subsonic.restapi.Response;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the count clamp on getNewestPodcasts (#286, mirroring the #262 shape). The
 * allocation is bounded by the episode table, but a negative count would crash the subList in
 * {@link PodcastPersistenceService#getNewestEpisodes}; the clamp's zero floor guards it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubsonicPodcastControllerTest {

    @Mock
    private PodcastPersistenceService podcastPersistenceService;
    @Mock
    private SecurityService securityService;
    @Mock
    private JaxbContentService jaxbContentService;
    @Mock
    private PlayerService playerService;
    @Mock
    private JAXBWriter jaxbWriter;

    private SubsonicPodcastController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new SubsonicPodcastController();
        ReflectionTestUtils.setField(controller, "podcastPersistenceService", podcastPersistenceService);
        ReflectionTestUtils.setField(controller, "securityService", securityService);
        ReflectionTestUtils.setField(controller, "jaxbContentService", jaxbContentService);
        ReflectionTestUtils.setField(controller, "playerService", playerService);
        ReflectionTestUtils.setField(controller, "jaxbWriter", jaxbWriter);

        when(securityService.getCurrentUsername(any())).thenReturn("alice");
        when(playerService.getPlayersForUserAndClientId(any(), any())).thenReturn(List.of(new Player()));
        when(playerService.getPlayer(any(), any(), anyString())).thenReturn(new Player());
        when(jaxbWriter.createResponse(anyBoolean())).thenReturn(new Response());
        when(podcastPersistenceService.getNewestEpisodes(anyInt())).thenReturn(List.of());
    }

    private MockHttpServletRequest req() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteUser("alice");
        request.setParameter("c", "test");
        return request;
    }

    @Test
    void getNewestPodcasts_clampsAbusiveCount() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("count", String.valueOf(Integer.MAX_VALUE));

        controller.getNewestPodcasts(request, new MockHttpServletResponse());

        verify(podcastPersistenceService).getNewestEpisodes(eq(500));
    }

    @Test
    void getNewestPodcasts_passesNormalCountThrough() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("count", "30");

        controller.getNewestPodcasts(request, new MockHttpServletResponse());

        verify(podcastPersistenceService).getNewestEpisodes(eq(30));
    }

    @Test
    void getNewestPodcasts_clampsNegativeCountToZero() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("count", "-7");

        controller.getNewestPodcasts(request, new MockHttpServletResponse());

        verify(podcastPersistenceService).getNewestEpisodes(eq(0));
    }
}
