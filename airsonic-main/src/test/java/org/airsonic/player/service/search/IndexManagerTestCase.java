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
package org.airsonic.player.service.search;

import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.domain.SearchCriteria;
import org.airsonic.player.domain.SearchResult;
import org.airsonic.player.repository.AlbumRepository;
import org.airsonic.player.repository.ArtistRepository;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.repository.MusicFolderRepository;
import org.airsonic.player.service.MediaFolderService;
import org.airsonic.player.service.MediaScannerService;
import org.airsonic.player.service.SearchService;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@EnableConfigurationProperties({ AirsonicHomeConfig.class })
public class IndexManagerTestCase {

    private List<MusicFolder> musicFolders = new ArrayList<>();

    @Autowired
    private SearchService searchService;

    @Autowired
    private IndexManager indexManager;

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MusicFolderRepository musicFolderRepository;

    @Autowired
    private MediaFolderService mediaFolderService;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void setup() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @BeforeEach
    public void beforeEach() {
        mediaFolderService.clearMusicFolderCache();
        mediaFolderService.clearMediaFileCache();
        // Sweep present=false Artist/Album orphans left behind by earlier test classes whose
        // @AfterEach deletes their music folder but not the Artist/Album rows the scan created.
        // testExpunge's zero-state pre-assertions at lines 189 and 208 require this — HSQLDB
        // tolerates the residue (in-memory + fresh per run), Postgres/MariaDB do not. The
        // underlying multi-offender cleanup antipattern is tracked separately as a hygiene
        // audit; this sweep keeps testExpunge robust under any suite ordering.
        artistRepository.deleteAll(artistRepository.findByPresentFalse());
        albumRepository.deleteAll(albumRepository.findByPresentFalse());
        Path musicDir = MusicFolderTestData.resolveMusicFolderPath();
        MusicFolder folder = new MusicFolder(musicDir, "Music", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        musicFolders.add(folder);
        musicFolderRepository.saveAndFlush(folder);
        TestCaseUtils.execScan(mediaScannerService);
    }

    @AfterEach
    public void afterEach() {
        musicFolderRepository.deleteAll(musicFolders);
        musicFolders = new ArrayList<>();
    }

    @Autowired
    private ArtistRepository artistRepository;

    @Autowired
    private AlbumRepository albumRepository;

    @Autowired
    private MediaFileRepository mediaFileRepository;

    @Autowired
    ResourceLoader resourceLoader;

    @Test
    public void testExpunge() {

        SearchCriteria criteria = new SearchCriteria();
        criteria.setOffset(0);
        criteria.setCount(Integer.MAX_VALUE);
        criteria.setQuery("_DIR_ Ravel");

        SearchCriteria criteriaSong = new SearchCriteria();
        criteriaSong.setOffset(0);
        criteriaSong.setCount(Integer.MAX_VALUE);
        criteriaSong.setQuery("Gaspard");

        SearchCriteria criteriaAlbumId3 = new SearchCriteria();
        criteriaAlbumId3.setOffset(0);
        criteriaAlbumId3.setCount(Integer.MAX_VALUE);
        criteriaAlbumId3.setQuery("Complete Piano Works");

        /* Delete DB record. */

        // artist
        SearchResult result = searchService.search(criteria, musicFolders, IndexType.ARTIST);
        assertEquals(2, result.getMediaFiles().size());
        assertEquals("_DIR_ Ravel", result.getMediaFiles().get(0).getName());
        assertEquals("_DIR_ Sixteen Horsepower", result.getMediaFiles().get(1).getName());

        List<MediaFile> candidates = mediaFileRepository.findByMediaTypeAndPresentFalse(MediaType.DIRECTORY);
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(this::deleteMediaFile);

        candidates = mediaFileRepository.findByMediaTypeAndPresentFalse(MediaType.DIRECTORY);
        assertEquals(2, candidates.size());

        // album
        result = searchService.search(criteria, musicFolders, IndexType.ALBUM);
        assertEquals(2, result.getMediaFiles().size());
        assertEquals("_DIR_ Ravel - Complete Piano Works", result.getMediaFiles().get(0).getName());
        assertEquals("_DIR_ Ravel - Chamber Music With Voice", result.getMediaFiles().get(1).getName());

        candidates = mediaFileRepository.findByMediaTypeAndPresentFalse(MediaType.ALBUM);
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(this::deleteMediaFile);

        candidates = mediaFileRepository.findByMediaTypeAndPresentFalse(MediaType.ALBUM);
        assertEquals(2, candidates.size());

        // song
        result = searchService.search(criteriaSong, musicFolders, IndexType.SONG);
        assertEquals(2, result.getMediaFiles().size());
        assertTrue(result.getMediaFiles().stream().anyMatch(mf -> mf.getName().equals("01 - Gaspard de la Nuit - i. Ondine")));
        assertTrue(result.getMediaFiles().stream().anyMatch(mf -> mf.getName().equals("02 - Gaspard de la Nuit - ii. Le Gibet")));

        candidates = mediaFileRepository.findByMediaTypeInAndPresentFalse(MediaType.playableTypes());
        assertEquals(0, candidates.size());

        result.getMediaFiles().forEach(this::deleteMediaFile);

        candidates = mediaFileRepository.findByMediaTypeInAndPresentFalse(MediaType.playableTypes());
        assertEquals(2, candidates.size());

        // artistid3
        result = searchService.search(criteria, musicFolders, IndexType.ARTIST_ID3);
        assertEquals(1, result.getArtists().size());
        assertEquals("_DIR_ Ravel", result.getArtists().get(0).getName());

        assertEquals(0, artistRepository.findByPresentFalse().size());

        List<Artist> artists = artistRepository.findAll();

        artistRepository.findByFolderInAndPresentTrue(musicFolders, Sort.by("id")).forEach(artist -> {
            artist.setPresent(false);
            artistRepository.saveAndFlush(artist);
        });

        artists = artistRepository.findByPresentFalse();

        assertEquals(4, artists.size());

        // albumId3
        result = searchService.search(criteriaAlbumId3, musicFolders, IndexType.ALBUM_ID3);
        assertEquals(1, result.getAlbums().size());
        assertEquals("Complete Piano Works", result.getAlbums().get(0).getName());

        List<Album> albums = albumRepository.findByPresentFalse();
        assertEquals(0, albums.size());

        albumRepository.findByFolderInAndPresentTrue(musicFolders, Sort.by("id")).forEach(album -> {
            album.setPresent(false);
            albumRepository.saveAndFlush(album);
        });

        albums = albumRepository.findByPresentFalse();
        assertEquals(4, albums.size());

        /* Does not scan, only expunges the index. */
        indexManager.startIndexing();
        indexManager.expunge();
        indexManager.stopIndexing(indexManager.getStatistics());

        /*
         * Subsequent search results.
         * Results can also be confirmed with Luke.
         */

        result = searchService.search(criteria, musicFolders, IndexType.ARTIST);
        assertEquals(0, result.getMediaFiles().size());

        result = searchService.search(criteria, musicFolders, IndexType.ALBUM);
        assertEquals(0, result.getMediaFiles().size());

        result = searchService.search(criteriaSong, musicFolders, IndexType.SONG);
        assertEquals(0, result.getMediaFiles().size());

        result = searchService.search(criteria, musicFolders, IndexType.ARTIST_ID3);
        assertEquals(0, result.getArtists().size());

        result = searchService.search(criteriaAlbumId3, musicFolders, IndexType.ALBUM_ID3);
        assertEquals(0, result.getAlbums().size());

    }

    private void deleteMediaFile(MediaFile mediaFile) {

        mediaFileRepository.findByPathAndFolderAndStartPosition(mediaFile.getPath(), mediaFile.getFolder(), mediaFile.getStartPosition())
                .ifPresent(mf -> {
                    mf.setPresent(false);
                    mf.setChildrenLastUpdated(Instant.ofEpochMilli(1));
                    mediaFileRepository.saveAndFlush(mf);
                });
    }

}