/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityEventMapping;

/**
 * The runtime maps a line of a worker's output stream onto the closed activity kinds through the harness's pointers and
 * never keeps a tool result's content or a whole argument list (Client Profiles § What a Worker Does).
 */
@DisplayName("Activity event mapper")
class ActivityEventMapperTest {

    private static final String EVENTS = """
            {
              "kind_pointer": "/type",
              "kinds": {
                "text": { "match": ["assistant"], "text": "/message/content" },
                "tool_call": { "match": ["tool_use"], "tool": "/name", "target": ["/input/file_path", "/input/command"] },
                "tool_result": { "match": ["tool_result"], "size": "/size", "is_error": "/failed" },
                "file_changed": { "match": ["file"], "path": "/path" },
                "turn": { "match": ["result"], "stop_reason": "/stop_reason" }
              }
            }
            """;

    private static final Map<String, String> USAGE = usagePointers();

    private static Map<String, String> usagePointers() {
        var pointers = new LinkedHashMap<String, String>();
        pointers.put("input_tokens", "/usage/input_tokens");
        pointers.put("output_tokens", "/usage/output_tokens");
        pointers.put("cache_read", "/usage/cache_read_input_tokens");
        pointers.put("cache_creation", "not_applicable");
        return pointers;
    }

    private static ActivityEventMapper mapper() {
        var mapping = ActivityEventMapping.of(
                JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events")).orElseThrow();
        return new ActivityEventMapper(mapping, USAGE);
    }

    @Test
    @DisplayName("a harness without an event stream maps nothing")
    void noMapping() {
        assertFalse(new ActivityEventMapper(null, Map.of()).mapsEvents());
        assertTrue(new ActivityEventMapper(null, Map.of()).map("{\"type\":\"assistant\"}").isEmpty());
    }

    @Test
    @DisplayName("a text line is mapped and cut to the compiled bound")
    void textLine() {
        var longText = "a".repeat(ActivityEntry.TEXT_BOUND + 10);
        var mapped = mapper().map("{\"type\":\"assistant\",\"message\":{\"content\":\"" + longText + "\"}}")
                .orElseThrow();

        assertAll(
                () -> assertEquals(ActivityEntry.Kind.TEXT, mapped.kind()),
                () -> assertEquals(ActivityEntry.TEXT_BOUND, mapped.text().length()),
                () -> assertTrue(mapped.truncated()));
    }

    @Test
    @DisplayName("a short text line is not marked truncated")
    void shortText() {
        var mapped = mapper().map("{\"type\":\"assistant\",\"message\":{\"content\":\"hello\"}}").orElseThrow();

        assertAll(
                () -> assertFalse(mapped.truncated()),
                () -> assertEquals("hello", mapped.text()));
    }

    @Test
    @DisplayName("a tool call keeps the tool's name and the first target, never the arguments")
    void toolCall() {
        var mapped = mapper().map("""
                {"type":"tool_use","name":"read_file","input":{"file_path":"src/A.java","pattern":"secret"}}
                """).orElseThrow();

        assertAll(
                () -> assertEquals(ActivityEntry.Kind.TOOL_CALL, mapped.kind()),
                () -> assertEquals("read_file", mapped.tool().name()),
                () -> assertEquals("src/A.java", mapped.tool().target()));
    }

    @Test
    @DisplayName("a tool call falls back to the next target pointer")
    void toolCallFallsBack() {
        var mapped = mapper().map("{\"type\":\"tool_use\",\"name\":\"bash\",\"input\":{\"command\":\"mvn verify\"}}")
                .orElseThrow();

        assertEquals("mvn verify", mapped.tool().target());
    }

    @Test
    @DisplayName("a tool result keeps the size and the failure, never the content")
    void toolResult() {
        var mapped = mapper().map("{\"type\":\"tool_result\",\"size\":4096,\"failed\":true,\"content\":\"secret\"}")
                .orElseThrow();

        assertAll(
                () -> assertEquals(ActivityEntry.Kind.TOOL_RESULT, mapped.kind()),
                () -> assertEquals(4096, mapped.result().bytes()),
                () -> assertTrue(mapped.result().failed()));
    }

    @Test
    @DisplayName("a file change keeps the path")
    void fileChanged() {
        var mapped = mapper().map("{\"type\":\"file\",\"path\":\"src/A.java\"}").orElseThrow();

        assertEquals("src/A.java", mapped.path());
    }

    @Test
    @DisplayName("a turn maps its stop reason and reads its usage through the pointers")
    void turn() {
        var mapped = mapper().map("""
                {"type":"result","stop_reason":"max_tokens",
                 "usage":{"input_tokens":120,"output_tokens":30,"cache_read_input_tokens":7,"cost":99}}
                """).orElseThrow();

        assertAll(
                () -> assertEquals(ActivityEntry.Kind.TURN, mapped.kind()),
                () -> assertEquals(ActivityEntry.StopReason.MAX_TOKENS, mapped.stopReason()),
                () -> assertEquals("{\"cache_creation\":\"not_applicable\",\"cache_read\":7,"
                        + "\"cost_usd\":\"unmeasured\",\"input_tokens\":120,\"output_tokens\":30,"
                        + "\"reasoning\":\"unmeasured\",\"tool_uses\":\"unmeasured\"}", mapped.usage()));
    }

    @Test
    @DisplayName("an unknown stop reason is other and an absent one is null")
    void stopReason() {
        assertAll(
                () -> assertEquals(ActivityEntry.StopReason.OTHER,
                        ActivityEventMapper.stopReason("pause_turn")),
                () -> assertNull(mapper().map("{\"type\":\"result\"}").orElseThrow().stopReason()),
                () -> assertEquals(ActivityEntry.StopReason.REFUSAL,
                        ActivityEventMapper.stopReason("refusal")));
    }

    @Test
    @DisplayName("a line that matches no kind, is malformed, or lacks a field is counted, not shown")
    void unmapped() {
        var mapper = mapper();

        assertAll(
                () -> assertTrue(mapper.map("{\"type\":\"unknown\"}").isEmpty()),
                () -> assertTrue(mapper.map("not json").isEmpty()),
                () -> assertTrue(mapper.map("{\"type\":\"tool_use\",\"input\":{}}").isEmpty()),
                () -> assertTrue(mapper.map("{\"type\":\"tool_result\",\"size\":\"big\"}").isEmpty()),
                () -> assertTrue(mapper.map("").isEmpty()),
                () -> assertTrue(mapper.map(null).isEmpty()));
    }

    @Test
    @DisplayName("a JSON pointer resolves escapes and array indices")
    void pointerResolution() {
        var root = JsonDocument.parse("""
                {"a/b":{"c~d":[10,20]}}
                """.getBytes(StandardCharsets.UTF_8), "line");

        assertAll(
                () -> assertEquals(10, ActivityEventMapper.resolve(root, "/a~1b/c~0d/0").orElseThrow()
                        .integerValue().orElse(-1)),
                () -> assertEquals(20, ActivityEventMapper.resolve(root, "/a~1b/c~0d/1").orElseThrow()
                        .integerValue().orElse(-1)),
                () -> assertTrue(ActivityEventMapper.resolve(root, "/a~1b/c~0d/2").isEmpty()),
                () -> assertTrue(ActivityEventMapper.resolve(root, "a/b").isEmpty()));
    }
}
