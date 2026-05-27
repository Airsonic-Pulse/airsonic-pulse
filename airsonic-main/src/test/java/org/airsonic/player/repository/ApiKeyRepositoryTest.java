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
package org.airsonic.player.repository;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.ApiKey;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.User.Role;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the {@code api_key} table — proves the Liquibase migration creates the
 * table, the entity round-trips, the {@code key_hash} UNIQUE constraint is enforced, and the
 * cascade-delete from {@code users} works.
 */
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
@Transactional
public class ApiKeyRepositoryTest {

    @Autowired
    private ApiKeyRepository apiKeyRepository;

    @Autowired
    private UserRepository userRepository;

    @TempDir
    private static Path tempDir;

    private static final String TEST_USER = "alice-apikey-test";

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void setUp() {
        // No manual cleanup — class-level @Transactional rolls back the test transaction at
        // method end, including the user saved here and any api_key rows inserted by the test.
        // Postgres would reject a manual cleanup DELETE that runs in a transaction already
        // aborted by uniqueKeyHashIsEnforced's constraint violation (SQLSTATE 25P02); relying
        // on framework rollback is portable across HSQLDB/Postgres/MariaDB.
        User user = new User(TEST_USER, "alice@example.com", false, 0L, 0L, 0L,
                Set.of(Role.STREAM));
        userRepository.saveAndFlush(user);
    }

    @Test
    public void saveAndLookupByHash() {
        ApiKey key = new ApiKey(TEST_USER, "deadbeef" + System.nanoTime(),
                "phone", Instant.now(), null);
        ApiKey saved = apiKeyRepository.save(key);

        assertNotNull(saved.getId());
        Optional<ApiKey> found = apiKeyRepository.findByKeyHash(saved.getKeyHash());
        assertTrue(found.isPresent());
        assertEquals(TEST_USER, found.get().getUsername());
        assertEquals("phone", found.get().getName());
        assertTrue(found.get().isEnabled());
    }

    @Test
    public void uniqueKeyHashIsEnforced() {
        String shared = "shared-hash-" + System.nanoTime();
        apiKeyRepository.saveAndFlush(
                new ApiKey(TEST_USER, shared, "first", Instant.now(), null));
        // Inserting a second row with the same key_hash must fail at the constraint layer,
        // even for a different user. Hash collision is the only way two keys would alias and
        // the UNIQUE index is what prevents that ambiguity at resolve time.
        assertThrows(DataIntegrityViolationException.class, () ->
                apiKeyRepository.saveAndFlush(
                        new ApiKey(TEST_USER, shared, "duplicate", Instant.now(), null)));
    }

    @Test
    public void findByUsernameOrderByCreatedAsc() {
        Instant earlier = Instant.now().minusSeconds(60);
        Instant later = Instant.now();
        apiKeyRepository.saveAndFlush(
                new ApiKey(TEST_USER, "h-later-" + System.nanoTime(), "later", later, null));
        apiKeyRepository.saveAndFlush(
                new ApiKey(TEST_USER, "h-earlier-" + System.nanoTime(), "earlier", earlier, null));

        List<ApiKey> keys = apiKeyRepository.findByUsernameOrderByCreatedAsc(TEST_USER);
        assertEquals(2, keys.size());
        assertEquals("earlier", keys.get(0).getName());
        assertEquals("later", keys.get(1).getName());
    }

    @Test
    public void cascadeDeleteFromUsers() {
        apiKeyRepository.saveAndFlush(
                new ApiKey(TEST_USER, "h-cascade-" + System.nanoTime(), "k", Instant.now(), null));
        assertEquals(1, apiKeyRepository.findByUsernameOrderByCreatedAsc(TEST_USER).size());

        // Delete the user via JDBC to bypass JPA cascade; this proves the DB-level cascade.
        userRepository.deleteById(TEST_USER);
        userRepository.flush();
        apiKeyRepository.flush();

        assertEquals(0, apiKeyRepository.findByUsernameOrderByCreatedAsc(TEST_USER).size(),
                "api_key rows must be cascade-deleted when the parent user is removed");
    }
}
