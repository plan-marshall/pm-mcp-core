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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.CrossScopeReconciler;
import de.planmarshall.core.store.CrossScopeReconciler.Reconciliation;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.start.RecoveryStep.Outcome;
import de.planmarshall.runtime.start.RecoveryStepFixtures.CopyReconciler;
import de.planmarshall.runtime.start.RecoveryStepFixtures.RecordingAuditSink;

/**
 * The cross-scope reconciliation step of the per-project recovery with test reconcilers over plan scopes in a test
 * base, on the lock manager of the runtime.
 */
@EnableGeneratorController
@DisplayName("Cross-scope reconciliation step")
class CrossScopeReconciliationStepTest {

    /** Contract literal: the wire name of the audit record of an applied reconciliation. */
    private static final String RECONCILED = "cross_scope_reconciled";

    /** Contract literal: the outcome text of a run that applied nothing. */
    private static final String NOTHING_APPLIED = "no reconciliation applied";

    @TempDir
    Path base;

    private final RecordingAuditSink auditSink = new RecordingAuditSink();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final byte[] content = ("{\"state\":\"" + name() + "\"}").getBytes(StandardCharsets.UTF_8);

    private FileLockManager lockManager;
    private JournalScope authoritative;
    private JournalScope derived;

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @BeforeEach
    void createScopes() {
        lockManager = new FileLockManager(auditSink);
        authoritative = JournalScope.plan(base, PROJECT_A, "plan-" + name());
        derived = JournalScope.plan(base, PROJECT_A, "derived-" + name());
    }

    private CrossScopeReconciliationStep step(CrossScopeReconciler... reconcilers) {
        return new CrossScopeReconciliationStep(_ -> List.of(reconcilers), lockManager, auditSink);
    }

    @Test
    @DisplayName("is the cross-scope reconciliation step")
    void shouldBeCrossScopeReconciliationStep() {
        assertEquals(RecoveryStep.Name.CROSS_SCOPE_RECONCILIATION,
                new CrossScopeReconciliationStep(CrossScopeReconciliationStep.ProjectReconcilers.NONE, lockManager,
                        auditSink).name(), "the step must carry its specified name");
    }

    @Test
    @DisplayName("runs after the roll-forward and derives from the rolled-forward authoritative store")
    void shouldRunAfterRollForward() throws Exception {
        RecoveryStepFixtures.interruptedReplace(authoritative, content);
        var rolledForward = name();
        RecoveryStep rollForward = new JournalRollForwardStep(_ -> {
            calls.add(rolledForward);
            return List.of(authoritative);
        }, lockManager, auditSink);
        RecoveryStep reconciliation = step(new CopyReconciler(authoritative, derived, calls));
        var recovery = new ProjectRecovery(() -> List.of(PROJECT_A),
                RecoveryStepFixtures.completeSteps(reconciliation, rollForward), Runnable::run);

        var outcome = assertInstanceOf(ProjectRecovery.Result.Recovered.class, recovery.awaitRecovered(PROJECT_A),
                "the project must be recovered").outcome();

        assertAll(
                // Contract literal: "reconcile" is the call the copy reconciler of the fixtures records
                () -> assertEquals(List.of(rolledForward, "reconcile"), calls,
                        "the roll-forward must run before the reconcile"),
                () -> assertFalse(outcome.failed(), "the recovery must not fail"),
                () -> assertArrayEquals(content, Files.readAllBytes(derived.resolve(STORE)),
                        "the derived store must hold the rolled content"));
    }

    @Test
    @DisplayName("reports the reconciliations applied and hands one cross_scope_reconciled record to the sink "
            + "(the outcome text is the specified format)")
    void shouldReportReconciliationsApplied() {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(authoritative.lockKey());
            RecoveryStepFixtures.replace(transaction, authoritative, content, auditSink, null);
        }

        var outcome = step(new CopyReconciler(authoritative, derived, calls)).run(PROJECT_A);

        assertAll(
                () -> assertEquals(new Outcome.Done("1 reconciliations applied: " + COPY_RULE + " in "
                        + derived.resolve(STORE)), outcome, "the outcome must name the reconciliation"),
                () -> assertEquals(List.of(RECONCILED), auditSink.eventNames(),
                        "one reconciled record must reach the sink"));
    }

    @Test
    @DisplayName("reports that nothing was applied when every derived write is in place")
    void shouldReportEmptyWhenInPlace() {
        var reconciler = new CopyReconciler(authoritative, derived, calls);
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(authoritative.lockKey());
            RecoveryStepFixtures.replace(transaction, authoritative, content, auditSink, null);
        }
        step(reconciler).run(PROJECT_A);

        var again = step(reconciler).run(PROJECT_A);

        assertAll(
                () -> assertEquals(new Outcome.Done(NOTHING_APPLIED), again,
                        "a second run must apply nothing"),
                () -> assertEquals(List.of(RECONCILED), auditSink.eventNames(), "no second record"),
                () -> assertEquals(new Outcome.Done(NOTHING_APPLIED), step().run(PROJECT_A),
                        "a run without reconcilers applies nothing"));
    }

    @Test
    @DisplayName("runs the reconcilers in their order and reports what each applied (the outcome text is the format)")
    void shouldRunReconcilersInOrder() {
        var order = new ArrayList<String>();
        var firstRule = new Reconciliation("rule-" + name(), "store-" + name());
        var secondRule = new Reconciliation("rule-" + name(), "store-" + name());
        CrossScopeReconciler first = (_, _) -> {
            order.add(firstRule.name());
            return List.of(firstRule);
        };
        CrossScopeReconciler second = (_, _) -> {
            order.add(secondRule.name());
            return List.of(secondRule);
        };

        var outcome = step(first, second).run(PROJECT_A);

        assertAll(
                () -> assertEquals(List.of(firstRule.name(), secondRule.name()), order,
                        "the reconcilers must run in their order"),
                () -> assertEquals(new Outcome.Done("2 reconciliations applied: %s in %s, %s in %s".formatted(
                        firstRule.name(), firstRule.store(), secondRule.name(), secondRule.store())), outcome,
                        "the outcome must name what each applied"));
    }

    @Test
    @DisplayName("a store that cannot be read is a failure, and the remaining reconcilers do not run")
    void shouldFailOnUnreadableStore() {
        var order = new ArrayList<String>();
        var reason = Generators.letterStrings(5, 30).next();
        CrossScopeReconciler failing = (_, _) -> {
            throw new UncheckedIOException(reason, new IOException(reason));
        };
        CrossScopeReconciler after = (_, _) -> {
            order.add(name());
            return List.of();
        };

        var outcome = step(failing, after).run(PROJECT_A);

        assertAll(
                () -> assertEquals(new Outcome.Failed(reason + " after " + NOTHING_APPLIED), outcome,
                        "the failure must carry the reason of the store"),
                () -> assertEquals(List.of(), order, "the remaining reconcilers must not run"));
    }
}
