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
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.*;
import org.airsonic.player.service.jukebox.AudioPlayer;
import org.airsonic.player.service.jukebox.AudioPlayerFactory;
import org.airsonic.player.util.FileUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Plays music on the local audio device.
 *
 * @author Sindre Mehus
 */
@Service
public class JukeboxLegacySubsonicService implements AudioPlayer.Listener {

    private static final Logger LOG = LoggerFactory.getLogger(JukeboxLegacySubsonicService.class);

    private final TranscodingService transcodingService;
    private final AudioScrobblerService audioScrobblerService;
    private final StatusService statusService;
    private final SettingsService settingsService;
    private final SecurityService securityService;
    private final MediaFileService mediaFileService;
    private final AudioPlayerFactory audioPlayerFactory;

    public JukeboxLegacySubsonicService(
        AudioPlayerFactory audioPlayerFactory,
        AudioScrobblerService audioScrobblerService,
        MediaFileService mediaFileService,
        SecurityService securityService,
        SettingsService settingsService,
        StatusService statusService,
        TranscodingService transcodingService
    ) {
        this.audioPlayerFactory = audioPlayerFactory;
        this.audioScrobblerService = audioScrobblerService;
        this.mediaFileService = mediaFileService;
        this.securityService = securityService;
        this.settingsService = settingsService;
        this.statusService = statusService;
        this.transcodingService = transcodingService;
    }

    private final ReentrantLock lock = new ReentrantLock();

    private AudioPlayer audioPlayer;
    private Player player;
    private TransferStatus status;
    private PlayStatus playStatus;
    private MediaFile currentPlayingFile;
    private float gain = AudioPlayer.DEFAULT_GAIN;
    private int offset;

    /**
     * Updates the jukebox by starting or pausing playback on the local audio device.
     *
     * @param player The player in question.
     * @param offset Start playing after this many seconds into the track.
     */
    public void updateJukebox(Player player, int offset) {
        this.lock.lock();
        try {
            User user = securityService.getUserByName(player.getUsername());
            if (!user.isJukeboxRole()) {
                LOG.warn(user.getUsername() + " is not authorized for jukebox playback.");
                return;
            }

            if (player.getPlayQueue().getStatus() == PlayQueue.Status.PLAYING) {
                this.player = player;
                MediaFile result;
                synchronized (player.getPlayQueue()) {
                    result = player.getPlayQueue().getCurrentFile();
                }
                play(result, offset);
            } else {
                if (audioPlayer != null) {
                    audioPlayer.pause();
                }
            }
        } finally {
            this.lock.unlock();
        }
    }

    private void play(MediaFile file, int offset) {
        this.lock.lock();
        try {
            InputStream in = null;
            try {

                // Resume if possible.
                boolean sameFile = file != null && file.equals(currentPlayingFile);
                boolean paused = audioPlayer != null && audioPlayer.getState() == AudioPlayer.State.PAUSED;
                if (sameFile && paused && offset == 0) {
                    audioPlayer.play();
                } else {
                    this.offset = offset;
                    if (audioPlayer != null) {
                        audioPlayer.close();
                        if (currentPlayingFile != null) {
                            onSongEnd(currentPlayingFile);
                        }
                    }

                    if (file != null) {
                        double duration = file.getDuration() == null ? 0 : file.getDuration() - offset;
                        TranscodingService.Parameters parameters = new TranscodingService.Parameters(file, new VideoTranscodingSettings(0, 0, offset, duration));
                        String command = settingsService.getJukeboxCommand();
                        parameters.setTranscoding(new Transcoding(null, "Jukebox", null, null, command, null, null, false));
                        in = transcodingService.getTranscodedInputStream(parameters);
                        audioPlayer = audioPlayerFactory.createAudioPlayer(in, this);
                        audioPlayer.setGain(gain);
                        audioPlayer.play();
                        onSongStart(file);
                    }
                }

                currentPlayingFile = file;

            } catch (Exception x) {
                LOG.error("Error in jukebox: " + x, x);
                FileUtil.closeQuietly(in);
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void stateChanged(AudioPlayer audioPlayer, AudioPlayer.State state) {
        this.lock.lock();
        try {
            if (state == AudioPlayer.State.EOM) {
                player.getPlayQueue().next();
                MediaFile result;
                synchronized (player.getPlayQueue()) {
                    result = player.getPlayQueue().getCurrentFile();
                }
                play(result, 0);
            }
        } finally {
            this.lock.unlock();
        }
    }

    public float getGain() {
        this.lock.lock();
        try {
            return gain;
        } finally {
            this.lock.unlock();
        }
    }

    public int getPosition() {
        this.lock.lock();
        try {
            return audioPlayer == null ? 0 : offset + audioPlayer.getPosition();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Returns the player which currently uses the jukebox.
     *
     * @return The player, may be {@code null}.
     */
    public Player getPlayer() {
        return player;
    }

    private void onSongStart(MediaFile file) {
        LOG.info("{} starting jukebox for {}", player.getUsername(), FileUtil.getShortPath(file.getRelativePath()));
        status = statusService.createStreamStatus(player);
        status.setMediaFile(file);
        status.addBytesTransferred(file.getFileSize());
        mediaFileService.incrementPlayCount(status.getPlayer(), file);
        playStatus = new PlayStatus(status.getId(), file, status.getPlayer(), status.getMillisSinceLastUpdate());
        statusService.addActiveLocalPlay(playStatus);
        scrobble(file, false);
    }

    private void onSongEnd(MediaFile file) {
        LOG.info("{} stopping jukebox for {}", player.getUsername(), FileUtil.getShortPath(file.getRelativePath()));
        if (playStatus != null) {
            statusService.removeActiveLocalPlay(playStatus);
            playStatus = null;
        }
        if (status != null) {
            statusService.removeStreamStatus(status);
            status = null;
        }
        scrobble(file, true);
    }

    private void scrobble(MediaFile file, boolean submission) {
        if (player.getClientId() == null) {  // Don't scrobble REST players.
            audioScrobblerService.register(file, player.getUsername(), submission, null);
        }
    }

    public void setGain(float gain) {
        this.lock.lock();
        try {
            this.gain = gain;
            if (audioPlayer != null) {
                audioPlayer.setGain(gain);
            }
        } finally {
            this.lock.unlock();
        }
    }

}
