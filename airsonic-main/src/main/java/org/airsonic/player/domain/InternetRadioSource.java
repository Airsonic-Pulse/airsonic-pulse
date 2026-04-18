package org.airsonic.player.domain;

import java.util.Objects;

public record InternetRadioSource(String streamUrl) {

    public InternetRadioSource {
        Objects.requireNonNull(streamUrl, "streamUrl");
    }
}
