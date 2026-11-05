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

 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.ajax;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.service.LyricsService;
import org.airsonic.player.service.MediaFileService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ConnectTimeoutException;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.jdom2.input.SAXBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.io.IOException;
import java.io.StringReader;
import java.net.SocketException;
import java.security.Principal;

import static org.airsonic.player.util.XMLUtil.createSAXBuilder;

/**
 * Provides services for retrieving song lyrics from chartlyrics.com.
 * <p/>
 * See http://www.chartlyrics.com/api.aspx for details.
 *
 */
@Controller
@MessageMapping("/lyrics")
public class LyricsWSController {

    private static final Logger LOG = LoggerFactory.getLogger(LyricsWSController.class);

    private final LyricsService lyricsService;

    private final SecurityService securityService;

    private final MediaFileService mediaFileService;

    public LyricsWSController(
        LyricsService lyricsService,
        SecurityService securityService,
        MediaFileService mediaFileService) {
        this.lyricsService = lyricsService;
        this.securityService = securityService;
        this.mediaFileService = mediaFileService;
    }


    /**
     * Returns lyrics for the given song and artist.
     *
     * @return The lyrics, never <code>null</code> .
     */
    @MessageMapping("/get")
    @SendToUser(broadcast = false)
    public LyricsStatus getLyrics(Principal user, LyricsGetRequest req) {

        String artist = req.getArtist();
        String song = req.getSong();

        LyricsStatus status = new LyricsStatus();
        try {

            artist = StringUtil.urlEncode(artist);
            song = StringUtil.urlEncode(song);

            String url = "http://api.chartlyrics.com/apiv1.asmx/SearchLyricDirect?artist=" + artist + "&song=" + song;
            String xml = executeGetRequest(url);
            String lyrics = parseSearchResult(xml);

            if (lyrics != null && req.getId() != null) {
                MediaFile mediaFile = mediaFileService.getMediaFile(req.getId());
                if (mediaFile != null && securityService.isFolderAccessAllowed(mediaFile, user.getName())) {
                    boolean persisted = lyricsService.saveLyricsForMediaFile(mediaFile, lyrics, "chartlyrics.com");
                    LOG.info("Persisted lyrics for song '{}' by '{}': {}", song, artist, persisted);
                    status.setPersisted(persisted);
                }
            }
        } catch (HttpResponseException x) {
            LOG.warn("Failed to get lyrics for song '{}'. Request failed: {}", song, x.toString());
            if (x.getStatusCode() == 503) {
                status.setTryLater(true);
            }
        } catch (SocketException | ConnectTimeoutException x) {
            LOG.warn("Failed to get lyrics for song '{}': {}", song, x.toString());
            status.setTryLater(true);
        } catch (Exception x) {
            LOG.warn("Failed to get lyrics for song '" + song + "'.", x);
        }
        return status;
    }



    private String parseSearchResult(String xml) throws Exception {
        SAXBuilder builder = createSAXBuilder();
        Document document = builder.build(new StringReader(xml));

        Element root = document.getRootElement();
        Namespace ns = root.getNamespace();

        String lyric = StringUtils.trimToNull(root.getChildText("Lyric", ns));

        return lyric;
    }

    private String executeGetRequest(String url) throws IOException {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(15000)
                .setSocketTimeout(15000)
                .build();
        HttpGet method = new HttpGet(url);
        method.setConfig(requestConfig);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            ResponseHandler<String> responseHandler = new BasicResponseHandler();
            return client.execute(method, responseHandler);
        }
    }

    public static class LyricsGetRequest {
        private String artist;
        private String song;
        private Integer id;

        public LyricsGetRequest() {
        }

        public LyricsGetRequest(String artist, String song, Integer id) {
            this.artist = artist;
            this.song = song;
            this.id = id;
        }

        public String getArtist() {
            return artist;
        }

        public void setArtist(String artist) {
            this.artist = artist;
        }

        public String getSong() {
            return song;
        }

        public void setSong(String song) {
            this.song = song;
        }

        public Integer getId() {
            return id;
        }

        public void setId(Integer id) {
            this.id = id;
        }
    }
}
