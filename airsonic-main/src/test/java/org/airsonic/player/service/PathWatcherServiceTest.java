package org.airsonic.player.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PathWatcherServiceTest {

    private static final BiConsumer<Path, WatchEvent<Path>> NOOP = (path, event) -> {
    };

    @TempDir
    private Path firstDir;

    @TempDir
    private Path secondDir;

    private PathWatcherService pathWatcherService;

    @BeforeEach
    public void setup() throws Exception {
        pathWatcherService = new PathWatcherService();
    }

    @AfterEach
    public void cleanup() throws Exception {
        ((WatchService) ReflectionTestUtils.getField(pathWatcherService, "watchService")).close();
        ((ExecutorService) ReflectionTestUtils.getField(pathWatcherService, "watcherThread")).shutdownNow();
        ((ExecutorService) ReflectionTestUtils.getField(pathWatcherService, "watcherFunctionThreadPool")).shutdownNow();
    }

    @SuppressWarnings("unchecked")
    private Map<String, WatchKey> watchNames() {
        return (Map<String, WatchKey>) ReflectionTestUtils.getField(pathWatcherService, "watchNames");
    }

    @SuppressWarnings("unchecked")
    private Map<WatchKey, ?> watchFunctions() {
        return (Map<WatchKey, ?>) ReflectionTestUtils.getField(pathWatcherService, "watchFunctions");
    }

    @Test
    public void testRemoveWatcherCancelsKeyAndForgetsName() throws Exception {
        pathWatcherService.setWatcher("watcher", firstDir, NOOP, null, NOOP, null);
        WatchKey key = watchNames().get("watcher");
        assertTrue(key.isValid());

        pathWatcherService.removeWatcher("watcher");

        assertFalse(key.isValid());
        assertTrue(watchNames().isEmpty());
        assertTrue(watchFunctions().isEmpty());
    }

    @Test
    public void testRemoveWatcherWithUnknownNameIsNoOp() {
        assertDoesNotThrow(() -> pathWatcherService.removeWatcher("never registered"));
        assertTrue(watchNames().isEmpty());
    }

    @Test
    public void testSetWatcherTwiceUnderOneNameLeavesOneLiveRegistration() throws Exception {
        pathWatcherService.setWatcher("watcher", firstDir, NOOP, null, NOOP, null);
        WatchKey firstKey = watchNames().get("watcher");

        pathWatcherService.setWatcher("watcher", secondDir, NOOP, null, NOOP, null);
        WatchKey secondKey = watchNames().get("watcher");

        assertFalse(firstKey.isValid());
        assertTrue(secondKey.isValid());
        assertEquals(secondDir, secondKey.watchable());
        assertEquals(1, watchNames().size());
        assertEquals(1, watchFunctions().size());
        assertTrue(watchFunctions().containsKey(secondKey));
    }
}
