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
 */
package org.airsonic.player.api;

import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.SavedPlayQueue;
import org.airsonic.player.service.PlayQueueService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.subsonic.restapi.Child;

import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc coverage for the indexBasedQueue extension's read-side fallback contract:
 * when a queue is saved id-based (current_index NULL on the entity), getPlayQueueByIndex
 * must derive currentIndex from indexOf(currentMediaFile) — first-occurrence, the same
 * imprecision id-based saves inherently have. The explicit-currentIndex path is also
 * exercised here to lock the preference of the stored value.
 */
@Transactional
public class IndexBasedQueueApiTest extends AbstractRESTTest {

    private static final String CLIENT_NAME = "indexBasedQueueApiTest";

    @MockitoBean
    private PlayQueueService playQueueService;

    @BeforeEach
    public void setUp() {
        // The controller maps each MediaFile through jaxbContentService.createJaxbChild for
        // the response's <entry> elements. The parent test mocks jaxbContentService, so an
        // unstubbed call returns null and JAXB marshalling of null entries fails. Stub the
        // 3-arg (Player, MediaFile, String) overload explicitly to disambiguate from the
        // 4-arg one, and return a minimal valid Child. lenient() because not every test path
        // necessarily reaches the createJaxbChild call.
        lenient().when(jaxbContentService.createJaxbChild(any(Player.class), any(MediaFile.class), anyString()))
            .thenAnswer(inv -> {
                Child c = new Child();
                c.setId(String.valueOf(inv.getArgument(1, MediaFile.class).getId()));
                c.setIsDir(false);
                c.setTitle("test");
                return c;
            });
    }

    private MediaFile mediaFile(int id) {
        MediaFile mf = new MediaFile();
        mf.setId(id);
        // MediaFile.equals dereferences folder unconditionally; the parent test provides a
        // non-null testFolder so indexOf-walking does not NPE.
        mf.setFolder(testFolder);
        mf.setPath("track-" + id + ".mp3");
        return mf;
    }

    private static SavedPlayQueue savedQueue(List<MediaFile> files, MediaFile current, Integer currentIndex) {
        SavedPlayQueue q = new SavedPlayQueue();
        q.setUsername(AIRSONIC_USER);
        q.setMediaFiles(new ArrayList<>(files));
        q.setCurrentMediaFile(current);
        q.setCurrentIndex(currentIndex);
        q.setPositionMillis(5000L);
        q.setChanged(Instant.parse("2026-05-01T12:00:00Z"));
        q.setChangedBy("legacy-client");
        return q;
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getPlayQueueByIndex", "/rest/getPlayQueueByIndex.view"})
    void getPlayQueueByIndex_fallsBackToIndexOfWhenCurrentIndexIsNull(String endpoint) throws Exception {
        // Queue saved via the LEGACY savePlayQueue path: current_index is null, but
        // currentMediaFile is set. The read must derive currentIndex via indexOf.
        List<MediaFile> files = List.of(mediaFile(10), mediaFile(11), mediaFile(12), mediaFile(13));
        SavedPlayQueue q = savedQueue(files, files.get(2), null);
        when(playQueueService.loadSavedPlayQueueForRest(AIRSONIC_USER)).thenReturn(q);

        mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.playQueueByIndex.currentIndex").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/rest/getPlayQueueByIndex", "/rest/getPlayQueueByIndex.view"})
    void getPlayQueueByIndex_prefersStoredCurrentIndexOverIndexOf(String endpoint) throws Exception {
        // Duplicate-track queue [A, B, A, C] saved via savePlayQueueByIndex with
        // currentIndex=2 (the second A). The stored index MUST be returned even though
        // indexOf(currentMediaFile) would yield 0 (first occurrence).
        MediaFile a = mediaFile(100);
        MediaFile b = mediaFile(200);
        MediaFile c = mediaFile(300);
        List<MediaFile> files = List.of(a, b, a, c);
        SavedPlayQueue q = savedQueue(files, a, 2);
        when(playQueueService.loadSavedPlayQueueForRest(AIRSONIC_USER)).thenReturn(q);

        mvc.perform(get(endpoint)
                .param("v", AIRSONIC_API_VERSION)
                .param("c", CLIENT_NAME)
                .param("u", AIRSONIC_USER)
                .param("p", AIRSONIC_PASSWORD)
                .param("f", EXPECTED_FORMAT))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subsonic-response.status").value("ok"))
            .andExpect(jsonPath("$.subsonic-response.playQueueByIndex.currentIndex").value(2));
    }
}
