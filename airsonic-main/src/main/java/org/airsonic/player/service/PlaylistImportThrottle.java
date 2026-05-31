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

import org.airsonic.player.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Per-path debounce and bounded fan-out around the playlist folder watcher (issue #215).
 *
 * <p>Filesystem events for the same path within {@link #DEFAULT_DEBOUNCE_WINDOW_MS} are
 * coalesced into a single import task; at most {@link #DEFAULT_MAX_IN_FLIGHT} import tasks
 * run concurrently and the rest queue at the {@link Semaphore} (no carrier-thread cost).
 * Import tasks run on virtual threads so they remain consistent with the project-wide
 * virtual-thread commitment; the bound comes from the semaphore, not the executor.
 *
 * <p>Bounds were chosen against {@code spring.datasource.hikari.maximum-pool-size=20}: a
 * cap of 8 (= {@code min(20/2, 8)}) lets a watcher-triggered storm use at most 40% of the
 * pool, leaving the scanner, REST handlers, and scrobblers room to make progress. The 1s
 * debounce matches the SMB-rsync burst characteristics reported in #215 (NIO's
 * {@code WatchService} does not surface inotify {@code CLOSE_WRITE}, so a time-based
 * quiet-period is the practical signal that a file write has settled).
 */
@Component
class PlaylistImportThrottle {
    private static final Logger LOG = LoggerFactory.getLogger(PlaylistImportThrottle.class);

    static final long DEFAULT_DEBOUNCE_WINDOW_MS = 1000L;
    static final int DEFAULT_MAX_IN_FLIGHT = 8;
    private static final long SHUTDOWN_TIMEOUT_MS = 5000L;

    private final ConcurrentMap<Path, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;
    private final Semaphore semaphore;
    private final long debounceWindowMs;
    private final int maxInFlight;
    private volatile boolean shuttingDown = false;

    @Autowired
    PlaylistImportThrottle() {
        this(DEFAULT_DEBOUNCE_WINDOW_MS, DEFAULT_MAX_IN_FLIGHT,
                Executors.newSingleThreadScheduledExecutor(
                        Util.getDaemonThreadfactory("playlist-watcher-debounce-")));
    }

    // Package-private for tests: lets a fixture inject a controllable scheduler and tweak
    // the debounce window / bound without going through Spring or system properties.
    PlaylistImportThrottle(long debounceWindowMs, int maxInFlight, ScheduledExecutorService scheduler) {
        this.debounceWindowMs = debounceWindowMs;
        this.maxInFlight = maxInFlight;
        this.semaphore = new Semaphore(maxInFlight);
        this.scheduler = scheduler;
    }

    /**
     * Coalesce filesystem events for {@code path}: any pending scheduled task for the same
     * path is cancelled and replaced. When the debounce window elapses without another
     * event for that path, {@code task} runs on a virtual thread under the bounded
     * semaphore.
     */
    void submit(Path path, Runnable task) {
        if (this.shuttingDown) {
            return;
        }
        try {
            // compute() makes the cancel-prior + schedule-new + put-tracking sequence
            // atomic per key, so concurrent submits for the same path cannot leave a
            // stranded future scheduled-but-untracked. The selfRef holder gives fire()
            // an identity it can use to do a conditional remove from the map.
            this.pending.compute(path, (k, prior) -> {
                if (prior != null) {
                    prior.cancel(false);
                }
                ScheduledFuture<?>[] selfRef = new ScheduledFuture<?>[1];
                selfRef[0] = this.scheduler.schedule(
                        () -> this.fire(path, selfRef[0], task),
                        this.debounceWindowMs, TimeUnit.MILLISECONDS);
                return selfRef[0];
            });
        } catch (RejectedExecutionException rejected) {
            // shutdown() raced ahead of us and closed the scheduler — drop this submit
            // silently. The NIO watcher thread must not propagate exceptions back into
            // the WatchService loop.
        }
    }

    private void fire(Path path, ScheduledFuture<?> self, Runnable task) {
        // Remove our entry only if it still points at us — a concurrent submit() may
        // have replaced it with a fresh future before this method ran, and that one
        // must stay.
        this.pending.remove(path, self);
        Thread.startVirtualThread(() -> runBounded(task));
    }

    private void runBounded(Runnable task) {
        try {
            this.semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            task.run();
        } catch (Throwable t) {
            LOG.warn("Playlist import task failed", t);
        } finally {
            this.semaphore.release();
        }
    }

    int currentInFlight() {
        return this.maxInFlight - this.semaphore.availablePermits();
    }

    int pendingScheduled() {
        return this.pending.size();
    }

    int maxInFlight() {
        return this.maxInFlight;
    }

    @PreDestroy
    void shutdown() {
        // Stop admitting new debounce timers, cancel everything pending, drain the
        // scheduler. In-flight virtual threads (past the semaphore acquire) are not
        // joined: when Spring closes the DataSource, HikariCP closes the underlying
        // JDBC connection and the in-flight statement throws SQLException, which the
        // AspectJ-woven @Transactional advice catches and rolls back. No half-committed
        // playlist state. Virtual threads do not hold JVM shutdown open.
        this.shuttingDown = true;
        this.pending.values().forEach(f -> f.cancel(false));
        this.pending.clear();
        this.scheduler.shutdownNow();
        try {
            if (!this.scheduler.awaitTermination(SHUTDOWN_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                LOG.warn("Playlist import throttle scheduler did not terminate within {}ms",
                        SHUTDOWN_TIMEOUT_MS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
