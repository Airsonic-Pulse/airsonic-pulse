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

 Copyright 2023 (C) Y.Tory
 */

package org.airsonic.player.service.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AdviceMode;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
@EnableAsync(mode = AdviceMode.ASPECTJ)
public class AsyncWebSocketClient {

    private final Logger LOG = LoggerFactory.getLogger(AsyncWebSocketClient.class);

    @Autowired
    private SimpMessagingTemplate brokerTemplate;

    @Async("BroadcastThreadPool")
    public void send(String destination, Object payload) {
        LOG.debug("Sending to {}: {}", destination, payload);
        brokerTemplate.convertAndSend(destination, payload);
    }

    @Async("BroadcastThreadPool")
    public CompletableFuture<Void> sendToUser(String user, String destination, Object payload) {
        if (user == null) {
            LOG.warn("Dropping message to {}: no user to address it to (#354)", destination);
            return CompletableFuture.completedFuture(null);
        }
        LOG.debug("Sending to user {}: {}: {}", user, destination, payload);
        brokerTemplate.convertAndSendToUser(user, destination, payload);
        return CompletableFuture.completedFuture(null);
    }

    @Async("BroadcastThreadPool")
    public CompletableFuture<Void> sendToUser(String user, String destination, Object payload, Map<String, Object> headers) {
        if (user == null) {
            LOG.warn("Dropping message to {}: no user to address it to (#354)", destination);
            return CompletableFuture.completedFuture(null);
        }
        LOG.debug("Sending to user {}: {}: {}", user, destination, payload);
        brokerTemplate.convertAndSendToUser(user, destination, payload, headers);
        return CompletableFuture.completedFuture(null);
    }
}
