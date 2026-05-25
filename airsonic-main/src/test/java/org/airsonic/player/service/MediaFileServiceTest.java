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
 */
package org.airsonic.player.service;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MediaFile.MediaType;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.repository.MediaFileRepository;
import org.airsonic.player.service.cache.MediaFileCache;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MediaFileServiceTest {

    @Mock
    private MetaDataParserFactory metaDataParserFactory;
    @Mock
    private MediaFileRepository mediaFileRepository;
    @Mock
    private CoverArtService coverArtService;
    @Mock
    private MediaFileCache mediaFileCache;
    @Mock
    private MediaFolderService mediaFolderService;
    @Mock
    private SettingsService settingsService;

    @InjectMocks
    private MediaFileService mediaFileService;


    @Mock
    private MusicFolder mockedFolder;

    @Mock
    private MediaFile mockedMediaFile;

    private final Path CLASS_PATH = Paths.get("src", "test", "resources");

    @BeforeEach
    public void setUp() {
        lenient().when(mockedFolder.getPath()).thenReturn(CLASS_PATH.resolve("MEDIAS"));
    }

    @Test
    public void createIndexedTracksFailedByNoIndexTracksReturnEmptyList() {
        // prepare test data
        MediaFile base = new MediaFile();
        base.setIndexPath("invalidCue/airsonic-test.cue");
        base.setPath("valid/airsonic-test.wav");
        base.setMediaType(MediaType.MUSIC);
        base.setFormat("wav");
        base.setId(10);
        base.setFolder(mockedFolder);

        when(mediaFileRepository.findByFolderAndPath(any(), eq("valid/airsonic-test.wav"))).thenReturn(List.of(mockedMediaFile));
        when(mockedMediaFile.isIndexedTrack()).thenReturn(true);
        when(mediaFileRepository.existsById(any())).thenReturn(true);

        // execute
        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base);

        // check empty list is returned
        assertTrue(actual.isEmpty());
        // verify updateMedia does not called
        verify(mediaFileRepository).findByFolderAndPath(any(), eq("valid/airsonic-test.wav"));
        verify(mediaFileRepository).save(base);
        verify(coverArtService).persistIfNeeded(eq(base));
    }

    @Test
    public void packMultiValueDedupsAndJoinsWithNewline() {
        // Single value → no trailing delimiter.
        assertEquals("Album", mediaFileService.packMultiValue(List.of("Album")));
        // Multiple values → joined by \n in order.
        assertEquals("Album\nCompilation", mediaFileService.packMultiValue(List.of("Album", "Compilation")));
        // Duplicates collapse, original order preserved.
        assertEquals("Album\nCompilation", mediaFileService.packMultiValue(List.of("Album", "Compilation", "Album")));
    }

    @Test
    public void packMultiValueReturnsNullForEmptyOrNull() {
        assertNull(mediaFileService.packMultiValue(null));
        assertNull(mediaFileService.packMultiValue(List.of()));
    }

    @Test
    public void packMultiValuePreservesPunctuationWithinValues() {
        // A record-label name with ';' and '/' must round-trip intact — proving the genre
        // separator was NOT reused as the pack delimiter (genre-separator default ';' would
        // mangle this name into two false labels).
        String packed = mediaFileService.packMultiValue(List.of("Sony/BMG; Columbia", "Warner"));
        // Round-trip through the response-side splitter must give back both originals verbatim.
        java.util.List<String> roundTripped = JaxbContentService.splitMultiValue(packed);
        assertEquals(2, roundTripped.size());
        assertEquals("Sony/BMG; Columbia", roundTripped.get(0));
        assertEquals("Warner", roundTripped.get(1));
    }

    @Test
    public void packGenresMapsId3v1NumericCodesPerToken() {
        when(settingsService.getGenreSeparators()).thenReturn(";");

        // A raw ID3v1 numeric-code value: getAll typically returns one entry "(17)" which
        // mapGenre resolves to "Rock", matching what the single `genre` column already stores.
        assertEquals("Rock", mediaFileService.packGenres(List.of("(17)")));

        // A packed delimited value with one numeric token mixed in is split first, then each
        // token mapped individually — never mapGenre-d as a whole packed string.
        assertEquals("Rock;Pop", mediaFileService.packGenres(List.of("(17); Pop")));

        // Cross-frame multi-value: two frames, one numeric, one text → both mapped, deduped,
        // joined with the primary separator.
        assertEquals("Rock;Metal", mediaFileService.packGenres(List.of("(17)", "Metal")));
    }
}
