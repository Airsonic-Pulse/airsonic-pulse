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

import org.airsonic.player.command.PodcastSettingsCommand;
import org.airsonic.player.command.PodcastSettingsCommand.PodcastRule;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastChannelRule;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PodcastPersistenceService;
import org.airsonic.player.service.SettingsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.ui.Model;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Regression tests for {@link PodcastSettingsController#formBackingObject} (#296). The page appended
 * a synthetic "DEFAULT" rule to the rules list with {@code .add()}, but the list came from
 * {@code Stream.toList()} (unmodifiable since a Collectors.toList()→Stream.toList() refactor), so
 * every GET /podcastSettings threw {@link UnsupportedOperationException} and the page 500'd. These
 * assert the page model builds without throwing and the DEFAULT rule is appended after the
 * per-channel rules.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PodcastSettingsControllerTest {

    @Mock
    private SettingsService settingsService;
    @Mock
    private PodcastPersistenceService podcastPersistenceService;
    @Mock
    private MediaFolderService mediaFolderService;

    @InjectMocks
    private PodcastSettingsController controller;

    private void stubSettings() {
        when(mediaFolderService.getAllMusicFolders()).thenReturn(List.of());
        when(mediaFolderService.getAllMusicFolders(true, true)).thenReturn(List.of());
        when(settingsService.getPodcastUpdateInterval()).thenReturn(24);
        when(settingsService.getPodcastEpisodeRetentionCount()).thenReturn(-1);
        when(settingsService.getPodcastEpisodeDownloadCount()).thenReturn(-1);
    }

    @Test
    void formBackingObject_appendsDefaultRule_whenNoChannelRules() {
        // Reporter's case: default podcast config, no channel-specific rules. Pre-fix this threw
        // UnsupportedOperationException on the .add() and the page was completely inaccessible.
        stubSettings();
        when(podcastPersistenceService.getAllChannels()).thenReturn(List.of());
        when(podcastPersistenceService.getAllChannelRules()).thenReturn(List.of());

        Model model = new ExtendedModelMap();
        String view = assertDoesNotThrow(() -> controller.formBackingObject(model));

        assertEquals("podcastSettings", view);
        PodcastSettingsCommand command = (PodcastSettingsCommand) model.getAttribute("command");
        assertEquals(1, command.getRules().size(), "only the DEFAULT rule is present");
        PodcastRule defaultRule = command.getRules().get(0);
        assertEquals(-1, defaultRule.getId());
        assertEquals("DEFAULT", defaultRule.getName());
        assertEquals(-1, defaultRule.getEpisodeRetentionCount());
        assertEquals(-1, defaultRule.getEpisodeDownloadCount());
    }

    @Test
    void formBackingObject_appendsDefaultRuleAfterChannelRules() {
        // A per-channel rule plus the DEFAULT: proves ordering (channel rules first, DEFAULT last)
        // and that the per-channel rules survive the single-pass build.
        stubSettings();
        PodcastChannelRule channelRule = org.mockito.Mockito.mock(PodcastChannelRule.class);
        when(channelRule.getId()).thenReturn(5);
        when(channelRule.getCheckInterval()).thenReturn(12);
        when(channelRule.getRetentionCount()).thenReturn(10);
        when(channelRule.getDownloadCount()).thenReturn(3);
        PodcastChannel channel = org.mockito.Mockito.mock(PodcastChannel.class);
        when(channel.getId()).thenReturn(5);
        when(channel.getTitle()).thenReturn("My Podcast");
        when(podcastPersistenceService.getAllChannels()).thenReturn(List.of(channel));
        when(podcastPersistenceService.getAllChannelRules()).thenReturn(List.of(channelRule));

        Model model = new ExtendedModelMap();
        String view = assertDoesNotThrow(() -> controller.formBackingObject(model));

        assertEquals("podcastSettings", view);
        PodcastSettingsCommand command = (PodcastSettingsCommand) model.getAttribute("command");
        assertEquals(2, command.getRules().size());
        // per-channel rule first
        assertEquals(5, command.getRules().get(0).getId());
        assertEquals("My Podcast", command.getRules().get(0).getName());
        // DEFAULT appended last
        assertEquals(-1, command.getRules().get(1).getId());
        assertEquals("DEFAULT", command.getRules().get(1).getName());
    }
}
