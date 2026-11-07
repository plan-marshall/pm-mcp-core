/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.queue.ActivityEventMapping;

/**
 * The events stage of the profile verification records each declared activity kind as reported or missing and fails for
 * a mapping that does not resolve on the recorded output (Client Profiles § Profile Verification).
 *
 * <p>
 * The values a recorded output carries (the text, the tool name, the file path, the result size) are generated. The
 * literals that stay are the contract: the event mapping document, the event JSON shape of the recorded lines with its
 * type discriminators, the activity kind names and the stage results {@code reported} and {@code missing}.
 */
@EnableGeneratorController
@DisplayName("Harness event verification")
class HarnessEventVerificationTest {

    private static final String EVENTS = """
            {
              "kind_pointer": "/type",
              "kinds": {
                "text": { "match": ["assistant"], "text": "/message/content" },
                "tool_call": { "match": ["tool_use"], "tool": "/name", "target": ["/input/file_path"] },
                "tool_result": { "match": ["tool_result"], "size": "/size", "is_error": "/failed" },
                "file_changed": { "match": ["file"], "path": "/path" },
                "turn": { "match": ["result"], "stop_reason": "/stop_reason" }
              }
            }
            """;

    /** @return a recorded output of the harness, written for the test, with generated values in the event shape */
    private static List<String> recorded() {
        var text = Generators.letterStrings(2, 8).next() + " " + Generators.letterStrings(2, 8).next() + ".";
        var tool = Generators.letterStrings(3, 12).next();
        var path = "src/" + Generators.letterStrings(1, 12).next() + ".java";
        var size = Generators.integers(0, 1_000_000).next();
        return List.of(
                "{\"type\":\"assistant\",\"message\":{\"content\":\"" + text + "\"}}",
                "{\"type\":\"tool_use\",\"name\":\"" + tool + "\",\"input\":{\"file_path\":\"" + path + "\"}}",
                "{\"type\":\"tool_result\",\"size\":" + size + ",\"failed\":false}",
                "{\"type\":\"file\",\"path\":\"" + path + "\"}",
                "{\"type\":\"result\",\"stop_reason\":\"tool_use\",\"usage\":{}}");
    }

    private static ActivityEventMapping mapping() {
        return ActivityEventMapping.of(
                JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events")).orElseThrow();
    }

    @Test
    @DisplayName("a recorded output that carries every kind reports them all and passes")
    void allReported() {
        var result = HarnessEventVerification.verify(mapping(), Map.of(), recorded());

        assertAll(
                () -> assertTrue(result.passed(), "an output with every kind passes"),
                () -> assertEquals(Map.of("text", "reported", "tool_call", "reported",
                                "tool_result", "reported", "file_changed", "reported", "turn", "reported"),
                        result.kinds(), "every declared kind is reported"),
                () -> assertTrue(result.missing().isEmpty(), "no kind is missing from the output"));
    }

    @Test
    @DisplayName("a kind the recorded output never carries is missing and fails the stage")
    void missingKind() {
        var result = HarnessEventVerification.verify(mapping(), Map.of(),
                recorded().stream().filter(line -> !line.contains("\"type\":\"file\"")).toList());

        assertAll(
                () -> assertFalse(result.passed(), "an output without a kind fails the stage"),
                () -> assertEquals("missing", result.kinds().get("file_changed"),
                        "the absent kind is recorded missing"),
                () -> assertEquals(List.of("file_changed"), List.copyOf(result.missing()),
                        "only the absent kind is listed missing"));
    }

    @Test
    @DisplayName("a mapping that does not resolve is missing, and a malformed line reports nothing")
    void unresolvedMapping() {
        var broken = List.of(
                "{\"type\":\"assistant\",\"message\":{}}",
                "{\"type\":\"tool_use\",\"input\":{}}",
                "{\"type\":\"tool_result\",\"size\":\"" + Generators.letterStrings(1, 8).next() + "\"}",
                "{\"type\":\"file\"}",
                "{\"type\":\"result\"}");

        var result = HarnessEventVerification.verify(mapping(), Map.of(), broken);

        assertAll(
                () -> assertFalse(result.passed(), "unresolved mappings fail the stage"),
                () -> assertEquals(4, result.missing().size(), "four kinds do not resolve on the output"),
                () -> assertEquals("reported", result.kinds().get("turn"), "the turn kind still resolves"));
    }

    @Test
    @DisplayName("a harness without an event stream is not measured by the stage")
    void noEvents() {
        var result = HarnessEventVerification.verify(null, Map.of(), recorded());

        assertAll(
                () -> assertTrue(result.passed(), "a harness without events passes the stage"),
                () -> assertTrue(result.kinds().isEmpty(), "no kind is measured without an event stream"));
    }
}
