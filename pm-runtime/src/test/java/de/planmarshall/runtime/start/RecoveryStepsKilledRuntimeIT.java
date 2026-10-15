/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static de.planmarshall.runtime.start.RecoveryStepFixtures.PROJECT_A;
import static de.planmarshall.runtime.start.RecoveryStepFixtures.STORE;
import static de.planmarshall.runtime.start.RuntimeChildProcess.DERIVED_PLAN;
import static de.planmarshall.runtime.start.RuntimeChildProcess.ROLLED_PLAN;
import static de.planmarshall.runtime.start.RuntimeChildProcess.WRITTEN;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.CopyReconciler;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;
import de.planmarshall.runtime.test.TestBases;

/**
 * The implemented recovery steps after a runtime killed with {@code SIGKILL} in the middle of the work each step
 * recovers (PM-ARCH-7). The killed runtime is a child JVM of {@link KilledRuntimeFixture}; the step of the next
 * runtime instance runs in this JVM on the same {@code PM_MCP_BASE}.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Recovery steps with a killed runtime")
class RecoveryStepsKilledRuntimeIT {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();
    private final RecordingAuditSink auditSink = new RecordingAuditSink();

    private Path base;
    private FileLockManager lockManager;

    @BeforeEach
    void createBase() throws IOException {
        base = Files.createDirectory(TestBases.create("pms"));
        lockManager = new FileLockManager(auditSink);
    }

    @AfterEach
    void killChildAndDeleteBase() throws InterruptedException {
        try {
            fixture.close();
        } finally {
            TestBases.delete(base);
        }
    }

    private void killInside(String mode) throws Exception {
        fixture.start(mode, base);
        fixture.awaitMarker(RuntimeChildProcess.inside(mode));
        fixture.kill();
    }

    @Test
    @DisplayName("the leases of a killed runtime: the next instance orphans exactly the leases of its project")
    void shouldOrphanLeasesOfKilledRuntime() throws Exception {
        killInside(RuntimeChildProcess.LEASES);
        var store = RecoveryStepFixtures.buildSlots(base, lockManager);
        var before = store.snapshot();

        var outcome = new LeaseRecoveryStep(List.of(store), LeaseRecoveryStep.AbandonmentEvidence.NONE,
                Clock.fixed(NOW, ZoneOffset.UTC)).run(PROJECT_A);

        var after = store.snapshot();
        var report = assertInstanceOf(Outcome.Done.class, outcome).report();
        assertAll(
                () -> assertEquals(List.of("slot-a", "slot-b"), after.stream().map(LeaseRecord::key).toList()),
                () -> assertEquals(NOW, after.get(0).owner().orphanedAt(), "lease of the project orphaned"),
                () -> assertEquals(before.get(1), after.get(1), "lease of the other project unchanged"),
                () -> assertFalse(after.get(1).owner().isOrphaned()),
                () -> assertTrue(report.contains("0 kept, 1 orphaned, 0 removed"), report));
    }

    @Test
    @DisplayName("a runtime killed after the journal and before the rename: the next instance rolls it forward once")
    void shouldRollForwardTransactionOfKilledRuntime() throws Exception {
        killInside(RuntimeChildProcess.ROLLFORWARD);
        var scope = JournalScope.plan(base, PROJECT_A, ROLLED_PLAN);
        var journalLeft = Files.exists(scope.journalFile());
        var storeLeft = Files.exists(scope.resolve(STORE));
        var step = new JournalRollForwardStep(_ -> List.of(scope), lockManager, auditSink);

        var first = step.run(PROJECT_A);
        var storeAfterFirst = Files.readAllBytes(scope.resolve(STORE));
        var second = step.run(PROJECT_A);

        var report = assertInstanceOf(Outcome.Done.class, first).report();
        assertAll(
                () -> assertTrue(journalLeft, "the killed runtime left its journal"),
                () -> assertFalse(storeLeft, "the killed runtime did not rename"),
                () -> assertTrue(report.startsWith("plan " + ROLLED_PLAN + ": rolled forward"), report),
                () -> assertArrayEquals(WRITTEN, storeAfterFirst),
                () -> assertEquals(new Outcome.Done("plan " + ROLLED_PLAN + ": nothing to recover"), second),
                () -> assertArrayEquals(storeAfterFirst, Files.readAllBytes(scope.resolve(STORE)), "unchanged"),
                () -> assertEquals(List.of("transaction_rolled_forward"), auditSink.eventNames()));
    }

    @Test
    @DisplayName("a runtime killed between the writes of an ordered write: the next instance derives the write once")
    void shouldReconcileOrderedWriteOfKilledRuntime() throws Exception {
        killInside(RuntimeChildProcess.ORDERED_WRITE);
        var authoritative = JournalScope.plan(base, PROJECT_A, ROLLED_PLAN);
        var derived = JournalScope.plan(base, PROJECT_A, DERIVED_PLAN);
        var derivedLeft = Files.exists(derived.resolve(STORE));
        var reconciler = new CopyReconciler(authoritative, derived, new CopyOnWriteArrayList<>());
        var step = new CrossScopeReconciliationStep(_ -> List.of(reconciler), lockManager, auditSink);

        var first = step.run(PROJECT_A);
        var second = step.run(PROJECT_A);

        assertAll(
                () -> assertArrayEquals(WRITTEN, Files.readAllBytes(authoritative.resolve(STORE))),
                () -> assertFalse(derivedLeft, "the killed runtime did not write the derived store"),
                () -> assertTrue(assertInstanceOf(Outcome.Done.class, first).report()
                        .startsWith("1 reconciliations applied"), first::toString),
                () -> assertArrayEquals(WRITTEN, Files.readAllBytes(derived.resolve(STORE))),
                () -> assertEquals(new Outcome.Done("no reconciliation applied"), second),
                () -> assertEquals(List.of("cross_scope_reconciled"), auditSink.eventNames()));
    }
}
