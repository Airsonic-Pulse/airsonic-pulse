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

 Copyright 2026 (C) Airsonic Authors
 */
package org.airsonic.player.service.metadata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads the {@code output_gain} base-gain field from an Opus file's {@code OpusHead} identification
 * header (RFC 7845 §5.1). jaudiotagger 3.0.1 has no Opus reader and {@code ffprobe} does not surface
 * {@code output_gain} in any tag/stream field, so the value is read directly from the container.
 * <p>
 * The {@code OpusHead} packet is the payload of the first Ogg page. Page layout (RFC 3533 §6):
 * {@code "OggS"} capture pattern (4) + version (1) + header type (1) + granule position (8) +
 * serial number (4) + page sequence (4) + CRC (4) + page-segment count (1) + segment table (N) +
 * page data. The {@code OpusHead} packet then follows: magic {@code "OpusHead"} (8) + version (1) +
 * channel count (1) + pre-skip u16 (2) + input sample rate u32 (4) + {@code output_gain} int16 LE
 * (2, Q7.8 dB) at offset 16 within the packet + mapping family (1) ...
 * <p>
 * All failure modes — not an Ogg page, no {@code OpusHead} magic, a truncated header — return
 * {@code null} rather than throwing, matching the defensive style of the surrounding parsers.
 */
final class OpusHeaderReader {

    private static final Logger LOG = LoggerFactory.getLogger(OpusHeaderReader.class);

    private static final byte[] OGG_MAGIC = {'O', 'g', 'g', 'S'};
    private static final byte[] OPUS_HEAD_MAGIC = {'O', 'p', 'u', 's', 'H', 'e', 'a', 'd'};
    // The OpusHead packet always fits well within the first Ogg page; this bounds the read.
    private static final int MAX_PREFIX = 512;
    // Fixed page-header length up to the segment count byte (RFC 3533 §6).
    private static final int OGG_SEGMENT_COUNT_OFFSET = 26;
    // output_gain offset within the OpusHead packet (RFC 7845 §5.1).
    private static final int OUTPUT_GAIN_OFFSET = 16;

    private OpusHeaderReader() {
    }

    /**
     * Reads the OpusHead {@code output_gain} for the given file and converts it from Q7.8
     * fixed-point to dB ({@code output_gain / 256.0}). Returns {@code null} for any non-Opus,
     * malformed, or truncated input — never throws.
     */
    static Double readBaseGainDb(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = in.readNBytes(MAX_PREFIX);

            // Ogg page capture pattern.
            if (buf.length < OGG_SEGMENT_COUNT_OFFSET + 1 || !startsWith(buf, 0, OGG_MAGIC)) {
                return null;
            }
            int segments = buf[OGG_SEGMENT_COUNT_OFFSET] & 0xFF;
            int dataStart = OGG_SEGMENT_COUNT_OFFSET + 1 + segments;

            // OpusHead packet magic, followed by enough bytes to hold output_gain.
            if (dataStart + OPUS_HEAD_MAGIC.length > buf.length
                    || !startsWith(buf, dataStart, OPUS_HEAD_MAGIC)
                    || dataStart + OUTPUT_GAIN_OFFSET + 2 > buf.length) {
                return null;
            }

            int lo = buf[dataStart + OUTPUT_GAIN_OFFSET] & 0xFF;
            int hi = buf[dataStart + OUTPUT_GAIN_OFFSET + 1];      // signed: int16 is signed
            short outputGain = (short) ((hi << 8) | lo);
            return outputGain / 256.0;
        } catch (IOException x) {
            LOG.warn("Could not read OpusHead from {}", file, x);
            return null;
        }
    }

    private static boolean startsWith(byte[] buf, int offset, byte[] magic) {
        for (int i = 0; i < magic.length; i++) {
            if (buf[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
