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

import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.User;
import org.airsonic.player.repository.ArtistRepository;
import org.airsonic.player.repository.StarredArtistRepository;
import org.airsonic.player.service.cache.ArtistByNameCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ArtistServiceTest {

    @Mock
    private ArtistRepository artistRepository;

    @Mock
    private StarredArtistRepository starredArtistRepository;

    @Mock
    private SettingsService settingsService;

    private JWTSecurityService jwtSecurityService;

    @Mock
    private MediaFileService mediaFileService;

    @Mock
    private ArtistByNameCache artistByNameCache;

    private ArtistService artistService;

    @Mock
    private MediaFile mockedMediaFile;

    @BeforeEach
    public void setUp() {
        jwtSecurityService = Mockito.spy(new JWTSecurityService(settingsService));
        artistService = new ArtistService(artistRepository, starredArtistRepository, jwtSecurityService,
                mediaFileService, artistByNameCache);
    }

    @Test
    public void testGetArtistImageURL() {

        // Given
        Artist artist = new Artist();
        artist.setName("artist");
        artist.setId(1);
        when(artistRepository.findByName("artist")).thenReturn(Optional.of(artist));
        when(settingsService.getJWTKey()).thenReturn("jwtkey");

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageURL("http://example.com/", "artist", 30, User.USERNAME_GUEST);
        }

        // Then
        verify(artistRepository).findByName("artist");
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=ar-1&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));

    }

    @Test
    public void testGetArtistImageURLNoArtistShouldReturnNull() {

        // Given
        when(artistRepository.findByName("artist")).thenReturn(Optional.empty());

        // When
        String url = artistService.getArtistImageURL("http://example.com/", "artist", 30, User.USERNAME_GUEST);

        // Then
        verify(artistRepository).findByName("artist");
        verifyNoInteractions(jwtSecurityService, settingsService);
        assertNull(url);
    }

    @Test
    public void testGetArtistImageURLNullArtistNameShouldReturnNull() {

        // When
        String url = artistService.getArtistImageURL("http://example.com/", null, 30, User.USERNAME_GUEST);

        // Then
        verifyNoInteractions(artistRepository, jwtSecurityService, settingsService);
        assertNull(url);
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true"
    })
    public void testGetArtistImageURLbyMediaFileWithAlbumArtist(boolean isAudio, boolean isAlbum) {

        // Given
        Artist artist = new Artist();
        artist.setName("artist");
        artist.setId(1);
        when(artistRepository.findByName("artist")).thenReturn(Optional.of(artist));
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(isAudio);
        if (!isAudio) {
            when(mockedMediaFile.isAlbum()).thenReturn(isAlbum);
        }
        when(mockedMediaFile.getAlbumArtist()).thenReturn("artist");

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verify(artistRepository).findByName("artist");
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=ar-1&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
    }

    @ParameterizedTest
    @CsvSource({
        "true, true",
        "true, false",
        "false, true"
    })
    public void testGetArtistImageURLbyMediaFileWithArtist(boolean isAudio, boolean isAlbum) {

        // Given
        Artist artist = new Artist();
        artist.setName("artist");
        artist.setId(1);
        when(artistRepository.findByName("artist")).thenReturn(Optional.of(artist));
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(isAudio);
        if (!isAudio) {
            when(mockedMediaFile.isAlbum()).thenReturn(isAlbum);
        }
        when(mockedMediaFile.getArtist()).thenReturn("artist");

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verify(artistRepository).findByName("artist");
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=ar-1&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
    }

    @Test
    public void testGetArtistImageURLbyMediaFileWithArtistMedia() {

        // Given
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(false);
        when(mockedMediaFile.isAlbum()).thenReturn(false);

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verifyNoInteractions(artistRepository);
        verifyNoMoreInteractions(mockedMediaFile);
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=2&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
    }

    @Test
    public void testGetArtistImageURLbyMediaFileWithAlbumMediaWithNoArtistUrl() {

        // Given
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(false);
        when(mockedMediaFile.isAlbum()).thenReturn(true);
        when(mockedMediaFile.getAlbumArtist()).thenReturn(null);
        when(mockedMediaFile.getArtist()).thenReturn(null);
        when(mediaFileService.getParentOf(mockedMediaFile, true)).thenReturn(mockedMediaFile);

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verifyNoInteractions(artistRepository);
        verifyNoMoreInteractions(mockedMediaFile);
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=2&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
        verify(mediaFileService).getParentOf(mockedMediaFile, true);
    }

    @Test
    public void testGetArtistImageURLbyMediaFileWithAudioMediaWithNoArtistUrl() {

        // Given
        when(settingsService.getJWTKey()).thenReturn("jwtkey");
        when(mockedMediaFile.getId()).thenReturn(2);
        when(mockedMediaFile.isAudio()).thenReturn(true);
        when(mockedMediaFile.getAlbumArtist()).thenReturn(null);
        when(mockedMediaFile.getArtist()).thenReturn(null);
        when(mediaFileService.getParentOf(mockedMediaFile, true)).thenReturn(mockedMediaFile);

        // When
        Instant now = Instant.now();
        String url = "";
        try (MockedStatic<Instant> instantMock = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            instantMock.when(Instant::now).thenReturn(now);
            url = artistService.getArtistImageUrlByMediaFile("http://example.com/", mockedMediaFile, 30, User.USERNAME_GUEST);
        }

        // Then
        verifyNoInteractions(artistRepository);
        verifyNoMoreInteractions(mockedMediaFile);
        assertTrue(url.startsWith("http://example.com/ext/coverArt.view?id=2&size=30&jwt="));
        verify(jwtSecurityService).addJWTToken(eq(User.USERNAME_GUEST), any(UriComponentsBuilder.class),
                eq(now.plusSeconds(300L)));
        verify(mediaFileService, times(2)).getParentOf(mockedMediaFile, true);
    }

    @Test
    public void getArtistByName_cacheHit_skipsRepository() {
        // Cache returns Optional.of(artist) — repository must not be touched.
        Artist artist = new Artist();
        artist.setId(7);
        artist.setName("Cached");
        when(artistByNameCache.get("Cached")).thenReturn(Optional.of(artist));

        Artist result = artistService.getArtist("Cached");

        assertSame(artist, result);
        verify(artistByNameCache).get("Cached");
        verifyNoInteractions(artistRepository);
        verify(artistByNameCache, never()).put(any(), any());
    }

    @Test
    public void getArtistByName_cachedMiss_skipsRepository() {
        // Cache returns Optional.empty() — that's the "cached miss" path, also no DAO hit.
        // This is the load-bearing case: most contributor names miss, so caching misses is
        // what makes the cache pay off.
        when(artistByNameCache.get("Unknown Composer")).thenReturn(Optional.empty());

        Artist result = artistService.getArtist("Unknown Composer");

        assertNull(result);
        verify(artistByNameCache).get("Unknown Composer");
        verifyNoInteractions(artistRepository);
        verify(artistByNameCache, never()).put(any(), any());
    }

    @Test
    public void getArtistByName_cacheAbsent_queriesRepositoryAndPopulatesCache() {
        // Cache returns null — cache miss, fall through to repository, store the result.
        Artist artist = new Artist();
        artist.setId(8);
        artist.setName("Fresh");
        when(artistByNameCache.get("Fresh")).thenReturn(null);
        when(artistRepository.findByName("Fresh")).thenReturn(Optional.of(artist));

        Artist result = artistService.getArtist("Fresh");

        assertSame(artist, result);
        verify(artistByNameCache).get("Fresh");
        verify(artistRepository).findByName("Fresh");
        verify(artistByNameCache).put("Fresh", Optional.of(artist));
    }

    @Test
    public void getArtistByName_cacheAbsentAndRepositoryEmpty_storesEmptyOptional() {
        // Cache absent + repository miss must result in Optional.empty stored in the cache —
        // otherwise the next request for the same uncatalogued name would query again.
        when(artistByNameCache.get("New Unknown")).thenReturn(null);
        when(artistRepository.findByName("New Unknown")).thenReturn(Optional.empty());

        Artist result = artistService.getArtist("New Unknown");

        assertNull(result);
        verify(artistByNameCache).get("New Unknown");
        verify(artistRepository).findByName("New Unknown");
        verify(artistByNameCache).put("New Unknown", Optional.empty());
    }

    @Test
    public void getArtistByName_disabledCache_alwaysQueriesRepository() {
        // When cache is disabled (scan in progress), get() returns null and put() is a no-op
        // by the cache's own setEnabled contract — the service still routes through the cache
        // helper. Two successive calls must each hit the repository to guarantee post-scan
        // commits are visible to readers during the scan window.
        Artist artist = new Artist();
        artist.setId(9);
        artist.setName("Live");
        when(artistByNameCache.get("Live")).thenReturn(null);
        when(artistRepository.findByName("Live")).thenReturn(Optional.of(artist));

        Artist first = artistService.getArtist("Live");
        Artist second = artistService.getArtist("Live");

        assertSame(artist, first);
        assertSame(artist, second);
        verify(artistRepository, times(2)).findByName("Live");
    }

    @Test
    public void expunge_clearsArtistByNameCache() {
        // After deleting non-present artists, cached entries pointing at those ids would be
        // stale — clear() drops them so the next lookup repopulates from post-expunge state.
        artistService.expunge();

        verify(artistRepository).deleteAllByPresentFalse();
        verify(artistByNameCache).clear();
    }

    @Test
    public void getArtistByName_blankName_doesNotTouchCacheOrRepository() {
        Artist result = artistService.getArtist("");

        assertNull(result);
        verifyNoInteractions(artistByNameCache);
        verifyNoInteractions(artistRepository);
    }

    @Test
    public void getArtistByName_returnedValueEqualsCachedValue() {
        // Sanity: the wrapped Optional is unwrapped consistently — id and name come back
        // exactly as the cache stored them.
        Artist artist = new Artist();
        artist.setId(11);
        artist.setName("Bernie Taupin");
        when(artistByNameCache.get("Bernie Taupin")).thenReturn(Optional.of(artist));

        Artist result = artistService.getArtist("Bernie Taupin");

        assertEquals(Integer.valueOf(11), result.getId());
        assertEquals("Bernie Taupin", result.getName());
    }

}
