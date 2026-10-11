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

import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.store.JournalRecovery;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.StoreRefusal;

/**
 * Step 4 of the per-project recovery ({@link RecoveryStep.Name#JOURNAL_ROLL_FORWARD}): the interrupted transaction
 * journals of the project are rolled forward. For each scope of the project ({@link ProjectScopes}: the plan, epic
 * and workspace scopes and the lesson store) it opens a transaction on the {@link LockManager}, acquires the key of
 * the scope and calls {@link JournalRecovery#recover}.
 * <p>
 * A rolled-forward journal and a scope with nothing to recover are reported. An
 * {@link JournalRecovery.InvariantViolation} and a {@link StoreRefusal} modify no store and are failures of that
 * scope, an integrity breach for the caller to raise; the step goes on with the next scope, and its outcome is then a
 * failure that lists every failed scope, which stops the recovery of the project before the cross-scope
 * reconciliation.
 *
 * @since 0.1
 */
public final class JournalRollForwardStep implements RecoveryStep {

    private final ProjectScopes scopes;
    private final LockManager lockManager;
    private final AuditSink auditSink;

    /**
     * The journal scopes of a project; the per-project machine store package fills this seam.
     */
    @FunctionalInterface
    public interface ProjectScopes {

        /** The seam while no machine store lists the scopes of a project: there is none. */
        ProjectScopes NONE = _ -> List.of();

        /**
         * @param projectId the id of the project being recovered
         * @return the journal scopes of the project
         */
        List<JournalScope> scopes(String projectId);
    }

    /**
     * @param scopes      the journal scopes of a project
     * @param lockManager the lock manager the transactions are opened on
     * @param auditSink   where the records of a roll-forward are handed over
     */
    public JournalRollForwardStep(ProjectScopes scopes, LockManager lockManager, AuditSink auditSink) {
        this.scopes = Objects.requireNonNull(scopes, "scopes");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    @Override
    public Name name() {
        return Name.JOURNAL_ROLL_FORWARD;
    }

    /**
     * Recovers every scope of the project.
     *
     * @param projectId the id of the enrolled project
     * @return the report of every scope; a failure listing the failed scopes, and the others, if a scope failed
     */
    @Override
    public Outcome run(String projectId) {
        var reports = new ArrayList<String>();
        var failures = new ArrayList<String>();
        for (var scope : scopes.scopes(projectId)) {
            var label = label(scope);
            try {
                switch (recover(scope)) {
                    case JournalRecovery.RolledForward(var journal, var applied) -> reports.add(
                            label + ": rolled forward " + journal.transactionId().value() + ", applied " + applied);
                    case JournalRecovery.NothingToRecover(var deleted) -> reports.add(label + ": nothing to recover"
                            + (deleted.isEmpty() ? "" : ", " + deleted.size() + " staged files deleted"));
                    case JournalRecovery.InvariantViolation(var journal, var stores) -> failures.add(label
                            + ": invariant violation in " + stores + ", journal " + journal.transactionId().value()
                            + " left in place");
                }
            } catch (StoreRefusal e) {
                failures.add(label + ": store refused (" + e.outcome() + ")");
            } catch (UncheckedIOException e) {
                failures.add(label + ": " + e.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            var all = new ArrayList<>(failures);
            all.addAll(reports);
            return new Outcome.Failed(String.join("; ", all));
        }
        return new Outcome.Done(reports.isEmpty() ? "no journal scope" : String.join("; ", reports));
    }

    private JournalRecovery.Outcome recover(JournalScope scope) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(scope.lockKey());
            return JournalRecovery.recover(transaction, scope, auditSink);
        }
    }

    private static String label(JournalScope scope) {
        return scope.type().wireName() + (scope.scopeId() == null ? "" : " " + scope.scopeId());
    }
}
