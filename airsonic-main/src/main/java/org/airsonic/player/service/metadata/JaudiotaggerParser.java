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
package org.airsonic.player.service.metadata;

import com.google.common.collect.ImmutableSet;
import org.airsonic.player.domain.Contributor;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.MediaFolderService;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang.StringUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.TagField;
import org.jaudiotagger.tag.datatype.Pair;
import org.jaudiotagger.tag.id3.AbstractID3v2Frame;
import org.jaudiotagger.tag.id3.AbstractID3v2Tag;
import org.jaudiotagger.tag.id3.framebody.FrameBodyIPLS;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTMCL;
import org.jaudiotagger.tag.id3.framebody.FrameBodyTXXX;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.mp4.Mp4Tag;
import org.jaudiotagger.tag.mp4.field.Mp4TagReverseDnsField;
import org.jaudiotagger.tag.reference.PictureTypes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.LogManager;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Parses meta data from audio files using the Jaudiotagger library
 * (http://www.jthink.net/jaudiotagger/)
 *
 * @author Sindre Mehus
 */
@Service
@Order(0)
public class JaudiotaggerParser extends MetaDataParser {

    private static final Logger LOG = LoggerFactory.getLogger(JaudiotaggerParser.class);

    // ReplayGain + Opus R128 tag-name constants live on {@link MetaDataParser} so both
    // JaudiotaggerParser and FFmpegParser reference one source of truth.

    // MP4 stores ReplayGain in iTunes-style reverse-DNS freeform atoms keyed by lowercase
    // descriptor (replaygain_track_gain, replaygain_album_gain, replaygain_track_peak,
    // replaygain_album_peak). jaudiotagger 3.0.1 exposes them through Mp4Tag.getFields()
    // with the prefix below; no Mp4FieldKey enum value covers them.
    static final String MP4_ITUNES_FREEFORM_PREFIX = "----:com.apple.iTunes:";

    // MP4 per-instrument performer credits (Picard convention) live in per-instrument freeform
    // atoms whose Mp4TagReverseDnsField descriptor takes this prefix — e.g. atom id
    // "----:com.apple.iTunes:PERFORMER:Guitar" has descriptor "PERFORMER:Guitar" and the
    // instrument is the suffix after the colon. The standard ----:com.apple.iTunes:Performer
    // atom is read separately via FieldKey.PERFORMER in the Vorbis-style branch.
    static final String MP4_PERFORMER_DESCRIPTOR_PREFIX = "PERFORMER:";

    // Clean-FieldKey contributor roles — each FieldKey is read via tag.getAll(...) and every
    // returned value becomes one Contributor with the given role label and no subRole. Performer
    // credits with an instrument as subRole are handled separately by addPerformers() below.
    private static final List<Map.Entry<FieldKey, String>> CONTRIBUTOR_ROLES = List.of(
            Map.entry(FieldKey.COMPOSER, "composer"),
            Map.entry(FieldKey.LYRICIST, "lyricist"),
            Map.entry(FieldKey.CONDUCTOR, "conductor"),
            Map.entry(FieldKey.ARRANGER, "arranger"),
            Map.entry(FieldKey.PRODUCER, "producer"),
            Map.entry(FieldKey.ENGINEER, "engineer"),
            Map.entry(FieldKey.MIXER, "mixer"),
            Map.entry(FieldKey.REMIXER, "remixer"),
            Map.entry(FieldKey.DJMIXER, "djmixer"),
            Map.entry(FieldKey.ORCHESTRA, "orchestra"),
            Map.entry(FieldKey.CHOIR, "choir"),
            Map.entry(FieldKey.ENSEMBLE, "ensemble"));

    // Function-role keys recognised inside an ID3v2.3 IPLS frame, derived from the canonical
    // CONTRIBUTOR_ROLES labels so the two stay in lockstep. Keyed by the lowercased label for
    // case-insensitive lookup; the value is the canonical label emitted as the Contributor role.
    // IPLS pairs whose key is NOT in this set are treated as instrument credits (performer with
    // the key as subRole) — see addId3IplsContributors().
    private static final Map<String, String> IPLS_FUNCTION_KEYS = CONTRIBUTOR_ROLES.stream()
            .collect(Collectors.toUnmodifiableMap(
                    e -> e.getValue().toLowerCase(Locale.ROOT),
                    Map.Entry::getValue));

    // Vorbis PERFORMER convention: "Name (Instrument)" — capture the bare name and a single
    // trailing parenthetical as instrument. No parens → whole string is the name.
    private static final Pattern VORBIS_PERFORMER_PATTERN = Pattern.compile("^(.*?)\\s*\\(([^()]+)\\)\\s*$");

    @Autowired
    private MediaFolderService mediaFolderService;

    public JaudiotaggerParser(MediaFolderService mediaFolderService) {
        this.mediaFolderService = mediaFolderService;
    }

    static {
        try {
            LogManager.getLogManager().reset();
        } catch (Throwable x) {
            LOG.warn("Failed to turn off logging from Jaudiotagger.", x);
        }
    }

    /**
     * Parses meta data for the given music file. No guessing or reformatting is done.
     *
     *
     * @param file The music file to parse.
     * @return Meta data for the file.
     */
    @Override
    public MetaData getRawMetaData(Path file) {

        MetaData metaData = new MetaData();

        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTag();
            if (tag != null) {
                metaData.setAlbumName(getTagField(tag, FieldKey.ALBUM));
                metaData.setAlbumSortName(getTagField(tag, FieldKey.ALBUM_SORT));
                metaData.setTitle(getTagField(tag, FieldKey.TITLE));
                metaData.setSortName(getTagField(tag, FieldKey.TITLE_SORT));
                metaData.setYear(parseIntegerPattern(getTagField(tag, FieldKey.YEAR), YEAR_NUMBER_PATTERN));
                // releaseDate keeps the raw YEAR tag value (may be YYYY / YYYY-MM / YYYY-MM-DD)
                // so the response can surface month/day when the tag carries them; the existing
                // integer year above is unchanged.
                metaData.setReleaseDate(getTagField(tag, FieldKey.YEAR));
                // originalReleaseDate: prefer the full ORIGINALRELEASEDATE tag, fall back to the
                // year-only ORIGINAL_YEAR. parseItemDate at response time handles either form.
                String originalRelease = getTagField(tag, FieldKey.ORIGINALRELEASEDATE);
                metaData.setOriginalReleaseDate(originalRelease != null ? originalRelease : getTagField(tag, FieldKey.ORIGINAL_YEAR));
                metaData.setCompilation(parseCompilation(getTagField(tag, FieldKey.IS_COMPILATION)));
                metaData.setReleaseTypes(getAllTagFields(tag, FieldKey.MUSICBRAINZ_RELEASE_TYPE));
                metaData.setRecordLabels(getAllTagFields(tag, FieldKey.RECORD_LABEL));
                metaData.setContributors(getContributors(tag));
                metaData.setBpm(parseBpm(getTagField(tag, FieldKey.BPM)));
                metaData.setGenre(mapGenre(getTagField(tag, FieldKey.GENRE)));
                metaData.setGenres(getAllTagFields(tag, FieldKey.GENRE));
                metaData.setDiscNumber(parseIntegerPattern(getTagField(tag, FieldKey.DISC_NO), null));
                metaData.setDiscSubtitle(getTagField(tag, FieldKey.DISC_SUBTITLE));
                metaData.setTrackNumber(parseIntegerPattern(getTagField(tag, FieldKey.TRACK), TRACK_NUMBER_PATTERN));
                metaData.setMusicBrainzReleaseId(getTagField(tag, FieldKey.MUSICBRAINZ_RELEASEID));
                metaData.setMusicBrainzRecordingId(getTagField(tag, FieldKey.MUSICBRAINZ_TRACK_ID));
                // The ID3 artist is grouped by album-artist, so source its sort name and MB id
                // from the release-artist tags rather than the per-track artist tags.
                metaData.setMusicBrainzArtistId(getTagField(tag, FieldKey.MUSICBRAINZ_RELEASEARTISTID));
                metaData.setArtistSortName(getTagField(tag, FieldKey.ALBUM_ARTIST_SORT));
                metaData.setReplayGainTrackGain(parseTrackGain(tag));
                metaData.setReplayGainAlbumGain(parseAlbumGain(tag));
                metaData.setReplayGainTrackPeak(parseReplayGain(getReplayGainField(tag, RG_TRACK_PEAK)));
                metaData.setReplayGainAlbumPeak(parseReplayGain(getReplayGainField(tag, RG_ALBUM_PEAK)));

                metaData.setArtist(getTagField(tag, FieldKey.ARTIST));
                metaData.setAlbumArtist(getTagField(tag, FieldKey.ALBUM_ARTIST));

                if (StringUtils.isBlank(metaData.getArtist())) {
                    metaData.setArtist(metaData.getAlbumArtist());
                }
                if (StringUtils.isBlank(metaData.getAlbumArtist())) {
                    metaData.setAlbumArtist(metaData.getArtist());
                }

            }

            AudioHeader audioHeader = audioFile.getAudioHeader();
            if (audioHeader != null) {
                metaData.setVariableBitRate(audioHeader.isVariableBitRate());
                metaData.setBitRate((int) audioHeader.getBitRateAsNumber());
                metaData.setDuration(audioHeader.getPreciseTrackLength());
            }


        } catch (Throwable x) {
            LOG.warn("Error when parsing tags in {}", file, x);
        }

        return metaData;
    }

    private static String getTagField(Tag tag, FieldKey fieldKey) {
        try {
            return StringUtils.replace(StringUtils.trimToNull(tag.getFirst(fieldKey)), "\0", " ");
        } catch (Exception x) {
            // Ignored.
            return null;
        }
    }

    static List<String> getAllTagFields(Tag tag, FieldKey fieldKey) {
        try {
            List<String> values = tag.getAll(fieldKey);
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return values.stream()
                    .map(v -> StringUtils.replace(StringUtils.trimToNull(v), "\0", " "))
                    .filter(v -> v != null)
                    .toList();
        } catch (Exception x) {
            // Ignored.
            return List.of();
        }
    }

    /**
     * Builds the per-track contributor list — clean-FieldKey credits (composer, lyricist, etc.)
     * plus performer credits that carry an optional instrument as the subRole. Clean roles come
     * from {@link #CONTRIBUTOR_ROLES} via {@link #getAllTagFields}. Performer extraction is
     * format-dispatched: ID3v2.4 reads the dedicated TMCL musician-credits frame for
     * (instrument, performer) pairs; ID3v2.3 reads the combined IPLS frame for both instrument
     * and function credits (jaudiotagger surfaces none of IPLS through the clean FieldKeys above,
     * so both categories are recovered there — see {@link #addId3IplsContributors}); MP4 reads
     * per-instrument iTunes freeform atoms (the {@code ----:com.apple.iTunes:PERFORMER:<instrument>}
     * Picard convention) alongside the standard {@code Performer} atom; everything else (Vorbis on
     * FLAC/Ogg/Opus) reads {@code FieldKey.PERFORMER} and parses the common
     * {@code "Name (Instrument)"} convention.
     */
    static List<Contributor> getContributors(Tag tag) {
        List<Contributor> result = new ArrayList<>();
        for (Map.Entry<FieldKey, String> entry : CONTRIBUTOR_ROLES) {
            for (String name : getAllTagFields(tag, entry.getKey())) {
                result.add(new Contributor(entry.getValue(), null, name));
            }
        }
        addPerformers(result, tag);
        return result;
    }

    /**
     * Appends performer Contributors with the instrument carried as {@code subRole}. ID3v2.4 keeps
     * (instrument, performer) pairs in the dedicated TMCL frame; reading them through the
     * generic {@code FieldKey.PERFORMER} accessor flattens the pairs and loses the instrument,
     * so frame-level access is required. MP4 also stores per-instrument credits out-of-band of
     * the standard FieldKey, via the {@code ----:com.apple.iTunes:PERFORMER:<instrument>}
     * freeform-atom convention — {@link #addMp4FreeformPerformers} reads those before falling
     * through to the generic {@code FieldKey.PERFORMER} loop (which on MP4 also reads the
     * standard {@code ----:com.apple.iTunes:Performer} atom). Non-ID3 formats expose performer
     * values directly under {@code FieldKey.PERFORMER}, with the {@code "Name (Instrument)"}
     * convention parsed below. Wraps any jaudiotagger throwable so a malformed frame can't break
     * the scan for one file.
     */
    private static void addPerformers(List<Contributor> sink, Tag tag) {
        try {
            if (tag instanceof AbstractID3v2Tag id3v2) {
                // ID3v2.4 musician credits: TMCL pairs (instrument -> performer).
                List<TagField> frames = id3v2.getFrame("TMCL");
                if (frames != null) {
                    for (TagField field : frames) {
                        if (!(field instanceof AbstractID3v2Frame frame)
                                || !(frame.getBody() instanceof FrameBodyTMCL tmcl)) {
                            continue;
                        }
                        for (Pair pair : tmcl.getPairing().getMapping()) {
                            String instrument = StringUtils.trimToNull(pair.getKey());
                            String rawNames = pair.getValue();
                            if (rawNames == null) {
                                continue;
                            }
                            // ID3v2.4 spec allows a comma-delimited list of performers per instrument.
                            for (String name : rawNames.split(",")) {
                                String cleaned = StringUtils.trimToNull(name);
                                if (cleaned != null) {
                                    sink.add(new Contributor("performer", instrument, cleaned));
                                }
                            }
                        }
                    }
                }
                // ID3v2.3 combined credits: IPLS pairs (instrument-or-function -> name). No-op on
                // tags without an IPLS frame (e.g. v2.4 files, which use TMCL above + TIPL-backed
                // FieldKeys read in getContributors).
                addId3IplsContributors(sink, id3v2);
                return;
            }
            if (tag instanceof Mp4Tag mp4) {
                addMp4FreeformPerformers(sink, mp4);
                // Fall through to the generic FieldKey.PERFORMER loop below so the standard
                // ----:com.apple.iTunes:Performer atom (Vorbis-style values) is still read.
            }
            for (String raw : getAllTagFields(tag, FieldKey.PERFORMER)) {
                Matcher m = VORBIS_PERFORMER_PATTERN.matcher(raw);
                if (m.matches()) {
                    String name = StringUtils.trimToNull(m.group(1));
                    String instrument = StringUtils.trimToNull(m.group(2));
                    if (name != null) {
                        sink.add(new Contributor("performer", instrument, name));
                    }
                } else {
                    sink.add(new Contributor("performer", null, raw));
                }
            }
        } catch (Exception x) {
            // Ignored.
        }
    }

    /**
     * Reads MP4 per-instrument performer credits from iTunes freeform atoms — the Picard
     * convention where each instrument is its own atom keyed by
     * {@code ----:com.apple.iTunes:PERFORMER:<instrument>}. The descriptor (the suffix after the
     * issuer prefix) carries the instrument name; the atom content carries the performer name(s),
     * comma-delimited for multiple performers on the same instrument (mirrors the ID3v2.4 TMCL
     * semantics in {@link #addPerformers}).
     */
    private static void addMp4FreeformPerformers(List<Contributor> sink, Mp4Tag tag) {
        Iterator<TagField> it = tag.getFields();
        while (it.hasNext()) {
            TagField field = it.next();
            if (!(field instanceof Mp4TagReverseDnsField rdns)) {
                continue;
            }
            String descriptor = rdns.getDescriptor();
            if (descriptor == null || !descriptor.startsWith(MP4_PERFORMER_DESCRIPTOR_PREFIX)) {
                continue;
            }
            String instrument = StringUtils.trimToNull(descriptor.substring(MP4_PERFORMER_DESCRIPTOR_PREFIX.length()));
            String content = rdns.getContent();
            if (instrument == null || content == null) {
                continue;
            }
            for (String name : content.split(",")) {
                String cleaned = StringUtils.trimToNull(name);
                if (cleaned != null) {
                    sink.add(new Contributor("performer", instrument, cleaned));
                }
            }
        }
    }

    /**
     * Reads ID3v2.3 contributor credits from the IPLS (Involved People List) frame. ID3v2.3 has
     * no TMCL/TIPL split — IPLS combines musician credits (instrument -> performer) and involved
     * people (function -> name) in one paired list with no spec-mandated key vocabulary, and
     * jaudiotagger does NOT surface any of it through the FieldKey accessors that
     * {@link #getContributors} relies on for the v2.4 TIPL-backed roles. So both categories are
     * extracted here by classifying each pair's key: a key matching a canonical function label in
     * {@link #IPLS_FUNCTION_KEYS} (case-insensitively) becomes a Contributor with that role and no
     * subRole; any other key is treated as an instrument and becomes a {@code performer} credit
     * carrying the original key (verbatim case) as the subRole. The value may be a comma-delimited
     * list of names, mirroring the TMCL handling. No-op when the tag has no IPLS frame.
     */
    private static void addId3IplsContributors(List<Contributor> sink, AbstractID3v2Tag tag) {
        List<TagField> frames = tag.getFrame("IPLS");
        if (frames == null) {
            return;
        }
        for (TagField field : frames) {
            if (!(field instanceof AbstractID3v2Frame frame)
                    || !(frame.getBody() instanceof FrameBodyIPLS ipls)) {
                continue;
            }
            for (Pair pair : ipls.getPairing().getMapping()) {
                String rawKey = StringUtils.trimToNull(pair.getKey());
                String rawNames = pair.getValue();
                if (rawKey == null || rawNames == null) {
                    continue;
                }
                String functionRole = IPLS_FUNCTION_KEYS.get(rawKey.toLowerCase(Locale.ROOT));
                String role = functionRole != null ? functionRole : "performer";
                String subRole = functionRole != null ? null : rawKey;
                for (String name : rawNames.split(",")) {
                    String cleaned = StringUtils.trimToNull(name);
                    if (cleaned != null) {
                        sink.add(new Contributor(role, subRole, cleaned));
                    }
                }
            }
        }
    }

    /**
     * Reads a ReplayGain or R128 value by name. ReplayGain has no jaudiotagger
     * {@link FieldKey}, so it is read directly: from ID3v2 it lives in a TXXX frame keyed by a
     * (case-insensitive) description; Vorbis comments (FLAC/Ogg/Opus) expose it as a plain
     * keyed field; MP4 stores it as an iTunes-style reverse-DNS freeform atom keyed by the
     * (conventionally lowercase) descriptor under {@link #MP4_ITUNES_FREEFORM_PREFIX}, matched
     * case-insensitively here so a tagger that capitalizes the descriptor still resolves.
     */
    static String getReplayGainField(Tag tag, String name) {
        try {
            if (tag instanceof AbstractID3v2Tag id3v2) {
                List<TagField> frames = id3v2.getFrame("TXXX");
                if (frames != null) {
                    for (TagField field : frames) {
                        if (field instanceof AbstractID3v2Frame frame
                                && frame.getBody() instanceof FrameBodyTXXX txxx
                                && name.equalsIgnoreCase(txxx.getDescription())) {
                            return StringUtils.trimToNull(txxx.getText());
                        }
                    }
                }
                return null;
            }
            if (tag instanceof Mp4Tag mp4) {
                String atomId = MP4_ITUNES_FREEFORM_PREFIX + name;
                Iterator<TagField> it = mp4.getFields();
                while (it.hasNext()) {
                    TagField field = it.next();
                    if (field instanceof Mp4TagReverseDnsField rdns
                            && atomId.equalsIgnoreCase(rdns.getId())) {
                        return StringUtils.trimToNull(rdns.getContent());
                    }
                }
                return null;
            }
            return StringUtils.trimToNull(tag.getFirst(name));
        } catch (Exception x) {
            // Ignored.
            return null;
        }
    }

    /**
     * Returns the track gain in ReplayGain-equivalent dB, preferring the ReplayGain tag when
     * present and falling back to the Opus R128 tag otherwise. A present-but-unparseable RG
     * tag returns {@code null} and does NOT fall through to R128 — the operator's authored
     * tag takes precedence over any inferred R128 value. See {@link #parseR128GainQ78} for
     * the R128↔RG conversion.
     */
    Double parseTrackGain(Tag tag) {
        String rg = getReplayGainField(tag, RG_TRACK_GAIN);
        if (rg != null) {
            return parseReplayGain(rg);
        }
        return parseR128GainQ78(getReplayGainField(tag, R128_TRACK_GAIN));
    }

    /**
     * Returns the album gain in ReplayGain-equivalent dB, preferring the ReplayGain tag when
     * present and falling back to the Opus R128 tag otherwise. Same precedence rule as
     * {@link #parseTrackGain}.
     */
    Double parseAlbumGain(Tag tag) {
        String rg = getReplayGainField(tag, RG_ALBUM_GAIN);
        if (rg != null) {
            return parseReplayGain(rg);
        }
        return parseR128GainQ78(getReplayGainField(tag, R128_ALBUM_GAIN));
    }

    /**
     * Updates the given file with the given meta data.
     *
     * @param file     The music file to update.
     * @param metaData The new meta data.
     */
    @Override
    public void setMetaData(MediaFile file, MetaData metaData) {

        try {
            AudioFile audioFile = AudioFileIO.read(file.getFullPath().toFile());
            Tag tag = audioFile.getTagOrCreateAndSetDefault();

            tag.setField(FieldKey.ARTIST, StringUtils.trimToEmpty(metaData.getArtist()));
            tag.setField(FieldKey.ALBUM, StringUtils.trimToEmpty(metaData.getAlbumName()));
            tag.setField(FieldKey.TITLE, StringUtils.trimToEmpty(metaData.getTitle()));
            tag.setField(FieldKey.GENRE, StringUtils.trimToEmpty(metaData.getGenre()));
            try {
                tag.setField(FieldKey.ALBUM_ARTIST, StringUtils.trimToEmpty(metaData.getAlbumArtist()));
            } catch (Exception x) {
                // Silently ignored. ID3v1 doesn't support album artist.
            }

            Integer track = metaData.getTrackNumber();
            if (track == null) {
                tag.deleteField(FieldKey.TRACK);
            } else {
                tag.setField(FieldKey.TRACK, String.valueOf(track));
            }

            Integer year = metaData.getYear();
            if (year == null) {
                tag.deleteField(FieldKey.YEAR);
            } else {
                tag.setField(FieldKey.YEAR, String.valueOf(year));
            }

            audioFile.commit();

        } catch (Throwable x) {
            LOG.warn("Failed to update tags for file {}", file, x);
            throw new RuntimeException("Failed to update tags for file " + file + ". " + x.getMessage(), x);
        }
    }

    /**
     * Returns whether this parser supports tag editing (using the {@link #setMetaData} method).
     *
     * @return Always true.
     */
    @Override
    public boolean isEditingSupported() {
        return true;
    }

    private static Set<String> imageAvailableFormats = ImmutableSet.of("mp3", "m4a", "m4b", "m4p", "aac", "ogg", "flac", "wav", "aif", "dsf", "aiff", "wma");
    // "opus" intentionally NOT included — jaudiotagger 3.0.1 has no Opus reader
    // (no OPUS entry in SupportedFileFormat; no reader registered for .opus).
    // Opus R128 support is wired through FFmpegParser instead — see #258 and #226 PR1.
    private static Set<String> applicableFormats = ImmutableSet.of("mp3", "m4a", "m4b", "m4p", "aac", "ogg", "flac", "wav", "aif", "dsf", "aiff", "wma");

    /**
     * Returns whether this parser is applicable to the given file.
     *
     * @param path The path to music file in question.
     * @return Whether this parser is applicable to the given file.
     */
    @Override
    public boolean isApplicable(Path path) {
        return Files.isRegularFile(path) && applicableFormats.contains(FilenameUtils.getExtension(path.toString()).toLowerCase());
    }

    /**
     * Returns whether cover art image data is available in the given file.
     *
     * @param file The music file.
     * @return Whether cover art image data is available.
     */
    public static boolean isImageAvailable(Path file) {
        try {
            return Files.isRegularFile(file)
                    && imageAvailableFormats.contains(FilenameUtils.getExtension(file.toString()).toLowerCase())
                    && getArtwork(file) != null;
        } catch (Throwable x) {
            LOG.info("Failed to find cover art tag in {}", file, x);
            return false;
        }
    }

    public static Artwork getArtwork(Path file) throws Exception {
        AudioFile audioFile = AudioFileIO.read(file.toFile());
        Tag tag = audioFile.getTag();
        Artwork artwork = null;
        if (tag != null) {
            Optional<Artwork> artworkOptional = tag.getArtworkList().stream().filter(art -> art.getPictureType() == PictureTypes.DEFAULT_ID).findAny();
            artwork = artworkOptional.orElse(tag.getFirstArtwork());
        }
        return artwork;
    }

    @Override
    MediaFolderService getMediaFolderService() {
        return mediaFolderService;
    }
}
