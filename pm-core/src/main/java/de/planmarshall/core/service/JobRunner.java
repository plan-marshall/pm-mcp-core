/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;

/**
 * The job runner (PM-EXEC-4): the service through which a job of any kind is started, cancelled and read. The job
 * runtime of {@code pm-runtime} implements it; no class of {@code pm-core} does. It stands beside the engine port
 * {@link de.planmarshall.core.spi.JobStarter}, which starts only the job of a job state.
 * <p>
 * The runner verifies a job before it starts it. It opens the transaction of that verification itself and closes it
 * before the launch, so no caller holds the enrolment lock while a job is launched or awaited: a caller of this
 * interface never passes a transaction in, and a job that runs for its whole budget never holds a lock of its caller.
 * <p>
 * A start the runner declines is a {@link StartResult.Refused} value, never an exception; the refusal is recorded as
 * the job's fate {@code refused} where a record was written. A cancel names its cause, and the cause decides the fate
 * of the terminated job by the outcome derivation (rows 2 to 4).
 *
 * @param <R> the request of a job as the runtime verifies it: what the job runs and where
 * @param <C> the candidate record of a probe that a scan or a detection wrote
 * @since 0.1
 */
public interface JobRunner<R, C> {

    /**
     * Starts a job. The job is verified, recorded and launched. While the runtime no longer admits jobs because it
     * drains, a verified job is recorded as queued instead, never launched, and settled without a process. A start
     * does not wait for a free build or model slot.
     *
     * @param request what to start
     * @return the id of the started job, or the refusal of the start
     */
    StartResult start(StartRequest<R> request);

    /**
     * Starts a probe of a program that is not enrolled yet, verified against its candidate record instead of an
     * enrolment, with the fixed budget of its probe.
     *
     * @param request the candidate and whether the operator consented to its kind
     * @return the id of the started probe job, or the refusal of the start
     */
    StartResult startProbe(ProbeRequest<C> request);

    /**
     * Cancels a job that is queued or running: a running job's process group is terminated, a queued job is settled
     * without a signal.
     *
     * @param jobId the job
     * @param cause why the job is cancelled; it decides the job's fate
     * @return whether the job was queued or running and is cancelled by this call; {@code false} for a job that has
     *         settled or that the runner does not know
     */
    boolean cancel(JobId jobId, CancelCause cause);

    /**
     * Reads the record of a job.
     *
     * @param jobId the job
     * @return the record as it is now; empty if the runner knows no such job
     */
    Optional<JobRecord> record(JobId jobId);

    /**
     * What a start asks for.
     *
     * @param kind             the kind of the job; never {@code probe}, which is started by
     *                         {@link JobRunner#startProbe(ProbeRequest)}
     * @param scope            the scope that owns the job
     * @param budgetKey        the budget key of the job
     * @param budgetSeconds    the budget of the job in seconds, at least 1
     * @param budgetDerivation how the budget was derived
     * @param request          what the job runs and where, verified by the runtime before the launch
     * @param stdin            the in-memory payload the job reads from its standard input, never part of its argument
     *                         vector; empty for none
     * @param <R>              the request of a job
     * @since 0.1
     */
    record StartRequest<R>(JobId.Kind kind, Scope scope, String budgetKey, long budgetSeconds,
    BudgetDerivation budgetDerivation, R request, Optional<String> stdin) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the kind is {@code probe}, the budget key is blank or the budget is less
         *                                  than one second
         */
        public StartRequest {
            Objects.requireNonNull(kind, "kind");
            if (kind == JobId.Kind.PROBE) {
                throw new IllegalArgumentException("A probe is started from its candidate record, not as a job");
            }
            Objects.requireNonNull(scope, "scope");
            Objects.requireNonNull(budgetKey, "budgetKey");
            if (budgetKey.isBlank()) {
                throw new IllegalArgumentException("budgetKey must not be blank");
            }
            if (budgetSeconds < 1) {
                throw new IllegalArgumentException("budgetSeconds must be at least 1: " + budgetSeconds);
            }
            Objects.requireNonNull(budgetDerivation, "budgetDerivation");
            Objects.requireNonNull(request, "request");
            Objects.requireNonNull(stdin, "stdin");
        }
    }

    /**
     * What a probe start asks for.
     *
     * @param candidate the candidate record of the probe
     * @param consented whether the operator consented to probes of its kind; a probe without that consent is never
     *                  started
     * @param <C>       the candidate record of a probe
     * @since 0.1
     */
    record ProbeRequest<C>(C candidate, boolean consented) {

        /** @throws NullPointerException if the candidate is {@code null} */
        public ProbeRequest {
            Objects.requireNonNull(candidate, "candidate");
        }
    }

    /**
     * What a start returns: the started job or the refusal.
     *
     * @since 0.1
     */
    sealed interface StartResult permits StartResult.Started, StartResult.Refused {

        /**
         * The job was started, or recorded as queued for its slot.
         *
         * @param jobId the id of the job
         * @since 0.1
         */
        record Started(JobId jobId) implements StartResult {

            /** @throws NullPointerException if the id is {@code null} */
            public Started {
                Objects.requireNonNull(jobId, "jobId");
            }
        }

        /**
         * The runner declined the start; no process was started.
         *
         * @param reason the refusal reason, for example {@code executable_digest_mismatch}
         * @since 0.1
         */
        record Refused(String reason) implements StartResult {

            private static final Pattern REASON = Pattern.compile("[a-z][a-z0-9_]*");

            /**
             * @throws NullPointerException     if the reason is {@code null}
             * @throws IllegalArgumentException if the reason is not a lower-case snake-case word
             */
            public Refused {
                Objects.requireNonNull(reason, "reason");
                if (!REASON.matcher(reason).matches()) {
                    throw new IllegalArgumentException("Not a refusal reason: " + reason);
                }
            }
        }
    }

    /**
     * Why a job is cancelled. The set is closed; each cause decides the fate of the terminated job by its row of the
     * outcome derivation.
     *
     * @since 0.1
     */
    enum CancelCause {
        /** The engine cancelled the job: a cancelled job state, the short-circuit of a composite wait, a scope end. */
        ENGINE_CANCEL(2, Fate.CANCELLED),
        /** The plan that owns the job was paused with the pause point {@code now}. */
        PLAN_PAUSED_NOW(2, Fate.CANCELLED),
        /** The supervisor ended a worker: lost, fenced or recycled. */
        SUPERVISOR_END(2, Fate.CANCELLED),
        /** The job's budget expired. */
        BUDGET_EXPIRED(3, Fate.TIMED_OUT),
        /** The runtime drains and the job did not end within the drain timeout. */
        DRAIN(4, Fate.KILLED);

        private final int outcomeRow;
        private final Fate fate;

        CancelCause(int outcomeRow, Fate fate) {
            this.outcomeRow = outcomeRow;
            this.fate = fate;
        }

        /** @return the row of the outcome derivation a job cancelled for this cause settles by */
        public int outcomeRow() {
            return outcomeRow;
        }

        /** @return the fate of a job cancelled for this cause */
        public Fate fate() {
            return fate;
        }
    }
}
