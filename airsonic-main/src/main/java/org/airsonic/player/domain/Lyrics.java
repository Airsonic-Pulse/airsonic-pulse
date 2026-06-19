package org.airsonic.player.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lyrics")
public class Lyrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "lyrics", nullable = false)
    private String lyrics;

    @Column(name = "media_file_id", nullable = false)
    private Integer mediaFileId;

    @Column(name = "source", nullable = false)
    private String source;

    // True when the lyrics carry per-line LRC timestamps (a parsed LRC sidecar). The unsynced
    // tiers (legacy cache, embedded tag) leave this false. Drives the getLyricsBySongId synced
    // flag; see #140.
    @Column(name = "synced", nullable = false)
    private boolean synced;

    // Per-line timestamped lyrics, present only when synced. EAGER because lyrics are always read
    // one song at a time (findByMediaFileId — no batch/list path exists) and the endpoint reads
    // the lines outside the service's @Transactional boundary, so a LAZY collection would throw.
    // The bounded small collection mirrors the codebase's EAGER User.musicFolders. @OrderBy makes
    // the line ordering deterministic across HSQLDB/Postgres/MariaDB (not insertion-dependent).
    @OneToMany(mappedBy = "lyrics", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position ASC")
    private List<StructuredLyricsLine> lines = new ArrayList<>();

    @Column(name = "created", nullable = false)
    private Instant created;

    @Column(name = "updated", nullable = false)
    private Instant updated;

    /**
     * Creates a new Lyrics instance with the provided lyrics.
     * The created and updated timestamps are set to the current time.
     * The timestamps are truncated to microseconds for consistency.
     *
     * @param lyrics The lyrics text.
     */
    public Lyrics(String lyrics, Integer mediaFileId, String source) {
        this.lyrics = lyrics;
        this.mediaFileId = mediaFileId;
        this.source = source;
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.created = now;
        this.updated = now;
    }

    public Lyrics() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLyrics() {
        return lyrics;
    }

    public void setLyrics(String lyrics) {
        this.lyrics = lyrics;
    }

    public Integer getMediaFileId() {
        return mediaFileId;
    }

    public void setMediaFileId(Integer mediaFileId) {
        this.mediaFileId = mediaFileId;
    }

    public Instant getCreated() {
        return created;
    }

    public void setCreated(Instant created) {
        this.created = created;
    }

    public Instant getUpdated() {
        return updated;
    }

    public void setUpdated(Instant updated) {
        this.updated = updated;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public boolean isSynced() {
        return synced;
    }

    public void setSynced(boolean synced) {
        this.synced = synced;
    }

    public List<StructuredLyricsLine> getLines() {
        return lines;
    }

    public void setLines(List<StructuredLyricsLine> lines) {
        this.lines = lines != null ? lines : new ArrayList<>();
    }

    @Override
    public int hashCode() {
        return lyrics != null ? lyrics.hashCode() : 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Lyrics other = (Lyrics) obj;
        return lyrics != null ? lyrics.equals(other.getLyrics()) : other.getLyrics() == null;
    }

}
