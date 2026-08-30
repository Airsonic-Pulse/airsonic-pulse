/* This file is part of Airsonic.  Airsonic is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 Airsonic is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.

 Copyright 2023 (C) Y.Tory
 */

package org.airsonic.player.service;

import org.airsonic.player.command.PodcastSettingsCommand.PodcastRule;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastChannelRule;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.PodcastStatus;
import org.airsonic.player.repository.PodcastChannelRepository;
import org.airsonic.player.repository.PodcastEpisodeRepository;
import org.airsonic.player.repository.PodcastRuleRepository;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.EnumSource.Mode;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PodcastPersistenceServiceTestCase {

    @Mock
    private SettingsService settingsService;
    @Mock
    private TaskSchedulingService taskService;
    @Mock
    private PodcastChannelRepository podcastChannelRepository;
    @Mock
    private PodcastRuleRepository podcastRuleRepository;
    @Mock
    private PodcastEpisodeRepository podcastEpisodeRepository;
    @Mock
    private SecurityService securityService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private AsyncWebSocketClient asyncSocketClient;
    @Mock
    private VersionService versionService;

    @InjectMocks
    private PodcastPersistenceService podcastService;

    @Mock
    private PodcastEpisode mockedEpisode;
    @Mock
    private MediaFile mockedMediaFile;
    @Mock
    private MusicFolder mockedMusicFolder;
    @Mock
    private PodcastChannel mockedChannel;
    @Mock
    private CloseableHttpClient mockedHttpClient;
    @Mock
    private CloseableHttpResponse mockedHttpResponse;
    @Mock
    private HttpEntity mockedHttpEntity;
    @Mock
    private PodcastChannelRule mockedChannelRule;

    @TempDir
    private Path tempFolder;

    // test data
    private PodcastChannelRule RULE_SCHEDULE = new PodcastChannelRule(1, 1, 1, 1);

    @Test
    void testCreateOrUpdateChannelRule() {
        // given
        // prepare instant
        PodcastChannel channel = new PodcastChannel();
        PodcastChannelRule rule = new PodcastChannelRule(1);

        when(podcastChannelRepository.findById(1)).thenReturn(Optional.of(channel));
        when(podcastRuleRepository.findById(1)).thenReturn(Optional.of(rule));
        PodcastRule command = new PodcastRule(RULE_SCHEDULE, "test");
        command.setId(1);

        // when
        PodcastChannelRule actual = podcastService.createOrUpdateChannelRuleByCommand(command);

        // then
        verify(podcastRuleRepository).save(any(PodcastChannelRule.class));
        assertEquals(1, actual.getCheckInterval());
        assertEquals(1, actual.getDownloadCount());
        assertEquals(1, actual.getRetentionCount());
        assertEquals(1, actual.getId());

    }

    @Test
    void testDeleteChannel() throws Exception {

        // given
        int channelId = 1;
        when(podcastChannelRepository.findById(channelId)).thenReturn(Optional.of(mockedChannel));
        when(podcastEpisodeRepository.findByChannel(mockedChannel)).thenReturn(Arrays.asList(mockedEpisode));
        when(mockedEpisode.getMediaFile()).thenReturn(null);
        when(mockedChannel.getMediaFile()).thenReturn(mockedMediaFile);
        when(mockedMediaFile.getFullPath()).thenReturn(tempFolder.resolve("test.mp3"));
        Files.createFile(tempFolder.resolve("test.mp3"));

        // when
        boolean actual = podcastService.deleteChannel(channelId);

        // then
        verify(podcastChannelRepository).delete(mockedChannel);
        verify(mediaFileService).delete(mockedMediaFile);
        assertFalse(Files.exists(tempFolder.resolve("test.mp3")));
        assertTrue(actual);
    }

    @Test
    void deleteChannelRunsEpisodeDeletesOnCallingThread() throws Exception {
        // #345: the episode loop used parallelStream(), so mediaFileService.delete ran on
        // ForkJoin common-pool workers. Transaction context is thread-bound, so those calls
        // opened their own transactions and committed mid-delete, colliding with the outer
        // @Transactional's flush (MariaDB snapshot isolation rejects the write with "Record
        // has changed since last read", #330). This class has no transaction manager, so
        // thread identity is the proxy for transaction membership: entity work on the calling
        // thread joins the outer transaction; work on any other thread cannot.
        int episodeCount = 64;
        MusicFolder folder = new MusicFolder(1, tempFolder, "podcast", MusicFolder.Type.PODCAST, true, Instant.now());
        List<PodcastEpisode> episodes = new ArrayList<>();
        for (int i = 0; i < episodeCount; i++) {
            MediaFile mediaFile = new MediaFile();
            mediaFile.setPath("episode-" + i + ".mp3");
            mediaFile.setFolder(folder);
            PodcastEpisode episode = new PodcastEpisode();
            episode.setMediaFile(mediaFile);
            episodes.add(episode);
        }

        when(podcastChannelRepository.findById(1)).thenReturn(Optional.of(mockedChannel));
        when(podcastEpisodeRepository.findByChannel(mockedChannel)).thenReturn(episodes);
        when(securityService.isReadAllowed(any(MediaFile.class), eq(false))).thenReturn(true);
        when(mockedChannel.getMediaFile()).thenReturn(null);

        List<String> deleteThreads = Collections.synchronizedList(new ArrayList<>());
        when(mediaFileService.delete(any(MediaFile.class))).thenAnswer(invocation -> {
            deleteThreads.add(Thread.currentThread().getName());
            // brief pause so a parallel stream provably distributes work across pool workers
            Thread.sleep(1);
            return invocation.getArgument(0);
        });

        boolean actual = podcastService.deleteChannel(1);

        assertTrue(actual);
        assertEquals(episodeCount, deleteThreads.size());
        String callingThread = Thread.currentThread().getName();
        List<String> foreignThreads = deleteThreads.stream().filter(name -> !name.equals(callingThread)).distinct().toList();
        assertTrue(foreignThreads.isEmpty(),
                "episode entity deletes must run on the calling thread (outer transaction); ran on: " + foreignThreads);
    }

    @Test
    void testDeleteChannelRule() {
        // given
        when(podcastRuleRepository.findById(10)).thenReturn(Optional.of(RULE_SCHEDULE));

        // when
        boolean result = podcastService.deleteChannelRule(10);

        // then
        verify(podcastRuleRepository).delete(RULE_SCHEDULE);
        assertTrue(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    void testDeleteEpisodeWithNullShouldDoNothing(boolean logicalDelete) {
        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.empty());

        // when
        podcastService.deleteEpisode(1, logicalDelete);

        // then
        verifyNoMoreInteractions(podcastEpisodeRepository);
    }

    @Test
    public void testDeleteEpisodeForLogicalDeleteShouldUpdateEpisode() throws Exception {
        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.of(mockedEpisode));
        when(mockedEpisode.getMediaFile()).thenReturn(mockedMediaFile);
        when(mockedMediaFile.getFullPath()).thenReturn(tempFolder.resolve("test.mp3"));
        Files.createFile(tempFolder.resolve("test.mp3"));
        assertTrue(Files.exists(tempFolder.resolve("test.mp3")));


        // when
        podcastService.deleteEpisode(1, true);

        // then
        verify(mediaFileService).delete(mockedMediaFile);
        verify(mockedEpisode).setStatus(PodcastStatus.DELETED);
        verify(mockedEpisode).setErrorMessage(null);
        verify(podcastEpisodeRepository).save(mockedEpisode);
        verifyNoMoreInteractions(podcastEpisodeRepository);
        assertFalse(Files.exists(tempFolder.resolve("test.mp3")));
    }

    @Test
    public void testDeleteEpisodeForDeleteShouldDeleteEpisode() throws Exception {
        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.of(mockedEpisode));
        when(mockedEpisode.getMediaFile()).thenReturn(mockedMediaFile);
        when(mockedMediaFile.getFullPath()).thenReturn(tempFolder.resolve("test.mp3"));
        Files.createFile(tempFolder.resolve("test.mp3"));
        assertTrue(Files.exists(tempFolder.resolve("test.mp3")));

        // when
        podcastService.deleteEpisode(1, false);

        // then
        verify(podcastEpisodeRepository).delete(mockedEpisode);
        verify(mediaFileService).delete(mockedMediaFile);
        assertFalse(Files.exists(tempFolder.resolve("test.mp3")));
        verifyNoMoreInteractions(podcastEpisodeRepository);
    }

    @Test
    void testPrepareDownloadEpisodeWithDeletedEpisodeShouldReturnNull() {

        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.of(mockedEpisode));
        when(mockedEpisode.getStatus()).thenReturn(PodcastStatus.DELETED);

        // when
        PodcastEpisode episode = podcastService.prepareDownloadEpisode(1);

        // then
        assertNull(episode);

    }

    @ParameterizedTest
    @EnumSource(value = PodcastStatus.class, names = {"DOWNLOADING", "COMPLETED"})
    public void testPrepareDownloadEpisodeWithDownloadingOrCompletedEpisodeShouldDoNothing(PodcastStatus status) {
        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.of(mockedEpisode));
        when(mockedEpisode.getStatus()).thenReturn(status);

        // when
        PodcastEpisode episode = podcastService.prepareDownloadEpisode(1);

        // then
        assertNull(episode);
    }

    @ParameterizedTest
    @EnumSource(value = PodcastStatus.class, mode = Mode.EXCLUDE, names = {"DELETED", "DOWNLOADING", "COMPLETED"})
    public void testPrepareDonwloadEpisodeShouldReturnDownloadingEpisode(PodcastStatus status) {

        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.of(mockedEpisode));
        when(mockedEpisode.getStatus()).thenReturn(status);

        // when
        PodcastEpisode episode = podcastService.prepareDownloadEpisode(1);

        // then
        assertEquals(mockedEpisode, episode);
        verify(mockedEpisode).setStatus(PodcastStatus.DOWNLOADING);
        verify(podcastEpisodeRepository).save(mockedEpisode);
    }

    // testGetEpisode
    @ParameterizedTest
    @ValueSource(strings = {"true", "false"})
    public void testGetEpisodeWithNotFoundShouldReturnNull(boolean includeDeleted) {
        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.empty());

        // when
        PodcastEpisode episode = podcastService.getEpisode(1, includeDeleted);

        // then
        assertNull(episode);
        verify(podcastEpisodeRepository).findById(1);
    }

    @Test
    public void testGetEpisodeWithDeletedEpisodeShouldReturnNull() {
        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.of(mockedEpisode));
        when(mockedEpisode.getStatus()).thenReturn(PodcastStatus.DELETED);

        // when
        PodcastEpisode episode = podcastService.getEpisode(1, false);

        // then
        assertNull(episode);
        verify(podcastEpisodeRepository).findById(1);
    }

    @ParameterizedTest
    @EnumSource(value = PodcastStatus.class, mode = Mode.EXCLUDE, names = {"DELETED"})
    public void testGetEpisodeWithExcludeDeleteShouldReturnEpisode(PodcastStatus status) {
        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.of(mockedEpisode));
        when(mockedEpisode.getStatus()).thenReturn(status);

        // when
        PodcastEpisode episode = podcastService.getEpisode(1, false);

        // then
        assertEquals(mockedEpisode, episode);
        verify(podcastEpisodeRepository).findById(1);
    }

    @ParameterizedTest
    @EnumSource(value = PodcastStatus.class)
    public void testGetEpisodeWithIncludeDeletedShouldReturnEpisode(PodcastStatus status) {
        // given
        when(podcastEpisodeRepository.findById(1)).thenReturn(Optional.of(mockedEpisode));

        // when
        PodcastEpisode episode = podcastService.getEpisode(1, true);

        // then
        assertEquals(mockedEpisode, episode);
        verify(mockedEpisode, never()).getStatus();
        verify(podcastEpisodeRepository).findById(1);
    }

    // testGetEpisodes
    @Test
    void testGetEpisodesWithNullIdShouldReturnEmptyList() {

        // when
        List<PodcastEpisode> episodes = podcastService.getEpisodes(null);

        // then
        assertEquals(0, episodes.size());
        verify(podcastEpisodeRepository, never()).findById(anyInt());
    }

    @Test
    void testGetEpisodesWithIdShouldReturnListWithNullMediaId() {
        // given
        when(podcastChannelRepository.findById(1)).thenReturn(Optional.of(mockedChannel));
        when(podcastEpisodeRepository.findByChannel(mockedChannel)).thenReturn(Arrays.asList(mockedEpisode));
        when(mockedEpisode.getMediaFile()).thenReturn(null);

        // when
        List<PodcastEpisode> episodes = podcastService.getEpisodes(1);

        // then
        assertEquals(1, episodes.size());
        assertEquals(mockedEpisode, episodes.get(0));
        verify(podcastChannelRepository).findById(1);
    }

    @Test
    void testGetEpisodesWithIdShouldReturnListWithAllowedMediaId() {
        // given
        when(podcastChannelRepository.findById(1)).thenReturn(Optional.of(mockedChannel));
        when(podcastEpisodeRepository.findByChannel(mockedChannel)).thenReturn(Arrays.asList(mockedEpisode));
        // setup mediafile allowed
        when(mockedEpisode.getMediaFile()).thenReturn(mockedMediaFile);
        when(securityService.isReadAllowed(mockedMediaFile, false)).thenReturn(true);

        // when
        List<PodcastEpisode> episodes = podcastService.getEpisodes(1);

        // then
        assertEquals(1, episodes.size());
        assertEquals(mockedEpisode, episodes.get(0));
        verify(podcastChannelRepository).findById(1);
    }

    @Test
    void testGetEpisodesWithDisallowedMediafileIdShouldReturnEmptyList() {
        // given
        when(podcastChannelRepository.findById(1)).thenReturn(Optional.of(mockedChannel));
        when(podcastEpisodeRepository.findByChannel(mockedChannel)).thenReturn(Arrays.asList(mockedEpisode));
        // setup mediafile allowed
        when(mockedEpisode.getMediaFile()).thenReturn(mockedMediaFile);
        when(securityService.isReadAllowed(mockedMediaFile, false)).thenReturn(false);

        // when
        List<PodcastEpisode> episodes = podcastService.getEpisodes(1);

        // then
        assertEquals(0, episodes.size());
        verify(podcastChannelRepository).findById(1);
    }

    // testGetNewestEpisodes
    @Test
    void testGetNewestEpisodes() {

        // given
        int count = 2;
        when(podcastEpisodeRepository.findByStatusAndPublishDateNotNullAndMediaFilePresentTrueOrderByPublishDateDesc(
                PodcastStatus.COMPLETED)).thenReturn(Arrays.asList(mockedEpisode, mockedEpisode, mockedEpisode));

        // when
        List<PodcastEpisode> episodes = podcastService.getNewestEpisodes(count);

        // then
        assertEquals(2, episodes.size());
        verify(mediaFileService, never()).getMediaFile(anyInt());
    }

    @Test
    public void testDeleteObsoleteEpisodes() {
        // Mocking the channel and episodes
        when(mockedChannel.getId()).thenReturn(1);
        when(mockedChannelRule.getRetentionCount()).thenReturn(5);
        when(podcastRuleRepository.findById(1)).thenReturn(Optional.of(mockedChannelRule));

        List<PodcastEpisode> episodes = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            episodes.add(mockedEpisode);
        }
        when(podcastEpisodeRepository.findByChannelAndLockedFalse(mockedChannel)).thenReturn(episodes);

        // Calling the method to be tested
        podcastService.deleteObsoleteEpisodes(mockedChannel);

        // Verifying the method calls
        verify(mockedEpisode, times(10)).setStatus(PodcastStatus.DELETED);
        verify(mockedEpisode, times(10)).setErrorMessage(null);
        verify(podcastEpisodeRepository, times(10)).save(mockedEpisode);
    }

    @Test
    public void testDeleteObsoleteEpisodesWithRetentionCountUnlimitedShouldDoNothing() {
        // Mocking the channel and episodes
        when(mockedChannel.getId()).thenReturn(1);
        when(mockedChannelRule.getRetentionCount()).thenReturn(-1);
        when(podcastRuleRepository.findById(1)).thenReturn(Optional.of(mockedChannelRule));

        // Calling the method to be tested
        podcastService.deleteObsoleteEpisodes(mockedChannel);

        // Verifying the method calls
        verifyNoInteractions(mockedEpisode);
        verifyNoInteractions(podcastEpisodeRepository);
    }

    @Test
    public void testDeleteObsoleteEpisodesWithDownloadingEpisodeShouldDoNothing() {
        // Mocking the channel and episodes
        when(mockedChannel.getId()).thenReturn(1);
        when(mockedChannelRule.getRetentionCount()).thenReturn(5);
        when(podcastRuleRepository.findById(1)).thenReturn(Optional.of(mockedChannelRule));

        List<PodcastEpisode> episodes = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            PodcastEpisode episode = new PodcastEpisode();
            episode.setStatus(PodcastStatus.DOWNLOADING);
        }
        when(podcastEpisodeRepository.findByChannelAndLockedFalse(mockedChannel)).thenReturn(episodes);

        // Calling the method to be tested
        podcastService.deleteObsoleteEpisodes(mockedChannel);

        // Verifying the method calls
        verifyNoMoreInteractions(podcastEpisodeRepository);
    }

    @Test
    public void testDeleteObsoleteEpisodesWithRetentionEnoughShouldDoNothing() {
        // Mocking the channel and episodes
        when(mockedChannel.getId()).thenReturn(1);
        when(mockedChannelRule.getRetentionCount()).thenReturn(5);
        when(podcastRuleRepository.findById(1)).thenReturn(Optional.of(mockedChannelRule));

        List<PodcastEpisode> episodes = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            PodcastEpisode episode = new PodcastEpisode();
            episode.setStatus(PodcastStatus.DOWNLOADING);
        }
        when(podcastEpisodeRepository.findByChannelAndLockedFalse(mockedChannel)).thenReturn(episodes);

        // Calling the method to be tested
        podcastService.deleteObsoleteEpisodes(mockedChannel);

        // Verifying the method calls
        verifyNoMoreInteractions(podcastEpisodeRepository);
    }
}
