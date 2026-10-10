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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;

/**
 * A write across scopes in its fixed order (PM-IMPL-7): the authoritative store first, then every store whose content
 * is derived from it.
 * <p>
 * The write is several transactions, each under the lock of its own store, never one lock over all of them: every
 * step opens its own lock transaction, acquires the key of its store, writes, and closes the transaction before the
 * next step starts, so no step holds the locks of two stores. The window between two steps is closed by the order and
 * by derivation, not by a lock. A step that fails stops the order, and the steps after it are left undone; because
 * the authoritative store was written first, a {@link CrossScopeReconciler} derives them from it at the next start,
 * through {@link #reconcile(LockManager, List, AuditSink)}.
 * <p>
 * The executor knows no concrete flow: what each step writes, and how its reconciler derives it, belongs to the flow.
 *
 * @since 0.1
 */
public final class OrderedWrite {

    /**
     * One write of the order.
     *
     * @param key   the key of the lock that guards the store the step writes
     * @param write the write, run in a transaction that holds the key and no other
     * @since 0.1
     */
    public record WriteStep(LockKey key, Consumer<LockTransaction> write) {

        /** @throws NullPointerException if a component is {@code null} */
        public WriteStep {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(write, "write");
        }
    }

    /**
     * Called after each step of {@link #run(LockManager)}, once its transaction is closed. The tests of crash
     * consistency stop the order there by throwing; production passes {@link #NONE}.
     *
     * @since 0.1
     */
    @FunctionalInterface
    interface StepHook {

        /** The hook that does nothing. */
        StepHook NONE = step -> {
            // production: no boundary is observed
        };

        /** @param step the index of the step that just completed, the authoritative step being 0 */
        void after(int step);
    }

    private final List<WriteStep> steps;
    private final StepHook hook;

    /**
     * @param steps the steps in their order, the authoritative store first
     * @throws IllegalArgumentException if there is no step
     */
    public OrderedWrite(List<WriteStep> steps) {
        this(steps, StepHook.NONE);
    }

    /**
     * @param hook called after each step
     * @see #OrderedWrite(List)
     */
    OrderedWrite(List<WriteStep> steps, StepHook hook) {
        this.steps = List.copyOf(steps);
        this.hook = Objects.requireNonNull(hook, "hook");
        if (this.steps.isEmpty()) {
            throw new IllegalArgumentException("An ordered write has at least its authoritative step");
        }
    }

    /** @return the steps in their order, the authoritative store first */
    public List<WriteStep> steps() {
        return steps;
    }

    /**
     * Runs the steps in their order, each in a lock transaction of its own that holds the key of its store only.
     * What a step throws propagates unchanged once its transaction is closed; the steps after it are not run then,
     * and are left to the reconciler of the flow.
     *
     * @param lockManager the lock manager to take the lock of each store from
     */
    public void run(LockManager lockManager) {
        Objects.requireNonNull(lockManager, "lockManager");
        for (var index = 0; index < steps.size(); index++) {
            var step = steps.get(index);
            try (var transaction = lockManager.openTransaction()) {
                transaction.acquire(step.key());
                step.write().accept(transaction);
            }
            hook.after(index);
        }
    }

    /**
     * Runs the reconcilers of the writes across scopes, as the recovery of a project does at startup after the
     * journals of its scopes were rolled forward, and hands one {@code cross_scope_reconciled} record to the audit
     * sink for each derived write a reconciler applied. A reconciler that finds nothing to do leaves no record.
     *
     * @param lockManager  the lock manager the reconcilers take their locks from
     * @param reconcilers  the reconcilers, run in this order
     * @param auditSink    where the records are handed over; no lock is held when it is called
     * @return every derived write applied, in the order of the reconcilers
     */
    public static List<CrossScopeReconciler.Reconciliation> reconcile(LockManager lockManager,
            List<CrossScopeReconciler> reconcilers, AuditSink auditSink) {
        Objects.requireNonNull(lockManager, "lockManager");
        Objects.requireNonNull(auditSink, "auditSink");
        var applied = new ArrayList<CrossScopeReconciler.Reconciliation>();
        for (var reconciler : List.copyOf(reconcilers)) {
            for (var reconciliation : List.copyOf(reconciler.reconcile(lockManager))) {
                auditSink.append(AuditEvent.crossScopeReconciled(reconciliation.name(), reconciliation.store()));
                applied.add(reconciliation);
            }
        }
        return List.copyOf(applied);
    }
}
