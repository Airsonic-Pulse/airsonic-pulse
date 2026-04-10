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
  Copyright 2017 (C) Airsonic Authors
  Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
*/
package org.airsonic.player.service;

import org.airsonic.player.domain.InternetRadio;
import org.airsonic.player.domain.InternetRadioSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
public class InternetRadioServiceTest {

    String TEST_RADIO_NAME = "Test Radio";
    String TEST_RADIO_HOMEPAGE = "http://example.com";
    String TEST_PLAYLIST_URL_MOVE = "http://example.com/stream_move.m3u";
    String TEST_PLAYLIST_URL_MOVE_LOOP = "http://example.com/stream_infinity_move.m3u";
    String TEST_PLAYLIST_URL_LARGE = "http://example.com/stream_infinity_repeat.m3u";
    String TEST_PLAYLIST_URL_LARGE_2 = "http://example.com/stream_infinity_big.m3u";
    String TEST_PLAYLIST_URL_1 = "http://example.com/stream1.m3u";
    String TEST_PLAYLIST_URL_2 = "http://example.com/stream2.m3u";
    String TEST_STREAM_URL_1 = "http://example.com/stream1";
    String TEST_STREAM_URL_2 = "http://example.com/stream2";
    String TEST_STREAM_URL_3 = "http://example.com/stream3";
    String TEST_STREAM_URL_4 = "http://example.com/stream4";
    String TEST_STREAM_PLAYLIST_CONTENTS_1 = ("http://example.com/stream1\n" +
            "http://example.com/stream2\n");
    String TEST_STREAM_PLAYLIST_CONTENTS_2 = ("#EXTM3U\n" +
            "#EXTINF:123, Sample artist - Sample title\n" +
            "http://example.com/stream3\n" +
            "#EXTINF:321,Example Artist - Example title\n" +
            "http://example.com/stream4\n");

    @Spy
    InternetRadioService internetRadioService;

    @Test
    public void testParseSimplePlaylist() throws Exception {

        // given
        // Prepare the mocked URL connection for the simple playlist
        InternetRadio radio1 = new InternetRadio(TEST_RADIO_NAME, TEST_PLAYLIST_URL_1, TEST_RADIO_HOMEPAGE, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        radio1.setId(1);
        HttpURLConnection mockURLConnection1 = Mockito.mock(HttpURLConnection.class);
        InputStream mockURLInputStream1 = new ByteArrayInputStream(TEST_STREAM_PLAYLIST_CONTENTS_1.getBytes());
        doReturn(mockURLInputStream1).when(mockURLConnection1).getInputStream();
        doReturn(HttpURLConnection.HTTP_OK).when(mockURLConnection1).getResponseCode();
        // Prepare the mock 'connectToURL' method
        doReturn(mockURLConnection1).when(internetRadioService).connectToURL(eq(URI.create(TEST_PLAYLIST_URL_1).toURL()));

        // when
        List<InternetRadioSource> radioSources = internetRadioService.getInternetRadioSources(radio1);

        // then
        assertEquals(2, radioSources.size());
        assertEquals(TEST_STREAM_URL_1, radioSources.get(0).streamUrl());
        assertEquals(TEST_STREAM_URL_2, radioSources.get(1).streamUrl());
    }

    @Test
    public void testRedirects() throws Exception {

        InternetRadio radioMove = new InternetRadio(TEST_RADIO_NAME, TEST_PLAYLIST_URL_MOVE, TEST_RADIO_HOMEPAGE, true,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        radioMove.setId(3);

        // Prepare the mocked URL connection for the redirection to simple playlist
        HttpURLConnection mockURLConnectionMove = Mockito.mock(HttpURLConnection.class);
        doReturn(HttpURLConnection.HTTP_MOVED_PERM).when(mockURLConnectionMove).getResponseCode();
        doReturn(TEST_PLAYLIST_URL_2).when(mockURLConnectionMove).getHeaderField(eq("Location"));
        doReturn(mockURLConnectionMove).when(internetRadioService).connectToURL(eq(URI.create(TEST_PLAYLIST_URL_MOVE).toURL()));

        // Prepare the mocked URL connection for the second simple playlist
        HttpURLConnection mockURLConnection2 = Mockito.mock(HttpURLConnection.class);
        InputStream mockURLInputStream2 = new ByteArrayInputStream(TEST_STREAM_PLAYLIST_CONTENTS_2.getBytes());
        doReturn(mockURLInputStream2).when(mockURLConnection2).getInputStream();
        doReturn(HttpURLConnection.HTTP_OK).when(mockURLConnection2).getResponseCode();
        doReturn(mockURLConnection2).when(internetRadioService).connectToURL(eq(URI.create(TEST_PLAYLIST_URL_2).toURL()));

        // when
        List<InternetRadioSource> radioSources = internetRadioService.getInternetRadioSources(radioMove);

        // given
        assertEquals(2, radioSources.size());
        assertEquals(TEST_STREAM_URL_3, radioSources.get(0).streamUrl());
        assertEquals(TEST_STREAM_URL_4, radioSources.get(1).streamUrl());
    }

    @Test
    public void testLargeInput() throws Exception {

        // given
        InternetRadio radioLarge = new InternetRadio(TEST_RADIO_NAME, TEST_PLAYLIST_URL_LARGE, TEST_RADIO_HOMEPAGE,
                true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        radioLarge.setId(4);
        // Prepare the mocked URL connection for the 'content too large' test
        HttpURLConnection mockURLConnectionLarge = Mockito.mock(HttpURLConnection.class);
        InputStream mockURLInputStreamLarge = new InputStream() {
            private long pos = 0;

            @Override
            public int read() {
                return TEST_STREAM_PLAYLIST_CONTENTS_2.charAt((int) (pos++ % TEST_STREAM_PLAYLIST_CONTENTS_2.length()));
            }
        };
        doReturn(mockURLInputStreamLarge).when(mockURLConnectionLarge).getInputStream();
        doReturn(HttpURLConnection.HTTP_OK).when(mockURLConnectionLarge).getResponseCode();
        doReturn(mockURLConnectionLarge).when(internetRadioService).connectToURL(eq(URI.create(TEST_PLAYLIST_URL_LARGE).toURL()));

        // when
        List<InternetRadioSource> radioSources = internetRadioService.getInternetRadioSources(radioLarge);

        // then
        // A PlaylistTooLarge exception is thrown internally, and the
        // `getInternetRadioSources` method logs it and returns a
        // limited number of sources.
        assertEquals(250, radioSources.size());
    }

    @Test
    public void testLargeInputURL() throws Exception {

        // given
        InternetRadio radioLarge2 = new InternetRadio(TEST_RADIO_NAME, TEST_PLAYLIST_URL_LARGE_2, TEST_RADIO_HOMEPAGE,
                true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        radioLarge2.setId(5);
        // Prepare the mocked URL connection for the 'content too large' test
        // (return a single entry with 'aaaa...' running infinitely long).
        HttpURLConnection mockURLConnectionLarge2 = Mockito.mock(HttpURLConnection.class);
        InputStream mockURLInputStreamLarge2 = new InputStream() {
            @Override
            public int read() {
                return 0x41;
            }
        };
        doReturn(mockURLInputStreamLarge2).when(mockURLConnectionLarge2).getInputStream();
        doReturn(HttpURLConnection.HTTP_OK).when(mockURLConnectionLarge2).getResponseCode();
        doReturn(mockURLConnectionLarge2).when(internetRadioService)
                .connectToURL(eq(URI.create(TEST_PLAYLIST_URL_LARGE_2).toURL()));

        // when
        List<InternetRadioSource> radioSources = internetRadioService.getInternetRadioSources(radioLarge2);

        // then
        // A PlaylistTooLarge exception is thrown internally, and the
        // `getInternetRadioSources` method logs it and returns a
        // limited number of bytes from the input.
        assertEquals(1, radioSources.size());
    }

    @Test
    public void testRedirectLoop() throws Exception {
        // given
        InternetRadio radioMoveLoop = new InternetRadio(TEST_RADIO_NAME, TEST_PLAYLIST_URL_MOVE_LOOP,
                TEST_RADIO_HOMEPAGE, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        radioMoveLoop.setId(3);

        // Prepare the mocked URL connection for the redirection loop
        HttpURLConnection mockURLConnectionMoveLoop = Mockito.mock(HttpURLConnection.class);
        doReturn(HttpURLConnection.HTTP_MOVED_PERM).when(mockURLConnectionMoveLoop).getResponseCode();
        doReturn(TEST_PLAYLIST_URL_MOVE_LOOP).when(mockURLConnectionMoveLoop).getHeaderField(eq("Location"));
        doReturn(mockURLConnectionMoveLoop).when(internetRadioService)
                .connectToURL(eq(URI.create(TEST_PLAYLIST_URL_MOVE_LOOP).toURL()));

        // when
        List<InternetRadioSource> radioSources = internetRadioService.getInternetRadioSources(radioMoveLoop);

        // A PlaylistHasTooManyRedirects exception is thrown internally,
        // and the `getInternetRadioSources` method logs it and returns 0 sources.
        assertEquals(0, radioSources.size());
    }
}
