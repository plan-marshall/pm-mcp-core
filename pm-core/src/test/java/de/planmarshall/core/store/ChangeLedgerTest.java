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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
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
 * the working-tree digest it was observed on.
 */
@DisplayName("Change ledger")
class ChangeLedgerTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-22T10:00:00Z");
    private static final String DIGEST = "a".repeat(64);
    private static final String COMMIT = "b".repeat(40);

    @TempDir
    Path base;

    private Path file;
    private ChangeLedger ledger;

    @BeforeEach
    void createLedger() throws Exception {
        file = Files.createDirectories(base.resolve("plans").resolve("some-plan")).resolve("change-ledger.jsonl");
        ledger = new ChangeLedger(new JsonlStoreFile(file, LockKey.plan(base, "pm-mcp-core", "some-plan")),
                new RecordingLockManager(), _ -> {
                });
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
            ledger.append(changeRow());
            ledger.append(changeRow().toBuilder().changedPaths(List.of("b.txt")).build());

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
            ledger.append(row);

            assertEquals(failure, value(ledger.read().getFirst()).worktreeShaFailure());
        }

        @Test
        @DisplayName("a build row names the gate it proves")
        void buildRow() {
            var row = ChangeLedgerRecord.builder().kind(Kind.BUILD).worktreeSha(DIGEST).worktreeShaVersion(1)
                    .gateKind("quality-gate").outcome(Outcome.SUCCEEDED).durationMs(1200L)
                    .jobId("b-0123456789abcdef").recordedAt(RECORDED_AT).build();

            ledger.append(row);

            assertEquals(row, value(ledger.read().getFirst()));
        }
    }

    @Nested
    @DisplayName("the schema")
    class Schema {

        @Test
        @DisplayName("ties an unavailable digest to its failure and a baseline to the baseline_red kind")
        void refusals() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha("unavailable")
                                    .worktreeShaVersion(1).recordedAt(RECORDED_AT).build()),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST)
                                    .worktreeShaVersion(1).worktreeShaFailure(new WorktreeShaFailure(
                                    List.of("git", "status"), WorktreeShaFailure.FailureKind.SIGNAL, null, 9))
                                    .recordedAt(RECORDED_AT).build()),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> ChangeLedgerRecord.builder().kind(Kind.TASK_DIFF).worktreeSha(DIGEST)
                                    .worktreeShaVersion(1).recordedAt(RECORDED_AT).build()),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> ChangeLedgerRecord.builder().kind(Kind.JOB).worktreeSha(DIGEST)
                                    .worktreeShaVersion(1).taskId("TASK-1").recordedAt(RECORDED_AT).build()));
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
    }

    @Nested
    @DisplayName("construction")
    class Construction {

        @Test
        @DisplayName("a lock that is not the lock of a scope is refused")
        void otherLockLevel() {
            assertThrows(IllegalArgumentException.class, () -> new ChangeLedger(
                    new JsonlStoreFile(file, LockKey.modelSlots(base)), new RecordingLockManager(), _ -> {
                    }));
        }
    }
}
