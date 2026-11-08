package org.airsonic.player.service;

import org.airsonic.player.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlaylistFileServiceWatcherTest {

    @Mock
    private PlaylistService playlistService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PathWatcherService pathWatcherService;

    @Mock
    private PlaylistImportThrottle importThrottle;

    private PlaylistFileService playlistFileService;

    @BeforeEach
    public void setup() {
        playlistFileService = new PlaylistFileService(
                playlistService,
                settingsService,
                userRepository,
                pathWatcherService,
                importThrottle,
                Collections.emptyList(),
                Collections.emptyList());
    }

    @Test
    public void testAddPlaylistFolderWatcherSkipsWatcherWhenFolderIsBlank() {
        when(settingsService.getPlaylistFolder()).thenReturn(null, "   ");

        playlistFileService.addPlaylistFolderWatcher();
        playlistFileService.addPlaylistFolderWatcher();

        verifyNoInteractions(pathWatcherService);
    }
}
