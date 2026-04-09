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

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.User;
import org.airsonic.player.domain.entity.Share;
import org.airsonic.player.domain.entity.ShareFile;
import org.airsonic.player.repository.ShareRepository;
import org.airsonic.player.util.NetworkUtil;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;


/**
 * Provides services for sharing media.
 *
 * @author Sindre Mehus
 * @see Share
 */
@Service
public class ShareService {

    private static final Logger LOG = LoggerFactory.getLogger(ShareService.class);

    private final MediaFileService mediaFileService;
    private final JWTSecurityService jwtSecurityService;
    private final ShareRepository shareRepository;

    public ShareService(MediaFileService mediaFileService, JWTSecurityService jwtSecurityService, ShareRepository shareRepository) {
        this.mediaFileService = mediaFileService;
        this.jwtSecurityService = jwtSecurityService;
        this.shareRepository = shareRepository;
    }

    public List<Share> getAllShares() {
        return shareRepository.findAll();
    }

    public List<Share> getSharesForUser(User user) {
        if (user.isAdminRole()) {
            return getAllShares();
        } else {
            return shareRepository.findByUsername(user.getUsername());
        }
    }

    public Share getShareById(int id) {
        return shareRepository.findById(id).orElse(null);
    }

    public Share getShareByName(String name) {
        return shareRepository.findByName(name).orElse(null);
    }

    @Transactional
    public List<MediaFile> getSharedFiles(int id, List<MusicFolder> musicFolders) {

        if (CollectionUtils.isEmpty(musicFolders)) {
            return Collections.emptyList();
        }
        List<ShareFile> files = shareRepository.findById(id).map(Share::getFiles).orElse(Collections.emptyList());
        return files.stream().map(sf -> mediaFileService.getMediaFile(sf.getMediaFileId()))
            .filter(mediaFile -> Objects.nonNull(mediaFile) && mediaFile.isPresent() && musicFolders.stream().anyMatch(folder -> folder.getId() == mediaFile.getFolder().getId()))
            .toList();
    }

    @Transactional
    public Share createShare(String username, List<MediaFile> files) {

        Instant now = Instant.now();
        Share share = new Share();
        share.setName(RandomStringUtils.random(5, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"));
        share.setCreated(now);
        share.setUsername(username);
        share.setExpires(now.plus(ChronoUnit.YEARS.getDuration()));

        if (!CollectionUtils.isEmpty(files)) {
            List<ShareFile> shareFiles = share.getFiles();
            for (MediaFile file : files) {
                shareFiles.add(new ShareFile(share, file.getId()));
            }
            share.setFiles(shareFiles);
        }
        LOG.info("Created share '{}' with {} file(s).", share.getName(), files.size());

        return shareRepository.save(share);
    }

    @Transactional
    public void updateShare(Share share) {
        shareRepository.save(share);
    }

    @Transactional
    public void deleteShare(int id) {
        shareRepository.deleteById(id);
    }

    public String getShareUrl(HttpServletRequest request, Share share) {
        String shareUrl = "ext/share/" + share.getName();
        return NetworkUtil.getBaseUrl(request) + jwtSecurityService
                .addJWTToken(User.USERNAME_ANONYMOUS, UriComponentsBuilder.fromUriString(shareUrl), share.getExpires())
                .build().toUriString();
    }

}
