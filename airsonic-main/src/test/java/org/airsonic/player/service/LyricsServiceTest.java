package org.airsonic.player.service;

import org.airsonic.player.domain.Lyrics;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.StructuredLyricsLine;
import org.airsonic.player.parser.lyrics.EmbeddedLyricsParser;
import org.airsonic.player.parser.lyrics.LrcParser;
import org.airsonic.player.repository.LyricsRepository;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LyricsServiceTest {

    @Mock
    private LyricsRepository lyricsRepository;
    @Mock
    private MediaFileService mediaFileService;
    @Mock
    private EmbeddedLyricsParser embeddedLyricsParser;
    @InjectMocks
    private LyricsService lyricsService;

    @Mock
    private MediaFile mediaFile;

    @TempDir
    private Path tempDir;

    /** Copies the committed simple.lrc fixture beside a hypothetical {@code <base>.mp3} in the temp dir. */
    private Path copySidecar(String base) throws Exception {
        Path sidecar = tempDir.resolve(base + ".lrc");
        Files.copy(MusicFolderTestData.resolveLyricsFolderPath().resolve("simple.lrc"), sidecar);
        return sidecar;
    }

    private Instant mtime(Path path) throws Exception {
        return Files.getLastModifiedTime(path).toInstant().truncatedTo(ChronoUnit.MICROS);
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnNullForDirectory() {
        when(mediaFile.isDirectory()).thenReturn(true);

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertNull(result);
        verifyNoInteractions(lyricsRepository);
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnNullForIndexedTrack() {
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(true);
        when(mediaFile.getId()).thenReturn(1);
        when(lyricsRepository.findByMediaFileId(eq(1))).thenReturn(Optional.empty());

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        verifyNoMoreInteractions(lyricsRepository);
        assertNull(result);
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnExistingLyrics() throws Exception {
        // A cached "file" row is returned as long as it is still fresh against its sidecar (#281):
        // the stored signal matches the sidecar's current mtime.
        MediaFile mediaFile = mock(MediaFile.class);
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(2);
        when(mediaFile.getFullPath()).thenReturn(MusicFolderTestData.resolveLyricsFolderPath().resolve("simple.mp3"));
        Lyrics lyrics = new Lyrics("existing lyrics", 2, "file");
        lyrics.setSourceLastModified(mtime(MusicFolderTestData.resolveLyricsFolderPath().resolve("simple.lrc")));
        when(lyricsRepository.findByMediaFileId(eq(2))).thenReturn(Optional.of(lyrics));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);
        verifyNoMoreInteractions(lyricsRepository);

        assertEquals(lyrics, result);
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnNullIfNoLrcFileExists() {
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(3);
        Path path = Path.of("lyrics.mp3");

        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(3))).thenReturn(Optional.empty());

        // Simulate Files.exists() by using a spy
        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);
        verifyNoMoreInteractions(lyricsRepository);

        assertNull(result);
    }

    @Test
    void getLyricsFromMediaFile_shouldParseLrcFileAndSaveLyrics() {
        Path path = MusicFolderTestData.resolveLyricsFolderPath().resolve("simple.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(3);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(3))).thenReturn(Optional.empty());
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> {
            Lyrics savedLyrics = invocation.getArgument(0);
            savedLyrics.setId(1); // Simulate ID assignment
            return savedLyrics;
        });

        // Simulate Files.exists() by using a spy
        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertNotNull(result.getLyrics());
        assertEquals(3, result.getMediaFileId());
        assertEquals(1, result.getId());
        assertEquals("file", result.getSource());
        assertNotNull(result.getCreated());
        assertNotNull(result.getUpdated());

    }

    @Test
    void getLyricsFromArtistAndTitle_shouldReturnNullIfArtistOrTitleNull() {
        Lyrics result = lyricsService.getLyricsFromArtistAndTitle(null, "title", null);
        assertNull(result);

        result = lyricsService.getLyricsFromArtistAndTitle("artist", null, null);
        assertNull(result);

        verifyNoInteractions(mediaFileService);
        verifyNoInteractions(lyricsRepository);
    }

    @Test
    void getLyricsFromArtistAndTitle_shouldReturnNullIfMediaFileNotFound() {
        when(mediaFileService.getSongByArtistAndTitle("artist", "title", null)).thenReturn(null);

        Lyrics result = lyricsService.getLyricsFromArtistAndTitle("artist", "title", null);

        assertNull(result);
        verifyNoInteractions(lyricsRepository);

    }

    @ParameterizedTest
    @ValueSource(strings = { "file", "user" })
    void saveLyricsForMediaFile_shouldReturnFalseForDirectory(String source) {
        MediaFile mediaFile = mock(MediaFile.class);
        when(mediaFile.isDirectory()).thenReturn(true);

        boolean result = lyricsService.saveLyricsForMediaFile(mediaFile, "lyrics", source);

        assertFalse(result);
        verifyNoInteractions(lyricsRepository);
    }

    @Test
    void saveLyricsForMediaFile_shouldSaveNewLyrics() {
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(10);
        when(lyricsRepository.findByMediaFileId(eq(10))).thenReturn(Optional.empty());

        boolean result = lyricsService.saveLyricsForMediaFile(mediaFile, "new lyrics", "user");

        assertTrue(result);
        ArgumentCaptor<Lyrics> captor = ArgumentCaptor.forClass(Lyrics.class);
        verify(lyricsRepository).save(captor.capture());
        assertEquals("new lyrics", captor.getValue().getLyrics());
        assertEquals(10, captor.getValue().getMediaFileId());
        assertEquals("user", captor.getValue().getSource());
    }

    @Test
    void saveLyricsForMediaFile_shouldUpdateExistingLyrics() {
        MediaFile mediaFile = mock(MediaFile.class);
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(11);
        Lyrics lyrics = new Lyrics("old lyrics", 11, "file");
        when(lyricsRepository.findByMediaFileId(11)).thenReturn(Optional.of(lyrics));

        boolean result = lyricsService.saveLyricsForMediaFile(mediaFile, "updated lyrics", "user");

        assertTrue(result);
        assertEquals("updated lyrics", lyrics.getLyrics());
        assertEquals(11, lyrics.getMediaFileId());
        assertEquals("user", lyrics.getSource());
        verify(lyricsRepository).save(lyrics);
    }

    // ---------------------------------------------------------------------------------------
    // Embedded lyrics tier (#158): DB cache -> LRC sidecar -> embedded tag -> null.
    // ---------------------------------------------------------------------------------------

    @Test
    void getLyricsFromMediaFile_shouldReturnEmbeddedLyricsWhenNoCacheAndNoSidecar() {
        // A path with no .lrc/.LRC sibling forces the embedded fallback. The embedded result is
        // cached with source "tag" (distinct from the "file" sidecar source).
        Path path = Path.of("no-sidecar-embedded.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(20);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(20))).thenReturn(Optional.empty());
        when(embeddedLyricsParser.getLyrics(path)).thenReturn("embedded lyrics");
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> {
            Lyrics saved = invocation.getArgument(0);
            saved.setId(7);
            return saved;
        });

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertNotNull(result);
        assertEquals("embedded lyrics", result.getLyrics());
        assertEquals(20, result.getMediaFileId());
        assertEquals("tag", result.getSource());
        verify(lyricsRepository).save(any(Lyrics.class));
    }

    @Test
    void getLyricsFromMediaFile_shouldPreferSidecarOverEmbedded() {
        // A real .lrc sidecar exists beside this fixture: the sidecar wins and the embedded reader
        // is never consulted (a sidecar is a deliberate user override of the embedded tag).
        Path path = MusicFolderTestData.resolveLyricsFolderPath().resolve("simple.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(21);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(21))).thenReturn(Optional.empty());
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertNotNull(result);
        assertEquals("file", result.getSource());
        verifyNoInteractions(embeddedLyricsParser);
    }

    @Test
    void getLyricsFromMediaFile_shouldPreferCacheOverEmbedded() {
        // A FRESH DB cache hit short-circuits everything — neither sidecar nor embedded is
        // consulted. A "tag" row is fresh while its signal matches media_file.changed (#281).
        Instant changed = Instant.parse("2026-01-15T12:00:00Z");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(22);
        when(mediaFile.getChanged()).thenReturn(changed);
        Lyrics cached = new Lyrics("cached lyrics", 22, "tag");
        cached.setSourceLastModified(changed);
        when(lyricsRepository.findByMediaFileId(eq(22))).thenReturn(Optional.of(cached));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertEquals(cached, result);
        verifyNoInteractions(embeddedLyricsParser);
        verify(lyricsRepository, never()).save(any(Lyrics.class));
    }

    @Test
    void getLyricsFromMediaFile_shouldReturnNullWhenNoCacheNoSidecarNoEmbedded() {
        Path path = Path.of("no-lyrics-anywhere.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(23);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(23))).thenReturn(Optional.empty());
        when(embeddedLyricsParser.getLyrics(path)).thenReturn(null);

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertNull(result);
        verify(lyricsRepository, never()).save(any(Lyrics.class));
    }

    // ---------------------------------------------------------------------------------------
    // Synced LRC lyrics (#140): a timestamped sidecar preserves per-line times into structured
    // lines + synced=true, while still populating the flat blob for the legacy endpoint.
    // ---------------------------------------------------------------------------------------

    @Test
    void getLyricsFromMediaFile_shouldPreserveTimestampsFromSyncedSidecar() {
        Path path = MusicFolderTestData.resolveLyricsFolderPath().resolve("simple.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(30);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(30))).thenReturn(Optional.empty());
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        // synced flag + structured lines populated from the LRC timestamps.
        assertTrue(result.isSynced());
        assertEquals("file", result.getSource());
        assertFalse(result.getLines().isEmpty());
        // First line: [00:10.37] -> 10370 ms, "Lyrics1 Line 1"; position runs 0..n monotonically.
        StructuredLyricsLine first = result.getLines().get(0);
        assertEquals(0, first.getPosition());
        assertEquals(10370L, first.getStartMs());
        assertEquals("Lyrics1 Line 1", first.getText());
        // The double-timestamped second line ([00:20.09][00:25.31]Lyrics2 Line 2) emits two lines.
        assertEquals(20090L, result.getLines().get(1).getStartMs());
        assertEquals(25310L, result.getLines().get(2).getStartMs());
        assertEquals("Lyrics2 Line 2", result.getLines().get(2).getText());
        // The flat blob is still populated (legacy /getLyrics + unsynced split rely on it).
        assertTrue(result.getLyrics().startsWith("Lyrics1 Line 1\n"));
        // Each structured line back-references its parent and is position-ordered.
        for (int i = 0; i < result.getLines().size(); i++) {
            assertEquals(i, result.getLines().get(i).getPosition());
            assertEquals(result, result.getLines().get(i).getLyrics());
        }
    }

    @Test
    void getLyricsFromMediaFile_embeddedTagIsUnsyncedWithNoStructuredLines() {
        // The embedded tier (#158) has no timestamps: synced stays false and no structured lines.
        Path path = Path.of("no-sidecar-synced.mp3");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(31);
        when(mediaFile.getFullPath()).thenReturn(path);
        when(lyricsRepository.findByMediaFileId(eq(31))).thenReturn(Optional.empty());
        when(embeddedLyricsParser.getLyrics(path)).thenReturn("plain embedded line");
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertFalse(result.isSynced());
        assertTrue(result.getLines().isEmpty());
        assertEquals("tag", result.getSource());
    }

    // ---------------------------------------------------------------------------------------
    // Cache invalidation (#281): a cached row stores the change signal of the source it was
    // built from (sidecar mtime for "file", media_file.changed for "tag") and is re-resolved
    // when the signal no longer matches. "user" and "chartlyrics.com" rows are never compared.
    // ---------------------------------------------------------------------------------------

    @Test
    void getLyricsFromMediaFile_sidecarEditSurfacesNewContentAndSignal() throws Exception {
        Path sidecar = copySidecar("song");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(40);
        when(mediaFile.getFullPath()).thenReturn(tempDir.resolve("song.mp3"));
        when(lyricsRepository.findByMediaFileId(eq(40))).thenReturn(Optional.empty());
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics cached = lyricsService.getLyricsFromMediaFile(mediaFile);
        assertEquals("file", cached.getSource());
        assertEquals(mtime(sidecar), cached.getSourceLastModified());
        assertTrue(cached.getLyrics().startsWith("Lyrics1 Line 1\n"));

        // Edit the sidecar: new content, bumped mtime.
        Files.writeString(sidecar, "[00:01.00]Edited line\n");
        Files.setLastModifiedTime(sidecar, FileTime.from(cached.getSourceLastModified().plusSeconds(5)));
        when(lyricsRepository.findByMediaFileId(eq(40))).thenReturn(Optional.of(cached));

        Lyrics refreshed = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertSame(cached, refreshed);
        assertTrue(refreshed.getLyrics().contains("Edited line"));
        assertEquals(mtime(sidecar), refreshed.getSourceLastModified());
        verify(lyricsRepository, times(2)).save(any(Lyrics.class));
        verifyNoInteractions(embeddedLyricsParser);
    }

    @Test
    void getLyricsFromMediaFile_sidecarUnchangedReturnsCachedWithoutReparse() throws Exception {
        Path sidecar = copySidecar("steady");
        LrcParser parserSpy = spy(new LrcParser());
        ReflectionTestUtils.setField(lyricsService, "lrcParser", parserSpy);
        Lyrics cached = new Lyrics("Lyrics1 Line 1\n", 41, "file");
        cached.setSourceLastModified(mtime(sidecar));
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(41);
        when(mediaFile.getFullPath()).thenReturn(tempDir.resolve("steady.mp3"));
        when(lyricsRepository.findByMediaFileId(eq(41))).thenReturn(Optional.of(cached));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertSame(cached, result);
        verify(parserSpy, never()).parse(any(Path.class));
        verify(lyricsRepository, never()).save(any(Lyrics.class));
        verifyNoInteractions(embeddedLyricsParser);
    }

    @Test
    void getLyricsFromMediaFile_sidecarRemovedFallsThroughToEmbedded() {
        // No .lrc exists for this path any more: the stale "file" row is re-resolved through the
        // normal tiers and lands on the embedded tag.
        Path song = tempDir.resolve("gone.mp3");
        Instant changed = Instant.parse("2026-03-01T08:00:00Z");
        Lyrics cached = new Lyrics("old sidecar text", 42, "file");
        cached.setSourceLastModified(Instant.parse("2026-01-01T00:00:00Z"));
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(42);
        when(mediaFile.getFullPath()).thenReturn(song);
        when(mediaFile.getChanged()).thenReturn(changed);
        when(lyricsRepository.findByMediaFileId(eq(42))).thenReturn(Optional.of(cached));
        when(embeddedLyricsParser.getLyrics(song)).thenReturn("embedded fallback");
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertSame(cached, result);
        assertEquals("tag", result.getSource());
        assertEquals("embedded fallback", result.getLyrics());
        assertEquals(changed, result.getSourceLastModified());
    }

    @Test
    void getLyricsFromMediaFile_sidecarRemovedNoEmbeddedDeletesStaleRow() {
        Path song = tempDir.resolve("vanished.mp3");
        Lyrics cached = new Lyrics("old sidecar text", 43, "file");
        cached.setSourceLastModified(Instant.parse("2026-01-01T00:00:00Z"));
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(43);
        when(mediaFile.getFullPath()).thenReturn(song);
        when(lyricsRepository.findByMediaFileId(eq(43))).thenReturn(Optional.of(cached));
        when(embeddedLyricsParser.getLyrics(song)).thenReturn(null);

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertNull(result);
        verify(lyricsRepository).delete(cached);
        verify(lyricsRepository, never()).save(any(Lyrics.class));
    }

    @Test
    void getLyricsFromMediaFile_retagSurfacesNewEmbeddedLyrics() {
        // media_file.changed moved past the stored signal: the tag is re-read and the row is
        // replaced with the new signal.
        Path song = tempDir.resolve("retag.mp3");
        Instant oldChanged = Instant.parse("2026-01-01T00:00:00Z");
        Instant newChanged = Instant.parse("2026-02-01T00:00:00Z");
        Lyrics cached = new Lyrics("old embedded", 44, "tag");
        cached.setSourceLastModified(oldChanged);
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(44);
        when(mediaFile.getFullPath()).thenReturn(song);
        when(mediaFile.getChanged()).thenReturn(newChanged);
        when(lyricsRepository.findByMediaFileId(eq(44))).thenReturn(Optional.of(cached));
        when(embeddedLyricsParser.getLyrics(song)).thenReturn("new embedded");
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertSame(cached, result);
        assertEquals("new embedded", result.getLyrics());
        assertEquals(newChanged, result.getSourceLastModified());
        verify(lyricsRepository).save(cached);
    }

    @Test
    void getLyricsFromMediaFile_tagUnchangedReturnsCachedWithoutTagRead() {
        Instant changed = Instant.parse("2026-02-01T00:00:00Z");
        Lyrics cached = new Lyrics("embedded text", 45, "tag");
        cached.setSourceLastModified(changed);
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(45);
        when(mediaFile.getChanged()).thenReturn(changed);
        when(lyricsRepository.findByMediaFileId(eq(45))).thenReturn(Optional.of(cached));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertSame(cached, result);
        verifyNoInteractions(embeddedLyricsParser);
        verify(lyricsRepository, never()).save(any(Lyrics.class));
    }

    @ParameterizedTest
    @ValueSource(strings = { "user", "chartlyrics.com" })
    void getLyricsFromMediaFile_manualAndExternalRowsNeverRefreshed(String source) {
        // No comparison ever: neither the sidecar nor media_file.changed is consulted (no
        // getFullPath/getChanged stubs — touching them would fail the strict-stubs harness).
        Lyrics cached = new Lyrics("protected text", 46, source);
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(46);
        when(lyricsRepository.findByMediaFileId(eq(46))).thenReturn(Optional.of(cached));

        Lyrics result = lyricsService.getLyricsFromMediaFile(mediaFile);

        assertSame(cached, result);
        verify(lyricsRepository, never()).save(any(Lyrics.class));
        verify(lyricsRepository, never()).delete(any(Lyrics.class));
        verifyNoInteractions(embeddedLyricsParser);
    }

    @Test
    void getLyricsFromMediaFile_nullSignalFileRowRefreshesOnceAndIsStamped() throws Exception {
        // A pre-#281 "file" row has no signal: it refreshes once, gets stamped, and the second
        // request is a plain fresh hit with no further save.
        Path sidecar = copySidecar("legacy");
        Lyrics cached = new Lyrics("stale pre-281 text", 47, "file");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(47);
        when(mediaFile.getFullPath()).thenReturn(tempDir.resolve("legacy.mp3"));
        when(lyricsRepository.findByMediaFileId(eq(47))).thenReturn(Optional.of(cached));
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics refreshed = lyricsService.getLyricsFromMediaFile(mediaFile);
        assertEquals(mtime(sidecar), refreshed.getSourceLastModified());
        assertTrue(refreshed.getLyrics().startsWith("Lyrics1 Line 1\n"));

        Lyrics second = lyricsService.getLyricsFromMediaFile(mediaFile);
        assertSame(refreshed, second);
        verify(lyricsRepository, times(1)).save(any(Lyrics.class));
    }

    @Test
    void getLyricsFromMediaFile_nullSignalTagRowRefreshesOnceAndIsStamped() {
        Path song = tempDir.resolve("legacytag.mp3");
        Instant changed = Instant.parse("2026-02-01T00:00:00Z");
        Lyrics cached = new Lyrics("stale pre-281 tag text", 48, "tag");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(48);
        when(mediaFile.getFullPath()).thenReturn(song);
        when(mediaFile.getChanged()).thenReturn(changed);
        when(lyricsRepository.findByMediaFileId(eq(48))).thenReturn(Optional.of(cached));
        when(embeddedLyricsParser.getLyrics(song)).thenReturn("fresh tag text");
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics refreshed = lyricsService.getLyricsFromMediaFile(mediaFile);
        assertEquals("fresh tag text", refreshed.getLyrics());
        assertEquals(changed, refreshed.getSourceLastModified());

        Lyrics second = lyricsService.getLyricsFromMediaFile(mediaFile);
        assertSame(refreshed, second);
        verify(embeddedLyricsParser, times(1)).getLyrics(song);
        verify(lyricsRepository, times(1)).save(any(Lyrics.class));
    }

    @Test
    void saveLyricsForMediaFile_clearsStructuredLinesAndRefreshesUpdated() throws Exception {
        // The row-replace helper closes the old wart: repurposing a synced sidecar row as a
        // manual edit clears the #140 structured lines, refreshes updated, and stores a null
        // signal (manual rows are exempt from comparison).
        copySidecar("manual");
        when(mediaFile.isDirectory()).thenReturn(false);
        when(mediaFile.isIndexedTrack()).thenReturn(false);
        when(mediaFile.getId()).thenReturn(49);
        when(mediaFile.getFullPath()).thenReturn(tempDir.resolve("manual.mp3"));
        when(lyricsRepository.findByMediaFileId(eq(49))).thenReturn(Optional.empty());
        when(lyricsRepository.save(any(Lyrics.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Lyrics row = lyricsService.getLyricsFromMediaFile(mediaFile);
        assertTrue(row.isSynced());
        assertTrue(row.getLines().size() > 0);
        Instant updatedBefore = row.getUpdated();
        when(lyricsRepository.findByMediaFileId(eq(49))).thenReturn(Optional.of(row));

        Thread.sleep(2); // ensure the micros-truncated updated stamp can move
        boolean saved = lyricsService.saveLyricsForMediaFile(mediaFile, "manual text", "user");

        assertTrue(saved);
        assertEquals(0, row.getLines().size());
        assertEquals("manual text", row.getLyrics());
        assertEquals("user", row.getSource());
        assertFalse(row.isSynced());
        assertNull(row.getSourceLastModified());
        assertTrue(row.getUpdated().isAfter(updatedBefore));
    }
}
