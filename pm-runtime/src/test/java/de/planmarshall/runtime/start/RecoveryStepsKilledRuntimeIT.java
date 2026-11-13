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
import static org.easymock.EasyMock.newCapture;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.LeaseRecord;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.CopyReconciler;
import de.planmarshall.runtime.test.AuditSinkDoubles;
import de.planmarshall.runtime.test.TestBases;

/**
 * The implemented recovery steps after a runtime killed with {@code SIGKILL} in the middle of the work each step
 * recovers (PM-ARCH-7). The killed runtime is a child JVM of {@link KilledRuntimeFixture}; the step of the next
 * runtime instance runs in this JVM on the same {@code PM_MCP_BASE}.
 * <p>
 * The modes, projects, plans, slot keys and the content written are the protocol with the child
 * ({@link RuntimeChildProcess}); the counts in the reports and the names of the audit records are the specified
 * outcome format of the steps. The instant of the lease step is generated.
 */
@EnableGeneratorController
@Timeout(value = 120, unit = TimeUnit.SECONDS)
@DisplayName("Recovery steps with a killed runtime")
class RecoveryStepsKilledRuntimeIT {

    /** Contract literals of the child-process protocol: the slot keys the child of mode leases claims. */
    private static final List<String> CHILD_SLOT_KEYS = List.of("slot-a", "slot-b");

    /** Contract literal: the wire name of the audit record of a rolled-forward transaction. */
    private static final String ROLLED_FORWARD = "transaction_rolled_forward";

    /** Contract literal: the wire name of the audit record of an applied reconciliation. */
    private static final String RECONCILED = "cross_scope_reconciled";

    private final Instant now = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());
    private final KilledRuntimeFixture fixture = new KilledRuntimeFixture();
    private final Capture<AuditEvent> auditEvents = newCapture(CaptureType.ALL);
    private final AuditSink auditSink = AuditSinkDoubles.capturing(auditEvents);

    private Path base;
    private FileLockManager lockManager;

    @BeforeEach
    void createBase() throws IOException {
        // A fixture argument, the prefix of the base directory, not test data
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
                Clock.fixed(now, ZoneOffset.UTC)).run(PROJECT_A);

        var after = store.snapshot();
        var report = assertInstanceOf(Outcome.Done.class, outcome, "the lease step must be done").report();
        assertAll(
                () -> assertEquals(CHILD_SLOT_KEYS, after.stream().map(LeaseRecord::key).toList(),
                        "both leases of the killed runtime must stay"),
                () -> assertEquals(now, after.get(0).owner().orphanedAt(), "the lease of the project is orphaned"),
                () -> assertEquals(before.get(1), after.get(1), "the lease of the other project is unchanged"),
                () -> assertFalse(after.get(1).owner().isOrphaned(), "the other lease must not be orphaned"),
                // Contract literal: the specified counts of the report of the lease step
                () -> assertTrue(report.contains("0 kept, 1 orphaned, 0 removed"),
                        "the report must count the orphaned lease"));
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

        var report = assertInstanceOf(Outcome.Done.class, first, "the first run of the step must be done").report();
        assertAll(
                () -> assertTrue(journalLeft, "the killed runtime left its journal"),
                () -> assertFalse(storeLeft, "the killed runtime did not rename"),
                // Contract literals: the specified reports of the roll-forward step
                () -> assertTrue(report.startsWith("plan " + ROLLED_PLAN + ": rolled forward"),
                        "the first run must roll the transaction forward"),
                () -> assertArrayEquals(WRITTEN, storeAfterFirst, "the store must hold the content of the child"),
                () -> assertEquals(new Outcome.Done("plan " + ROLLED_PLAN + ": nothing to recover"), second,
                        "the second run must find nothing to recover"),
                () -> assertArrayEquals(storeAfterFirst, Files.readAllBytes(scope.resolve(STORE)),
                        "the second run must leave the store unchanged"),
                () -> assertEquals(List.of(ROLLED_FORWARD), AuditSinkDoubles.eventNames(auditEvents),
                        "exactly one roll-forward record is handed over"));
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
                () -> assertArrayEquals(WRITTEN, Files.readAllBytes(authoritative.resolve(STORE)),
                        "the killed runtime wrote the authoritative store"),
                () -> assertFalse(derivedLeft, "the killed runtime did not write the derived store"),
                // Contract literals: the specified reports of the reconciliation step
                () -> assertTrue(assertInstanceOf(Outcome.Done.class, first, "the first run must be done").report()
                        .startsWith("1 reconciliations applied"), "the first run must apply one reconciliation"),
                () -> assertArrayEquals(WRITTEN, Files.readAllBytes(derived.resolve(STORE)),
                        "the derived store must hold the written content"),
                () -> assertEquals(new Outcome.Done("no reconciliation applied"), second,
                        "the second run must apply no reconciliation"),
                () -> assertEquals(List.of(RECONCILED), AuditSinkDoubles.eventNames(auditEvents),
                        "exactly one reconciliation record is handed over"));
    }
}
