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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;

/**
 * Each activity entry has the members of its own kind and no others, its bounds are enforced, and every kind
 * round-trips through the codec.
 */
@DisplayName("Activity entry validation")
class ActivityEntryValidationTest {

    private static ActivityEntry.ActivityEntryBuilder base(ActivityEntry.Source source, ActivityEntry.Kind kind) {
        return ActivityEntry.builder().seq(1).attempt(1).source(source).kind(kind).at(QueueFixtures.AT);
    }

    @Test
    @DisplayName("every kind round-trips")
    void everyKind() {
        var entries = List.of(
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT).text("hello").truncated(true).build(),
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_CALL)
                        .tool(new ActivityEntry.Tool("bash", "ls -la")).build(),
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_RESULT)
                        .result(new ActivityEntry.Result(12, false)).build(),
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.FILE_CHANGED).path("src/Main.java").build(),
                base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TURN)
                        .stopReason(ActivityEntry.StopReason.END_TURN)
                        .usage("{\"input_tokens\":12,\"note\":\"ü\"}").build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.LINK)
                        .link(new ActivityEntry.Link(ActivityEntry.LinkRelation.ACK, "TASK-007")).build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.QUESTION).questionId("Q-001").build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ANSWER_ACK).questionId("Q-001").build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.SIGNAL)
                        .signal(new ActivityEntry.Signal(SignalName.AGENT_STARTED, SignalAction.RESTART)).build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ATTEMPT_END).end("off_track")
                        .unmappedLines(2).build());

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
                () -> assertThrows(IllegalArgumentException.class, () -> ActivityEntry.builder().seq(0)
                        .attempt(1).source(ActivityEntry.Source.SERVER).kind(ActivityEntry.Kind.ATTEMPT_END)
                        .end("closed").at(QueueFixtures.AT).build()),
                () -> assertThrows(IllegalArgumentException.class, () -> ActivityEntry.builder().seq(1)
                        .attempt(0).source(ActivityEntry.Source.SERVER).kind(ActivityEntry.Kind.ATTEMPT_END)
                        .end("closed").at(QueueFixtures.AT).build()));
    }
}
