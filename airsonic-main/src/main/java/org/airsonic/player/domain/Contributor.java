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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.domain;

/**
 * A single OpenSubsonic contributor extracted from a track's tags: a {@code role} (e.g.
 * {@code "composer"}, {@code "lyricist"}, {@code "performer"}), an optional {@code subRole}
 * (the instrument for performer credits, sourced from TMCL pairs), and the contributor's
 * {@code name} as it appears in the tag.
 * <p>
 * Used as the internal carrier shape between {@code JaudiotaggerParser}, the packed
 * {@code media_file.contributors} column (see {@link Contributors}), and {@code JaxbContentService}
 * which builds the JAXB {@code Contributor} response element from this record.
 */
public record Contributor(String role, String subRole, String name) {
}
