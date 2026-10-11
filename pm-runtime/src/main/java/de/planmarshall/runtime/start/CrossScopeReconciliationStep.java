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
import de.planmarshall.core.store.CrossScopeReconciler;
import de.planmarshall.core.store.CrossScopeReconciler.Reconciliation;
import de.planmarshall.core.store.StoreRefusal;

/**
 * Step 5 of the per-project recovery ({@link RecoveryStep.Name#CROSS_SCOPE_RECONCILIATION}): the writes outside a
 * journal's scope that a crash left undone are reconciled. It runs after the journal roll-forward, by the order of
 * {@link RecoveryStep.Name}, so every authoritative store is complete when its derived writes are derived.
 * <p>
 * It runs every {@link CrossScopeReconciler} the caller registers for the project ({@link ProjectReconcilers}) with
 * the {@link LockManager} and the {@link AuditSink}, and reports the reconciliations applied; a reconciler that
 * applies nothing reports nothing. The reconcilers are given: their rules (for {@code start.create-scope}, job
 * states, the end of a worker and the rest) come with their stores.
 *
 * @since 0.1
 */
public final class CrossScopeReconciliationStep implements RecoveryStep {

    private final ProjectReconcilers reconcilers;
    private final LockManager lockManager;
    private final AuditSink auditSink;

    /**
     * The cross-scope reconcilers of a project; each store that writes across scopes registers its own.
     */
    @FunctionalInterface
    public interface ProjectReconcilers {

        /** The seam while no store writes across scopes: there is no reconciler. */
        ProjectReconcilers NONE = _ -> List.of();

        /**
         * @param projectId the id of the project being recovered
         * @return the reconcilers of the project, in the order they run
         */
        List<CrossScopeReconciler> reconcilers(String projectId);
    }

    /**
     * @param reconcilers the cross-scope reconcilers of a project
     * @param lockManager the lock manager the reconcilers take their locks from
     * @param auditSink   the audit sink of the scope transactions of the derived writes
     */
    public CrossScopeReconciliationStep(ProjectReconcilers reconcilers, LockManager lockManager, AuditSink auditSink) {
        this.reconcilers = Objects.requireNonNull(reconcilers, "reconcilers");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.auditSink = Objects.requireNonNull(auditSink, "auditSink");
    }

    @Override
    public Name name() {
        return Name.CROSS_SCOPE_RECONCILIATION;
    }

    /**
     * Runs every reconciler of the project.
     *
     * @param projectId the id of the enrolled project
     * @return the reconciliations applied; a failure when a store a reconciler reads or writes is refused or cannot
     *         be read or written, after which the remaining reconcilers do not run
     */
    @Override
    public Outcome run(String projectId) {
        var applied = new ArrayList<Reconciliation>();
        for (var reconciler : reconcilers.reconcilers(projectId)) {
            try {
                applied.addAll(reconciler.reconcile(lockManager, auditSink));
            } catch (StoreRefusal e) {
                return new Outcome.Failed("store refused (" + e.outcome() + ") after " + describe(applied));
            } catch (UncheckedIOException e) {
                return new Outcome.Failed(e.getMessage() + " after " + describe(applied));
            }
        }
        return new Outcome.Done(describe(applied));
    }

    private static String describe(List<Reconciliation> applied) {
        if (applied.isEmpty()) {
            return "no reconciliation applied";
        }
        return applied.size() + " reconciliations applied: " + String.join(", ",
                applied.stream().map(reconciliation -> reconciliation.name() + " in " + reconciliation.store())
                        .toList());
    }
}
