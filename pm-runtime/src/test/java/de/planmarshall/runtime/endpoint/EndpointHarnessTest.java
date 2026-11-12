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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import java.net.Inet6Address;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/**
 * One task on an endpoint harness (PM-CLIENT-2, Client Profiles § The Request, § Enrolment): the owner check refuses a
 * program that is not the enrolled one before any task text is sent, and an accepted answer carries its usage.
 * <p>
 * Every request is answered by the response primed per test into the {@link ModuleDispatcherElement} of
 * {@link #getModuleDispatcher()}, keyed by method and request target, so a request for a wrong path or with a wrong
 * method gets no answer. The port comes from the injected {@link URIBuilder}; the host is the loopback literal of the
 * address the server is bound to, because the client admits only the loopback literals. Contract literals, kept by
 * design: the model-endpoint wire format (the chat path, the readiness and models paths of the fixture profile, the
 * JSON field names {@code choices}, {@code message}, {@code content}, {@code usage}, {@code prompt_tokens},
 * {@code completion_tokens}, {@code models}, {@code id}, {@code stream} and {@code tools}); the status codes; the
 * client id and the result schema modes of the fixture profile; the wording of the refusal details. The launch
 * prompt, the task, the model, the answer, the token counts, the listed model and the detail of an owner lookup are
 * generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("Endpoint harness")
class EndpointHarnessTest {

    /** Canned responses by {@code METHOD target}; each answers every request to its target. */
    private final Map<String, MockResponse> routes = new ConcurrentHashMap<>();
    private MockWebServer server;
    private String base;
    private String model;
    private String verdict;
    private int promptTokens;
    private int completionTokens;

    /** Answers a GET or POST with the response primed for its method and target; anything else gets none. */
    ModuleDispatcherElement getModuleDispatcher() {
        return new ModuleDispatcherElement() {
            @Override
            public String getBaseUrl() {
                return "/";
            }

            @Override
            public Optional<MockResponse> handleGet(RecordedRequest request) {
                return answer(request);
            }

            @Override
            public Optional<MockResponse> handlePost(RecordedRequest request) {
                return answer(request);
            }

            @Override
            public Set<HttpMethodMapper> supportedMethods() {
                return Set.of(HttpMethodMapper.GET, HttpMethodMapper.POST);
            }
        };
    }

    private Optional<MockResponse> answer(RecordedRequest request) {
        return Optional.ofNullable(routes.get(request.getMethod() + " " + request.getTarget()));
    }

    @BeforeEach
    void start(MockWebServer mockWebServer, URIBuilder uriBuilder) {
        server = mockWebServer;
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        base = "http://" + host + ":" + uriBuilder.build().getPort();
        model = word();
        verdict = word();
        promptTokens = Generators.integers(1, 500).next();
        completionTokens = Generators.integers(1, 500).next();
    }

    private void on(String method, String target, int status, String body) {
        routes.put(method + " " + target, new MockResponse.Builder().code(status).body(body).build());
    }

    /** @return a generated lower-case word, safe in a URL, a JSON string and a host label */
    private static String word() {
        return Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return the content of the answer: a JSON object naming the generated verdict */
    private String content() {
        return "{\"verdict\":\"" + verdict + "\"}";
    }

    /** @return the chat answer of the protocol, carrying the content and the generated usage */
    private String chatAnswer() {
        return "{\"choices\":[{\"message\":{\"content\":\"" + content().replace("\"", "\\\"") + "\"}}],"
                + "\"usage\":{\"prompt_tokens\":" + promptTokens + ",\"completion_tokens\":" + completionTokens + "}}";
    }

    private EndpointRequest request() {
        return EndpointRequest.compose(EndpointFixtures.profile(EndpointFixtures.BASE_URL), model, word(), word(),
                null).request();
    }

    private static SocketOwner owner(String executablePath) {
        return (host, port) -> SocketOwner.Lookup.found(executablePath);
    }

    private static EndpointHarness harness(SocketOwner owner) {
        return new EndpointHarness(new EndpointClient(), owner);
    }

    private static EndpointHarness enrolledHarness() {
        return harness(owner(EndpointFixtures.enrolment().executablePath().toString()));
    }

    @Test
    @DisplayName("another program on the port refuses the task before any text is sent")
    void anotherProgramRefused() {
        on("POST", EndpointClient.CHAT_PATH, 200, chatAnswer());

        var outcome = harness(owner(EndpointFixtures.FOREIGN_EXECUTABLE))
                .task(EndpointFixtures.profile(base), EndpointFixtures.enrolment(), request(), null);

        assertAll("the refused foreign program",
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, outcome.fate(), outcome::detail),
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, outcome.reason(),
                        "the refusal names the missing enrolment"),
                () -> assertTrue(outcome.detail().contains("the enrolled executable"), outcome::detail),
                () -> assertEquals(0, server.getRequestCount(), "no task text reached the server"));
    }

    @Test
    @DisplayName("a socket whose owner cannot be read refuses the task")
    void unknownOwnerRefused() {
        on("POST", EndpointClient.CHAT_PATH, 200, chatAnswer());
        var detail = word() + " " + word();

        var outcome = harness((host, port) -> SocketOwner.Lookup.notFound(detail))
                .task(EndpointFixtures.profile(base), EndpointFixtures.enrolment(), request(), null);

        assertAll("the refused unknown owner",
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, outcome.fate(), outcome::detail),
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, outcome.reason(),
                        "the refusal names the missing enrolment"),
                () -> assertTrue(outcome.detail().contains(detail), outcome::detail),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"));
    }

    @Test
    @DisplayName("another build at the enrolled path refuses the task")
    void changedDigestRefused() {
        on("POST", EndpointClient.CHAT_PATH, 200, chatAnswer());
        var previousBuild = EndpointFixtures.enrolment(EndpointFixtures.executablePath());

        var outcome = harness(owner(EndpointFixtures.executablePath()))
                .task(EndpointFixtures.profile(base), previousBuild, request(), null);

        assertAll("the refused changed build",
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, outcome.fate(), outcome::detail),
                () -> assertEquals(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, outcome.reason(),
                        "the refusal names the missing enrolment"),
                () -> assertTrue(outcome.detail().contains("not the build the enrolment recorded"), outcome::detail),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"));
    }

    @Test
    @DisplayName("the enrolled program answers, and the request went out with streaming off")
    void acceptedAnswer() throws Exception {
        on("POST", EndpointClient.CHAT_PATH, 200, chatAnswer());

        var outcome = enrolledHarness().task(EndpointFixtures.profile(base), EndpointFixtures.enrolment(), request(),
                null);
        var recorded = server.takeRequest(2, TimeUnit.SECONDS);

        assertNotNull(recorded, "a request reached the mock web server");
        var body = recorded.getBody().utf8();
        assertAll("the accepted answer",
                () -> assertEquals(EndpointOutcome.Fate.ANSWERED, outcome.fate(), outcome::detail),
                () -> assertEquals(content(), outcome.content(), "the content of the answer is delivered"),
                () -> assertEquals(promptTokens + completionTokens, outcome.usage().totalTokens().orElseThrow(),
                        "the usage of the answer is the token sum"),
                () -> assertEquals(EndpointClient.CHAT_PATH, recorded.getTarget(), "the task goes to the chat path"),
                () -> assertTrue(body.contains("\"model\":\"" + model + "\""), "the request names the model"),
                () -> assertTrue(body.contains("\"stream\":false"), "the request switches streaming off"),
                () -> assertFalse(body.contains("\"tools\""), "the request declares no tools at all"));
    }

    @Test
    @DisplayName("a profile whose result schema is none still has its answer validated by the server")
    void noneStillValidated() {
        on("POST", EndpointClient.CHAT_PATH, 200, chatAnswer());
        var schema = JsonDocument.parse(("{\"type\":\"object\",\"required\":[\"missing_" + word() + "\"]}")
                .getBytes(StandardCharsets.UTF_8), "schema");

        var outcome = enrolledHarness().task(EndpointFixtures.profile(base, "none"), EndpointFixtures.enrolment(),
                request(), schema);

        assertAll("the validated answer",
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate(), outcome::detail),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason(),
                        "an answer outside the schema is invalid"));
    }

    @Test
    @DisplayName("the readiness check answers true only for a success status")
    void readiness() throws Exception {
        var profile = EndpointFixtures.profile(base);
        on("GET", profile.endpoint().readinessOrModelsPath(), 200, "{}");

        var ready = enrolledHarness().ready(profile);
        var recorded = server.takeRequest(2, TimeUnit.SECONDS);

        assertAll("the ready endpoint",
                () -> assertTrue(ready, "a 200 on the readiness path is ready"),
                () -> assertNotNull(recorded, "a request reached the mock web server"),
                () -> assertEquals(profile.endpoint().readinessOrModelsPath(), recorded.getTarget(),
                        "the readiness path is requested"));
    }

    @Test
    @DisplayName("a readiness check that does not answer a success status is not ready")
    void notReady() {
        var profile = EndpointFixtures.profile(base);
        on("GET", profile.endpoint().readinessOrModelsPath(), 503, "{}");

        assertFalse(enrolledHarness().ready(profile), "a 503 on the readiness path is not ready");
    }

    @Test
    @DisplayName("a models listing that cannot be read is no listing")
    void unreadableListing() {
        var profile = EndpointFixtures.profile(base);
        on("GET", profile.endpoint().modelsPath(), 500, word());

        assertTrue(enrolledHarness().listing(profile).isEmpty(), "an unreadable listing yields no models");
    }

    @Test
    @DisplayName("the models listing is read through the profile's pointer")
    void listing() {
        var profile = EndpointFixtures.profile(base);
        var listed = word();
        on("GET", profile.endpoint().modelsPath(), 200, "{\"models\":[{\"id\":\"" + listed + "\"}]}");

        var listing = enrolledHarness().listing(profile).orElseThrow();

        assertAll("the read listing",
                () -> assertTrue(listing.isArray(), "the listing resolves to an array"),
                () -> assertEquals(1, listing.elements().size(), "the listing holds one model"),
                () -> assertEquals(Optional.of(listed),
                        listing.elements().getFirst().member("id").flatMap(JsonNode::text),
                        "the listed model id is read"));
    }
}
