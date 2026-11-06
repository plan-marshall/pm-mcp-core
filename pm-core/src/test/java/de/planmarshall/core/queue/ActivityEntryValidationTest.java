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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;

/**
 * Each activity entry has the members of its own kind and no others, its bounds are enforced, and every kind
 * round-trips through the codec.
 */
@EnableGeneratorController
@SuppressWarnings("java:S5778")
// the assertion table names one throwing builder call per case, by design
@DisplayName("Activity entry validation")
class ActivityEntryValidationTest {

    private static ActivityEntry.ActivityEntryBuilder base(ActivityEntry.Source source, ActivityEntry.Kind kind) {
        return ActivityEntry.builder().seq(1).attempt(1).source(source).kind(kind).at(QueueFixtures.AT);
    }

    @Test
    @DisplayName("every kind round-trips")
    void everyKind() {
        var text = Generators.letterStrings(1, 50).next();
        var toolName = Generators.letterStrings(1, 32).next();
        var toolTarget = Generators.letterStrings(1, 64).next();
        var resultBytes = Generators.integers(0, 100_000).next();
        var failed = Generators.booleans().next();
        var path = "src/" + Generators.letterStrings(1, 60).next() + ".java";
        var inputTokens = Generators.integers(0, 10_000).next();
        var linkTarget = "TASK-" + Generators.integers(100, 999).next();
        var questionId = "Q-" + Generators.integers(100, 999).next();
        var unmappedLines = Generators.integers(0, 100).next();
        var entries = List.of(
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT).text(text).truncated(true).build(),
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_CALL)
                        .tool(new ActivityEntry.Tool(toolName, toolTarget)).build(),
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_RESULT)
                        .result(new ActivityEntry.Result(resultBytes, failed)).build(),
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.FILE_CHANGED).path(path).build(),
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TURN)
                        .stopReason(ActivityEntry.StopReason.END_TURN)
                        .usage("{\"input_tokens\":" + inputTokens + ",\"note\":\"ü\"}").build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.LINK)
                        .link(new ActivityEntry.Link(ActivityEntry.LinkRelation.ACK, linkTarget)).build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.QUESTION).questionId(questionId).build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ANSWER_ACK).questionId(questionId).build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.SIGNAL)
                        .signal(new ActivityEntry.Signal(SignalName.AGENT_STARTED, SignalAction.RESTART)).build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ATTEMPT_END).end("off_track")
                        .unmappedLines(unmappedLines).build());

        assertEquals(EnumSet.allOf(ActivityEntry.Kind.class),
                entries.stream().map(ActivityEntry::kind).collect(Collectors.toSet()),
                "The fixture covers every kind");

        for (var entry : entries) {
            assertEquals(entry, ActivityEntryCodec.read(Path.of("entry.jsonl"), ActivityEntryCodec.write(entry)),
                    entry.kind().wireName());
        }
    }

    @Test
    @DisplayName("a kind is recorded by its own source")
    void source() {
        assertThrows(IllegalArgumentException.class,
                () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.TEXT).text("x").build());
    }

    @Test
    @DisplayName("a member belongs to its own kind")
    void members() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.SIGNAL).text("x")
                                .signal(new ActivityEntry.Signal(SignalName.REPEATING, SignalAction.SHOW)).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT)
                                .tool(new ActivityEntry.Tool("bash", null)).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT).truncated(true)
                                .text("x").end("closed").build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.QUESTION).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_CALL).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.LINK).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.SIGNAL).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ATTEMPT_END).build()));
    }

    @Test
    @DisplayName("the bounds of the members are enforced")
    void bounds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT)
                                .text("a".repeat(2001)).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_CALL)
                                .tool(new ActivityEntry.Tool("a".repeat(129), null)).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.FILE_CHANGED)
                                .path("a".repeat(1001)).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_RESULT)
                                .result(new ActivityEntry.Result(-1, true)).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.QUESTION).questionId("Q-").build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ATTEMPT_END).end("x")
                                .unmappedLines(-1).build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TURN).usage("[]").build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TURN).usage("{} {}").build()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TURN)
                                .usage("{\n\"input_tokens\":1}").build()),
                () -> assertThrows(IllegalArgumentException.class, () -> ActivityEntry.builder().seq(0)
                        .attempt(1).source(ActivityEntry.Source.SERVER).kind(ActivityEntry.Kind.ATTEMPT_END)
                        .end("closed").at(QueueFixtures.AT).build()),
                () -> assertThrows(IllegalArgumentException.class, () -> ActivityEntry.builder().seq(1)
                        .attempt(0).source(ActivityEntry.Source.SERVER).kind(ActivityEntry.Kind.ATTEMPT_END)
                        .end("closed").at(QueueFixtures.AT).build()));
    }
}
