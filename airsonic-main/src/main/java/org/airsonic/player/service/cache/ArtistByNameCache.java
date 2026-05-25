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
package org.airsonic.player.service.cache;

import org.airsonic.player.domain.Artist;
import org.airsonic.player.spring.CacheConfiguration;
import org.springframework.stereotype.Component;

import javax.cache.CacheManager;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Cache for the name → {@link Artist} resolution that happens once per item (and once per
 * contributor) in {@code JaxbContentService.createJaxbChild}. Misses are cached as
 * {@link Optional#empty()} because contributor names (composer, lyricist, performer …) almost
 * always miss — and re-querying the DAO every miss is the whole performance hazard this cache
 * exists to remove.
 * <p>
 * Correctness rests on two hooks driven from the media-scan path
 * (see {@code MediaScannerService.doScanLibrary}): the cache is disabled during the scan window
 * so reads during scan bypass it and see live DB state, and it is cleared + re-enabled in the
 * scan-finally block so the next request repopulates from post-scan state. Between scans the
 * artist table is read-only from the request path, so cached entries are by definition fresh.
 * Admin-triggered expunge clears the cache explicitly via {@code ArtistService.expunge}.
 * <p>
 * {@code get} returns {@code null} when the key is absent from the cache (caller should query
 * the DAO) and a non-null {@code Optional} when the key is cached (which may itself be empty
 * for a cached miss). This three-state contract is what lets cached misses short-circuit.
 */
@Component
public class ArtistByNameCache {

    private final CacheManager cacheManager;
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    public ArtistByNameCache(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
        this.cacheManager.enableStatistics(CacheConfiguration.ARTIST_BY_NAME_CACHE, true);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public Optional<Artist> get(String name) {
        if (isDisabled() || name == null) {
            return null;
        }
        Optional cached = cacheManager
                .getCache(CacheConfiguration.ARTIST_BY_NAME_CACHE, String.class, Optional.class)
                .get(name);
        return (Optional<Artist>) cached;
    }

    public void put(String name, Optional<Artist> value) {
        if (isDisabled() || name == null || value == null) {
            return;
        }
        cacheManager
                .getCache(CacheConfiguration.ARTIST_BY_NAME_CACHE, String.class, Optional.class)
                .put(name, value);
    }

    public void clear() {
        cacheManager.getCache(CacheConfiguration.ARTIST_BY_NAME_CACHE).clear();
    }

    public void setEnabled(boolean enabled) {
        this.enabled.set(enabled);
    }

    private boolean isDisabled() {
        return !enabled.get();
    }
}
