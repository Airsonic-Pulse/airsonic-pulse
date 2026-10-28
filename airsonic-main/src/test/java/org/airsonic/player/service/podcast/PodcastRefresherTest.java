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
package org.airsonic.player.service.podcast;

import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.repository.CoverArtRepository;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.VersionService;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PodcastRefresherTest {

    // @Async is woven at compile time (spring-aspects aspectLibrary); outside a Spring context
    // the aspect needs a BeanFactory to resolve the qualified "PodcastRefreshThreadPool" executor.
    // A synchronous executor keeps assertions on the test thread.
    @BeforeAll
    public static void configureWovenAsyncAspect() {
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("PodcastRefreshThreadPool", new SyncTaskExecutor());
        AnnotationAsyncExecutionAspect.aspectOf().setBeanFactory(beanFactory);
    }

    @Mock
    private PodcastPersistenceService podcastPersistenceService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private VersionService versionService;

    @Mock
    private CoverArtRepository coverArtRepository;

    @Mock
    private AsyncWebSocketClient asyncWebSocketClient;

    @InjectMocks
    private PodcastRefresher podcastRefresher;

    private String rssFeed(int episodeCount) {
        Instant base = Instant.parse("2026-01-01T00:00:00Z");
        StringBuilder feed = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<rss version=\"2.0\"><channel><title>chan</title>");
        for (int i = 0; i < episodeCount; i++) {
            String pubDate = DateTimeFormatter.RFC_1123_DATE_TIME
                    .format(ZonedDateTime.ofInstant(base.plusSeconds(i * 60L), ZoneOffset.UTC));
            feed.append("<item>")
                .append("<title>Episode ").append(i).append("</title>")
                .append("<guid>guid-").append(i).append("</guid>")
                .append("<pubDate>").append(pubDate).append("</pubDate>")
                .append("<description>desc ").append(i).append("</description>")
                .append("<enclosure url=\"https://feed.example.org/ep-").append(i)
                .append(".mp3\" length=\"1000\" type=\"audio/mpeg\"/>")
                .append("</item>");
        }
        return feed.append("</channel></rss>").toString();
    }

    @Test
    void refreshRunsEpisodePersistenceOnCallingThread() throws Exception {
        // #353: the episode loop used parallelStream(), so createEpisode/updateEpisode ran on
        // ForkJoin common-pool workers, each opening its own transaction while the detached
        // channel and episode entities were shared across threads (same class of defect as the
        // #345 delete loop). The refresh itself is already async per channel, so nothing waits
        // on this loop; thread identity is the deterministic proxy for the fix, mirroring #345.
        int episodeCount = 64;
        PodcastChannel channel = new PodcastChannel(1, "https://feed.example.org/rss", "chan",
                null, null, PodcastStatus.NEW, null, null);
        when(podcastPersistenceService.prepareRefreshChannel(1)).thenReturn(channel);
        when(settingsService.getPodcastEpisodeDownloadCount()).thenReturn(10);

        List<String> persistenceThreads = Collections.synchronizedList(new ArrayList<>());
        when(podcastPersistenceService.createEpisode(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    persistenceThreads.add(Thread.currentThread().getName());
                    // brief pause so a parallel stream provably distributes work across pool workers
                    Thread.sleep(1);
                    return new PodcastEpisode(null, invocation.getArgument(0), invocation.getArgument(1),
                            invocation.getArgument(2), null, invocation.getArgument(3), invocation.getArgument(4),
                            invocation.getArgument(5), invocation.getArgument(6), invocation.getArgument(7),
                            0L, PodcastStatus.NEW, null);
                });
        when(podcastPersistenceService.updateEpisode(any())).thenAnswer(invocation -> {
            persistenceThreads.add(Thread.currentThread().getName());
            return invocation.getArgument(0);
        });

        HttpEntity feedEntity = new ByteArrayEntity(rssFeed(episodeCount).getBytes(StandardCharsets.UTF_8));
        CloseableHttpClient mockedHttpClient = Mockito.mock(CloseableHttpClient.class);
        CloseableHttpResponse mockedHttpResponse = Mockito.mock(CloseableHttpResponse.class);
        boolean refreshed;
        try (MockedStatic<HttpClients> mockedHttpClients = Mockito.mockStatic(HttpClients.class,
                Mockito.CALLS_REAL_METHODS)) {
            mockedHttpClients.when(() -> HttpClients.createDefault()).thenReturn(mockedHttpClient);
            when(mockedHttpClient.execute(any(HttpGet.class))).thenReturn(mockedHttpResponse);
            when(mockedHttpResponse.getEntity()).thenReturn(feedEntity);
            refreshed = podcastRefresher.refresh(1, false).get();
        }

        assertTrue(refreshed);
        verify(podcastPersistenceService).setChannelCompleted(channel);
        // one createEpisode plus one updateEpisode (status pass) per feed item
        assertEquals(2 * episodeCount, persistenceThreads.size());
        String callingThread = Thread.currentThread().getName();
        List<String> foreignThreads = persistenceThreads.stream()
                .filter(name -> !name.equals(callingThread)).distinct().toList();
        assertTrue(foreignThreads.isEmpty(),
                "episode entity writes must run on the refresh thread, not shared across workers; ran on: "
                        + foreignThreads);
    }
}
