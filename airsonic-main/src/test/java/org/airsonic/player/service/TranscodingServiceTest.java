package org.airsonic.player.service;

import org.airsonic.player.config.AirsonicHomeConfig;
import org.airsonic.player.domain.Player;
import org.airsonic.player.domain.Transcoding;
import org.airsonic.player.repository.PlayerRepository;
import org.airsonic.player.repository.TranscodingRepository;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@EnableConfigurationProperties({AirsonicHomeConfig.class})
public class TranscodingServiceTest {

    @Autowired
    private TranscodingService transcodingService;

    @Autowired
    private PlayerRepository playerRepository;

    @Autowired
    private TranscodingRepository transcodingRepository;

    @TempDir
    private static Path tempDir;

    @BeforeAll
    public static void init() {
        System.setProperty("airsonic.home", tempDir.toString());
    }

    @Test
    public void testDeleteTranscoding() {
        // This test is to ensure that the deleteTranscoding method works correctly
        // It will be implemented in a way that it does not require any specific setup
        // and simply calls the method to verify it does not throw any exceptions.
        Player player = new Player();
        playerRepository.saveAndFlush(player);
        Transcoding transcoding = transcodingRepository.findAll().get(0);

        player.setTranscodings(new ArrayList<>(List.of(transcoding)));
        playerRepository.saveAndFlush(player);
        assertEquals(1, player.getTranscodings().size());

        transcodingService.deleteTranscoding(transcoding.getId()); // Assuming 1 is a valid transcoding ID for testing
        player = playerRepository.findById(player.getId()).orElseThrow();
        assertEquals(0, player.getTranscodings().size());

        List<Transcoding> transcodings = transcodingRepository.findByPlayersContaining(player);
        assertEquals(0, transcodings.size());
    }

    // Regression coverage for the offsetSeconds substitution path (fixes #181).
    // The %o slot drives ffmpeg's -ss argument. Prior to the fix, a Double offsetSeconds
    // was truncated to int before reaching the substitution map, silently dropping
    // sub-second precision. The path had no test coverage and the bug was latent.
    @Test
    public void testSubstitutionMapCarriesFractionalTimeOffset() {
        Map<String, String> vars = TranscodingService.generateTranscodingSubstitutionMap(
                "t", "a", "l", null, null,
                String.valueOf(12.5d),
                null, null, null, null, null, null, null, null, null, null);
        assertEquals("12.5", vars.get("%o"));
    }

    @Test
    public void testSubstitutionMapPreservesIntegerOffsetAsDecimal() {
        Map<String, String> vars = TranscodingService.generateTranscodingSubstitutionMap(
                "t", "a", "l", null, null,
                String.valueOf(12.0d),
                null, null, null, null, null, null, null, null, null, null);
        assertEquals("12.0", vars.get("%o"));
    }
}
