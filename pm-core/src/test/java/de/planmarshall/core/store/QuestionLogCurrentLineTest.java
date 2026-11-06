/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static de.planmarshall.core.store.QuestionRecordCodecTest.answered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.asked;
import static de.planmarshall.core.store.QuestionRecordCodecTest.delivered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.id;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The current line of a question (PM-WF-12, PM-IMPL-2): its last readable line, read past a refused line and before a
 * torn tail, with the lines before it left as they were. The question ids of the set and the refused and torn JSONL
 * lines are wire contracts; a question outside the set is generated.
 */
@EnableGeneratorController
@DisplayName("Question log: the current line")
class QuestionLogCurrentLineTest extends QuestionLogFixture {

    @Test
    @DisplayName("is the last readable line, each change a new line that leaves the lines before it as they were")
    void lastLineIsCurrent() throws Exception {
        askSet();
        var asked = content();
        write(t -> log.deliver(t, List.of(delivered("Q-001", 1))));
        write(t -> log.answer(t, List.of(answered("Q-001", 1))));
        var notAsked = id("Q-%03d", Generators.integers(4, 999).next());

        var lines = log.read();

        assertAll(
                () -> assertArrayEquals(asked, Arrays.copyOf(content(), asked.length),
                        "the asked lines should stay as they were"),
                () -> assertEquals(List.of(asked("Q-001", "Q-001"), delivered("Q-001", 1), answered("Q-001", 1)),
                        lines.of("Q-001"), "the question should have its three lines"),
                () -> assertEquals(Optional.of(answered("Q-001", 1)), lines.current("Q-001"),
                        "the answer should be the current line"),
                () -> assertEquals(Optional.of(asked("Q-002", "Q-001")), lines.current("Q-002"),
                        "the asked line of Q-002 should be current"),
                () -> assertEquals(Optional.empty(), lines.current(notAsked),
                        "a question not asked should have no line"),
                () -> assertTrue(lines.isClosed("Q-001"), "the answered question should be closed"),
                () -> assertFalse(lines.isClosed("Q-002"), "the unanswered question should be open"),
                () -> assertEquals(OptionalInt.empty(), lines.currentRevision(notAsked),
                        "a question not asked should have no revision"));
    }

    @Test
    @DisplayName("is read past a refused line, which keeps its line number, and before a torn tail (wire lines)")
    void refusedLineAndTornTail() throws Exception {
        askSet();
        Files.write(log.store(), "{\"format_version\":1,\"event\":\"expired\"}\n".getBytes(UTF_8),
                StandardOpenOption.APPEND);
        var size = Files.size(log.store());
        Files.write(log.store(), "{\"format_version\":1,".getBytes(UTF_8), StandardOpenOption.APPEND);

        var lines = log.read();

        assertAll(
                () -> assertEquals(3, lines.records().size(), "the three asked lines should be read"),
                () -> assertEquals(List.of(4L), lines.refused().stream().map(JsonlStoreFile.Refused::line)
                        .toList(), "the refused line should keep its number 4"),
                () -> assertEquals(OptionalLong.of(size), lines.tornTailOffset(),
                        "the torn tail should start after line 4"),
                () -> assertEquals(Optional.of(asked("Q-003", "Q-001")), lines.current("Q-003"),
                        "the asked line of Q-003 should be current"));
    }

    @Test
    @DisplayName("of a log that does not exist is none")
    void absent() {
        var lines = log.read();

        assertAll(
                () -> assertEquals(List.of(), lines.entries(), "an absent log should have no entries"),
                () -> assertEquals(OptionalLong.empty(), lines.tornTailOffset(),
                        "an absent log should have no torn tail"));
    }
}
