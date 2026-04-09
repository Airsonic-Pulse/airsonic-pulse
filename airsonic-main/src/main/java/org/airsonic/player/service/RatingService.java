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
import org.airsonic.player.domain.entity.UserRating;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.UserRatingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;

/**
 * Provides services for user ratings.
 *
 * @author Sindre Mehus
 */
@Service
public class RatingService {

    private static final Logger LOG = LoggerFactory.getLogger(RatingService.class);

    @Autowired
    private SecurityService securityService;
    @Autowired
    private UserRatingRepository userRatingRepository;
    @Autowired
    private MediaFileRepository mediaFileRepository;

    /**
     * Returns the highest rated albums.
     *
     * @param offset      Number of albums to skip.
     * @param count       Maximum number of albums to return.
     * @param musicFolders Only return albums in these folders.
     * @return The highest rated albums.
     */
    @Transactional(readOnly = true)
    public List<MediaFile> getHighestRatedAlbums(int offset, int count, List<MusicFolder> musicFolders) {

        if (count < 1 || musicFolders.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, Double> averagePerMediaFileId = userRatingRepository.findAll().stream().collect(groupingBy(UserRating::getMediaFileId, Collectors.averagingDouble(UserRating::getRating)));

        List<MediaFile> sortedAlbums = averagePerMediaFileId.entrySet().stream().map(entry -> {
            return mediaFileRepository.findByIdAndFolderInAndMediaTypeAndPresentTrue(entry.getKey(), musicFolders, MediaFile.MediaType.ALBUM).map(file -> {
                file.setAverageRating(entry.getValue());
                return file;
            }).orElse(null);
        }).filter(file -> Objects.nonNull(file) && securityService.isReadAllowed(file, true))
            .sorted(Comparator.comparing(MediaFile::getAverageRating).reversed())
            .toList();

        if (offset >= sortedAlbums.size()) {
            return Collections.emptyList();
        }
        return sortedAlbums.subList(offset, Math.min(offset + count, sortedAlbums.size()));
    }

    /**
     * Sets the rating for a music file and a given user.
     *
     * @param username  The user name.
     * @param mediaFile The music file.
     * @param rating    The rating between 1 and 5, or <code>null</code> to remove the rating.
     */
    @Transactional
    public void setRatingForUser(String username, MediaFile mediaFile, Integer rating) {
        if (username == null || mediaFile == null) {
            return;
        }
        if (rating == null) {
            userRatingRepository.deleteByUsernameAndMediaFileId(username, mediaFile.getId());
        } else {
            UserRating userRating = new UserRating(username, mediaFile.getId(), rating);
            try {
                userRatingRepository.save(userRating);
            } catch (Exception e) {
                LOG.error("Failed to save rating for user {} and media file {}", username, mediaFile.getId(), e);
            }
        }
    }

    /**
     * Returns the average rating for the given music file.
     *
     * @param mediaFile The music file.
     * @return The average rating, or <code>null</code> if no ratings are set.
     */
    @Transactional
    public Double getAverageRating(MediaFile mediaFile) {
        if (mediaFile == null) {
            return null;
        }
        return userRatingRepository.getAverageRatingByMediaFileId(mediaFile.getId());
    }

    /**
     * Returns the rating for the given user and music file.
     *
     * @param username  The user name.
     * @param mediaFile The music file.
     * @return The rating, or <code>null</code> if no rating is set.
     */
    @Transactional
    public Integer getRatingForUser(String username, MediaFile mediaFile) {
        if (username == null || mediaFile == null) {
            return null;
        }
        return userRatingRepository.findOptByUsernameAndMediaFileId(username, mediaFile.getId()).map(UserRating::getRating).orElse(null);
    }

    /**
     * Returns the number of albums rated by the given user.
     *
     * @param username The user name.
     * @param musicFolders Only return albums in these folders.
     * @return The number of albums rated by the given user.
     */
    public int getRatedAlbumCount(String username, List<MusicFolder> musicFolders) {
        if (musicFolders.isEmpty()) {
            return 0;
        }
        List<MediaFile> albums = mediaFileRepository.findByFolderInAndMediaTypeAndPresentTrue(musicFolders, MediaFile.MediaType.ALBUM, PageRequest.of(0, Integer.MAX_VALUE));

        return userRatingRepository.countByUsernameAndMediaFileIdIn(username, albums.stream().map(MediaFile::getId).toList());
    }

}
