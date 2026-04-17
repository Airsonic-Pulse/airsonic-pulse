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

 Copyright 2023 (C) Y.Tory

 */
package org.airsonic.player.service;

import org.airsonic.player.domain.Version;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.support.PropertiesLoaderUtils;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("EmbeddedTestCategory")
@ExtendWith(MockitoExtension.class)
public class VersionServiceTest {

    private MockedStatic<PropertiesLoaderUtils> propertiesLoaderUtilsMockedStatic;

    private final String VERSION = "10.6.2-SNAPSHOT";
    private final String COMMIT = "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0";
    private final String TIMESTAMP = "20230509140255";
    private final String NAME = "Airsonic Main";

    @BeforeEach
    public void setUp() throws Exception {
        propertiesLoaderUtilsMockedStatic = Mockito.mockStatic(PropertiesLoaderUtils.class);
        Properties properties = new Properties();
        properties.put("version", VERSION);
        properties.put("revision", COMMIT);
        properties.put("timestamp", TIMESTAMP);
        properties.put("name", NAME);
        properties.put("projectUrl", "https://github.com/kagemomiji/airsonic-advanced");
        propertiesLoaderUtilsMockedStatic.when(() -> PropertiesLoaderUtils.loadAllProperties("build.properties")).thenReturn(properties);
    }

    @AfterEach
    public void tearDown() throws Exception {
        propertiesLoaderUtilsMockedStatic.close();
    }

    @Test
    public void testGetVersion() throws Exception {
        VersionService versionService = new VersionService();
        Version version = versionService.getLocalVersion();
        assertNotNull(versionService.getLocalVersion());
        assertEquals(10, version.getMajor());
        assertEquals(6, version.getMinor());
    }

    @Test
    public void testGetLocalBuildDate() throws Exception {
        VersionService versionService = new VersionService();
        assertEquals(1683640975000L, versionService.getLocalBuildDate().toEpochMilli());
    }

    @Test
    public void testGetLocalBuildNumber() throws Exception {
        VersionService versionService = new VersionService();
        assertEquals(COMMIT, versionService.getLocalBuildNumber());
    }

    @Test
    public void testIsNewFinalVersionAvailable() throws Exception {
        VersionService versionService = new VersionService();
        assertTrue(versionService.isNewFinalVersionAvailable());
    }

    @Test
    public void testIsNewBetaVersionAvailable() throws Exception {
        VersionService versionService = new VersionService();
        assertTrue(versionService.isNewBetaVersionAvailable());
    }
}
