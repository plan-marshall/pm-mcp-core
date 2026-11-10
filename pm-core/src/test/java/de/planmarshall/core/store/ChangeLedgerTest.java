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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
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
 * The change ledger (Store Schemas § Change Ledger Record): the append-only rows a freshness gate reads, each carrying
 * the working-tree digest it was observed on, or the failure of the git invocation that could not measure one, written
 * through the scope's transaction and covered by its sidecar.
 */
@EnableGeneratorController
@DisplayName("Change ledger")
class ChangeLedgerTest {

    private static final String DIGEST = "a".repeat(64);
    private static final String COMMIT = "b".repeat(40);
    private static final String PROJECT = "pm-mcp-core";

    @TempDir
    Path base;

    private Instant recordedAt;
    private Clock clock;
    private String plan;
    private String otherPlan;
    private String taskId;
    private String deliverableId;
    private JournalScope scope;
    private Path file;
    private ChangeLedger ledger;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createLedger() throws Exception {
        recordedAt = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next());
        clock = Clock.fixed(Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next()),
                ZoneOffset.UTC);
        plan = "plan-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        otherPlan = "other-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        taskId = "TASK-" + Generators.integers(1, 999).next();
        deliverableId = "D" + Generators.integers(1, 99).next();
        scope = JournalScope.plan(base, PROJECT, plan);
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
            var scopeTransaction = ScopeTransaction.open(transaction, scope, audit, clock);
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

    private ChangeLedgerRecord changeRow() {
        return ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha(DIGEST)
                .worktreeShaVersion(ChangeLedgerRecord.WORKTREE_SHA_VERSION).changedPaths(List.of("a.txt"))
                .commitSha(COMMIT).changeOrigin(ChangeOrigin.PLAN_CHANGE).recordedAt(recordedAt).build();
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

            assertAll("The rows are read back in append order",
                    () -> assertEquals(2, entries.size(),
                            "Both appended rows are read back"),
                    () -> assertEquals(changeRow(), value(entries.getFirst()),
                            "The first row is the first append"),
                    () -> assertEquals(List.of("b.txt"), value(entries.getLast()).changedPaths(),
                            "The last row carries its own changed path"),
                    () -> assertTrue(Files.readString(file).endsWith("\n"),
                            "The ledger file ends with a newline"));
        }

        @Test
        @DisplayName("of an unavailable digest carries how the git invocation failed")
        void unavailable() {
            var failure = new WorktreeShaFailure(List.of("git", "status"), WorktreeShaFailure.FailureKind.EXIT_CODE,
                    128, null);

            var row = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha("unavailable")
                    .worktreeShaVersion(1).worktreeShaFailure(failure).recordedAt(recordedAt).build();
            append(row);

            assertEquals(failure, value(ledger.read().getFirst()).worktreeShaFailure(),
                    "The row carries how the git invocation failed");
        }

        @Test
        @DisplayName("a build row names the gate it proves")
        void buildRow() {
            var row = ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .gateKind("quality-gate").outcome(Outcome.SUCCEEDED).durationMs(1200L)
                    .jobId("b-0123456789abcdef").recordedAt(recordedAt).build();

            append(row);

            assertEquals(row, value(ledger.read().getFirst()),
                    "The build row round-trips with its gate and outcome");
        }

        @Test
        @DisplayName("a baseline-red row carries its proof and its expectation")
        void baselineRedRow() {
            var baseline = new ChangeLedgerRecord.Baseline("verify", COMMIT, COMMIT,
                    ChangeLedgerRecord.Baseline.ProofSource.CI_BASE_RESULT, DIGEST, List.of(DIGEST));
            var row = ChangeLedgerRecord.builder().kind(Kind.BASELINE_RED).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .gateKind("verify").outcome(Outcome.FAILED).expect(ChangeLedgerRecord.Expect.RED)
                    .expectResult(ChangeLedgerRecord.ExpectResult.EXPECTED_RED).baseline(baseline)
                    .deliverableId(deliverableId).durationMs(0L).recordedAt(recordedAt).build();

            append(row);

            assertEquals(row, value(ledger.read().getFirst()),
                    "The baseline-red row round-trips with its proof");
        }

        @Test
        @DisplayName("a task-diff row carries its task and its paths")
        void taskDiffRow() {
            var row = ChangeLedgerRecord.builder().kind(Kind.TASK_DIFF).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .taskId(taskId).changedPaths(List.of("a.txt", "b.txt")).recordedAt(recordedAt).build();

            append(row);

            assertEquals(row, value(ledger.read().getFirst()),
                    "The task-diff row round-trips with its task and paths");
        }
    }

    @Nested
    @DisplayName("the scope transaction")
    class ScopeTransactionWrite {

        @Test
        @DisplayName("an appended line has a sidecar entry, as every store of the scope's transactions")
        void sidecarEntry() {
            append(changeRow());

            var digest = DigestSidecar.load(scope.sidecarFile()).digest(ChangeLedger.FILE_NAME);

            assertAll("The appended line has its sidecar entry",
                    () -> assertEquals(Optional.of(StoreDigest.of(Files.readAllBytes(file))), digest,
                            "The sidecar digest is the digest of the ledger"),
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

            assertEquals(new ScopeIntegrity.Mismatch(ChangeLedger.FILE_NAME, recorded, StoreDigest.of(edited)),
                    outcome,
                    "The edited ledger is a mismatch of the integrity check");
        }

        @Test
        @DisplayName("a transaction of another scope is refused")
        void otherScope() throws Exception {
            var other = JournalScope.plan(base, PROJECT, otherPlan);
            Files.createDirectories(other.journalFile().getParent());

            assertThrows(IllegalArgumentException.class, () -> appendInTransactionOf(other),
                    "A transaction of another scope is refused");
        }

        /** Appends this ledger's row in a transaction of another scope. */
        private void appendInTransactionOf(JournalScope other) {
            try (var transaction = locks.openTransaction()) {
                transaction.acquire(other.lockKey());
                var scopeTransaction = ScopeTransaction.open(transaction, other, audit, clock);
                ledger.append(scopeTransaction, changeRow());
            }
        }
    }

    @Nested
    @DisplayName("the schema")
    class Schema {

        @Test
        @DisplayName("ties an unavailable digest to its failure and a baseline to the baseline_red kind")
        void refusals() {
            var unavailableWithoutFailure = ChangeLedgerRecord.builder().kind(Kind.JOB)
                    .worktreeSha("unavailable").worktreeShaVersion(1).recordedAt(recordedAt);
            var failureWithDigest = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).worktreeShaFailure(new WorktreeShaFailure(List.of("git", "status"),
                    WorktreeShaFailure.FailureKind.SIGNAL, null, 9))
                    .recordedAt(recordedAt);
            var taskDiffWithoutTask = ChangeLedgerRecord.builder().kind(Kind.TASK_DIFF).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).recordedAt(recordedAt);
            var taskOnAnotherKind = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).taskId(taskId).recordedAt(recordedAt);

            assertAll("A row outside its kind's shape is refused",
                    () -> assertThrows(IllegalArgumentException.class, unavailableWithoutFailure::build,
                            "An unavailable digest needs its failure"),
                    () -> assertThrows(IllegalArgumentException.class, failureWithDigest::build,
                            "A failure needs an unavailable digest"),
                    () -> assertThrows(IllegalArgumentException.class, taskDiffWithoutTask::build,
                            "A task-diff row needs its task"),
                    () -> assertThrows(IllegalArgumentException.class, taskOnAnotherKind::build,
                            "Only a task-diff row may name a task"));
        }

        @Test
        @DisplayName("refuses a broken digest, version, gate, job, commit, duration and baseline")
        void moreRefusals() {
            var badSha = ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha("not-a-digest")
                    .worktreeShaVersion(1).recordedAt(recordedAt);
            var badVersion = ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha(DIGEST)
                    .worktreeShaVersion(2).recordedAt(recordedAt);
            var badGate = ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .gateKind("clean").recordedAt(recordedAt);
            var badJob = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .jobId("x-0123456789abcdef").recordedAt(recordedAt);
            var badCommit = ChangeLedgerRecord.builder().kind(Kind.CHANGE).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).commitSha("abc").recordedAt(recordedAt);
            var badDuration = ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).durationMs(-1L).recordedAt(recordedAt);
            var baselineOnAnotherKind = ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST)
                    .worktreeShaVersion(1).recordedAt(recordedAt)
                    .baseline(new ChangeLedgerRecord.Baseline("verify", COMMIT, COMMIT,
                            ChangeLedgerRecord.Baseline.ProofSource.LOCAL_MERGE_BASE_BUILD, DIGEST, List.of(DIGEST)));

            assertAll("A malformed row member is refused",
                    () -> assertThrows(IllegalArgumentException.class, badSha::build,
                            "A worktree sha that is no digest is refused"),
                    () -> assertThrows(IllegalArgumentException.class, badVersion::build,
                            "An unknown worktree sha version is refused"),
                    () -> assertThrows(IllegalArgumentException.class, badGate::build,
                            "A gate kind outside the vocabulary is refused"),
                    () -> assertThrows(IllegalArgumentException.class, badJob::build,
                            "A job id outside its grammar is refused"),
                    () -> assertThrows(IllegalArgumentException.class, badCommit::build,
                            "A commit sha that is no digest is refused"),
                    () -> assertThrows(IllegalArgumentException.class, badDuration::build,
                            "A negative duration is refused"),
                    () -> assertThrows(IllegalArgumentException.class, baselineOnAnotherKind::build,
                            "Only a baseline-red row may carry a baseline"));
        }

        @Test
        @DisplayName("a line that does not parse stays in the ledger and is reported with its line number")
        void refusedLine() throws Exception {
            Files.writeString(file, "{\"format_version\":1,\"kind\":\"change\"}\n" + new String(
                    ChangeLedgerCodec.write(changeRow())) + "\n");

            var entries = ledger.read();

            assertAll("A line that does not parse stays in the ledger",
                    () -> assertInstanceOf(JsonlStoreFile.Refused.class, entries.getFirst(),
                            "The unparsable line is refused in place"),
                    () -> assertEquals(1L, entries.getFirst().line(),
                            "The refused line reports its line number"),
                    () -> assertInstanceOf(JsonlStoreFile.Accepted.class, entries.getLast(),
                            "The next line stays readable"));
        }

        @Test
        @DisplayName("a line whose version does not fit an int is refused, and the next line stays readable")
        void outOfRangeVersion() throws Exception {
            Files.writeString(file, "{\"format_version\":1,\"kind\":\"change\",\"worktree_sha\":\""
                    + DIGEST + "\",\"worktree_sha_version\":4294967296,"
                    + "\"recorded_at\":\"2026-10-22T10:00:00Z\"}\n"
                    + new String(ChangeLedgerCodec.write(changeRow()), UTF_8) + "\n", UTF_8);

            var entries = ledger.read();

            assertAll("An out-of-range version is refused line by line",
                    () -> assertInstanceOf(JsonlStoreFile.Refused.class, entries.getFirst(),
                            "The line with an out-of-range version is refused"),
                    () -> assertInstanceOf(JsonlStoreFile.Accepted.class, entries.getLast(),
                            "The next line stays readable"));
        }
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("the workspace ledger is the workspace's change-ledger.jsonl")
        void workspacePath() {
            var workspace = JournalScope.workspace(base, PROJECT);

            assertAll("The workspace ledger sits in the workspace directory",
                    () -> assertEquals("workspace/change-ledger.jsonl", new ChangeLedger(workspace).storePath(),
                            "The store path is the workspace-relative one"),
                    () -> assertEquals(workspace.resolve("workspace/change-ledger.jsonl"),
                            new ChangeLedger(workspace).store(),
                            "The store file resolves below the workspace"));
        }

        @Test
        @DisplayName("the lesson store has no change ledger")
        void lessonStore() {
            var lessons = JournalScope.lessons(base, PROJECT);

            assertThrows(IllegalArgumentException.class, () -> new ChangeLedger(lessons),
                    "A lesson store has no change ledger");
        }
    }
}
