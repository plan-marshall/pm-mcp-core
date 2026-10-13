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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Serial;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.store.RetryCounters.RetryRule;
import de.planmarshall.core.store.ScopeTransaction.Step;

/**
 * The workflow state store of a scope (PM-ARCH-6, PM-ARCH-4, PM-WF-7): it is written only through the transaction
 * journal of its scope, under the scope lock, so a write stopped at any step leaves the previous or the new document
 * after the recovery and never a mix; it is read without a lock and reports a missing store as absent; and the retry
 * counts it holds survive a restart of the runtime, because a new store instance reads them from the file.
 */
@DisplayName("Workflow status store")
class WorkflowStatusStoreTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "workflow-engine";
    private static final String STATUS = "mcp/status.json";
    private static final String MERGE_SYNC = "finalize.merge-sync";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private WorkflowStatusStore store;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    /** What the step hook throws to stop a commit at a boundary. */
    private static final class StopWrite extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        StopWrite(Step step) {
            super("stopped after " + step);
        }
    }

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, PLAN);
        Files.createDirectories(scope.journalFile().getParent());
        store = new WorkflowStatusStore(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    private static WorkflowStatus planStatus(String state) {
        return WorkflowStatusCodecTest.read(WorkflowStatusCodecTest.DOCUMENT).withActiveJob(null)
                .withActiveJobs(Map.of()).withState(state);
    }

    private void commit(WorkflowStatus status) {
        commit(status, ScopeTransaction.StepHook.NONE);
    }

    private void commit(WorkflowStatus status, ScopeTransaction.StepHook hook) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK, hook);
            store.write(write, status);
            write.commit();
        }
    }

    private void recover() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            JournalRecovery.recover(transaction, scope, audit);
        }
    }

    @Nested
    @DisplayName("write and read")
    class WriteAndRead {

        @Test
        @DisplayName("a write goes through the journal: the document, its digest, no journal left")
        void writeThroughJournal() {
            var status = planStatus("finalize.merge-gate");

            commit(status);

            var document = WorkflowStatusCodec.write(status);
            assertAll(
                    () -> assertArrayEquals(document, Files.readAllBytes(scope.resolve(STATUS))),
                    () -> assertEquals(Optional.of(StoreDigest.of(document)),
                            DigestSidecar.load(scope.sidecarFile()).digest(STATUS)),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal is closed"),
                    () -> assertEquals(Optional.of(status), store.read()));
        }

        @Test
        @DisplayName("an absent store is read as absent, never as an empty status")
        void absentStore() {
            assertEquals(Optional.empty(), store.read());
        }

        @Test
        @DisplayName("each scope type keeps its status in its metadata directory")
        void storePaths() {
            var root = LockKey.projectStore(base, PROJECT);
            var lessons = JournalScope.lessons(base, PROJECT);
            assertAll(
                    () -> assertEquals(root.resolve("plans/workflow-engine/mcp/status.json"), store.store()),
                    () -> assertEquals(root.resolve("epics/an-epic/status.json"),
                            new WorkflowStatusStore(JournalScope.epic(base, PROJECT, "an-epic")).store()),
                    () -> assertEquals(root.resolve("workspace/mcp/status.json"),
                            new WorkflowStatusStore(JournalScope.workspace(base, PROJECT)).store()),
                    () -> assertThrows(IllegalArgumentException.class, () -> new WorkflowStatusStore(lessons)));
        }
    }

    @Nested
    @DisplayName("single writer")
    class SingleWriter {

        @Test
        @DisplayName("a write without the scope lock is refused before anything is written")
        void refusedWithoutLock() {
            try (var transaction = locks.openTransaction()) {
                assertThrows(InternalFaultException.class,
                        () -> ScopeTransaction.open(transaction, scope, audit, CLOCK));
            }
            assertFalse(Files.exists(scope.resolve(STATUS)));
        }

        @Test
        @DisplayName("the status of another scope is refused; the status of this scope is a control")
        void refusesStatusOfAnotherScope() {
            var other = planStatus("finalize.merge-gate").withScopeId("another-plan");
            var status = planStatus("finalize.merge-gate");
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(scope.lockKey());
                var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);

                assertThrows(IllegalArgumentException.class, () -> store.write(write, other));
                store.write(write, status);
                assertThrows(IllegalArgumentException.class, () -> store.write(write, status),
                        "the status is one target of a transaction");
            }
        }
    }

    static Stream<Arguments> boundaries() {
        return Stream.of(
                Arguments.of("after staging", Step.STAGE, false),
                Arguments.of("after the journal", Step.JOURNAL, true),
                Arguments.of("after applying", Step.APPLY, true),
                Arguments.of("after the digests", Step.DIGESTS, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundaries")
    @DisplayName("a write stopped at a step boundary leaves the previous or the new document after recovery")
    void stoppedWrite(String name, Step stopStep, boolean rolledForward) {
        var previous = planStatus("finalize.merge-gate");
        commit(previous);
        var next = planStatus("finalize.merge-sync").withRetryCounters(
                RetryCounters.empty().attempt(RetryRule.SYNC_FAILED, MERGE_SYNC));

        assertThrows(StopWrite.class, () -> commit(next, (step, target) -> {
            if (step == stopStep) {
                throw new StopWrite(step);
            }
        }));
        recover();

        var expected = rolledForward ? next : previous;
        assertAll(
                () -> assertArrayEquals(WorkflowStatusCodec.write(expected), Files.readAllBytes(scope.resolve(STATUS))),
                () -> assertEquals(Optional.of(expected), store.read()),
                () -> assertFalse(Files.exists(scope.journalFile()), "nothing is left to recover"));
    }

    @Nested
    @DisplayName("retry counts across a restart")
    class RestartSurvival {

        @Test
        @DisplayName("a new store instance reads the counts, and a further attempt continues from them")
        void countsSurviveRestart() {
            var counted = planStatus(MERGE_SYNC).withRetryCounters(RetryCounters.empty()
                    .attempt(RetryRule.SYNC_FAILED, MERGE_SYNC).attempt(RetryRule.SYNC_FAILED, MERGE_SYNC));
            commit(counted);

            var restarted = new WorkflowStatusStore(JournalScope.plan(base, PROJECT, PLAN));
            var read = restarted.read().orElseThrow();
            var continued = read.withRetryCounters(read.retryCounters().attempt(RetryRule.SYNC_FAILED, MERGE_SYNC));
            commit(continued);

            assertAll(
                    () -> assertEquals(2, read.retryCounters().attemptsOf(RetryRule.SYNC_FAILED, MERGE_SYNC)),
                    () -> assertEquals(3, new WorkflowStatusStore(JournalScope.plan(base, PROJECT, PLAN)).read()
                            .orElseThrow().retryCounters().attemptsOf(RetryRule.SYNC_FAILED, MERGE_SYNC)));
        }

        @Test
        @DisplayName("matched control: a scope that never retried reads 0 after a restart")
        void neverRetriedReadsZero() {
            commit(planStatus(MERGE_SYNC).withRetryCounters(RetryCounters.empty()));

            var read = new WorkflowStatusStore(JournalScope.plan(base, PROJECT, PLAN)).read().orElseThrow();

            assertAll(
                    () -> assertEquals(0, read.retryCounters().attemptsOf(RetryRule.SYNC_FAILED, MERGE_SYNC)),
                    () -> assertTrue(read.retryCounters().isEmpty()));
        }
    }
}
