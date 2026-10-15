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

import java.util.Objects;

/**
 * One step of the per-project recovery ({@link ProjectRecovery}), in the order of
 * doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Per-Project Recovery.
 * <p>
 * Sweep rule, the contract of every step: a step selects candidates only from positive evidence of abandonment (a
 * dead owner identified by PID plus start instant, a terminal state, or an expired lease or retention period), never
 * from a name that fails to parse as an identifier, and every swept directory excludes its reserved entries by
 * construction.
 * <p>
 * A step reports what it did as an {@link Outcome}; a failure it can name is a {@link Outcome.Failed}, never an
 * exception. A failed step stops the recovery of its project only.
 *
 * @since 0.1
 */
public interface RecoveryStep {

    /** The report of a step whose content is not implemented in this runtime yet. */
    String NOT_IMPLEMENTED = "not implemented in this runtime";

    /**
     * The steps of the per-project recovery, in the order of the specification.
     */
    enum Name {
        /** Step 1: enrolment integrity verification (PM-SEC-5). */
        ENROLMENT_INTEGRITY,
        /** Step 2: the audit records the project's enrolment writes still owe are handed over. */
        OWED_ENROLMENT_AUDIT,
        /** Step 3: jobs and workers left by the previous runtime instance are settled. */
        JOBS_AND_WORKERS,
        /** Step 4: leases of dead holders are marked orphaned; released only on positive evidence. */
        LEASES,
        /** Step 5: interrupted transaction journals are rolled forward. */
        JOURNAL_ROLL_FORWARD,
        /** Step 6: writes outside a journal's scope are reconciled. */
        CROSS_SCOPE_RECONCILIATION,
        /** Step 7: the recorded worktree presence of the plans is reconciled. */
        WORKTREE_PRESENCE,
        /** Step 8: engine deadlines are re-armed. */
        ENGINE_DEADLINES,
        /** Step 9: the drive of every non-terminal plan and epic resumes. */
        RESUME_DRIVE,
        /** Step 10: pending entry records older than the maximum age are removed. */
        PENDING_RECORD_SWEEP,
        /** Step 11: baseline scratch worktrees of gone or terminal plans are removed. */
        BASELINE_WORKTREE_SWEEP
    }

    /**
     * What a step did.
     */
    sealed interface Outcome {

        /**
         * The step is done.
         *
         * @param report a short report of what the step did
         */
        record Done(String report) implements Outcome {

            /**
             * @throws NullPointerException if the report is {@code null}
             */
            public Done {
                Objects.requireNonNull(report, "report");
            }
        }

        /**
         * The step failed; the recovery of the project stops.
         *
         * @param reason why the step failed
         */
        record Failed(String reason) implements Outcome {

            /**
             * @throws NullPointerException if the reason is {@code null}
             */
            public Failed {
                Objects.requireNonNull(reason, "reason");
            }
        }
    }

    /**
     * @return the name of the step, which sets its place in the order
     */
    Name name();

    /**
     * Runs the step for one project.
     *
     * @param projectId the id of the enrolled project
     * @return what the step did
     */
    Outcome run(String projectId);

    /**
     * A named seam whose content comes with a later store or component: it does nothing and reports
     * {@value #NOT_IMPLEMENTED}.
     *
     * @param name the name of the step
     * @return the no-op step
     */
    static RecoveryStep notImplemented(Name name) {
        Objects.requireNonNull(name, "name");
        return new RecoveryStep() {

            @Override
            public Name name() {
                return name;
            }

            @Override
            public Outcome run(String projectId) {
                return new Outcome.Done(NOT_IMPLEMENTED);
            }
        };
    }
}
