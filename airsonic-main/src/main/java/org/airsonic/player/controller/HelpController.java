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

 Copyright 2024 (C) Y.Tory
 Copyright 2016 (C) Airsonic Authors
 Based upon Subsonic, Copyright 2009 (C) Sindre Mehus
 */
package org.airsonic.player.controller;

import org.airsonic.player.service.RuntimeService;
import org.airsonic.player.service.SecurityService;
import org.airsonic.player.service.SettingsService;
import org.airsonic.player.service.VersionService;
import org.airsonic.player.util.StringUtil;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.ModelAndView;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller for the help page.
 *
 * @author Sindre Mehus
 */
@Controller
@RequestMapping({"/help", "/help.view"})
public class HelpController {

    private static final Logger LOG = LoggerFactory.getLogger(HelpController.class);

    private static final int LOG_LINES_TO_SHOW = 50;

    @Autowired
    private VersionService versionService;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private SecurityService securityService;
    @Autowired
    private RuntimeService runtimeService;

    @GetMapping
    protected ModelAndView handleRequestInternal(HttpServletRequest request,
                                                 HttpServletResponse response,
                                                 Locale locale) {
        Map<String, Object> map = new HashMap<>();

        if (versionService.isNewFinalVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", versionService.getLatestFinalVersion());
        } else if (versionService.isNewBetaVersionAvailable()) {
            map.put("newVersionAvailable", true);
            map.put("latestVersion", versionService.getLatestBetaVersion());
        } else {
            map.put("newVersionAvailable", false);
        }

        String serverInfo = request.getSession().getServletContext().getServerInfo() +
                            ", java " + System.getProperty("java.version") +
                            ", " + System.getProperty("os.name");

        map.put("user", securityService.getCurrentUser(request));
        map.put("brand", settingsService.getBrand());
        map.put("localVersion", versionService.getLocalVersion());
        map.put("displayVersion", versionService.getDisplayVersion());
        map.put("buildDate", versionService.getLocalBuildDate());
        map.put("buildNumber", versionService.getLocalBuildNumber());
        map.put("serverInfo", serverInfo);
        map.put("usedMemory", StringUtil.formatBytes(runtimeService.getUsedMemory(), locale));
        map.put("totalMemory", StringUtil.formatBytes(runtimeService.getTotalMemory(), locale));
        Path logFile = Paths.get(settingsService.getLogFile());
        List<String> latestLogEntries = getLatestLogEntries(logFile);
        map.put("logEntries", latestLogEntries);
        map.put("logFile", logFile);
        map.put("projectUrl", versionService.getProjectUrl());
        map.put("issueTrackerUrl", versionService.getIssueTrackerUrl());

        return new ModelAndView("help","model",map);
    }

    private static List<String> getLatestLogEntries(Path logFile) {
        List<String> lines = new LinkedList<>();
        try (ReversedLinesFileReader reader = ReversedLinesFileReader.builder().setFile(logFile.toFile())
                .setCharset(Charset.defaultCharset()).get()) {
            String current;
            while ((current = reader.readLine()) != null) {
                if (lines.size() >= LOG_LINES_TO_SHOW) {
                    break;
                }
                lines.add(0, current);
            }
            return lines;
        } catch (IOException e) {
            LOG.warn("Could not open log file {}", logFile, e);
            return null;
        }
    }


}
