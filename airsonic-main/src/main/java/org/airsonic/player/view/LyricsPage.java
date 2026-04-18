package org.airsonic.player.view;

import jakarta.annotation.Nullable;

/**
 * Data holder for the lyrics view.
 *
 * @param id the mediafile id
 * @param artist the artist name
 * @param song the song title
 */
public record LyricsPage(@Nullable Integer id, @Nullable String artist, @Nullable String song, @Nullable String lyrics, String source) {
}
