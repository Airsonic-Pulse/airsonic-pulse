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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for {@link OpusHeaderReader} (#250). The OpusHead identification header is crafted
 * byte-for-byte (RFC 7845 §5.1 packet inside an RFC 3533 Ogg page) so the {@code output_gain}
 * read and the Q7.8 → dB conversion are exercised without a committed binary fixture.
 */
class OpusHeaderReaderTest {

    @TempDir
    private Path tempDir;

    /**
     * Writes a minimal Ogg page whose payload is an OpusHead packet carrying the given
     * {@code output_gain} (Q7.8 int16). Layout matches what {@link OpusHeaderReader} expects:
     * "OggS" page header with a single 19-byte segment, then the OpusHead packet.
     */
    private Path opusFile(int outputGain) throws IOException {
        ByteArrayOutputStream opusHead = new ByteArrayOutputStream();
        opusHead.writeBytes("OpusHead".getBytes());      // magic (8)
        opusHead.write(0x01);                             // version (1)
        opusHead.write(0x02);                             // channel count (1)
        opusHead.write(0x38);                             // pre-skip lo
        opusHead.write(0x01);                             // pre-skip hi (2)
        opusHead.write(0x80);                             // sample rate (4) — 48000 LE
        opusHead.write(0xBB);
        opusHead.write(0x00);
        opusHead.write(0x00);
        opusHead.write(outputGain & 0xFF);                // output_gain int16 LE (2) @ packet offset 16
        opusHead.write((outputGain >> 8) & 0xFF);
        opusHead.write(0x00);                             // channel mapping family (1)
        byte[] packet = opusHead.toByteArray();           // 19 bytes

        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.writeBytes("OggS".getBytes());               // capture pattern (4)
        page.write(0x00);                                 // stream structure version (1)
        page.write(0x02);                                 // header type: beginning of stream (1)
        page.writeBytes(new byte[8]);                     // granule position (8)
        page.writeBytes(new byte[4]);                     // bitstream serial number (4)
        page.writeBytes(new byte[4]);                     // page sequence number (4)
        page.writeBytes(new byte[4]);                     // CRC checksum (4)
        page.write(0x01);                                 // page segment count (1)
        page.write(packet.length);                        // segment table: one segment of 19 bytes
        page.writeBytes(packet);

        Path file = this.tempDir.resolve("test-" + outputGain + ".opus");
        Files.write(file, page.toByteArray());
        return file;
    }

    @Test
    void readsPositiveOutputGainAsQ78Db() throws IOException {
        // 256 Q7.8 = +1.0 dB
        assertEquals(1.0, OpusHeaderReader.readBaseGainDb(opusFile(256)), 0.0001);
    }

    @Test
    void readsNegativeOutputGainAsQ78Db() throws IOException {
        // -256 Q7.8 = -1.0 dB (exercises the signed int16 path)
        assertEquals(-1.0, OpusHeaderReader.readBaseGainDb(opusFile(-256)), 0.0001);
    }

    @Test
    void readsFractionalOutputGain() throws IOException {
        // 128 Q7.8 = +0.5 dB
        assertEquals(0.5, OpusHeaderReader.readBaseGainDb(opusFile(128)), 0.0001);
    }

    @Test
    void readsZeroOutputGainAsZeroNotNull() throws IOException {
        // The common case: most Opus files carry output_gain 0. This is a real 0.0 dB value,
        // distinct from "could not read" (null).
        Double gain = OpusHeaderReader.readBaseGainDb(opusFile(0));
        assertEquals(0.0, gain, 0.0);
    }

    @Test
    void nonOpusContentReturnsNull() throws IOException {
        Path notOpus = this.tempDir.resolve("not.opus");
        Files.write(notOpus, "this is not an ogg opus file at all".getBytes());
        assertNull(OpusHeaderReader.readBaseGainDb(notOpus));
    }

    @Test
    void oggPageWithoutOpusHeadMagicReturnsNull() throws IOException {
        // Valid Ogg capture pattern but the packet is not OpusHead.
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.writeBytes("OggS".getBytes());
        page.write(0x00);
        page.write(0x02);
        page.writeBytes(new byte[8]);
        page.writeBytes(new byte[4]);
        page.writeBytes(new byte[4]);
        page.writeBytes(new byte[4]);
        page.write(0x01);
        page.write(0x08);
        page.writeBytes("Vorbis??".getBytes());
        Path file = this.tempDir.resolve("vorbis.opus");
        Files.write(file, page.toByteArray());
        assertNull(OpusHeaderReader.readBaseGainDb(file));
    }

    @Test
    void truncatedHeaderBeforeOutputGainReturnsNull() throws IOException {
        // OpusHead magic present but the packet is cut off before the output_gain field.
        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.writeBytes("OggS".getBytes());
        page.write(0x00);
        page.write(0x02);
        page.writeBytes(new byte[8]);
        page.writeBytes(new byte[4]);
        page.writeBytes(new byte[4]);
        page.writeBytes(new byte[4]);
        page.write(0x01);
        page.write(0x0A);
        page.writeBytes("OpusHead".getBytes());           // magic only, 10 bytes total — no gain
        page.write(0x01);
        page.write(0x02);
        Path file = this.tempDir.resolve("truncated.opus");
        Files.write(file, page.toByteArray());
        assertNull(OpusHeaderReader.readBaseGainDb(file));
    }

    @Test
    void emptyFileReturnsNull() throws IOException {
        Path file = this.tempDir.resolve("empty.opus");
        Files.write(file, new byte[0]);
        assertNull(OpusHeaderReader.readBaseGainDb(file));
    }

    @Test
    void fileShorterThanPageHeaderReturnsNull() throws IOException {
        // "OggS" present but fewer than 27 bytes — must not index the segment-count byte.
        Path file = this.tempDir.resolve("short.opus");
        Files.write(file, "OggS-too-short".getBytes());
        assertNull(OpusHeaderReader.readBaseGainDb(file));
    }

    @Test
    void multiSegmentPageHeaderShiftsOutputGainOffset() throws IOException {
        // A first page advertising two segments: the OpusHead packet starts at 27 + 2, so the
        // reader's dataStart arithmetic (not a hard-coded offset) must locate output_gain at
        // dataStart + 16. 256 Q7.8 = +1.0 dB.
        ByteArrayOutputStream opusHead = new ByteArrayOutputStream();
        opusHead.writeBytes("OpusHead".getBytes());
        opusHead.write(0x01);
        opusHead.write(0x02);
        opusHead.write(0x38);
        opusHead.write(0x01);
        opusHead.write(0x80);
        opusHead.write(0xBB);
        opusHead.write(0x00);
        opusHead.write(0x00);
        opusHead.write(0x00);                             // output_gain lo (256 LE)
        opusHead.write(0x01);                             // output_gain hi
        opusHead.write(0x00);
        byte[] packet = opusHead.toByteArray();

        ByteArrayOutputStream page = new ByteArrayOutputStream();
        page.writeBytes("OggS".getBytes());
        page.write(0x00);
        page.write(0x02);
        page.writeBytes(new byte[8]);
        page.writeBytes(new byte[4]);
        page.writeBytes(new byte[4]);
        page.writeBytes(new byte[4]);
        page.write(0x02);                                 // two segments
        page.write(packet.length);                        // segment 1 length
        page.write(0x00);                                 // segment 2 length (0)
        page.writeBytes(packet);

        Path file = this.tempDir.resolve("multiseg.opus");
        Files.write(file, page.toByteArray());
        assertEquals(1.0, OpusHeaderReader.readBaseGainDb(file), 0.0001);
    }
}
