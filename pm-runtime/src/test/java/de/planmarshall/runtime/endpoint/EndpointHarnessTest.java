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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.mockwebserver.EnableMockWebServer;
import java.net.Inet6Address;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;

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

    private static SocketOwner owner(String executablePath) {
        return (host, port) -> SocketOwner.Lookup.found(executablePath);
    }

    @Test
    @DisplayName("another program on the port refuses the task before any text is sent")
    void anotherProgramRefused() {
        var harness = new EndpointHarness(new EndpointClient(), owner(EndpointFixtures.FOREIGN_EXECUTABLE));
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
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, outcome.reason()),
                () -> assertTrue(outcome.detail().contains("no program listens")),
                () -> assertEquals(0, server.getRequestCount()));
    }

    @Test
    @DisplayName("another build at the enrolled path refuses the task")
    void changedDigestRefused() {
        var harness = new EndpointHarness(new EndpointClient(), owner(EndpointFixtures.executablePath()));
        var profile = EndpointFixtures.profile(base());
        var previousBuild = EndpointFixtures.enrolment(EndpointFixtures.executablePath());

        var outcome = harness.task(profile, previousBuild, request(), null);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, outcome.reason()),
                () -> assertTrue(outcome.detail().contains("not the build the enrolment recorded")),
                () -> assertEquals(0, server.getRequestCount()));
    }

    @Test
    @DisplayName("the enrolled program answers, and the request went out with streaming off")
    void acceptedAnswer() throws Exception {
        var enrolled = EndpointFixtures.enrolment();
        var harness = new EndpointHarness(new EndpointClient(), owner(enrolled.executablePath().toString()));
        var profile = EndpointFixtures.profile(base());

        var outcome = harness.task(profile, enrolled, request(), null);
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
    @DisplayName("a profile whose result schema is none still has its answer validated by the server")
    void noneStillValidated() {
        var enrolled = EndpointFixtures.enrolment();
        var harness = new EndpointHarness(new EndpointClient(), owner(enrolled.executablePath().toString()));
        var profile = EndpointFixtures.profile(base(), "none");
        var schema = JsonDocument.parse(
                "{\"type\":\"object\",\"required\":[\"other\"]}".getBytes(StandardCharsets.UTF_8),
                "schema");

        var outcome = harness.task(profile, enrolled, request(), schema);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason()));
    }

    @Test
    @DisplayName("the readiness check answers true only for a success status")
    void readiness() {
        var enrolled = EndpointFixtures.enrolment();
        var harness = new EndpointHarness(new EndpointClient(), owner(enrolled.executablePath().toString()));

        assertTrue(harness.ready(EndpointFixtures.profile(base())));
    }

    @Test
    @DisplayName("a readiness check that does not answer a success status is not ready")
    void notReady() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse.Builder().code(503).body("{}").build();
            }
        });
        var enrolled = EndpointFixtures.enrolment();
        var harness = new EndpointHarness(new EndpointClient(), owner(enrolled.executablePath().toString()));

        assertFalse(harness.ready(EndpointFixtures.profile(base())));
    }

    @Test
    @DisplayName("a models listing that cannot be read is no listing")
    void unreadableListing() {
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse.Builder().code(500).body("not json").build();
            }
        });
        var enrolled = EndpointFixtures.enrolment();
        var harness = new EndpointHarness(new EndpointClient(), owner(enrolled.executablePath().toString()));

        assertNull(harness.listing(EndpointFixtures.profile(base())));
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
        var enrolled = EndpointFixtures.enrolment();
        var harness = new EndpointHarness(new EndpointClient(), owner(enrolled.executablePath().toString()));

        var listing = harness.listing(EndpointFixtures.profile(base()));

        assertAll(
                () -> assertNotNull(listing),
                () -> assertTrue(listing.isArray()),
                () -> assertEquals(1, listing.elements().size()));
    }
}
