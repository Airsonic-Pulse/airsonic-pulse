package org.airsonic.player.util;

import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.service.MediaFolderService;
import org.springframework.jdbc.core.JdbcTemplate;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class MusicFolderTestData {

    private static String baseResources = "/MEDIAS/";

    public static Path resolveBaseMediaPath() {
        try {
            return Paths.get(MusicFolderTestData.class.getResource(baseResources).toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException("Could not find base resources for tests", e);
        }
    }

    public static Path resolveMusicFolderPath() {
        return resolveBaseMediaPath().resolve("Music");
    }

    public static Path resolveMusic2FolderPath() {
        return resolveBaseMediaPath().resolve("Music2");
    }

    public static Path resolveMusic3FolderPath() {
        return resolveBaseMediaPath().resolve("Music3");
    }

    public static Path resolveMusic4FolderPath() {
        return resolveBaseMediaPath().resolve("Music4");
    }

    public static Path resolveMusicCueFolderPath() {
        return resolveBaseMediaPath().resolve("cue");
    }

    public static Path resolveMusicCueWithBOMFolderPath() {
        return resolveBaseMediaPath().resolve("cueBom");
    }

    public static Path resolveMusicDisableCueFolderPath() {
        return resolveBaseMediaPath().resolve("disableCue");
    }

    public static Path resolveMusicInvalidCue2FolderPath() {
        return resolveBaseMediaPath().resolve("invalidCue2");
    }

    public static Path resolveMusicInvalidCue3FolderPath() {
        return resolveBaseMediaPath().resolve("invalidCue3");
    }

    public static Path resolveMusicMpcFolderPath() {
        return resolveBaseMediaPath().resolve("mpc");
    }

    public static Path resolveM4bAudioPath() {
        return resolveBaseMediaPath().resolve("m4baudiobook");
    }

    public static Path resolveMusicLoopFolderPath() {
        return resolveBaseMediaPath().resolve("loop");
    }

    public static Path resolveMusicCueAndFlacFolderPath() {
        return resolveBaseMediaPath().resolve("cueAndFlac");
    }

    public static Path resolveMusicFlacWithCueFolderPath() {
        return resolveBaseMediaPath().resolve("flacWithCue");
    }

    public static Path resolveLyricsFolderPath() {
        return resolveBaseMediaPath().resolve("lyrics");
    }


    /**
     * Builds UNPERSISTED, id-less MusicFolder instances for the shared MEDIAS/Music and
     * MEDIAS/Music2 paths. These are creation templates only: never keep them for cleanup,
     * because without the JPA-assigned id a deleteMusicFolder(folder.getId()) call is a
     * deleteMusicFolder(null) no-op and the row leaks across test classes (#271, #266).
     * Persist through {@link #createMusicFolders} (or save them yourself and pass each
     * through {@link #requirePersisted}) and hold only the returned instances.
     */
    public static List<MusicFolder> getTestMusicFolders() {
        List<MusicFolder> liste = new ArrayList<>();
        Path musicDir = resolveMusicFolderPath();
        MusicFolder musicFolder = new MusicFolder(musicDir, "MusicTest", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        liste.add(musicFolder);

        Path music2Dir = resolveMusic2FolderPath();
        MusicFolder musicFolder2 = new MusicFolder(music2Dir, "MusicTest2", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        liste.add(musicFolder2);
        return liste;
    }

    /**
     * Persists the given folders through {@link MediaFolderService#createMusicFolder} and
     * returns the same instances once each carries its JPA-assigned id. This is the factory
     * contract for fixture folders: setup keeps ONLY the returned list, and cleanup deletes
     * by {@code folder.getId()} from it, so an id-less instance can never reach cleanup.
     *
     * @throws IllegalStateException if a folder comes back without an id
     */
    public static List<MusicFolder> createMusicFolders(MediaFolderService mediaFolderService, List<MusicFolder> folders) {
        List<MusicFolder> persisted = new ArrayList<>();
        for (MusicFolder folder : folders) {
            mediaFolderService.createMusicFolder(folder);
            persisted.add(requirePersisted(folder));
        }
        return persisted;
    }

    /**
     * Persists the folders from {@link #getTestMusicFolders()} and returns the persisted
     * instances; see {@link #createMusicFolders}.
     */
    public static List<MusicFolder> createTestMusicFolders(MediaFolderService mediaFolderService) {
        return createMusicFolders(mediaFolderService, getTestMusicFolders());
    }

    /**
     * Asserts the folder carries its JPA-assigned id and returns it. Guards the cleanup
     * contract: deleting by an id-less instance is a silent no-op that leaks the row.
     */
    public static MusicFolder requirePersisted(MusicFolder folder) {
        if (folder.getId() == null) {
            throw new IllegalStateException("MusicFolder '" + folder.getName()
                    + "' has no JPA-assigned id; cleanup through this instance would be a"
                    + " deleteMusicFolder(null) no-op and leak the row (#266). Persist it"
                    + " first and keep the persisted instance.");
        }
        return folder;
    }

    /**
     * Deletes the Artist and Album rows the media scanner created for the given persisted
     * folders. The artist/album tables carry a plain {@code folder_id} column with no FK to
     * music_folder, so these rows survive folder deletion and must be removed explicitly;
     * scoping the DELETE to the captured folder ids keeps this exact (no broad sweeps).
     * Call it from {@code @AfterEach} BEFORE deleting the folders themselves.
     *
     * @throws IllegalStateException if a folder has no id (see {@link #requirePersisted})
     */
    public static void deleteScannedArtistsAndAlbums(JdbcTemplate jdbcTemplate, Collection<MusicFolder> folders) {
        for (MusicFolder folder : folders) {
            requirePersisted(folder);
            jdbcTemplate.update("DELETE FROM album WHERE folder_id = ?", folder.getId());
            jdbcTemplate.update("DELETE FROM artist WHERE folder_id = ?", folder.getId());
        }
    }
}
