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
 */

package org.airsonic.player.domain.entity;

import java.io.Serializable;
import java.util.Objects;

public class UserRatingKey implements Serializable {

    private String username;

    private int mediaFileId;

    public UserRatingKey() {
    }

    public UserRatingKey(String username, int mediaFileId) {
        this.username = username;
        this.mediaFileId = mediaFileId;
    }

    public String getUsername() {
        return username;
    }

    public int getMediaFileId() {
        return mediaFileId;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setMediaFileId(int mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    @Override
    public String toString() {
        return "UserRatingKey{" +
                "username='" + username + '\'' +
                ", mediaFileId=" + mediaFileId +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserRatingKey that)) return false;

        if (mediaFileId != that.mediaFileId) return false;
        if (!username.equals(that.username)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, mediaFileId);
    }

}
