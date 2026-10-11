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

import static de.planmarshall.runtime.start.RecoveryStepFixtures.COPY_RULE;
import static de.planmarshall.runtime.start.RecoveryStepFixtures.PROJECT_A;
import static de.planmarshall.runtime.start.RecoveryStepFixtures.STORE;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;


import de.planmarshall.core.store.CrossScopeReconciler;
import de.planmarshall.core.store.CrossScopeReconciler.Reconciliation;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.CopyReconciler;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The cross-scope reconciliation step of the per-project recovery with test reconcilers over plan scopes in a test
 * base, on the lock manager of the runtime.
 */
@DisplayName("Cross-scope reconciliation step")
class CrossScopeReconciliationStepTest {

    private static final byte[] CONTENT = "{\"state\":\"merged\"}".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path base;

    private final RecordingAuditSink auditSink = new RecordingAuditSink();
    private final List<String> calls = new CopyOnWriteArrayList<>();

    private FileLockManager lockManager;
    private JournalScope authoritative;
    private JournalScope derived;

    @BeforeEach
    void createScopes() {
        lockManager = new FileLockManager(auditSink);
        authoritative = JournalScope.plan(base, PROJECT_A, "plan-a");
        derived = JournalScope.plan(base, PROJECT_A, "plan-b");
    }

    private CrossScopeReconciliationStep step(CrossScopeReconciler... reconcilers) {
        return new CrossScopeReconciliationStep(_ -> List.of(reconcilers), lockManager, auditSink);
    }

    @Test
    @DisplayName("is the cross-scope reconciliation step")
    void shouldBeCrossScopeReconciliationStep() {
        assertEquals(RecoveryStep.Name.CROSS_SCOPE_RECONCILIATION,
                new CrossScopeReconciliationStep(CrossScopeReconciliationStep.ProjectReconcilers.NONE, lockManager,
                        auditSink).name());
    }

    @Test
    @DisplayName("runs after the roll-forward and derives from the rolled-forward authoritative store")
    void shouldRunAfterRollForward() throws Exception {
        RecoveryStepFixtures.interruptedReplace(authoritative, CONTENT);
        RecoveryStep rollForward = new JournalRollForwardStep(_ -> {
            calls.add("roll-forward");
            return List.of(authoritative);
        }, lockManager, auditSink);
        RecoveryStep reconciliation = step(new CopyReconciler(authoritative, derived, calls));
        var recovery = new ProjectRecovery(() -> List.of(PROJECT_A), List.of(reconciliation, rollForward),
                Runnable::run);

        var outcome = assertInstanceOf(ProjectRecovery.Result.Recovered.class, recovery.awaitRecovered(PROJECT_A))
                .outcome();

        assertAll(
                () -> assertEquals(List.of("roll-forward", "reconcile"), calls),
                () -> assertFalse(outcome.failed(), outcome::toString),
                () -> assertArrayEquals(CONTENT, Files.readAllBytes(derived.resolve(STORE))));
    }

    @Test
    @DisplayName("reports the reconciliations applied and hands one cross_scope_reconciled record to the sink")
    void shouldReportReconciliationsApplied() {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(authoritative.lockKey());
            RecoveryStepFixtures.replace(transaction, authoritative, CONTENT, auditSink, null);
        }

        var outcome = step(new CopyReconciler(authoritative, derived, calls)).run(PROJECT_A);

        assertAll(
                () -> assertEquals(new Outcome.Done("1 reconciliations applied: " + COPY_RULE + " in "
                        + derived.resolve(STORE)), outcome),
                () -> assertEquals(List.of("cross_scope_reconciled"), auditSink.eventNames()));
    }

    @Test
    @DisplayName("reports that nothing was applied when every derived write is in place")
    void shouldReportEmptyWhenInPlace() {
        var reconciler = new CopyReconciler(authoritative, derived, calls);
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(authoritative.lockKey());
            RecoveryStepFixtures.replace(transaction, authoritative, CONTENT, auditSink, null);
        }
        step(reconciler).run(PROJECT_A);

        var again = step(reconciler).run(PROJECT_A);

        assertAll(
                () -> assertEquals(new Outcome.Done("no reconciliation applied"), again),
                () -> assertEquals(List.of("cross_scope_reconciled"), auditSink.eventNames(), "no second record"),
                () -> assertEquals(new Outcome.Done("no reconciliation applied"), step().run(PROJECT_A)));
    }

    @Test
    @DisplayName("runs the reconcilers in their order and reports what each applied")
    void shouldRunReconcilersInOrder() {
        var order = new ArrayList<String>();
        CrossScopeReconciler first = (_, _) -> {
            order.add("first");
            return List.of(new Reconciliation("rule-1", "store-1"));
        };
        CrossScopeReconciler second = (_, _) -> {
            order.add("second");
            return List.of(new Reconciliation("rule-2", "store-2"));
        };

        var outcome = step(first, second).run(PROJECT_A);

        assertAll(
                () -> assertEquals(List.of("first", "second"), order),
                () -> assertEquals(new Outcome.Done("2 reconciliations applied: rule-1 in store-1, rule-2 in store-2"),
                        outcome));
    }

    @Test
    @DisplayName("a store that cannot be read is a failure, and the remaining reconcilers do not run")
    void shouldFailOnUnreadableStore() {
        var order = new ArrayList<String>();
        CrossScopeReconciler failing = (_, _) -> {
            throw new UncheckedIOException("disk gone", new IOException("disk gone"));
        };
        CrossScopeReconciler after = (_, _) -> {
            order.add("after");
            return List.of();
        };

        var outcome = step(failing, after).run(PROJECT_A);

        assertAll(
                () -> assertEquals(new Outcome.Failed("disk gone after no reconciliation applied"), outcome),
                () -> assertEquals(List.of(), order));
    }
}
