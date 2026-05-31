package org.airsonic.player.service;

import com.google.common.collect.Lists;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.PlaylistMediaFile;
import org.airsonic.player.domain.User;
import org.airsonic.player.repository.PlaylistRepository;
import org.airsonic.player.repository.UserRepository;
import org.airsonic.player.service.playlist.DefaultPlaylistImportHandler;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PlaylistFileServiceTestImport {

    @InjectMocks
    private DefaultPlaylistImportHandler defaultPlaylistImportHandler;

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private MediaFileService mediaFileService;

    @Mock
    private SettingsService settingsService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityService securityService;

    @Mock
    private PathWatcherService pathWatcherService;

    @Mock
    private PlaylistImportThrottle importThrottle;

    @Mock
    private PlaylistService playlistService;

    @TempDir
    private Path tempDir;

    @Captor
    private ArgumentCaptor<Playlist> playlistCaptor;

    @Captor
    private ArgumentCaptor<List<MediaFile>> medias;

    private PlaylistFileService playlistFileService;

    @BeforeEach
    public void beforeEach() {
        playlistFileService = new PlaylistFileService(
                playlistService,
                settingsService,
                userRepository,
                pathWatcherService,
                importThrottle,
                Collections.emptyList(),
                Lists.newArrayList(defaultPlaylistImportHandler));
    }

    @Test
    public void testImportFromM3U() throws Exception {
        // given
        String username = "testUser";
        String playlistName = "test-playlist";
        StringBuilder builder = new StringBuilder();
        builder.append("#EXTM3U\n");
        File mf1 = tempDir.resolve("mf1.mp3").toFile();
        FileUtils.touch(mf1);
        File mf2 = tempDir.resolve("mf2.mp3").toFile();
        FileUtils.touch(mf2);
        File mf3 = tempDir.resolve("mf3.mp3").toFile();
        FileUtils.touch(mf3);
        builder.append(mf1.getAbsolutePath()).append("\n");
        builder.append(mf2.getAbsolutePath()).append("\n");
        builder.append(mf3.getAbsolutePath()).append("\n");
        doAnswer(new PersistPlayList(23)).when(playlistService).createPlaylist(playlistCaptor.capture());
        when(playlistService.setFilesInPlaylist(eq(23), any())).thenAnswer((Answer<Playlist>) invocationOnMock -> {
            Playlist playlist = playlistCaptor.getValue();
            playlist.setId(invocationOnMock.getArgument(0));
            List<MediaFile> mediaFiles = invocationOnMock.getArgument(1);
            List<PlaylistMediaFile> files = new ArrayList<>();
            for (int i = 0; i < mediaFiles.size(); i++) {
                files.add(new PlaylistMediaFile(playlist, mediaFiles.get(i), i));
            }
            playlist.setPlaylistMediaFiles(files);
            playlist.setFileCount(mediaFiles.size());
            playlist.setDuration(mediaFiles.stream().mapToDouble(MediaFile::getDuration).sum());
            return playlist;
        });
        doAnswer(new MediaFileHasEverything()).when(mediaFileService).getMediaFile(any(Path.class));
        InputStream inputStream = new ByteArrayInputStream(builder.toString().getBytes(StandardCharsets.UTF_8));
        String path = "/path/to/" + playlistName + ".m3u";
        User user = new User();
        user.setUsername(username);

        // when
        Playlist actual = playlistFileService.importPlaylist(user, playlistName, path, Paths.get(path), inputStream, null);

        // then
        verify(playlistService).createPlaylist(any(Playlist.class));
        verify(playlistService).setFilesInPlaylist(anyInt(), any());
        verify(playlistService).broadcast(any());
        verify(playlistService).broadcastFileChange(eq(23), eq(true), eq(true));
        verifyNoMoreInteractions(playlistService);
        Playlist expected = new Playlist();
        expected.setUsername(username);
        expected.setName(playlistName);
        expected.setComment("Auto-imported from " + path);
        expected.setImportedFrom(path);
        expected.setShared(true);
        expected.setId(23);
        expected.setFileCount(3);
        expected.setDuration(369.0);
        assertTrue(EqualsBuilder.reflectionEquals(actual, expected, "created", "changed", "sharedUsers", "mediaFiles"));
        assertEquals(3, actual.getPlaylistMediaFiles().size());
    }

    @Test
    public void testImportFromPLS() throws Exception {
        String username = "testUser";
        String playlistName = "test-playlist";
        StringBuilder builder = new StringBuilder();
        builder.append("[playlist]\n");
        File mf1 = tempDir.resolve("mf1.mp3").toFile();
        FileUtils.touch(mf1);
        File mf2 = tempDir.resolve("mf2.mp3").toFile();
        FileUtils.touch(mf2);
        File mf3 = tempDir.resolve("mf3.mp3").toFile();
        FileUtils.touch(mf3);
        builder.append("File1=").append(mf1.getAbsolutePath()).append("\n");
        builder.append("File2=").append(mf2.getAbsolutePath()).append("\n");
        builder.append("File3=").append(mf3.getAbsolutePath()).append("\n");
        doAnswer(new PersistPlayList(23)).when(playlistService).createPlaylist(playlistCaptor.capture());
        when(playlistService.setFilesInPlaylist(eq(23), any())).thenAnswer((Answer<Playlist>) invocationOnMock -> {
            Playlist playlist = playlistCaptor.getValue();
            playlist.setId(invocationOnMock.getArgument(0));
            List<MediaFile> mediaFiles = invocationOnMock.getArgument(1);
            List<PlaylistMediaFile> files = new ArrayList<>();
            for (int i = 0; i < mediaFiles.size(); i++) {
                files.add(new PlaylistMediaFile(playlist, mediaFiles.get(i), i));
            }
            playlist.setPlaylistMediaFiles(files);
            playlist.setFileCount(mediaFiles.size());
            playlist.setDuration(mediaFiles.stream().mapToDouble(MediaFile::getDuration).sum());
            return playlist;
        });
        doAnswer(new MediaFileHasEverything()).when(mediaFileService).getMediaFile(any(Path.class));
        InputStream inputStream = new ByteArrayInputStream(builder.toString().getBytes(StandardCharsets.UTF_8));
        String path = "/path/to/" + playlistName + ".pls";
        User user = new User();
        user.setUsername(username);

        // when
        Playlist actual = playlistFileService.importPlaylist(user, playlistName, path, Paths.get(path), inputStream, null);

        // then
        verify(playlistService).createPlaylist(any(Playlist.class));
        verify(playlistService).setFilesInPlaylist(anyInt(), any());
        verify(playlistService).broadcast(any());
        verify(playlistService).broadcastFileChange(eq(23), eq(true), eq(true));
        verifyNoMoreInteractions(playlistService);
        Playlist expected = new Playlist();
        expected.setUsername(username);
        expected.setName(playlistName);
        expected.setComment("Auto-imported from " + path);
        expected.setImportedFrom(path);
        expected.setShared(true);
        expected.setId(23);
        expected.setFileCount(3);
        expected.setDuration(369.0);
        assertTrue(EqualsBuilder.reflectionEquals(actual, expected, "created", "changed", "sharedUsers", "mediaFiles"));
        assertEquals(3, actual.getPlaylistMediaFiles().size());
    }

    @Test
    public void testImportFromXSPF() throws Exception {
        String username = "testUser";
        String playlistName = "test-playlist";
        StringBuilder builder = new StringBuilder();
        builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">\n"
                + "    <trackList>\n");

        File mf1 = tempDir.resolve("mf1.mp3").toFile();
        FileUtils.touch(mf1);
        File mf2 = tempDir.resolve("mf2.mp3").toFile();
        FileUtils.touch(mf2);
        File mf3 = tempDir.resolve("mf3.mp3").toFile();
        FileUtils.touch(mf3);
        builder.append("<track><location>file://").append(mf1.getAbsolutePath()).append("</location></track>\n");
        builder.append("<track><location>file://").append(mf2.getAbsolutePath()).append("</location></track>\n");
        builder.append("<track><location>file://").append(mf3.getAbsolutePath()).append("</location></track>\n");
        builder.append("    </trackList>\n" + "</playlist>\n");
        doAnswer(new PersistPlayList(23)).when(playlistService).createPlaylist(playlistCaptor.capture());
        when(playlistService.setFilesInPlaylist(eq(23), any())).thenAnswer((Answer<Playlist>) invocationOnMock -> {
            Playlist playlist = playlistCaptor.getValue();
            playlist.setId(invocationOnMock.getArgument(0));
            List<MediaFile> mediaFiles = invocationOnMock.getArgument(1);
            List<PlaylistMediaFile> files = new ArrayList<>();
            for (int i = 0; i < mediaFiles.size(); i++) {
                files.add(new PlaylistMediaFile(playlist, mediaFiles.get(i), i));
            }
            playlist.setPlaylistMediaFiles(files);
            playlist.setFileCount(mediaFiles.size());
            playlist.setDuration(mediaFiles.stream().mapToDouble(MediaFile::getDuration).sum());
            return playlist;
        });
        doAnswer(new MediaFileHasEverything()).when(mediaFileService).getMediaFile(any(Path.class));
        InputStream inputStream = new ByteArrayInputStream(builder.toString().getBytes(StandardCharsets.UTF_8));
        String path = "/path/to/" + playlistName + ".xspf";
        User user = new User();
        user.setUsername(username);
        // when
        Playlist actual = playlistFileService.importPlaylist(user, playlistName, path, Paths.get(path), inputStream, null);

        // then
        verify(playlistService).createPlaylist(any(Playlist.class));
        verify(playlistService).setFilesInPlaylist(anyInt(), any());
        verify(playlistService).broadcast(any());
        verify(playlistService).broadcastFileChange(eq(23), eq(true), eq(true));
        verifyNoMoreInteractions(playlistService);
        Playlist expected = new Playlist();
        expected.setUsername(username);
        expected.setName(playlistName);
        expected.setComment("Auto-imported from " + path);
        expected.setImportedFrom(path);
        expected.setShared(true);
        expected.setId(23);
        expected.setFileCount(3);
        expected.setDuration(369.0);
        assertTrue(EqualsBuilder.reflectionEquals(actual, expected, "created", "changed", "sharedUsers", "mediaFiles"));
        assertEquals(3, actual.getPlaylistMediaFiles().size());
    }

    private class PersistPlayList implements Answer<Object> {
        private final int id;

        public PersistPlayList(int id) {
            this.id = id;
        }

        @Override
        public Object answer(InvocationOnMock invocationOnMock) {
            Playlist playlist = invocationOnMock.getArgument(0);
            playlist.setId(id);
            return playlist;
        }
    }


    private class MediaFileHasEverything implements Answer<MediaFile> {

        @Override
        public MediaFile answer(InvocationOnMock invocationOnMock) {
            Path path = invocationOnMock.getArgument(0);
            MediaFile mediaFile = new MediaFile();
            mediaFile.setPath(path.toString());
            mediaFile.setDuration(123.0);
            return mediaFile;
        }
    }
}
