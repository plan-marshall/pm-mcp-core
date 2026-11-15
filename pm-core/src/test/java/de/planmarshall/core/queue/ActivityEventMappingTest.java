/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;

/**
 * The events mapping a client profile declares is projected onto the closed activity kinds, so the runtime reads the
 * pointers and never the profile document (Client Profiles § What a Worker Does).
 */
// Every datum in this class is a contract: the event-mapping documents, their JSON members and harness kind names.
@DisplayName("Activity event mapping")
class ActivityEventMappingTest {

    private static final String EVENTS = """
            {
              "kind_pointer": "/type",
              "kinds": {
                "text": { "match": ["assistant"], "text": "/message/content" },
                "tool_call": { "match": ["tool_use"], "tool": "/name", "target": ["/input/path", "/input/command"] },
                "tool_result": { "match": ["tool_result"], "size": "/size", "is_error": "/failed" },
                "file_changed": { "match": ["file"], "path": "/path" },
                "turn": { "match": ["result"], "stop_reason": "/stop_reason" }
              }
            }
            """;

    private static ActivityEventMapping mapping() {
        return ActivityEventMapping.of(JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events"))
                .orElseThrow();
    }

    @Test
    @DisplayName("a null events value is a harness without an event stream")
    void nullMapping() {
        assertTrue(ActivityEventMapping.of(
                        JsonDocument.parse("null".getBytes(StandardCharsets.UTF_8), "events")).isEmpty(),
                "A null events value yields no mapping");
    }

    @Test
    @DisplayName("each harness kind keeps its match values and its pointers")
    void projectsKinds() {
        var mapping = mapping();

        assertAll(
                () -> assertEquals("/type", mapping.kindPointer(),
                        "kind pointer is the JSON pointer /type"),
                () -> assertEquals(Optional.of(ActivityEntry.Kind.TEXT), mapping.kindOf("assistant"),
                        "assistant matches the text kind"),
                () -> assertEquals(Optional.of(ActivityEntry.Kind.TOOL_CALL), mapping.kindOf("tool_use"),
                        "tool_use matches the tool_call kind"),
                () -> assertEquals(Optional.of(ActivityEntry.Kind.TOOL_RESULT), mapping.kindOf("tool_result"),
                        "tool_result matches the tool_result kind"),
                () -> assertEquals(Optional.of(ActivityEntry.Kind.FILE_CHANGED), mapping.kindOf("file"),
                        "a file event maps to the file_changed kind"),
                () -> assertEquals(Optional.of(ActivityEntry.Kind.TURN), mapping.kindOf("result"),
                        "a result event maps to the turn kind"),
                () -> assertEquals(Optional.empty(), mapping.kindOf("unknown"),
                        "an unknown harness kind yields no kind"),
                () -> assertEquals(Optional.empty(), mapping.kindOf(null),
                        "a null harness kind yields no kind"),
                () -> assertEquals("/message/content",
                        mapping.kinds().get(ActivityEntry.Kind.TEXT).textPointer(),
                        "the text kind points at /message/content"),
                () -> assertEquals(2, mapping.kinds().get(ActivityEntry.Kind.TOOL_CALL).targetPointers().size(),
                        "the tool_call kind keeps two target pointers"),
                () -> assertEquals("/failed",
                        mapping.kinds().get(ActivityEntry.Kind.TOOL_RESULT).isErrorPointer(),
                        "the tool_result kind reads /failed"),
                () -> assertEquals("/stop_reason",
                        mapping.kinds().get(ActivityEntry.Kind.TURN).stopReasonPointer(),
                        "the turn kind reads /stop_reason"));
    }

    @Test
    @DisplayName("a null stop_reason pointer is kept as no pointer")
    void nullStopReasonPointer() {
        var mapping = ActivityEventMapping.of(JsonDocument.parse("""
                {
                  "kind_pointer": "/type",
                  "kinds": {
                    "text": { "match": [], "text": "/text" },
                    "tool_call": { "match": [], "tool": "/tool", "target": [] },
                    "tool_result": { "match": [], "size": "/size", "is_error": null },
                    "file_changed": { "match": [], "path": "/path" },
                    "turn": { "match": [], "stop_reason": null }
                  }
                }
                """.getBytes(StandardCharsets.UTF_8), "events")).orElseThrow();

        assertNull(mapping.kinds().get(ActivityEntry.Kind.TURN).stopReasonPointer(),
                "a null stop_reason keeps no pointer");
        assertNull(mapping.kinds().get(ActivityEntry.Kind.TOOL_RESULT).isErrorPointer(),
                "a null is_error keeps no pointer");
    }

    @Test
    @DisplayName("a kind pointer that is not a JSON pointer is refused")
    void refusesNonPointer() {
        var document = JsonDocument.parse("""
                {
                  "kind_pointer": "type",
                  "kinds": {
                    "text": { "match": [], "text": "/text" },
                    "tool_call": { "match": [], "tool": "/tool", "target": [] },
                    "tool_result": { "match": [], "size": "/size", "is_error": null },
                    "file_changed": { "match": [], "path": "/path" },
                    "turn": { "match": [], "stop_reason": null }
                  }
                }
                """.getBytes(StandardCharsets.UTF_8), "events");

        assertThrows(IllegalArgumentException.class, () -> ActivityEventMapping.of(document),
                "kind_pointer type is not a JSON pointer");
    }
}
