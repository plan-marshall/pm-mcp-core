/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.runtime.job.JobEvidence.Exited;
import de.planmarshall.runtime.job.JobEvidence.InProcessEnded;
import de.planmarshall.runtime.job.JobEvidence.LogState;
import de.planmarshall.runtime.job.JobEvidence.NeverStarted;
import de.planmarshall.runtime.job.JobEvidence.ProcessEnded;
import de.planmarshall.runtime.job.JobEvidence.RefusedBeforeSpawn;
import de.planmarshall.runtime.job.JobEvidence.Signalled;
import de.planmarshall.runtime.job.JobEvidence.Termination;

/**
 * The outcome derivation of a job (PM-EXEC-4): the closed table of 13 rows that turns what the runtime observed into
 * the job's fate and the row that decided it. The rows are evaluated top to bottom, and the first that matches decides:
 * <ol>
 * <li>refused before spawn: {@code refused};</li>
 * <li>terminated for an engine cancel, a pause of the plan that holds now, or a supervisor end: {@code cancelled},
 * whatever the exit status;</li>
 * <li>terminated because the budget expired: {@code timed_out}, whatever the exit status;</li>
 * <li>ended by any other signal (drain, orphan termination, a crash or external signal), still queued at drain, or left
 * by a previous runtime instance: {@code killed};</li>
 * <li>the job log missing, truncated or unreadable: {@code indeterminate}, also for exit code 0;</li>
 * <li>a project script whose reported status contradicts the evidence: {@code indeterminate};</li>
 * <li>exit code 0 with the kind's positive success marker and no contradicting evidence: {@code succeeded};</li>
 * <li>exit code 0 without the marker or with contradicting evidence: {@code indeterminate};</li>
 * <li>a non-zero exit code with at least one parsed diagnostic: {@code failed};</li>
 * <li>a non-zero exit code without a parsed diagnostic: {@code failed_unparsed} for a build job, {@code failed} for
 * every other kind;</li>
 * <li>an in-process operation with a domain outcome: {@code succeeded};</li>
 * <li>an in-process operation with a transport outcome: {@code failed};</li>
 * <li>an in-process operation that ended outside its contract: {@code indeterminate}.</li>
 * </ol>
 * Rows 5 to 10 apply to process jobs only and rows 11 to 13 to in-process operations only; rows 1 to 4 apply to both.
 * <p>
 * The success marker of row 7 is the kind's: for a probe, an exit code the probe declares, which stands in for exit
 * code 0; for a worker whose harness declares its exit code unreliable, the final result event, which replaces the
 * exit code in rows 7 to 10. A status a job reports about itself is evidence and never the outcome: adopting it would
 * let a job that failed report itself successful.
 *
 * @since 0.1
 */
@UtilityClass
public class OutcomeDerivation {

    /** The exit code that stands for "no final result event" of a worker whose exit code is unreliable. */
    private static final int NO_FINAL_RESULT = 1;

    /**
     * The fate of a job and the row of the table that decided it.
     *
     * @param fate the fate
     * @param row  the row, 1 to 13
     * @since 0.1
     */
    public record Outcome(Fate fate, int row) {

        /**
         * @throws NullPointerException     if the fate is {@code null}
         * @throws IllegalArgumentException if the row lies outside 1 to 13
         */
        public Outcome {
            Objects.requireNonNull(fate, "fate");
            if (row < 1 || row > 13) {
                throw new IllegalArgumentException("Not a row of the outcome derivation: " + row);
            }
        }
    }

    /**
     * @param evidence what the runtime observed of the job
     * @return the fate of the job and the row that decided it
     */
    public static Outcome derive(JobEvidence evidence) {
        Objects.requireNonNull(evidence, "evidence");
        return switch (evidence) {
            case RefusedBeforeSpawn _ -> new Outcome(Fate.REFUSED, 1);
            case NeverStarted _ -> new Outcome(Fate.KILLED, 4);
            case ProcessEnded process -> process.termination().map(OutcomeDerivation::terminated)
                    .orElseGet(() -> ended(process));
            case InProcessEnded operation -> operation.termination().map(OutcomeDerivation::terminated)
                    .orElseGet(() -> switch (operation.result()) {
                        case DOMAIN -> new Outcome(Fate.SUCCEEDED, 11);
                        case TRANSPORT -> new Outcome(Fate.FAILED, 12);
                        case OUT_OF_CONTRACT -> new Outcome(Fate.INDETERMINATE, 13);
                    });
        };
    }

    private static Outcome terminated(Termination termination) {
        return switch (termination) {
            case ENGINE_CANCEL, PAUSE_NOW, SUPERVISOR_END -> new Outcome(Fate.CANCELLED, 2);
            case BUDGET_EXPIRED -> new Outcome(Fate.TIMED_OUT, 3);
            case DRAIN, ORPHAN -> new Outcome(Fate.KILLED, 4);
        };
    }

    private static Outcome ended(ProcessEnded process) {
        if (process.waitStatus() instanceof Signalled) {
            return new Outcome(Fate.KILLED, 4);
        }
        if (process.log() != LogState.READABLE) {
            return new Outcome(Fate.INDETERMINATE, 5);
        }
        var parse = process.parse();
        if (process.kind() == JobId.Kind.PROJECT_SCRIPT && parse.selfReportContradiction().isPresent()) {
            return new Outcome(Fate.INDETERMINATE, 6);
        }
        var exitCode = exitCode(process);
        var succeededExit = process.kind() == JobId.Kind.PROBE ? process.declaredExitCodes().contains(exitCode)
                : exitCode == 0 && parse.successMarker();
        if (succeededExit && !parse.contradictingEvidence()) {
            return new Outcome(Fate.SUCCEEDED, 7);
        }
        if (exitCode == 0) {
            return new Outcome(Fate.INDETERMINATE, 8);
        }
        if (parse.diagnostics() > 0) {
            return new Outcome(Fate.FAILED, 9);
        }
        return new Outcome(process.kind() == JobId.Kind.BUILD ? Fate.FAILED_UNPARSED : Fate.FAILED, 10);
    }

    /** The exit code rows 7 to 10 read: the observed one, or for an unreliable worker the final result event. */
    private static int exitCode(ProcessEnded process) {
        if (!process.exitReliable()) {
            return process.parse().successMarker() ? 0 : NO_FINAL_RESULT;
        }
        return ((Exited) process.waitStatus()).code();
    }
}
