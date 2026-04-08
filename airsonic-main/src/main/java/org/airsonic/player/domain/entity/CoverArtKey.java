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

import org.airsonic.player.domain.CoverArt;

import java.io.Serializable;
import java.util.Objects;

public class CoverArtKey implements Serializable {

    private Integer entityId;

    private CoverArt.EntityType entityType;

    public CoverArtKey() {
    }

    public CoverArtKey(Integer entityId, CoverArt.EntityType entityType) {
        this.entityId = entityId;
        this.entityType = entityType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CoverArtKey that)) return false;

        if (entityId != that.entityId) return false;
        if (entityType != (that.entityType)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityId, entityType);
    }

}
