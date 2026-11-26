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
package org.airsonic.player.service.sonos;

import com.sonos.services._1.AbstractMedia;
import com.sonos.services._1.MediaMetadata;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.service.JWTSecurityService;
import org.airsonic.player.service.LastFmService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.TranscodingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class SonosHelperTest {

    @Mock
    private MediaFileService mediaFileService;

    @Mock
    private PlayerService playerService;

    @Mock
    private TranscodingService transcodingService;

    @Mock
    private JWTSecurityService jwtSecurityService;

    @Mock
    private MediaFolderService mediaFolderService;

    @Mock
    private LastFmService lastFmService;

    @InjectMocks
    private SonosHelper sonosHelper;

    private final MockHttpServletRequest request = new MockHttpServletRequest();

    @BeforeEach
    public void setUp() {
        Player player = new Player();
        player.setId(1);
        when(playerService.getPlayersForUserAndClientId(anyString(), anyString())).thenReturn(List.of(player));
        lenient().when(transcodingService.getSuffix(any(), any(), any())).thenReturn("mp3");
        lenient().when(jwtSecurityService.addJWTToken(anyString(), anyString())).thenReturn("uri");
    }

    // duration is minOccurs="0" in the Sonos WSDL; a null MediaFile duration must be
    // omitted from the track metadata, not NPE the whole browse response (#322)
    @Test
    public void forSongWithNullDurationOmitsDuration() {
        MediaFile song = new MediaFile();
        song.setId(5);
        song.setTitle("song");
        song.setMediaType(MediaFile.MediaType.MUSIC);
        // duration stays null: unknown-length file

        MediaMetadata result = sonosHelper.forSong(song, "user", request);

        assertNull(result.getTrackMetadata().getDuration());
    }

    @Test
    public void forSongWithDurationRoundsToSeconds() {
        MediaFile song = new MediaFile();
        song.setId(5);
        song.setTitle("song");
        song.setMediaType(MediaFile.MediaType.MUSIC);
        song.setDuration(200.6);

        MediaMetadata result = sonosHelper.forSong(song, "user", request);

        assertEquals(201, result.getTrackMetadata().getDuration());
    }

    private MediaFile song(int id) {
        MediaFile song = new MediaFile();
        song.setId(id);
        song.setTitle("song " + id);
        song.setMediaType(MediaFile.MediaType.MUSIC);
        return song;
    }

    private Set<String> idsOf(List<AbstractMedia> media) {
        return media.stream().map(AbstractMedia::getId).collect(Collectors.toSet());
    }

    // Shuffle Play on an artist: the filtered song list is unmodifiable and must be copied
    // before it is shuffled (#414)
    @Test
    public void forShuffleArtistReturnsEverySongWithoutThrowing() {
        MediaFile artist = new MediaFile();
        artist.setId(1);
        when(mediaFileService.getMediaFile(1)).thenReturn(artist);
        when(mediaFileService.getDescendantsOf(artist, false)).thenReturn(List.of(song(11), song(12), song(13)));

        List<AbstractMedia> result = sonosHelper.forShuffleArtist(1, 10, "user", request);

        assertEquals(3, result.size());
        assertEquals(Set.of("11", "12", "13"), idsOf(result));
    }

    // Artist Radio: same shape, with the songs coming from the similar-songs lookup (#414)
    @Test
    public void forRadioArtistReturnsEverySongWithoutThrowing() {
        MediaFile artist = new MediaFile();
        artist.setId(1);
        when(mediaFileService.getMediaFile(1)).thenReturn(artist);
        when(mediaFolderService.getMusicFoldersForUser("user")).thenReturn(List.of());
        when(lastFmService.getSimilarSongsByMediaFile(artist, 10, List.of()))
                .thenReturn(List.of(song(21), song(22), song(23)));

        List<AbstractMedia> result = sonosHelper.forRadioArtist(1, 10, "user", request);

        assertEquals(3, result.size());
        assertEquals(Set.of("21", "22", "23"), idsOf(result));
    }
}
