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
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
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
import de.planmarshall.runtime.credentials.TokenLaneCredential;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The remote form of a judgment endpoint (Client Profiles § Remote Form): the Token Lane secret is sent only to the
 * origin of the entry's {@code url}, a redirect to another origin or to {@code http} receives no secret and ends the
 * attempt, and neither the key nor the body reaches a log line or the audit record.
 * <p>
 * Every request is answered by the branch primed per test into the {@link ModuleDispatcherElement} of
 * {@link #getModuleDispatcher()}, keyed by method and request target, so a request for a wrong path or with a wrong
 * method gets no answer; the branch may answer by the request, as the echo test does. The origin comes from the
 * injected {@link URIBuilder} of the HTTPS mock web server. Every record {@link JudgmentEndpoint} logs is asserted by
 * its constant through {@link LogAsserts}. Contract literals, kept by design: the {@code system_one} wire format (its
 * path, the models path and pointer of the profile, the JSON field names {@code model}, {@code answers},
 * {@code choice}, {@code probabilities}, {@code usage}, {@code input_tokens}, {@code output_tokens} and
 * {@code echo}); the status codes, the {@code Authorization}, {@code Location} and {@code Content-Type} headers and the
 * {@code Bearer} scheme; the branch keys {@code a} and {@code b} with probabilities that sum to {@code 1} and that do
 * not; the {@code remote} locality and the {@code example.test} domain of a foreign origin; the reason names. The
 * client id, the model, the credential key and its secret, the facts, the question, the criteria and the token counts
 * are generated.
 */
@EnableMockWebServer(useHttps = true)
@EnableGeneratorController
@EnableTestLogger
@ModuleDispatcher
@DisplayName("The remote form of a judgment endpoint")
class JudgmentEndpointRemoteTest {

    /** The probabilities of a valid answer, which sum to {@code 1} (contract). */
    private static final String VALID_PROBABILITIES = "\"a\":0.75,\"b\":0.25";

    /** Canned answers by {@code METHOD target}; each answers every request to its target. */
    private final Map<String, Function<RecordedRequest, MockResponse>> routes = new ConcurrentHashMap<>();
    private MockWebServer server;
    private URI root;
    private final Capture<AuditEvent> auditEvents = newCapture(CaptureType.ALL);
    private final Capture<String> registeredSecrets = newCapture(CaptureType.ALL);
    private AuditSink audit;
    private SecretRegistry registry;
    private String clientId;
    private String key;
    private String secret;
    private String model;
    private String facts;
    private String questionId;
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
        root = uriBuilder.build();
        audit = createMock(AuditSink.class);
        audit.append(capture(auditEvents));
        expectLastCall().anyTimes();
        replay(audit);
        registry = createMock(SecretRegistry.class);
        registry.register(capture(registeredSecrets));
        expectLastCall().anyTimes();
        replay(registry);
        clientId = word() + "-judge";
        key = word() + "-token";
        secret = word() + "-" + word() + "-" + Generators.integers(1_000, 9_999).next();
        model = word() + "-" + Generators.integers(1, 9_999).next();
        facts = word() + " " + word() + " " + word() + " " + word();
        questionId = "q-" + word();
        inputTokens = Generators.integers(1, 4_000).next();
    }

    private void answerWith(Function<RecordedRequest, MockResponse> branch) {
        routes.put("POST " + SystemOneRequest.PATH, branch);
    }

    /** @return a generated lower-case word, safe in a JSON string */
    private static String word() {
        return Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
    }

    private String origin() {
        return root.getScheme() + "://" + root.getHost() + ":" + root.getPort();
    }

    /** @return the same port under the other loopback name, an origin of its own */
    private String otherOrigin() {
        var other = "localhost".equalsIgnoreCase(root.getHost()) ? "127.0.0.1" : "localhost";
        return root.getScheme() + "://" + other + ":" + root.getPort();
    }

    private EndpointProfile profile() {
        return new EndpointProfile(clientId, null, null, null,
                new EndpointProfile.Endpoint("system_one", "remote", origin(), null, "/v1/models", "/data",
                        "enforced", 8192, 512, 30, key),
                Map.of("standard", model), null);
    }

    private JudgmentEndpoint endpoint(TokenLaneCredential credential) {
        return new JudgmentEndpoint(new JudgmentClient(credential, registry, JudgmentTestTls.trusting()),
                (host, port) -> SocketOwner.Lookup.notFound(word()), audit, Clock.systemUTC());
    }

    private JudgmentEndpoint endpoint() {
        return endpoint(entry -> Optional.of(new TokenLaneCredential.Entry(secret, URI.create(origin()))));
    }

    private HarnessEnrolment enrolment(String enrolledOrigin, String enrolledKey) {
        return HarnessEnrolment.endpoint(clientId, enrolledOrigin, enrolledKey, Instant.EPOCH,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()));
    }

    private HarnessEnrolment enrolment() {
        return enrolment(origin(), key);
    }

    private JsonNode state() {
        return JsonNode.object(Map.of("finding", JsonNode.string(facts)));
    }

    private List<SystemOneRequest.Question> questions() {
        return List.of(new SystemOneRequest.Question(questionId, word() + " " + word() + "?",
                Map.of("a", new SystemOneRequest.Criterion("A", word() + " " + word(), null),
                        "b", new SystemOneRequest.Criterion("B", word() + " " + word(), null))));
    }

    private String answer(String probabilities) {
        return "{\"model\":\"" + model + "\",\"answers\":{\"" + questionId + "\":{\"choice\":\"a\","
                + "\"probabilities\":{" + probabilities + "}}},"
                + "\"usage\":{\"input_tokens\":" + inputTokens + ",\"output_tokens\":3}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }

    private static Function<RecordedRequest, MockResponse> redirect(String location) {
        var response = new MockResponse.Builder().code(302).addHeader("Location", location).build();
        return request -> response;
    }

    private Function<RecordedRequest, MockResponse> validAnswer() {
        var response = json(answer(VALID_PROBABILITIES));
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
                    "no " + level + " line holds the value");
        }
    }

    private static void assertAttemptEndedLogged() {
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.ENDPOINT_ATTEMPT_ENDED.resolveIdentifierString());
    }

    private static void assertLaunchRefusedLogged() {
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                WARN.WORKER_LAUNCH_REFUSED.resolveIdentifierString());
    }

    @Test
    @DisplayName("the entry's own origin answers both requests, each with the bearer secret")
    void ownOriginSucceeds() throws Exception {
        answerWith(validAnswer());

        var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

        var first = nextRequest();
        var second = nextRequest();
        assertAll("the answered remote task",
                () -> assertTrue(answers.answered(), "both requests answered validly"),
                () -> assertEquals(2, server.getRequestCount(), "exactly two requests are sent"),
                () -> assertEquals("Bearer " + secret, first.getHeaders().get("Authorization"),
                        "the first request carries the secret"),
                () -> assertEquals("Bearer " + secret, second.getHeaders().get("Authorization"),
                        "the second request carries the secret"),
                () -> assertTrue(registeredSecrets.getValues().contains(secret), "the secret is registered for redaction"),
                () -> assertEquals(inputTokens, answers.ascending().answer().usage().inputTokens().value(),
                        "the input tokens of the answer are read"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                INFO.JUDGMENT_TASK_ANSWERED.resolveIdentifierString());
        assertNoLogLineHolds(secret);
    }

    @Test
    @DisplayName("a redirect to another origin receives no secret and ends the attempt")
    void redirectToAnotherOrigin() throws Exception {
        answerWith(redirect(otherOrigin() + SystemOneRequest.PATH));

        var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

        var first = nextRequest();
        assertAll("the refused redirect",
                () -> assertFalse(answers.answered(), "a redirected task is not answered"),
                () -> assertEquals(EndpointOutcome.Reason.REDIRECT_REFUSED, answers.ascending().outcome().reason(),
                        "the attempt ends as a refused redirect"),
                () -> assertEquals(EndpointOutcome.Fate.ENDED, answers.ascending().outcome().fate(),
                        "the attempt is ended, not refused"),
                () -> assertEquals(1, server.getRequestCount(), "the redirect target received no request"),
                () -> assertEquals("Bearer " + secret, first.getHeaders().get("Authorization"),
                        "only the own origin received the secret"),
                () -> assertFalse(auditEvents.getValues().toString().contains(secret), "the audit record holds no secret"));
        assertNoLogLineHolds(secret);
        assertAttemptEndedLogged();
    }

    @Test
    @DisplayName("a redirect to http receives no secret and ends the attempt")
    void redirectToHttp() throws Exception {
        try (var cleartext = new MockWebServer()) {
            cleartext.start();
            answerWith(redirect(cleartext.url(SystemOneRequest.PATH).toString()));

            var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

            assertAll("the refused cleartext redirect",
                    () -> assertFalse(answers.answered(), "a redirected task is not answered"),
                    () -> assertEquals(EndpointOutcome.Reason.REDIRECT_REFUSED,
                            answers.ascending().outcome().reason(), "the attempt ends as a refused redirect"),
                    () -> assertEquals(1, server.getRequestCount(), "only the own origin received a request"),
                    () -> assertEquals(0, cleartext.getRequestCount(),
                            "the cleartext target received no request"),
                    () -> assertFalse(auditEvents.getValues().toString().contains(secret), "the audit record holds no secret"));
            assertNoLogLineHolds(secret);
            assertAttemptEndedLogged();
        }
    }

    @Test
    @DisplayName("a program-form enrolment refuses the remote task before a request")
    void refusesAProgramEnrolment() {
        answerWith(validAnswer());

        var answers = endpoint().task(profile(), EndpointFixtures.enrolment(), model, state(), questions());

        assertAll("the refused program enrolment",
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED,
                        answers.ascending().outcome().reason(), "the refusal names the missing enrolment"),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"));
        assertLaunchRefusedLogged();
    }

    @Test
    @DisplayName("an enrolment of another origin refuses the remote task before a request")
    void refusesAnotherOrigin() {
        answerWith(validAnswer());
        var other = enrolment("https://" + word() + ".example.test", key);

        var answers = endpoint().task(profile(), other, model, state(), questions());

        assertAll("the refused foreign origin",
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED,
                        answers.ascending().outcome().reason(), "the refusal names the missing enrolment"),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"));
        assertLaunchRefusedLogged();
    }

    @Test
    @DisplayName("an enrolment of another credential entry refuses the remote task before a request")
    void refusesAnotherCredential() {
        answerWith(validAnswer());
        var other = enrolment(origin(), key + "-" + word());

        var answers = endpoint().task(profile(), other, model, state(), questions());

        assertAll("the refused credential entry",
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED,
                        answers.ascending().outcome().reason(), "the refusal names the missing enrolment"),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"));
        assertLaunchRefusedLogged();
    }

    @Test
    @DisplayName("a missing credential entry ends the attempt before a request is sent")
    void missingCredential() {
        answerWith(validAnswer());

        var answers = endpoint(entry -> Optional.empty()).task(profile(), enrolment(), model, state(), questions());

        assertAll("the missing credential",
                () -> assertFalse(answers.answered(), "a task without credential is not answered"),
                () -> assertEquals(EndpointOutcome.Reason.REQUEST_REFUSED, answers.ascending().outcome().reason(),
                        "the attempt ends as a refused request"),
                () -> assertTrue(answers.ascending().outcome().detail().contains(key),
                        answers.ascending().outcome()::detail),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"));
        assertAttemptEndedLogged();
    }

    @Test
    @DisplayName("an entry for another origin sends no secret")
    void originMismatch() {
        answerWith(validAnswer());
        TokenLaneCredential foreign = entry -> Optional.of(
                new TokenLaneCredential.Entry(secret, URI.create("https://" + word() + ".example.test")));

        var answers = endpoint(foreign).task(profile(), enrolment(), model, state(), questions());

        assertAll("the foreign credential origin",
                () -> assertFalse(answers.answered(), "a task with a foreign entry is not answered"),
                () -> assertEquals(EndpointOutcome.Reason.REQUEST_REFUSED, answers.ascending().outcome().reason(),
                        "the attempt ends as a refused request"),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"),
                () -> assertTrue(registeredSecrets.getValues().isEmpty(), "no secret is registered for redaction"));
        assertNoLogLineHolds(secret);
        assertAttemptEndedLogged();
    }

    @Test
    @DisplayName("an answer that echoes the request body leaves neither the body nor the key in a log or audit line")
    void leavesBodyAndKeyOut() {
        answerWith(request -> json("{\"model\":\"" + model + "\",\"echo\":" + request.getBody().utf8() + ","
                + "\"answers\":{\"" + questionId + "\":{\"choice\":\"a\",\"probabilities\":{\"a\":0.9,\"b\":0.12}}},"
                + "\"usage\":{\"input_tokens\":" + inputTokens + ",\"output_tokens\":3}}"));

        var answers = endpoint().task(profile(), enrolment(), model, state(), questions());

        assertAll("the read-outs without body and key",
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, answers.ascending().outcome().reason(),
                        "probabilities over 1 are an invalid answer"),
                () -> assertFalse(answers.ascending().outcome().detail().contains(facts),
                        "the error text carries no part of the body"),
                () -> assertEquals(1, auditEvents.getValues().size(), "the task leaves exactly one record"),
                () -> assertFalse(auditEvents.getValues().toString().contains(facts), "the audit record holds no facts"),
                () -> assertFalse(auditEvents.getValues().toString().contains(secret), "the audit record holds no secret"),
                () -> assertTrue(auditEvents.getValues().toString().contains(model), "the audit record names the model"));
        assertNoLogLineHolds(facts);
        assertNoLogLineHolds(secret);
        assertAttemptEndedLogged();
    }
}
