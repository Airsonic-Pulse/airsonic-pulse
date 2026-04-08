package org.airsonic.player.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.io.Serializable;

@Entity
@Table(name = "playlist_file")
public class PlaylistMediaFile implements Serializable {

    public PlaylistMediaFile() {
    }

    public PlaylistMediaFile(Playlist playlist, MediaFile mediaFile, int orderIndex) {
        this.playlist = playlist;
        this.mediaFile = mediaFile;
        this.orderIndex = orderIndex;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "playlist_id")
    private Playlist playlist;

    @ManyToOne
    @JoinColumn(name = "media_file_id")
    private MediaFile mediaFile;

    @Column(name = "order_index")
    private int orderIndex;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Playlist getPlaylist() {
        return playlist;
    }

    public void setPlaylist(Playlist playlist) {
        this.playlist = playlist;
    }

    public MediaFile getMediaFile() {
        return mediaFile;
    }

    public void setMediaFile(MediaFile mediaFile) {
        this.mediaFile = mediaFile;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(int orderIndex) {
        this.orderIndex = orderIndex;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlaylistMediaFile that)) return false;

        if (orderIndex != that.orderIndex) return false;
        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (playlist != null ? !playlist.equals(that.playlist) : that.playlist != null) return false;
        return mediaFile != null ? mediaFile.equals(that.mediaFile) : that.mediaFile == null;
    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (playlist != null ? playlist.hashCode() : 0);
        result = 31 * result + (mediaFile != null ? mediaFile.hashCode() : 0);
        result = 31 * result + orderIndex;
        return result;
    }


}
