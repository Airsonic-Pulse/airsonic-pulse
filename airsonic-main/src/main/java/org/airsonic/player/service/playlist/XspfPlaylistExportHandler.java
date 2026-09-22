package org.airsonic.player.service.playlist;

import chameleon.playlist.SpecificPlaylist;
import chameleon.playlist.SpecificPlaylistProvider;
import chameleon.playlist.xspf.Location;
import chameleon.playlist.xspf.Track;
import chameleon.playlist.xspf.XspfProvider;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.repository.PlaylistRepository;
import org.airsonic.player.service.CoverArtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

@Component
public class XspfPlaylistExportHandler implements PlaylistExportHandler {

    private static Logger LOG = LoggerFactory.getLogger(XspfPlaylistExportHandler.class);

    @Autowired
    private PlaylistRepository playlistRepository;

    @Autowired
    private CoverArtService coverArtService;

    @Override
    public boolean canHandle(Class<? extends SpecificPlaylistProvider> providerClass) {
        return XspfProvider.class.equals(providerClass);
    }

    @Override
    @Transactional
    public SpecificPlaylist handle(int id, SpecificPlaylistProvider provider) {
        return createXsfpPlaylistFromDBId(id);
    }

    private chameleon.playlist.xspf.Playlist createXsfpPlaylistFromDBId(int id) {
        chameleon.playlist.xspf.Playlist newPlaylist = new chameleon.playlist.xspf.Playlist();
        Playlist playlist = playlistRepository.findById(id).orElseGet(() -> {
            LOG.error("Playlist with id {} not found", id);
            return null;
        });
        newPlaylist.setTitle(playlist.getName());
        newPlaylist.setCreator("Airsonic user " + playlist.getUsername());
        newPlaylist.setDate(Date.from(Instant.now())); //TODO switch to Instant upstream

        playlist.getPlaylistMediaFiles().stream().map(pmf -> {
            MediaFile mediaFile = pmf.getMediaFile();
            Track track = new Track();
            track.setTrackNumber(mediaFile.getTrackNumber());
            track.setCreator(mediaFile.getArtist());
            track.setTitle(mediaFile.getTitle());
            track.setAlbum(mediaFile.getAlbumName());
            Double duration = mediaFile.getDuration();
            if (duration != null) { // duration is optional in XSPF; omit rather than NPE (#322)
                track.setDuration((int) Math.round(duration)); // TODO switch to Double upstream
            }
            track.setImage(Optional.ofNullable(coverArtService.getMediaFileArtPath(mediaFile.getId())).map(p -> p.toString()).orElse(null));
            Location location = new Location();
            location.setText(mediaFile.getFullPath().toString());
            track.getStringContainers().add(location);
            return track;
        }).forEach(newPlaylist::addTrack);

        return newPlaylist;
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
