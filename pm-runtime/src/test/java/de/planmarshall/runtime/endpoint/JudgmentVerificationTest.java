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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import de.cuioss.test.mockwebserver.dispatcher.HttpMethodMapper;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcher;
import de.cuioss.test.mockwebserver.dispatcher.ModuleDispatcherElement;
import java.net.URI;
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
import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.runtime.credentials.SecretRegistry;
import de.planmarshall.runtime.credentials.TokenLaneCredential;

/**
 * The verification of a judgment endpoint (Client Profiles § Verification of a Judgment Endpoint): the identity stage
 * reads the listing with the credential and checks that the role's model is listed, the launch stage sends two options
 * in both orders and refuses a service that answers free text, the classes stage does not apply, and the limits stage
 * bisects only on the operator's choice.
 * <p>
 * Every request is answered by a branch of the {@link ModuleDispatcherElement} of {@link #getModuleDispatcher()},
 * keyed by method and request target and primed per test, so a request for a wrong path or with a wrong method gets
 * no answer; the probe branch answers by the body of the request, because every probe goes to the same path. The
 * origin comes from the injected {@link URIBuilder} of the HTTPS mock web server. Contract literals, kept by design:
 * the {@code system_one} wire format (its path, the models path {@code /v1/models} and pointer {@code /data} of the
 * profile, the JSON field names {@code data}, {@code id}, {@code model}, {@code answers}, {@code choice},
 * {@code probabilities}, {@code usage}, {@code input_tokens}, {@code output_tokens} and {@code error}); the status
 * codes and the {@code max_tokens_exceeded} code; the probe question {@code probe} and its options {@code alpha},
 * {@code beta} and {@code only}; the probability pairs; the body length past which the size refusal answers; the probe,
 * stage and status names. The client id, the model, the credential key, its secret and the free text are generated.
 */
@EnableMockWebServer(useHttps = true)
@EnableGeneratorController
@ModuleDispatcher
@DisplayName("Verification of a judgment endpoint")
class JudgmentVerificationTest {

    /** The path of the models listing of the profile (contract). */
    private static final String MODELS = "/v1/models";

    /** The body length past which the size refusal answers: well inside the bisected range (contract of the test). */
    private static final int SIZE_LIMIT = 20_000;

    /** Canned answers by {@code METHOD target}; each answers every request to its target. */
    private final Map<String, Function<RecordedRequest, MockResponse>> routes = new ConcurrentHashMap<>();
    private MockWebServer server;
    private URI root;
    private String clientId;
    private String key;
    private String secret;
    private String model;

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
        root = uriBuilder.build();
        clientId = word() + "-judge";
        key = word() + "-token";
        secret = word() + "-" + word() + "-" + Generators.integers(1_000, 9_999).next();
        model = word() + "-" + Generators.integers(1, 9_999).next();
        var listing = json("{\"data\":[{\"id\":\"" + model + "\"}]}");
        routes.put("GET " + MODELS, request -> listing);
    }

    private void probesAnsweredBy(Function<RecordedRequest, MockResponse> branch) {
        routes.put("POST " + SystemOneRequest.PATH, branch);
    }

    /** @return a generated lower-case word, safe in a JSON string */
    private static String word() {
        return Generators.letterStrings(4, 12).next().toLowerCase(Locale.ROOT);
    }

    private String origin() {
        return root.getScheme() + "://" + root.getHost() + ":" + root.getPort();
    }

    private EndpointProfile profile() {
        return new EndpointProfile(clientId, null, null, null,
                new EndpointProfile.Endpoint("system_one", "remote", origin(), null, MODELS, "/data",
                        "enforced", 8192, 512, 30, key),
                Map.of("standard", model), null);
    }

    private JudgmentVerification verification() {
        TokenLaneCredential credential = entry -> Optional.of(new TokenLaneCredential.Entry(secret,
                URI.create(origin())));
        var client = new JudgmentClient(credential, SecretRegistry.none(), JudgmentTestTls.trusting());
        return new JudgmentVerification(client, (host, port) -> SocketOwner.Lookup.notFound(word()),
                new WeightsReader(), (executable, argv, pattern) -> Optional.empty());
    }

    private String typedAnswer() {
        return "{\"model\":\"" + model + "\",\"answers\":{\"probe\":{\"choice\":\"alpha\","
                + "\"probabilities\":{\"alpha\":0.6,\"beta\":0.4}}},"
                + "\"usage\":{\"input_tokens\":" + Generators.integers(1, 500).next() + ",\"output_tokens\":2}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }

    /** @return the probe answer of the endpoint's own model: the one-option probe or the two-option probe */
    private MockResponse probeAnswer(RecordedRequest request) {
        var body = request.getBody().utf8();
        var input = body.length() / EndpointRequest.CHARS_PER_TOKEN;
        if (body.contains("\"only\"")) {
            return json("{\"model\":\"" + model + "\",\"answers\":{\"probe\":{\"choice\":\"only\","
                    + "\"probabilities\":{\"only\":1}}},\"usage\":{\"input_tokens\":" + input
                    + ",\"output_tokens\":2}}");
        }
        return json("{\"model\":\"" + model + "\",\"answers\":{\"probe\":{\"choice\":\"alpha\","
                + "\"probabilities\":{\"alpha\":0.6,\"beta\":0.4}}},\"usage\":{\"input_tokens\":" + input + ","
                + "\"output_tokens\":2}}");
    }

    @Test
    @DisplayName("the identity stage reads the listing and finds the role's model, without an executable")
    void identityReadsTheListing() {
        var typed = json(typedAnswer());
        probesAnsweredBy(request -> typed);

        var result = verification().verify(profile(), model, false);

        assertAll("the identity of the remote endpoint",
                () -> assertTrue(result.identity().passed(), "the identity stage passes"),
                () -> assertNull(result.identity().executablePath(), "a remote endpoint has no executable"),
                () -> assertEquals(List.of(model), result.identity().models(), "the listing names the role's model"),
                () -> assertTrue(result.launch().passed(), "the launch stage passes"),
                () -> assertEquals("enforced", result.launch().resultSchema(), "the result schema is enforced"),
                () -> assertNull(result.classes(), "the classes stage does not apply"),
                () -> assertFalse(result.stageStatuses().containsKey("classes"),
                        "the classes stage has no status"),
                () -> assertFalse(result.values().contains(new ProfileStages.EndpointValue("/executable",
                        ProfileStages.EndpointValueStatus.MEASURED)), "no executable value is measured"));
    }

    @Test
    @DisplayName("the launch stage sends two options in both orders, a contradicted request and a one-option request")
    void launchSendsItsProbes() {
        probesAnsweredBy(this::probeAnswer);

        var result = verification().verify(profile(), model, false);

        assertAll("the launch probes",
                () -> assertTrue(result.launch().passed(), "the launch stage passes"),
                () -> assertEquals("valid", result.launch().probes().get("two_options_ascending"),
                        "the ascending probe is valid"),
                () -> assertEquals("valid", result.launch().probes().get("two_options_descending"),
                        "the descending probe is valid"),
                () -> assertEquals("same_options", result.launch().probes().get("both_orders"),
                        "both orders answer the same options"),
                () -> assertEquals("valid", result.launch().probes().get("contradicted_instructions"),
                        "the contradicted probe is valid"),
                () -> assertEquals("valid", result.launch().probes().get("one_option"),
                        "the one-option probe is valid"),
                () -> assertEquals(6, server.getRequestCount(), "the listing, the readiness check and four probes"));
    }

    @Test
    @DisplayName("a service that answers free text is refused and is no enforced result schema")
    void refusesFreeText() {
        var freeText = new MockResponse.Builder().code(200).body(word() + " " + word()).build();
        probesAnsweredBy(request -> freeText);

        var result = verification().verify(profile(), model, false);

        assertAll("the refused free text",
                () -> assertFalse(result.launch().passed(), "the launch stage fails on free text"),
                () -> assertEquals("none", result.launch().resultSchema(), "free text is measured none"),
                () -> assertTrue(result.refusesDraft(), "a free-text service refuses the draft"));
    }

    @Test
    @DisplayName("the limits stage runs only on the operator's explicit choice on a remote form")
    void limitsOnlyOnChoice() {
        probesAnsweredBy(this::probeAnswer);

        var notChosen = verification().verify(profile(), model, false);
        var chosen = verification().verify(profile(), model, true);

        assertAll("the limits on choice",
                () -> assertEquals(new ProfileStages.EndpointLimits(false, 0), notChosen.limits(),
                        "the limits stage does not run unchosen"),
                () -> assertTrue(chosen.limits().passed(), "the chosen limits stage passes"),
                () -> assertTrue(chosen.limits().contextTokens() >= JudgmentVerification.MINIMUM_CONTEXT_TOKENS,
                        "the bound is at least the minimum"),
                () -> assertEquals("passed", chosen.stageStatuses().get("limits"),
                        "the limits stage status is passed"));
    }

    @Test
    @DisplayName("the limits stage takes a 400 with max_tokens_exceeded as its bound")
    void limitsBoundAtTheSizeRefusal() {
        var refusal = new MockResponse.Builder().code(400).body("{\"error\":\"max_tokens_exceeded\"}").build();
        probesAnsweredBy(request -> request.getBody().utf8().length() > SIZE_LIMIT ? refusal : probeAnswer(request));

        var result = verification().verify(profile(), model, true);

        assertAll("the bound at the size refusal",
                () -> assertTrue(result.limits().passed(), "the limits stage passes"),
                () -> assertTrue(result.limits().contextTokens() >= JudgmentVerification.MINIMUM_CONTEXT_TOKENS,
                        "the bound is at least the minimum"),
                () -> assertTrue(result.limits().contextTokens() < JudgmentVerification.MAXIMUM_CONTEXT_TOKENS,
                        "the bound is below the maximum"));
    }

    @Test
    @DisplayName("a refusal that is not the size ends the limits stage without a bound")
    void limitsFailOnAnotherRefusal() {
        var failure = new MockResponse.Builder().code(500).body("{\"error\":\"" + word() + "\"}").build();
        probesAnsweredBy(request -> request.getBody().utf8().length() > SIZE_LIMIT ? failure : probeAnswer(request));

        var result = verification().verify(profile(), model, true);

        assertAll("the limits without a bound",
                () -> assertFalse(result.limits().passed(), "the limits stage does not pass"),
                () -> assertEquals("not_run", result.stageStatuses().get("limits"),
                        "the limits stage status is not_run"),
                () -> assertFalse(result.refusesDraft(), "the limits stage records, it does not refuse the draft"));
    }
}
