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

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.TestLoggerFactory;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import java.net.Inet6Address;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.runtime.credentials.SecretRegistry;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * A task of a judgment role (Client Profiles § Judgment Endpoint): the two requests in opposite orders, the answer's
 * model id, a {@code 429} that ends the attempt without a retry, the probabilities that must sum to {@code 1}, and the
 * rule that no body and no credential reaches a log line or the audit record.
 * <p>
 * Every request is answered by the branch primed per test into the {@link ModuleDispatcherElement} of
 * {@link #getModuleDispatcher()}, keyed by method and request target, so a request for a wrong path or with a wrong
 * method gets no answer; the branch may answer by the request, as the echo test does. The port comes from the injected
 * {@link URIBuilder}; the host is the loopback literal of the address the server is bound to. Every record
 * {@link JudgmentEndpoint} logs is asserted by its constant through {@link LogAsserts}. Contract literals, kept by
 * design: the {@code system_one} wire format (its path, the models path and pointer of the profile, the JSON field
 * names {@code model}, {@code answers}, {@code choice}, {@code probabilities}, {@code usage}, {@code input_tokens},
 * {@code output_tokens} and {@code echo}); the status codes, the {@code Retry-After} header and the JSON media type;
 * the branch keys {@code a} and {@code b}, whose order the two requests reverse; the probabilities that sum to
 * {@code 1} and those that do not; the profile's context budget and the size of an entity that exceeds it; the status
 * and reason names, the audit event name and the wording of the details. The client id, the executable, the models,
 * the facts, the question, the criteria and the token counts are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@EnableTestLogger
@ModuleDispatcher
@DisplayName("A judgment task")
class JudgmentEndpointTest {

    /** The probabilities of a valid answer, which sum to {@code 1} (contract). */
    private static final String VALID_PROBABILITIES = "\"a\":0.75,\"b\":0.25";

    /** Canned answers by {@code METHOD target}; each answers every request to its target. */
    private final Map<String, Function<RecordedRequest, MockResponse>> routes = new ConcurrentHashMap<>();
    private MockWebServer server;
    private final Capture<AuditEvent> auditEvents = newCapture(CaptureType.ALL);
    private AuditSink audit;
    private String base;
    private String clientId;
    private String model;
    private String facts;
    private String questionId;
    private String question;
    private long inputTokens;

    /** Answers a POST through the branch primed for its target; anything else gets none. */
    ModuleDispatcherElement getModuleDispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return "/";
            }

            @Override
            public Optional<MockResponse> handlePost(RecordedRequest request) {
                return Optional.ofNullable(routes.get(request.getMethod() + " " + request.getTarget()))
                        .map(branch -> branch.apply(request));
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.POST);
            }
        };
    }

    @BeforeEach
    void start(MockWebServer mockWebServer, URIBuilder uriBuilder) {
        server = mockWebServer;
        audit = createMock(AuditSink.class);
        audit.append(capture(auditEvents));
        expectLastCall().anyTimes();
        replay(audit);
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        base = "http://" + host + ":" + uriBuilder.build().getPort();
        clientId = word() + "-judge";
        model = word() + "-" + Generators.integers(1, 9_999).next();
        facts = sentence();
        questionId = "q-" + word();
        question = sentence() + "?";
        inputTokens = Generators.integers(1, 4_000).next();
    }

    private void answerWith(Function<RecordedRequest, MockResponse> branch) {
        routes.put("POST " + SystemOneRequest.PATH, branch);
    }

    /** @return a generated lower-case word, safe in a JSON string */
    private static String word() {
        return Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a generated text of a few words */
    private static String sentence() {
        return word() + " " + word() + " " + word() + " " + word();
    }

    private EndpointProfile profile() {
        return new EndpointProfile(clientId, word(), null, null,
                new EndpointProfile.Endpoint("system_one", "loopback", base, null, "/v1/models", "/data",
                        "enforced", 8192, 512, 30, null),
                Map.of("standard", model), null);
    }

    private JudgmentEndpoint endpoint() {
        return endpoint((host, port) -> SocketOwner.Lookup.found(EndpointFixtures.executablePath()));
    }

    private JudgmentEndpoint endpoint(SocketOwner owner) {
        return new JudgmentEndpoint(new JudgmentClient(key -> Optional.empty(), SecretRegistry.none()), owner, audit,
                Clock.systemUTC());
    }

    private HarnessEnrolment enrolment() {
        return EndpointFixtures.enrolled(clientId, EndpointFixtures.executablePath());
    }

    private JsonNode state() {
        return JsonNode.object(Map.of("finding", JsonNode.string(facts)));
    }

    private List<SystemOneRequest.Question> questions() {
        return List.of(new SystemOneRequest.Question(questionId, question,
                Map.of("b", new SystemOneRequest.Criterion("B", sentence(), null),
                        "a", new SystemOneRequest.Criterion("A", sentence(), null))));
    }

    private String answer(String answeringModel, String probabilities) {
        return "{\"model\":\"" + answeringModel + "\",\"answers\":{\"" + questionId + "\":{\"choice\":\"a\","
                + "\"probabilities\":{" + probabilities + "}}},"
                + "\"usage\":{\"input_tokens\":" + inputTokens + ",\"output_tokens\":"
                + Generators.integers(1, 9).next() + "}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }

    private static Function<RecordedRequest, MockResponse> fixed(MockResponse response) {
        return request -> response;
    }

    private RecordedRequest nextRequest() throws InterruptedException {
        var recorded = server.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded, "a request reached the mock web server");
        return recorded;
    }

    private static void assertNoLogLineHolds(String value) {
        for (var level : TestLogLevel.values()) {
            assertTrue(TestLoggerFactory.getTestHandler().resolveLogMessagesContaining(level, value).isEmpty(),
                    "no " + level + " line holds the task text");
        }
    }

    @Test
    @DisplayName("both requests are sent, the first sorted by branch_key and the second in the opposite order")
    void bothOrders() throws Exception {
        answerWith(fixed(json(answer(model, VALID_PROBABILITIES))));

        var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

        var first = nextRequest();
        var second = nextRequest();
        var ascending = first.getBody().utf8();
        var descending = second.getBody().utf8();
        assertAll("the two orders",
                () -> assertTrue(answers.answered(), "both requests answered validly"),
                () -> assertEquals(2, server.getRequestCount(), "exactly two requests are sent"),
                () -> assertTrue(ascending.indexOf("\"a\":") < ascending.indexOf("\"b\":"),
                        "the first request sorts the branch keys"),
                () -> assertTrue(descending.indexOf("\"b\":") < descending.indexOf("\"a\":"),
                        "the second request reverses the order"),
                () -> assertEquals(SystemOneRequest.PATH, first.getTarget(), "the system_one path is requested"),
                () -> assertEquals("a", answers.ascending().answer().answers().get(questionId).choice(),
                        "the choice of the answer is read"),
                () -> assertEquals("succeeded", auditEvents.getValues().getFirst().outcome(), "an answered task is a succeeded record"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.JUDGMENT_TASK_ANSWERED.resolveIdentifierString());
    }

    @Test
    @DisplayName("an answer of another model id is refused model_alias and no second request is sent")
    void refusesAnAlias() {
        answerWith(fixed(json(answer(model + "-" + word(), VALID_PROBABILITIES))));

        var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

        assertAll("the refused alias",
                () -> assertFalse(answers.answered(), "an aliased answer is no answer"),
                () -> assertEquals(EndpointOutcome.Reason.MODEL_ALIAS, answers.ascending().outcome().reason(),
                        "the attempt ends as a model alias"),
                () -> assertEquals(EndpointOutcome.Fate.ENDED, answers.ascending().outcome().fate(),
                        "the attempt is ended, not refused"),
                () -> assertEquals(1, server.getRequestCount(), "no second request is sent"),
                () -> assertEquals("model_alias", answers.status(), "the task status names the alias"),
                () -> assertFalse(answers.ascending().outcome().detail().contains(facts),
                        "the error text carries no part of the body"),
                () -> assertTrue(answers.ascending().outcome().detail().contains("another model id"),
                        answers.ascending().outcome()::detail),
                () -> assertEquals("failed", auditEvents.getValues().getFirst().outcome(), "an aliased task is a failed record"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.ENDPOINT_ATTEMPT_ENDED.resolveIdentifierString());
    }

    @Test
    @DisplayName("a 429 with a retry-after ends the attempt and is not retried inside it")
    void endsARateLimitWithoutRetry() {
        answerWith(fixed(new MockResponse.Builder().code(429)
                .addHeader("Retry-After", String.valueOf(Generators.integers(1, 120).next()))
                .body("{\"error\":\"" + word() + "\"}").build()));

        var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

        assertAll("the rate-limited attempt",
                () -> assertFalse(answers.answered(), "a rate-limited task is not answered"),
                () -> assertEquals(EndpointOutcome.Reason.HTTP_ERROR, answers.ascending().outcome().reason(),
                        "a 429 ends the attempt as an HTTP error"),
                () -> assertEquals(1, server.getRequestCount(), "the runtime retries nothing inside an attempt"),
                () -> assertTrue(answers.ascending().outcome().detail().contains("429"),
                        answers.ascending().outcome()::detail));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.ENDPOINT_ATTEMPT_ENDED.resolveIdentifierString());
    }

    @Test
    @DisplayName("an answer whose probabilities do not sum to 1 is an invalid answer")
    void refusesProbabilitiesThatDoNotSum() {
        answerWith(fixed(json(answer(model, "\"a\":0.9,\"b\":0.12"))));

        var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

        assertAll("the invalid probabilities",
                () -> assertFalse(answers.answered(), "an invalid answer is no answer"),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, answers.ascending().outcome().reason(),
                        "the attempt ends as an invalid answer"),
                () -> assertEquals(1, server.getRequestCount(), "no second request is sent"),
                () -> assertEquals("invalid_answer", answers.status(), "the task status names the invalid answer"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.ENDPOINT_ATTEMPT_ENDED.resolveIdentifierString());
    }

    @Test
    @DisplayName("an answer that echoes the request body leaves neither the body nor the key in a log or audit line")
    void leavesTheBodyOutOfLogsAndAudit() {
        answerWith(request -> json("{\"model\":\"" + model + "\",\"echo\":" + request.getBody().utf8() + ","
                + "\"answers\":{\"" + questionId + "\":{\"choice\":\"a\",\"probabilities\":{" + VALID_PROBABILITIES
                + "}}},\"usage\":{\"input_tokens\":" + inputTokens + ",\"output_tokens\":3}}"));

        var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

        assertAll("the read-outs without the body",
                () -> assertTrue(answers.answered(), "the echoed answer is valid, so only the read-outs are at stake"),
                () -> assertEquals(1, auditEvents.getValues().size(), "the task leaves exactly one record"),
                () -> assertFalse(auditEvents.getValues().toString().contains(facts), "the audit record holds no facts"),
                () -> assertFalse(auditEvents.getValues().toString().contains(question), "the audit record holds no question"),
                () -> assertTrue(auditEvents.getValues().toString().contains("judgment_task_completed"),
                        "the audit record names its event"),
                () -> assertTrue(auditEvents.getValues().toString().contains(model), "the audit record names the model"));
        assertNoLogLineHolds(facts);
        assertNoLogLineHolds(question);
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.JUDGMENT_TASK_ANSWERED.resolveIdentifierString());
    }

    @Test
    @DisplayName("a program whose build is not the enrolled one refuses the task before a request")
    void refusesAnotherBuild() {
        answerWith(fixed(json(answer(model, VALID_PROBABILITIES))));
        var previousBuild = EndpointFixtures.enrolment(EndpointFixtures.executablePath());

        var answers = endpoint().task(profile(), previousBuild, model, state(), questions());

        assertAll("the refused build",
                () -> assertFalse(answers.answered(), "a refused task is not answered"),
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED,
                        answers.ascending().outcome().reason(), "the refusal names the missing enrolment"),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.WORKER_LAUNCH_REFUSED.resolveIdentifierString());
    }

    @Test
    @DisplayName("a program on the port that is not the enrolled one refuses the task before a request")
    void refusesAForeignProgram() {
        answerWith(fixed(json(answer(model, VALID_PROBABILITIES))));
        var foreign = endpoint((host, port) -> SocketOwner.Lookup.found(EndpointFixtures.FOREIGN_EXECUTABLE));

        var answers = foreign.task(profile(), enrolment(), model, state(), questions());

        assertAll("the refused foreign program",
                () -> assertFalse(answers.answered(), "a refused task is not answered"),
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED,
                        answers.ascending().outcome().reason(), "the refusal names the missing enrolment"),
                () -> assertEquals(0, server.getRequestCount(),
                        "no task text reaches an unenrolled program"),
                () -> assertEquals("harness_not_enrolled", answers.status(), "the task status names the refusal"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.WORKER_LAUNCH_REFUSED.resolveIdentifierString());
    }

    @Test
    @DisplayName("an entity that does not fit the context budget closes the task before a request")
    void refusesAnEntityThatDoesNotFit() {
        answerWith(fixed(json(answer(model, VALID_PROBABILITIES))));
        // 40 000 characters exceed the context budget of 8192 tokens of the profile (contract)
        var huge = JsonNode.object(Map.of("finding", JsonNode.string("x".repeat(40_000))));

        var answers = endpoint().task(profile(), enrolment(), model, huge, questions());

        assertAll("the oversized entity",
                () -> assertFalse(answers.answered(), "an oversized task is not answered"),
                () -> assertEquals(EndpointOutcome.Reason.INPUT_TOO_LARGE, answers.ascending().outcome().reason(),
                        "the refusal names the oversized input"),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"),
                () -> assertEquals("input_too_large", answers.status(), "the task status names the size"));
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, JudgmentEndpoint.class);
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO, JudgmentEndpoint.class);
    }
}
