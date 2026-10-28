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
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The HTTP calls to a loopback endpoint (PM-CLIENT-2, Client Profiles § The Request): an answer keeps its status and body,
 * a target that answers nothing is unreachable, and the URL is the profile's base URL and the path.
 */
@EnableMockWebServer
@DisplayName("Endpoint client")
class EndpointClientTest {

    private MockWebServer server;
    private final EndpointClient client = new EndpointClient();

    @BeforeEach
    void start(MockWebServer mockWebServer) {
        server = mockWebServer;
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                if (request.getTarget().contains("/broken")) {
                    return new MockResponse.Builder().code(500).body("{\"error\":\"broken\"}").build();
                }
                return new MockResponse.Builder().code(200).body("{\"ok\":true}").build();
            }
        });
    }

    private String base() {
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        return "http://" + host + ":" + server.getPort();
    }

    @Test
    @DisplayName("a GET keeps the status and the body of the answer")
    void getKeepsTheAnswer() {
        var response = client.get(EndpointFixtures.profile(base()), "/models");

        assertAll(
                () -> assertTrue(response.successful()),
                () -> assertEquals(200, response.status()),
                () -> assertEquals("{\"ok\":true}", response.body()),
                () -> assertEquals(EndpointClient.Failure.NONE, response.failure()));
    }

    @Test
    @DisplayName("an error status is an answer, not a failure of the call")
    void errorStatusIsAnAnswer() throws Exception {
        var response = client.get(EndpointFixtures.profile(base()), "/broken");
        var recorded = server.takeRequest(2, TimeUnit.SECONDS);

        assertAll(
                () -> assertFalse(response.successful()),
                () -> assertEquals(500, response.status()),
                () -> assertEquals(EndpointClient.Failure.NONE, response.failure()),
                () -> assertTrue(recorded.getHeaders().toString().contains("application/json")));
    }

    @Test
    @DisplayName("a POST sends the body it is given")
    void postSendsTheBody() throws Exception {
        var response = client.post(EndpointFixtures.profile(base()), EndpointClient.CHAT_PATH, "{\"model\":\"mid\"}");
        var recorded = server.takeRequest(2, TimeUnit.SECONDS);

        assertAll(
                () -> assertTrue(response.successful()),
                () -> assertEquals("{\"model\":\"mid\"}", recorded.getBody().utf8()));
    }

    @Test
    @DisplayName("a target that answers nothing is unreachable")
    void unreachable() throws Exception {
        int port;
        try (var socket = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
            port = socket.getLocalPort();
        }

        var response = client.get(EndpointFixtures.profile("http://127.0.0.1:" + port), "/models");

        assertAll(
                () -> assertFalse(response.successful()),
                () -> assertEquals(EndpointClient.Failure.UNREACHABLE, response.failure()),
                () -> assertTrue(response.detail().contains("unreachable")),
                () -> assertEquals(0, response.status()));
    }

    @Test
    @DisplayName("a target that is not a loopback address is refused before any request")
    void notLoopback() {
        var response = client.get(EndpointFixtures.profile("https://runner.example.test"), "/models");

        assertAll(
                () -> assertFalse(response.successful()),
                () -> assertEquals(EndpointClient.Failure.UNREACHABLE, response.failure()),
                () -> assertTrue(response.detail().contains("not a loopback address")));
    }

    @Test
    @DisplayName("the URL is the base URL and the path")
    void uri() {
        var profile = EndpointFixtures.profile("http://127.0.0.1:11434");

        assertAll(
                () -> assertEquals(URI.create("http://127.0.0.1:11434/models"),
                        EndpointClient.uri(profile, "/models")),
                () -> assertEquals(URI.create("http://127.0.0.1:11434/models"),
                        EndpointClient.uri(profile, "models")));
    }

    @Test
    @DisplayName("both loopback literals are admitted, with the brackets URI keeps")
    void loopbackLiterals() {
        assertAll(
                () -> assertTrue(EndpointClient.loopback(URI.create("http://127.0.0.1:11434"))),
                () -> assertTrue(EndpointClient.loopback(URI.create("http://[::1]:11434"))),
                () -> assertFalse(EndpointClient.loopback(URI.create("http://10.0.0.2:11434"))),
                () -> assertFalse(EndpointClient.loopback(URI.create("https://runner.example.test"))));
    }
}
