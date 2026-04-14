package org.airsonic.player.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

@Configuration
public class ThreadPoolConfig {

    @Bean(name = "BroadcastThreadPool")
    public SimpleAsyncTaskExecutor configThreadPool() {
        var executor = new SimpleAsyncTaskExecutor("BroadcastThread-");
        executor.setVirtualThreads(true);
        return executor;
    }

    @Bean(name = "PodcastDownloadThreadPool")
    public SimpleAsyncTaskExecutor podcastDownloadThreadPool() {
        var executor = new SimpleAsyncTaskExecutor("podcast-download-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(3);
        return executor;
    }

    @Bean(name = "PodcastRefreshThreadPool")
    public SimpleAsyncTaskExecutor podcastRefreshThreadPool() {
        var executor = new SimpleAsyncTaskExecutor("podcast-refresh-");
        executor.setVirtualThreads(true);
        executor.setConcurrencyLimit(5);
        return executor;
    }
}
