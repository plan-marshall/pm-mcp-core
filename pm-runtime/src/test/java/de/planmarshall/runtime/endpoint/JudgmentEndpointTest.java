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

/**
 * A task of a judgment role (Client Profiles § Judgment Endpoint): the two requests in opposite orders, the answer's
 * model id, a {@code 429} that ends the attempt without a retry, the probabilities that must sum to {@code 1}, and the
 * rule that no body and no credential reaches a log line or the audit record.
 */
@EnableMockWebServer
@DisplayName("A judgment task")
class JudgmentEndpointTest {

    private static final String BASE_TASK_STATE = "the build failed twice on the module pm-core";
    private static final String QUESTION = "Which reading of the failure holds?";

    private MockWebServer server;
    private RecordingAuditSink audit;
    private RecordingLogHandler logs;
    private final List<RecordedRequest> requests = new ArrayList<>();
    private volatile Responder responder;

    @BeforeEach
    void start(MockWebServer mockWebServer) {
        server = mockWebServer;
        audit = new RecordingAuditSink();
        logs = new RecordingLogHandler();
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

    private String base() {
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        return "http://" + host + ":" + server.getPort();
    }

    private EndpointProfile profile() {
        return new EndpointProfile("local-judge", "ollama", null, null,
                new EndpointProfile.Endpoint("system_one", "loopback", base(), null, "/v1/models", "/data",
                        "enforced", 8192, 512, 30, null),
                Map.of("standard", "judge-2026-01-01"), null);
    }

    private JudgmentEndpoint endpoint() {
        return new JudgmentEndpoint(new JudgmentClient(key -> Optional.empty(), SecretRegistry.none()), audit,
                Clock.systemUTC());
    }

    private static JsonNode state() {
        return JsonNode.object(Map.of("finding", JsonNode.string(BASE_TASK_STATE)));
    }

    private static List<SystemOneRequest.Question> questions() {
        return List.of(new SystemOneRequest.Question("q-1", QUESTION,
                Map.of("b", new SystemOneRequest.Criterion("B", "The second reading", null),
                        "a", new SystemOneRequest.Criterion("A", "The first reading", null))));
    }

    private static String answer(String model) {
        return "{\"model\":\"" + model + "\",\"answers\":{\"q-1\":{\"choice\":\"a\","
                + "\"probabilities\":{\"a\":0.75,\"b\":0.25}}},"
                + "\"usage\":{\"input_tokens\":120,\"output_tokens\":3}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }

    @Test
    @DisplayName("both requests are sent, the first sorted by branch_key and the second in the opposite order")
    void bothOrders() throws Exception {
        responder = request -> json(answer("judge-2026-01-01"));

        var answers = endpoint().task(profile(), "judge-2026-01-01", state(), questions());

        var first = server.takeRequest(2, TimeUnit.SECONDS);
        var second = server.takeRequest(2, TimeUnit.SECONDS);
        assertAll(
                () -> assertTrue(answers.answered()),
                () -> assertEquals(2, requests.size()),
                () -> assertTrue(first.getBody().utf8().indexOf("\"a\":") < first.getBody().utf8()
                        .indexOf("\"b\":")),
                () -> assertTrue(second.getBody().utf8().indexOf("\"b\":") < second.getBody().utf8()
                        .indexOf("\"a\":")),
                () -> assertEquals("/v1/systemone", first.getTarget()),
                () -> assertEquals("a", answers.ascending().answer().answers().get("q-1").choice()));
    }

    @Test
    @DisplayName("an answer of another model id is refused model_alias and no second request is sent")
    void refusesAnAlias() {
        responder = request -> json(answer("judge-2026-02-01"));

        var answers = endpoint().task(profile(), "judge-2026-01-01", state(), questions());

        assertAll(
                () -> assertFalse(answers.answered()),
                () -> assertEquals(EndpointOutcome.Reason.MODEL_ALIAS, answers.ascending().outcome().reason()),
                () -> assertEquals(EndpointOutcome.Fate.ENDED, answers.ascending().outcome().fate()),
                () -> assertEquals(1, requests.size()),
                () -> assertEquals("model_alias", answers.status()),
                () -> assertEquals("failed", audit.only().outcome()));
    }

    @Test
    @DisplayName("a 429 with a retry-after ends the attempt and is not retried inside it")
    void endsARateLimitWithoutRetry() {
        responder = request -> new MockResponse.Builder().code(429).addHeader("Retry-After", "30")
                .body("{\"error\":\"rate limited\"}").build();

        var answers = endpoint().task(profile(), "judge-2026-01-01", state(), questions());

        assertAll(
                () -> assertFalse(answers.answered()),
                () -> assertEquals(EndpointOutcome.Reason.HTTP_ERROR, answers.ascending().outcome().reason()),
                () -> assertEquals(1, requests.size(), "the runtime retries nothing inside an attempt"),
                () -> assertTrue(answers.ascending().outcome().detail().contains("429")));
    }

    @Test
    @DisplayName("an answer whose probabilities do not sum to 1 is an invalid answer")
    void refusesProbabilitiesThatDoNotSum() {
        responder = request -> json(answer("judge-2026-01-01").replace("\"a\":0.75,\"b\":0.25",
                "\"a\":0.9,\"b\":0.12"));

        var answers = endpoint().task(profile(), "judge-2026-01-01", state(), questions());

        assertAll(
                () -> assertFalse(answers.answered()),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, answers.ascending().outcome().reason()),
                () -> assertEquals(1, requests.size()),
                () -> assertEquals("invalid_answer", answers.status()));
    }

    @Test
    @DisplayName("an answer that echoes the request body leaves neither the body nor the key in a log or audit line")
    void leavesTheBodyOutOfLogsAndAudit() {
        responder = request -> json("{\"model\":\"judge-2026-01-01\",\"echo\":" + request.getBody().utf8() + ","
                + "\"answers\":{\"q-1\":{\"choice\":\"a\",\"probabilities\":{\"a\":0.75,\"b\":0.25}}},"
                + "\"usage\":{\"input_tokens\":120,\"output_tokens\":3}}");

        endpoint().task(profile(), "judge-2026-01-01", state(), questions());

        assertAll(
                () -> assertEquals(1, audit.events().size()),
                () -> assertFalse(audit.text().contains(BASE_TASK_STATE)),
                () -> assertFalse(audit.text().contains(QUESTION)),
                () -> assertFalse(logs.text().contains(BASE_TASK_STATE)),
                () -> assertFalse(logs.text().contains(QUESTION)),
                () -> assertTrue(audit.text().contains("judgment_task_completed")),
                () -> assertTrue(audit.text().contains("judge-2026-01-01")));
    }

    /** How the server answers the request. */
    @FunctionalInterface
    interface Responder {
        MockResponse answer(RecordedRequest request);
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

        AuditEvent only() {
            return events.getFirst();
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
