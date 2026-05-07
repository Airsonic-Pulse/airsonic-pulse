package org.airsonic.player.security;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@SpringBootTest
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class RESTAuthTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "admin";
    private static final String API_VERSION = "1.16.1";

    @TempDir
    private static Path tempDir;

    @Autowired
    private MockMvc mvc;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Test
    public void testRequestParamAuthSuccess() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("f", "json")
                .param("c", "test")
                .param("u", USERNAME)
                .param("p", PASSWORD)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void testRequestParamAuthFailure() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .param("u", USERNAME)
                .param("p", "incorrectpassword"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.error.code").value(40));
    }

    @Test
    public void testBasicAuthSuccess() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .with(httpBasic(USERNAME, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subsonic-response.status").value("ok"));
    }

    @Test
    public void testBasicAuthFailure() throws Exception {
        mvc.perform(get("/rest/getArtists")
                .param("v", API_VERSION)
                .param("c", "test")
                .param("f", "json")
                .with(httpBasic(USERNAME, "incorrectpassword"))
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
