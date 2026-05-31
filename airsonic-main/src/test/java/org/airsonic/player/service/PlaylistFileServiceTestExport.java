package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.PlaylistMediaFile;
import org.airsonic.player.repository.PlaylistRepository;
import org.airsonic.player.repository.UserRepository;
import org.airsonic.player.service.playlist.DefaultPlaylistExportHandler;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlaylistFileServiceTestExport {

    PlaylistFileService playlistFileService;

    @InjectMocks
    DefaultPlaylistExportHandler defaultPlaylistExportHandler;

    @Mock
    private PlaylistService playlistService;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private MediaFileService mediaFileService;

    @Mock
    private MediaFolderService mediaFolderService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AsyncWebSocketClient asyncWebSocketClient;

    @Mock
    private PathWatcherService pathWatcherService;

    @Mock
    private PlaylistImportThrottle importThrottle;

    @Mock
    MusicFolder mockedFolder;

    @TempDir
    private Path tempDir;

    @Captor
    ArgumentCaptor<Playlist> actual;

    @Captor
    ArgumentCaptor<List<MediaFile>> medias;

    @BeforeEach
    public void setup() {
        playlistFileService = new PlaylistFileService(
                playlistService,
                settingsService,
                userRepository,
                pathWatcherService,
                importThrottle,
                new ArrayList<>(List.of(defaultPlaylistExportHandler)),
                new ArrayList<>());
    }

    @Test
    public void testExportToM3U() throws Exception {

        Playlist playlist = new Playlist();
        playlist.setId(23);
        int orderIndex = 0;
        List<PlaylistMediaFile> playlistMediaFiles = new ArrayList<>();
        for (MediaFile mediaFile : getPlaylistFiles()) {
            playlistMediaFiles.add(new PlaylistMediaFile(playlist, mediaFile, orderIndex));
            orderIndex++;
        }
        playlist.setPlaylistMediaFiles(playlistMediaFiles);
        when(playlistRepository.findById(eq(23))).thenReturn(Optional.of(playlist));
        when(settingsService.getPlaylistExportFormat()).thenReturn("m3u");
        when(mediaFolderService.getMusicFolderById(any())).thenReturn(mockedFolder);
        when(mockedFolder.getPath()).thenReturn(Paths.get("/"));

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        playlistFileService.exportPlaylist(23, outputStream);
        byte[] actual = outputStream.toByteArray();
        byte[] expected = getClass().getResourceAsStream("/PLAYLISTS/23.m3u").readAllBytes();
        assertArrayEquals(expected, actual);
    }

    private List<MediaFile> getPlaylistFiles() {
        List<MediaFile> mediaFiles = new ArrayList<>();

        MediaFile mf1 = new MediaFile();
        mf1.setId(142);
        mf1.setPath("/some/path/to_album/to_artist/name - of - song.mp3");
        mf1.setPresent(true);
        mediaFiles.add(mf1);

        MediaFile mf2 = new MediaFile();
        mf2.setId(1235);
        mf2.setPath("/some/path/to_album2/to_artist/another song.mp3");
        mf2.setPresent(true);
        mediaFiles.add(mf2);

        MediaFile mf3 = new MediaFile();
        mf3.setId(198403);
        mf3.setPath("/some/path/to_album2/to_artist/another song2.mp3");
        mf3.setPresent(false);
        mediaFiles.add(mf3);

        return mediaFiles;
    }
}
