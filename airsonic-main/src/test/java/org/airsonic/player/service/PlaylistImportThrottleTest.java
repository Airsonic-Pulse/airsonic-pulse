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
package org.airsonic.player.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavior test for {@link PlaylistImportThrottle} (issue #215). Drives the throttle
 * with a small debounce window and a tight bound so the asserts run in real time but
 * stay well under any CI timeout. Uses real virtual threads and a real
 * {@link ScheduledExecutorService} so what gets tested matches what runs in production.
 */
class PlaylistImportThrottleTest {

    private static final long DEBOUNCE_MS = 100L;
    private static final int MAX_IN_FLIGHT = 3;
    // Headroom for the scheduler thread + virtual-thread start + semaphore acquire under
    // a loaded CI runner. The debounce itself is DEBOUNCE_MS; the wall-clock budget for
    // a single submit to complete is DEBOUNCE_MS + this constant.
    private static final long DELIVERY_HEADROOM_MS = 1500L;

    private ScheduledExecutorService scheduler;
    private PlaylistImportThrottle throttle;

    @BeforeEach
    void setUp() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "throttle-test-debounce");
            t.setDaemon(true);
            return t;
        });
        this.throttle = new PlaylistImportThrottle(DEBOUNCE_MS, MAX_IN_FLIGHT, this.scheduler);
    }

    @AfterEach
    void tearDown() {
        this.throttle.shutdown();
    }

    @Test
    void multipleEventsForSamePath_coalesceIntoOneTask() throws Exception {
        Path path = Paths.get("/tmp/playlist-1.m3u");
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(1);

        // Fire 20 events for the same path inside the debounce window. Each subsequent
        // submit must cancel the prior scheduled future and replace it; only the last
        // one survives to fire.
        for (int i = 0; i < 20; i++) {
            this.throttle.submit(path, () -> {
                runs.incrementAndGet();
                done.countDown();
            });
        }

        assertTrue(done.await(DEBOUNCE_MS + DELIVERY_HEADROOM_MS, TimeUnit.MILLISECONDS),
                "Coalesced task should have run within the debounce window + headroom");
        // Give any rogue extra firings a fair chance to land before asserting the count.
        Thread.sleep(DEBOUNCE_MS);
        assertEquals(1, runs.get(), "20 events for the same path must coalesce into a single delivery");
    }

    @Test
    void eventsForDifferentPaths_eachFireOnce() throws Exception {
        Path pathA = Paths.get("/tmp/playlist-a.m3u");
        Path pathB = Paths.get("/tmp/playlist-b.m3u");
        CountDownLatch latch = new CountDownLatch(2);

        this.throttle.submit(pathA, latch::countDown);
        this.throttle.submit(pathB, latch::countDown);

        assertTrue(latch.await(DEBOUNCE_MS + DELIVERY_HEADROOM_MS, TimeUnit.MILLISECONDS),
                "Both paths should deliver; debounce is per-path, not global");
    }

    @Test
    void inFlightCount_neverExceedsBound() throws Exception {
        int submitted = MAX_IN_FLIGHT * 3; // 9 tasks for a bound of 3
        AtomicInteger observedMax = new AtomicInteger();
        CountDownLatch allEntered = new CountDownLatch(submitted);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(submitted);

        for (int i = 0; i < submitted; i++) {
            Path path = Paths.get("/tmp/playlist-" + i + ".m3u");
            this.throttle.submit(path, () -> {
                observedMax.accumulateAndGet(this.throttle.currentInFlight(), Math::max);
                allEntered.countDown();
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                finished.countDown();
            });
        }

        // Wait for the first MAX_IN_FLIGHT tasks to actually be running (i.e. holding
        // permits and parked on `release`). The rest must be parked on semaphore.acquire().
        Thread.sleep(DEBOUNCE_MS + DELIVERY_HEADROOM_MS);
        assertEquals(MAX_IN_FLIGHT, this.throttle.currentInFlight(),
                "in-flight count should equal the bound while tasks are held");

        // Release the held tasks; everything must eventually finish.
        release.countDown();
        assertTrue(allEntered.await(5, TimeUnit.SECONDS),
                "All submitted tasks should eventually enter the critical section");
        assertTrue(finished.await(5, TimeUnit.SECONDS),
                "All submitted tasks should eventually finish");
        assertTrue(observedMax.get() <= MAX_IN_FLIGHT,
                "in-flight tasks never exceeded the bound (saw " + observedMax.get() + ")");
        assertEquals(0, this.throttle.currentInFlight(),
                "all permits returned after tasks finish");
    }

    @Test
    void lateEvent_triggersSecondDelivery() throws Exception {
        Path path = Paths.get("/tmp/playlist-late.m3u");
        AtomicInteger runs = new AtomicInteger();
        CountDownLatch first = new CountDownLatch(1);
        CountDownLatch second = new CountDownLatch(1);

        this.throttle.submit(path, () -> {
            runs.incrementAndGet();
            first.countDown();
        });
        assertTrue(first.await(DEBOUNCE_MS + DELIVERY_HEADROOM_MS, TimeUnit.MILLISECONDS),
                "First delivery should fire within the window");

        // After the first task fires, the pending entry must have been cleared; a fresh
        // submit for the same path then deserves its own delivery.
        this.throttle.submit(path, () -> {
            runs.incrementAndGet();
            second.countDown();
        });
        assertTrue(second.await(DEBOUNCE_MS + DELIVERY_HEADROOM_MS, TimeUnit.MILLISECONDS),
                "Second delivery after the window should fire as its own task");
        assertEquals(2, runs.get());
    }

    @Test
    void shutdown_cancelsPendingAndDoesNotBlockOrThrow() throws Exception {
        Path path = Paths.get("/tmp/playlist-pending.m3u");
        AtomicInteger runs = new AtomicInteger();
        // Kill the @BeforeEach fixture throttle so this test owns lifecycle entirely;
        // build a fresh one with a 10s debounce so the timer is guaranteed pending when
        // we shut it down a few lines later. tearDown() runs shutdown() on the field
        // again — harmless, ScheduledExecutorService.shutdownNow on a terminated pool
        // is a no-op.
        this.throttle.shutdown();
        ScheduledExecutorService longScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "throttle-shutdown-test");
            t.setDaemon(true);
            return t;
        });
        PlaylistImportThrottle longThrottle = new PlaylistImportThrottle(
                10_000L, MAX_IN_FLIGHT, longScheduler);
        try {
            for (int i = 0; i < 5; i++) {
                longThrottle.submit(path, runs::incrementAndGet);
            }
            assertEquals(1, longThrottle.pendingScheduled(),
                    "5 same-path submits should leave exactly one pending future");

            long start = System.nanoTime();
            longThrottle.shutdown();
            long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

            assertTrue(elapsedMs < 5_000L,
                    "shutdown should not block (took " + elapsedMs + "ms)");
            assertEquals(0, longThrottle.pendingScheduled(),
                    "all pending futures cleared on shutdown");

            // After shutdown, no late firings should run the task.
            Thread.sleep(200L);
            assertEquals(0, runs.get(),
                    "no late deliveries should run after shutdown");
        } finally {
            longThrottle.shutdown();
        }
    }

    @Test
    void submitAfterShutdown_isNoOp() {
        Path path = Paths.get("/tmp/playlist-post-shutdown.m3u");
        AtomicInteger runs = new AtomicInteger();
        this.throttle.shutdown();
        this.throttle.submit(path, runs::incrementAndGet);
        assertEquals(0, this.throttle.pendingScheduled(),
                "submit() after shutdown should not schedule new work");
        assertEquals(0, runs.get());
    }

    @Test
    void bound_isExposedForOperability() {
        assertEquals(MAX_IN_FLIGHT, this.throttle.maxInFlight());
        assertEquals(0, this.throttle.currentInFlight(),
                "fresh throttle reports zero in-flight");
        assertFalse(this.throttle.pendingScheduled() > 0,
                "fresh throttle reports nothing pending");
    }

}
