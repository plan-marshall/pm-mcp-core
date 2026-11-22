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

import java.time.Instant;

/**
 * One step of the periodic sweep ({@link PeriodicSweep}) over an enrolled project
 * (doc/specification/runtime-lifecycle/01-startup-recovery-drain.adoc, Periodic Sweep).
 * <p>
 * Sweep rule, the contract of every step: a step selects candidates only from positive evidence that the residue is
 * no longer live, never from an age and never from a name that fails to parse as an identifier. For pending entries
 * the evidence is the end of the entry: the removal its creation or its abandoning began is complete when the entry's
 * record is gone, so a directory a cut-off removal left behind is residue and an entry whose record exists is open and
 * never removed. Every swept directory excludes its reserved entries by construction.
 * <p>
 * A step reports what it did as a {@link RecoveryStep.Outcome}; a failure it can name is a
 * {@link RecoveryStep.Outcome.Failed}, never an exception. A failed step is logged and stops neither the other steps
 * of its project nor the run.
 *
 * @since 0.1
 */
public interface SweepStep {

    /**
     * The steps of the periodic sweep.
     */
    enum Name {
        /**
         * The directory of a pending entry whose removal a stop cut off is removed, one audit record per removed
         * directory ({@link PendingEntryRecordsSweep}).
         */
        PENDING_ENTRY_RECORDS
    }

    /**
     * @return the name of the step
     */
    Name name();

    /**
     * Sweeps one project.
     *
     * @param projectId the id of the enrolled project, whose recovery has finished
     * @param at        the instant of the sweep's run, which its records carry
     * @return what the step did
     */
    RecoveryStep.Outcome run(String projectId, Instant at);
}
