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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/**
 * The one request of a task on an endpoint (PM-CLIENT-2, Client Profiles § The Request): the launch prompt as the system
 * message, the task input as the user message, streaming explicitly off, no {@code tools} member and no tool choice, the
 * role's result schema where the endpoint accepts one, and every default of the serving program sent explicitly.
 */
@DisplayName("Endpoint request")
class EndpointRequestTest {

    private static JsonNode schema() {
        return JsonDocument.parse("{\"type\":\"object\",\"required\":[\"verdict\"]}".getBytes(StandardCharsets.UTF_8),
                "schema");
    }

    @Test
    @DisplayName("the request sends the launch prompt and the task, streaming off and with no tool")
    void streamingOffAndNoTool() {
        var composed = EndpointRequest.compose(EndpointFixtures.profile(), "mid", "You are the screener.", "Text to screen.",
                null);
        var body = composed.request().body();

        assertAll(
                () -> assertTrue(composed.isComposed()),
                () -> assertNull(composed.refusal()),
                () -> assertTrue(body.contains("\"stream\":false")),
                () -> assertTrue(body.contains("\"role\":\"system\"")),
                () -> assertTrue(body.contains("\"role\":\"user\"")),
                () -> assertTrue(body.contains("You are the screener.")),
                () -> assertTrue(body.contains("\"model\":\"mid\"")),
                () -> assertTrue(body.contains("\"max_tokens\":1024")),
                () -> assertTrue(body.contains("\"temperature\":0")),
                () -> assertFalse(body.contains("\"tools\"")),
                () -> assertFalse(body.contains("\"tool_choice\"")),
                () -> assertFalse(body.contains("\"response_format\"")));
    }

    @Test
    @DisplayName("the result schema is the response format where the endpoint accepts one")
    void resultSchemaSent() {
        var body = EndpointRequest.compose(EndpointFixtures.profile(), "mid", "prompt", "task", schema())
                .request().body();

        assertAll(
                () -> assertTrue(body.contains("\"response_format\"")),
                () -> assertTrue(body.contains("\"json_schema\"")),
                () -> assertTrue(body.contains("\"strict\":true")));
    }

    @Test
    @DisplayName("a profile whose result schema is none sends none")
    void noSchemaWhereTheEndpointRefusesIt() {
        var profile = new EndpointProfile("local-runner", "ollama",
                new EndpointProfile.Identity(List.of("--version"), "version ([0-9.]+)"), null,
                new EndpointProfile.Endpoint("openai_chat", "loopback", EndpointFixtures.BASE_URL, null, "/models",
                        "/models", "none", 8192, 1024, 30),
                Map.of(), null);

        var body = EndpointRequest.compose(profile, "mid", "prompt", "task", schema()).request().body();

        assertFalse(body.contains("\"response_format\""));
    }

    @Test
    @DisplayName("a launch prompt that alone does not fit the context budget refuses the composition")
    void promptExceedingBudget() {
        var prompt = "x".repeat(EndpointRequest.CHARS_PER_TOKEN * 7168 + 4);

        var composed = EndpointRequest.compose(EndpointFixtures.profile(), "mid", prompt, "task", null);

        assertAll(
                () -> assertFalse(composed.isComposed()),
                () -> assertEquals(EndpointRequest.Refusal.PROMPT_EXCEEDS_BUDGET, composed.refusal()),
                () -> assertTrue(composed.detail().contains("context budget")));
    }

    @Test
    @DisplayName("a task that does not fit the context budget refuses the composition")
    void taskExceedingBudget() {
        var task = "x".repeat(EndpointRequest.CHARS_PER_TOKEN * 7168 + 4);

        var composed = EndpointRequest.compose(EndpointFixtures.profile(), "mid", "prompt", task, null);

        assertAll(
                () -> assertFalse(composed.isComposed()),
                () -> assertEquals(EndpointRequest.Refusal.TASK_EXCEEDS_BUDGET, composed.refusal()));
    }

    @Test
    @DisplayName("the tool-call probe declares one tool and no tool choice")
    void probeDeclaresOneTool() {
        var body = EndpointRequest.compose(EndpointFixtures.profile(), "mid", "prompt", "task", null)
                .request().bodyWithTools(EndpointVerification.PROBE_TOOL);

        assertAll(
                () -> assertTrue(body.contains("\"tools\"")),
                () -> assertTrue(body.contains("probe_tool")),
                () -> assertFalse(body.contains("\"tool_choice\"")));
    }

    @Test
    @DisplayName("the estimate rounds up at four characters per token")
    void estimateRoundsUp() {
        assertAll(
                () -> assertEquals(0, EndpointRequest.estimate(null)),
                () -> assertEquals(0, EndpointRequest.estimate("")),
                () -> assertEquals(1, EndpointRequest.estimate("abc")),
                () -> assertEquals(2, EndpointRequest.estimate("abcde")));
    }
}
