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

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Packing and splitting for the {@code media_file.contributors} column: a structured multi-value
 * representation of the per-track OpenSubsonic contributor list. Each contributor is a
 * (role, optional subRole, name) triple; records are joined by {@link #RECORD_SEPARATOR}
 * ({@code \n}) and the three fields within a record by {@link #FIELD_SEPARATOR} ({@code U+001F}).
 * <p>
 * Both separators are ASCII control characters that should never occur in legitimate tag text,
 * but {@link #pack} defensively replaces either character with a space inside any field so the
 * encoding stays self-delimiting even against pathological inputs.
 */
public final class Contributors {

    public static final char FIELD_SEPARATOR = '';
    public static final char RECORD_SEPARATOR = '\n';

    private Contributors() {
    }

    public static String pack(List<Contributor> contributors) {
        if (contributors == null || contributors.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Contributor c : contributors) {
            if (c == null) {
                continue;
            }
            String role = sanitize(c.role());
            String name = sanitize(c.name());
            if (role == null || name == null) {
                continue;
            }
            String subRole = sanitize(c.subRole());
            if (!first) {
                sb.append(RECORD_SEPARATOR);
            }
            sb.append(role).append(FIELD_SEPARATOR)
                    .append(subRole == null ? "" : subRole)
                    .append(FIELD_SEPARATOR)
                    .append(name);
            first = false;
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    public static List<Contributor> split(String packed) {
        if (StringUtils.isBlank(packed)) {
            return List.of();
        }
        String[] records = StringUtils.split(packed, RECORD_SEPARATOR);
        List<Contributor> result = new ArrayList<>(records.length);
        for (String record : records) {
            if (record == null || record.isEmpty()) {
                continue;
            }
            String[] fields = StringUtils.splitPreserveAllTokens(record, FIELD_SEPARATOR);
            if (fields.length != 3) {
                continue;
            }
            String role = StringUtils.trimToNull(fields[0]);
            String name = StringUtils.trimToNull(fields[2]);
            if (role == null || name == null) {
                continue;
            }
            String subRole = StringUtils.trimToNull(fields[1]);
            result.add(new Contributor(role, subRole, name));
        }
        return result;
    }

    private static String sanitize(String value) {
        String trimmed = StringUtils.trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.replace(FIELD_SEPARATOR, ' ').replace(RECORD_SEPARATOR, ' ');
    }
}
