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
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
 * <p>
 * Every datum of this test is a contract: the OpenAI-compatible wire members, the HTTP status codes, the refusal text
 * and the schema's member names.
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
                () -> assertEquals(EndpointOutcome.Fate.ANSWERED, outcome.fate(),
                        "a valid answer is accepted"),
                () -> assertEquals(EndpointOutcome.Reason.NONE, outcome.reason(),
                        "an accepted answer has no refusal reason"),
                () -> assertEquals("{\"verdict\":\"clean\"}", outcome.content(),
                        "the accepted content is the answer text"),
                () -> assertEquals(Measurement.measured(10), outcome.usage().inputTokens(),
                        "the input tokens are the usage count"),
                () -> assertEquals(Measurement.measured(5), outcome.usage().outputTokens(),
                        "the output tokens are the usage count"),
                () -> assertEquals(15, outcome.usage().totalTokens().orElseThrow(),
                        "the total tokens are the summed usage count"));
    }

    @Test
    @DisplayName("prose under a schema ends the attempt")
    void proseUnderSchemaEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(answer(answerWith("The text looks fine to me.", "")), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate(),
                        "prose under a schema ends the attempt"),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason(),
                        "the refusal reason is an invalid answer"),
                () -> assertTrue(outcome.detail().contains("not one JSON document"),
                        "the detail says it is not one JSON document"));
    }

    @Test
    @DisplayName("a document that does not validate ends the attempt")
    void invalidDocumentEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(answer(answerWith("{\"other\":1}", "")), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate(),
                        "a document outside the schema ends the attempt"),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason(),
                        "the reason is an invalid answer"),
                () -> assertTrue(outcome.detail().contains("does not validate"),
                        "the detail says the answer does not validate"));
    }

    @Test
    @DisplayName("an event stream ends the attempt")
    void eventStreamEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(answer("data: {\"choices\":[]}\n\ndata: [DONE]\n"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate(),
                        "an event stream ends the attempt"),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason(),
                        "an event stream is no valid answer"));
    }

    @Test
    @DisplayName("a refusal text ends the attempt")
    void refusalTextEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(answer(
                "{\"choices\":[{\"message\":{\"refusal\":\"I cannot help with that\"}}]}"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate(),
                        "a refusal text ends the attempt"),
                () -> assertEquals(EndpointOutcome.Reason.REFUSAL_TEXT, outcome.reason(),
                        "the reason is the refusal text"));
    }

    @Test
    @DisplayName("an HTTP error ends the attempt")
    void httpErrorEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(
                new EndpointClient.Response(500, "boom", EndpointClient.Failure.NONE, "answered"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate(),
                        "an HTTP error ends the attempt"),
                () -> assertEquals(EndpointOutcome.Reason.HTTP_ERROR, outcome.reason(),
                        "the reason is an HTTP error"));
    }

    @Test
    @DisplayName("a timeout ends the attempt")
    void timeoutEndsTheAttempt() {
        var outcome = EndpointOutcome.accept(
                new EndpointClient.Response(0, "", EndpointClient.Failure.TIMEOUT, "no answer inside 30 s"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate(),
                        "a timeout ends the attempt"),
                () -> assertEquals(EndpointOutcome.Reason.TIMEOUT, outcome.reason(),
                        "the reason is a timeout"));
    }

    @Test
    @DisplayName("an answer without a usage count is unmeasured")
    void missingCountUnmeasured() {
        var outcome = EndpointOutcome.accept(answer(answerWith("{\"verdict\":\"clean\"}",
                ",\"usage\":{\"prompt_tokens\":10}")), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ANSWERED, outcome.fate(),
                        "a partial usage still yields an answer"),
                () -> assertEquals(Measurement.measured(10), outcome.usage().inputTokens(),
                        "the input tokens are still measured"),
                () -> assertEquals(Measurement.unmeasured(), outcome.usage().outputTokens(),
                        "a missing count leaves output unmeasured"),
                () -> assertNotNull(outcome.usage().unmeasuredReason(),
                        "the missing count carries a reason"));
    }

    @Test
    @DisplayName("an answer without a choice is no valid answer")
    void noChoice() {
        var outcome = EndpointOutcome.accept(answer("{\"choices\":[]}"), SCHEMA);

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.ENDED, outcome.fate(),
                        "an answer without a choice ends the attempt"),
                () -> assertEquals(EndpointOutcome.Reason.INVALID_ANSWER, outcome.reason(),
                        "the reason is an invalid answer"));
    }

    @Test
    @DisplayName("a refusal carries the reason and no content")
    void refusedCarriesNoContent() {
        var outcome = EndpointOutcome.refused(EndpointOutcome.Reason.HARNESS_NOT_ENROLLED, "another program listens");

        assertAll(
                () -> assertEquals(EndpointOutcome.Fate.REFUSED, outcome.fate(),
                        "a refusal is a refused fate"),
                () -> assertEquals("", outcome.content(), "a refusal carries no content"),
                () -> assertEquals("harness_not_enrolled", outcome.reason().wireName(),
                        "the reason wire name is harness_not_enrolled"));
    }
}
