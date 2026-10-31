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
 */
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

    /** A recorded output of the harness, written for the test. */
    private static final List<String> RECORDED = List.of(
            "{\"type\":\"assistant\",\"message\":{\"content\":\"I will read the file\"}}",
            "{\"type\":\"tool_use\",\"name\":\"read_file\",\"input\":{\"file_path\":\"src/A.java\"}}",
            "{\"type\":\"tool_result\",\"size\":2048,\"failed\":false}",
            "{\"type\":\"file\",\"path\":\"src/A.java\"}",
            "{\"type\":\"result\",\"stop_reason\":\"tool_use\",\"usage\":{}}");

    private static ActivityEventMapping mapping() {
        return ActivityEventMapping.of(
                JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events")).orElseThrow();
    }

    @Test
    @DisplayName("a recorded output that carries every kind reports them all and passes")
    void allReported() {
        var result = HarnessEventVerification.verify(mapping(), Map.of(), RECORDED);

        assertAll(
                () -> assertTrue(result.passed()),
                () -> assertEquals(Map.of("text", "reported", "tool_call", "reported",
                                "tool_result", "reported", "file_changed", "reported", "turn", "reported"),
                        result.kinds()),
                () -> assertTrue(result.missing().isEmpty()));
    }

    @Test
    @DisplayName("a kind the recorded output never carries is missing and fails the stage")
    void missingKind() {
        var result = HarnessEventVerification.verify(mapping(), Map.of(),
                RECORDED.stream().filter(line -> !line.contains("\"file\"")).toList());

        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals("missing", result.kinds().get("file_changed")),
                () -> assertEquals(List.of("file_changed"), List.copyOf(result.missing())));
    }

    @Test
    @DisplayName("a mapping that does not resolve is missing, and a malformed line reports nothing")
    void unresolvedMapping() {
        var broken = List.of(
                "{\"type\":\"assistant\",\"message\":{}}",
                "{\"type\":\"tool_use\",\"input\":{}}",
                "{\"type\":\"tool_result\",\"size\":\"large\"}",
                "{\"type\":\"file\"}",
                "{\"type\":\"result\"}");

        var result = HarnessEventVerification.verify(mapping(), Map.of(), broken);

        assertAll(
                () -> assertFalse(result.passed()),
                () -> assertEquals(4, result.missing().size()),
                () -> assertEquals("reported", result.kinds().get("turn")));
    }

    @Test
    @DisplayName("a harness without an event stream is not measured by the stage")
    void noEvents() {
        var result = HarnessEventVerification.verify(null, Map.of(), RECORDED);

        assertAll(
                () -> assertTrue(result.passed()),
                () -> assertTrue(result.kinds().isEmpty()));
    }
}
