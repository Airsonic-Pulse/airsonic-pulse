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

  Copyright 2024 (C) Y.Tory
  Copyright 2017 (C) Airsonic Authors
  Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
*/
package org.airsonic.player.service.upnp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class UpnpProcessorRouterImpl implements UpnpProcessorRouter {

    @Autowired
    @Lazy
    private RootUpnpProcessor rootUpnpProcessor;
    @Lazy
    @Autowired
    private AlbumUpnpProcessor albumUpnpProcessor;
    @Lazy
    @Autowired
    private ArtistUpnpProcessor artistUpnpProcessor;
    @Lazy
    @Autowired
    private GenreUpnpProcessor genreUpnpProcessor;
    @Lazy
    @Autowired
    private MediaFileUpnpProcessor mediaFileUpnpProcessor;
    @Lazy
    @Autowired
    private PlaylistUpnpProcessor playlistUpnpProcessor;
    @Lazy
    @Autowired
    private RecentAlbumUpnpProcessor recentAlbumUpnpProcessor;

    @Override
    public UpnpContentProcessor<?, ?> findProcessor(ProcessorType type) {
        return switch (type) {
            case ROOT -> rootUpnpProcessor;
            case PLAYLIST -> playlistUpnpProcessor;
            case FOLDER, MEDIAFILE -> mediaFileUpnpProcessor;
            case ALBUM -> albumUpnpProcessor;
            case RECENT -> recentAlbumUpnpProcessor;
            case ARTIST -> artistUpnpProcessor;
            case GENRE -> genreUpnpProcessor;
            case ARTISTALBUM, UNKNOWN -> null;
        };
    }

    @Override
    public MediaFileUpnpProcessor getMediaFileProcessor() {
        return mediaFileUpnpProcessor;
    }

    @Override
    public ArtistUpnpProcessor getArtistProcessor() {
        return artistUpnpProcessor;
    }

    @Override
    public AlbumUpnpProcessor getAlbumProcessor() {
        return albumUpnpProcessor;
    }

    @Override
    public RecentAlbumUpnpProcessor getRecentProcessor() {
        return recentAlbumUpnpProcessor;
    }

    @Override
    public GenreUpnpProcessor getGenreProcessor() {
        return genreUpnpProcessor;
    }

    @Override
    public PlaylistUpnpProcessor getPlaylistProcessor() {
        return playlistUpnpProcessor;
    }

}
