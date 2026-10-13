/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.spi.JobStarter.JobKind;
import de.planmarshall.core.spi.JobStarter.StartRequest;
import de.planmarshall.core.spi.JobStarter.StartResult;
import de.planmarshall.core.spi.PendingEntryReader.EntryRead;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead;
import de.planmarshall.core.spi.QuestionRecordReader.ReadRequest;
import de.planmarshall.core.spi.QueueTaskCreator.CreateRequest;
import de.planmarshall.core.spi.QueueTaskCreator.Created;
import de.planmarshall.core.store.EntryId;

/**
 * The values of the engine ports (PM-IMPL-5, PM-ARCH-4): the identifier grammars of what a started job and a created
 * queue task return, and the tri-state reads of a question record and of a pending entry record. A question record that
 * cannot be read must stay distinguishable from an open one, otherwise a broken store would be taken for a question
 * still awaiting its answer and the state would rest forever; a pending entry record that cannot be read must stay
 * distinguishable from a live one, otherwise a broken store would be taken for a bootstrap that can be addressed.
 */
@DisplayName("Engine ports of the core SPI")
class EnginePortsTest {

    private static final String JOB_ID_HEX = "0123456789abcdef";

    @Nested
    @DisplayName("JobStarter")
    class JobStarterTest {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"b-0123456789abcdef", "c-ffffffffffffffff", "g-0000000000000000",
                "p-a1b2c3d4e5f60789"})
        @DisplayName("a job_id of the four kinds the port starts is accepted")
        void acceptsJobIdOfStartableKind(String jobId) {
            var started = new StartResult.Started(jobId);

            assertEquals(jobId, started.jobId());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"l-0123456789abcdef", "x-0123456789abcdef", "b-0123456789ABCDEF",
                "b-0123456789abcde", "b-0123456789abcdef0", "b0123456789abcdef", "qt-0123456789abcdef", ""})
        @DisplayName("a job_id outside ^[bcgp]-[0-9a-f]{16}$ is refused")
        void refusesJobIdOutsideGrammar(String jobId) {
            var thrown = assertThrows(IllegalArgumentException.class, () -> new StartResult.Started(jobId));

            assertEquals("job_id does not match ^[bcgp]-[0-9a-f]{16}$: " + jobId, thrown.getMessage());
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @CsvSource({"b, BUILD, build", "c, CI, ci", "g, GIT, git", "p, PROJECT_SCRIPT, project_script"})
        @DisplayName("the prefix of the job_id names the kind of the started job")
        void prefixNamesKind(char prefix, JobKind expected, String wireName) {
            var started = new StartResult.Started(prefix + "-" + JOB_ID_HEX);

            assertAll(
                    () -> assertEquals(expected, started.kind()),
                    () -> assertEquals(prefix, expected.prefix()),
                    () -> assertEquals(wireName, expected.wireName()));
        }

        @Test
        @DisplayName("the kinds are the four the port starts, worker jobs excluded")
        void kindsAreTheFourStartable() {
            var names = Arrays.stream(JobKind.values()).map(JobKind::wireName).toList();

            assertEquals(List.of("build", "ci", "git", "project_script"), names);
        }

        @Test
        @DisplayName("a prefix that names no kind of the port is refused")
        void refusesUnknownPrefix() {
            assertThrows(IllegalArgumentException.class, () -> JobKind.ofPrefix('l'));
        }

        @Test
        @DisplayName("a refusal is a value carrying its reason, a blank reason is refused")
        void refusalIsAValue() {
            var starter = (JobStarter) request -> new StartResult.Refused("runtime draining");

            var result = starter.start(new StartRequest(Optional.of("my-plan"), "build.verify", "build.run"));

            assertAll(
                    () -> assertEquals("runtime draining", assertInstanceOf(StartResult.Refused.class, result)
                            .reason()),
                    () -> assertThrows(IllegalArgumentException.class, () -> new StartResult.Refused(" ")),
                    () -> assertThrows(NullPointerException.class, () -> new StartResult.Refused(null)));
        }

        @Test
        @DisplayName("the request names the scope, state and primitive; blank identifiers are refused")
        void requestValidation() {
            var workspace = new StartRequest(Optional.empty(), "setup.build", "build.run");

            assertAll(
                    () -> assertEquals(Optional.empty(), workspace.scopeId()),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new StartRequest(Optional.empty(), "", "build.run")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new StartRequest(Optional.empty(), "setup.build", " ")),
                    () -> assertThrows(NullPointerException.class,
                            () -> new StartRequest(null, "setup.build", "build.run")));
        }
    }

    @Nested
    @DisplayName("QueueTaskCreator")
    class QueueTaskCreatorTest {

        @Test
        @DisplayName("a queue_task_id of ^qt-[0-9a-f]{16}$ is accepted")
        void acceptsQueueTaskId() {
            var created = assertDoesNotThrow(() -> new Created("qt-" + JOB_ID_HEX));

            assertEquals("qt-" + JOB_ID_HEX, created.queueTaskId());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"qt-0123456789ABCDEF", "qt-0123456789abcde", "qt-0123456789abcdef0",
                "q-0123456789abcdef", "b-0123456789abcdef", "qt0123456789abcdef", ""})
        @DisplayName("a queue_task_id outside ^qt-[0-9a-f]{16}$ is refused")
        void refusesQueueTaskIdOutsideGrammar(String queueTaskId) {
            var thrown = assertThrows(IllegalArgumentException.class, () -> new Created(queueTaskId));

            assertEquals("queue_task_id does not match ^qt-[0-9a-f]{16}$: " + queueTaskId, thrown.getMessage());
        }

        @Test
        @DisplayName("a test double creates one task per request and keys it by the entity")
        void createsOneTaskPerEntity() {
            var requests = new ArrayList<CreateRequest>();
            QueueTaskCreator creator = request -> {
                requests.add(request);
                return new Created("qt-%016x".formatted(requests.size()));
            };

            var first = creator.create(new CreateRequest(Optional.of("my-plan"), "plan.review", "TASK-001",
                    "review-code"));
            var second = creator.create(new CreateRequest(Optional.of("my-plan"), "plan.review", "TASK-002",
                    "review-code"));

            assertAll(
                    () -> assertEquals(List.of("TASK-001", "TASK-002"),
                            requests.stream().map(CreateRequest::entityKey).toList()),
                    () -> assertNotEquals(first.queueTaskId(), second.queueTaskId()));
        }

        @Test
        @DisplayName("blank state, entity or role is refused in the request")
        void requestValidation() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new CreateRequest(Optional.empty(), " ", "TASK-001", "review-code")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new CreateRequest(Optional.empty(), "plan.review", "", "review-code")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new CreateRequest(Optional.empty(), "plan.review", "TASK-001", "")),
                    () -> assertThrows(NullPointerException.class,
                            () -> new CreateRequest(Optional.empty(), "plan.review", "TASK-001", null)));
        }
    }

    @Nested
    @DisplayName("QuestionRecordReader")
    class QuestionRecordReaderTest {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Q-001", "Q-999999", "Q-0042"})
        @DisplayName("a question_id of ^Q-[0-9]{3,6}$ is accepted")
        void acceptsQuestionId(String questionId) {
            var request = new ReadRequest(Optional.of("my-plan"), questionId);

            assertEquals(questionId, request.questionId());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"Q-01", "Q-1234567", "q-001", "Q001", "Q-00a", ""})
        @DisplayName("a question_id outside ^Q-[0-9]{3,6}$ is refused")
        void refusesQuestionIdOutsideGrammar(String questionId) {
            var thrown = assertThrows(IllegalArgumentException.class,
                    () -> new ReadRequest(Optional.empty(), questionId));

            assertEquals("question_id does not match ^Q-[0-9]{3,6}$: " + questionId, thrown.getMessage());
        }

        @Test
        @DisplayName("the read has three distinct results: open, resolved with its outcome, unreadable")
        void readIsTriState() {
            var records = Map.<String, QuestionRead>of(
                    "Q-001", new QuestionRead.Open(),
                    "Q-002", new QuestionRead.Resolved("answered"),
                    "Q-003", new QuestionRead.Unreadable("questions.jsonl: line 3 is not JSON"));
            QuestionRecordReader reader = request -> records.get(request.questionId());

            var routes = List.of("Q-001", "Q-002", "Q-003").stream()
                    .map(id -> route(reader.read(new ReadRequest(Optional.of("my-plan"), id))))
                    .toList();

            assertEquals(List.of("rest", "take answered", "could not read"), routes);
        }

        @Test
        @DisplayName("an unreadable record is never equal to an open one")
        void unreadableIsNotOpen() {
            QuestionRead unreadable = new QuestionRead.Unreadable("record absent");

            assertAll(
                    () -> assertNotEquals(new QuestionRead.Open(), unreadable),
                    () -> assertEquals(new QuestionRead.Open(), new QuestionRead.Open()));
        }

        @Test
        @DisplayName("a blank outcome or reason is refused")
        void blankComponentsAreRefused() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new QuestionRead.Resolved("")),
                    () -> assertThrows(IllegalArgumentException.class, () -> new QuestionRead.Unreadable(" ")),
                    () -> assertThrows(NullPointerException.class, () -> new QuestionRead.Resolved(null)));
        }

        private static String route(QuestionRead read) {
            return switch (read) {
                case QuestionRead.Open _ -> "rest";
                case QuestionRead.Resolved resolved -> "take " + resolved.outcome();
                case QuestionRead.Unreadable _ -> "could not read";
            };
        }
    }

    @Nested
    @DisplayName("PendingEntryReader")
    class PendingEntryReaderTest {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"ent-0123abcd", "ent-0123456789abcdef0123456789abcdef"})
        @DisplayName("the request carries the typed entry_id, of 8 to 32 hex digits")
        void carriesTypedEntryId(String value) {
            var entryId = new EntryId(value);

            var request = new PendingEntryReader.ReadRequest(entryId);

            assertEquals(entryId, request.entryId());
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"ent-0123abc", "ent-0123456789abcdef0123456789abcdef0", "ent-0123ABCD", "TASK-001"})
        @DisplayName("a string outside the entry_id grammar never becomes the entry of a request")
        void noRequestForUncheckedString(String value) {
            assertAll(
                    () -> assertEquals(Optional.empty(), EntryId.parse(value)),
                    () -> assertThrows(IllegalArgumentException.class, () -> new EntryId(value)));
        }

        @Test
        @DisplayName("a null entry_id is refused")
        void refusesNullEntryId() {
            assertThrows(NullPointerException.class, () -> new PendingEntryReader.ReadRequest(null));
        }

        @Test
        @DisplayName("the read has three distinct results: live with its kind, deleted, unreadable")
        void readIsTriState() {
            var records = Map.<String, EntryRead>of(
                    "ent-0000000a", new EntryRead.Live("plan"),
                    "ent-0000000b", new EntryRead.Live("epic"),
                    "ent-0000000c", new EntryRead.Deleted(),
                    "ent-0000000d", new EntryRead.Unreadable("pending/ent-0000000d.json is not JSON"));
            PendingEntryReader reader = request -> records.get(request.entryId().value());

            var routes = List.of("ent-0000000a", "ent-0000000b", "ent-0000000c", "ent-0000000d").stream()
                    .map(id -> route(reader.read(new PendingEntryReader.ReadRequest(new EntryId(id)))))
                    .toList();

            assertEquals(List.of("live plan", "live epic", "deleted", "could not read"), routes);
        }

        @Test
        @DisplayName("an unreadable or deleted record is never equal to a live one")
        void unreadableAndDeletedAreNotLive() {
            EntryRead live = new EntryRead.Live("plan");

            assertAll(
                    () -> assertNotEquals(live, new EntryRead.Unreadable("record locked")),
                    () -> assertNotEquals(live, new EntryRead.Deleted()),
                    () -> assertEquals(new EntryRead.Deleted(), new EntryRead.Deleted()),
                    () -> assertEquals(live, new EntryRead.Live("plan")));
        }

        @Test
        @DisplayName("a blank or null kind or reason is refused")
        void blankComponentsAreRefused() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new EntryRead.Live("")),
                    () -> assertThrows(IllegalArgumentException.class, () -> new EntryRead.Unreadable(" ")),
                    () -> assertThrows(NullPointerException.class, () -> new EntryRead.Live(null)),
                    () -> assertThrows(NullPointerException.class, () -> new EntryRead.Unreadable(null)));
        }

        private static String route(EntryRead read) {
            return switch (read) {
                case EntryRead.Live live -> "live " + live.kind();
                case EntryRead.Deleted _ -> "deleted";
                case EntryRead.Unreadable _ -> "could not read";
            };
        }
    }

    @Nested
    @DisplayName("WorkflowClock")
    class WorkflowClockTest {

        @Test
        @DisplayName("a test double drives time deterministically")
        void doubleDrivesTime() {
            var instants = new ArrayList<>(List.of(Instant.parse("2026-01-01T00:00:00Z"),
                    Instant.parse("2026-01-01T00:05:00Z")));
            WorkflowClock clock = instants::removeFirst;

            assertAll(
                    () -> assertEquals(Instant.parse("2026-01-01T00:00:00Z"), clock.now()),
                    () -> assertEquals(Instant.parse("2026-01-01T00:05:00Z"), clock.now()));
        }
    }
}
