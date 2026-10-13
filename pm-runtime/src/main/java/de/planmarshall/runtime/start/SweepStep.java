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
 * (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Per-Project Recovery).
 * <p>
 * Sweep rule, the contract of every step: a step selects candidates only from positive evidence of abandonment, never
 * from a name that fails to parse as an identifier. For pending entry records the age is the only evidence: a record
 * older than the instant the step is given is removed, a younger one never. A session generation is removed only once
 * it is fenced, and only if its fence is older than that instant. Every swept directory excludes its reserved entries
 * by construction.
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
         * Pending entry records older than the maximum age are removed, one audit record per removed record
         * ({@link PendingEntryRecordsSweep}).
         */
        PENDING_ENTRY_RECORDS,
        /**
         * Session generations fenced before the maximum age are removed under the queue lock
         * ({@link FencedSessionGenerationsSweep}).
         */
        FENCED_SESSION_GENERATIONS
    }

    /**
     * @return the name of the step
     */
    Name name();

    /**
     * Sweeps one project.
     *
     * @param projectId the id of the enrolled project, whose recovery has finished
     * @param olderThan the instant before which a pending entry record, or the fence of a session generation, is old
     *                  enough to be removed
     * @return what the step did
     */
    RecoveryStep.Outcome run(String projectId, Instant olderThan);
}
