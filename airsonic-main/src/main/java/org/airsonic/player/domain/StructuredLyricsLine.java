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
package org.airsonic.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single timestamped line of synced (LRC) lyrics belonging to a {@link Lyrics} row. Carries the
 * line's display order ({@code position}), its start offset in milliseconds ({@code startMs}), and
 * the line {@code text}. Persisted as a child of {@code lyrics} so the flat text blob (used by the
 * legacy {@code /getLyrics} endpoint and the unsynced fallback) and the structured synced lines
 * (used by {@code getLyricsBySongId} to emit {@code synced=true} with per-line {@code start})
 * coexist on the same cache row.
 */
@Entity
@Table(name = "structured_lyrics_line")
public class StructuredLyricsLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "lyrics_id", nullable = false)
    private Lyrics lyrics;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "start_ms", nullable = false)
    private long startMs;

    @Column(name = "text", nullable = false)
    private String text;

    public StructuredLyricsLine() {
    }

    public StructuredLyricsLine(Lyrics lyrics, int position, long startMs, String text) {
        this.lyrics = lyrics;
        this.position = position;
        this.startMs = startMs;
        this.text = text;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Lyrics getLyrics() {
        return lyrics;
    }

    public void setLyrics(Lyrics lyrics) {
        this.lyrics = lyrics;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public long getStartMs() {
        return startMs;
    }

    public void setStartMs(long startMs) {
        this.startMs = startMs;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }
}
