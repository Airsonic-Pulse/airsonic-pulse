package org.airsonic.player.domain;

public record MediaFileWithUrlInfo(
        MediaFile file,
        boolean streamable,
        String coverArtUrl,
        String streamUrl,
        String captionsUrl,
        String contentType) {
}
