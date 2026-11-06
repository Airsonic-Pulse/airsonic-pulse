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
package org.airsonic.player.util;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Durable guard for the fixture-cleanup contract (#266): a non-transactional
 * {@code @SpringBootTest} class that commits persistent fixtures must delete exactly what it
 * created. The guard snapshots the row counts of the persistent fixture tables when the class
 * starts and asserts after the class that every count is unchanged, naming each drifted table
 * and its delta. It compares per-class deltas, not global zero counts, so residue from suite
 * ordering (or a pre-seeded database) can never produce a false failure on a clean class.
 * <p>
 * The "before" snapshot is taken lazily at the first test's {@code BeforeEachCallback} (not in
 * {@code beforeAll}) so the Spring context is never forced to load before the class's static
 * {@code @BeforeAll} has set {@code airsonic.home}. The audited classes commit nothing
 * persistent from {@code @BeforeAll}, so the snapshot still precedes every fixture write; it
 * deliberately also precedes the class's own {@code @BeforeEach}, so a class-local defensive
 * sweep that deletes another class's residue shows up as a negative delta here instead of
 * passing silently.
 * <p>
 * Registration is explicit — {@code @ExtendWith(FixtureTableGuardExtension.class)} on each
 * audited class — rather than META-INF automatic registration, which would need
 * {@code junit.jupiter.extensions.autodetection.enabled} wired into surefire and would drag
 * every Jupiter test (including non-Spring unit tests) through the guard.
 */
public class FixtureTableGuardExtension implements BeforeEachCallback, AfterAllCallback {

    /** Tables a persistent fixture can commit to; artist/album have no FK to music_folder. */
    private static final List<String> GUARDED_TABLES = List.of(
            "music_folder", "media_file", "artist", "album",
            "podcast_channel", "podcast_episode",
            "playlist", "play_queue", "api_key", "users");

    private static final Namespace NAMESPACE = Namespace.create(FixtureTableGuardExtension.class);
    private static final String BEFORE_KEY = "countsBeforeClass";

    @Override
    public void beforeEach(ExtensionContext context) {
        ExtensionContext classContext = classContext(context);
        ExtensionContext.Store store = classContext.getStore(NAMESPACE);
        if (store.get(BEFORE_KEY) == null) {
            store.put(BEFORE_KEY, snapshot(context));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterAll(ExtensionContext context) {
        Map<String, Long> before = (Map<String, Long>) context.getStore(NAMESPACE).get(BEFORE_KEY);
        if (before == null) {
            // No test ran (class skipped/filtered); nothing was committed.
            return;
        }
        Map<String, Long> after = snapshot(context);
        List<String> drifted = GUARDED_TABLES.stream()
                .filter(table -> !Objects.equals(before.get(table), after.get(table)))
                .map(table -> table + ": " + before.get(table) + " -> " + after.get(table)
                        + " (delta " + (after.get(table) - before.get(table)) + ")")
                .toList();
        if (!drifted.isEmpty()) {
            fail(context.getRequiredTestClass().getSimpleName()
                    + " did not clean up the persistent fixture rows it is responsible for (#266): "
                    + String.join(", ", drifted));
        }
    }

    private ExtensionContext classContext(ExtensionContext context) {
        ExtensionContext current = context;
        while (current.getTestMethod().isPresent() && current.getParent().isPresent()) {
            current = current.getParent().get();
        }
        return current;
    }

    private Map<String, Long> snapshot(ExtensionContext context) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(
                SpringExtension.getApplicationContext(context).getBean(DataSource.class));
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String table : GUARDED_TABLES) {
            counts.put(table, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class));
        }
        return counts;
    }
}
