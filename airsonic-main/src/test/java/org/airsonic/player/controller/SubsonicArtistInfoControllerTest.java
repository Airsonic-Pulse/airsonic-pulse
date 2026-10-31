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

import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.i18n.LocaleResolver;
import org.airsonic.player.service.ArtistService;
import org.airsonic.player.service.JaxbContentService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SecurityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.subsonic.restapi.Response;

import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Locale;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for the count clamps on the artist-info endpoints (#286, mirroring the #262 shape).
 * getSimilarSongs and getSimilarSongs2 clamp; getTopSongs and getArtistInfo2 deliberately pass
 * the count through because their services are bounded by the Last.fm response, and the
 * pass-through tests pin that decision.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SubsonicArtistInfoControllerTest {

    @Mock
    private LastFmService lastFmService;
    @Mock
    private ArtistService artistService;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private SecurityService securityService;
    @Mock
    private JaxbContentService jaxbContentService;
    @Mock
    private PlayerService playerService;
    @Mock
    private JAXBWriter jaxbWriter;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private LocaleResolver localeResolver;

    private SubsonicArtistInfoController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new SubsonicArtistInfoController();
        ReflectionTestUtils.setField(controller, "lastFmService", lastFmService);
        ReflectionTestUtils.setField(controller, "artistService", artistService);
        ReflectionTestUtils.setField(controller, "mediaFolderService", mediaFolderService);
        ReflectionTestUtils.setField(controller, "securityService", securityService);
        ReflectionTestUtils.setField(controller, "jaxbContentService", jaxbContentService);
        ReflectionTestUtils.setField(controller, "playerService", playerService);
        ReflectionTestUtils.setField(controller, "jaxbWriter", jaxbWriter);
        ReflectionTestUtils.setField(controller, "mediaFileService", mediaFileService);
        ReflectionTestUtils.setField(controller, "localeResolver", localeResolver);

        when(securityService.getCurrentUsername(any())).thenReturn("alice");
        when(playerService.getPlayersForUserAndClientId(any(), any())).thenReturn(List.of(new Player()));
        when(playerService.getPlayer(any(), any(), anyString())).thenReturn(new Player());
        when(mediaFolderService.getMusicFoldersForUser(anyString())).thenReturn(List.of());
        when(jaxbWriter.createResponse(anyBoolean())).thenReturn(new Response());
        when(mediaFileService.getMediaFile(anyInt())).thenReturn(new MediaFile());
        when(artistService.getArtist(anyInt())).thenReturn(new Artist());
        when(localeResolver.resolveLocale(any(HttpServletRequest.class))).thenReturn(Locale.ENGLISH);
        when(lastFmService.getSimilarSongsByMediaFile(any(), anyInt(), any())).thenReturn(List.of());
        when(lastFmService.getSimilarSongs(any(Artist.class), anyInt(), any())).thenReturn(List.of());
        when(lastFmService.getTopSongs(anyString(), anyInt(), any())).thenReturn(List.of());
        when(lastFmService.getSimilarArtists(any(Artist.class), anyInt(), anyBoolean(), any())).thenReturn(List.of());
    }

    private MockHttpServletRequest req() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteUser("alice");
        request.setParameter("c", "test");
        return request;
    }

    @Test
    void getSimilarSongs_clampsAbusiveCount() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("id", "10");
        request.setParameter("count", String.valueOf(Integer.MAX_VALUE));

        controller.getSimilarSongs(request, new MockHttpServletResponse());

        verify(lastFmService).getSimilarSongsByMediaFile(any(), eq(500), any());
    }

    @Test
    void getSimilarSongs_passesNormalCountThrough() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("id", "10");
        request.setParameter("count", "25");

        controller.getSimilarSongs(request, new MockHttpServletResponse());

        verify(lastFmService).getSimilarSongsByMediaFile(any(), eq(25), any());
    }

    @Test
    void getSimilarSongs_clampsNegativeCountToZero() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("id", "10");
        request.setParameter("count", "-5");

        controller.getSimilarSongs(request, new MockHttpServletResponse());

        verify(lastFmService).getSimilarSongsByMediaFile(any(), eq(0), any());
    }

    @Test
    void getSimilarSongs2_clampsAbusiveCount() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("id", "10");
        request.setParameter("count", String.valueOf(Integer.MAX_VALUE));

        controller.getSimilarSongs2(request, new MockHttpServletResponse());

        verify(lastFmService).getSimilarSongs(any(Artist.class), eq(500), any());
    }

    @Test
    void getSimilarSongs2_passesNormalCountThrough() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("id", "10");
        request.setParameter("count", "25");

        controller.getSimilarSongs2(request, new MockHttpServletResponse());

        verify(lastFmService).getSimilarSongs(any(Artist.class), eq(25), any());
    }

    @Test
    void getSimilarSongs2_clampsNegativeCountToZero() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("id", "10");
        request.setParameter("count", "-5");

        controller.getSimilarSongs2(request, new MockHttpServletResponse());

        verify(lastFmService).getSimilarSongs(any(Artist.class), eq(0), any());
    }

    @Test
    void getTopSongs_passesCountThroughUnclamped() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("artist", "Some Artist");
        request.setParameter("count", String.valueOf(Integer.MAX_VALUE));

        controller.getTopSongs(request, new MockHttpServletResponse());

        // Deliberately unclamped (#286): LastFmService bounds the work by the Last.fm response.
        verify(lastFmService).getTopSongs(eq("Some Artist"), eq(Integer.MAX_VALUE), any());
    }

    @Test
    void getArtistInfo2_passesCountThroughUnclamped() throws Exception {
        MockHttpServletRequest request = req();
        request.setParameter("id", "10");
        request.setParameter("count", String.valueOf(Integer.MAX_VALUE));

        controller.getArtistInfo2(request, new MockHttpServletResponse());

        // Deliberately unclamped (#286): LastFmService bounds the work by the Last.fm response.
        verify(lastFmService).getSimilarArtists(any(Artist.class), eq(Integer.MAX_VALUE), eq(false), any());
    }
}
