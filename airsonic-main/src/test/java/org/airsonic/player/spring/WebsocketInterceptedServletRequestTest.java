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
package org.airsonic.player.spring;

import org.airsonic.player.spring.WebsocketConfiguration.WebsocketInterceptedServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WebsocketInterceptedServletRequestTest {

    // #354: the client address must be snapshotted while the handshake request is live — the
    // container recycles the underlying request before STOMP messages arrive, and the old
    // URI-host fallback then stored the server's own hostname as the player's ip_address
    @Test
    public void remoteAddressIsSnapshottedAtConstruction() {
        MockHttpServletRequest handshakeRequest = new MockHttpServletRequest();
        handshakeRequest.setRemoteAddr("203.0.113.7");
        handshakeRequest.setRemoteHost("client.example.org");

        WebsocketInterceptedServletRequest wrapper = new WebsocketInterceptedServletRequest(
                new ServletServerHttpRequest(handshakeRequest), "/");

        // simulate the container recycling the underlying request after the handshake
        handshakeRequest.setRemoteAddr("198.51.100.99");
        handshakeRequest.setRemoteHost("recycled.example.org");

        assertEquals("203.0.113.7", wrapper.getRemoteAddr());
        assertEquals("client.example.org", wrapper.getRemoteHost());
    }
}
