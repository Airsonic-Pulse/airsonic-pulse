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
package org.airsonic.player.domain;

import jakarta.persistence.*;

import java.io.Serializable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents a top level directory in which music or other media is stored.
 *
 * @author Sindre Mehus
 * @version $Revision: 1.1 $ $Date: 2005/11/27 14:32:05 $
 */
@Entity
@Table(name = "music_folder")
public class MusicFolder implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "path", nullable = false, unique = true)
    private Path path;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "type", nullable = false)
    @Enumerated(EnumType.STRING)
    private Type type = Type.MEDIA;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "changed", nullable = false)
    private Instant changed;

    @Column(name = "deleted")
    private boolean deleted;

    @ManyToMany(mappedBy = "musicFolders")
    private List<User> users = new ArrayList<>();

    public MusicFolder() {
    }

    /**
     * Creates a new music folder.
     *
     * @param id      The system-generated ID.
     * @param path    The path of the music folder.
     * @param name    The user-defined name.
     * @param enabled Whether the folder is enabled.
     * @param changed When the corresponding database entry was last changed.
     */
    public MusicFolder(Integer id, Path path, String name, Type type, boolean enabled, Instant changed) {
        this.id = id;
        this.path = path;
        this.name = name;
        this.type = type;
        this.enabled = enabled;
        this.changed = changed;
    }

    /**
     * Creates a new music folder.
     *
     * @param path    The path of the music folder.
     * @param name    The user-defined name.
     * @param enabled Whether the folder is enabled.
     * @param changed When the corresponding database entry was last changed.
     */
    public MusicFolder(Path path, String name, Type type, boolean enabled, Instant changed) {
        this(null, path, name, type, enabled, changed);
    }

    /**
     * Returns the system-generated ID.
     *
     * @return The system-generated ID.
     */
    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Returns the path of the music folder.
     *
     * @return The path of the music folder.
     */
    public Path getPath() {
        return path;
    }

    /**
     * Sets the path of the music folder.
     *
     * @param path The path of the music folder.
     */
    public void setPath(Path path) {
        this.path = path;
    }

    /**
     * Returns the user-defined name.
     *
     * @return The user-defined name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user-defined name.
     *
     * @param name The user-defined name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns whether the folder is enabled.
     *
     * @return Whether the folder is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether the folder is enabled.
     *
     * @param enabled Whether the folder is enabled.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns when the corresponding database entry was last changed.
     *
     * @return When the corresponding database entry was last changed.
     */
    public Instant getChanged() {
        return changed;
    }

    /**
     * Sets when the corresponding database entry was last changed.
     *
     * @param changed When the corresponding database entry was last changed.
     */
    public void setChanged(Instant changed) {
        this.changed = changed;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return Objects.equals(id, ((MusicFolder) o).id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    public static enum Type {
        MEDIA, PODCAST
    }


    public static List<Integer> toIdList(List<MusicFolder> from) {
        return from.stream().map(toId()).toList();
    }

    public static List<String> toPathList(List<MusicFolder> from) {
        return from.stream().map(toPath()).toList();
    }

    public static Function<MusicFolder, Integer> toId() {
        return from -> from.getId();
    }

    public static Function<MusicFolder, String> toPath() {
        return from -> from.getPath().toString();
    }
}