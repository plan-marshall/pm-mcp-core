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

import de.cuioss.test.mockwebserver.EnableMockWebServer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mockwebserver3.Dispatcher;
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
 * reads the listing with the credential and checks that the role's model is listed, the launch stage sends two options in
 * both orders and refuses a service that answers free text, the classes stage does not apply, and the limits stage
 * bisects only on the operator's choice.
 */
@EnableMockWebServer(useHttps = true)
@DisplayName("Verification of a judgment endpoint")
class JudgmentVerificationTest {

    private static final String MODEL = "judge-2026-01-01";
    private static final String SECRET = "s3cr3t-judge-token";

    private MockWebServer server;
    private final List<RecordedRequest> requests = new ArrayList<>();
    private volatile Responder responder;

    @BeforeEach
    void start(MockWebServer mockWebServer) {
        server = mockWebServer;
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                requests.add(request);
                return responder.answer(request);
            }
        });
    }

    private String origin() {
        return server.url("/").toString().replaceAll("/$", "");
    }

    private EndpointProfile profile() {
        return new EndpointProfile("hosted-judge", null, null, null,
                new EndpointProfile.Endpoint("system_one", "remote", origin(), null, "/v1/models", "/data",
                        "enforced", 8192, 512, 30, "judge-token"),
                Map.of("standard", MODEL), null);
    }

    private JudgmentVerification verification() {
        var credential = (TokenLaneCredential) key -> Optional
                .of(new TokenLaneCredential.Entry(SECRET, URI.create(origin())));
        var client = new JudgmentClient(credential, SecretRegistry.none(), JudgmentTestTls.trusting());
        return new JudgmentVerification(client, (host, port) -> SocketOwner.Lookup.notFound("not read"),
                new WeightsReader(), (executable, argv, pattern) -> Optional.empty());
    }

    private static String typedAnswer(String question, String choice, String other) {
        return "{\"model\":\"" + MODEL + "\",\"answers\":{\"" + question + "\":{\"choice\":\"" + choice + "\","
                + "\"probabilities\":{\"" + choice + "\":0.6,\"" + other + "\":0.4}}},"
                + "\"usage\":{\"input_tokens\":120,\"output_tokens\":2}}";
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build();
    }

    private static MockResponse listing() {
        return json("{\"data\":[{\"id\":\"" + MODEL + "\"}]}");
    }

    /** How the server answers the request. */
    @FunctionalInterface
    interface Responder {
        MockResponse answer(RecordedRequest request);
    }

    @Test
    @DisplayName("the identity stage reads the listing and finds the role's model, without an executable")
    void identityReadsTheListing() {
        responder = request -> "/v1/models".equals(request.getTarget()) ? listing()
                : json(typedAnswer("probe", "alpha", "beta"));

        var result = verification().verify(profile(), MODEL, false);

        assertAll(
                () -> assertTrue(result.identity().passed()),
                () -> assertNull(result.identity().executablePath()),
                () -> assertEquals(List.of(MODEL), result.identity().models()),
                () -> assertTrue(result.launch().passed()),
                () -> assertEquals("enforced", result.launch().resultSchema()),
                () -> assertNull(result.classes(), "the classes stage does not apply"),
                () -> assertFalse(result.stageStatuses().containsKey("classes")),
                () -> assertFalse(result.values().contains(
                        new ProfileStages.EndpointValue("/executable", ProfileStages.EndpointValueStatus.MEASURED))));
    }

    @Test
    @DisplayName("the launch stage sends two options in both orders, a contradicted request and a one-option request")
    void launchSendsItsProbes() {
        responder = request -> "/v1/models".equals(request.getTarget()) ? listing() : probeAnswer(request);

        var result = verification().verify(profile(), MODEL, false);

        assertAll(
                () -> assertTrue(result.launch().passed()),
                () -> assertEquals("valid", result.launch().probes().get("two_options_ascending")),
                () -> assertEquals("valid", result.launch().probes().get("two_options_descending")),
                () -> assertEquals("same_options", result.launch().probes().get("both_orders")),
                () -> assertEquals("valid", result.launch().probes().get("contradicted_instructions")),
                () -> assertEquals("valid", result.launch().probes().get("one_option")),
                () -> assertEquals(6, requests.size(), "the listing, the readiness check and the four probes"));
    }

    @Test
    @DisplayName("a service that answers free text is refused and is no enforced result schema")
    void refusesFreeText() {
        responder = request -> "/v1/models".equals(request.getTarget()) ? listing()
                : new MockResponse.Builder().code(200).body("alpha").build();

        var result = verification().verify(profile(), MODEL, false);

        assertAll(
                () -> assertFalse(result.launch().passed()),
                () -> assertEquals("none", result.launch().resultSchema()),
                () -> assertTrue(result.refusesDraft()));
    }

    @Test
    @DisplayName("the limits stage runs only on the operator's explicit choice on a remote form")
    void limitsOnlyOnChoice() {
        responder = request -> "/v1/models".equals(request.getTarget()) ? listing() : probeAnswer(request);

        var notChosen = verification().verify(profile(), MODEL, false);
        var chosen = verification().verify(profile(), MODEL, true);

        assertAll(
                () -> assertEquals(new ProfileStages.EndpointLimits(false, 0), notChosen.limits()),
                () -> assertTrue(chosen.limits().passed()),
                () -> assertTrue(chosen.limits().contextTokens() >= JudgmentVerification.MINIMUM_CONTEXT_TOKENS),
                () -> assertEquals("passed", chosen.stageStatuses().get("limits")));
    }

    @Test
    @DisplayName("the limits stage takes a 400 with max_tokens_exceeded as its bound")
    void limitsBoundAtTheSizeRefusal() {
        responder = request -> "/v1/models".equals(request.getTarget()) ? listing()
                : request.getBody().utf8().length() > 20_000
                ? new MockResponse.Builder().code(400)
                .body("{\"error\":\"max_tokens_exceeded\"}").build()
                : probeAnswer(request);

        var result = verification().verify(profile(), MODEL, true);

        assertAll(
                () -> assertTrue(result.limits().passed()),
                () -> assertTrue(result.limits().contextTokens() >= JudgmentVerification.MINIMUM_CONTEXT_TOKENS),
                () -> assertTrue(result.limits().contextTokens() < JudgmentVerification.MAXIMUM_CONTEXT_TOKENS));
    }

    @Test
    @DisplayName("a refusal that is not the size ends the limits stage without a bound")
    void limitsFailOnAnotherRefusal() {
        responder = request -> "/v1/models".equals(request.getTarget()) ? listing()
                : request.getBody().utf8().length() > 20_000
                ? new MockResponse.Builder().code(500).body("{\"error\":\"boom\"}").build()
                : probeAnswer(request);

        var result = verification().verify(profile(), MODEL, true);

        assertAll(
                () -> assertFalse(result.limits().passed()),
                () -> assertEquals("not_run", result.stageStatuses().get("limits")),
                () -> assertFalse(result.refusesDraft(), "the limits stage records, it does not refuse the draft"));
    }

    private static MockResponse probeAnswer(RecordedRequest request) {
        var body = request.getBody().utf8();
        var input = body.length() / EndpointRequest.CHARS_PER_TOKEN;
        if (body.contains("\"only\"")) {
            return json("{\"model\":\"" + MODEL + "\",\"answers\":{\"probe\":{\"choice\":\"only\","
                    + "\"probabilities\":{\"only\":1}}},\"usage\":{\"input_tokens\":" + input + ",\"output_tokens\":2}}");
        }
        return json("{\"model\":\"" + MODEL + "\",\"answers\":{\"probe\":{\"choice\":\"alpha\","
                + "\"probabilities\":{\"alpha\":0.6,\"beta\":0.4}}},\"usage\":{\"input_tokens\":" + input + ","
                + "\"output_tokens\":2}}");
    }
}
