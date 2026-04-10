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
 */

package org.airsonic.player.service;

import de.umass.lastfm.Album;
import de.umass.lastfm.Artist;
import de.umass.lastfm.ImageSize;
import de.umass.lastfm.Track;
import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.AlbumNotes;
import org.airsonic.player.domain.ArtistBio;
import org.airsonic.player.domain.LastFmCoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.repository.ArtistRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
public class LastFmServiceTest {

    @Mock
    private AirsonicHomeConfig homeConfig;
    @Mock
    private ArtistRepository artistRepository;
    @Mock
    private MediaFileService mediaFileService;

    private LastFmService lastFmService;

    @Mock
    private MediaFile mockedMediaFile;
    @Mock
    private org.airsonic.player.domain.Artist mockedArtist;
    @Mock
    private org.airsonic.player.domain.Album mockedAlbum;

    @Mock
    private Artist mockedInfoArtist;
    @Mock
    private Artist mockedLastFmArtist1;
    @Mock
    private Artist mockedLastFmArtist2;
    @Mock
    private Track mockedLastFmTrack1;
    @Mock
    private Track mockedLastFmTrack2;
    @Mock
    private Album mockedInfoAlbum;
    @Mock
    private Album mockedLastFmAlbum;


    private static List<String> similarArtists = new ArrayList<String>();

    private static List<String> similarArtistsWithNotPresent = new ArrayList<String>();

    @BeforeAll
    public static void setUp() {
        for (int i = 0; i < 5; i++) {
            similarArtists.add("artist" + i);
            similarArtistsWithNotPresent.add("notPresentArtist" + i);
        }
    }

    @TempDir
    private Path tempDir;

    @BeforeEach
    public void setUpBeforeAll() {
        when(homeConfig.getAirsonicHome()).thenReturn(tempDir);
        lastFmService = new LastFmService(homeConfig, artistRepository, mediaFileService);
    }

    private void setupMockedMediaFileReturnNullArtist() {
        when(mockedMediaFile.getName()).thenReturn(null);
        when(mockedMediaFile.isAlbum()).thenReturn(false);
        when(mockedMediaFile.isFile()).thenReturn(false);
    }

    private void setupMockedMediaFileReturnTestArtist() {
        when(mockedMediaFile.getAlbumArtist()).thenReturn("testArtist");
        when(mockedMediaFile.isAlbum()).thenReturn(true);
    }

    @Test
    public void testGetSimilarArtistsByMediaFileWithNullMediaFileShouldReturnEmptyList() {
        assertEquals(0, lastFmService.getSimilarArtistsByMediaFile(null, 0, true, new ArrayList<MusicFolder>()).size());
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "3, 1"
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    public void testGetSimilarArtistsByMediaFileWithIncludeNotPresentFalseShouldReturnWithNotPresent(int count, int expectedSize) {
        setupMockedMediaFileReturnTestArtist();
        when(mockedLastFmArtist1.getName()).thenReturn(similarArtists.get(0));
        when(mockedLastFmArtist2.getName()).thenReturn(similarArtistsWithNotPresent.get(0));
        when(mediaFileService.getArtistByName(startsWith("artist"), any())).thenReturn(new MediaFile());
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");

        try (MockedStatic<Artist> mockedArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedArtist.when(() -> Artist.getSimilar(eq("testArtist"), anyString())).thenReturn(Arrays.asList(mockedLastFmArtist1, mockedLastFmArtist2));
            mockedArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            assertEquals(expectedSize, lastFmService.getSimilarArtistsByMediaFile(mockedMediaFile, count, false, new ArrayList<MusicFolder>()).size());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "2, 2",
        "3, 2"
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    public void testGetSimilarArtistsByMediaFileWithIncludeNotPresentTrueShouldReturnWithNotPresent(int count, int expectedSize) {
        setupMockedMediaFileReturnTestArtist();
        when(mockedLastFmArtist1.getName()).thenReturn(similarArtists.get(0));
        when(mockedLastFmArtist2.getName()).thenReturn(similarArtistsWithNotPresent.get(0));
        when(mediaFileService.getArtistByName(startsWith("artist"), any())).thenReturn(new MediaFile());
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");

        try (MockedStatic<Artist> mockedArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedArtist.when(() -> Artist.getSimilar(eq("testArtist"), anyString())).thenReturn(Arrays.asList(mockedLastFmArtist1, mockedLastFmArtist2));
            mockedArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            List<MediaFile> actual = lastFmService.getSimilarArtistsByMediaFile(mockedMediaFile, count, true, new ArrayList<MusicFolder>());
            assertEquals(expectedSize, actual.size());
            if (count > 1) {
                assertEquals(-1, actual.get(1).getId());
            }
        }
    }

    @Test
    public void testGetSimilarArtistsWithNullArtistShouldReturnEmptyList() {
        assertEquals(0, lastFmService.getSimilarArtists(null, 10, true, new ArrayList<MusicFolder>()).size());
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "3, 1"
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    public void testGetSimilarArtistsWithIncludeNotPresentFalseShouldReturnWithNotPresent(int count, int expectedSize) {
        when(mockedArtist.getName()).thenReturn("testArtist");
        when(mockedLastFmArtist1.getName()).thenReturn(similarArtists.get(0));
        when(mockedLastFmArtist2.getName()).thenReturn(similarArtistsWithNotPresent.get(0));
        when(artistRepository.findByNameAndFolderIn(startsWith("artist"), any())).thenReturn(Optional.of(new org.airsonic.player.domain.Artist()));
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getSimilar(eq("testArtist"), anyString())).thenReturn(Arrays.asList(mockedLastFmArtist1, mockedLastFmArtist2));
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            assertEquals(expectedSize, lastFmService.getSimilarArtists(mockedArtist, count, false, new ArrayList<MusicFolder>()).size());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "2, 2",
        "3, 2"
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    public void testGetSimilarArtistsWithIncludeNotPresentTrueShouldReturnWithNotPresent(int count, int expectedSize) {
        when(mockedArtist.getName()).thenReturn("testArtist");
        when(mockedLastFmArtist1.getName()).thenReturn(similarArtists.get(0));
        when(mockedLastFmArtist2.getName()).thenReturn(similarArtistsWithNotPresent.get(0));
        when(mediaFileService.getArtistByName(startsWith("artist"), any())).thenReturn(new MediaFile());
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getSimilar(eq("testArtist"), anyString())).thenReturn(Arrays.asList(mockedLastFmArtist1, mockedLastFmArtist2));
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            List<org.airsonic.player.domain.Artist> actual = lastFmService.getSimilarArtists(mockedArtist, count, true, new ArrayList<MusicFolder>());
            assertEquals(expectedSize, actual.size());
            if (count > 1) {
                assertEquals(-1, actual.get(1).getId());
            }
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "2, 2",
        "3, 2"
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    public void testGetSimilarSongs(int count, int expectedSize) {
        when(mockedArtist.getName()).thenReturn("testArtist");
        when(mediaFileService.getSongsByArtist(0, 1000, "testArtist")).thenReturn(Arrays.asList(mockedMediaFile));
        when(mediaFileService.getSongsByArtist(0, 1000, similarArtists.get(0))).thenReturn(Arrays.asList(new MediaFile()));
        when(mockedLastFmArtist1.getName()).thenReturn(similarArtists.get(0));
        when(mockedLastFmArtist2.getName()).thenReturn(similarArtistsWithNotPresent.get(0));
        when(mediaFileService.getArtistByName(startsWith("artist"), any())).thenReturn(new MediaFile());
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        org.airsonic.player.domain.Artist artist = new org.airsonic.player.domain.Artist();
        artist.setName("testArtist");
        when(artistRepository.findByNameAndFolderIn(startsWith("artist"), any())).thenReturn(Optional.of(artist));

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getSimilar(eq("testArtist"), anyString())).thenReturn(Arrays.asList(mockedLastFmArtist1, mockedLastFmArtist2));
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            assertEquals(expectedSize, lastFmService.getSimilarSongs(mockedArtist, count, new ArrayList<MusicFolder>()).size());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "3, 1"
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    public void testGetSimilarSongsByMediaFileWithNullArtist(int count, int expectedSize) {
        setupMockedMediaFileReturnTestArtist();
        when(mediaFileService.getArtistByName(eq("testArtist"),any())).thenReturn(null);
        when(mockedLastFmArtist1.getName()).thenReturn(similarArtists.get(0));
        when(mockedLastFmArtist2.getName()).thenReturn(similarArtistsWithNotPresent.get(0));
        when(mediaFileService.getArtistByName(startsWith("artist"), any())).thenReturn(new MediaFile());
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        when(mediaFileService.getRandomSongsForParent(any(), eq(count))).thenReturn(Arrays.asList(mockedMediaFile));

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getSimilar(eq("testArtist"), anyString())).thenReturn(Arrays.asList(mockedLastFmArtist1, mockedLastFmArtist2));
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            assertEquals(expectedSize, lastFmService.getSimilarSongsByMediaFile(mockedMediaFile, count, new ArrayList<MusicFolder>()).size());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "2, 2",
        "3, 2",
    })
    @MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
    public void testGetSimilarSongsByMediaFileWithArtist(int count, int expectedSize) {
        setupMockedMediaFileReturnTestArtist();
        MediaFile artistMediaFile = new MediaFile();
        artistMediaFile.setArtist("testArtist");
        when(mediaFileService.getArtistByName(eq("testArtist"),any())).thenReturn(artistMediaFile);
        when(mockedLastFmArtist1.getName()).thenReturn(similarArtists.get(0));
        when(mockedLastFmArtist2.getName()).thenReturn(similarArtistsWithNotPresent.get(0));
        when(mediaFileService.getArtistByName(startsWith("artist"), any())).thenReturn(new MediaFile());
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        when(mediaFileService.getRandomSongsForParent(any(), eq(count))).thenReturn(Arrays.asList(mockedMediaFile));

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getSimilar(eq("testArtist"), anyString())).thenReturn(Arrays.asList(mockedLastFmArtist1, mockedLastFmArtist2));
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            assertEquals(expectedSize, lastFmService.getSimilarSongsByMediaFile(mockedMediaFile, count, new ArrayList<MusicFolder>()).size());
        }
    }

    @Test
    public void testGetArtistBioByMediaFileWithNullArtistShouldReturnNull() {
        // return artist with null name
        setupMockedMediaFileReturnNullArtist();

        // verify
        assertNull(lastFmService.getArtistBioByMediaFile(mockedMediaFile, Locale.ENGLISH));
    }

    @Test
    public void testGetArtistBioByMediaFileWithNullArtistInfoShouldReturnNull() {
        // given
        setupMockedMediaFileReturnTestArtist();
        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), eq(Locale.ENGLISH), isNull(), anyString())).thenReturn(null);
            // verify
            assertNull(lastFmService.getArtistBioByMediaFile(mockedMediaFile, Locale.ENGLISH));
        }
    }

    @Test
    public void testGetArtistBioByMediaFileWithExceptionShouldReturnNull() {
        // given
        setupMockedMediaFileReturnTestArtist();
        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), eq(Locale.ENGLISH), isNull(), anyString())).thenThrow(new RuntimeException());
            // verify
            assertNull(lastFmService.getArtistBioByMediaFile(mockedMediaFile, Locale.ENGLISH));
        }
    }

    @Test
    public void testGetArtistBioByMediaFileShouldReturnArtistBio() {
        // given
        setupMockedMediaFileReturnTestArtist();
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        when(mockedInfoArtist.getMbid()).thenReturn("testMbid");
        when(mockedInfoArtist.getUrl()).thenReturn("testUrl");

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), eq(Locale.ENGLISH), isNull(), anyString())).thenReturn(mockedInfoArtist);
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            // verify
            ArtistBio artistBio = lastFmService.getArtistBioByMediaFile(mockedMediaFile, Locale.ENGLISH);
            assertEquals("testSummary", artistBio.biography());
            assertEquals("testMbid", artistBio.musicBrainzId());
            assertEquals("testUrl", artistBio.lastFmUrl());
            verifyNoMoreInteractions(mockedInfoArtist);
        }
    }

    @Test
    public void testGetArtistBioShouldRuturn() {

        // return artist with null name
        when(mockedArtist.getName()).thenReturn("testArtist");

        // verify
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        when(mockedInfoArtist.getMbid()).thenReturn("testMbid");
        when(mockedInfoArtist.getUrl()).thenReturn("testUrl");

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), eq(Locale.ENGLISH), isNull(), anyString())).thenReturn(mockedInfoArtist);
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            // verify
            ArtistBio artistBio = lastFmService.getArtistBio(mockedArtist, Locale.ENGLISH);
            assertEquals("testSummary", artistBio.biography());
            assertEquals("testMbid", artistBio.musicBrainzId());
            assertEquals("testUrl", artistBio.lastFmUrl());
            verifyNoMoreInteractions(mockedInfoArtist);
        }
    }

    @Test
    public void testGetTopSongsByMediaFileWithNullArtistShouldReturnEmptyList() {
        // return artist with null name
        setupMockedMediaFileReturnNullArtist();

        // verify
        assertEquals(0, lastFmService.getTopSongsByMediaFile(mockedMediaFile, 10, new ArrayList<MusicFolder>()).size());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0})
    public void testGetTopSongsByMediaFileWithCountSmallerThanZeroShouldReturnEmptyList(int count) {
        // given
        setupMockedMediaFileReturnTestArtist();

        // verify
        assertEquals(0, lastFmService.getTopSongsByMediaFile(mockedMediaFile, count, new ArrayList<MusicFolder>()).size());
    }

    @Test
    public void testGetTopSongsByMediaFileWithNullCannonicalArtistShouldReturnEmptyList() {
        // given
        setupMockedMediaFileReturnTestArtist();

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(null);
            // verify
            assertEquals(0, lastFmService.getTopSongsByMediaFile(mockedMediaFile, 10, new ArrayList<MusicFolder>()).size());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "1, 1",
        "2, 2",
        "3, 2"
    })
    @MockitoSettings(strictness = Strictness.LENIENT)
    public void testGetTopSongsByMediaFileShouldReturnList(int count, int expectedSize) {
        // given
        setupMockedMediaFileReturnTestArtist();
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        when(mockedLastFmTrack1.getName()).thenReturn("testTrack");
        when(mockedLastFmTrack2.getName()).thenReturn("notPresentTestTrack");
        when(mediaFileService.getSongByArtistAndTitle(eq("testArtist"), startsWith("testTrack"), any())).thenReturn(new MediaFile());
        when(mediaFileService.getSongByArtistAndTitle(eq("testArtist"), startsWith("notPresentTestTrack"), any())).thenReturn(null);

        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            mockedStaticArtist.when(() -> Artist.getTopTracks(eq("testArtist"), anyString())).thenReturn(Arrays.asList(mockedLastFmTrack1, mockedLastFmTrack1, mockedLastFmTrack2));
            // verify
            assertEquals(expectedSize, lastFmService.getTopSongsByMediaFile(mockedMediaFile, count, new ArrayList<MusicFolder>()).size());
        }
    }

    @Test
    public void testGetAlbumNotesByMediaFileWithNullArtistShouldReturnNull() {
        // return artist with null name
        setupMockedMediaFileReturnNullArtist();
        when(mockedMediaFile.getAlbumName()).thenReturn("testAlbum");

        // verify
        assertNull(lastFmService.getAlbumNotesByMediaFile(mockedMediaFile));
    }

    @Test
    public void testGetAlbumNotesByMediaFileWithNullAlbumShouldReturnNull() {
        // return artist with null name
        setupMockedMediaFileReturnTestArtist();
        when(mockedMediaFile.getAlbumName()).thenReturn(null);
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");

        // verify
        try (MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            assertNull(lastFmService.getAlbumNotesByMediaFile(mockedMediaFile));
        }
    }

    @Test
    public void testGetAlbumNotesByMediaFileWithNullAlbumInfoShouldReturnNull() {
        // return artist with null name
        setupMockedMediaFileReturnTestArtist();
        when(mockedMediaFile.getAlbumName()).thenReturn("testAlbum");
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        try (MockedStatic<Artist> mockedStaticArtist = Mockito.mockStatic(Artist.class);
            MockedStatic<Album> mockedStaticAlbum = Mockito.mockStatic(Album.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            mockedStaticAlbum.when(() -> Album.getInfo(eq("testArtist"), eq("testAlbum"), anyString())).thenReturn(null);
            // verify
            assertNull(lastFmService.getAlbumNotesByMediaFile(mockedMediaFile));
        }
    }

    @Test
    public void testGetAlbumNotesByMediaFileShouldReturnAlbumNotes() {
        // return artist with null name
        setupMockedMediaFileReturnTestArtist();
        when(mockedMediaFile.getAlbumName()).thenReturn("testAlbum");
        when(mockedInfoAlbum.getWikiSummary()).thenReturn("testSummary");
        when(mockedInfoAlbum.getMbid()).thenReturn("testMbid");
        when(mockedInfoAlbum.getUrl()).thenReturn("testUrl");
        when(mockedInfoAlbum.getImageURL(eq(ImageSize.MEGA))).
            thenReturn("testMegaImageURL");
        when(mockedInfoAlbum.getImageURL(eq(ImageSize.LARGE))).
            thenReturn("testLargeImageURL");
        when(mockedInfoAlbum.getImageURL(eq(ImageSize.MEDIUM))).
            thenReturn("testMediumImageURL");
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        try (MockedStatic<Artist> mockedStaticArtist = Mockito.mockStatic(Artist.class);
            MockedStatic<Album> mockedStaticAlbum = Mockito.mockStatic(Album.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            mockedStaticAlbum.when(() -> Album.getInfo(eq("testArtist"), eq("testAlbum"), anyString())).thenReturn(mockedInfoAlbum);
            // verify
            AlbumNotes albumNotes = lastFmService.getAlbumNotesByMediaFile(mockedMediaFile);
            assertEquals("testSummary", albumNotes.notes());
            assertEquals("testMbid", albumNotes.musicBrainzId());
            assertEquals("testUrl", albumNotes.lastFmUrl());
            assertEquals("testMegaImageURL", albumNotes.largeImageUrl());
            assertEquals("testLargeImageURL", albumNotes.mediumImageUrl());
            assertEquals("testMediumImageURL", albumNotes.smallImageUrl());
        }
    }

    @Test
    public void testGetAlbumNotesByAlbumShouldReturnAlbumNotes() {
        when(mockedAlbum.getArtist()).thenReturn("testArtist");
        when(mockedAlbum.getName()).thenReturn("testAlbum");
        when(mockedInfoAlbum.getWikiSummary()).thenReturn("testSummary");
        when(mockedInfoAlbum.getMbid()).thenReturn("testMbid");
        when(mockedInfoAlbum.getUrl()).thenReturn("testUrl");
        when(mockedInfoAlbum.getImageURL(eq(ImageSize.MEGA))).
            thenReturn("testMegaImageURL");
        when(mockedInfoAlbum.getImageURL(eq(ImageSize.LARGE))).
            thenReturn("testLargeImageURL");
        when(mockedInfoAlbum.getImageURL(eq(ImageSize.MEDIUM))).
            thenReturn("testMediumImageURL");
        when(mockedInfoArtist.getWikiSummary()).thenReturn("testSummary");
        try (
            MockedStatic<Artist> mockedStaticArtist = org.mockito.Mockito.mockStatic(Artist.class);
            MockedStatic<Album> mockedStaticAlbum = org.mockito.Mockito.mockStatic(Album.class)) {
            mockedStaticArtist.when(() -> Artist.getInfo(eq("testArtist"), anyString())).thenReturn(mockedInfoArtist);
            mockedStaticAlbum.when(() -> Album.getInfo(eq("testArtist"), eq("testAlbum"), anyString())).thenReturn(mockedInfoAlbum);
            // verify
            AlbumNotes albumNotes = lastFmService.getAlbumNotesByAlbum(mockedAlbum);
            assertEquals("testSummary", albumNotes.notes());
            assertEquals("testMbid", albumNotes.musicBrainzId());
            assertEquals("testUrl", albumNotes.lastFmUrl());
            assertEquals("testMegaImageURL", albumNotes.largeImageUrl());
            assertEquals("testLargeImageURL", albumNotes.mediumImageUrl());
            assertEquals("testMediumImageURL", albumNotes.smallImageUrl());
        }
    }

    @Test
    public void testSearchCoverArtWithNullArtistAndAlbumShouldReturnEmptyList() {
        // verify
        assertEquals(0, lastFmService.searchCoverArt(null, null).size());
    }

    @ParameterizedTest
    @CsvSource(value = {
        "testArtist,testAlbum,testArtist testAlbum",
        "testArtist,,testArtist ",
        ",testAlbum,testAlbum"}, ignoreLeadingAndTrailingWhitespace = false)
    public void testSearchCoverArtWithShouldReturnList(String artist, String album, String expectedQuery) {

        when(mockedLastFmAlbum.getImageURL(any())).
            thenReturn("testImageURL");
        when(mockedLastFmAlbum.getArtist()).thenReturn("testArtist");
        when(mockedLastFmAlbum.getName()).thenReturn("testAlbum");

        try (
            MockedStatic<Album> mockedStaticAlbum = Mockito.mockStatic(Album.class)) {
            mockedStaticAlbum.when(() -> Album.search(eq(expectedQuery), anyString())).thenReturn(Arrays.asList(mockedLastFmAlbum, mockedLastFmAlbum));
            // verify
            List<LastFmCoverArt> cache = lastFmService.searchCoverArt(artist, album);
            assertEquals(2, cache.size());
            assertEquals("testAlbum", cache.get(0).getAlbum());
            assertEquals("testArtist", cache.get(0).getArtist());
            assertEquals("testImageURL", cache.get(0).getImageUrl());
        }
    }

}
