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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/**
 * The two requests of a judgment task (Client Profiles § Judgment Endpoint, The Request and the Answer): the options of a
 * question in one order and in the opposite one, nothing beside the protocol's members, and the context budget.
 */
@DisplayName("The two requests of a judgment task")
class SystemOneRequestTest {

    private static final String MODEL = "judge-2026-01-01";

    private static EndpointProfile profile(int contextTokens) {
        return new EndpointProfile("hosted-judge", null, null, null,
                new EndpointProfile.Endpoint("system_one", "remote", "https://judge.example.test", null, "/v1/models",
                        "/data", "enforced", contextTokens, 512, 30, "judge-token"),
                Map.of("standard", MODEL), null);
    }

    private static List<SystemOneRequest.Question> questions() {
        return List.of(new SystemOneRequest.Question("q-1", "Which reading holds?",
                Map.of("b", new SystemOneRequest.Criterion("B", "The second reading", "It closes the question"),
                        "a", new SystemOneRequest.Criterion("A", "The first reading", null),
                        "c", new SystemOneRequest.Criterion("C", "The third reading", null))));
    }

    private static JsonNode state() {
        return JsonNode.object(Map.of("finding", JsonNode.string("the build failed twice")));
    }

    @Test
    @DisplayName("the options of every question are listed sorted, and in the opposite order in the second request")
    void oppositeOrders() {
        var composed = SystemOneRequest.compose(profile(8192), MODEL, state(), questions()).request();

        assertAll(
                () -> assertTrue(composed.ascending().indexOf("\"a\":") < composed.ascending().indexOf("\"b\":"),
                        "the first request lists the options sorted"),
                () -> assertTrue(composed.ascending().indexOf("\"b\":") < composed.ascending().indexOf("\"c\":")),
                () -> assertTrue(composed.descending().indexOf("\"c\":") < composed.descending().indexOf("\"b\":")),
                () -> assertTrue(composed.descending().indexOf("\"b\":") < composed.descending().indexOf("\"a\":")));
    }

    @Test
    @DisplayName("a request holds the model, the state and the questions, and nothing else")
    void sendsNothingElse() throws Exception {
        var body = SystemOneRequest.compose(profile(8192), MODEL, state(), questions()).request().ascending();
        var document = JsonDocument.parse(body.getBytes(StandardCharsets.UTF_8), "request");

        assertAll(
                () -> assertEquals(MODEL, document.member("model").flatMap(JsonNode::text).orElseThrow()),
                () -> assertEquals(state(), document.member("state").orElseThrow()),
                () -> assertTrue(document.member("recommendation").isEmpty()),
                () -> assertTrue(document.member("tools").isEmpty()),
                () -> assertTrue(document.member("answers").isEmpty()),
                () -> assertEquals(List.of("model", "state", "questions"),
                        List.copyOf(document.members().keySet())));
    }

    @Test
    @DisplayName("a question carries the type choice, its instructions and its criteria")
    void questionShape() throws Exception {
        var body = SystemOneRequest.compose(profile(8192), MODEL, state(), questions()).request().ascending();
        var document = JsonDocument.parse(body.getBytes(StandardCharsets.UTF_8), "request");
        var question = document.member("questions").flatMap(value -> value.member("q-1")).orElseThrow();
        var criterion = question.member("criteria").flatMap(value -> value.member("b")).orElseThrow();

        assertAll(
                () -> assertEquals("choice", question.member("type").flatMap(JsonNode::text).orElseThrow()),
                () -> assertEquals("Which reading holds?",
                        question.member("instructions").flatMap(JsonNode::text).orElseThrow()),
                () -> assertEquals("B", criterion.member("label").flatMap(JsonNode::text).orElseThrow()),
                () -> assertEquals("The second reading",
                        criterion.member("description").flatMap(JsonNode::text).orElseThrow()),
                () -> assertEquals("It closes the question",
                        criterion.member("consequences").flatMap(JsonNode::text).orElseThrow()),
                () -> assertTrue(question.member("criteria").flatMap(value -> value.member("a")).orElseThrow()
                        .member("consequences").isEmpty(), "a criterion without consequences omits the member"));
    }

    @Test
    @DisplayName("a task asks at least one question and names each once")
    void refusesAnEmptyOrAmbiguousTask() {
        var empty = SystemOneRequest.compose(profile(8192), MODEL, state(), List.of());
        var ambiguous = SystemOneRequest.compose(profile(8192), MODEL, state(),
                List.of(questions().getFirst(), questions().getFirst()));

        assertAll(
                () -> assertFalse(empty.isComposed()),
                () -> assertEquals(SystemOneRequest.Refusal.TASK_INVALID, empty.refusal()),
                () -> assertFalse(ambiguous.isComposed()),
                () -> assertEquals(SystemOneRequest.Refusal.TASK_INVALID, ambiguous.refusal()),
                () -> assertTrue(ambiguous.detail().contains("twice")));
    }

    @Test
    @DisplayName("an entity larger than the context budget is refused before a request is sent")
    void refusesAnEntityOverTheBudget() {
        var huge = JsonNode.object(Map.of("finding", JsonNode.string("x".repeat(4000))));
        var result = SystemOneRequest.compose(profile(1024), MODEL, huge, questions());

        assertAll(
                () -> assertFalse(result.isComposed()),
                () -> assertEquals(SystemOneRequest.Refusal.ENTITY_EXCEEDS_BUDGET, result.refusal()),
                () -> assertTrue(result.detail().contains("exceeds the context budget")));
    }
}
