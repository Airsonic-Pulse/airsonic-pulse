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
package org.airsonic.player.service.playlist;

import chameleon.playlist.xspf.Track;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.PlaylistMediaFile;
import org.airsonic.player.repository.PlaylistRepository;
import org.airsonic.player.service.CoverArtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class XspfPlaylistExportHandlerTest {

    @Mock
    private PlaylistRepository playlistRepository;

    @Mock
    private CoverArtService coverArtService;

    @InjectMocks
    private XspfPlaylistExportHandler handler;

    @Mock
    private MusicFolder mockedFolder;

    private MediaFile song(int id, Double duration) {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(id);
        mediaFile.setPath("song" + id + ".mp3");
        mediaFile.setTitle("Song " + id);
        mediaFile.setFolder(mockedFolder);
        mediaFile.setDuration(duration);
        return mediaFile;
    }

    // duration is optional in XSPF (minOccurs="0"); a null MediaFile duration must be
    // exported as an absent element, not NPE the whole export (#322)
    @Test
    public void handleExportsTracksWithAndWithoutDuration() {
        lenient().when(mockedFolder.getPath()).thenReturn(Paths.get("/music"));

        Playlist playlist = new Playlist();
        playlist.setId(23);
        playlist.setName("playlist");
        playlist.setUsername("user");
        playlist.setPlaylistMediaFiles(List.of(
                new PlaylistMediaFile(playlist, song(1, 12.4), 0),
                new PlaylistMediaFile(playlist, song(2, null), 1)));

        when(playlistRepository.findById(23)).thenReturn(Optional.of(playlist));

        chameleon.playlist.xspf.Playlist result =
                (chameleon.playlist.xspf.Playlist) handler.handle(23, null);

        List<Track> tracks = result.getTracks();
        assertEquals(2, tracks.size());
        assertEquals(12, tracks.get(0).getDuration());
        assertNull(tracks.get(1).getDuration());
    }
}
