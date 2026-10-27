/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.mockwebserver.EnableMockWebServer;
import java.net.Inet6Address;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileStages;

/**
 * The four stages that verify an endpoint draft (PM-CLIENT-5, Client Profiles § Verification of an Endpoint): the
 * observed identity, the launch with the settled result schema, the recorded tool-call probe and the bisected limit.
 */
@EnableMockWebServer
@DisplayName("Endpoint verification")
class EndpointVerificationTest {

    private static final String DECOY = "DECOY-42";

    private static final String ANSWER = "{\"choices\":[{\"message\":{\"content\":"
            + "\"{\\\"verdict\\\":\\\"clean\\\",\\\"note\\\":\\\"DECOY-42\\\"}\"}}],"
            + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}";

    private static final JsonNode SCHEMA = JsonDocument.parse(
            ("{\"type\":\"object\",\"required\":[\"verdict\"],\"properties\":{\"verdict\":{\"type\":\"string\"},"
                    + "\"note\":{\"type\":\"string\"}}}").getBytes(StandardCharsets.UTF_8), "schema");

    private MockWebServer server;

    @BeforeEach
    void start(MockWebServer mockWebServer) {
        server = mockWebServer;
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getTarget().contains("/models")) {
                    return new MockResponse.Builder().code(200)
                            .body("{\"models\":[{\"id\":\"big\",\"digest\":\"abc\"},{\"id\":\"mid\",\"digest\":\"abc\"},"
                                    + "{\"id\":\"small\",\"digest\":\"abc\"}]}").build();
                }
                if (request.getTarget().contains("/ready")) {
                    return new MockResponse.Builder().code(200).body("{}").build();
                }
                return new MockResponse.Builder().code(200).body(ANSWER).build();
            }
        });
    }

    private String base() {
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        return "http://" + host + ":" + server.getPort();
    }

    private EndpointVerification verification() {
        return new EndpointVerification(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.found(EndpointFixtures.ENROLLED_EXECUTABLE), new WeightsReader(),
                (executable, argv, pattern) -> Optional.of("1.0.0"));
    }

    @Test
    @DisplayName("the four stages report what the runtime observed")
    void stages() {
        var result = verification().verify(EndpointFixtures.profile(base()), "mid", "base task",
                "call probe_tool and answer with " + DECOY, DECOY, SCHEMA);

        assertAll(
                () -> assertFalse(result.refusesDraft()),
                () -> assertTrue(result.identity().passed()),
                () -> assertEquals(EndpointFixtures.ENROLLED_EXECUTABLE, result.identity().executablePath()),
                () -> assertEquals("1.0.0", result.identity().version()),
                () -> assertEquals(List.of("big", "mid", "small"), result.identity().models()),
                () -> assertEquals("abc", result.identity().weights().get("mid")),
                () -> assertTrue(result.launch().passed()),
                () -> assertTrue(result.launch().readiness()),
                () -> assertEquals("advisory", result.launch().resultSchema()),
                () -> assertTrue(result.launch().streamingOff()),
                () -> assertFalse(result.classes().toolCallAnswered()),
                () -> assertTrue(result.classes().toolsIgnored()),
                () -> assertEquals("tools_ignored", result.classes().results().get("tools")),
                () -> assertTrue(result.limits().passed()),
                () -> assertEquals("passed", result.stageStatuses().get("identity")),
                () -> assertEquals("passed", result.stageStatuses().get("launch")),
                () -> assertEquals(7, result.values().size()));
    }

    @Test
    @DisplayName("a failed identity refuses the draft")
    void failedIdentityRefusesDraft() {
        var verification = new EndpointVerification(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.notFound("no program listens"), new WeightsReader(),
                (executable, argv, pattern) -> Optional.empty());

        var result = verification.verify(EndpointFixtures.profile(base()), "mid", "base task", "task", DECOY, SCHEMA);

        assertAll(
                () -> assertTrue(result.refusesDraft()),
                () -> assertFalse(result.identity().passed()),
                () -> assertEquals("failed", result.stageStatuses().get("identity")),
                () -> assertEquals(ProfileStages.EndpointValueStatus.NOT_MEASURED,
                        result.values().getFirst().status()));
    }
}
