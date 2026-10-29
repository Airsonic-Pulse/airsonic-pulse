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
package org.airsonic.player.controller;

import org.airsonic.player.build.UnmodifiableResult;
import org.airsonic.player.command.SearchCommand;
import org.airsonic.player.command.SearchResultAlbum;
import org.airsonic.player.command.SearchResultArtist;
import org.airsonic.player.domain.*;
import org.airsonic.player.service.AlbumService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.PersonalSettingsService;
import org.airsonic.player.service.PlayerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.search.IndexType;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Controller for the search page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/search", "/search.view"})
public class SearchController {

    @Autowired
    private SecurityService securityService;
    @Autowired
    private PlayerService playerService;
    @Autowired
    private SearchService searchService;
    @Autowired
    private MediaFolderService mediaFolderService;
    @Autowired
    private MediaFileService mediaFileService;
    @Autowired
    private AlbumService albumService;
    @Autowired
    private PersonalSettingsService personalSettingsService;

    @GetMapping
    protected String displayForm() {
        return "search";
    }

    @ModelAttribute
    protected void formBackingObject(HttpServletRequest request, Model model) {
        model.addAttribute("command",new SearchCommand());
    }

    @PostMapping
    protected String onSubmit(HttpServletRequest request, HttpServletResponse response,@ModelAttribute("command") SearchCommand command, Model model) throws Exception {

        User user = securityService.getCurrentUser(request);
        UserSettings userSettings = personalSettingsService.getUserSettings(user.getUsername());
        command.setUser(user);
        command.setPartyModeEnabled(userSettings.getPartyModeEnabled());

        List<MusicFolder> musicFolders = mediaFolderService.getMusicFoldersForUser(user.getUsername());
        String query = StringUtils.trimToNull(command.getQuery());

        if (query != null) {

            SearchCriteria criteria = new SearchCriteria();
            criteria.setCount(userSettings.getSearchCount());
            criteria.setQuery(query);

            SearchResult artists = searchService.search(criteria, musicFolders, IndexType.ARTIST);
            SearchResult artistsId3 = searchService.search(criteria, musicFolders, IndexType.ARTIST_ID3);
            command.setArtists(createArtistResults(artists));
            command.setArtistsFromTag(createArtistResultsFromId3Tag(artistsId3, musicFolders));

            SearchResult albums = searchService.search(criteria, musicFolders, IndexType.ALBUM);
            SearchResult albumsId3 = searchService.search(criteria, musicFolders, IndexType.ALBUM_ID3);
            command.setAlbums(createAlbumResults(albums));
            command.setAlbumsFromTag(createAlbumResultsFromId3Tag(albumsId3));

            SearchResult songs = searchService.search(criteria, musicFolders, IndexType.SONG);
            command.setSongs(songs.getMediaFiles());

            command.setPlayer(playerService.getPlayer(request, response, user.getUsername()));
        }

        return "search";
    }

    /**
     * Create a list of search result artists from the search result.
     *
     * @param artists the search result to create the artists from (media files)
     * @return a list of search result artists
     */
    @UnmodifiableResult
    private List<SearchResultArtist> createArtistResults(SearchResult artists) {

        Map<Pair<String, Integer>, SearchResultArtist> artistMap = new LinkedHashMap<>();

        artists.getMediaFiles().stream().forEach(m -> {
            String artist = Optional.ofNullable(m.getTitle())
                    .or(() -> Optional.ofNullable(m.getArtist()))
                    .or(() -> Optional.ofNullable(m.getAlbumArtist()))
                    .orElse("(Unknown)");
            SearchResultArtist artistResult = artistMap.computeIfAbsent(Pair.of(artist, m.getFolder().getId()),
                    k -> new SearchResultArtist(artist, m.getFolder()));
            artistResult.addMediaFileId(m.getId());
        });

        return artistMap.values().stream().toList();
    }

    /**
     * Create a list of search result artists from the search result.
     *
     * @param artistsId3   the search result to create the artists from (ID3 tags)
     * @param musicFolders the music folders to search for the media files
     * @return a list of search result artists
     */
    @UnmodifiableResult
    private List<SearchResultArtist> createArtistResultsFromId3Tag(SearchResult artistsId3,
            List<MusicFolder> musicFolders) {

        Map<Pair<String, Integer>, SearchResultArtist> artistMapFromTag = new LinkedHashMap<>();

        artistsId3.getArtists().stream()
                .map(Artist::getName)
                .flatMap(ar -> albumService.getAlbumsByArtist(ar, musicFolders).stream()
                        .map(al -> mediaFileService.getMediaFile(al.getPath(), al.getFolder()))
                        .filter(Objects::nonNull)
                        .map(m -> Pair.of(ar, m)))
                .forEach(p -> {
                    SearchResultArtist artistResult = artistMapFromTag.computeIfAbsent(
                            Pair.of(p.getKey(), p.getValue().getFolder().getId()),
                            k -> new SearchResultArtist(p.getKey(), p.getValue().getFolder()));
                    artistResult.addMediaFileId(p.getValue().getId());
                });

        return artistMapFromTag.values().stream().toList();
    }

    /**
     * Create a list of search result albums from the search result.
     *
     * @param albums the search result to create the albums from (media files)
     * @return a list of search result albums
     */
    @UnmodifiableResult
    private List<SearchResultAlbum> createAlbumResults(SearchResult albums) {

        Map<Triple<String, String, Integer>, SearchResultAlbum> albumMap = new LinkedHashMap<>();

        albums.getMediaFiles().stream().forEach(m -> {
            String album = m.getAlbumName();
            String artist = Optional.ofNullable(m.getArtist())
                    .or(() -> Optional.ofNullable(m.getAlbumArtist()))
                    .orElse("(Unknown)");
            SearchResultAlbum albumResult = albumMap.computeIfAbsent(Triple.of(album, artist, m.getFolder().getId()),
                    k -> new SearchResultAlbum(album, artist, m.getFolder()));
            albumResult.addMediaFileId(m.getId());
        });

        return albumMap.values().stream().toList();
    }

    /**
     * Create a list of search result albums from the search result.
     *
     * @param albums the search result to create the albums from (media files)
     * @return a list of search result albums
     */
    @UnmodifiableResult
    private List<SearchResultAlbum> createAlbumResultsFromId3Tag(SearchResult albumsId3) {

        Map<Triple<String, String, Integer>, SearchResultAlbum> albumId3Map = new LinkedHashMap<>();

        albumsId3.getAlbums().stream()
                .forEach(a -> {
                    MediaFile mediaFile = mediaFileService.getMediaFile(a.getPath(), a.getFolder());
                    if (mediaFile == null) {
                        return;
                    }
                    String album = a.getName();
                    String artist = Optional.ofNullable(mediaFile.getArtist())
                            .or(() -> Optional.ofNullable(mediaFile.getAlbumArtist()))
                            .orElse(a.getArtist());
                    SearchResultAlbum albumResult = albumId3Map.computeIfAbsent(
                            Triple.of(album, artist, a.getFolder().getId()),
                            k -> new SearchResultAlbum(album, artist, a.getFolder()));
                    albumResult.addMediaFileId(mediaFile.getId());
                });

        return albumId3Map.values().stream().toList();
    }


}
