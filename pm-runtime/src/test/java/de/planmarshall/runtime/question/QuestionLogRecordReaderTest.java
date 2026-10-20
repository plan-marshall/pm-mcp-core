/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.question;

import static de.planmarshall.runtime.question.QuestionFixtures.asked;
import static de.planmarshall.runtime.question.QuestionFixtures.closing;
import static de.planmarshall.runtime.question.QuestionFixtures.delivered;
import static de.planmarshall.runtime.question.QuestionFixtures.line;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Open;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Resolved;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Unreadable;
import de.planmarshall.core.spi.QuestionRecordReader.ReadRequest;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.QuestionEvent;
import de.planmarshall.core.store.QuestionLog;
import de.planmarshall.core.store.QuestionRecord;
import de.planmarshall.core.store.QuestionRefusal;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.question.QuestionFixtures.EndOfLine;
import de.planmarshall.runtime.test.TestBases;

/**
 * The adapter of the engine port {@code QuestionRecordReader} over the question log of {@code core.store} (PM-ARCH-4,
 * PM-IMPL-5): the tri-state read over a fixture log, the whole log judged so that a broken log is never reported open,
 * and the scope the request addresses.
 */
@DisplayName("Question log record reader")
class QuestionLogRecordReaderTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "a-plan";
    private static final String EPIC = "an-epic";
    private static final String QUESTION = "Q-001";

    private Path base;
    private JournalScope plan;
    private JournalScope workspace;
    private FileLockManager locks;
    private QuestionLogRecordReader reader;

    @BeforeEach
    void createRuntime() throws IOException {
        base = TestBases.create("pmq-");
        Files.createDirectories(base);
        base = base.toRealPath();
        plan = JournalScope.plan(base, PROJECT, PLAN);
        workspace = JournalScope.workspace(base, PROJECT);
        Files.createDirectories(plan.pathBase());
        Files.createDirectories(plan.journalFile().getParent());
        Files.createDirectories(workspace.pathBase());
        Files.createDirectories(workspace.journalFile().getParent());
        locks = new FileLockManager(event -> {
            // a lock acquisition of these tests records nothing
        });
        reader = QuestionFixtures.reader(base, PROJECT);
    }

    @AfterEach
    void deleteRuntime() {
        TestBases.delete(base);
    }

    private static ReadRequest request(String questionId) {
        return new ReadRequest(Optional.of(PLAN), questionId);
    }

    /** The closing events, derived from the enum at run time: the closed set of resolution outcomes. */
    private static Stream<QuestionEvent> closingEvents() {
        return Arrays.stream(QuestionEvent.values()).filter(QuestionEvent::closes);
    }

    private void append(JournalScope scope, EndOfLine endOfLine, QuestionRecord... records) {
        QuestionFixtures.append(locks, scope, endOfLine, records);
    }

    // ------------------------------------------------------------------ Open

    @Nested
    @DisplayName("an open record")
    class OpenRecord {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Open, also when a later delivery line did not close it")
        void readsOpen(EndOfLine endOfLine) {
            append(plan, endOfLine, asked(QUESTION), delivered(QUESTION));

            var read = reader.read(request(QUESTION));

            assertEquals(new Open(), read, "an asked record with a delivery and no closing line is open");
        }

        @Test
        @DisplayName("reads Open for each part of a multi-part decision")
        void readsOpenPartOfSet() {
            append(plan, EndOfLine.LF, asked(QUESTION), asked("Q-002"));

            assertAll(
                    () -> assertEquals(new Open(), reader.read(request(QUESTION))),
                    () -> assertEquals(new Open(), reader.read(request("Q-002"))));
        }
    }

    // ------------------------------------------------------------------ Resolved

    @Nested
    @DisplayName("a resolved record")
    class ResolvedRecord {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Resolved with the outcome of every closing event, the set derived from the enum")
        void readsEveryOutcome(EndOfLine endOfLine) {
            var expected = closingEvents().map(QuestionEvent::wireName).toList();
            assertEquals(Set.copyOf(expected), Set.of("answered", "withdrawn", "cancelled"),
                    "the closing events are the closed set of resolution outcomes");

            for (var event : closingEvents().toList()) {
                var questionId = "Q-%03d".formatted(event.ordinal() + 1);
                append(plan, endOfLine, asked(questionId), closing(questionId, event));

                var read = reader.read(request(questionId));

                assertEquals(new Resolved(event.wireName()), read,
                        "the adapter returns the outcome the closing line records");
            }
        }

        @Test
        @DisplayName("reads Resolved for a closed question among open ones")
        void readsClosedAmongOpenQuestions() {
            append(plan, EndOfLine.LF, asked(QUESTION), asked("Q-002"), closing(QUESTION, QuestionEvent.ANSWERED));

            assertAll(
                    () -> assertEquals(new Resolved("answered"), reader.read(request(QUESTION))),
                    () -> assertEquals(new Open(), reader.read(request("Q-002"))));
        }
    }

    // ------------------------------------------------------------------ Unreadable

    /** The causes the adapter maps to Unreadable; every one of them is a log it cannot decide. */
    enum UnreadableCause {
        ABSENT_LOG,
        EMPTY_LOG,
        UNPARSEABLE_LINE,
        RECORD_OF_ANOTHER_CLASS,
        STORE_REFUSES,
        TORN_TAIL
    }

    static Stream<Arguments> unreadableCases() {
        return Stream.concat(
                Stream.of(Arguments.of(UnreadableCause.ABSENT_LOG, EndOfLine.LF),
                        Arguments.of(UnreadableCause.EMPTY_LOG, EndOfLine.LF),
                        Arguments.of(UnreadableCause.STORE_REFUSES, EndOfLine.LF),
                        Arguments.of(UnreadableCause.TORN_TAIL, EndOfLine.LF)),
                Stream.of(EndOfLine.values()).flatMap(endOfLine -> Stream.of(
                        Arguments.of(UnreadableCause.UNPARSEABLE_LINE, endOfLine),
                        Arguments.of(UnreadableCause.RECORD_OF_ANOTHER_CLASS, endOfLine))));
    }

    @Nested
    @DisplayName("a log that cannot be read")
    class UnreadableLog {

        @ParameterizedTest(name = "{0} with {1}")
        @MethodSource("de.planmarshall.runtime.question.QuestionLogRecordReaderTest#unreadableCases")
        @DisplayName("reads Unreadable for every cause, never Open")
        void readsUnreadable(UnreadableCause cause, EndOfLine endOfLine) throws IOException {
            var expectedReason = createFixture(cause, endOfLine);

            var read = reader.read(request(QUESTION));

            assertNotEquals(new Open(), read, "a broken log is never reported open");
            assertNotEquals(new Resolved("answered"), read);
            var unreadable = assertInstanceOf(Unreadable.class, read);
            assertEquals(expectedReason, unreadable.reason());
        }

        private String createFixture(UnreadableCause cause, EndOfLine endOfLine) throws IOException {
            var store = new QuestionLog(plan).store();
            switch (cause) {
                case ABSENT_LOG -> {
                    return "the question Q-001 is not in the log";
                }
                case EMPTY_LOG -> {
                    Files.createDirectories(store.getParent());
                    Files.write(store, new byte[0]);
                    return "the question Q-001 is not in the log";
                }
                case UNPARSEABLE_LINE -> {
                    QuestionFixtures.writeRaw(plan, endOfLine, "this is no question record");
                    return "the question log holds a refused line 1";
                }
                case RECORD_OF_ANOTHER_CLASS -> {
                    QuestionFixtures.writeRaw(plan, endOfLine,
                            "{\"format_version\":1,\"waiver_id\":\"W-0001\",\"plan_id\":\"a-plan\"}");
                    return "the question log holds a refused line 1";
                }
                case STORE_REFUSES -> {
                    Files.createDirectories(store);
                    return "the question log questions.jsonl cannot be read";
                }
                case TORN_TAIL -> {
                    var asked = line(asked(QUESTION));
                    Files.createDirectories(store.getParent());
                    Files.writeString(store, asked + "\n" + "{", UTF_8);
                    return "the question log ends in a torn tail at byte " + (asked.getBytes(UTF_8).length + 1);
                }
            }
            throw new IllegalStateException("unhandled cause " + cause);
        }
    }

    @Nested
    @DisplayName("a log holding a refused line")
    class RefusedLine {

        @Test
        @DisplayName("reads Unreadable for an open record followed by an unparseable line, and names the line")
        void openRecordThenUnparseableLine() {
            append(plan, EndOfLine.LF, asked(QUESTION));
            QuestionFixtures.appendLine(plan, "no question record\n");

            var read = reader.read(request(QUESTION));

            var unreadable = assertInstanceOf(Unreadable.class, read,
                    "the whole log is judged, not only the lines of the question");
            assertEquals("the question log holds a refused line 2", unreadable.reason());
        }

        @Test
        @DisplayName("reads Unreadable for a resolved record followed by a refused line (ADR core-store/039)")
        void resolvedRecordThenRefusedLine() {
            append(plan, EndOfLine.LF, asked(QUESTION), closing(QUESTION, QuestionEvent.ANSWERED));
            QuestionFixtures.appendLine(plan, "{\"format_version\":2}\n");

            var refusal = appendRefused(asked("Q-002"));

            assertAll("the refused line refuses an append and the read",
                    () -> assertEquals("unreadable", refusal.code()),
                    () -> assertEquals("the question log holds a refused line 3",
                            ((Unreadable) reader.read(request(QUESTION))).reason()),
                    () -> assertNotEquals(new Open(), reader.read(request(QUESTION))));
        }

        private QuestionRefusal appendRefused(QuestionRecord record) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(plan.lockKey());
                var write = ScopeTransaction.open(transaction, plan, event -> {
                    // the test records nothing
                }, QuestionFixtures.CLOCK);
                var refusal = new QuestionLog(plan).append(write, List.of(record));
                assertTrue(refusal.isPresent(), "ADR core-store/039 refuses every append to a log with a refused line");
                return refusal.get();
            }
        }
    }

    // ------------------------------------------------------------------ the scope of the request

    @Nested
    @DisplayName("the scope of the request")
    class ScopeResolution {

        @Test
        @DisplayName("reads the workspace log for an empty scope id")
        void readsTheWorkspaceScope() {
            append(workspace, EndOfLine.LF, asked(QUESTION));

            assertEquals(new Open(), reader.read(new ReadRequest(Optional.empty(), QUESTION)));
        }

        @Test
        @DisplayName("reads an epic log for a scope id naming an epic")
        void readsAnEpicScope() {
            var epic = JournalScope.epic(base, PROJECT, EPIC);
            createDirectory(epic.pathBase());
            createDirectory(epic.journalFile().getParent());
            append(epic, EndOfLine.LF, asked(QUESTION));

            assertEquals(new Open(), reader.read(new ReadRequest(Optional.of(EPIC), QUESTION)));
        }

        @Test
        @DisplayName("reads Unreadable for a scope with no store")
        void readsUnreadableForAnAbsentScope() {
            var read = reader.read(new ReadRequest(Optional.of("absent-scope"), QUESTION));

            assertEquals(new Unreadable("no question log for the scope"), read);
        }

        @Test
        @DisplayName("reads Unreadable when a plan and an epic carry the same id")
        void readsUnreadableForAnAmbiguousScope() {
            var epic = JournalScope.epic(base, PROJECT, PLAN);
            createDirectory(epic.pathBase());

            var read = reader.read(request(QUESTION));

            assertEquals(new Unreadable("the scope a-plan names a plan and an epic"), read);
        }

        @Test
        @DisplayName("reads Unreadable for a scope id that is no identifier, throwing nothing")
        void readsUnreadableForAnInvalidScopeId() {
            var read = assertDoesNotThrow(
                    () -> reader.read(new ReadRequest(Optional.of("../../escape"), QUESTION)));

            var unreadable = assertInstanceOf(Unreadable.class, read);
            assertTrue(unreadable.reason().startsWith("the scope is not an identifier"),
                    "the reason should name the refused scope id form: " + unreadable.reason());
        }

        @Test
        @DisplayName("reads Unreadable for a question absent from a log that has others")
        void readsUnreadableForANeverAskedQuestion() {
            append(plan, EndOfLine.LF, asked("Q-002"));

            assertEquals(new Unreadable("the question Q-001 is not in the log"),
                    reader.read(request(QUESTION)));
        }

        private void createDirectory(Path path) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    // ------------------------------------------------------------------ agreement with the store

    @Nested
    @DisplayName("agreement with QuestionLog")
    class Agreement {

        @Test
        @DisplayName("reads the same state as QuestionLog for every record both see")
        void agreesOnEveryRecord() {
            append(plan, EndOfLine.LF, asked(QUESTION), asked("Q-002"), closing(QUESTION, QuestionEvent.ANSWERED),
                    asked("Q-003"), closing("Q-003", QuestionEvent.WITHDRAWN));
            var lines = new QuestionLog(plan).read();
            var questionIds = lines.records().stream().map(QuestionRecord::questionId).distinct().toList();
            assertEquals(List.of(QUESTION, "Q-002", "Q-003"), questionIds);

            for (var questionId : questionIds) {
                var current = lines.current(questionId).orElseThrow();
                QuestionRead expected = current.closes() ? new Resolved(current.event().wireName()) : new Open();

                assertEquals(expected, reader.read(request(questionId)),
                        "the adapter and the store agree on " + questionId);
            }
        }
    }
}
