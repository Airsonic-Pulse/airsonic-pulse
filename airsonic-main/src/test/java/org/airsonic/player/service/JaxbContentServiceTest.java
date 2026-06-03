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

 Copyright 2025 (C) Y.Tory
 */
package org.airsonic.player.service;

import org.airsonic.player.controller.CoverArtController;
import org.airsonic.player.controller.JAXBWriter;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.Contributors;
import org.airsonic.player.domain.CoverArt;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Playlist;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.subsonic.restapi.AlbumID3;
import org.subsonic.restapi.ArtistID3;
import org.subsonic.restapi.Child;
import org.subsonic.restapi.Contributor;
import org.subsonic.restapi.MediaType;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JaxbContentServiceTest {
    @Spy
    private JAXBWriter jaxbWriter = new JAXBWriter(mock(VersionService.class));
    @Mock
    private ArtistService artistService;
    @Mock
    private CoverArtService coverArtService;
    @Mock
    private PlaylistService playlistService;
    @Mock
    private AlbumService albumService;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private TranscodingService transcodingService;
    @Mock
    private RatingService ratingService;
    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private JaxbContentService service;

    @Nested
    public class JaxbArtistTest {
        @Mock
        private Artist artist;
        private CoverArt coverArt = new CoverArt();

        @Test
        void createJaxbArtist_setsFieldsCorrectly() {
            Instant starredDate = Instant.now();
            ArtistID3 jaxbArtist = new ArtistID3();
            when(artist.getId()).thenReturn(42);
            when(artist.getName()).thenReturn("Test Artist");
            when(artist.getAlbumCount()).thenReturn(3);
            when(artistService.getStarredDate(eq(42), anyString())).thenReturn(starredDate);
            when(coverArtService.getArtistArt(42)).thenReturn(coverArt);

            ArtistID3 result = service.createJaxbArtist(jaxbArtist, artist, "user");

            assertEquals("42", result.getId());
            assertEquals("Test Artist", result.getName());
            assertEquals(3, result.getAlbumCount());
            assertThat(result.getStarred()).isNotNull();
            assertEquals(CoverArtController.ARTIST_COVERART_PREFIX + "42", result.getCoverArt());
            assertEquals("artist", result.getMediaType());
        }

        @Test
        void createJaxbArtist_noCoverArt() {
            ArtistID3 jaxbArtist = new ArtistID3();
            Artist artist = mock(Artist.class);
            when(artist.getId()).thenReturn(1);
            when(artist.getName()).thenReturn("NoArt");
            when(artist.getAlbumCount()).thenReturn(0);
            when(artistService.getStarredDate(eq(1), anyString())).thenReturn(null);
            when(coverArtService.getArtistArt(1)).thenReturn(CoverArt.NULL_ART);

            ArtistID3 result = service.createJaxbArtist(jaxbArtist, artist, "user");
            assertNull(result.getCoverArt());
            assertNull(result.getStarred());
            assertEquals("1", result.getId());
            assertEquals("NoArt", result.getName());
            assertEquals(0, result.getAlbumCount());
        }

        @Test
        void createJaxbArtist_setsRatings_whenArtistDirectoryResolvable() {
            ArtistID3 jaxbArtist = new ArtistID3();
            Artist artist = mock(Artist.class);
            MediaFile artistDir = mock(MediaFile.class);
            List<MusicFolder> folders = List.of(mock(MusicFolder.class));
            when(artist.getId()).thenReturn(7);
            when(artist.getName()).thenReturn("Rated Artist");
            when(artist.getAlbumCount()).thenReturn(2);
            when(coverArtService.getArtistArt(7)).thenReturn(CoverArt.NULL_ART);
            when(mediaFolderService.getMusicFoldersForUser("user")).thenReturn(folders);
            when(mediaFileService.getArtistByName("Rated Artist", folders)).thenReturn(artistDir);
            when(ratingService.getRatingForUser("user", artistDir)).thenReturn(4);
            when(ratingService.getAverageRating(artistDir)).thenReturn(3.7);

            ArtistID3 result = service.createJaxbArtist(jaxbArtist, artist, "user");

            assertEquals(4, result.getUserRating());
            assertEquals(3.7, result.getAverageRating());
        }

        @Test
        void createJaxbArtist_omitsUserRating_whenUserHasNoRatingButAverageExists() {
            ArtistID3 jaxbArtist = new ArtistID3();
            Artist artist = mock(Artist.class);
            MediaFile artistDir = mock(MediaFile.class);
            List<MusicFolder> folders = List.of(mock(MusicFolder.class));
            when(artist.getId()).thenReturn(8);
            when(artist.getName()).thenReturn("Average Only");
            when(artist.getAlbumCount()).thenReturn(1);
            when(coverArtService.getArtistArt(8)).thenReturn(CoverArt.NULL_ART);
            when(mediaFolderService.getMusicFoldersForUser("user")).thenReturn(folders);
            when(mediaFileService.getArtistByName("Average Only", folders)).thenReturn(artistDir);
            when(ratingService.getRatingForUser("user", artistDir)).thenReturn(null);
            when(ratingService.getAverageRating(artistDir)).thenReturn(2.5);

            ArtistID3 result = service.createJaxbArtist(jaxbArtist, artist, "user");

            assertNull(result.getUserRating());
            assertEquals(2.5, result.getAverageRating());
        }

        @Test
        void createJaxbArtist_omitsBothRatings_whenArtistIsVirtual() {
            // Virtual artist: derived from albumArtist tag with no physical directory. The
            // resolver returns null; rating lookups against a null MediaFile return null;
            // JAXB omits both attributes — no exception, no implicit MediaFile creation.
            ArtistID3 jaxbArtist = new ArtistID3();
            Artist artist = mock(Artist.class);
            List<MusicFolder> folders = List.of(mock(MusicFolder.class));
            when(artist.getId()).thenReturn(9);
            when(artist.getName()).thenReturn("Virtual Artist");
            when(artist.getAlbumCount()).thenReturn(0);
            when(coverArtService.getArtistArt(9)).thenReturn(CoverArt.NULL_ART);
            when(mediaFolderService.getMusicFoldersForUser("user")).thenReturn(folders);
            when(mediaFileService.getArtistByName("Virtual Artist", folders)).thenReturn(null);
            when(ratingService.getRatingForUser("user", null)).thenReturn(null);
            when(ratingService.getAverageRating(null)).thenReturn(null);

            ArtistID3 result = service.createJaxbArtist(jaxbArtist, artist, "user");

            assertNull(result.getUserRating());
            assertNull(result.getAverageRating());
            assertEquals("9", result.getId());
            assertEquals("Virtual Artist", result.getName());
        }
    }

    @Nested
    public class JaxbAlbumTest {
        @Mock
        private Album album;
        @Mock
        private Artist artist;
        private CoverArt coverArt = new CoverArt();
        private Instant starredDate = Instant.now();

        @Test
        void createJaxbAlbum_setsFieldsCorrectly() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(10);
            when(album.getName()).thenReturn("AlbumName");
            when(album.getArtist()).thenReturn("ArtistName");
            when(album.getSongCount()).thenReturn(12);
            when(album.getDuration()).thenReturn(1234.5);
            when(album.getCreated()).thenReturn(starredDate);
            when(album.getYear()).thenReturn(2020);
            when(album.getGenre()).thenReturn("Rock");
            when(album.getPlayCount()).thenReturn(42);
            when(album.getLastPlayed()).thenReturn(Instant.parse("2026-05-01T12:00:00Z"));
            when(album.getMusicBrainzReleaseId()).thenReturn("mbid-album-123");
            when(coverArtService.getAlbumArt(10)).thenReturn(coverArt);
            when(albumService.getAlbumStarredDate(10, "user")).thenReturn(starredDate);
            when(coverArtService.getAlbumArt(10)).thenReturn(coverArt);
            when(artistService.getArtist("ArtistName")).thenReturn(artist);
            when(artist.getId()).thenReturn(99);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertEquals("10", result.getId());
            assertEquals("AlbumName", result.getName());
            assertEquals("ArtistName", result.getArtist());
            assertEquals("99", result.getArtistId());
            assertEquals(CoverArtController.ALBUM_COVERART_PREFIX + "10", result.getCoverArt());
            assertEquals(12, result.getSongCount());
            assertEquals(1235, result.getDuration());
            assertNotNull(result.getCreated());
            assertNotNull(result.getStarred());
            assertEquals(2020, result.getYear());
            assertEquals("Rock", result.getGenre());
            assertEquals(42L, result.getPlayCount());
            assertNotNull(result.getPlayed());
            assertEquals("mbid-album-123", result.getMusicBrainzId());
            assertEquals("ArtistName", result.getDisplayArtist());
        }

        @Test
        void createJaxbAlbum_noArtistOrCoverArt() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(2);
            when(album.getName()).thenReturn("NoArtist");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(album.getYear()).thenReturn(null);
            when(album.getGenre()).thenReturn(null);
            when(coverArtService.getAlbumArt(2)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(2, "user")).thenReturn(null);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertNull(result.getArtistId());
            assertNull(result.getCoverArt());
        }

        @Test
        void createJaxbAlbum_setsCompilationAndDates() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(20);
            when(album.getName()).thenReturn("Comp");
            when(album.getArtist()).thenReturn("Various Artists");
            when(album.getSongCount()).thenReturn(12);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(20)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(20, "user")).thenReturn(null);
            when(artistService.getArtist("Various Artists")).thenReturn(null);
            when(album.getCompilation()).thenReturn(Boolean.TRUE);
            when(album.getOriginalReleaseDate()).thenReturn("2003-10-12");
            when(album.getReleaseDate()).thenReturn("2020-05");

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertEquals(Boolean.TRUE, result.isIsCompilation());
            assertNotNull(result.getOriginalReleaseDate());
            assertEquals(Integer.valueOf(2003), result.getOriginalReleaseDate().getYear());
            assertEquals(Integer.valueOf(10), result.getOriginalReleaseDate().getMonth());
            assertEquals(Integer.valueOf(12), result.getOriginalReleaseDate().getDay());
            assertNotNull(result.getReleaseDate());
            assertEquals(Integer.valueOf(2020), result.getReleaseDate().getYear());
            assertEquals(Integer.valueOf(5), result.getReleaseDate().getMonth());
            assertNull(result.getReleaseDate().getDay());
        }

        @Test
        void createJaxbAlbum_omitsCompilationAndDatesWhenAbsent() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(21);
            when(album.getName()).thenReturn("Plain");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(21)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(21, "user")).thenReturn(null);
            // Mockito's default for a Boolean-returning method is Boolean.FALSE, not null,
            // so the absent case needs an explicit null stub to model "tag missing".
            when(album.getCompilation()).thenReturn(null);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertNull(result.isIsCompilation());
            assertNull(result.getOriginalReleaseDate());
            assertNull(result.getReleaseDate());
        }

        @Test
        void createJaxbAlbum_populatesReleaseTypesAndRecordLabels() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(30);
            when(album.getName()).thenReturn("Multi");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(30)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(30, "user")).thenReturn(null);
            when(album.getReleaseTypes()).thenReturn("Album\nCompilation");
            when(album.getRecordLabels()).thenReturn("Sony Music\nWarner");

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertEquals(java.util.List.of("Album", "Compilation"), result.getReleaseTypes());
            assertEquals(2, result.getRecordLabels().size());
            assertEquals("Sony Music", result.getRecordLabels().get(0).getName());
            assertEquals("Warner", result.getRecordLabels().get(1).getName());
        }

        @Test
        void createJaxbAlbum_recordLabelNameWithGenreSeparatorRoundsTripIntact() {
            // Locks in that the pack delimiter is collision-resistant: a label name containing
            // ';' and '/' (e.g. the kind of characters the genre-separator setting often
            // includes) must NOT be split into multiple labels at response time.
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(31);
            when(album.getName()).thenReturn("Punct");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(31)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(31, "user")).thenReturn(null);
            when(album.getRecordLabels()).thenReturn("Sony/BMG; Columbia\nWarner");

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertEquals(2, result.getRecordLabels().size());
            assertEquals("Sony/BMG; Columbia", result.getRecordLabels().get(0).getName());
            assertEquals("Warner", result.getRecordLabels().get(1).getName());
        }

        @Test
        void createJaxbAlbum_omitsMultiValueWhenAbsent() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(32);
            when(album.getName()).thenReturn("Plain");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(32)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(32, "user")).thenReturn(null);
            // releaseTypes / recordLabels unstubbed → null on the mock → split yields empty
            // → no <releaseTypes>/<recordLabels> elements emitted (list stays empty).

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertTrue(result.getReleaseTypes().isEmpty());
            assertTrue(result.getRecordLabels().isEmpty());
        }

        @Test
        void createJaxbAlbum_threeArgOverloadEmitsNoDiscTitlesAndDoesNotFetchSongs() {
            // The 3-arg overload is the list-endpoint path. It must NOT trigger a per-album
            // mediaFileService.getSongsForAlbum call — the firm N+1 constraint — and must
            // emit no <discTitles> elements (no songs available to build them from).
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(40);
            when(album.getName()).thenReturn("ListPath");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(40)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(40, "user")).thenReturn(null);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertTrue(result.getDiscTitles().isEmpty());
            verify(mediaFileService, never()).getSongsForAlbum(org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any());
        }

        @Test
        void createJaxbAlbum_fourArgOverloadPopulatesDiscTitlesFromTracks() {
            // Realistic multi-disc album: disc 1 "Bonus" + disc 2 "Live in Tokyo", multiple
            // tracks per disc, first non-blank subtitle per disc wins, sorted ascending.
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(41);
            when(album.getName()).thenReturn("DetailPath");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(41)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(41, "user")).thenReturn(null);

            java.util.List<MediaFile> tracks = new java.util.ArrayList<>();
            tracks.add(track(1, "Bonus"));
            tracks.add(track(1, "Bonus"));
            tracks.add(track(2, "Live in Tokyo"));
            tracks.add(track(2, "Live in Tokyo"));

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user", tracks);

            assertEquals(2, result.getDiscTitles().size());
            assertEquals(1, result.getDiscTitles().get(0).getDisc());
            assertEquals("Bonus", result.getDiscTitles().get(0).getTitle());
            assertEquals(2, result.getDiscTitles().get(1).getDisc());
            assertEquals("Live in Tokyo", result.getDiscTitles().get(1).getTitle());
        }

        @Test
        void createJaxbAlbum_fourArgOverloadOmitsDiscsWithoutSubtitle() {
            // Discs 1/2/3 where only disc 2 carries a subtitle → one DiscTitle for disc 2;
            // discs 1 and 3 are skipped, not emitted with empty titles.
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(42);
            when(album.getName()).thenReturn("PartialSubtitles");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(42)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(42, "user")).thenReturn(null);

            java.util.List<MediaFile> tracks = new java.util.ArrayList<>();
            tracks.add(track(1, null));
            tracks.add(track(2, "Live"));
            tracks.add(track(3, ""));

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user", tracks);

            assertEquals(1, result.getDiscTitles().size());
            assertEquals(2, result.getDiscTitles().get(0).getDisc());
            assertEquals("Live", result.getDiscTitles().get(0).getTitle());
        }

        @Test
        void createJaxbAlbum_fourArgOverloadEmitsNothingWhenNoTracksHaveSubtitles() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(43);
            when(album.getName()).thenReturn("NoSubtitles");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(43)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(43, "user")).thenReturn(null);

            java.util.List<MediaFile> tracks = new java.util.ArrayList<>();
            tracks.add(track(1, null));
            tracks.add(track(2, null));

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user", tracks);

            assertTrue(result.getDiscTitles().isEmpty());
        }

        // Album-level ReplayGain emission. Mockito 5 returns boxed 0.0 (not null) for Double
        // return types, so the absent case needs explicit null stubs to model "no value".

        @Test
        void createJaxbAlbum_replayGain_omitsElementWhenAllSourcesNull() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(50);
            when(album.getName()).thenReturn("NoRG");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(50)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(50, "user")).thenReturn(null);
            when(album.getReplayGainAlbumGain()).thenReturn(null);
            when(album.getReplayGainAlbumPeak()).thenReturn(null);
            when(settingsService.getReplayGainFallback()).thenReturn(null);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertNull(result.getReplayGain(),
                    "replayGain element must be omitted when no source produces any value");
        }

        @Test
        void createJaxbAlbum_replayGain_emitsAlbumValuesWithoutTrackValues() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(51);
            when(album.getName()).thenReturn("RGTagged");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(51)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(51, "user")).thenReturn(null);
            when(album.getReplayGainAlbumGain()).thenReturn(-7.50);
            when(album.getReplayGainAlbumPeak()).thenReturn(0.988);
            when(settingsService.getReplayGainFallback()).thenReturn(null);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertNotNull(result.getReplayGain());
            assertEquals(-7.50, result.getReplayGain().getAlbumGain());
            assertEquals(0.988, result.getReplayGain().getAlbumPeak());
            assertNull(result.getReplayGain().getTrackGain(),
                    "trackGain has no album-level meaning and must be omitted");
            assertNull(result.getReplayGain().getTrackPeak(),
                    "trackPeak has no album-level meaning and must be omitted");
            assertNull(result.getReplayGain().getFallbackGain());
        }

        @Test
        void createJaxbAlbum_replayGain_emitsFallbackOnly_whenAlbumValuesAbsentButFallbackSet() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(52);
            when(album.getName()).thenReturn("FallbackOnly");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(52)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(52, "user")).thenReturn(null);
            when(album.getReplayGainAlbumGain()).thenReturn(null);
            when(album.getReplayGainAlbumPeak()).thenReturn(null);
            when(settingsService.getReplayGainFallback()).thenReturn(-10.0);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertNotNull(result.getReplayGain(),
                    "replayGain element must be emitted whenever fallbackGain is configured");
            assertEquals(-10.0, result.getReplayGain().getFallbackGain());
            assertNull(result.getReplayGain().getAlbumGain());
            assertNull(result.getReplayGain().getAlbumPeak());
            assertNull(result.getReplayGain().getTrackGain());
            assertNull(result.getReplayGain().getTrackPeak());
        }

        @Test
        void createJaxbAlbum_replayGain_emitsFallbackAlongsideAlbumValues() {
            AlbumID3 jaxbAlbum = new AlbumID3();
            when(album.getId()).thenReturn(53);
            when(album.getName()).thenReturn("AlbumPlusFallback");
            when(album.getArtist()).thenReturn(null);
            when(album.getSongCount()).thenReturn(0);
            when(album.getDuration()).thenReturn(0.0);
            when(album.getCreated()).thenReturn(null);
            when(coverArtService.getAlbumArt(53)).thenReturn(CoverArt.NULL_ART);
            when(albumService.getAlbumStarredDate(53, "user")).thenReturn(null);
            when(album.getReplayGainAlbumGain()).thenReturn(-6.5);
            when(album.getReplayGainAlbumPeak()).thenReturn(0.95);
            when(settingsService.getReplayGainFallback()).thenReturn(-8.0);

            AlbumID3 result = service.createJaxbAlbum(jaxbAlbum, album, "user");

            assertNotNull(result.getReplayGain());
            assertEquals(-6.5, result.getReplayGain().getAlbumGain());
            assertEquals(0.95, result.getReplayGain().getAlbumPeak());
            assertEquals(-8.0, result.getReplayGain().getFallbackGain());
            assertNull(result.getReplayGain().getTrackGain());
            assertNull(result.getReplayGain().getTrackPeak());
        }

        private MediaFile track(int discNumber, String discSubtitle) {
            MediaFile mf = new MediaFile();
            mf.setDiscNumber(discNumber);
            mf.setDiscSubtitle(discSubtitle);
            return mf;
        }
    }

    @Nested
    public class JaxbPlaylistTest {
        @Mock
        private Playlist playlist;

        @Test
        void createJaxbPlaylist_setsFieldsCorrectly() {
            org.subsonic.restapi.Playlist jaxbPlaylist = new org.subsonic.restapi.Playlist();
            Playlist playlist = mock(Playlist.class);
            when(playlist.getId()).thenReturn(5);
            when(playlist.getName()).thenReturn("MyPlaylist");
            when(playlist.getComment()).thenReturn("A comment");
            when(playlist.getUsername()).thenReturn("owner");
            when(playlist.getShared()).thenReturn(true);
            when(playlist.getFileCount()).thenReturn(7);
            when(playlist.getDuration()).thenReturn(321.0);
            when(playlist.getCreated()).thenReturn(Instant.ofEpochMilli(33333333L));
            when(playlist.getChanged()).thenReturn(Instant.ofEpochMilli(44444444L));
            when(playlistService.getPlaylistUsers(5)).thenReturn(Arrays.asList("user1", "user2"));

            org.subsonic.restapi.Playlist result = service.createJaxbPlaylist(jaxbPlaylist, playlist);

            assertEquals("5", result.getId());
            assertEquals("MyPlaylist", result.getName());
            assertEquals("A comment", result.getComment());
            assertEquals("owner", result.getOwner());
            assertEquals(true, result.isPublic());
            assertEquals(7, result.getSongCount());
            assertEquals(321, result.getDuration());
            assertNotNull(result.getCreated());
            assertNotNull(result.getChanged());
            assertEquals(CoverArtController.PLAYLIST_COVERART_PREFIX + "5", result.getCoverArt());
            assertEquals(Arrays.asList("user1", "user2"), result.getAllowedUser());
        }
    }

    @Nested
    class JaxbChildTest {
        @Mock
        private MediaFile mediaFile;
        @Mock
        private MediaFile parent;
        private CoverArt coverArt = new CoverArt();

        @Test
        void createJaxbChild_setsFieldsForFile() {
            Player player = mock(Player.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(parent);
            when(mediaFile.getId()).thenReturn(100);
            when(parent.getId()).thenReturn(99);
            when(mediaFileService.isRoot(parent)).thenReturn(false);
            when(mediaFile.getName()).thenReturn("song.mp3");
            when(mediaFile.getAlbumName()).thenReturn("Album");
            when(mediaFile.getArtist()).thenReturn("Artist");
            when(mediaFile.isDirectory()).thenReturn(false);
            when(mediaFile.isFile()).thenReturn(true);
            when(mediaFile.getYear()).thenReturn(2021);
            when(mediaFile.getGenre()).thenReturn("Pop");
            when(mediaFile.getCreated()).thenReturn(Instant.ofEpochMilli(55555555L));
            when(mediaFileService.getMediaFileStarredDate(mediaFile, "user"))
                    .thenReturn(Instant.ofEpochMilli(66666666L));
            when(ratingService.getRatingForUser("user", mediaFile)).thenReturn(4);
            when(ratingService.getAverageRating(mediaFile)).thenReturn(3.5);
            when(mediaFile.getPlayCount()).thenReturn(10);
            when(mediaFile.getLastPlayed()).thenReturn(Instant.parse("2026-05-01T12:00:00Z"));
            when(mediaFile.getMusicBrainzRecordingId()).thenReturn("mbid-track-456");
            when(mediaFile.getAlbumArtist()).thenReturn("Album Artist");
            when(mediaFile.getDuration()).thenReturn(200.0);
            when(mediaFile.getBitRate()).thenReturn(320);
            when(mediaFile.getTrackNumber()).thenReturn(1);
            when(mediaFile.getDiscNumber()).thenReturn(1);
            when(mediaFile.getFileSize()).thenReturn(123456L);
            when(mediaFile.getFormat()).thenReturn("mp3");
            when(mediaFile.isVideo()).thenReturn(false);
            when(mediaFile.getPath()).thenReturn("/music/song.mp3");
            when(mediaFile.getMediaType()).thenReturn(MediaFile.MediaType.MUSIC);
            Album album = mock(Album.class);
            when(albumService.getAlbumByMediaFile(mediaFile)).thenReturn(album);
            when(album.getId()).thenReturn(77);
            Artist artist = mock(Artist.class);
            when(artistService.getArtist("Artist")).thenReturn(artist);
            when(artist.getId()).thenReturn(88);
            when(coverArtService.getMediaFileArt(99)).thenReturn(coverArt);
            when(transcodingService.isTranscodingRequired(mediaFile, player)).thenReturn(true);
            when(transcodingService.getSuffix(player, mediaFile, null)).thenReturn("ogg");

            Child child = service.createJaxbChild(player, mediaFile, "user");
            assertEquals("100", child.getId());
            assertEquals("99", child.getParent());
            assertEquals("song.mp3", child.getTitle());
            assertEquals("Album", child.getAlbum());
            assertEquals("Artist", child.getArtist());
            assertFalse(child.isIsDir());
            assertEquals("99", child.getCoverArt());
            assertEquals(2021, child.getYear());
            assertEquals("Pop", child.getGenre());
            assertNotNull(child.getCreated());
            assertNotNull(child.getStarred());
            assertEquals(4, child.getUserRating());
            assertEquals(3.5, child.getAverageRating());
            assertEquals(10L, child.getPlayCount());
            assertEquals(200, child.getDuration());
            assertEquals(320, child.getBitRate());
            assertEquals(1, child.getTrack());
            assertEquals(1, child.getDiscNumber());
            assertEquals(123456L, child.getSize());
            assertEquals("mp3", child.getSuffix());
            assertNotNull(child.getContentType());
            assertFalse(child.isIsVideo());
            assertEquals("/music/song.mp3", child.getPath());
            assertEquals("77", child.getAlbumId());
            assertEquals("88", child.getArtistId());
            assertEquals(MediaType.MUSIC, child.getType());
            assertEquals("ogg", child.getTranscodedSuffix());
            assertNotNull(child.getTranscodedContentType());
            assertNotNull(child.getPlayed());
            assertEquals("mbid-track-456", child.getMusicBrainzId());
            assertEquals("Artist", child.getDisplayArtist());
            assertEquals("Album Artist", child.getDisplayAlbumArtist());
            assertEquals("music", child.getMediaType());
        }

        @Test
        void createJaxbChild_setsFieldsForDirectory() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(200);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(200)).thenReturn(coverArt);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertEquals("200", child.getId());
            assertTrue(child.isIsDir());
            assertEquals("200", child.getCoverArt());
        }

        @Test
        void createJaxbChild_noCoverArt() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(300);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(300)).thenReturn(CoverArt.NULL_ART);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertNull(child.getCoverArt());
        }

        // Mockito 5's RETURNS_DEFAULTS returns boxed-zero (0.0) for Double-returning mocked
        // methods rather than null, so unstubbed mediaFile.getReplayGain*() reads as 0.0 — a
        // valid gain value as far as buildReplayGain is concerned. These tests stub all four
        // explicitly to null so the omission and emission logic is the only thing under test.
        @Test
        void createJaxbChild_replayGain_omitsElement_whenAllSourcesNull() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(310);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(310)).thenReturn(CoverArt.NULL_ART);
            when(mediaFile.getReplayGainTrackGain()).thenReturn(null);
            when(mediaFile.getReplayGainAlbumGain()).thenReturn(null);
            when(mediaFile.getReplayGainTrackPeak()).thenReturn(null);
            when(mediaFile.getReplayGainAlbumPeak()).thenReturn(null);
            when(settingsService.getReplayGainFallback()).thenReturn(null);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertNull(child.getReplayGain(),
                    "replayGain element must be omitted when no source produces any value");
        }

        @Test
        void createJaxbChild_replayGain_emitsFallbackOnly_whenTagsAbsentButFallbackSet() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(320);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(320)).thenReturn(CoverArt.NULL_ART);
            when(mediaFile.getReplayGainTrackGain()).thenReturn(null);
            when(mediaFile.getReplayGainAlbumGain()).thenReturn(null);
            when(mediaFile.getReplayGainTrackPeak()).thenReturn(null);
            when(mediaFile.getReplayGainAlbumPeak()).thenReturn(null);
            when(settingsService.getReplayGainFallback()).thenReturn(-10.0);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertNotNull(child.getReplayGain(),
                    "replayGain element must be emitted whenever fallbackGain is configured");
            assertEquals(-10.0, child.getReplayGain().getFallbackGain());
            assertNull(child.getReplayGain().getTrackGain());
            assertNull(child.getReplayGain().getAlbumGain());
            assertNull(child.getReplayGain().getTrackPeak());
            assertNull(child.getReplayGain().getAlbumPeak());
        }

        @Test
        void createJaxbChild_replayGain_emitsFallbackAlongsideTagValues() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(330);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(330)).thenReturn(CoverArt.NULL_ART);
            when(mediaFile.getReplayGainTrackGain()).thenReturn(-6.5);
            when(mediaFile.getReplayGainAlbumGain()).thenReturn(null);
            when(mediaFile.getReplayGainTrackPeak()).thenReturn(0.988);
            when(mediaFile.getReplayGainAlbumPeak()).thenReturn(null);
            when(settingsService.getReplayGainFallback()).thenReturn(-8.0);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertNotNull(child.getReplayGain());
            assertEquals(-6.5, child.getReplayGain().getTrackGain());
            assertEquals(0.988, child.getReplayGain().getTrackPeak());
            assertEquals(-8.0, child.getReplayGain().getFallbackGain());
            assertNull(child.getReplayGain().getAlbumGain());
        }

        @Test
        void createJaxbChild_populatesGenresArrayAndKeepsSingleGenre() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(400);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(400)).thenReturn(CoverArt.NULL_ART);
            when(mediaFile.getGenre()).thenReturn("Rock; Metal");
            when(mediaFile.getGenres()).thenReturn("Rock; Metal");
            when(settingsService.getGenreSeparators()).thenReturn(";");

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertEquals("Rock; Metal", child.getGenre());
            assertEquals(2, child.getGenres().size());
            assertEquals("Rock", child.getGenres().get(0).getName());
            assertEquals("Metal", child.getGenres().get(1).getName());
            verify(settingsService).getGenreSeparators();
        }

        @Test
        void createJaxbChild_id3v1MappedNameMatchesBetweenGenreAndGenresArray() {
            // packGenres now maps each token through mapGenre, so the canonical column value for
            // an ID3v1 "(17)" file is "Rock" — the same name that mediaFile.getGenre() already
            // returns via the existing mapGenre(getFirst(GENRE)) path. Asserting both come out
            // equal locks in the symmetry the prior asymmetry-bug violated.
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(600);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(600)).thenReturn(CoverArt.NULL_ART);
            when(mediaFile.getGenre()).thenReturn("Rock");
            when(mediaFile.getGenres()).thenReturn("Rock");
            when(settingsService.getGenreSeparators()).thenReturn(";");

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertEquals("Rock", child.getGenre());
            assertEquals(1, child.getGenres().size());
            assertEquals("Rock", child.getGenres().get(0).getName());
            assertEquals(child.getGenre(), child.getGenres().get(0).getName());
        }

        @Test
        void createJaxbChild_noGenresWhenAbsent() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(500);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(500)).thenReturn(CoverArt.NULL_ART);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertNull(child.getGenre());
            assertTrue(child.getGenres().isEmpty());
        }
    }

    @Nested
    class JaxbContributorsTest {

        @Test
        void buildContributors_nullColumnReturnsEmpty() {
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.getContributors()).thenReturn(null);
            assertTrue(service.buildContributors(mediaFile).isEmpty());
        }

        @Test
        void buildContributors_blankColumnReturnsEmpty() {
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.getContributors()).thenReturn("");
            assertTrue(service.buildContributors(mediaFile).isEmpty());
        }

        @Test
        void buildContributors_packedColumnReturnsMatchingJaxbContributors() {
            MediaFile mediaFile = mock(MediaFile.class);
            String packed = Contributors.pack(List.of(
                    new org.airsonic.player.domain.Contributor("composer", null, "John Williams"),
                    new org.airsonic.player.domain.Contributor("lyricist", null, "Bernie Taupin")));
            when(mediaFile.getContributors()).thenReturn(packed);
            // Both contributors uncatalogued — fallback path produces empty-id artists.
            when(artistService.getArtist("John Williams")).thenReturn(null);
            when(artistService.getArtist("Bernie Taupin")).thenReturn(null);

            List<Contributor> result = service.buildContributors(mediaFile);

            assertEquals(2, result.size());
            assertEquals("composer", result.get(0).getRole());
            assertNull(result.get(0).getSubRole());
            assertEquals("John Williams", result.get(0).getArtist().getName());
            assertEquals("lyricist", result.get(1).getRole());
            assertEquals("Bernie Taupin", result.get(1).getArtist().getName());
        }

        @Test
        void createJaxbArtistByName_catalogedArtistEmitsRealId() {
            Artist catalogued = mock(Artist.class);
            when(catalogued.getId()).thenReturn(42);
            when(catalogued.getName()).thenReturn("John Williams");
            when(artistService.getArtist("John Williams")).thenReturn(catalogued);

            ArtistID3 result = service.createJaxbArtistByName("John Williams");

            assertEquals("42", result.getId());
            assertEquals("John Williams", result.getName());
        }

        @Test
        void createJaxbArtistByName_uncatalogedArtistEmitsEmptyIdSentinel() {
            // No matching Airsonic Artist for this tag-derived name → empty-string id
            // sentinel + raw tag name + albumCount=0 (the local XSD requires albumCount).
            when(artistService.getArtist("Unknown Composer")).thenReturn(null);

            ArtistID3 result = service.createJaxbArtistByName("Unknown Composer");

            assertEquals("", result.getId());
            assertEquals("Unknown Composer", result.getName());
            assertEquals(0, result.getAlbumCount());
        }

        @Test
        void createJaxbChild_populatesContributorsFromPackedColumn() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(700);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(700)).thenReturn(CoverArt.NULL_ART);
            String packed = Contributors.pack(List.of(
                    new org.airsonic.player.domain.Contributor("composer", null, "John Williams"),
                    new org.airsonic.player.domain.Contributor("lyricist", null, "Bernie Taupin")));
            when(mediaFile.getContributors()).thenReturn(packed);
            Artist catalogued = mock(Artist.class);
            when(catalogued.getId()).thenReturn(42);
            when(catalogued.getName()).thenReturn("John Williams");
            when(artistService.getArtist("John Williams")).thenReturn(catalogued);
            when(artistService.getArtist("Bernie Taupin")).thenReturn(null);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertEquals(2, child.getContributors().size());
            Contributor composer = child.getContributors().get(0);
            assertEquals("composer", composer.getRole());
            assertNull(composer.getSubRole());
            assertEquals("42", composer.getArtist().getId());
            assertEquals("John Williams", composer.getArtist().getName());
            Contributor lyricist = child.getContributors().get(1);
            assertEquals("lyricist", lyricist.getRole());
            assertNull(lyricist.getSubRole());
            assertEquals("", lyricist.getArtist().getId());
            assertEquals("Bernie Taupin", lyricist.getArtist().getName());
        }

        @Test
        void createJaxbChild_emitsPerformerWithSubRole() {
            // End-to-end: a performer Contributor packed into the media_file.contributors
            // column round-trips through Contributors.split → buildContributors → JAXB with
            // the instrument carried as subRole. Locks the Batch 2 promise that subRole
            // populates on the wire.
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(900);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(900)).thenReturn(CoverArt.NULL_ART);
            String packed = Contributors.pack(List.of(
                    new org.airsonic.player.domain.Contributor("performer", "Guitar", "Eric Clapton")));
            when(mediaFile.getContributors()).thenReturn(packed);
            when(artistService.getArtist("Eric Clapton")).thenReturn(null);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertEquals(1, child.getContributors().size());
            Contributor performer = child.getContributors().get(0);
            assertEquals("performer", performer.getRole());
            assertEquals("Guitar", performer.getSubRole());
            assertEquals("Eric Clapton", performer.getArtist().getName());
        }

        @Test
        void createJaxbChild_omitsContributorsWhenColumnNull() {
            Player player = mock(Player.class);
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFileService.getParentOf(mediaFile)).thenReturn(null);
            when(mediaFile.getId()).thenReturn(800);
            when(mediaFile.isDirectory()).thenReturn(true);
            when(mediaFile.isFile()).thenReturn(false);
            when(coverArtService.getMediaFileArt(800)).thenReturn(CoverArt.NULL_ART);
            when(mediaFile.getContributors()).thenReturn(null);

            Child child = service.createJaxbChild(player, mediaFile, "user");

            assertTrue(child.getContributors().isEmpty());
            // No DB artist lookup performed when the column is empty.
            verify(artistService, never()).getArtist(anyString());
        }
    }

    @Nested
    class CreateJaxbArtistFromMediaFileTest {
        @Test
        void createJaxbArtist_fromMediaFile_setsFieldsCorrectly_withTitle() {
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.getId()).thenReturn(123);
            when(mediaFile.getTitle()).thenReturn("Artist Title");
            when(mediaFileService.getMediaFileStarredDate(mediaFile, "user"))
                    .thenReturn(Instant.ofEpochMilli(123456789L));

            org.subsonic.restapi.Artist result = service.createJaxbArtist(mediaFile, "user");

            assertEquals("123", result.getId());
            assertEquals("Artist Title", result.getName());
            assertNotNull(result.getStarred());
        }

        @Test
        void createJaxbArtist_fromMediaFile_setsFieldsCorrectly_withNullTitle() {
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.getId()).thenReturn(124);
            when(mediaFile.getTitle()).thenReturn(null);
            when(mediaFile.getArtist()).thenReturn("Artist Name");
            when(mediaFileService.getMediaFileStarredDate(mediaFile, "user")).thenReturn(null);

            org.subsonic.restapi.Artist result = service.createJaxbArtist(mediaFile, "user");

            assertEquals("124", result.getId());
            assertEquals("Artist Name", result.getName());
            assertNull(result.getStarred());
        }

        @Test
        void createJaxbArtist_fromMediaFile_setsRatings() {
            MediaFile mediaFile = mock(MediaFile.class);
            when(mediaFile.getId()).thenReturn(125);
            when(mediaFile.getTitle()).thenReturn("Rated Artist");
            when(mediaFileService.getMediaFileStarredDate(mediaFile, "user")).thenReturn(null);
            when(ratingService.getRatingForUser("user", mediaFile)).thenReturn(5);
            when(ratingService.getAverageRating(mediaFile)).thenReturn(4.2);

            org.subsonic.restapi.Artist result = service.createJaxbArtist(mediaFile, "user");

            assertEquals(5, result.getUserRating());
            assertEquals(4.2, result.getAverageRating());
        }
    }

    @Nested
    class ParseItemDateTest {
        @Test
        void parseItemDate_fullDate() {
            org.subsonic.restapi.ItemDate d = JaxbContentService.parseItemDate("2003-10-12");
            assertNotNull(d);
            assertEquals(Integer.valueOf(2003), d.getYear());
            assertEquals(Integer.valueOf(10), d.getMonth());
            assertEquals(Integer.valueOf(12), d.getDay());
        }

        @Test
        void parseItemDate_yearAndMonth() {
            org.subsonic.restapi.ItemDate d = JaxbContentService.parseItemDate("2020-05");
            assertNotNull(d);
            assertEquals(Integer.valueOf(2020), d.getYear());
            assertEquals(Integer.valueOf(5), d.getMonth());
            assertNull(d.getDay());
        }

        @Test
        void parseItemDate_yearOnly() {
            org.subsonic.restapi.ItemDate d = JaxbContentService.parseItemDate("1999");
            assertNotNull(d);
            assertEquals(Integer.valueOf(1999), d.getYear());
            assertNull(d.getMonth());
            assertNull(d.getDay());
        }

        @Test
        void parseItemDate_tolerateTrailingTime() {
            org.subsonic.restapi.ItemDate d = JaxbContentService.parseItemDate("2003-10-12T00:00:00");
            assertNotNull(d);
            assertEquals(Integer.valueOf(2003), d.getYear());
            assertEquals(Integer.valueOf(10), d.getMonth());
            assertEquals(Integer.valueOf(12), d.getDay());
        }

        @Test
        void parseItemDate_tolerateTrailingSpaceDelimitedTime() {
            org.subsonic.restapi.ItemDate d = JaxbContentService.parseItemDate("2003-10-12 12:30");
            assertNotNull(d);
            assertEquals(Integer.valueOf(2003), d.getYear());
            assertEquals(Integer.valueOf(10), d.getMonth());
            assertEquals(Integer.valueOf(12), d.getDay());
        }

        @Test
        void parseItemDate_blankOrNullOrMalformedReturnsNull() {
            assertNull(JaxbContentService.parseItemDate(null));
            assertNull(JaxbContentService.parseItemDate(""));
            assertNull(JaxbContentService.parseItemDate("   "));
            assertNull(JaxbContentService.parseItemDate("not-a-date"));
            assertNull(JaxbContentService.parseItemDate("20"));
            assertNull(JaxbContentService.parseItemDate("99999"));
        }
    }

}
