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

import org.apache.commons.lang.StringUtils;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a remote player. A player has a unique ID, a user-defined name, a
 * logged-on user, miscellaneous identifiers, and an associated playlist.
 *
 * @author Sindre Mehus
 */
@Entity
@Table(name = "player")
public class Player {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "name", nullable = true)
    private String name;

    @Column(name = "technology", nullable = false)
    @Enumerated(EnumType.STRING)
    private PlayerTechnology technology = PlayerTechnology.WEB;

    @Column(name = "client_id", nullable = true)
    private String clientId;

    @Column(name = "type", nullable = true)
    private String type;

    @Column(name = "username", nullable = true)
    private String username;

    @Column(name = "ip_address", nullable = true)
    private String ipAddress;

    @Column(name = "dynamic_ip", nullable = false)
    private boolean dynamicIp = true;

    @Column(name = "auto_control_enabled")
    private boolean autoControlEnabled = true;

    @Column(name = "m3u_bom_enabled")
    private boolean m3uBomEnabled = true;

    @Column(name = "last_seen", nullable = true)
    private Instant lastSeen;

    @Column(name = "transcode_scheme", nullable = false)
    @Enumerated(EnumType.STRING)
    private TranscodeScheme transcodeScheme = TranscodeScheme.OFF;

    @ManyToMany(fetch = FetchType.LAZY, cascade = {CascadeType.MERGE, CascadeType.PERSIST})
    @JoinTable(
        name = "player_transcoding",
        joinColumns = {@JoinColumn(name = "player_id")},
        inverseJoinColumns = {@JoinColumn(name = "transcoding_id")}
    )
    private List<Transcoding> transcodings = new ArrayList<>();

    @Transient
    private PlayQueue playQueue;

    public Player() {
    }

    /**
     * Creates a new player.
     *
     * @param name The user-defined player name.  * @param clientId The third-party client ID (used if this player is managed over the Airsonic REST API)
     * @param technology The player "technology", e.g., web, external or jukebox.
     * @param type The player type, e.g., WinAmp, iTunes.
     * @param username The logged-in user.
     * @param ipAddress The IP address of the player.
     */
    public Player(String name, String clientId, PlayerTechnology technology, String type, String username, String ipAddress) {
        this.name = name;
        this.technology = technology;
        this.type = type;
        this.username = username;
        this.ipAddress = ipAddress;
    }

    /**
     * Returns the player ID.
     *
     * @return The player ID.
     */
    public Integer getId() {
        return id;
    }

    /**
     * Sets the player ID.
     *
     * @param id The player ID.
     */
    public void setId(Integer id) {
        this.id = id;
    }

    /**
     * Returns the user-defined player name.
     *
     * @return The user-defined player name.
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user-defined player name.
     *
     * @param name The user-defined player name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the player "technology", e.g., web, external or jukebox.
     *
     * @return The player technology.
     */
    public PlayerTechnology getTechnology() {
        return technology;
    }

    /**
     * Returns the third-party client ID (used if this player is managed over the
     * Airsonic REST API).
     *
     * @return The client ID.
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the third-party client ID (used if this player is managed over the
     * Airsonic REST API).
     *
     * @param clientId The client ID.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Sets the player "technology", e.g., web, external or jukebox.
     *
     * @param technology The player technology.
     */
    public void setTechnology(PlayerTechnology technology) {
        this.technology = technology;
    }

    public boolean isJukebox() {
        return technology == PlayerTechnology.JUKEBOX;
    }

    public boolean isExternal() {
        return technology == PlayerTechnology.EXTERNAL;
    }

    public boolean isExternalWithPlaylist() {
        return technology == PlayerTechnology.EXTERNAL_WITH_PLAYLIST;
    }

    public boolean isWeb() {
        return technology == PlayerTechnology.WEB;
    }

    /**
     * Returns the player type, e.g., WinAmp, iTunes.
     *
     * @return The player type.
     */
    public String getType() {
        return type;
    }

    /**
     * Sets the player type, e.g., WinAmp, iTunes.
     *
     * @param type The player type.
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * Returns the logged-in user.
     *
     * @return The logged-in user.
     */
    public String getUsername() {
        return username;
    }

    /**
     * Sets the logged-in username.
     *
     * @param username The logged-in username.
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns whether the player is automatically started.
     *
     * @return Whether the player is automatically started.
     */
    public boolean getAutoControlEnabled() {
        return autoControlEnabled;
    }

    /**
     * Sets whether the player is automatically started.
     *
     * @param autoControlEnabled Whether the player is automatically started.
     */
    public void setAutoControlEnabled(boolean autoControlEnabled) {
        this.autoControlEnabled = autoControlEnabled;
    }

    /**
     * Returns whether apply BOM mark when generating a M3U file.
     *
     * @return Whether apply BOM mark when generating a M3U file.
     */
    public boolean getM3uBomEnabled() {
        return m3uBomEnabled;
    }

    /**
     * Sets whether apply BOM mark when generating a M3U file.
     *
     * @param m3uBomEnabled Whether apply BOM mark when generating a M3U file.
     */
    public void setM3uBomEnabled(boolean m3uBomEnabled) {
        this.m3uBomEnabled = m3uBomEnabled;
    }

    /**
     * Returns the time when the player was last seen.
     *
     * @return The time when the player was last seen.
     */
    public Instant getLastSeen() {
        return lastSeen;
    }

    /**
     * Sets the time when the player was last seen.
     *
     * @param lastSeen The time when the player was last seen.
     */
    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    /**
     * Returns the transcode scheme.
     *
     * @return The transcode scheme.
     */
    public TranscodeScheme getTranscodeScheme() {
        return transcodeScheme;
    }

    /**
     * Sets the transcode scheme.
     *
     * @param transcodeScheme The transcode scheme.
     */
    public void setTranscodeScheme(TranscodeScheme transcodeScheme) {
        this.transcodeScheme = transcodeScheme;
    }

    /**
     * Returns the IP address of the player.
     *
     * @return The IP address of the player.
     */
    public String getIpAddress() {
        return ipAddress;
    }

    /**
     * Sets the IP address of the player.
     *
     * @param ipAddress The IP address of the player.
     */
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    /**
     * Returns whether this player has a dynamic IP address.
     *
     * @return Whether this player has a dynamic IP address.
     */
    public boolean getDynamicIp() {
        return dynamicIp;
    }

    /**
     * Sets whether this player has a dynamic IP address.
     *
     * @param dynamicIp Whether this player has a dynamic IP address.
     */
    public void setDynamicIp(boolean dynamicIp) {
        this.dynamicIp = dynamicIp;
    }

    /**
     * Returns the player's playlist.
     *
     * @return The player's playlist
     */
    public PlayQueue getPlayQueue() {
        return playQueue;
    }

    /**
     * Sets the player's playlist.
     *
     * @param playQueue The player's playlist.
     */
    public void setPlayQueue(PlayQueue playQueue) {
        this.playQueue = playQueue;
    }

    /**
     * Returns a long description of the player, e.g., <code>Player 3 [admin]</code>
     *
     * @return A long description of the player.
     */
    public String getDescription() {
        StringBuilder builder = new StringBuilder();
        if (name != null) {
            builder.append(name);
        } else {
            builder.append("Player ").append(id);
        }

        builder.append(" [").append(username).append(']');
        return builder.toString();
    }

    /**
     * Returns a short description of the player, e.g., <code>Player 3</code>
     *
     * @return A short description of the player.
     */
    public String getShortDescription() {
        if (StringUtils.isNotBlank(name)) {
            return name;
        }
        return "Player " + id;
    }

    /**
     * Returns the transcodings for the player.
     */
    public List<Transcoding> getTranscodings() {
        return transcodings;
    }

    /**
     * Sets the transcodings for the player.
     */
    public void setTranscodings(List<Transcoding> transcodings) {
        this.transcodings = transcodings;
    }

    /**
     * Adds a transcoding to the player.
     */
    public void addTranscoding(Transcoding transcoding) {
        this.transcodings.add(transcoding);
    }

    /**
     * Returns a string representation of the player.
     *
     * @return A string representation of the player.
     * @see #getDescription()
     */
    @Override
    public String toString() {
        return getDescription();
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Player other)) {
            return false;
        }
        return Objects.equals(id, other.id);
    }

}
