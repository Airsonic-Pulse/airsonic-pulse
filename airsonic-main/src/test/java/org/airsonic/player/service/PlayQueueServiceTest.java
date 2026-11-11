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

import com.google.common.collect.ImmutableMap;
import org.airsonic.player.domain.InternetRadio;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.domain.SavedPlayQueue;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.repository.InternetRadioRepository;
import org.airsonic.player.repository.SavedPlayQueueRepository;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PlayQueueServiceTest {

    @Mock
    private JukeboxService jukeboxService;
    @Mock
    private AsyncWebSocketClient webSocketClient;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private SavedPlayQueueRepository savedPlayQueueRepository;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private PersonalSettingsService personalSettingsService;
    @Mock
    private LastFmService lastFmService;
    @Mock
    private SearchService searchService;
    @Mock
    private PodcastPersistenceService podcastService;
    @Mock
    private PlaylistService playlistService;
    @Mock
    private InternetRadioRepository internetRadioRepository;
    @Mock
    private InternetRadioService internetRadioService;

    @Captor
    private ArgumentCaptor<Collection<MediaFile>> queuedFilesCaptor;

    @InjectMocks
    private PlayQueueService playQueueService;

    @Mock
    private Player mockedPlayer;
    @Mock
    private PlayQueue mockedPlayQueue;

    private ScheduledThreadPoolExecutor executor;

    @BeforeEach
    public void setup() {
        executor = new ScheduledThreadPoolExecutor(1);
    }

    @AfterEach
    public void teardown() {
        executor.shutdown();
    }



    @Nested
    class NonJukeboxTests {

        @BeforeEach
        public void setup() {
            when(mockedPlayer.isJukebox()).thenReturn(false);
        }

        @Test
        public void testStart() throws Exception {
            // given
            when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            when(mockedPlayer.getUsername()).thenReturn("testuser");
            when(mockedPlayer.getId()).thenReturn(1);

            // Verify websocketClient is called to send message
            when(webSocketClient.sendToUser(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            // Test
            playQueueService.start(mockedPlayer);

            // then
            verify(mockedPlayQueue).setStatus(PlayQueue.Status.PLAYING);
            verify(webSocketClient).sendToUser("testuser", "/queue/playqueues/1/playstatus", PlayQueue.Status.PLAYING);
        }

        @Test
        public void testStop() throws Exception {
            // given
            when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            when(mockedPlayer.getUsername()).thenReturn("testuser");
            when(mockedPlayer.getId()).thenReturn(1);

            // Verify websocketClient is called to send message
            when(webSocketClient.sendToUser(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            // Test
            playQueueService.stop(mockedPlayer);

            // then
            verify(mockedPlayQueue).setStatus(PlayQueue.Status.STOPPED);
            verify(webSocketClient).sendToUser("testuser", "/queue/playqueues/1/playstatus", PlayQueue.Status.STOPPED);
        }

        @ParameterizedTest
        @CsvSource({
            "PLAYING, STOPPED",
            "STOPPED, PLAYING"
        })
        public void testToggleStartStop(PlayQueue.Status initialStatus, PlayQueue.Status expectedStatus) throws Exception {
            // given
            when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            when(mockedPlayer.getUsername()).thenReturn("testuser");
            when(mockedPlayer.getId()).thenReturn(1);
            when(mockedPlayQueue.getStatus()).thenReturn(initialStatus);

            // Verify websocketClient is called to send message
            when(webSocketClient.sendToUser(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            // Test
            playQueueService.toggleStartStop(mockedPlayer);

            // then
            verify(mockedPlayQueue).setStatus(expectedStatus);
            verify(webSocketClient).sendToUser("testuser", "/queue/playqueues/1/playstatus", expectedStatus);
        }

        @Test
        public void testSkip() throws Exception {
            // given
            when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            when(mockedPlayer.getUsername()).thenReturn("testuser");
            when(mockedPlayer.getId()).thenReturn(1);
            when(mockedPlayQueue.getStatus()).thenReturn(PlayQueue.Status.PLAYING);
            when(webSocketClient.sendToUser(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            // when
            playQueueService.skip(mockedPlayer, 2, 3L);

            // then
            verify(webSocketClient).sendToUser("testuser", "/queue/playqueues/1/skip", ImmutableMap.of("index", 2, "offset", 3L));
            verify(webSocketClient).sendToUser("testuser", "/queue/playqueues/1/playstatus", PlayQueue.Status.PLAYING);
            verify(mockedPlayQueue).setIndex(2);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    public void testSavePlayQueueWithIndex(int index) throws Exception {
        // given
        when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
        List<MediaFile> files = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MediaFile mediaFile = new MediaFile();
            mediaFile.setId(i);
            files.add(mediaFile);
        }
        when(mockedPlayer.getUsername()).thenReturn("testuser");
        when(mockedPlayQueue.getFiles()).thenReturn(files);
        when(mockedPlayQueue.getFile(index)).thenReturn(files.get(index));
        when(savedPlayQueueRepository.findByUsername("testuser")).thenReturn(Optional.ofNullable(null));
        doAnswer(invocation -> {
            SavedPlayQueue savedPlayQueue = invocation.getArgument(0, SavedPlayQueue.class);
            savedPlayQueue.setId(1);
            return savedPlayQueue;
        }).when(savedPlayQueueRepository).save(any());

        // when
        playQueueService.savePlayQueue(mockedPlayer, index, 10L);

        // then
        ArgumentCaptor<SavedPlayQueue> captor = ArgumentCaptor.forClass(SavedPlayQueue.class);
        verify(savedPlayQueueRepository).save(captor.capture());
        SavedPlayQueue savedPlayQueue = captor.getValue();
        assertEquals("testuser", savedPlayQueue.getUsername());
        assertEquals(10L, savedPlayQueue.getPositionMillis());
        assertEquals(10, savedPlayQueue.getMediaFiles().size());
        assertEquals(index, savedPlayQueue.getCurrentMediaFile().getId());
        assertNotNull(savedPlayQueue.getChanged());
        assertEquals("testuser", savedPlayQueue.getChangedBy());
    }

    @Test
    public void testSavePlayQueueWithMinusOneIndex() throws Exception {
        // given
        when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
        List<MediaFile> files = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            MediaFile mediaFile = new MediaFile();
            mediaFile.setId(i);
            files.add(mediaFile);
        }
        when(mockedPlayer.getUsername()).thenReturn("testuser");
        when(mockedPlayQueue.getFiles()).thenReturn(files);
        doAnswer(invocation -> {
            SavedPlayQueue savedPlayQueue = invocation.getArgument(0, SavedPlayQueue.class);
            savedPlayQueue.setId(1);
            return savedPlayQueue;
        }).when(savedPlayQueueRepository).save(any());

        // when
        playQueueService.savePlayQueue(mockedPlayer, -1, 10L);

        // then
        ArgumentCaptor<SavedPlayQueue> captor = ArgumentCaptor.forClass(SavedPlayQueue.class);
        verify(savedPlayQueueRepository).save(captor.capture());
        verify(mockedPlayQueue, never()).getFile(anyInt());
        SavedPlayQueue savedPlayQueue = captor.getValue();
        assertEquals("testuser", savedPlayQueue.getUsername());
        assertEquals(10L, savedPlayQueue.getPositionMillis());
        assertEquals(10, savedPlayQueue.getMediaFiles().size());
        assertNull(savedPlayQueue.getCurrentMediaFile());
        assertNotNull(savedPlayQueue.getChanged());
        assertEquals("testuser", savedPlayQueue.getChangedBy());
    }

    // TODO: test methods include broadcastPlayQueue

    @Test
    public void testSavePlayQueueByIndexWritesIndexAndDerivedCurrentMediaFile() throws Exception {
        // given a queue of 5 files, save with currentIndex=2 → currentMediaFile = file[2]
        List<Integer> mediaFileIds = List.of(10, 11, 12, 13, 14);
        for (Integer id : mediaFileIds) {
            MediaFile mf = new MediaFile();
            mf.setId(id);
            when(mediaFileService.getMediaFile(id, true)).thenReturn(mf);
        }
        when(savedPlayQueueRepository.findByUsername("testuser")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            SavedPlayQueue saved = invocation.getArgument(0, SavedPlayQueue.class);
            saved.setId(1);
            return saved;
        }).when(savedPlayQueueRepository).save(any());

        playQueueService.savePlayQueueByIndex("testuser", mediaFileIds, 2, 5000L, "testclient");

        ArgumentCaptor<SavedPlayQueue> captor = ArgumentCaptor.forClass(SavedPlayQueue.class);
        verify(savedPlayQueueRepository).save(captor.capture());
        SavedPlayQueue saved = captor.getValue();
        assertEquals(Integer.valueOf(2), saved.getCurrentIndex());
        assertEquals(Integer.valueOf(12), saved.getCurrentMediaFile().getId());
        assertEquals(5L, (long) saved.getMediaFiles().size());
        assertEquals(5000L, saved.getPositionMillis());
        assertEquals("testclient", saved.getChangedBy());
    }

    @Test
    public void testSavePlayQueueByIndexDisambiguatesDuplicateTracks() throws Exception {
        // given the same track at indices 0 and 2 of [A, B, A, C], saving index 0 vs index 2
        // must round-trip distinctly even though the legacy currentMediaFile FK is identical.
        List<Integer> mediaFileIds = List.of(100, 200, 100, 300);
        for (Integer id : List.of(100, 200, 300)) {
            MediaFile mf = new MediaFile();
            mf.setId(id);
            when(mediaFileService.getMediaFile(id, true)).thenReturn(mf);
        }
        when(savedPlayQueueRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            SavedPlayQueue saved = invocation.getArgument(0, SavedPlayQueue.class);
            saved.setId(1);
            return saved;
        }).when(savedPlayQueueRepository).save(any());

        playQueueService.savePlayQueueByIndex("u1", mediaFileIds, 0, 0L, "c");
        playQueueService.savePlayQueueByIndex("u2", mediaFileIds, 2, 0L, "c");

        ArgumentCaptor<SavedPlayQueue> captor = ArgumentCaptor.forClass(SavedPlayQueue.class);
        verify(savedPlayQueueRepository, times(2)).save(captor.capture());
        List<SavedPlayQueue> saves = captor.getAllValues();
        assertEquals(Integer.valueOf(0), saves.get(0).getCurrentIndex());
        assertEquals(Integer.valueOf(2), saves.get(1).getCurrentIndex());
        // both resolve to the same media_file_id (100), but currentIndex distinguishes them.
        assertEquals(Integer.valueOf(100), saves.get(0).getCurrentMediaFile().getId());
        assertEquals(Integer.valueOf(100), saves.get(1).getCurrentMediaFile().getId());
    }

    @Test
    public void testSavePlayQueueByIndexNullIndexLeavesCurrentNull() throws Exception {
        List<Integer> mediaFileIds = List.of(10, 11);
        for (Integer id : mediaFileIds) {
            MediaFile mf = new MediaFile();
            mf.setId(id);
            when(mediaFileService.getMediaFile(id, true)).thenReturn(mf);
        }
        when(savedPlayQueueRepository.findByUsername("u")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            SavedPlayQueue saved = invocation.getArgument(0, SavedPlayQueue.class);
            saved.setId(1);
            return saved;
        }).when(savedPlayQueueRepository).save(any());

        playQueueService.savePlayQueueByIndex("u", mediaFileIds, null, 0L, "c");

        ArgumentCaptor<SavedPlayQueue> captor = ArgumentCaptor.forClass(SavedPlayQueue.class);
        verify(savedPlayQueueRepository).save(captor.capture());
        SavedPlayQueue saved = captor.getValue();
        assertNull(saved.getCurrentIndex());
        assertNull(saved.getCurrentMediaFile());
    }

    @Test
    public void testSavePlayQueueByIndexAdjustsIndexWhenInvalidIdPrecedesCurrent() throws Exception {
        // given [10, 99, 11, 12, 13] where 99 is unknown and currentIndex=3 (file 12)
        // → filtered queue is [10, 11, 12, 13]; adjusted currentIndex must be 2 (file 12's
        // position in the filtered list).
        List<Integer> mediaFileIds = List.of(10, 99, 11, 12, 13);
        for (Integer id : List.of(10, 11, 12, 13)) {
            MediaFile mf = new MediaFile();
            mf.setId(id);
            when(mediaFileService.getMediaFile(id, true)).thenReturn(mf);
        }
        when(mediaFileService.getMediaFile(99, true)).thenReturn(null);
        when(savedPlayQueueRepository.findByUsername("u")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            SavedPlayQueue saved = invocation.getArgument(0, SavedPlayQueue.class);
            saved.setId(1);
            return saved;
        }).when(savedPlayQueueRepository).save(any());

        playQueueService.savePlayQueueByIndex("u", mediaFileIds, 3, 0L, "c");

        ArgumentCaptor<SavedPlayQueue> captor = ArgumentCaptor.forClass(SavedPlayQueue.class);
        verify(savedPlayQueueRepository).save(captor.capture());
        SavedPlayQueue saved = captor.getValue();
        assertEquals(4, saved.getMediaFiles().size());
        assertEquals(Integer.valueOf(2), saved.getCurrentIndex());
        assertEquals(Integer.valueOf(12), saved.getCurrentMediaFile().getId());
    }

    @Test
    public void testSavePlayQueueByIndexNullsCurrentWhenCurrentEntryIsInvalid() throws Exception {
        // given [10, 99, 11] where 99 is unknown and currentIndex=1 (points at the invalid entry)
        // → both currentIndex and currentMediaFile must be null (no valid current to address).
        List<Integer> mediaFileIds = List.of(10, 99, 11);
        for (Integer id : List.of(10, 11)) {
            MediaFile mf = new MediaFile();
            mf.setId(id);
            when(mediaFileService.getMediaFile(id, true)).thenReturn(mf);
        }
        when(mediaFileService.getMediaFile(99, true)).thenReturn(null);
        when(savedPlayQueueRepository.findByUsername("u")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            SavedPlayQueue saved = invocation.getArgument(0, SavedPlayQueue.class);
            saved.setId(1);
            return saved;
        }).when(savedPlayQueueRepository).save(any());

        playQueueService.savePlayQueueByIndex("u", mediaFileIds, 1, 0L, "c");

        ArgumentCaptor<SavedPlayQueue> captor = ArgumentCaptor.forClass(SavedPlayQueue.class);
        verify(savedPlayQueueRepository).save(captor.capture());
        SavedPlayQueue saved = captor.getValue();
        assertEquals(2, saved.getMediaFiles().size());
        assertNull(saved.getCurrentIndex());
        assertNull(saved.getCurrentMediaFile());
    }

    @Nested
    class DoPlayDefensiveCopyTests {

        @BeforeEach
        public void setup() {
            when(mockedPlayer.isWeb()).thenReturn(true);
            when(mockedPlayer.getUsername()).thenReturn("testuser");
            when(mockedPlayer.getId()).thenReturn(1);
            when(mockedPlayer.getPlayQueue()).thenReturn(mockedPlayQueue);
            // the mutation doPlay performs must be the production one, not a mock no-op
            doCallRealMethod().when(mediaFileService).removeVideoFiles(any());
            when(webSocketClient.sendToUser(anyString(), anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
        }

        private MediaFile mediaFile(int id, MediaType mediaType) {
            MediaFile file = new MediaFile();
            file.setId(id);
            file.setMediaType(mediaType);
            file.setPresent(true);
            return file;
        }

        @Test
        public void testPlayStarredSurvivesUnmodifiableStarredSongsList() {
            // given the production shape: getStarredSongs returns an unmodifiable list (#371)
            MediaFile song = mediaFile(1, MediaType.MUSIC);
            MediaFile video = mediaFile(2, MediaType.VIDEO);
            List<MusicFolder> folders = List.of();
            when(mediaFolderService.getMusicFoldersForUser("testuser")).thenReturn(folders);
            when(mediaFileService.getStarredSongs(0, Integer.MAX_VALUE, "testuser", folders))
                    .thenReturn(List.copyOf(List.of(song, video)));

            // when
            playQueueService.playStarred(mockedPlayer, "sessionId");

            // then: the queue got the starred songs minus the video, nothing thrown
            verify(mockedPlayQueue).addFiles(eq(false), queuedFilesCaptor.capture());
            assertEquals(List.of(song), List.copyOf(queuedFilesCaptor.getValue()));
        }

        @Test
        public void testPlayPlaylistByIndexSkipsMissingFileWithoutThrowing() {
            MediaFile missing = mediaFile(3, MediaType.MUSIC);
            missing.setPresent(false);
            UserSettings settings = new UserSettings();
            settings.setQueueFollowingSongs(false);
            when(personalSettingsService.getUserSettings("testuser")).thenReturn(settings);
            when(playlistService.getFilesInPlaylist(8, true)).thenReturn(new ArrayList<>(List.of(missing)));

            assertDoesNotThrow(() -> playQueueService.playPlaylist(mockedPlayer, 8, 0, "sessionId"));

            verify(mockedPlayQueue).addFiles(eq(false), queuedFilesCaptor.capture());
            assertEquals(List.of(), List.copyOf(queuedFilesCaptor.getValue()));
        }

        @Test
        public void testPlayPlaylistByIndexQueuesThePresentFile() {
            MediaFile other = mediaFile(4, MediaType.MUSIC);
            MediaFile song = mediaFile(5, MediaType.MUSIC);
            UserSettings settings = new UserSettings();
            settings.setQueueFollowingSongs(false);
            when(personalSettingsService.getUserSettings("testuser")).thenReturn(settings);
            when(playlistService.getFilesInPlaylist(8, true)).thenReturn(new ArrayList<>(List.of(other, song)));

            playQueueService.playPlaylist(mockedPlayer, 8, 1, "sessionId");

            verify(mockedPlayQueue).addFiles(eq(false), queuedFilesCaptor.capture());
            assertEquals(List.of(song), List.copyOf(queuedFilesCaptor.getValue()));
        }

        @Test
        public void testEveryDoPlayCallerSurvivesAnUnmodifiableList() {
            MediaFile song = mediaFile(1, MediaType.MUSIC);
            List<MusicFolder> folders = List.of();
            UserSettings settings = new UserSettings();
            when(mediaFolderService.getMusicFoldersForUser("testuser")).thenReturn(folders);
            when(mediaFolderService.getMusicFoldersForUser("testuser", -1)).thenReturn(folders);
            when(personalSettingsService.getUserSettings("testuser")).thenReturn(settings);

            MediaFile directory = mediaFile(10, MediaType.DIRECTORY);
            when(mediaFileService.getMediaFile(10)).thenReturn(directory);
            when(mediaFileService.getDescendantsOf(directory, true)).thenReturn(List.of(song));

            MediaFile topSongArtist = mediaFile(11, MediaType.DIRECTORY);
            when(mediaFileService.getMediaFile(11)).thenReturn(topSongArtist);
            when(lastFmService.getTopSongsByMediaFile(topSongArtist, 50, folders)).thenReturn(List.of(song));

            MediaFile randomParent = mediaFile(12, MediaType.DIRECTORY);
            when(mediaFileService.getMediaFile(12)).thenReturn(randomParent);
            when(mediaFileService.getRandomSongsForParent(randomParent, 3)).thenReturn(List.of(song));

            MediaFile similarArtist = mediaFile(13, MediaType.DIRECTORY);
            when(mediaFileService.getMediaFile(13)).thenReturn(similarArtist);
            when(lastFmService.getSimilarSongsByMediaFile(similarArtist, 3, folders)).thenReturn(List.of(song));

            MediaFile album = mediaFile(14, MediaType.ALBUM);
            when(searchService.getRandomAlbums(40, folders)).thenReturn(List.of(album));
            when(mediaFileService.getVisibleChildrenOf(album, false, false)).thenReturn(List.of(song));

            when(playlistService.getFilesInPlaylist(8, true)).thenReturn(List.of(song));

            PodcastChannel channel = new PodcastChannel(5, "https://example.com/feed", "channel", null, null,
                    PodcastStatus.COMPLETED, null, null);
            PodcastEpisode episode = new PodcastEpisode(21, channel, "guid", "https://example.com/episode",
                    mediaFile(15, MediaType.MUSIC), "episode", null, null, null, null, null,
                    PodcastStatus.COMPLETED, null);
            when(podcastService.getEpisodes(5)).thenReturn(List.of(episode));
            when(podcastService.getEpisode(21, false)).thenReturn(episode);

            InternetRadio radio = new InternetRadio("radio", "https://example.com/stream", null, true, null);
            radio.setId(7);
            when(internetRadioRepository.findByIdAndEnabledTrue(7)).thenReturn(Optional.of(radio));

            Map<String, Executable> callers = new LinkedHashMap<>();
            callers.put("playMediaFile", () -> playQueueService.playMediaFile(mockedPlayer, 10, "sessionId"));
            callers.put("playTopSong", () -> playQueueService.playTopSong(mockedPlayer, 11, null, "sessionId"));
            callers.put("playRandom", () -> playQueueService.playRandom(mockedPlayer, 12, 3, "sessionId"));
            callers.put("playSimilar", () -> playQueueService.playSimilar(mockedPlayer, 13, 3, "sessionId"));
            callers.put("playShuffle", () -> playQueueService.playShuffle(mockedPlayer, "random", 0, 40, null, null, "sessionId"));
            callers.put("playPlaylist", () -> playQueueService.playPlaylist(mockedPlayer, 8, null, "sessionId"));
            callers.put("playPodcastChannel", () -> playQueueService.playPodcastChannel(mockedPlayer, 5, "sessionId"));
            callers.put("playPodcastEpisode", () -> playQueueService.playPodcastEpisode(mockedPlayer, 21, "sessionId"));
            callers.put("playInternetRadio", () -> playQueueService.playInternetRadio(mockedPlayer, 7, null, "sessionId"));

            callers.forEach((name, call) -> assertDoesNotThrow(call, name + " must tolerate an unmodifiable list"));
        }
    }

    @Test
    public void testSavePlayQueueByIndexEmptyQueueWithNullIndex() throws Exception {
        when(savedPlayQueueRepository.findByUsername("u")).thenReturn(Optional.empty());
        doAnswer(invocation -> {
            SavedPlayQueue saved = invocation.getArgument(0, SavedPlayQueue.class);
            saved.setId(1);
            return saved;
        }).when(savedPlayQueueRepository).save(any());

        playQueueService.savePlayQueueByIndex("u", List.of(), null, 0L, "c");

        ArgumentCaptor<SavedPlayQueue> captor = ArgumentCaptor.forClass(SavedPlayQueue.class);
        verify(savedPlayQueueRepository).save(captor.capture());
        SavedPlayQueue saved = captor.getValue();
        assertEquals(0, saved.getMediaFiles().size());
        assertNull(saved.getCurrentIndex());
        assertNull(saved.getCurrentMediaFile());
    }

}
