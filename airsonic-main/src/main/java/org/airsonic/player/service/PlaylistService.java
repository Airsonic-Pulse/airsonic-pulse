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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.PlayQueue;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.PlaylistMediaFile;
import org.airsonic.player.domain.User;
import org.airsonic.player.repository.PlaylistMediaFileRepository;
import org.airsonic.player.repository.PlaylistRepository;
import org.airsonic.player.repository.UserRepository;
import org.airsonic.player.service.cache.PlaylistCache;
import org.airsonic.player.service.websocket.AsyncWebSocketClient;
import org.airsonic.player.util.LambdaUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Nonnull;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Provides services for loading and saving playlists to and from persistent storage.
 *
 * @author Sindre Mehus
 * @see PlayQueue
 */
@Service
public class PlaylistService {

    private static final Logger LOG = LoggerFactory.getLogger(PlaylistService.class);

    private final UserRepository userRepository;
    private final PlaylistRepository playlistRepository;
    private final AsyncWebSocketClient asyncWebSocketClient;
    private final PlaylistCache playlistCache;
    private final PlaylistMediaFileRepository playlistMediaFileRepository;
    private final MediaFileService mediaFileService;
    private final MediaFolderService mediaFolderService;
    private final PlayerService playerService;

    public PlaylistService(
        UserRepository userRepository,
        PlaylistRepository playlistRepository,
        AsyncWebSocketClient asyncWebSocketClient,
        PlaylistCache playlistCache,
        PlaylistMediaFileRepository playlistMediaFileRepository,
        PlayerService playerService,
        MediaFolderService mediaFolderService,
        MediaFileService mediaFileService
    ) {
        this.userRepository = userRepository;
        this.playlistRepository = playlistRepository;
        this.asyncWebSocketClient = asyncWebSocketClient;
        this.playlistCache = playlistCache;
        this.playlistMediaFileRepository = playlistMediaFileRepository;
        this.playerService = playerService;
        this.mediaFolderService = mediaFolderService;
        this.mediaFileService = mediaFileService;
    }


    /**
     * Returns all playlists.
     *
     * @return All playlists.
     */
    public List<Playlist> getAllPlaylists() {
        Sort sort = Sort.by("name").ascending();
        return playlistRepository.findAll(sort);
    }


    /**
     * Returns all playlists that the given user is allowed to read.
     *
     * @param username The user. If {@code null}, no playlists are returned.
     * @return All playlists that the given user is allowed to read.
     */
    public List<Playlist> getReadablePlaylistsForUser(String username) {

        if (username == null) {
            return Collections.emptyList();
        }

        List<Playlist> result1 = playlistRepository.findByUsername(username);
        List<Playlist> result2 = playlistRepository.findBySharedTrue();
        List<Playlist> result3 = playlistRepository.findByUsernameNotAndSharedUsersUsername(username, username);

        // Remove duplicates.
        return Stream.of(result1, result2, result3)
                .flatMap(r -> r.parallelStream())
                .filter(LambdaUtils.distinctByKey(p -> p.getId()))
                .sorted(Comparator.comparing(Playlist::getName))
                .toList();
    }

    /**
     * Returns all playlists that the given user is allowed to write.
     *
     * @param username The user. If {@code null}, no playlists are returned.
     * @return
     */
    public List<Playlist> getWritablePlaylistsForUser(String username) {
        return userRepository.findByUsername(username).map(user -> {
            if (user.isAdminRole()) {
                return getReadablePlaylistsForUser(username);
            } else {
                return playlistRepository.findByUsernameOrderByNameAsc(username);
            }
        }).orElseGet(() -> {
            LOG.warn("User {} not found", username);
            return new ArrayList<>();
        });

    }

    public Playlist getPlaylist(Integer id) {
        Playlist playlist = playlistCache.getPlaylistById(id);
        if (playlist == null) {
            playlist = playlistRepository.findById(id).orElse(null);
            playlistCache.putPlaylistById(id, playlist);
        }
        return playlist;
    }

    @Transactional
    public List<String> getPlaylistUsers(Integer playlistId) {

        List<String> result = playlistCache.getUsersForPlaylist(playlistId);

        if (CollectionUtils.isEmpty(result)) {
            List<User> users = playlistRepository.findById(playlistId).map(Playlist::getSharedUsers).orElse(Collections.emptyList());
            result = users.stream().map(User::getUsername).filter(Objects::nonNull).toList();
            playlistCache.putUsersForPlaylist(playlistId, result);
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<MediaFile> getFilesInPlaylist(int id) {
        return getFilesInPlaylist(id, false);
    }


    @Transactional(readOnly = true)
    public List<MediaFile> getFilesInPlaylist(int id, boolean includeNotPresent) {
        return playlistMediaFileRepository.findMediaFilesByPlaylistId(id).stream()
                .filter(Objects::nonNull)
                .filter(x -> x.isPresent() || includeNotPresent)
                .toList();
    }

    private List<MediaFile> filterNoDurationFiles(List<MediaFile> files) {
        List<MediaFile> result = new ArrayList<>();
        for (MediaFile file : files) {
            if (file.getDuration() == null) {
                LOG.warn("File {} has no duration", file.getId());
            } else {
                result.add(file);
            }
        }
        return result;
    }

    @Transactional
    public Playlist setFilesInPlaylist(int id, List<MediaFile> files) {
        playlistCache.removePlaylistById(id);
        return playlistRepository.findById(id).map(p -> {
            Playlist playlist = setFilesInPlaylist(p, files);
            playlistRepository.saveAndFlush(playlist);
            return playlist;
        }).orElseGet(
            () -> {
                LOG.warn("Playlist {} not found", id);
                return null;
            });
    }

    private Playlist setFilesInPlaylist(Playlist playlist, List<MediaFile> files) {

        List<MediaFile> filteredFiles = filterNoDurationFiles(files);
        int fileCount = filteredFiles.size();
        List<PlaylistMediaFile> playlistMediaFiles =
            playlist.getPlaylistMediaFiles().stream()
                .limit(fileCount).collect(Collectors.toList());
        // update order index and media file
        int orderIndex = 0;
        for (MediaFile file : filteredFiles) {
            if (orderIndex < playlistMediaFiles.size()) {
                PlaylistMediaFile pmf = playlistMediaFiles.get(orderIndex);
                pmf.setMediaFile(file);
                pmf.setOrderIndex(orderIndex);
            } else {
                PlaylistMediaFile pmf = new PlaylistMediaFile(playlist, file, orderIndex);
                playlistMediaFiles.add(pmf);
            }
            orderIndex++;
        }
        playlist.setPlaylistMediaFiles(playlistMediaFiles);
        playlist.setFileCount(filteredFiles.size());
        playlist.setDuration(filteredFiles.stream().mapToDouble(MediaFile::getDuration).sum());
        playlist.setChanged(Instant.now());
        return playlist;
    }

    @Transactional
    public void removeFilesInPlaylistByIndices(Integer id, List<Integer> indices) {
        playlistCache.removePlaylistById(id);
        playlistRepository.findById(id).ifPresentOrElse(p -> {
            List<PlaylistMediaFile> files = p.getPlaylistMediaFiles();
            List<MediaFile> newFiles = new ArrayList<>();
            for (int i = 0; i < files.size(); i++) {
                if (!indices.contains(i)) {
                    newFiles.add(files.get(i).getMediaFile());
                }
            }
            Playlist playlist = setFilesInPlaylist(p, newFiles);
            playlistRepository.save(playlist);
        }, () -> {
                LOG.warn("Playlist {} not found", id);
            }
        );
    }

    /**
     * Refreshes the file count and duration of all playlists.
     */
    @Transactional
    public List<Playlist> refreshPlaylistsStats() {
        return playlistRepository.findAll().stream().map(p -> {
            p.setFileCount(p.getPlaylistMediaFiles().size());
            p.setDuration(p.getPlaylistMediaFiles().stream().map(PlaylistMediaFile::getMediaFile).filter(f -> f.getDuration() != null).mapToDouble(MediaFile::getDuration).sum());
            p.setChanged(Instant.now());
            playlistRepository.save(p);
            return p;
        }).toList();
    }

    /**
     * Creates a new playlist.
     *
     * @param name   the playlist name
     * @param shared if true, the playlist is shared with other users
     * @param username the username of the user that created the playlist
     * @return the created playlist
     */
    @Transactional
    public Playlist createPlaylist(String name, boolean shared, String username) {
        Instant now = Instant.now();
        Playlist playlist = new Playlist();
        playlist.setName(name);
        playlist.setShared(shared);
        playlist.setUsername(username);
        playlist.setCreated(now);
        playlist.setChanged(now);
        playlistRepository.save(playlist);
        return playlist;
    }


    /**
     * Creates a new playlist.
     * @param playlist
     */
    @Transactional
    public Playlist createPlaylist(Playlist playlist) {
        Instant now = Instant.now();
        playlist.setCreated(now);
        playlist.setChanged(now);
        playlistRepository.save(playlist);
        if (playlist.getShared()) {
            asyncWebSocketClient.send("/topic/playlists/updated", playlist);
        } else {
            asyncWebSocketClient.sendToUser(playlist.getUsername(), "/queue/playlists/updated", playlist);
        }
        return playlist;
    }

    @Transactional
    public void addPlaylistUser(@Nonnull Playlist playlist, @Nonnull String username) {
        playlistCache.removeUsersForPlaylist(playlist.getId());
        userRepository.findByUsername(username).ifPresentOrElse(user -> {
            playlistRepository.findById(playlist.getId()).ifPresentOrElse(p -> {
                if (!p.getSharedUsers().contains(user)) {
                    p.addSharedUser(user);
                    playlistRepository.save(p);
                    // this might cause dual notifications on the client if the playlist is already public
                    asyncWebSocketClient.sendToUser(username, "/queue/playlists/updated", p);
                } else {
                    LOG.info("Playlist {} already shared with {}", playlist.getId(), username);
                }
            }, () -> {
                    LOG.warn("Playlist {} not found", playlist.getId());
                }
            );
        }, () -> {
                LOG.warn("User {} not found", username);
            }
        );
    }

    @Transactional
    public void deletePlaylistUser(@Nonnull Playlist playlist, @Nonnull String username) {
        playlistCache.removeUsersForPlaylist(playlist.getId());
        playlistRepository.findByIdAndSharedUsersUsername(playlist.getId(), username).ifPresentOrElse(p -> {
            p.removeSharedUserByUsername(username);
            playlistRepository.save(p);
            if (!p.getShared()) {
                asyncWebSocketClient.sendToUser(username, "/queue/playlists/deleted", p.getId());
            }
        }, () -> {
                LOG.warn("Playlist {} shared with {} not found", playlist.getId(), username);
            }
        );
    }

    public boolean isReadAllowed(Playlist playlist, String username) {
        if (username == null) {
            return false;
        }
        if (username.equals(playlist.getUsername()) || playlist.getShared()) {
            return true;
        }
        return getPlaylistUsers(playlist.getId()).contains(username);
    }

    /**
     * Returns true if the playlist exists.
     *
     * @param id the playlist id
     * @return true if the playlist exists
     */
    @Transactional(readOnly = true)
    public boolean isExist(Integer id) {
        return id != null && playlistRepository.existsById(id);
    }

    public boolean isWriteAllowed(Integer id, String username) {
        return username != null && playlistRepository.existsByIdAndUsername(id, username);
    }

    public boolean isWriteAllowed(Playlist playlist, String username) {
        return username != null && username.equals(playlist.getUsername());
    }

    @Transactional
    public void deletePlaylist(@Nonnull Integer id) {
        playlistCache.removePlaylistById(id);
        playlistCache.removeUsersForPlaylist(id);
        playlistRepository.deleteById(id);
        asyncWebSocketClient.send("/topic/playlists/deleted", id);
    }


    /**
     * Broadcasts the playlist to all users that have access to it.
     *
     * @param id the playlist id to broadcast
     */
    public void broadcastDeleted(int id) {
        asyncWebSocketClient.send("/topic/playlists/deleted", id);
    }

    public void updatePlaylist(Integer id, String name) {
        updatePlaylist(id, name, null, null);
    }

    @Transactional
    public void updatePlaylist(Integer id, String name, String comment, Boolean shared) {
        playlistCache.removePlaylistById(id);
        playlistRepository.findById(id).ifPresentOrElse(p -> {
            if (name != null) p.setName(name);
            if (comment != null) p.setComment(comment);
            if (shared != null) p.setShared(shared);
            p.setChanged(Instant.now());
            playlistRepository.save(p);
        }, () -> {
                LOG.warn("Playlist {} not found", id);
            }
        );
    }

    /**
     * Broadcasts the playlist to all users that have access to it.
     *
     * @param playlist the playlist to broadcast
     */
    public void broadcast(Playlist playlist) {
        if (playlist.getShared()) {
            asyncWebSocketClient.send("/topic/playlists/updated", playlist);
        } else {
            asyncWebSocketClient.sendToUser(playlist.getUsername(), "/queue/playlists/updated", playlist);
        }
    }


    /**
     * Broadcasts the playlist to all users that have access to it.
     *
     * @param id the playlist id to broadcast
     * @param isShared if true, the playlist was shared with other users
     * @param filesChangedBroadcastContext if true, the client will know that the files in the playlist have changed
     */
    @Transactional(readOnly = true)
    public void broadcastFileChange(Integer id, boolean isShared, boolean filesChangedBroadcastContext) {
        playlistRepository.findById(id).ifPresent(playlist -> {
            BroadcastedPlaylist bp = new BroadcastedPlaylist(playlist, filesChangedBroadcastContext);
            if (playlist.getShared()) {
                asyncWebSocketClient.send("/topic/playlists/updated", bp);
            } else {
                if (isShared) {
                    asyncWebSocketClient.send("/topic/playlists/deleted", playlist.getId());
                }
                Stream.concat(Stream.of(playlist.getUsername()), playlist.getSharedUsers().stream().map(User::getUsername))
                        .forEach(u -> asyncWebSocketClient.sendToUser(u, "/queue/playlists/updated", bp));
            }
        });
    }

    /**
     * Creates a new playlist for the specified play queue.
     *
     * @param playerId the ID of the player
     * @param username the username of the user creating the playlist
     * @param locale the locale to use for formatting
     * @return the ID of the created playlist
     */
    @Transactional
    public Integer createPlaylistForPlayQueue(@Nonnull Integer playerId, @Nonnull String username, Locale locale) {
        DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale);
        Instant now = Instant.now();
        String name = dateFormat.format(now.atZone(ZoneId.systemDefault()));

        Playlist playlist = createPlaylist(name, false, username);
        broadcast(playlist);

        Player player = playerService.getPlayerById(playerId);
        playlist = setFilesInPlaylist(playlist, player.getPlayQueue().getFiles());

        playlistRepository.saveAndFlush(playlist);
        return playlist.getId();
    }

    @Transactional
    public Integer createPlaylistForStarredSongs(@Nonnull String username, Locale locale) {

        DateTimeFormatter dateFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withLocale(locale);
        ResourceBundle bundle = ResourceBundle.getBundle("org.airsonic.player.i18n.ResourceBundle", locale);
        Instant now = Instant.now();
        String name = bundle.getString("top.starred") + " " + dateFormat.format(now.atZone(ZoneId.systemDefault()));

        Playlist playlist = createPlaylist(name, false, username);
        broadcast(playlist);

        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(username);
        List<MediaFile> musicFiles = mediaFileService.getStarredSongs(0, Integer.MAX_VALUE, username, musicFolders);
        setFilesInPlaylist(playlist, musicFiles);
        Integer playlistId = playlist.getId();
        return playlistId;
    }

    public static class BroadcastedPlaylist extends Playlist {
        private final boolean filesChanged;

        public BroadcastedPlaylist(Playlist p, boolean filesChanged) {
            super(p);
            this.setId(p.getId());
            this.filesChanged = filesChanged;
        }

        public boolean getFilesChanged() {
            return filesChanged;
        }
    }


}
