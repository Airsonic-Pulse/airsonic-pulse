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
package org.airsonic.player.controller;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.util.StringUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins the {@link AbstractSubsonicController#mapId} short-circuits across the commons-lang 2.x
 * to lang3 migration (#180): lang3's {@code isNumeric("")} is {@code false} where 2.x returned
 * {@code true}, so the explicit {@code isEmpty} guard must keep an empty id out of the
 * hex-decode lookup path.
 */
class AbstractSubsonicControllerMapIdTest {

    private AbstractSubsonicController controller;

    @BeforeEach
    void setUp() {
        this.controller = new AbstractSubsonicController() {
        };
        this.controller.mediaFileService = mock(MediaFileService.class);
    }

    @Test
    void mapIdEmptyStringShortCircuitsWithoutLookup() {
        // The 2.x result (empty id returned unchanged) is preserved; without the guard the
        // empty id would fall into the try block and hit mediaFileService.
        assertEquals("", this.controller.mapId(""));
        verifyNoInteractions(this.controller.mediaFileService);
    }

    @Test
    void mapIdNullAndNumericIdsShortCircuit() {
        assertNull(this.controller.mapId(null));
        assertEquals("42", this.controller.mapId("42"));
        verifyNoInteractions(this.controller.mediaFileService);
    }

    @Test
    void mapIdHexEncodedPathResolvesThroughMediaFileService() {
        String id = StringUtil.utf8HexEncode("/music/track.mp3");
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(7);
        when(this.controller.mediaFileService.getMediaFile("/music/track.mp3")).thenReturn(mediaFile);

        assertEquals("7", this.controller.mapId(id));
    }
}
