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
        var destination = Generators.letterStrings(3, 32).next() + ":443";
        var launchModel = "launch-" + Generators.letterStrings(1, 16).next();
        var servedModel = "served-" + Generators.letterStrings(1, 16).next();
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
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.EGRESS)
                        .egress(new ActivityEntry.Egress(destination)).build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.MODEL_DIFFERS)
                        .launchModel(launchModel).servedModel(servedModel).build(),
                base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ATTEMPT_END).end("off_track")
                        .unmappedLines(unmappedLines).build());

        assertEquals(EnumSet.allOf(ActivityEntry.Kind.class),
                entries.stream().map(ActivityEntry::kind).collect(Collectors.toSet()),
                "The fixture covers every kind");

        for (var entry : entries) {
            assertEquals(entry, ActivityEntryCodec.read(Path.of("entry.jsonl"), ActivityEntryCodec.write(entry)),
                    "every kind survives a write and read round trip");
        }
    }

    @Test
    @DisplayName("a kind is recorded by its own source")
    void source() {
        assertThrows(IllegalArgumentException.class,
                () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.TEXT).text("x").build(),
                "text is recorded by the harness source");
    }

    @Test
    @DisplayName("a member belongs to its own kind")
    void members() {
        var sameModel = Generators.letterStrings(3, 16).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.SIGNAL).text("x")
                                .signal(new ActivityEntry.Signal(SignalName.REPEATING, SignalAction.SHOW)).build(),
                        "a signal entry cannot carry text"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT)
                                .tool(new ActivityEntry.Tool("bash", null)).build(),
                        "a text entry cannot carry a tool"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT).truncated(true)
                                .text("x").end("closed").build(),
                        "a text entry cannot carry an end reason"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.QUESTION).build(),
                        "a question entry needs a question id"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_CALL).build(),
                        "a tool_call entry needs a tool"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.LINK).build(),
                        "a link entry needs a link target"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.SIGNAL).build(),
                        "a signal entry needs a signal"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.SIGNAL)
                                .signal(new ActivityEntry.Signal(SignalName.REPEATING, SignalAction.SHOW))
                                .egress(new ActivityEntry.Egress("example.test:443")).build(),
                        "an egress member belongs to an egress entry"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.EGRESS).build(),
                        "an egress entry needs its destination"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ATTEMPT_END).build(),
                        "an attempt_end entry needs its end reason"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.MODEL_DIFFERS)
                                .launchModel(Generators.letterStrings(1, 32).next()).build(),
                        "a model_differs entry needs both models"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT).text("x")
                                .launchModel(Generators.letterStrings(1, 32).next()).build(),
                        "a text entry cannot carry a launch model"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.MODEL_DIFFERS)
                                .launchModel(sameModel).servedModel(sameModel).build(),
                        "a model_differs entry names two different models"),
                () -> {
                    var marked = "model-" + SecretRedactor.REDACTED + "-x";
                    assertThrows(IllegalArgumentException.class,
                            () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.MODEL_DIFFERS)
                                    .launchModel(marked).servedModel(marked).build(),
                            "a pair that merely carries the marker is refused");
                });
    }

    @Test
    @DisplayName("the bounds of the members are enforced")
    void bounds() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TEXT)
                                .text("a".repeat(2001)).build(),
                        "text beyond the 2000 code point bound is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_CALL)
                                .tool(new ActivityEntry.Tool("a".repeat(129), null)).build(),
                        "a tool name beyond 128 characters is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.FILE_CHANGED)
                                .path("a".repeat(1001)).build(),
                        "a path beyond 1000 characters is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TOOL_RESULT)
                                .result(new ActivityEntry.Result(-1, true)).build(),
                        "a negative result byte count is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.QUESTION).questionId("Q-").build(),
                        "a question id must name its question"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.ATTEMPT_END).end("x")
                                .unmappedLines(-1).build(),
                        "a negative unmapped line count is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.EGRESS)
                                .egress(new ActivityEntry.Egress("a".repeat(260))).build(),
                        "a destination beyond 259 characters is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.EGRESS)
                                .egress(new ActivityEntry.Egress("a:")).build(),
                        "a destination below the 3-character floor is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.EGRESS)
                                .egress(new ActivityEntry.Egress("host:4\n43")).build(),
                        "a destination with a line break is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TURN).usage("[]").build(),
                        "a usage member that is not an object is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TURN).usage("{} {}").build(),
                        "a usage document with two objects is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.HARNESS, ActivityEntry.Kind.TURN)
                                .usage("{\n\"input_tokens\":1}").build(),
                        "a usage member on two lines is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.MODEL_DIFFERS)
                                .launchModel("a".repeat(ActivityEntry.MODEL_BOUND + 1))
                                .servedModel(Generators.letterStrings(3, 16).next()).build(),
                        "a launch model beyond the bound is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> base(ActivityEntry.Source.SERVER, ActivityEntry.Kind.MODEL_DIFFERS)
                                .launchModel("").servedModel(Generators.letterStrings(3, 16).next()).build(),
                        "an empty model identifier is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> ActivityEntry.builder().seq(0)
                        .attempt(1).source(ActivityEntry.Source.SERVER).kind(ActivityEntry.Kind.ATTEMPT_END)
                        .end("closed").at(QueueFixtures.AT).build(), "a sequence of zero is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> ActivityEntry.builder().seq(1)
                        .attempt(0).source(ActivityEntry.Source.SERVER).kind(ActivityEntry.Kind.ATTEMPT_END)
                        .end("closed").at(QueueFixtures.AT).build(), "an attempt of zero is refused"));
    }
}
