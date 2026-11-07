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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import java.net.Inet6Address;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.ProfileStages;

/**
 * The four stages that verify an endpoint draft (PM-CLIENT-5, Client Profiles § Verification of an Endpoint): the
 * observed identity, the launch with the settled result schema, the recorded tool-call probe and the bisected limit.
 * <p>
 * Every request is answered by a branch of the {@link ModuleDispatcherElement} of {@link #getModuleDispatcher()},
 * keyed by method and request target and primed per test, so a request for a wrong path or with a wrong method gets
 * no answer; the chat branch may answer by the body of the request, because the launch stage sends the same path with
 * and without a result schema. The port comes from the injected {@link URIBuilder}; the host is the loopback literal of
 * the address the server is bound to. Contract literals, kept by design: the model-endpoint wire format (the chat
 * path, the readiness and models paths of the fixture profile, the JSON field names {@code choices}, {@code message},
 * {@code content}, {@code tool_calls}, {@code usage}, {@code prompt_tokens}, {@code completion_tokens},
 * {@code response_format}, {@code models}, {@code id} and {@code digest}); the status codes; the model ids of the
 * fixture profile's tiers; the stage names, the stage statuses and the {@code advisory}, {@code none} and
 * {@code tools_ignored} measurements; the eight endpoint values a verification reports. The tasks, the decoy, the
 * verdict, the digests, the version and the token counts are generated.
 */
@EnableMockWebServer
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("Endpoint verification")
class EndpointVerificationTest {

    /** The model ids of the tiers of the fixture profile, in the order the listing names them (contract). */
    private static final List<String> MODELS = List.of("big", "mid", "small");

    /** The model of the standard tier of the fixture profile (contract). */
    private static final String MODEL = "mid";

    /** Canned answers by {@code METHOD target}; each answers every request to its target. */
    private final Map<String, Function<RecordedRequest, MockResponse>> routes = new ConcurrentHashMap<>();
    private MockWebServer server;
    private EndpointProfile profile;
    private String decoy;
    private String digest;
    private String version;
    private JsonNode schema;

    /** Answers a GET or POST through the branch primed for its method and target; anything else gets none. */
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
        return Optional.ofNullable(routes.get(request.getMethod() + " " + request.getTarget()))
                .map(branch -> branch.apply(request));
    }

    @BeforeEach
    void start(MockWebServer mockWebServer, URIBuilder uriBuilder) {
        server = mockWebServer;
        var host = server.getSocketAddress().getAddress() instanceof Inet6Address ? "[::1]" : "127.0.0.1";
        profile = EndpointFixtures.profile("http://" + host + ":" + uriBuilder.build().getPort());
        decoy = word().toUpperCase(Locale.ROOT) + "-" + Generators.integers(1, 999).next();
        digest = Generators.strings("0123456789abcdef", 12, 12).next();
        version = Generators.integers(1, 9).next() + "." + Generators.integers(0, 99).next() + "."
                + Generators.integers(0, 99).next();
        var required = word();
        schema = JsonDocument.parse(("{\"type\":\"object\",\"required\":[\"" + required + "\"],\"properties\":{\""
                + required + "\":{\"type\":\"string\"},\"note\":{\"type\":\"string\"}}}")
                .getBytes(StandardCharsets.UTF_8), "schema");
        on("GET", profile.endpoint().readinessOrModelsPath(), fixed(200, "{}"));
        on("GET", profile.endpoint().modelsPath(), fixed(200, listing(MODELS)));
        on("POST", EndpointClient.CHAT_PATH, fixed(200, chatAnswer(required)));
    }

    private void on(String method, String target, Function<RecordedRequest, MockResponse> branch) {
        routes.put(method + " " + target, branch);
    }

    private static Function<RecordedRequest, MockResponse> fixed(int status, String body) {
        var response = new MockResponse.Builder().code(status).body(body).build();
        return request -> response;
    }

    /** @return a generated lower-case word, safe in a JSON string and a JSON member name */
    private static String word() {
        return Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return the models listing of the protocol, with the generated digest on every model that carries one */
    private String listing(List<String> withDigest) {
        var models = MODELS.stream().map(id -> withDigest.contains(id)
                ? "{\"id\":\"" + id + "\",\"digest\":\"" + digest + "\"}"
                : "{\"id\":\"" + id + "\"}").toList();
        return "{\"models\":[" + String.join(",", models) + "]}";
    }

    /** @return the chat answer of the protocol, whose content holds the required member and names the decoy */
    private String chatAnswer(String required) {
        var content = "{\\\"" + required + "\\\":\\\"" + word() + "\\\",\\\"note\\\":\\\"" + decoy + "\\\"}";
        return "{\"choices\":[{\"message\":{\"content\":\"" + content + "\"}}],\"usage\":{\"prompt_tokens\":"
                + Generators.integers(1, 500).next() + ",\"completion_tokens\":" + Generators.integers(1, 500).next()
                + "}}";
    }

    private String toolTask() {
        return word() + " " + EndpointVerification.PROBE_TOOL + " " + decoy;
    }

    private EndpointVerification verification() {
        return new EndpointVerification(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.found(EndpointFixtures.executablePath()), new WeightsReader(),
                (executable, argv, pattern) -> Optional.of(version));
    }

    @Test
    @DisplayName("the four stages report what the runtime observed")
    void stages() {
        var result = verification().verify(profile, MODEL, word(), toolTask(), decoy, schema);

        assertAll("the observed stages",
                () -> assertFalse(result.refusesDraft(), "a verified endpoint keeps its draft"),
                () -> assertTrue(result.identity().passed(), "the identity stage passes"),
                () -> assertEquals(EndpointFixtures.executablePath(), result.identity().executablePath(),
                        "the identity names the observed program"),
                () -> assertEquals(version, result.identity().version(), "the identity names the probed version"),
                () -> assertEquals(MODELS, result.identity().models(), "the identity lists the served models"),
                () -> assertEquals(digest, result.identity().weights().get(MODEL),
                        "the identity names the weights of a model"),
                () -> assertTrue(result.launch().passed(), "the launch stage passes"),
                () -> assertTrue(result.launch().readiness(), "the readiness check answered a success"),
                () -> assertEquals("advisory", result.launch().resultSchema(),
                        "a schema not held to is measured advisory"),
                () -> assertTrue(result.launch().streamingOff(), "the launch answered with streaming off"),
                () -> assertFalse(result.classes().toolCallAnswered(), "the probe was not answered by a tool call"),
                () -> assertTrue(result.classes().toolsIgnored(), "the decoy answer records ignored tools"),
                () -> assertEquals("tools_ignored", result.classes().results().get("tools"),
                        "the tools class is recorded as ignored"),
                () -> assertTrue(result.limits().passed(), "the limits stage passes"),
                () -> assertEquals("passed", result.stageStatuses().get("identity"),
                        "the identity stage status is passed"),
                () -> assertEquals("passed", result.stageStatuses().get("launch"),
                        "the launch stage status is passed"),
                () -> assertEquals(8, result.values().size(), "the verification reports eight values"));
    }

    @Test
    @DisplayName("an endpoint that refuses a result schema is measured none and still launches")
    void noneMeasured() {
        var refusal = new MockResponse.Builder().code(400).body("{\"error\":\"" + word() + "\"}").build();
        var plain = new MockResponse.Builder().code(200).body(chatAnswer(word())).build();
        on("POST", EndpointClient.CHAT_PATH,
                request -> request.getBody().utf8().contains("\"response_format\"") ? refusal : plain);

        var result = verification().verify(profile, MODEL, word(), word(), decoy, schema);

        assertAll("the endpoint without schema",
                () -> assertTrue(result.launch().passed(), "the launch passes without a schema"),
                () -> assertEquals("none", result.launch().resultSchema(), "a refused schema is measured none"),
                () -> assertFalse(result.refusesDraft(), "a refused schema keeps the draft"));
    }

    @Test
    @DisplayName("an endpoint that answers the tool probe with a tool call is recorded as answering one")
    void toolCallAnswered() {
        on("POST", EndpointClient.CHAT_PATH, fixed(200, "{\"choices\":[{\"message\":{\"content\":\"{}\","
                + "\"tool_calls\":[{\"id\":\"" + word() + "\"}]}}]}"));

        var result = verification().verify(profile, MODEL, word(), toolTask(), decoy, schema);

        assertAll("the answered tool call",
                () -> assertTrue(result.classes().toolCallAnswered(), "the tool call answer is recorded"),
                () -> assertFalse(result.classes().toolsIgnored(), "a tool call answer ignores no tools"));
    }

    @Test
    @DisplayName("a model whose weights identity cannot be read fails the identity stage")
    void unreadWeightsFailIdentity() {
        on("GET", profile.endpoint().modelsPath(), fixed(200, listing(List.of("big", "small"))));

        var result = verification().verify(profile, MODEL, word(), word(), decoy, schema);

        assertAll("the unread weights",
                () -> assertFalse(result.identity().passed(), "the identity stage fails"),
                () -> assertEquals(MODELS, result.identity().models(), "the identity still lists the models"),
                () -> assertFalse(result.identity().weights().containsKey(MODEL),
                        "the unread weights are not recorded"),
                () -> assertTrue(result.refusesDraft(), "a failed identity refuses the draft"));
    }

    @Test
    @DisplayName("a failed identity refuses the draft and sends nothing")
    void failedIdentityRefusesDraft() {
        var verification = new EndpointVerification(new EndpointClient(),
                (host, port) -> SocketOwner.Lookup.notFound(word()), new WeightsReader(),
                (executable, argv, pattern) -> Optional.empty());

        var result = verification.verify(profile, MODEL, word(), word(), decoy, schema);

        assertAll("the refused draft",
                () -> assertTrue(result.refusesDraft(), "a failed identity refuses the draft"),
                () -> assertFalse(result.identity().passed(), "the identity stage fails"),
                () -> assertEquals("failed", result.stageStatuses().get("identity"),
                        "the identity stage status is failed"),
                () -> assertEquals(ProfileStages.EndpointValueStatus.NOT_MEASURED,
                        result.values().getFirst().status(), "no value is measured"),
                () -> assertEquals(0, server.getRequestCount(), "no request reached the mock web server"));
    }
}
