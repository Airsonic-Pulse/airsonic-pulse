package org.airsonic.player.view;

/**
 * Data holder for the lyrics view.
 *
 * @param id the mediafile id
 * @param artist the artist name
 * @param song the song title
 */
public record LyricsPage(Integer id, String artist, String song, String lyrics, String source) {
}
