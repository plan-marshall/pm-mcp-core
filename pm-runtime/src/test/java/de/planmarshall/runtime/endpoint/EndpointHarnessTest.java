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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.mockwebserver.EnableMockWebServer;
import java.net.Inet6Address;
import java.util.concurrent.TimeUnit;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * One task on an endpoint harness (PM-CLIENT-2, Client Profiles § The Request, § Enrolment): the owner check refuses a
 * program that is not the enrolled one before any task text is sent, and an accepted answer carries its usage.
 */
@EnableMockWebServer
@DisplayName("Endpoint harness")
class EndpointHarnessTest {

    private static final String ANSWER = "{\"choices\":[{\"message\":{\"content\":\"{\\\"verdict\\\":\\\"clean\\\"}\"}}],"
            + "\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":2}}";

    private MockWebServer server;

    @BeforeEach
    void start(MockWebServer mockWebServer) {
        server = mockWebServer;
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse.Builder().code(200).body(ANSWER).build();
            }
        });
    }

    private String base() {
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        return "http://" + host + ":" + server.getPort();
    }

    private static EndpointRequest request() {
        return EndpointRequest.compose(EndpointFixtures.profile(EndpointFixtures.BASE_URL), "mid", "prompt", "task",
                null).request();
    }

    @Test
    @DisplayName("another program on the port refuses the task before any text is sent")
    void anotherProgramRefused() {
        var harness = new EndpointHarness(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.found("/usr/local/bin/other"));
        var profile = EndpointFixtures.profile(base());

        var outcome = harness.task(profile, EndpointFixtures.enrolment(), request(), null);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, outcome.reason()),
                () -> assertTrue(outcome.detail().contains("the enrolled executable")),
                () -> assertEquals(0, server.getRequestCount(), "no task text reached the server"));
    }

    @Test
    @DisplayName("a socket whose owner cannot be read refuses the task")
    void unknownOwnerRefused() {
        var harness = new EndpointHarness(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.notFound("no program listens on the port"));
        var profile = EndpointFixtures.profile(base());

        var outcome = harness.task(profile, EndpointFixtures.enrolment(), request(), null);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, outcome.fate()),
                () -> assertTrue(outcome.detail().contains("no program listens")),
                () -> assertEquals(0, server.getRequestCount()));
    }

    @Test
    @DisplayName("the enrolled program answers, and the request went out with streaming off")
    void acceptedAnswer() throws Exception {
        var harness = new EndpointHarness(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.found(EndpointFixtures.ENROLLED_EXECUTABLE));
        var profile = EndpointFixtures.profile(base());

        var outcome = harness.task(profile, EndpointFixtures.enrolment(), request(), null);
        var recorded = server.takeRequest(2, TimeUnit.SECONDS);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ANSWERED, outcome.fate(), outcome.detail()),
                () -> assertEquals("{\"verdict\":\"clean\"}", outcome.content()),
                () -> assertEquals(5, outcome.usage().totalTokens().orElseThrow()),
                () -> assertNotNull(recorded, "a request reached the server"),
                () -> assertTrue(recorded.getBody().utf8().contains("\"stream\":false")),
                () -> assertFalse(recorded.getBody().utf8().contains("\"tools\"")));
    }

    @Test
    @DisplayName("the readiness check answers true only for a success status")
    void readiness() {
        var harness = new EndpointHarness(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.found(EndpointFixtures.ENROLLED_EXECUTABLE));

        assertTrue(harness.ready(EndpointFixtures.profile(base())));
    }

    @Test
    @DisplayName("the models listing is read through the profile's pointer")
    void listing() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse.Builder().code(200)
                        .body("{\"models\":[{\"id\":\"mid\"}]}").build();
            }
        });
        var harness = new EndpointHarness(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.found(EndpointFixtures.ENROLLED_EXECUTABLE));

        var listing = harness.listing(EndpointFixtures.profile(base()));

        assertAll(
                () -> assertNotNull(listing),
                () -> assertTrue(listing.isArray()),
                () -> assertEquals(1, listing.elements().size()));
    }
}
