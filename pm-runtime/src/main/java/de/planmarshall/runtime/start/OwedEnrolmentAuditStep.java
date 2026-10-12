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
import java.util.Objects;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.enrolment.Enrolments;

/**
 * Step 2 of the per-project recovery, {@link RecoveryStep.Name#OWED_ENROLMENT_AUDIT}
 * (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Per-Project Recovery; PM-IMPL-7): the audit
 * records the project's enrolment writes still owe are handed over, under the project's enrolment lock
 * ({@link Enrolments#replayOwedAudit(String)}).
 * <p>
 * A record the audit sink refuses again stays owed and is not a failure: the recovery is not refused for it. An intent
 * file that is unreadable, of another format version, or cannot be read, rewritten or removed fails the step, and the
 * intent is kept.
 * <p>
 * {@link #unenrolledPass()} is the background pass of the specification for an intent whose project is no longer
 * enrolled: it hands over what every project without its enrolment file owes and removes the orphan sidecar an
 * interrupted {@code unenrol} left ({@link Enrolments#replayOwedAuditOfUnenrolledProjects()}); the per-project recovery
 * lists enrolment files only and never sees such a project. A failure is logged ({@code PM_MCP-167}) and leaves the
 * intent, and the sidecar, in place; one project's failure does not block the others.
 *
 * @since 0.1
 */
public final class OwedEnrolmentAuditStep implements RecoveryStep {

    private static final CuiLogger LOGGER = new CuiLogger(OwedEnrolmentAuditStep.class);

    private final Enrolments enrolments;

    /** @param enrolments the operator writes of the enrolment store, which hold the owed records */
    public OwedEnrolmentAuditStep(Enrolments enrolments) {
        this.enrolments = Objects.requireNonNull(enrolments, "enrolments");
    }

    @Override
    public Name name() {
        return Name.OWED_ENROLMENT_AUDIT;
    }

    @Override
    public Outcome run(String projectId) {
        try {
            return new Outcome.Done(enrolments.replayOwedAudit(projectId) + " owed audit records handed over");
        } catch (StoreRefusal e) {
            return new Outcome.Failed("owed audit records refused: " + e.getMessage());
        } catch (UncheckedIOException e) {
            return new Outcome.Failed("owed audit records cannot be handed over: " + e.getMessage());
        }
    }

    /**
     * @return the background task that hands over the records of the projects without an enrolment file and removes
     *         the orphan sidecar of an interrupted {@code unenrol}; it catches a refused or failing intent or sidecar
     *         and logs it, and lets any other failure propagate
     */
    public Runnable unenrolledPass() {
        return () -> {
            try {
                enrolments.replayOwedAuditOfUnenrolledProjects();
            } catch (StoreRefusal | UncheckedIOException e) {
                LOGGER.warn(e, WARN.ENROLMENT_AUDIT_REPLAY_FAILED, e.getMessage());
            }
        };
    }
}
