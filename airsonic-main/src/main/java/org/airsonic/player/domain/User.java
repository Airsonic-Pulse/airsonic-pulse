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
package org.airsonic.player.domain;

import org.airsonic.player.repository.RolesConverter;
import org.airsonic.player.util.Util;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Represent a user.
 *
 * @author Sindre Mehus
 */
@Entity
@Table(name = "users")
public class User {

    public static final String USERNAME_ADMIN = "admin";
    public static final String USERNAME_GUEST = "guest";
    public static final String USERNAME_ANONYMOUS = "anonymous";

    @Id
    @Column(name = "username", nullable = false, unique = true)
    private String username;

    @Column(name = "email", nullable = true, unique = true)
    private String email;

    @Column(name = "ldap_authenticated")
    private boolean ldapAuthenticated;

    // Per-user legacy-auth switch (#233). When false, the user has opted out of Subsonic
    // username/password and token+salt authentication for their own account (having migrated to
    // apiKey) — the REST legacy filter rejects those attempts with PASSWORD_AUTH_NOT_SUPPORTED.
    // Defaults true so existing and new accounts keep legacy auth until they explicitly opt out;
    // this bridges toward the global removal in #56.
    @Column(name = "password_auth_enabled", nullable = false)
    private boolean passwordAuthEnabled = true;

    @Column(name = "bytes_streamed")
    private long bytesStreamed;

    @Column(name = "bytes_downloaded")
    private long bytesDownloaded;

    @Column(name = "bytes_uploaded")
    private long bytesUploaded;

    @Column(name = "roles")
    @Convert(converter = RolesConverter.class)
    private Set<Role> roles = new HashSet<>();

    // TODO: change fetch type to LAZY
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "music_folder_user",
            joinColumns = @JoinColumn(name = "username"),
            inverseJoinColumns = @JoinColumn(name = "music_folder_id", referencedColumnName = "id"))
    private List<MusicFolder> musicFolders = new ArrayList<>();

    public User() {
    }

    public User(String username, String email, boolean ldapAuthenticated,
            long bytesStreamed, long bytesDownloaded, long bytesUploaded, Set<Role> roles) {
        this.username = username;
        this.email = email;
        this.ldapAuthenticated = ldapAuthenticated;
        this.bytesStreamed = bytesStreamed;
        this.bytesDownloaded = bytesDownloaded;
        this.bytesUploaded = bytesUploaded;
        this.roles = roles;
    }

    public User(String username, String email) {
        this(username, email, false, 0, 0, 0, Collections.emptySet());
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public boolean isLdapAuthenticated() {
        return ldapAuthenticated;
    }

    public void setLdapAuthenticated(boolean ldapAuthenticated) {
        this.ldapAuthenticated = ldapAuthenticated;
    }

    public boolean isPasswordAuthEnabled() {
        return passwordAuthEnabled;
    }

    public void setPasswordAuthEnabled(boolean passwordAuthEnabled) {
        this.passwordAuthEnabled = passwordAuthEnabled;
    }

    public long getBytesStreamed() {
        return bytesStreamed;
    }

    public void setBytesStreamed(long bytesStreamed) {
        this.bytesStreamed = bytesStreamed;
    }

    public long getBytesDownloaded() {
        return bytesDownloaded;
    }

    public void setBytesDownloaded(long bytesDownloaded) {
        this.bytesDownloaded = bytesDownloaded;
    }

    public long getBytesUploaded() {
        return bytesUploaded;
    }

    public void setBytesUploaded(long bytesUploaded) {
        this.bytesUploaded = bytesUploaded;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public void setRoles(Set<Role> roles) {
        this.roles = roles;
    }

    public boolean isAdminRole() {
        return roles.contains(Role.ADMIN);
    }

    public boolean isSettingsRole() {
        return roles.contains(Role.SETTINGS);
    }

    public boolean isCommentRole() {
        return roles.contains(Role.COMMENT);
    }

    public boolean isDownloadRole() {
        return roles.contains(Role.DOWNLOAD);
    }

    public boolean isUploadRole() {
        return roles.contains(Role.UPLOAD);
    }

    public boolean isPlaylistRole() {
        return roles.contains(Role.PLAYLIST);
    }

    public boolean isCoverArtRole() {
        return roles.contains(Role.COVERART);
    }

    public boolean isPodcastRole() {
        return roles.contains(Role.PODCAST);
    }

    public boolean isStreamRole() {
        return roles.contains(Role.STREAM);
    }

    public boolean isJukeboxRole() {
        return roles.contains(Role.JUKEBOX);
    }

    public boolean isShareRole() {
        return roles.contains(Role.SHARE);
    }

    public List<MusicFolder> getMusicFolders() {
        return musicFolders;
    }

    public void setMusicFolders(List<MusicFolder> musicFolders) {
        this.musicFolders = musicFolders;
    }

    public void addMusicFolder(MusicFolder musicFolder) {
        musicFolders.add(musicFolder);
    }

    @Override
    public String toString() {
        return username + " " + Util.toJson(roles);
    }

    public enum Role {

        ADMIN, SETTINGS, DOWNLOAD, UPLOAD, PLAYLIST, COVERART, COMMENT, PODCAST, STREAM, JUKEBOX, SHARE
    }
}
