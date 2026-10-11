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
import java.util.Objects;

/**
 * One step of the periodic sweep ({@link PeriodicSweep}) over an enrolled project
 * (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Per-Project Recovery).
 * <p>
 * Sweep rule, the contract of every step: a step selects candidates only from positive evidence of abandonment, never
 * from a name that fails to parse as an identifier. For pending entry records the age is the only evidence: a record
 * older than the instant the step is given is removed, a younger one never. Every swept directory excludes its
 * reserved entries by construction.
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
         * Pending entry records older than the maximum age are removed, one audit record per removed record. The
         * pending store comes later; until then this is a named seam.
         */
        PENDING_ENTRY_RECORDS,
        /**
         * Fenced session generations are removed under the queue lock. The queue comes later; until then this is a
         * named seam.
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
     * @param olderThan the instant before which a pending entry record is old enough to be removed
     * @return what the step did
     */
    RecoveryStep.Outcome run(String projectId, Instant olderThan);

    /**
     * A named seam whose content comes with a later store: it does nothing and reports
     * {@value RecoveryStep#NOT_IMPLEMENTED}.
     *
     * @param name the name of the step
     * @return the no-op step
     */
    static SweepStep notImplemented(Name name) {
        Objects.requireNonNull(name, "name");
        return new SweepStep() {

            @Override
            public Name name() {
                return name;
            }

            @Override
            public RecoveryStep.Outcome run(String projectId, Instant olderThan) {
                return new RecoveryStep.Outcome.Done(RecoveryStep.NOT_IMPLEMENTED);
            }
        };
    }
}
