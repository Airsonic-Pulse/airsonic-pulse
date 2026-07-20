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

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import com.google.common.collect.ImmutableMap;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.config.AirsonicCueConfig;
import org.airsonic.player.config.AirsonicFolderConfig;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.apache.commons.configuration2.spring.ConfigurationPropertySource;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test of {@link SettingsService}.
 *
 * @author Sindre Mehus
 */
@SpringBootTest
public class SettingsServiceTestCase {

    @TempDir
    private static Path airsonicHome;

    private SettingsService settingsService;

    @Autowired
    private StandardEnvironment env;

    @Autowired
    private AirsonicFolderConfig defaultFolderConfig;

    @Autowired
    private AirsonicCueConfig cueConfig;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", airsonicHome.toString());
    }

    @BeforeEach
    public void setUpEach() throws IOException {
        TestCaseUtils.cleanAirsonicHomeForTest();
        FileUtils.deleteDirectory(airsonicHome.toFile());
        Files.createDirectories(airsonicHome);
        ConfigurationPropertiesService.reset();
        cueConfig.setEnabled(true);
        cueConfig.setHideIndexedFiles(true);
        settingsService = newSettingsService();
    }

    private SettingsService newSettingsService() {
        SettingsService settingsService = new SettingsService();
        env.getPropertySources().addFirst(new ConfigurationPropertySource("airsonic-pre-init-configs", ConfigurationPropertiesService.getInstance().getConfiguration()));
        settingsService.setEnvironment(env);
        settingsService.setAirsonicConfig(new AirsonicHomeConfig(airsonicHome.toString(), null));
        settingsService.setAirsonicDefaultFolderConfig(defaultFolderConfig);
        settingsService.setAirsonicCueConfig(cueConfig);
        return settingsService;
    }

    @Test
    public void testDefaultValues() {
        assertEquals("en", settingsService.getLocale().getLanguage());
        assertEquals(1, settingsService.getIndexCreationInterval());
        assertEquals(3, settingsService.getIndexCreationHour());
        assertTrue(settingsService.getPlaylistFolder().endsWith("playlists"));
        assertEquals("default", settingsService.getThemeId());
        assertEquals(10, settingsService.getPodcastEpisodeRetentionCount());
        assertEquals(1, settingsService.getPodcastEpisodeDownloadCount());
        assertEquals(24, settingsService.getPodcastUpdateInterval());
        assertEquals(false, settingsService.isLdapEnabled());
        assertEquals("ldap://host.domain.com:389/cn=Users,dc=domain,dc=com", settingsService.getLdapUrl());
        assertNull(settingsService.getLdapManagerDn());
        assertNull(settingsService.getLdapManagerPassword());
        assertEquals("(sAMAccountName={0})", settingsService.getLdapSearchFilter());
        assertEquals(false, settingsService.isLdapAutoShadowing());
        assertEquals("30m", settingsService.getSessionDuration());
        assertEquals(true, settingsService.getEnableCueIndexing());
        assertEquals(true, settingsService.getHideVirtualTracks());
        assertTrue(settingsService.isShowVersionOnLogin());
    }

    @Test
    public void testChangeSettings() {
        settingsService.setIndexString("indexString");
        settingsService.setIgnoredArticles("a the foo bar");
        settingsService.setShortcuts("new incoming \"rock 'n' roll\"");
        settingsService.setPlaylistFolder("playlistFolder");
        settingsService.setMusicFileTypes("mp3 ogg  aac");
        settingsService.setCoverArtFileTypes("jpeg gif  png");
        settingsService.setWelcomeMessage("welcomeMessage");
        settingsService.setLoginMessage("loginMessage");
        settingsService.setSessionDuration("60m");
        settingsService.setLocale(Locale.CANADA_FRENCH);
        settingsService.setThemeId("dark");
        settingsService.setIndexCreationInterval(4);
        settingsService.setIndexCreationHour(9);
        settingsService.setPodcastEpisodeRetentionCount(5);
        settingsService.setPodcastEpisodeDownloadCount(-1);
        settingsService.setPodcastUpdateInterval(-1);
        settingsService.setLdapEnabled(true);
        settingsService.setLdapUrl("newLdapUrl");
        settingsService.setLdapManagerDn("admin");
        settingsService.setLdapManagerPassword("secret");
        settingsService.setLdapSearchFilter("newLdapSearchFilter");
        settingsService.setLdapAutoShadowing(true);
        settingsService.setEnableCueIndexing(false);
        settingsService.setHideVirtualTracks(false);
        settingsService.setShowVersionOnLogin(false);

        verifySettings(settingsService);

        settingsService.save();
        verifySettings(settingsService);

        verifySettings(newSettingsService());
    }

    private void verifySettings(SettingsService ss) {
        assertEquals("indexString", ss.getIndexString());
        assertEquals("a the foo bar", ss.getIgnoredArticles());
        assertEquals("new incoming \"rock 'n' roll\"", ss.getShortcuts());
        assertTrue(Arrays.equals(new String[] {"a", "the", "foo", "bar"}, ss.getIgnoredArticlesAsArray()));
        assertTrue(Arrays.equals(new String[] {"new", "incoming", "rock 'n' roll"}, ss.getShortcutsAsArray()));
        assertEquals("playlistFolder", ss.getPlaylistFolder());
        assertEquals("mp3 ogg  aac", ss.getMusicFileTypes());
        assertThat(ss.getMusicFileTypesSet()).containsOnly("mp3", "ogg", "aac");
        assertEquals("jpeg gif  png", ss.getCoverArtFileTypes());
        assertThat(ss.getCoverArtFileTypesSet()).containsOnly("jpeg", "gif", "png");
        assertEquals("welcomeMessage", ss.getWelcomeMessage());
        assertEquals("loginMessage", ss.getLoginMessage());
        assertEquals("60m", settingsService.getSessionDuration());
        assertEquals(Locale.CANADA_FRENCH, ss.getLocale());
        assertEquals("dark", ss.getThemeId());
        assertEquals(4, ss.getIndexCreationInterval());
        assertEquals(9, ss.getIndexCreationHour());
        assertEquals(5, settingsService.getPodcastEpisodeRetentionCount());
        assertEquals(-1, settingsService.getPodcastEpisodeDownloadCount());
        assertEquals(-1, settingsService.getPodcastUpdateInterval());
        assertTrue(settingsService.isLdapEnabled());
        assertEquals("newLdapUrl", settingsService.getLdapUrl());
        assertEquals("admin", settingsService.getLdapManagerDn());
        assertEquals("secret", settingsService.getLdapManagerPassword());
        assertEquals("newLdapSearchFilter", settingsService.getLdapSearchFilter());
        assertTrue(settingsService.isLdapAutoShadowing());
        assertFalse(settingsService.getEnableCueIndexing());
        assertFalse(settingsService.getHideVirtualTracks());
        assertFalse(ss.isShowVersionOnLogin());
    }

    @Test
    public void migratePropFileKeys_noKeys() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertNull(env.getProperty("bla"));
        assertNull(env.getProperty("bla2"));
        assertNull(env.getProperty("bla3"));
    }

    @Test
    public void migratePropFileKeys_deleteKeys_BackwardsCompatibilitySet() {
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", null);
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertEquals("hello", env.getProperty("bla"));
        assertEquals("hello", env.getProperty("bla2"));
        assertEquals("hello", env.getProperty("bla3"));
    }

    @Test
    public void migratePropFileKeys_deleteKeys_NonBackwardsCompatible() {
        ConfigurationPropertiesService.getInstance().setProperty(SettingsService.KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS, "false");
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", null);
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertNull(env.getProperty("bla"));
        assertNull(env.getProperty("bla2"));
        assertNull(env.getProperty("bla3"));
    }

    @Test
    public void migratePropFileKeys_withKeys_ExplicitlyBackwardsCompatible() {
        ConfigurationPropertiesService.getInstance().setProperty(SettingsService.KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS, "true");
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");
        ConfigurationPropertiesService.getInstance().setProperty("bla3", "hello2");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");
        keyMaps.put("bla4", "bla5");
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertEquals("hello", env.getProperty("bla"));
        assertEquals("hello", env.getProperty("bla2"));
        assertEquals("hello2", env.getProperty("bla3"));
        assertEquals("hello2", env.getProperty("bla4"));
        assertEquals("hello2", env.getProperty("bla5"));
    }

    @Test
    public void migratePropFileKeys_withKeys_ImplicitlyBackwardsCompatible() {
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");
        ConfigurationPropertiesService.getInstance().setProperty("bla3", "hello2");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");
        keyMaps.put("bla4", "bla5");
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertEquals("hello", env.getProperty("bla"));
        assertEquals("hello", env.getProperty("bla2"));
        assertEquals("hello2", env.getProperty("bla3"));
        assertEquals("hello2", env.getProperty("bla4"));
        assertEquals("hello2", env.getProperty("bla5"));
    }

    @Test
    public void migratePropFileKeys_withKeys_NonBackwardsCompatible() {
        ConfigurationPropertiesService.getInstance().setProperty(SettingsService.KEY_PROPERTIES_FILE_RETAIN_OBSOLETE_KEYS, "false");
        ConfigurationPropertiesService.getInstance().setProperty("bla", "hello");
        ConfigurationPropertiesService.getInstance().setProperty("bla3", "hello2");

        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");
        keyMaps.put("bla4", "bla5");
        SettingsService.migratePropFileKeys(keyMaps, ConfigurationPropertiesService.getInstance());

        assertNull(env.getProperty("bla"));
        assertNull(env.getProperty("bla2"));
        assertNull(env.getProperty("bla3"));
        assertNull(env.getProperty("bla4"));
        assertEquals("hello2", env.getProperty("bla5"));
    }

    @Test
    public void migrateEnvKeys_noKeys() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        Map<String, Object> migrated = new LinkedHashMap<>();
        SettingsService.migratePropertySourceKeys(keyMaps, new MapPropertySource("migrated-properties", Collections.emptyMap()), migrated);

        assertThat(migrated).isEmpty();
    }

    @Test
    public void migrateEnvKeys_keyChainPrecedence1() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");

        Map<String, Object> migrated = new LinkedHashMap<>();
        // higher precedence starts earlier in the chain order
        SettingsService.migratePropertySourceKeys(keyMaps,
                new MapPropertySource("migrated-properties", ImmutableMap.of("bla", "1")), migrated);
        SettingsService.migratePropertySourceKeys(keyMaps,
                new MapPropertySource("migrated-properties", ImmutableMap.of("bla3", "3")), migrated);

        assertThat(migrated).containsOnly(entry("bla2", "1"), entry("bla3", "1"), entry("bla4", "1"));
    }

    @Test
    public void migrateEnvKeys_keyChainPrecedence2() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", "bla4");

        Map<String, Object> migrated = new LinkedHashMap<>();
        // higher precedence starts later in the chain order
        SettingsService.migratePropertySourceKeys(keyMaps,
                new MapPropertySource("migrated-properties", ImmutableMap.of("bla3", "1")), migrated);
        SettingsService.migratePropertySourceKeys(keyMaps,
                new MapPropertySource("migrated-properties", ImmutableMap.of("bla", "3")), migrated);

        assertThat(migrated).containsOnly(entry("bla2", "3"), entry("bla3", "3"), entry("bla4", "1"));
    }

    @Test
    public void migrateEnvKeys_deleteKeys() {
        Map<String, String> keyMaps = new LinkedHashMap<>();
        keyMaps.put("bla", "bla2");
        keyMaps.put("bla2", "bla3");
        keyMaps.put("bla3", null);

        Map<String, Object> migrated = new LinkedHashMap<>();
        // higher precedence starts later in the chain order
        SettingsService.migratePropertySourceKeys(keyMaps,
                new MapPropertySource("migrated-properties", ImmutableMap.of("bla3", "1")), migrated);
        SettingsService.migratePropertySourceKeys(keyMaps,
                new MapPropertySource("migrated-properties", ImmutableMap.of("bla", "3")), migrated);

        assertThat(migrated).containsOnly(entry("bla2", "3"), entry("bla3", "3"));
    }
}
