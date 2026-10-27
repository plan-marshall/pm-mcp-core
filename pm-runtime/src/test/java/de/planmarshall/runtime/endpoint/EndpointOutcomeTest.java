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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.store.Measurement;

/**
 * What a task on an endpoint harness ends with (PM-CLIENT-2, Client Profiles § The Request): an answer that does not
 * validate, a refusal text, an HTTP error and a timeout each end the attempt like a worker without a valid result, and a
 * missing usage count is {@code unmeasured}.
 */
@DisplayName("Endpoint answer")
class EndpointOutcomeTest {

    private static final JsonNode SCHEMA = JsonDocument.parse(
            ("{\"type\":\"object\",\"required\":[\"verdict\"],\"additionalProperties\":false,"
                    + "\"properties\":{\"verdict\":{\"type\":\"string\"}}}").getBytes(StandardCharsets.UTF_8),
            "schema");

    private static EndpointClient.Response answer(String body) {
        return new EndpointClient.Response(200, body, EndpointClient.Failure.NONE, "answered");
    }

    private static String answerWith(String content, String usage) {
        return "{\"choices\":[{\"message\":{\"content\":" + quote(content) + "}}]" + usage + "}";
    }

    private static String quote(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }

    @Test
    @DisplayName("a valid document of the result schema is an accepted answer with its usage")
    void acceptedWithUsage() {
        var outcome = EndpointOutcome.accept(answer(answerWith("{\"verdict\":\"clean\"}",
                ",\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":5}")), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ANSWERED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.NONE, outcome.reason()),
                () -> assertEquals("{\"verdict\":\"clean\"}", outcome.content()),
                () -> assertEquals(Measurement.measured(10), outcome.usage().inputTokens()),
                () -> assertEquals(Measurement.measured(5), outcome.usage().outputTokens()),
                () -> assertEquals(15, outcome.usage().totalTokens().orElseThrow()));
    }

    @Test
    @DisplayName("prose under a schema ends the attempt")
    void proseUnderSchemaEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(answer(answerWith("The text looks fine to me.", "")), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason()),
                () -> assertTrue(outcome.detail().contains("not one JSON document")));
    }

    @Test
    @DisplayName("a document that does not validate ends the attempt")
    void invalidDocumentEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(answer(answerWith("{\"other\":1}", "")), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason()),
                () -> assertTrue(outcome.detail().contains("does not validate")));
    }

    @Test
    @DisplayName("an event stream ends the attempt")
    void eventStreamEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(answer("data: {\"choices\":[]}\n\ndata: [DONE]\n"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason()));
    }

    @Test
    @DisplayName("a refusal text ends the attempt")
    void refusalTextEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(answer(
                "{\"choices\":[{\"message\":{\"refusal\":\"I cannot help with that\"}}]}"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.REFUSAL_TEXT, outcome.reason()));
    }

    @Test
    @DisplayName("an HTTP error ends the attempt")
    void httpErrorEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(
                new EndpointClient.Response(500, "boom", EndpointClient.Failure.NONE, "answered"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.HTTP_ERROR, outcome.reason()));
    }

    @Test
    @DisplayName("a timeout ends the attempt")
    void timeoutEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(
                new EndpointClient.Response(0, "", EndpointClient.Failure.TIMEOUT, "no answer inside 30 s"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate()),
                () -> assertEquals(EndpointOutcome.Reason.TIMEOUT, outcome.reason()));
    }

    @Test
    @DisplayName("an answer without a usage count is unmeasured")
    void missingCountUnmeasured() {
        var outcome = EndpointOutcome.accept(answer(answerWith("{\"verdict\":\"clean\"}",
                ",\"usage\":{\"prompt_tokens\":10}")), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ANSWERED, outcome.fate()),
                () -> assertEquals(Measurement.measured(10), outcome.usage().inputTokens()),
                () -> assertEquals(Measurement.unmeasured(), outcome.usage().outputTokens()),
                () -> assertTrue(outcome.usage().unmeasuredReason().isPresent()));
    }

    @Test
    @DisplayName("a refusal carries the reason and no content")
    void refusedCarriesNoContent() {
        var outcome = EndpointOutcome.refused(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, "another program listens");

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, outcome.fate()),
                () -> assertEquals("", outcome.content()),
                () -> assertEquals("harness_not_enrolled", outcome.reason().wireName()));
    }
}
