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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Open;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Resolved;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead.Unreadable;
import de.planmarshall.core.spi.QuestionRecordReader.ReadRequest;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.QuestionEvent;
import de.planmarshall.core.store.QuestionRecord;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.question.QuestionFixtures.EndOfLine;
import de.planmarshall.runtime.test.TestBases;

/**
 * The boundaries of the adapter (PM-ARCH-4, PM-IMPL-5): the question id grammar, the scope id grammar, a log of one
 * record and of many, the intermediate lines of a revision, and the refused closing line that must never read open.
 */
@DisplayName("Question log record reader: the boundaries")
class QuestionLogRecordReaderBoundaryTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "a-plan";

    private Path base;
    private JournalScope plan;
    private FileLockManager locks;
    private QuestionLogRecordReader reader;

    @BeforeEach
    void createRuntime() throws IOException {
        base = TestBases.create("pmq-");
        Files.createDirectories(base);
        base = base.toRealPath();
        plan = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(plan.journalFile().getParent());
        locks = new FileLockManager(event -> {
            // a lock acquisition of these tests records nothing
        });
        reader = QuestionFixtures.reader(base, PROJECT);
    }

    @AfterEach
    void deleteRuntime() {
        TestBases.delete(base);
    }

    private ReadRequest request(String questionId) {
        return new ReadRequest(Optional.of(PLAN), questionId);
    }

    @Nested
    @DisplayName("the question id at the grammar bounds")
    class QuestionIdBounds {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads a question with three digits and one with six")
        void readsBothValidForms(EndOfLine endOfLine) {
            QuestionFixtures.append(locks, plan, endOfLine, asked("Q-001"),
                    closing("Q-001", QuestionEvent.ANSWERED), asked("Q-999999"),
                    closing("Q-999999", QuestionEvent.CANCELLED));

            assertAll(
                    () -> assertEquals(new Resolved("answered"), reader.read(request("Q-001"))),
                    () -> assertEquals(new Resolved("cancelled"), reader.read(request("Q-999999"))));
        }
    }

    @Nested
    @DisplayName("the scope id at the grammar bounds")
    class ScopeIdBounds {

        @Test
        @DisplayName("reads Unreadable for two characters, the shortest id the grammar refuses")
        void refusesTheLowerBoundary() {
            assertEquals(new Unreadable("the scope is not an identifier: ab"),
                    reader.read(new ReadRequest(Optional.of("ab"), "Q-001")));
        }

        @Test
        @DisplayName("reads Unreadable for three characters and for sixty-four, the grammar's valid bounds, when no store exists")
        void acceptsBothValidBoundsAsAnAbsentScope() {
            var longest = "a" + "b".repeat(62) + "c";

            assertAll(
                    () -> assertEquals(new Unreadable("no question log for the scope"),
                            reader.read(new ReadRequest(Optional.of("abc"), "Q-001"))),
                    () -> assertEquals(new Unreadable("no question log for the scope"),
                            reader.read(new ReadRequest(Optional.of(longest), "Q-001"))));
        }
    }

    @Nested
    @DisplayName("the log size")
    class LogSize {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads a log of one record")
        void readsOneRecord(EndOfLine endOfLine) {
            QuestionFixtures.append(locks, plan, endOfLine, asked("Q-001"));

            assertEquals(new Open(), reader.read(request("Q-001")));
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads a log of two hundred records of one question")
        void readsManyRecords(EndOfLine endOfLine) {
            QuestionFixtures.append(locks, plan, endOfLine, asked("Q-001"));
            IntStream.rangeClosed(2, 199).forEach(revision ->
                    QuestionFixtures.append(locks, plan, endOfLine, revision("Q-001", revision)));
            QuestionFixtures.append(locks, plan, endOfLine, closing("Q-001", QuestionEvent.ANSWERED, 199));

            assertEquals(new Resolved("answered"), reader.read(request("Q-001")));
        }

        private QuestionRecord revision(String questionId, int revision) {
            return QuestionRecord.builder().event(QuestionEvent.REVISED).questionId(questionId).revision(revision)
                    .envelope("{}").at(QuestionFixtures.AT).build();
        }
    }

    @Nested
    @DisplayName("the lines of one question")
    class LinesOfOneQuestion {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Open after a revision and a delivery that did not close it")
        void readsOpenBetweenRevisions(EndOfLine endOfLine) {
            QuestionFixtures.append(locks, plan, endOfLine, asked("Q-001"),
                    revised("Q-001", 2), delivered("Q-001", 2));

            assertEquals(new Open(), reader.read(request("Q-001")));
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Resolved after the intermediate lines and the closing line")
        void readsResolvedAfterIntermediateLines(EndOfLine endOfLine) {
            QuestionFixtures.append(locks, plan, endOfLine, asked("Q-001"),
                    revised("Q-001", 2), delivered("Q-001", 2),
                    closing("Q-001", QuestionEvent.ANSWERED, 2));

            assertEquals(new Resolved("answered"), reader.read(request("Q-001")));
        }

        private QuestionRecord revised(String questionId, int revision) {
            return QuestionRecord.builder().event(QuestionEvent.REVISED).questionId(questionId).revision(revision)
                    .envelope("{}").at(QuestionFixtures.AT).build();
        }
    }

    @Nested
    @DisplayName("a refused closing line")
    class RefusedClosingLine {

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Unreadable, never Open, when the closing line of the question was refused when read")
        void neverReadsOpen(EndOfLine endOfLine) {
            QuestionFixtures.append(locks, plan, endOfLine, asked("Q-001"));
            QuestionFixtures.appendLine(plan, endOfLine,
                    "{\"format_version\":1,\"event\":\"answered\",\"question_id\":\"Q-001\"}");

            var read = reader.read(request("Q-001"));

            assertNotEquals(new Open(), read, "a refused closing line may have closed the question");
            var unreadable = assertInstanceOf(Unreadable.class, read);
            assertEquals("the question log holds a refused line 2", unreadable.reason());
        }

        @ParameterizedTest
        @EnumSource(EndOfLine.class)
        @DisplayName("reads Unreadable for a plan whose log holds only a refused line")
        void readsUnreadableForARefusedFirstLine(EndOfLine endOfLine) {
            QuestionFixtures.appendLine(plan, endOfLine, "{\"format_version\":2}");

            var read = reader.read(request("Q-001"));

            var unreadable = assertInstanceOf(Unreadable.class, read);
            assertEquals("the question log holds a refused line 1", unreadable.reason());
        }
    }
}
