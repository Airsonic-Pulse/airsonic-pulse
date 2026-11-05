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

 Copyright 2023 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.domain.Playlist;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.PlaylistFileService;
import org.airsonic.player.service.SecurityService;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpServletRequest;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/importPlaylist", "/importPlaylist.view"})
public class ImportPlaylistController {

    private static final long MAX_PLAYLIST_SIZE_MB = 5L;

    @Autowired
    private SecurityService securityService;
    @Autowired
    private PlaylistFileService playlistFileService;

    @PostMapping
    protected String handlePost(@RequestParam("file") MultipartFile file,
                                RedirectAttributes redirectAttributes,
                                HttpServletRequest request
    ) {
        Map<String, Object> map = new HashMap<String, Object>();

        try {
            if (!StringUtils.isBlank(file.getOriginalFilename())) {
                if (file.getSize() > MAX_PLAYLIST_SIZE_MB * 1024L * 1024L) {
                    throw new Exception("The playlist file is too large. Max file size is " + MAX_PLAYLIST_SIZE_MB + " MB.");
                }
                String playlistName = FilenameUtils.getBaseName(file.getOriginalFilename());
                String fileName = FilenameUtils.getName(file.getOriginalFilename());
                User user = securityService.getCurrentUser(request);
                Playlist playlist = playlistFileService.importPlaylist(user, playlistName, fileName, null, file.getInputStream(), null);
                map.put("playlist", playlist);
            } else {
                throw new Exception("No file specified.");
            }
        } catch (Exception e) {
            map.put("error", e.getMessage());
        }

        redirectAttributes.addFlashAttribute("model", map);
        return "redirect:importPlaylist";
    }

    @GetMapping
    public String handleGet() {
        return "importPlaylist";
    }

}
