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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.ChangeLedgerRecord.ChangeOrigin;
import de.planmarshall.core.store.ChangeLedgerRecord.Kind;
import de.planmarshall.core.store.ChangeLedgerRecord.Outcome;
import de.planmarshall.core.store.ChangeLedgerRecord.WorktreeShaFailure;

/**
 * The change ledger (Store Schemas § Change Ledger Record): the append-only rows a freshness gate reads, each bound to
 * the working-tree digest it was observed on, written through the scope's transaction and covered by its sidecar.
 */
@DisplayName("Change ledger")
class ChangeLedgerTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-22T10:00:00Z");
    private static final String DIGEST = "a".repeat(64);
    private static final String COMMIT = "b".repeat(40);
    private static final String PROJECT = "pm-mcp-core";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private Path file;
    private ChangeLedger ledger;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createLedger() throws Exception {
        scope = JournalScope.plan(base, PROJECT, "some-plan");
        Files.createDirectories(scope.journalFile().getParent());
        file = scope.resolve(ChangeLedger.FILE_NAME);
        ledger = new ChangeLedger(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    /** Opens the scope's transaction, hands it to the write and commits it. */
    private void commit(Consumer<ScopeTransaction> write) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var scopeTransaction = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            write.accept(scopeTransaction);
            scopeTransaction.commit();
        }
    }

    private void append(ChangeLedgerRecord row) {
        commit(transaction -> ledger.append(transaction, row));
    }

    private ScopeIntegrity.Verification verify(String relativePath) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            return ScopeIntegrity.verify(transaction, scope, relativePath, audit);
        }
    }

    private static ChangeLedgerRecord changeRow() {
        return ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha(DIGEST)
                .worktreeShaVersion(ChangeLedgerRecord.WORKTREE_SHA_VERSION).changedPaths(List.of("a.txt"))
                .commitSha(COMMIT).changeOrigin(ChangeOrigin.PLAN_CHANGE).recordedAt(RECORDED_AT).build();
    }

    private static ChangeLedgerRecord value(JsonlStoreFile.Entry<ChangeLedgerRecord> entry) {
        return switch (entry) {
            case JsonlStoreFile.Accepted<ChangeLedgerRecord> accepted -> accepted.value();
            case JsonlStoreFile.Refused<ChangeLedgerRecord> refused -> throw new AssertionError(refused.refusal());
        };
    }

    @Nested
    @DisplayName("a row")
    class Row {

        @Test
        @DisplayName("is appended and read back, in the order of the append")
        void roundTrip() {
            append(changeRow());
            append(changeRow().toBuilder().changedPaths(List.of("b.txt")).build());

            var entries = ledger.read();

            assertAll(
                    () -> assertEquals(2, entries.size()),
                    () -> assertEquals(changeRow(), value(entries.getFirst())),
                    () -> assertEquals(List.of("b.txt"), value(entries.getLast()).changedPaths()),
                    () -> assertTrue(Files.readString(file).endsWith("\n")));
        }

        @Test
        @DisplayName("of an unavailable digest carries how the git invocation failed")
        void unavailable() {
            var failure = new WorktreeShaFailure(List.of("git", "status"), WorktreeShaFailure.FailureKind.EXIT_CODE,
                    128, null);

            var row = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha("unavailable")
                    .worktreeShaVersion(1).worktreeShaFailure(failure).recordedAt(RECORDED_AT).build();
            append(row);

            assertEquals(failure, value(ledger.read().getFirst()).worktreeShaFailure());
        }

        @Test
        @DisplayName("a build row names the gate it proves")
        void buildRow() {
            var row = ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .gateKind("quality-gate").outcome(Outcome.SUCCEEDED).durationMs(1200L)
                    .jobId("b-0123456789abcdef").recordedAt(RECORDED_AT).build();

            append(row);

            assertEquals(row, value(ledger.read().getFirst()));
        }

        @Test
        @DisplayName("a baseline-red row carries its proof and its expectation")
        void baselineRedRow() {
            var baseline = new ChangeLedgerRecord.Baseline("verify", COMMIT, COMMIT,
                    ChangeLedgerRecord.Baseline.ProofSource.CI_BASE_RESULT, DIGEST, List.of(DIGEST));
            var row = ChangeLedgerRecord.builder().kind(Kind.BASELINE_RED).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .gateKind("verify").outcome(Outcome.FAILED).expect(ChangeLedgerRecord.Expect.RED)
                    .expectResult(ChangeLedgerRecord.ExpectResult.EXPECTED_RED).baseline(baseline)
                    .deliverableId("D1").durationMs(0L).recordedAt(RECORDED_AT).build();

            append(row);

            assertEquals(row, value(ledger.read().getFirst()));
        }

        @Test
        @DisplayName("a task-diff row carries its task and its paths")
        void taskDiffRow() {
            var row = ChangeLedgerRecord.builder().kind(Kind.TASK_DIFF).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .taskId("TASK-1").changedPaths(List.of("a.txt", "b.txt")).recordedAt(RECORDED_AT).build();

            append(row);

            assertEquals(row, value(ledger.read().getFirst()));
        }
    }

    @Nested
    @DisplayName("the scope transaction")
    class ScopeTransactionWrite {

        @Test
        @DisplayName("an appended line has a sidecar entry, as every store of the scope's transactions")
        void sidecarEntry() throws Exception {
            append(changeRow());

            var digest = DigestSidecar.load(scope.sidecarFile()).digest(ChangeLedger.FILE_NAME);

            assertAll(
                    () -> assertEquals(Optional.of(StoreDigest.of(Files.readAllBytes(file))), digest),
                    () -> assertFalse(Files.exists(scope.journalFile()), "the journal is closed"));
        }

        @Test
        @DisplayName("an out-of-band edit of the ledger is a Mismatch of the integrity check")
        void outOfBandEdit() throws Exception {
            append(changeRow());
            var recorded = DigestSidecar.load(scope.sidecarFile()).digest(ChangeLedger.FILE_NAME).orElseThrow();
            var edited = Files.readAllBytes(file);
            edited[edited.length - 2] = ' ';
            Files.write(file, edited);

            var outcome = verify(ChangeLedger.FILE_NAME);

            assertEquals(new ScopeIntegrity.Mismatch(ChangeLedger.FILE_NAME, recorded,
                    Optional.of(StoreDigest.of(edited))), outcome);
        }

        @Test
        @DisplayName("a transaction of another scope is refused")
        void otherScope() throws Exception {
            var other = JournalScope.plan(base, PROJECT, "another-plan");
            Files.createDirectories(other.journalFile().getParent());

            assertThrows(IllegalArgumentException.class, () -> {
                try (var transaction = locks.openTransaction()) {
                    transaction.acquire(other.lockKey());
                    var scopeTransaction = ScopeTransaction.open(transaction, other, audit, CLOCK);
                    ledger.append(scopeTransaction, changeRow());
                }
            });
        }
    }

    @Nested
    @DisplayName("the schema")
    class Schema {

        @Test
        @DisplayName("ties an unavailable digest to its failure and a baseline to the baseline_red kind")
        void refusals() {
            var unavailableWithoutFailure = ChangeLedgerRecord.builder().kind(Kind.JOB)
                    .worktreeSha("unavailable").worktreeShaVersion(1).recordedAt(RECORDED_AT);
            var failureWithDigest = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).worktreeShaFailure(new WorktreeShaFailure(List.of("git", "status"),
                    WorktreeShaFailure.FailureKind.SIGNAL, null, 9))
                    .recordedAt(RECORDED_AT);
            var taskDiffWithoutTask = ChangeLedgerRecord.builder().kind(Kind.TASK_DIFF).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).recordedAt(RECORDED_AT);
            var taskOnAnotherKind = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).taskId("TASK-1").recordedAt(RECORDED_AT);

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, unavailableWithoutFailure::build),
                    () -> assertThrows(IllegalArgumentException.class, failureWithDigest::build),
                    () -> assertThrows(IllegalArgumentException.class, taskDiffWithoutTask::build),
                    () -> assertThrows(IllegalArgumentException.class, taskOnAnotherKind::build));
        }

        @Test
        @DisplayName("refuses a broken digest, version, gate, job, commit, duration and baseline")
        void moreRefusals() {
            var badSha = ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha("not-a-digest")
                    .worktreeShaVersion(1).recordedAt(RECORDED_AT);
            var badVersion = ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha(DIGEST)
                    .worktreeShaVersion(2).recordedAt(RECORDED_AT);
            var badGate = ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .gateKind("clean").recordedAt(RECORDED_AT);
            var badJob = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .jobId("x-0123456789abcdef").recordedAt(RECORDED_AT);
            var badCommit = ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).commitSha("abc").recordedAt(RECORDED_AT);
            var badDuration = ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).durationMs(-1L).recordedAt(RECORDED_AT);
            var baselineOnAnotherKind = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).recordedAt(RECORDED_AT)
                    .baseline(new ChangeLedgerRecord.Baseline("verify", COMMIT, COMMIT,
                            ChangeLedgerRecord.Baseline.ProofSource.LOCAL_MERGE_BASE_BUILD, DIGEST, List.of(DIGEST)));

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, badSha::build),
                    () -> assertThrows(IllegalArgumentException.class, badVersion::build),
                    () -> assertThrows(IllegalArgumentException.class, badGate::build),
                    () -> assertThrows(IllegalArgumentException.class, badJob::build),
                    () -> assertThrows(IllegalArgumentException.class, badCommit::build),
                    () -> assertThrows(IllegalArgumentException.class, badDuration::build),
                    () -> assertThrows(IllegalArgumentException.class, baselineOnAnotherKind::build));
        }

        @Test
        @DisplayName("a line that does not parse stays in the ledger and is reported with its line number")
        void refusedLine() throws Exception {
            Files.writeString(file, "{\"format_version\":1,\"kind\":\"change\"}\n" + new String(
                    ChangeLedgerCodec.write(changeRow())) + "\n");

            var entries = ledger.read();

            assertAll(
                    () -> assertInstanceOf(JsonlStoreFile.Refused.class, entries.getFirst()),
                    () -> assertEquals(1L, entries.getFirst().line()),
                    () -> assertInstanceOf(JsonlStoreFile.Accepted.class, entries.getLast()));
        }

        @Test
        @DisplayName("a line whose version does not fit an int is refused, and the next line stays readable")
        void outOfRangeVersion() throws Exception {
            Files.writeString(file, "{\"format_version\":1,\"kind\":\"change\",\"worktree_sha\":\""
                    + DIGEST + "\",\"worktree_sha_version\":4294967296,"
                    + "\"recorded_at\":\"2026-10-22T10:00:00Z\"}\n"
                    + new String(ChangeLedgerCodec.write(changeRow()), UTF_8) + "\n", UTF_8);

            var entries = ledger.read();

            assertAll(
                    () -> assertInstanceOf(JsonlStoreFile.Refused.class, entries.getFirst()),
                    () -> assertInstanceOf(JsonlStoreFile.Accepted.class, entries.getLast()));
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("the workspace ledger is the workspace's change-ledger.jsonl")
        void workspacePath() {
            var workspace = JournalScope.workspace(base, PROJECT);

            assertAll(
                    () -> assertEquals("workspace/change-ledger.jsonl", new ChangeLedger(workspace).storePath()),
                    () -> assertEquals(workspace.resolve("workspace/change-ledger.jsonl"),
                            new ChangeLedger(workspace).store()));
        }

        @Test
        @DisplayName("the lesson store has no change ledger")
        void lessonStore() {
            assertThrows(IllegalArgumentException.class,
                    () -> new ChangeLedger(JournalScope.lessons(base, PROJECT)));
        }
    }
}
