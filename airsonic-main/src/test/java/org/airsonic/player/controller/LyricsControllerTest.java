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

 Copyright 2025 (C) Y.Tory
 */

package org.airsonic.player.controller;

import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.view.LyricsPage;
import org.airsonic.player.view.LyricsUpdate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.ModelAndView;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LyricsControllerTest {
    @TempDir
    private static Path tempDir;

    @BeforeAll
    static void setUp(@TempDir Path tempDir) {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LyricsService lyricsService;

    @MockitoBean
    private MediaFileService mediaFileService;

    @MockitoBean
    private SecurityService securityService;

    @Mock
    private MediaFile mediaFile;

    @Test
    @WithMockUser(username = "testUser")
    void handleRequestInternal_returnsNotFound_whenMediaFileIsNull() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(null);

        mockMvc.perform(get("/lyrics").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("notFound"));
    }

    @Test
    @WithMockUser(username = "testUser")
    void handleRequestInternal_returnsNotFound_whenAccessDenied() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "testUser")).thenReturn(false);

        mockMvc.perform(get("/lyrics").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("notFound"));
    }

    @Test
    @WithMockUser(username = "testUser")
    void handleRequestInternal_returnsLyricsView_withLyrics() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "testUser")).thenReturn(true);
        when(mediaFile.getArtist()).thenReturn("Artist");
        when(mediaFile.getAlbumArtist()).thenReturn("AlbumArtist");
        when(mediaFile.getTitle()).thenReturn("Song");
        Lyrics lyrics = new Lyrics();
        lyrics.setLyrics("Some lyrics");
        lyrics.setSource("source");
        when(lyricsService.getLyricsFromMediaFile(mediaFile)).thenReturn(lyrics);

        MvcResult result = mockMvc.perform(get("/lyrics").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("lyrics"))
                .andReturn();

        ModelAndView mav = result.getModelAndView();
        LyricsPage page = (LyricsPage) mav.getModel().get("view");
        assertEquals("Artist", page.artist());
        assertEquals("Song", page.song());
        assertEquals("Some lyrics", page.lyrics());
        assertEquals("source", page.source());
    }

    @Test
    @WithMockUser(username = "testUser")
    void handleRequestInternal_returnsLyricsView_withNullLyrics() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "testUser")).thenReturn(true);
        when(mediaFile.getArtist()).thenReturn(null);
        when(mediaFile.getAlbumArtist()).thenReturn("AlbumArtist");
        when(mediaFile.getTitle()).thenReturn("Song");
        when(lyricsService.getLyricsFromMediaFile(mediaFile)).thenReturn(null);

        MvcResult result = mockMvc.perform(get("/lyrics").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("lyrics"))
                .andReturn();

        ModelAndView mav = result.getModelAndView();
        LyricsPage page = (LyricsPage) mav.getModel().get("view");

        assertNotNull(page);
        assertEquals("AlbumArtist", page.artist());
        assertEquals("Song", page.song());
        assertNull(page.lyrics());
        assertEquals("none", page.source());
    }

    @Test
    @WithMockUser(username = "testUser")
    void editLyrics_returnsNotFound_whenMediaFileIsNull() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(null);

        mockMvc.perform(get("/lyrics/edit").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("notFound"));
    }

    @Test
    @WithMockUser(username = "testUser")
    void editLyrics_returnsNotFound_whenAccessDenied() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "testUser")).thenReturn(false);

        mockMvc.perform(get("/lyrics/edit").param("id", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("notFound"));
    }

    @Test
    @WithAnonymousUser
    void editLyricsWithAnonymousUser_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/lyrics/edit").param("id", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "testUser")
    void updateLyrics_returnsNotFound_whenMediaFileIsNull() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(null);

        mockMvc.perform(post("/lyrics")
                .flashAttr("form", new LyricsUpdate(1, "New lyrics"))
                .with(csrf()))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(view().name("notFound"));
    }

    @Test
    @WithMockUser(username = "testUser")
    void updateLyrics_returnsNotFound_whenAccessDenied() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "testUser")).thenReturn(false);

        mockMvc.perform(post("/lyrics")
                .flashAttr("form", new LyricsUpdate(1, "New lyrics"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("notFound"));
    }

    @Test
    @WithMockUser(username = "testUser")
    void updateLyrics_savesLyrics_whenValid() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "testUser")).thenReturn(true);
        when(mediaFile.getId()).thenReturn(1);
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "testUser")).thenReturn(false);

        mockMvc.perform(post("/lyrics")
                .flashAttr("form", new LyricsUpdate(1, "New lyrics"))
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("notFound"));
    }

    @Test
    @WithMockUser(username = "testUser")
    void updateLyricsWithBlankLyrics_deletesLyrics() throws Exception {
        when(mediaFileService.getMediaFile(1)).thenReturn(mediaFile);
        when(securityService.isFolderAccessAllowed(mediaFile, "testUser")).thenReturn(true);
        when(mediaFile.getId()).thenReturn(1);
        doNothing().when(lyricsService).deleteLyricsForMediaFile(eq(mediaFile));

        mockMvc.perform(post("/lyrics")
                .flashAttr("form", new LyricsUpdate(1, ""))
                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/lyrics?id=1"));

        verify(lyricsService).deleteLyricsForMediaFile(eq(mediaFile));
    }

}