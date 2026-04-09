/*
 * This file is part of Airsonic.
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Airsonic is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Copyright 2024 (C) Y.Tory
 *  Copyright 2015 (C) Sindre Mehus
 */
package org.airsonic.player.command;

import org.airsonic.player.domain.PodcastChannel;
import org.airsonic.player.domain.PodcastEpisode;
import org.airsonic.player.domain.User;

import java.util.ArrayList;
import java.util.List;

public class PodcastChannelCommand {

    private User user;
    private PodcastChannel channel;
    private List<PodcastEpisodeCommand> episodes = new ArrayList<>();
    private boolean partyModeEnabled;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public PodcastChannel getChannel() {
        return channel;
    }

    public void setChannel(PodcastChannel channel) {
        this.channel = channel;
    }

    public List<PodcastEpisodeCommand> getEpisodes() {
        return episodes;
    }

    public void setEpisodes(List<PodcastEpisodeCommand> episodes) {
        this.episodes = episodes;
    }

    public void setEpisodesByDAO(List<PodcastEpisode> episodes) {
        this.episodes = episodes.stream().map(PodcastEpisodeCommand::new).toList();
    }

    public boolean isPartyModeEnabled() {
        return partyModeEnabled;
    }

    public void setPartyModeEnabled(boolean partyModeEnabled) {
        this.partyModeEnabled = partyModeEnabled;
    }

}
