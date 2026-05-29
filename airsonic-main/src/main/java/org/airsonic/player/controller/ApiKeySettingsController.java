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
package org.airsonic.player.controller;

import org.airsonic.player.domain.ApiKey;
import org.airsonic.player.domain.User;
import org.airsonic.player.service.ApiKeyService;
import org.airsonic.player.service.ApiKeyService.GeneratedApiKey;
import org.airsonic.player.service.SecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Per-user management of OpenSubsonic API keys (issue #145).
 *
 * Each authenticated user manages ONLY their own keys: list/generate/disable/revoke.
 * The generate flow uses the post-redirect-get pattern with a flash attribute so the
 * raw key is shown exactly once and never persisted on the entity, in the response
 * after the show-once render, or in any log line.
 *
 * The {@link ApiKeyService#revoke(Integer)} and {@link ApiKeyService#setEnabled(Integer, boolean)}
 * methods are intentionally ownership-blind. This controller enforces ownership via
 * a {@link ApiKeyService#list(String)} membership check before either call, so a user
 * forging another user's key id cannot affect that key.
 */
@Controller
@RequestMapping({"/apikeySettings", "/apikeySettings.view"})
public class ApiKeySettingsController {

    private static final Logger LOG = LoggerFactory.getLogger(ApiKeySettingsController.class);

    static final int MAX_NAME_LENGTH = 120;

    @Autowired
    private ApiKeyService apiKeyService;

    @Autowired
    private SecurityService securityService;

    @GetMapping
    protected String displayForm() {
        return "apiKeySettings";
    }

    @ModelAttribute
    protected void populateModel(Authentication user, ModelMap map) {
        String username = user.getName();
        List<ApiKeyView> keys = apiKeyService.list(username).stream()
                .map(ApiKeyView::from)
                .toList();

        User userInDb = securityService.getUserByName(username);

        map.addAttribute("username", username);
        map.addAttribute("keys", keys);
        map.addAttribute("adminRole", userInDb != null && userInDb.isAdminRole());
    }

    @PostMapping("/generate")
    protected String generate(Principal user,
            @RequestParam("name") String name,
            @RequestParam(name = "expiresAt", required = false) String expiresAt,
            RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }
        String trimmedName = name == null ? "" : name.trim();
        if (trimmedName.isEmpty()) {
            redirectAttributes.addFlashAttribute("settings_toast", false);
            redirectAttributes.addFlashAttribute("apikey_error", "apikeysettings.error.nameRequired");
            return "redirect:/apikeySettings.view";
        }
        if (trimmedName.length() > MAX_NAME_LENGTH) {
            redirectAttributes.addFlashAttribute("settings_toast", false);
            redirectAttributes.addFlashAttribute("apikey_error", "apikeysettings.error.nameTooLong");
            return "redirect:/apikeySettings.view";
        }

        Instant expiresAtInstant;
        try {
            expiresAtInstant = parseExpiresAt(expiresAt);
        } catch (DateTimeParseException x) {
            redirectAttributes.addFlashAttribute("settings_toast", false);
            redirectAttributes.addFlashAttribute("apikey_error", "apikeysettings.error.expiresInvalid");
            return "redirect:/apikeySettings.view";
        }
        if (expiresAtInstant != null && expiresAtInstant.isBefore(Instant.now())) {
            redirectAttributes.addFlashAttribute("settings_toast", false);
            redirectAttributes.addFlashAttribute("apikey_error", "apikeysettings.error.expiresInPast");
            return "redirect:/apikeySettings.view";
        }

        GeneratedApiKey generated = apiKeyService.generate(user.getName(), trimmedName, expiresAtInstant);

        // Raw key flows back to the view via flash attributes ONLY. Spring's FlashMap
        // removes them from the session after the next request retrieves them, so the
        // key lives in the user's own session for a single HTTP roundtrip and is gone
        // after the show-once render. Never logged.
        redirectAttributes.addFlashAttribute("freshlyGeneratedKey", generated.rawKey());
        redirectAttributes.addFlashAttribute("freshlyGeneratedName", generated.persisted().getName());
        redirectAttributes.addFlashAttribute("settings_toast", true);
        return "redirect:/apikeySettings.view";
    }

    @PostMapping("/revoke")
    protected String revoke(Principal user,
            @RequestParam("id") Integer id,
            RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }
        if (!isOwnedBy(id, user.getName())) {
            // IDOR guard: silently no-op on a not-owned id rather than echo back any
            // information that distinguishes "not yours" from "no such key".
            LOG.debug("Rejected API-key revoke: id does not belong to authenticated user");
            redirectAttributes.addFlashAttribute("settings_toast", false);
            return "redirect:/apikeySettings.view";
        }
        apiKeyService.revoke(id);
        redirectAttributes.addFlashAttribute("settings_toast", true);
        return "redirect:/apikeySettings.view";
    }

    @PostMapping("/setEnabled")
    protected String setEnabled(Principal user,
            @RequestParam("id") Integer id,
            @RequestParam("enabled") boolean enabled,
            RedirectAttributes redirectAttributes) {
        if (user == null) {
            return "redirect:/login";
        }
        if (!isOwnedBy(id, user.getName())) {
            LOG.debug("Rejected API-key setEnabled: id does not belong to authenticated user");
            redirectAttributes.addFlashAttribute("settings_toast", false);
            return "redirect:/apikeySettings.view";
        }
        apiKeyService.setEnabled(id, enabled);
        redirectAttributes.addFlashAttribute("settings_toast", true);
        return "redirect:/apikeySettings.view";
    }

    private boolean isOwnedBy(Integer id, String username) {
        if (id == null || username == null) {
            return false;
        }
        return apiKeyService.list(username).stream()
                .map(ApiKey::getId)
                .anyMatch(id::equals);
    }

    private static Instant parseExpiresAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant();
    }

    /**
     * View-layer projection of {@link ApiKey} that intentionally omits {@code username}
     * and {@code keyHash}, so the template cannot accidentally render either.
     */
    public record ApiKeyView(Integer id, String name, Instant created, Instant lastUsed,
            boolean enabled, Instant expiresAt) {
        static ApiKeyView from(ApiKey k) {
            return new ApiKeyView(k.getId(), k.getName(), k.getCreated(), k.getLastUsed(),
                    k.isEnabled(), k.getExpiresAt());
        }
    }
}
