package org.airsonic.player.service.playlist;

import chameleon.content.Content;
import chameleon.playlist.Media;
import chameleon.playlist.Playlist;
import chameleon.playlist.SpecificPlaylist;
import chameleon.playlist.SpecificPlaylistProvider;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.PlaylistMediaFile;
import org.airsonic.player.repository.PlaylistRepository;
import org.airsonic.player.util.StringUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultPlaylistExportHandler implements PlaylistExportHandler {

    @Autowired
    private PlaylistRepository playlistRepository;

    @Override
    public boolean canHandle(Class<? extends SpecificPlaylistProvider> providerClass) {
        return true;
    }

    @Override
    @Transactional
    public SpecificPlaylist handle(int id, SpecificPlaylistProvider provider) throws Exception {
        chameleon.playlist.Playlist playlist = createChameleonGenericPlaylistFromDBId(id);
        return provider.toSpecificPlaylist(playlist);
    }

    private Playlist createChameleonGenericPlaylistFromDBId(int id) {
        Playlist newPlaylist = new Playlist();
        List<MediaFile> files = playlistRepository.findById(id).map(playlist -> {
            return playlist.getPlaylistMediaFiles().stream().map(PlaylistMediaFile::getMediaFile).toList();
        }).orElseGet(() -> {
            return new ArrayList<>();
        });
        files.forEach(file -> {
            Media component = new Media();
            Content content = new Content(file.getFullPath().toString());
            if (file.getDuration() != null) {
                content.setDuration((long) (file.getDuration() * 1000));
            }
            if (file.getFileSize() != null) {
                content.setLength(file.getFileSize());
            }
            if (file.getChanged() != null) {
                content.setLastModified(file.getChanged().toEpochMilli());
            }
            if (file.getHeight() != null) {
                content.setHeight(file.getHeight());
            }
            if (file.getWidth() != null) {
                content.setWidth(file.getWidth());
            }
            if (file.getFormat() != null) {
                content.setType(StringUtil.getMimeType(file.getFormat()));
            }
            component.setSource(content);
            newPlaylist.getRootSequence().addComponent(component);
        });
        return newPlaylist;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
