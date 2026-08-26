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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards against two controllers registering the same (pattern, method) pair, which Spring
 * accepts at startup (the full RequestMappingInfos differ) but rejects at request time with
 * "Ambiguous handler methods mapped" once per-request matching reduces both infos to the same
 * conditions. #325 hit exactly this: StreamController mapped /ext/stream directly while
 * SubsonicMediaController composed the same pattern from its class-level /ext prefix, killing
 * every external-player, UPnP, Sonos and share stream request.
 *
 * Only unconditional mappings are compared — a params/headers/consumes/produces condition is a
 * legitimate disambiguator, so infos carrying one are skipped.
 */
@SpringBootTest
public class HandlerMappingUniquenessTest {

    @TempDir
    private static Path tempAirsonicHome;

    @BeforeAll
    public static void beforeAll() {
        System.setProperty("airsonic.home", tempAirsonicHome.toString());
    }

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    public void noTwoHandlersShareAnUnconditionalPatternAndMethod() {
        Map<String, HandlerMethod> seen = new HashMap<>();
        List<String> collisions = new ArrayList<>();

        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            if (!info.getParamsCondition().getExpressions().isEmpty()
                    || !info.getHeadersCondition().getExpressions().isEmpty()
                    || !info.getConsumesCondition().getExpressions().isEmpty()
                    || !info.getProducesCondition().getExpressions().isEmpty()) {
                continue;
            }

            Set<String> patterns = new HashSet<>();
            if (info.getPathPatternsCondition() != null) {
                info.getPathPatternsCondition().getPatterns()
                        .forEach(pattern -> patterns.add(pattern.getPatternString()));
            } else {
                patterns.addAll(info.getPatternsCondition().getPatterns());
            }

            Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
            if (methods.isEmpty()) {
                methods = Set.of(RequestMethod.values());
            }

            for (String pattern : patterns) {
                for (RequestMethod method : methods) {
                    String key = method + " " + pattern;
                    HandlerMethod previous = seen.putIfAbsent(key, entry.getValue());
                    if (previous != null && !previous.equals(entry.getValue())) {
                        collisions.add(key + " -> " + previous + " AND " + entry.getValue());
                    }
                }
            }
        }

        assertTrue(collisions.isEmpty(),
                "Ambiguous handler registrations (same pattern and method, no disambiguating condition"
                        + " — these throw \"Ambiguous handler methods mapped\" at request time):\n"
                        + String.join("\n", collisions));
    }

    @Test
    public void streamEndpointsResolveToTheirControllers() throws Exception {
        assertEquals(StreamController.class, resolveHandler("/ext/stream").getBeanType());
        assertEquals("handleRequest", resolveHandler("/ext/stream").getMethod().getName());

        assertEquals(SubsonicMediaController.class, resolveHandler("/rest/stream").getBeanType());
        assertEquals("stream", resolveHandler("/rest/stream").getMethod().getName());
    }

    private HandlerMethod resolveHandler(String path) throws Exception {
        HandlerExecutionChain chain = handlerMapping.getHandler(new MockHttpServletRequest("GET", path));
        assertNotNull(chain, "No handler mapped for GET " + path);
        return (HandlerMethod) chain.getHandler();
    }
}
