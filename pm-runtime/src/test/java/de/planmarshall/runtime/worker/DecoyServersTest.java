/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The loopback decoy servers (PM-CLIENT-5, Profile Verification): each decoy stays on {@code 127.0.0.1}, listens on its
 * own ephemeral port, and records exactly the requests a worker made of it.
 */
@DisplayName("The loopback decoy servers")
class DecoyServersTest {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    @Test
    @DisplayName("a request to the decoy MCP server is recorded on it alone")
    void aMcpHitIsRecorded() throws IOException, InterruptedException {
        try (var decoys = DecoyServers.start()) {
            var response = get(decoys.mcp().uri().resolve("mcp"));

            assertAll(
                    () -> assertEquals(200, response.statusCode(), "the decoy answers"),
                    () -> assertTrue(decoys.mcp().touched(), "the MCP decoy records the request"),
                    () -> assertEquals(List.of("GET /mcp"), decoys.mcp().hits(), "the path is recorded"),
                    () -> assertFalse(decoys.endpoint().touched(), "the endpoint decoy is untouched"));
        }
    }

    @Test
    @DisplayName("an untouched decoy records no hit")
    void anUntouchedDecoyRecordsNoHit() throws IOException {
        try (var decoys = DecoyServers.start()) {
            assertAll(
                    () -> assertFalse(decoys.mcp().touched(), "the MCP decoy saw nothing"),
                    () -> assertFalse(decoys.endpoint().touched(), "the endpoint decoy saw nothing"),
                    () -> assertTrue(decoys.mcp().hits().isEmpty(), "no hit is recorded"),
                    () -> assertTrue(decoys.endpoint().hits().isEmpty(), "no hit is recorded"));
        }
    }

    @Test
    @DisplayName("both decoys stay on loopback and listen on distinct ports")
    void bothStayOnLoopback() throws IOException {
        try (var decoys = DecoyServers.start()) {
            assertAll(
                    () -> assertTrue(decoys.mcp().port() > 0, "the MCP decoy bound a port"),
                    () -> assertTrue(decoys.endpoint().port() > 0, "the endpoint decoy bound a port"),
                    () -> assertNotEquals(decoys.mcp().port(), decoys.endpoint().port(),
                            "the two decoys are separate servers"),
                    () -> assertEquals("127.0.0.1", decoys.mcp().uri().getHost(), "the MCP decoy stays on loopback"),
                    () -> assertEquals("127.0.0.1", decoys.endpoint().uri().getHost(),
                            "the endpoint decoy stays on loopback"));
        }
    }

    @Test
    @DisplayName("a request to the decoy endpoint is recorded on the endpoint only")
    void anEndpointHitIsRecorded() throws IOException, InterruptedException {
        try (var decoys = DecoyServers.start()) {
            var response = get(decoys.endpoint().uri().resolve("models"));

            assertAll(
                    () -> assertEquals(200, response.statusCode(), "the decoy answers"),
                    () -> assertTrue(decoys.endpoint().touched(), "the endpoint decoy records the request"),
                    () -> assertEquals(List.of("GET /models"), decoys.endpoint().hits(), "the path is recorded"),
                    () -> assertFalse(decoys.mcp().touched(), "the MCP decoy is untouched"));
        }
    }

    private static HttpResponse<String> get(URI uri) throws IOException, InterruptedException {
        return CLIENT.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
