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
import java.net.URI;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.credentials.SecretRegistry;
import de.planmarshall.runtime.credentials.TokenLaneCredential;

/**
 * The remote form of a judgment endpoint (Client Profiles § Remote Form): the Token Lane secret is sent only to the
 * origin of the entry's {@code url}, a redirect to another origin or to {@code http} receives no secret and ends the
 * attempt, and neither the key nor the body reaches a log line or the audit record.
 */
@EnableMockWebServer(useHttps = true)
@DisplayName("The remote form of a judgment endpoint")
class JudgmentEndpointRemoteTest {

    private static final String SECRET = "s3cr3t-judge-token";
    private static final String KEY = "judge-token";
    private static final String MODEL = "judge-2026-01-01";
    private static final String STATE = "the build failed twice on the module pm-core";

    private MockWebServer server;
    private RecordingAuditSink audit;
    private RecordingLogHandler logs;
    private RecordingRegistry registry;
    private final List<RecordedRequest> requests = new ArrayList<>();
    private volatile Responder responder;

    @BeforeEach
    void start(MockWebServer mockWebServer) {
        server = mockWebServer;
        audit = new RecordingAuditSink();
        logs = new RecordingLogHandler();
        registry = new RecordingRegistry();
        Logger.getLogger("").addHandler(logs);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requests.add(request);
                return responder.answer(request);
            }
        });
    }

    @AfterEach
    void stop() {
        Logger.getLogger("").removeHandler(logs);
    }

    private String origin() {
        return server.url("/").toString().replaceAll("/$", "");
    }

    private EndpointProfile profile() {
        return new EndpointProfile("hosted-judge", null, null, null,
                new EndpointProfile.Endpoint("system_one", "remote", origin(), null, "/v1/models", "/data",
                        "enforced", 8192, 512, 30, KEY),
                Map.of("standard", MODEL), null);
    }

    private JudgmentEndpoint endpoint(TokenLaneCredential credential) {
        return new JudgmentEndpoint(new JudgmentClient(credential, registry, JudgmentTestTls.trusting()), audit,
                Clock.systemUTC());
    }

    private JudgmentEndpoint endpoint() {
        return endpoint(key -> Optional.of(new TokenLaneCredential.Entry(SECRET, URI.create(origin()))));
    }

    private static JsonNode state() {
        return JsonNode.object(Map.of("finding", JsonNode.string(STATE)));
    }

    private static List<SystemOneRequest.Question> questions() {
        return List.of(new SystemOneRequest.Question("q-1", "Which reading of the failure holds?",
                Map.of("a", new SystemOneRequest.Criterion("A", "The first reading", null),
                        "b", new SystemOneRequest.Criterion("B", "The second reading", null))));
    }

    private static String answer() {
        return "{\"model\":\"" + MODEL + "\",\"answers\":{\"q-1\":{\"choice\":\"a\","
                + "\"probabilities\":{\"a\":0.75,\"b\":0.25}}},"
                + "\"usage\":{\"input_tokens\":120,\"output_tokens\":3}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }

    private static MockResponse redirect(String location) {
        return new MockResponse.Builder().code(302).addHeader("Location", location).build();
    }

    @Test
    @DisplayName("the entry's own origin answers both requests, each with the bearer secret")
    void ownOriginSucceeds() throws Exception {
        responder = request -> json(answer());

        var answers = endpoint().task(profile(), MODEL, state(), questions());

        var first = server.takeRequest(2, TimeUnit.SECONDS);
        assertAll(
                () -> assertTrue(answers.answered()),
                () -> assertEquals(2, requests.size()),
                () -> assertEquals("Bearer " + SECRET, first.getHeaders().get("Authorization")),
                () -> assertTrue(registry.secrets().contains(SECRET)),
                () -> assertEquals(120, answers.ascending().answer().usage().inputTokens().value()));
    }

    @Test
    @DisplayName("a redirect to another origin receives no secret and ends the attempt")
    void redirectToAnotherOrigin() {
        var other = "https://127.0.0.1:" + server.getPort() + "/v1/systemone";
        responder = request -> redirect(other);

        var answers = endpoint().task(profile(), MODEL, state(), questions());

        assertAll(
                () -> assertFalse(answers.answered()),
                () -> assertEquals(EndpointOutcome.Reason.REDIRECT_REFUSED, answers.ascending().outcome().reason()),
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, answers.ascending().outcome().fate()),
                () -> assertEquals(1, requests.size(), "the redirect target received no request"),
                () -> assertEquals("Bearer " + SECRET, requests.getFirst().getHeaders().get("Authorization")),
                () -> assertFalse(logs.text().contains(SECRET)),
                () -> assertFalse(audit.text().contains(SECRET)));
    }

    @Test
    @DisplayName("a redirect to http receives no secret and ends the attempt")
    void redirectToHttp() {
        var cleartext = "http://localhost:" + server.getPort() + "/v1/systemone";
        responder = request -> redirect(cleartext);

        var answers = endpoint().task(profile(), MODEL, state(), questions());

        assertAll(
                () -> assertFalse(answers.answered()),
                () -> assertEquals(EndpointOutcome.Reason.REDIRECT_REFUSED, answers.ascending().outcome().reason()),
                () -> assertEquals(1, requests.size()),
                () -> assertFalse(logs.text().contains(SECRET)),
                () -> assertFalse(audit.text().contains(SECRET)));
    }

    @Test
    @DisplayName("a missing credential entry ends the attempt before a request is sent")
    void missingCredential() {
        responder = request -> json(answer());

        var answers = endpoint(key -> Optional.empty()).task(profile(), MODEL, state(), questions());

        assertAll(
                () -> assertFalse(answers.answered()),
                () -> assertEquals(EndpointOutcome.Reason.REQUEST_REFUSED, answers.ascending().outcome().reason()),
                () -> assertTrue(answers.ascending().outcome().detail().contains(KEY)),
                () -> assertEquals(0, requests.size()));
    }

    @Test
    @DisplayName("an entry for another origin sends no secret")
    void originMismatch() {
        responder = request -> json(answer());
        var foreign = (TokenLaneCredential) key -> Optional.of(
                new TokenLaneCredential.Entry(SECRET, URI.create("https://other.example.test")));

        var answers = endpoint(foreign).task(profile(), MODEL, state(), questions());

        assertAll(
                () -> assertFalse(answers.answered()),
                () -> assertEquals(EndpointOutcome.Reason.REQUEST_REFUSED, answers.ascending().outcome().reason()),
                () -> assertEquals(0, requests.size()),
                () -> assertTrue(registry.secrets().isEmpty()));
    }

    @Test
    @DisplayName("an answer that echoes the request body leaves neither the body nor the key in a log or audit line")
    void leavesBodyAndKeyOut() {
        responder = request -> json("{\"model\":\"" + MODEL + "\",\"echo\":" + request.getBody().utf8() + ","
                + "\"answers\":{\"q-1\":{\"choice\":\"a\",\"probabilities\":{\"a\":0.9,\"b\":0.12}}},"
                + "\"usage\":{\"input_tokens\":120,\"output_tokens\":3}}");

        var answers = endpoint().task(profile(), MODEL, state(), questions());

        assertAll(
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, answers.ascending().outcome().reason()),
                () -> assertEquals(1, audit.events().size()),
                () -> assertFalse(audit.text().contains(STATE)),
                () -> assertFalse(logs.text().contains(STATE)),
                () -> assertFalse(audit.text().contains(SECRET)),
                () -> assertFalse(logs.text().contains(SECRET)),
                () -> assertTrue(audit.text().contains(MODEL)));
    }

    /** How the server answers the request. */
    @FunctionalInterface
    interface Responder {
        MockResponse answer(RecordedRequest request);
    }

    /** The secrets the runtime registered with the redaction registry. */
    static final class RecordingRegistry implements SecretRegistry {

        private final List<String> secrets = new ArrayList<>();

        @Override
        public void register(String secret) {
            secrets.add(secret);
        }

        List<String> secrets() {
            return List.copyOf(secrets);
        }
    }

    /** The audit records of the test. */
    static final class RecordingAuditSink implements AuditSink {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public void append(AuditEvent event) {
            events.add(event);
        }

        List<AuditEvent> events() {
            return List.copyOf(events);
        }

        String text() {
            return events.toString();
        }
    }

    /** The log lines of the runtime, captured at the root logger. */
    static final class RecordingLogHandler extends Handler {

        private final List<String> lines = new ArrayList<>();
        private final SimpleFormatter formatter = new SimpleFormatter();

        @Override
        public void publish(LogRecord record) {
            lines.add(formatter.format(record));
        }

        @Override
        public void flush() {
            // Nothing is buffered.
        }

        @Override
        public void close() {
            // Nothing is held.
        }

        String text() {
            return String.join("\n", lines);
        }
    }
}
