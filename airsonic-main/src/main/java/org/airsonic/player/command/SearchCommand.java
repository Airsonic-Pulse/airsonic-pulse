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

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.command;

import org.airsonic.player.controller.SearchController;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Command used in {@link SearchController}.
 *
 * @author Sindre Mehus
 */
public class SearchCommand {

    private String query;
    private List<SearchResultArtist> artists = new ArrayList<>();
    private List<SearchResultArtist> artistsFromTag = new ArrayList<>();
    private List<SearchResultAlbum> albums;
    private List<SearchResultAlbum> albumsFromTag;
    private List<MediaFile> songs;
    private boolean isIndexBeingCreated;
    private User user;
    private boolean partyModeEnabled;
    private Player player;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean isIndexBeingCreated() {
        return isIndexBeingCreated;
    }

    public void setIndexBeingCreated(boolean indexBeingCreated) {
        isIndexBeingCreated = indexBeingCreated;
    }

    public List<SearchResultArtist> getArtists() {
        return artists;
    }

    public void setArtists(List<SearchResultArtist> artists) {
        this.artists = artists;
    }

    public List<SearchResultArtist> getArtistsFromTag() {
        return artistsFromTag;
    }

    public void setArtistsFromTag(List<SearchResultArtist> artistsFromTag) {
        this.artistsFromTag = artistsFromTag;
    }

    public List<SearchResultAlbum> getAlbums() {
        return albums;
    }

    public void setAlbums(List<SearchResultAlbum> albums) {
        this.albums = albums;
    }

    public List<SearchResultAlbum> getAlbumsFromTag() {
        return albumsFromTag;
    }

    public void setAlbumsFromTag(List<SearchResultAlbum> albumsFromTag) {
        this.albumsFromTag = albumsFromTag;
    }

    public List<MediaFile> getSongs() {
        return songs;
    }

    public void setSongs(List<MediaFile> songs) {
        this.songs = songs;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public boolean isPartyModeEnabled() {
        return partyModeEnabled;
    }

    public void setPartyModeEnabled(boolean partyModeEnabled) {
        this.partyModeEnabled = partyModeEnabled;
    }

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player player) {
        this.player = player;
    }

    public record Match(MediaFile mediaFile, String title, String album, String artist) {
    }
}
