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

import static de.planmarshall.core.store.QuestionRecordCodecTest.adjudicated;
import static de.planmarshall.core.store.QuestionRecordCodecTest.answered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.asked;
import static de.planmarshall.core.store.QuestionRecordCodecTest.cancelled;
import static de.planmarshall.core.store.QuestionRecordCodecTest.delivered;
import static de.planmarshall.core.store.QuestionRecordCodecTest.revised;
import static de.planmarshall.core.store.QuestionRecordCodecTest.withdrawn;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.QuestionRefusal.AlreadyAnswered;
import de.planmarshall.core.store.QuestionRefusal.Conflict;

/**
 * The question log of a scope (PM-WF-12, PM-IMPL-2, PM-IMPL-7): one line per event, appended through the transaction
 * of the scope and never changed, the last readable line of a question its current line, and the two invariants the
 * store holds under the lock of the scope, a question closed by exactly one line and an answer naming the current
 * revision, each broken one returned as a {@link QuestionRefusal} value with nothing appended.
 */
@DisplayName("Question log")
class QuestionLogTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private QuestionLog log;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, "a-plan");
        Files.createDirectories(scope.journalFile().getParent());
        log = new QuestionLog(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    /**
     * Runs one write of the log in a transaction of its own, and commits it unless the log refused it; a refused
     * write must have added no target.
     */
    private Optional<QuestionRefusal> write(Function<ScopeTransaction, Optional<QuestionRefusal>> action) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            var refusal = action.apply(write);
            if (refusal.isEmpty()) {
                write.commit();
            } else {
                assertFalse(write.isTarget(log.store()), "a refused write adds nothing to the transaction");
            }
            return refusal;
        }
    }

    /** Asks the set Q-001 of three questions: the decision on Q-001, the parts Q-002 and Q-003. */
    private void askSet() {
        assertEquals(Optional.empty(), write(t -> log.ask(t, List.of(asked("Q-001", "Q-001"),
                asked("Q-002", "Q-001"), asked("Q-003", "Q-001")))));
    }

    private byte[] content() throws IOException {
        return Files.readAllBytes(log.store());
    }

    @Nested
    @DisplayName("a revision")
    class Revision {

        @Test
        @DisplayName("appends a revised line for every question of its set, all with the new revision")
        void revisesWholeSet() throws Exception {
            askSet();
            var before = content();

            var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                    revised("Q-003", 2))));

            var lines = log.read();
            assertAll(
                    () -> assertEquals(Optional.empty(), refusal),
                    () -> assertArrayEquals(before, Arrays.copyOf(content(), before.length)),
                    () -> assertEquals(6, lines.records().size()),
                    () -> assertEquals(List.of(OptionalInt.of(2), OptionalInt.of(2), OptionalInt.of(2)),
                            List.of(lines.currentRevision("Q-001"), lines.currentRevision("Q-002"),
                                    lines.currentRevision("Q-003"))),
                    () -> assertEquals(Optional.of(revised("Q-003", 2)), lines.current("Q-003")));
        }

        @Test
        @DisplayName("of a set with a closed question revises the open ones only")
        void revisesOpenQuestions() {
            askSet();
            write(t -> log.answer(t, List.of(answered("Q-002", 1))));

            var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-003", 2))));

            assertAll(
                    () -> assertEquals(Optional.empty(), refusal),
                    () -> assertEquals(OptionalInt.of(1), log.read().currentRevision("Q-002")),
                    () -> assertEquals(OptionalInt.of(2), log.read().currentRevision("Q-003")));
        }

        @Test
        @DisplayName("of a closed question is refused already_answered, and nothing is appended")
        void closedQuestion() throws Exception {
            askSet();
            write(t -> log.answer(t, List.of(answered("Q-002", 1))));
            var before = content();

            var refusal = write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2),
                    revised("Q-003", 2))));

            assertAll(
                    () -> assertEquals(Optional.of(new AlreadyAnswered("Q-002")), refusal),
                    () -> assertArrayEquals(before, content()));
        }

        @Test
        @DisplayName("of a part of the set, with two revisions, or not above the current one is a fault of the caller")
        void malformedRevision() throws Exception {
            askSet();
            var before = content();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2))))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 3),
                                    revised("Q-003", 2))))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(t -> log.revise(t, List.of(revised("Q-001", 1), revised("Q-002", 1),
                                    revised("Q-003", 1))))),
                    () -> assertArrayEquals(before, content()));
        }
    }

    @Nested
    @DisplayName("a closing line")
    class Closing {

        @Test
        @DisplayName("a second one is refused already_answered, and nothing is appended")
        void secondClosingLine() throws Exception {
            askSet();
            write(t -> log.answer(t, List.of(answered("Q-001", 1))));
            var before = content();

            var again = write(t -> log.answer(t, List.of(answered("Q-001", 1))));
            var withdrawal = write(t -> log.withdraw(t, List.of(withdrawn("Q-001"))));

            assertAll(
                    () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), again),
                    () -> assertEquals(AlreadyAnswered.CODE, again.orElseThrow().code()),
                    () -> assertEquals("already_answered", AlreadyAnswered.CODE),
                    () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), withdrawal),
                    () -> assertArrayEquals(before, content()));
        }

        @Test
        @DisplayName("two of them in one append are refused, and neither is appended")
        void twoInOneAppend() throws Exception {
            askSet();
            var before = content();

            var refusal = write(t -> log.append(t, List.of(answered("Q-002", 1), withdrawn("Q-002"))));

            assertAll(
                    () -> assertEquals(Optional.of(new AlreadyAnswered("Q-002")), refusal),
                    () -> assertArrayEquals(before, content()));
        }

        @Test
        @DisplayName("an answer after cancelled is refused, and nothing is appended")
        void answerAfterCancelled() throws Exception {
            askSet();
            write(t -> log.cancel(t, List.of(cancelled("Q-001"), cancelled("Q-002"), cancelled("Q-003"))));
            var before = content();

            var refusal = write(t -> log.answer(t, List.of(answered("Q-001", 1))));

            assertAll(
                    () -> assertEquals(Optional.of(new AlreadyAnswered("Q-001")), refusal),
                    () -> assertArrayEquals(before, content()),
                    () -> assertTrue(log.read().isClosed("Q-001")));
        }
    }

    @Nested
    @DisplayName("the revision of an answer")
    class AnswerRevision {

        @Test
        @DisplayName("of an older revision is refused conflict, carrying the current revision")
        void olderRevision() throws Exception {
            askSet();
            write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2), revised("Q-003", 2))));
            var before = content();

            var refusal = write(t -> log.answer(t, List.of(answered("Q-001", 1))));

            assertAll(
                    () -> assertEquals(Optional.of(new Conflict("Q-001", 2)), refusal),
                    () -> assertEquals("conflict", refusal.orElseThrow().code()),
                    () -> assertArrayEquals(before, content()),
                    () -> assertFalse(log.read().isClosed("Q-001")));
        }

        @Test
        @DisplayName("of a revision not yet made, an adjudication or a delivery of an old one are refused conflict")
        void otherRevisions() {
            askSet();
            write(t -> log.revise(t, List.of(revised("Q-001", 2), revised("Q-002", 2), revised("Q-003", 2))));

            assertAll(
                    () -> assertEquals(Optional.of(new Conflict("Q-001", 2)),
                            write(t -> log.answer(t, List.of(answered("Q-001", 3))))),
                    () -> assertEquals(Optional.of(new Conflict("Q-002", 2)),
                            write(t -> log.adjudicate(t, List.of(adjudicated("Q-002", 1))))),
                    () -> assertEquals(Optional.of(new Conflict("Q-003", 2)),
                            write(t -> log.deliver(t, List.of(delivered("Q-003", 1))))));
        }

        @Test
        @DisplayName("of the current revision is appended with its adjudication in one append")
        void adjudicatedAndAnswered() {
            askSet();

            var refusal = write(t -> log.append(t, List.of(adjudicated("Q-001", 1), answered("Q-001", 1))));

            assertAll(
                    () -> assertEquals(Optional.empty(), refusal),
                    () -> assertEquals(Optional.of(answered("Q-001", 1)), log.read().current("Q-001")));
        }

        @Test
        @DisplayName("a conflict names a revision from 1")
        void conflictRevision() {
            assertThrows(IllegalArgumentException.class, () -> new Conflict("Q-001", 0));
        }
    }

    @Nested
    @DisplayName("the current line")
    class CurrentLine {

        @Test
        @DisplayName("is the last readable line, each change a new line that leaves the lines before it as they were")
        void lastLineIsCurrent() throws Exception {
            askSet();
            var asked = content();
            write(t -> log.deliver(t, List.of(delivered("Q-001", 1))));
            write(t -> log.answer(t, List.of(answered("Q-001", 1))));

            var lines = log.read();

            assertAll(
                    () -> assertArrayEquals(asked, Arrays.copyOf(content(), asked.length)),
                    () -> assertEquals(List.of(asked("Q-001", "Q-001"), delivered("Q-001", 1), answered("Q-001", 1)),
                            lines.of("Q-001")),
                    () -> assertEquals(Optional.of(answered("Q-001", 1)), lines.current("Q-001")),
                    () -> assertEquals(Optional.of(asked("Q-002", "Q-001")), lines.current("Q-002")),
                    () -> assertEquals(Optional.empty(), lines.current("Q-004")),
                    () -> assertTrue(lines.isClosed("Q-001")),
                    () -> assertFalse(lines.isClosed("Q-002")),
                    () -> assertEquals(OptionalInt.empty(), lines.currentRevision("Q-004")));
        }

        @Test
        @DisplayName("is read past a refused line, which keeps its line number, and before a torn tail")
        void refusedLineAndTornTail() throws Exception {
            askSet();
            Files.write(log.store(), "{\"format_version\":1,\"event\":\"expired\"}\n".getBytes(UTF_8),
                    StandardOpenOption.APPEND);
            var size = Files.size(log.store());
            Files.write(log.store(), "{\"format_version\":1,".getBytes(UTF_8), StandardOpenOption.APPEND);

            var lines = log.read();

            assertAll(
                    () -> assertEquals(3, lines.records().size()),
                    () -> assertEquals(List.of(4L), lines.refused().stream().map(JsonlStoreFile.Refused::line)
                            .toList()),
                    () -> assertEquals(OptionalLong.of(size), lines.tornTailOffset()),
                    () -> assertEquals(Optional.of(asked("Q-003", "Q-001")), lines.current("Q-003")));
        }

        @Test
        @DisplayName("of a log that does not exist is none")
        void absent() {
            var lines = log.read();

            assertAll(
                    () -> assertEquals(List.of(), lines.entries()),
                    () -> assertEquals(OptionalLong.empty(), lines.tornTailOffset()));
        }
    }

    @Nested
    @DisplayName("a line malformed for the log")
    class Malformed {

        @Test
        @DisplayName("a question asked twice, or a line about a question the log has not asked, is refused")
        void unknownOrRepeatedQuestion() {
            askSet();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(t -> log.ask(t, List.of(asked("Q-002", "Q-002"))))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(t -> log.answer(t, List.of(answered("Q-009", 1))))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(t -> log.ask(t, List.of(asked("Q-005", "Q-004"))))));
        }

        @Test
        @DisplayName("a set not named by its first question, a line of another event, or no line is refused")
        void wrongShape() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(t -> log.ask(t, List.of(asked("Q-002", "Q-001"), asked("Q-001", "Q-001"))))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> write(t -> log.answer(t, List.of(delivered("Q-001", 1))))),
                    () -> assertThrows(IllegalArgumentException.class, () -> write(t -> log.append(t, List.of()))),
                    () -> assertFalse(Files.exists(log.store())));
        }

        @Test
        @DisplayName("an append through a transaction of another scope is refused")
        void otherScope() throws Exception {
            var other = JournalScope.plan(base, PROJECT, "other-plan");
            Files.createDirectories(other.journalFile().getParent());
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(other.lockKey());
                var write = ScopeTransaction.open(transaction, other, audit, CLOCK);
                var questions = List.of(asked("Q-001", "Q-001"));

                assertThrows(IllegalArgumentException.class, () -> log.ask(write, questions));
            }
        }
    }

    @Test
    @DisplayName("lies at the path base of a plan or an epic, below workspace/ and below pending/<entry_id>/")
    void location() {
        var entry = new EntryId("ent-0123456789abcdef");
        var workspace = JournalScope.workspace(base, PROJECT);
        var epic = JournalScope.epic(base, PROJECT, "an-epic");

        assertAll(
                () -> assertEquals(scope.pathBase().resolve("questions.jsonl"), log.store()),
                () -> assertEquals("questions.jsonl", new QuestionLog(epic).storePath()),
                () -> assertEquals(epic.pathBase().resolve("questions.jsonl"), new QuestionLog(epic).store()),
                () -> assertEquals("workspace/questions.jsonl", new QuestionLog(workspace).storePath()),
                () -> assertEquals("pending/ent-0123456789abcdef/questions.jsonl",
                        QuestionLog.ofEntry(workspace, entry).storePath()),
                () -> assertThrows(IllegalArgumentException.class, () -> QuestionLog.ofEntry(scope, entry)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QuestionLog(JournalScope.lessons(base, PROJECT))));
    }
}
