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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
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
 * and the scope the request addresses. Every line-oriented fixture is written with both line endings.
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

    private void createDirectory(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
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

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Open for each part of a multi-part decision")
        void readsOpenPartOfSet(EndOfLine endOfLine) {
            append(plan, endOfLine, asked(QUESTION), asked("Q-002"));

            assertAll(
                    () -> assertEquals(new Open(), reader.read(request(QUESTION))),
                    () -> assertEquals(new Open(), reader.read(request("Q-002"))));
        }
    }

    // ------------------------------------------------------------------ Resolved

    @Nested
    @DisplayName("a resolved record")
    class ResolvedRecord {

        static Stream<Arguments> closingOutcomes() {
            return closingEvents().flatMap(event -> Stream.of(EndOfLine.values())
                    .map(endOfLine -> Arguments.of(event, endOfLine)));
        }

        @ParameterizedTest(name = "{0} with {1}")
        @MethodSource("closingOutcomes")
        @DisplayName("reads Resolved with the outcome of every closing event, the set derived from the enum")
        void readsEveryOutcome(QuestionEvent event, EndOfLine endOfLine) {
            var expected = closingEvents().map(QuestionEvent::wireName).collect(Collectors.toSet());
            assertEquals(Set.of("answered", "withdrawn", "cancelled"), expected,
                    "the closing events are the closed set of resolution outcomes");

            var questionId = "Q-%03d".formatted(event.ordinal() + 1);
            append(plan, endOfLine, asked(questionId), closing(questionId, event));

            var read = reader.read(request(questionId));

            assertEquals(new Resolved(event.wireName()), read,
                    "the adapter returns the outcome the closing line records");
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Resolved for a closed question among open ones")
        void readsClosedAmongOpenQuestions(EndOfLine endOfLine) {
            append(plan, endOfLine, asked(QUESTION), asked("Q-002"), closing(QUESTION, QuestionEvent.ANSWERED));

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
                        Arguments.of(UnreadableCause.STORE_REFUSES, EndOfLine.LF)),
                Stream.of(EndOfLine.values()).flatMap(endOfLine -> Stream.of(
                        Arguments.of(UnreadableCause.UNPARSEABLE_LINE, endOfLine),
                        Arguments.of(UnreadableCause.RECORD_OF_ANOTHER_CLASS, endOfLine),
                        Arguments.of(UnreadableCause.TORN_TAIL, endOfLine))));
    }

    @Nested
    @DisplayName("a log that cannot be read")
    class UnreadableLog {

        @ParameterizedTest(name = "{0} with {1}")
        @MethodSource("de.planmarshall.runtime.question.QuestionLogRecordReaderTest#unreadableCases")
        @DisplayName("reads Unreadable for every cause, never Open")
        void readsUnreadable(UnreadableCause cause, EndOfLine endOfLine) throws Exception {
            var expectedReason = createFixture(cause, endOfLine);

            var read = reader.read(request(QUESTION));

            assertNotEquals(new Open(), read, "a broken log is never reported open");
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
                    Files.writeString(store, asked + endOfLine.text() + "{", UTF_8);
                    return "the question log ends in a torn tail at byte "
                            + (asked.getBytes(UTF_8).length + endOfLine.text().length());
                }
            }
            throw new IllegalStateException("unhandled cause " + cause);
        }
    }

    @Nested
    @DisplayName("a log holding a refused line")
    class RefusedLine {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Unreadable for an open record followed by an unparseable line, and names the line")
        void openRecordThenUnparseableLine(EndOfLine endOfLine) {
            append(plan, endOfLine, asked(QUESTION));
            QuestionFixtures.appendLine(plan, endOfLine, "no question record");

            var read = reader.read(request(QUESTION));

            var unreadable = assertInstanceOf(Unreadable.class, read,
                    "the whole log is judged, not only the lines of the question");
            assertEquals("the question log holds a refused line 2", unreadable.reason());
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Unreadable for a resolved record followed by a refused line (ADR core-store/039)")
        void resolvedRecordThenRefusedLine(EndOfLine endOfLine) {
            append(plan, endOfLine, asked(QUESTION), closing(QUESTION, QuestionEvent.ANSWERED));
            QuestionFixtures.appendLine(plan, endOfLine, "{\"format_version\":2}");

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

    @Nested
    @DisplayName("a torn log")
    class TornTail {

        @Test
        @DisplayName("reads Unreadable when the log is a torn tail at the first byte")
        void readsUnreadableForATornTailAtTheFirstByte() {
            QuestionFixtures.writeTorn(plan, EndOfLine.LF);

            var read = reader.read(request(QUESTION));

            assertEquals(new Unreadable("the question log ends in a torn tail at byte 0"), read);
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("names the refused line before a torn tail that follows it")
        void readsTheRefusedLineBeforeTheTornTail(EndOfLine endOfLine) {
            QuestionFixtures.writeTorn(plan, endOfLine, "{\"format_version\":2}");

            var read = reader.read(request(QUESTION));

            assertEquals(new Unreadable("the question log holds a refused line 1"), read);
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("names the first of several refused lines")
        void readsTheFirstOfSeveralRefusedLines(EndOfLine endOfLine) {
            QuestionFixtures.appendLine(plan, endOfLine, "{\"format_version\":2}");
            QuestionFixtures.appendLine(plan, endOfLine, "{\"format_version\":3}");

            var read = reader.read(request(QUESTION));

            assertEquals(new Unreadable("the question log holds a refused line 1"), read);
        }
    }

    // ------------------------------------------------------------------ the scope of the request

    @Nested
    @DisplayName("the scope of the request")
    class ScopeResolution {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads the workspace log for an empty scope id")
        void readsTheWorkspaceScope(EndOfLine endOfLine) {
            append(workspace, endOfLine, asked(QUESTION));

            assertEquals(new Open(), reader.read(new ReadRequest(Optional.empty(), QUESTION)));
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads an epic log for a scope id naming an epic")
        void readsAnEpicScope(EndOfLine endOfLine) {
            var epic = JournalScope.epic(base, PROJECT, EPIC);
            createDirectory(epic.pathBase());
            createDirectory(epic.journalFile().getParent());
            append(epic, endOfLine, asked(QUESTION));

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
            assertEquals("the scope is not an identifier: ../../escape", unreadable.reason());
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Unreadable for a question absent from a log that has others")
        void readsUnreadableForANeverAskedQuestion(EndOfLine endOfLine) {
            append(plan, endOfLine, asked("Q-002"));

            assertEquals(new Unreadable("the question Q-001 is not in the log"),
                    reader.read(request(QUESTION)));
        }
    }

    // ------------------------------------------------------------------ agreement with the store

    @Nested
    @DisplayName("agreement with QuestionLog")
    class Agreement {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads the state QuestionLog records for each question, the expectation named per question")
        void agreesOnEveryRecord(EndOfLine endOfLine) {
            append(plan, endOfLine, asked(QUESTION), asked("Q-002"), closing(QUESTION, QuestionEvent.ANSWERED),
                    asked("Q-003"), closing("Q-003", QuestionEvent.WITHDRAWN));
            var expected = Map.of(QUESTION, (QuestionRead) new Resolved("answered"),
                    "Q-002", new Open(),
                    "Q-003", new Resolved("withdrawn"));
            var lines = new QuestionLog(plan).read();
            var questionIds = lines.records().stream().map(QuestionRecord::questionId).distinct().toList();
            assertEquals(List.of(QUESTION, "Q-002", "Q-003"), questionIds);

            for (var entry : expected.entrySet()) {
                var current = lines.current(entry.getKey()).orElseThrow();
                assertAll("the store and the adapter agree on " + entry.getKey(),
                        () -> assertEquals(entry.getValue(), reader.read(request(entry.getKey()))),
                        () -> assertEquals(entry.getValue() instanceof Resolved, current.closes(),
                                "the store's current line closes exactly when the expected read is resolved"));
            }
        }
    }

    // ------------------------------------------------------------------ construction

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("refuses a null base, a null project id and a null request")
        void refusesNulls() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> new QuestionLogRecordReader(null, PROJECT)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new QuestionLogRecordReader(base, null)),
                    () -> assertThrows(NullPointerException.class, () -> reader.read(null)));
        }

        @Test
        @DisplayName("refuses a project id that is no identifier")
        void refusesAnInvalidProjectId() {
            var refusal = assertThrows(IllegalArgumentException.class,
                    () -> new QuestionLogRecordReader(base, "../../escape"));

            assertTrue(refusal.getMessage().contains("projectId"),
                    "the refusal names the project id: " + refusal.getMessage());
        }

        @Test
        @DisplayName("answers Unreadable, not an exception, when the base does not exist")
        void readsUnreadableForANonExistentBase() {
            var missing = new QuestionLogRecordReader(base.resolve("missing"), PROJECT);

            var read = assertDoesNotThrow(() -> missing.read(request(QUESTION)));

            assertEquals(new Unreadable("no question log for the scope"), read);
        }
    }
}
