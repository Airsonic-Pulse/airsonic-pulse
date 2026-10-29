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

import org.airsonic.player.build.UnmodifiableResult;
import org.airsonic.player.command.PersonalSettingsCommand;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.AlbumListType;
import org.airsonic.player.domain.Avatar;
import org.airsonic.player.domain.AvatarScheme;
import org.airsonic.player.domain.TranscodeScheme;
import org.airsonic.player.domain.UserSettingVisibility;
import org.airsonic.player.domain.UserSettings;
import org.airsonic.player.domain.entity.SystemAvatar;
import org.airsonic.player.domain.entity.UserSetting;
import org.airsonic.player.domain.entity.UserSettingDetail;
import org.airsonic.player.repository.CustomAvatarRepository;
import org.airsonic.player.repository.SystemAvatarRepository;
import org.airsonic.player.repository.UserSettingRepository;
import org.airsonic.player.service.cache.UserSettingsCache;
import org.airsonic.player.util.FileUtil;
import org.airsonic.player.util.ImageUtil;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PersonalSettingsService {

    private static final Logger LOG = LoggerFactory.getLogger(PersonalSettingsService.class);
    private static final int MAX_AVATAR_SIZE = 64;

    @Autowired
    private SystemAvatarRepository systemAvatarRepository;
    @Autowired
    private CustomAvatarRepository customAvatarRepository;
    @Autowired
    private UserSettingRepository userSettingRepository;
    @Autowired
    private AirsonicHomeConfig homeConfig;
    @Autowired
    private UserSettingsCache userSettingsCache;

    @UnmodifiableResult
    public List<Avatar> getSystemAvatars() {
        return systemAvatarRepository.findAll().stream().map(SystemAvatar::toAvatar).toList();
    }

    public Avatar getCustomAvatar(String username) {
        if (StringUtils.isBlank(username)) {
            return null;
        }
        Path airsonicHome = homeConfig.getAirsonicHome();
        return customAvatarRepository.findByUsername(username).map(ca -> ca.toAvatar(airsonicHome)).orElse(null);
    }

    public Avatar getSystemAvatar(Integer id) {
        if (id == null) {
            return null;
        }
        return systemAvatarRepository.findById(id).map(SystemAvatar::toAvatar).orElse(null);
    }

    /**
     * Returns the avatar for the given user. If id is not null, return the system
     * avatar with the given id. If username is not null, return the custom avatar
     * for the given user. If forceCustom is true, return the custom avatar for the
     * given user.
     *
     * @param id          The system avatar id
     * @param username    The username of the user to get the custom avatar for.
     * @param forceCustom If true, return the custom avatar for the given user.
     * @return Avatar.
     */
    public Avatar getAvatar(Integer id, String username, boolean forceCustom) {

        if (id != null) {
            return getSystemAvatar(id);
        }

        if (username == null) {
            return null;
        }

        UserSetting setting = getUserSetting(username);

        if (forceCustom || setting.getSettings().getAvatarScheme() == AvatarScheme.CUSTOM) {
            return getCustomAvatar(username);
        }

        if (setting.getSettings().getAvatarScheme() == AvatarScheme.NONE) {
            return null;
        }
        return getSystemAvatar(setting.getSettings().getSystemAvatarId());
    }

    /**
     * Creates an avatar image from the given data.
     *
     * @param fileName the file name of the image.
     * @param data     the image data.
     * @param username the username.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> createCustomAvatar(String fileName, byte[] data, String username) {

        BufferedImage image;
        Map<String, Object> result = new HashMap<>();
        try {
            image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new IOException("Failed to decode incoming image: " + fileName + " (" + data.length + " bytes).");
            }
            int width = image.getWidth();
            int height = image.getHeight();
            String mimeType = StringUtil.getMimeType(FilenameUtils.getExtension(fileName));
            Path folder = homeConfig.getAirsonicHome().resolve("avatars").resolve(username);
            Files.createDirectories(folder);
            Path fileOnDisk = folder.resolve(fileName + "." + StringUtils.substringAfter(mimeType, "/"));
            // Scale down image if necessary.
            if (width > MAX_AVATAR_SIZE || height > MAX_AVATAR_SIZE) {
                double scaleFactor = MAX_AVATAR_SIZE / (double) Math.max(width, height);
                height = (int) (height * scaleFactor);
                width = (int) (width * scaleFactor);
                image = ImageUtil.scale(image, width, height);
                mimeType = StringUtil.getMimeType("jpeg");
                fileOnDisk = folder.resolve(fileName + ".jpeg");
                ImageIO.write(image, "jpeg", fileOnDisk.toFile());

                result.put("resized", true);
            } else {
                Files.copy(new ByteArrayInputStream(data), fileOnDisk, StandardCopyOption.REPLACE_EXISTING);
            }
            customAvatarRepository.deleteAllByUsername(username);
            Avatar avatar = new Avatar(fileName, Instant.now(), mimeType, width, height, fileOnDisk);
            customAvatarRepository.save(avatar.toCustomAvatar(username, homeConfig.getAirsonicHome()));
            LOG.info("Created avatar '{}' ({} bytes) for user {}", fileName, FileUtil.size(fileOnDisk), username);
            return result;

        } catch (IOException x) {
            LOG.warn("Failed to upload personal image", x);
            result.put("error", x);
            return result;
        }
    }

    /**
     * Returns settings for the given user.
     *
     * @param username The username.
     * @return User-specific settings. Never <code>null</code>.
     */
    public UserSettings getUserSettings(String username) {

        UserSettings userSettings = userSettingsCache.getUserSettings(username);
        if (userSettings == null) {
            UserSetting userSetting = getUserSetting(username);
            userSettings = new UserSettings(username, userSetting.getSettings());
            userSettingsCache.putUserSettings(username, userSettings);
        }
        return userSettings;
    }

    /**
     * Returns settings for the given user.
     * @param username The username.
     * @return User-specific settings. Never <code>null</code>.
     */
    private UserSetting getUserSetting(String username) {
        return userSettingRepository.findByUsername(username).orElseGet(() -> new UserSetting(username, createDefaultUserSetting()));
    }

    private UserSettingDetail createDefaultUserSetting() {

        UserSettingDetail detail = new UserSettingDetail();
        detail.setFinalVersionNotificationEnabled(true);
        detail.setBetaVersionNotificationEnabled(false);
        detail.setSongNotificationEnabled(true);
        detail.setShowNowPlayingEnabled(true);
        detail.setPartyModeEnabled(false);
        detail.setNowPlayingAllowed(true);
        detail.setAutoHidePlayQueue(true);
        detail.setKeyboardShortcutsEnabled(false);
        detail.setShowSideBar(true);
        detail.setShowArtistInfoEnabled(true);
        detail.setViewAsList(false);
        detail.setQueueFollowingSongs(true);
        detail.setDefaultAlbumList(AlbumListType.RANDOM);
        detail.setLastFmEnabled(false);
        detail.setListenBrainzEnabled(false);
        detail.setChanged(Instant.now());

        UserSettingVisibility playlist = detail.getPlaylistVisibility();
        playlist.setArtistVisible(true);
        playlist.setAlbumVisible(true);
        playlist.setYearVisible(true);
        playlist.setDurationVisible(true);
        playlist.setBitRateVisible(true);
        playlist.setFormatVisible(true);
        playlist.setFileSizeVisible(true);

        UserSettingVisibility main = detail.getMainVisibility();
        main.setTrackNumberVisible(true);
        main.setArtistVisible(true);
        main.setDurationVisible(true);

        return detail;
    }

    @Transactional
    public void updateByCommand(String username, Locale locale, String themeId, PersonalSettingsCommand command) {

        UserSetting userSetting = getUserSetting(username);
        UserSettingDetail settingDetail = userSetting.getSettings();

        settingDetail.setLocale(locale);
        settingDetail.setThemeId(themeId);
        settingDetail.setDefaultAlbumList(AlbumListType.fromId(command.getAlbumListId()));
        settingDetail.setPartyModeEnabled(command.isPartyModeEnabled());
        settingDetail.setQueueFollowingSongs(command.isQueueFollowingSongs());
        settingDetail.setShowNowPlayingEnabled(command.isShowNowPlayingEnabled());
        settingDetail.setShowArtistInfoEnabled(command.isShowArtistInfoEnabled());
        settingDetail.setNowPlayingAllowed(command.isNowPlayingAllowed());
        settingDetail.setMainVisibility(command.getMainVisibility());
        settingDetail.setPlaylistVisibility(command.getPlaylistVisibility());
        settingDetail.setPlayqueueVisibility(command.getPlayqueueVisibility());
        settingDetail.setFinalVersionNotificationEnabled(command.isFinalVersionNotificationEnabled());
        settingDetail.setBetaVersionNotificationEnabled(command.isBetaVersionNotificationEnabled());
        settingDetail.setSongNotificationEnabled(command.isSongNotificationEnabled());
        settingDetail.setAutoHidePlayQueue(command.isAutoHidePlayQueue());
        settingDetail.setKeyboardShortcutsEnabled(command.isKeyboardShortcutsEnabled());
        settingDetail.setLastFmEnabled(command.getLastFmEnabled());
        settingDetail.setListenBrainzEnabled(command.getListenBrainzEnabled());
        settingDetail.setListenBrainzUrl(StringUtils.trimToNull(command.getListenBrainzUrl()));
        settingDetail.setPodcastIndexEnabled(command.getPodcastIndexEnabled());
        settingDetail.setPodcastIndexUrl(StringUtils.trimToNull(command.getPodcastIndexUrl()));
        settingDetail.setSystemAvatarId(getSystemAvatarId(command));
        settingDetail.setAvatarScheme(getAvatarScheme(command));
        settingDetail.setPaginationSizeFiles(command.getPaginationSizeFiles());
        settingDetail.setPaginationSizeFolders(command.getPaginationSizeFolders());
        settingDetail.setPaginationSizePlaylist(command.getPaginationSizePlaylist());
        settingDetail.setPaginationSizePlayqueue(command.getPaginationSizePlayqueue());
        settingDetail.setPaginationSizeBookmarks(command.getPaginationSizeBookmarks());
        settingDetail.setAutoBookmark(command.getAutoBookmark());
        settingDetail.setAudioBookmarkFrequency(command.getAudioBookmarkFrequency());
        settingDetail.setVideoBookmarkFrequency(command.getVideoBookmarkFrequency());
        settingDetail.setSearchCount(command.getSearchCount());
        settingDetail.setChanged(Instant.now());

        userSettingRepository.save(userSetting);
        userSettingsCache.removeUserSettings(username);
    }

    private AvatarScheme getAvatarScheme(PersonalSettingsCommand command) {
        if (command.getAvatarId() == AvatarScheme.NONE.getCode()) {
            return AvatarScheme.NONE;
        }
        if (command.getAvatarId() == AvatarScheme.CUSTOM.getCode()) {
            return AvatarScheme.CUSTOM;
        }
        return AvatarScheme.SYSTEM;
    }

    private Integer getSystemAvatarId(PersonalSettingsCommand command) {
        int avatarId = command.getAvatarId();
        if (avatarId == AvatarScheme.NONE.getCode() ||
            avatarId == AvatarScheme.CUSTOM.getCode()) {
            return null;
        }
        return avatarId;
    }


    /**
     * Updates the transcode scheme for the given user.
     *
     * @param username The username.
     * @param scheme The transcode scheme.
     */
    @Transactional
    public void updateTranscodeScheme(String username, TranscodeScheme scheme) {

        UserSetting userSetting = getUserSetting(username);
        UserSettingDetail settingDetail = userSetting.getSettings();
        if (settingDetail.getTranscodeScheme() == scheme) {
            return;
        }
        settingDetail.setTranscodeScheme(scheme);
        settingDetail.setChanged(Instant.now());
        userSettingRepository.save(userSetting);
        userSettingsCache.removeUserSettings(username);
    }

    /**
     * Updates the selected music folder id for the given user.
     *
     * @param username The username.
     * @param musicFolderId The music folder id.
     */
    @Transactional
    public void updateSelectedMusicFolderId(String username, Integer musicFolderId) {

        UserSetting userSetting = getUserSetting(username);
        UserSettingDetail settingDetail = userSetting.getSettings();
        if (settingDetail.getSelectedMusicFolderId() == musicFolderId) {
            return;
        }
        settingDetail.setSelectedMusicFolderId(musicFolderId);
        userSettingRepository.save(userSetting);
        userSettingsCache.removeUserSettings(username);
    }

    /**
     * Updates the show side bar status for the given user.
     * @param username The username.
     * @param showSideBar The show side bar status.
     */
    @Transactional
    public void updateShowSideBarStatus(String username, boolean showSideBar) {

        UserSetting userSetting = getUserSetting(username);
        UserSettingDetail settingDetail = userSetting.getSettings();
        if (settingDetail.getShowSideBar() == showSideBar) {
            return;
        }
        settingDetail.setShowSideBar(showSideBar);
        // Note: setChanged() is intentionally not called. This would break browser caching
        // of the left frame.
        userSettingRepository.save(userSetting);
        userSettingsCache.removeUserSettings(username);
    }

    /**
     * Updates the show side bar status for the given user.
     * @param username The username.
     * @parama viewAsList The view as list status.
     */
    @Transactional
    public void updateViewAsListStatus(String username, boolean viewAsList) {

        UserSetting userSetting = getUserSetting(username);
        UserSettingDetail settingDetail = userSetting.getSettings();
        if (settingDetail.getViewAsList() == viewAsList) {
            return;
        }
        settingDetail.setViewAsList(viewAsList);
        settingDetail.setChanged(Instant.now());
        userSettingRepository.save(userSetting);
        userSettingsCache.removeUserSettings(username);
    }

}
