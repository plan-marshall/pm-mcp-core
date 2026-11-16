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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.EndpointProfile;
import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;

/**
 * The one request of a task on an endpoint (PM-CLIENT-2, Client Profiles § The Request): the launch prompt as the system
 * message, the task input as the user message, streaming explicitly off, no {@code tools} member and no tool choice, the
 * role's result schema where the endpoint accepts one, and every default of the serving program sent explicitly.
 * <p>
 * The prompt and task texts are generated; the wire member names, the model tier {@code mid}, the schema and the
 * defaults {@code 1024} and {@code 0} are the contract the request proves.
 */
@EnableGeneratorController
@DisplayName("Endpoint request")
class EndpointRequestTest {

    private String prompt;

    private String task;

    @BeforeEach
    void draw() {
        prompt = Generators.letterStrings(1, 40).next();
        task = Generators.letterStrings(1, 40).next();
    }

    private static JsonNode schema() {
        return JsonDocument.parse("{\"type\":\"object\",\"required\":[\"verdict\"]}".getBytes(StandardCharsets.UTF_8),
                "schema");
    }

    @Test
    @DisplayName("the request sends the launch prompt and the task, streaming off and with no tool")
    void streamingOffAndNoTool() {
        var composed = EndpointRequest.compose(EndpointFixtures.profile(), "mid", prompt, task,
                null);
        var body = composed.request().body();
        var document = JsonDocument.parse(body.getBytes(StandardCharsets.UTF_8), "request");
        var systemContent = document.member("messages").orElseThrow().elements().stream()
                .filter(message -> message.member("role").flatMap(JsonNode::text).filter("system"::equals).isPresent())
                .findFirst().orElseThrow().member("content").orElseThrow().text().orElseThrow();

        assertAll(
                () -> assertTrue(composed.isComposed(), "the request is composed"),
                () -> assertNull(composed.refusal(), "a composed request has no refusal"),
                () -> assertTrue(body.contains("\"stream\":false"), "streaming is explicitly off"),
                () -> assertTrue(body.contains("\"role\":\"system\""), "the first message is the system message"),
                () -> assertTrue(body.contains("\"role\":\"user\""), "the task is sent as the user message"),
                () -> assertEquals(prompt, systemContent, "the system message carries the launch prompt"),
                () -> assertTrue(body.contains("\"model\":\"mid\""), "the model tier is sent"),
                () -> assertTrue(body.contains("\"max_tokens\":1024"), "the default max tokens are sent"),
                () -> assertTrue(body.contains("\"temperature\":0"), "the default temperature is sent"),
                () -> assertFalse(body.contains("\"tools\""), "no tools member is sent"),
                () -> assertFalse(body.contains("\"tool_choice\""), "no tool choice is sent"),
                () -> assertFalse(body.contains("\"response_format\""), "no response format is sent"));
    }

    @Test
    @DisplayName("the result schema is the response format where the endpoint accepts one")
    void resultSchemaSent() {
        var body = EndpointRequest.compose(EndpointFixtures.profile(), "mid", prompt, task, schema())
                .request().body();

        assertAll(
                () -> assertTrue(body.contains("\"response_format\""), "the response format is sent"),
                () -> assertTrue(body.contains("\"json_schema\""), "the response format names json_schema"),
                () -> assertTrue(body.contains("\"strict\":true"), "the schema is sent strict"));
    }

    @Test
    @DisplayName("a profile whose result schema is none sends none")
    void noSchemaWhereTheEndpointRefusesIt() {
        var profile = new EndpointProfile("local-runner", "ollama",
                new EndpointProfile.Identity(List.of("--version"), "version ([0-9.]+)"), null,
                new EndpointProfile.Endpoint("openai_chat", EndpointFixtures.BASE_URL, null, "/models",
                        "/models", "none", 8192, 1024, 30),
                Map.of(), null);

        var body = EndpointRequest.compose(profile, "mid", prompt, task, schema()).request().body();

        assertFalse(body.contains("\"response_format\""),
                "a profile without a schema sends no response format");
    }

    @Test
    @DisplayName("a launch prompt that alone does not fit the context budget refuses the composition")
    void promptExceedingBudget() {
        var longPrompt = Generators.letterStrings(EndpointRequest.CHARS_PER_TOKEN * 7168 + 4,
                EndpointRequest.CHARS_PER_TOKEN * 7168 + 4).next();

        var composed = EndpointRequest.compose(EndpointFixtures.profile(), "mid", longPrompt, task, null);

        assertAll(
                () -> assertFalse(composed.isComposed(), "an oversized prompt refuses the composition"),
                () -> assertEquals(EndpointRequest.Refusal.PROMPT_EXCEEDS_BUDGET, composed.refusal(),
                        "the refusal is a prompt budget overflow"),
                () -> assertTrue(composed.detail().contains("context budget"),
                        "the detail names the context budget"));
    }

    @Test
    @DisplayName("a task that does not fit the context budget refuses the composition")
    void taskExceedingBudget() {
        var longTask = Generators.letterStrings(EndpointRequest.CHARS_PER_TOKEN * 7168 + 4,
                EndpointRequest.CHARS_PER_TOKEN * 7168 + 4).next();

        var composed = EndpointRequest.compose(EndpointFixtures.profile(), "mid", prompt, longTask, null);

        assertAll(
                () -> assertFalse(composed.isComposed(), "an oversized task refuses the composition"),
                () -> assertEquals(EndpointRequest.Refusal.TASK_EXCEEDS_BUDGET, composed.refusal(),
                        "the refusal is a task budget overflow"));
    }

    @Test
    @DisplayName("the tool-call probe declares one tool and no tool choice")
    void probeDeclaresOneTool() {
        var body = EndpointRequest.compose(EndpointFixtures.profile(), "mid", prompt, task, null)
                .request().bodyWithTools(EndpointVerification.PROBE_TOOL);

        assertAll(
                () -> assertTrue(body.contains("\"tools\""), "the probe declares a tools member"),
                () -> assertTrue(body.contains("probe_tool"), "the probe declares its own tool"),
                () -> assertFalse(body.contains("\"tool_choice\""), "the probe sends no tool choice"));
    }

    @Test
    @DisplayName("the estimate rounds up at four characters per token")
    void estimateRoundsUp() {
        assertAll(
                () -> assertEquals(0, EndpointRequest.estimate(null), "a null text estimates no tokens"),
                () -> assertEquals(0, EndpointRequest.estimate(""), "an empty text estimates no tokens"),
                () -> assertEquals(1, EndpointRequest.estimate("abc"), "three characters estimate one token"),
                () -> assertEquals(2, EndpointRequest.estimate("abcde"), "five characters estimate two tokens"));
    }
}
