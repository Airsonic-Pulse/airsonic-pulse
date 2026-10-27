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
package org.airsonic.player.service.websocket;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class AsyncWebSocketClientTest {

    // @Async is woven at compile time (spring-aspects aspectLibrary); outside a Spring context
    // the aspect needs a BeanFactory to resolve the qualified "BroadcastThreadPool" executor.
    // A synchronous executor keeps assertions on the test thread.
    @BeforeAll
    public static void configureWovenAsyncAspect() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("BroadcastThreadPool", new SyncTaskExecutor());
        AnnotationAsyncExecutionAspect.aspectOf().setBeanFactory(beanFactory);
    }

    @Mock
    private SimpMessagingTemplate brokerTemplate;

    @InjectMocks
    private AsyncWebSocketClient client;

    // the real SimpMessagingTemplate throws "User must not be null" on a null user (#354);
    // mirror that on the mock so the guard, not mock leniency, is what these tests prove
    private void stubTemplateToRejectNullUser() {
        lenient().doThrow(new IllegalArgumentException("User must not be null"))
                .when(brokerTemplate).convertAndSendToUser(isNull(), anyString(), any());
        lenient().doThrow(new IllegalArgumentException("User must not be null"))
                .when(brokerTemplate).convertAndSendToUser(isNull(), anyString(), any(), anyMap());
    }

    @Test
    public void sendToUserWithNullUserDropsMessageWithoutBrokerCall() {
        stubTemplateToRejectNullUser();

        CompletableFuture<Void> result = assertDoesNotThrow(
                () -> client.sendToUser(null, "/queue/playqueues/1/updated", Map.of()));

        assertTrue(result.isDone());
        verifyNoInteractions(brokerTemplate);
    }

    @Test
    public void sendToUserWithHeadersAndNullUserDropsMessageWithoutBrokerCall() {
        stubTemplateToRejectNullUser();

        CompletableFuture<Void> result = assertDoesNotThrow(
                () -> client.sendToUser(null, "/queue/playqueues/1/skip", Map.of(), Map.of()));

        assertTrue(result.isDone());
        verifyNoInteractions(brokerTemplate);
    }

    @Test
    public void sendToUserWithUserDelegatesToBroker() {
        Map<String, Object> payload = Map.of("index", 1);

        CompletableFuture<Void> result = client.sendToUser("alice", "/queue/playqueues/1/updated", payload);

        assertTrue(result.isDone());
        verify(brokerTemplate).convertAndSendToUser("alice", "/queue/playqueues/1/updated", payload);
    }

    @Test
    public void sendToUserWithHeadersAndUserDelegatesToBroker() {
        Map<String, Object> payload = Map.of("index", 1);
        Map<String, Object> headers = Map.of("simpSessionId", "s1");

        CompletableFuture<Void> result = client.sendToUser("alice", "/queue/playqueues/1/skip", payload, headers);

        assertTrue(result.isDone());
        verify(brokerTemplate).convertAndSendToUser("alice", "/queue/playqueues/1/skip", payload, headers);
    }
}
