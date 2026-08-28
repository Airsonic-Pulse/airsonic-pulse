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
import org.airsonic.player.repository.MusicFileInfoRepository;
import org.airsonic.player.service.cache.MediaFileCache;
import org.airsonic.player.service.metadata.MetaDataParserFactory;
import org.airsonic.player.util.FileUtil;
import org.digitalmediaserver.cuelib.CueSheet;
import org.digitalmediaserver.cuelib.FileData;
import org.digitalmediaserver.cuelib.Index;
import org.digitalmediaserver.cuelib.Position;
import org.digitalmediaserver.cuelib.TrackData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    @Mock
    private MusicFileInfoRepository musicFileInfoRepository;

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

    // ---------------------------------------------------------------------------------------
    // CUE indexed-track materialisation (#211): multifile FILE-block mapping + fractional frame
    // offsets. Tests build the CueSheet in memory; base resolves to the real MEDIAS/piano.mp3 so
    // Files.size succeeds.
    // ---------------------------------------------------------------------------------------

    @Test
    public void createIndexedTracksUsesOnlyTracksFromMatchingCueFileData() {
        // Two FILE blocks; base is piano.mp3, so only that block's tracks materialise (defect #1).
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData otherFileData = new FileData(cueSheet, "airsonic-test.wav", "WAVE");
        otherFileData.getTrackData().add(createTrack(otherFileData, 1, "Wrong File Track", 0, 0, 0));
        cueSheet.getFileData().add(otherFileData);

        FileData matchingFileData = new FileData(cueSheet, "piano.mp3", "MP3");
        matchingFileData.getTrackData().add(createTrack(matchingFileData, 2, "Matching Track 1", 0, 0, 0));
        matchingFileData.getTrackData().add(createTrack(matchingFileData, 3, "Matching Track 2", 0, 10, 37));
        cueSheet.getFileData().add(matchingFileData);

        MediaFile base = cueBase();
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(2, actual.size());
        assertEquals("Matching Track 1", actual.get(0).getTitle());
        assertEquals("Matching Track 2", actual.get(1).getTitle());
        assertEquals("piano.mp3", actual.get(0).getPath());
        assertEquals("piano.mp3", actual.get(1).getPath());
        // 0:10:37 -> 10 + 37/75.0 = 10.493 (defect #2: the fraction survives, not truncated to 10)
        assertEquals(0.0d, actual.get(0).getStartPosition(), 0.0d);
        assertEquals(10.493d, actual.get(1).getStartPosition(), 0.001d);
        assertEquals(10.493d, actual.get(0).getDuration(), 0.001d);
        // last track in the block runs to the file length (30.0), not the other block's track
        assertEquals(19.506d, actual.get(1).getDuration(), 0.001d);
    }

    @Test
    public void createIndexedTracksRefreshesExistingCueTrackMetadataPreservingPlayCount() {
        // An existing indexed track is refreshed in place (title/trackNumber/duration) while
        // playCount/lastPlayed/comment are preserved.
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 2, "Fresh Title", 0, 0, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = cueBase();

        MediaFile staleTrack = new MediaFile();
        staleTrack.setId(42);
        staleTrack.setPath("piano.mp3");
        staleTrack.setFolder(mockedFolder);
        staleTrack.setStartPosition(0.0);
        staleTrack.setDuration(10.0);
        staleTrack.setTitle("Stale Title");
        staleTrack.setTrackNumber(5);
        staleTrack.setPlayCount(7);
        staleTrack.setLastPlayed(Instant.ofEpochSecond(1000));
        staleTrack.setComment("keep me");

        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of(staleTrack));
        when(mediaFileRepository.existsById(eq(42))).thenReturn(true);

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(1, actual.size());
        assertEquals("Fresh Title", actual.get(0).getTitle());
        assertEquals(2L, (long) actual.get(0).getTrackNumber());
        assertEquals(30.0d, actual.get(0).getDuration(), 0.001d);
        // preserved across the in-place refresh
        assertEquals(7, actual.get(0).getPlayCount());
        assertEquals(Instant.ofEpochSecond(1000), actual.get(0).getLastPlayed());
        assertEquals("keep me", actual.get(0).getComment());
        // refreshed in place: same row instance is saved, no new row created
        verify(mediaFileRepository).save(staleTrack);
    }

    @Test
    public void createIndexedTracksSingleFileCarriesSubSecondStartPosition() {
        // Defect #2 direct test on the single-file (common) path: a track at 1:23:34 keeps its
        // sub-second component (.453) rather than truncating to 83.0.
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 1, "Only Track", 1, 23, 34));
        cueSheet.getFileData().add(fileData);

        MediaFile base = cueBase();
        base.setDuration(200.0);
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(1, actual.size());
        // 60 + 23 + 34/75.0 = 83.453 — proves the fraction is not truncated
        assertEquals(83.453d, actual.get(0).getStartPosition(), 0.001d);
    }

    @Test
    public void createIndexedTracksLastTrackInSingleTrackBlockUsesFileLength() {
        // A block with a single track: its duration runs to the file length, not to any other block.
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 1, "Sole Track", 0, 5, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = cueBase();
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(1, actual.size());
        assertEquals(5.0d, actual.get(0).getStartPosition(), 0.0d);
        // file length (30.0) - start (5.0) = 25.0
        assertEquals(25.0d, actual.get(0).getDuration(), 0.001d);
    }

    @Test
    public void createIndexedTracksSingleFileTwoTracksMapAgainstBaseUnchanged() {
        // Single-file regression anchor: a single-FILE two-track sheet maps both tracks against the
        // one base with the expected start/duration boundaries (single-file is the n=1 case).
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 1, "Track One", 0, 0, 0));
        fileData.getTrackData().add(createTrack(fileData, 2, "Track Two", 0, 12, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = cueBase();
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(2, actual.size());
        assertEquals(0.0d, actual.get(0).getStartPosition(), 0.0d);
        assertEquals(12.0d, actual.get(0).getDuration(), 0.001d);
        assertEquals(12.0d, actual.get(1).getStartPosition(), 0.0d);
        assertEquals(18.0d, actual.get(1).getDuration(), 0.001d);
    }

    @Test
    public void createIndexedTracksMissingIndexDoesNotAbortScan() {
        // Defensive: a track with no INDEX (empty indices) is materialisation-reachable via
        // getIndices().get(0); the block returns empty without propagating an exception, so other
        // blocks and the wider scan continue.
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        // a TrackData with no Index added
        fileData.getTrackData().add(new TrackData(fileData, 1, "AUDIO"));
        cueSheet.getFileData().add(fileData);

        MediaFile base = cueBase();
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertTrue(actual.isEmpty());
    }

    @Test
    public void createIndexedTracksNullBaseDurationSkipsBlockWithoutNpe() {
        // #289: the eager unbox of base.getDuration() aborted the whole FILE block with an NPE when the
        // base file's duration was null (unparsed). The 0.0-sentinel guard removes the NPE; the
        // pre-existing line-1245 guard (lastTrackStart >= wholeFileLength) then fires against the 0.0
        // length and the block is skipped gracefully — empty result, index path cleared — rather than
        // crashing the scan. The tracks reappear on a later scan once the base duration parses.
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 1, "Sole Track", 0, 5, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = cueBase();
        base.setDuration(null);
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = assertDoesNotThrow(
                () -> ReflectionTestUtils.<List<MediaFile>>invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet));

        // block skipped gracefully: no tracks, and the base's index path is cleared (1245 side effect)
        assertTrue(actual.isEmpty());
        assertNull(base.getIndexPath());
    }

    @Test
    public void createIndexedTracksNonNullBaseDurationStillMaterialises() {
        // Sanity counterpart to the null-base case: with a known base duration the line-1245 guard does
        // NOT fire, so the block materialises normally with a non-null computed duration. Proves the
        // 0.0-sentinel guard only degrades the degenerate null case and leaves the happy path intact.
        CueSheet cueSheet = new CueSheet();
        cueSheet.setTitle("Album");
        cueSheet.setPerformer("Album Artist");

        FileData fileData = new FileData(cueSheet, "piano.mp3", "MP3");
        fileData.getTrackData().add(createTrack(fileData, 1, "Sole Track", 0, 5, 0));
        cueSheet.getFileData().add(fileData);

        MediaFile base = cueBase();
        when(mediaFileRepository.findByFolderAndPath(any(), eq("piano.mp3"))).thenReturn(List.of());
        when(mediaFileRepository.findByPathAndFolderAndStartPosition(any(), any(), any())).thenReturn(Optional.empty());
        when(musicFileInfoRepository.findByPath(any())).thenReturn(Optional.empty());

        List<MediaFile> actual = ReflectionTestUtils.invokeMethod(mediaFileService, "createIndexedTracks", base, cueSheet);

        assertEquals(1, actual.size());
        assertNotNull(actual.get(0).getDuration());
        // file length (30.0) - start (5.0) = 25.0
        assertEquals(25.0d, actual.get(0).getDuration(), 0.001d);
    }

    private MediaFile cueBase() {
        MediaFile base = new MediaFile();
        base.setIndexPath("cue/airsonic-test.cue");
        base.setPath("piano.mp3");
        base.setMediaType(MediaType.MUSIC);
        base.setFormat("mp3");
        base.setDuration(30.0);
        base.setFolder(mockedFolder);
        base.setChanged(Instant.now());
        base.setLastScanned(Instant.now());
        base.setCreated(Instant.now());
        return base;
    }

    private TrackData createTrack(FileData fileData, int number, String title, int minutes, int seconds, int frames) {
        TrackData trackData = new TrackData(fileData, number, "AUDIO");
        trackData.setTitle(title);
        trackData.getIndices().add(new Index(1, new Position(minutes, seconds, frames)));
        return trackData;
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

    // ---------------------------------------------------------------------------------------
    // media_file.version write-back (#341): a VERSION bump flags every older row for one
    // re-parse, and the re-parse must stamp the new version onto the row it saves — otherwise
    // the row stays below VERSION forever and every scan is a full re-parse.
    // ---------------------------------------------------------------------------------------

    @Test
    public void updateMediaFileByFile_stampsCurrentVersionOnReparsedRow() {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setId(7);
        mediaFile.setPath("piano.mp3");
        mediaFile.setFolder(mockedFolder);
        // a row written by an older release: stored version below current
        ReflectionTestUtils.setField(mediaFile, "version", MediaFile.VERSION - 1);

        when(mockedFolder.getId()).thenReturn(1);
        when(mockedFolder.getType()).thenReturn(MusicFolder.Type.MEDIA);
        when(mediaFolderService.getMusicFolderForFile(any(), eq(true), eq(true))).thenReturn(Optional.empty());
        when(metaDataParserFactory.getParser(any())).thenReturn(null);
        when(settingsService.getVideoFileTypesSet()).thenReturn(Set.of());
        when(mediaFileRepository.existsById(any())).thenReturn(true);

        mediaFileService.refreshMediaFile(mediaFile);

        ArgumentCaptor<MediaFile> saved = ArgumentCaptor.forClass(MediaFile.class);
        verify(mediaFileRepository).save(saved.capture());
        assertEquals(MediaFile.VERSION, saved.getValue().getVersion());
    }

    @Test
    public void needsUpdate_skipsRowAtCurrentVersionWithUnchangedFile() {
        MediaFile mediaFile = new MediaFile();   // entity default: version == MediaFile.VERSION
        mediaFile.setPath("piano.mp3");
        mediaFile.setFolder(mockedFolder);
        mediaFile.setChanged(FileUtil.lastModified(mediaFile.getFullPath()));

        when(settingsService.getFullScan()).thenReturn(false);

        Boolean result = ReflectionTestUtils.invokeMethod(mediaFileService, "needsUpdate", mediaFile, false);
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    public void needsUpdate_reparsesRowBelowCurrentVersion() {
        MediaFile mediaFile = new MediaFile();
        mediaFile.setPath("piano.mp3");
        mediaFile.setFolder(mockedFolder);
        mediaFile.setChanged(FileUtil.lastModified(mediaFile.getFullPath()));
        ReflectionTestUtils.setField(mediaFile, "version", MediaFile.VERSION - 1);

        // no getFullScan stub: version < VERSION short-circuits the OR before the setting is read

        Boolean result = ReflectionTestUtils.invokeMethod(mediaFileService, "needsUpdate", mediaFile, false);
        assertEquals(Boolean.TRUE, result);
    }
}
