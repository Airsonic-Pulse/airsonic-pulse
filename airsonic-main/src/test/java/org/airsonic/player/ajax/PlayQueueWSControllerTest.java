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
package org.airsonic.player.ajax;

import org.airsonic.player.domain.Player;
import org.airsonic.player.service.PlayQueueService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.spring.WebsocketConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.mock.web.MockHttpServletRequest;

import jakarta.servlet.http.HttpSession;

import java.security.Principal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlayQueueWSControllerTest {

    @Mock
    private PlayerService playerService;

    @Mock
    private PlayQueueService playQueueService;

    @InjectMocks
    private PlayQueueWSController controller;

    @Mock
    private HttpSession session;

    // same shape the handshake interceptor stashes; see PlayQueueService.postBroadcast for the
    // accessor-construction pattern
    private SimpMessageHeaderAccessor headers(Principal user) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(WebsocketConfiguration.UNDERLYING_SERVLET_REQUEST, new MockHttpServletRequest());
        attributes.put(WebsocketConfiguration.USER_AGENT, "test-agent");
        attributes.put(WebsocketConfiguration.UNDERLYING_HTTP_SESSION, session);
        headers.setSessionAttributes(attributes);
        if (user != null) {
            headers.setUser(user);
        }
        return headers;
    }

    // #354: the player must be resolved under the STOMP session's principal, not a
    // SecurityContext lookup that is structurally null on STOMP threads
    @Test
    public void handlerResolvesPlayerUnderPrincipalName() throws Exception {
        Player player = new Player();
        player.setId(5);
        when(playerService.getPlayer(any(), isNull(), eq(5), any(), any(), eq(true), eq(false), eq(true)))
                .thenReturn(player);

        controller.start(5, headers(() -> "alice"));

        ArgumentCaptor<String> username = ArgumentCaptor.forClass(String.class);
        verify(playerService).getPlayer(any(), isNull(), eq(5), username.capture(), any(), eq(true), eq(false), eq(true));
        assertEquals("alice", username.getValue());
        verify(playQueueService).start(player);
        verify(session).setAttribute("player", 5);
    }

    @Test
    public void handlerWithoutPrincipalMintsNoPlayerAndNoOps() throws Exception {
        assertNull(controller.getPlayQueue(5, headers(null)));
        controller.start(5, headers(null));

        verifyNoInteractions(playerService);
        verifyNoInteractions(playQueueService);
    }
}
