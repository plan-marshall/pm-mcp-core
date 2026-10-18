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
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord.SelfReportContradiction;

/**
 * What the runtime observed of a job itself, the only input of the {@link OutcomeDerivation} (PM-EXEC-4): whether it
 * refused the job before spawn, which signal it sent and why, how the process ended, the state of the job log and what
 * the parser found in it, and for an in-process operation the class of its result.
 * <p>
 * A status the job reports about itself is part of the evidence only as a contradiction the parser found
 * ({@link ParseResult#selfReportContradiction()}); it is never the outcome.
 *
 * @since 0.1
 */
public sealed interface JobEvidence permits JobEvidence.RefusedBeforeSpawn, JobEvidence.NeverStarted,
        JobEvidence.ProcessEnded, JobEvidence.InProcessEnded {

    /**
     * Why the runtime sent the termination signal to a job, or cancelled an in-process operation.
     *
     * @since 0.1
     */
    enum Termination {
        /** The engine cancelled the job: a cancelled job state, the short-circuit of a composite wait, a scope end. */
        ENGINE_CANCEL,
        /** The plan that owns the job was paused with the pause point {@code now}. */
        PAUSE_NOW,
        /** The supervisor ended a worker: lost, fenced or recycled. */
        SUPERVISOR_END,
        /** The job's budget expired. */
        BUDGET_EXPIRED,
        /** The runtime drains and the job did not end within the drain timeout. */
        DRAIN,
        /** The recovery terminated the live process group of a job a previous runtime left running. */
        ORPHAN
    }

    /**
     * The state of the job log when the job ended.
     *
     * @since 0.1
     */
    enum LogState {
        /** The log was read to its end. */
        READABLE,
        /** The log does not exist. */
        MISSING,
        /** The log ends before the job's output did. */
        TRUNCATED,
        /** The log exists and cannot be read. */
        UNREADABLE
    }

    /**
     * The class of the result of an in-process operation.
     *
     * @since 0.1
     */
    enum InProcessResult {
        /** A domain outcome of the operation's closed contract enumeration. */
        DOMAIN,
        /** A transport outcome: authentication, permission, availability, a malformed answer, a missing capability. */
        TRANSPORT,
        /** An end outside the operation's contract: an unexpected exception. */
        OUT_OF_CONTRACT
    }

    /**
     * The job was refused before it was spawned, by the verify-not-resolve pipeline or by admission.
     *
     * @param reason the refusal reason
     * @since 0.1
     */
    record RefusedBeforeSpawn(String reason) implements JobEvidence {

        /** @throws NullPointerException if the reason is {@code null} */
        public RefusedBeforeSpawn {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /**
     * The job never got a process the runtime observed end: it was still queued at drain, or a previous runtime
     * instance left its record queued or running.
     *
     * @param cause why the job ended without an observed end
     * @since 0.1
     */
    record NeverStarted(Cause cause) implements JobEvidence {

        /**
         * Why a job ended without an observed end.
         *
         * @since 0.1
         */
        public enum Cause {
            /** The job was still queued when the runtime drained. */
            QUEUED_AT_DRAIN,
            /** A previous runtime instance left the record queued or running. */
            RUNTIME_RESTART
        }

        /** @throws NullPointerException if the cause is {@code null} */
        public NeverStarted {
            Objects.requireNonNull(cause, "cause");
        }
    }

    /**
     * How the process of a job ended: an exit code or a terminating signal.
     *
     * @since 0.1
     */
    sealed interface WaitStatus permits Exited, Signalled {
    }

    /**
     * The process exited.
     *
     * @param code the exit code
     * @since 0.1
     */
    record Exited(int code) implements WaitStatus {
    }

    /**
     * The process was ended by a signal.
     *
     * @param signal the name of the signal, for example {@code SIGKILL}
     * @since 0.1
     */
    record Signalled(String signal) implements WaitStatus {

        /** @throws NullPointerException if the signal is {@code null} */
        public Signalled {
            Objects.requireNonNull(signal, "signal");
        }
    }

    /**
     * What the parser of the job's kind found in the job log.
     *
     * @param successMarker           whether the positive success marker of the kind was found: the terminal success
     *                                marker of a build, the post-condition of a git job, a payload of the expected
     *                                shape of a CI job, an agreeing schema-valid payload of a project script, the final
     *                                result event of a worker
     * @param diagnostics             the number of located diagnostics
     * @param contradictingEvidence   whether the log carries evidence that contradicts a success: parsed errors,
     *                                failing test counts
     * @param selfReportContradiction the contradiction between the status a project script reported and the evidence;
     *                                empty when there is none
     * @since 0.1
     */
    record ParseResult(boolean successMarker, int diagnostics, boolean contradictingEvidence,
    Optional<SelfReportContradiction> selfReportContradiction) {

        /** What the parser found in a log it could not read: nothing. */
        public static final ParseResult NOTHING = new ParseResult(false, 0, false, Optional.empty());

        /**
         * @throws NullPointerException     if the contradiction is {@code null}
         * @throws IllegalArgumentException if the number of diagnostics is negative
         */
        public ParseResult {
            if (diagnostics < 0) {
                throw new IllegalArgumentException("diagnostics must not be negative: " + diagnostics);
            }
            Objects.requireNonNull(selfReportContradiction, "selfReportContradiction");
        }
    }

    /**
     * The process of a job ended, and the runtime observed it.
     *
     * @param kind               the kind of the job
     * @param termination        why the runtime sent the termination signal; empty when it sent none
     * @param waitStatus         how the process ended
     * @param log                the state of the job log
     * @param parse              what the parser found in the log
     * @param declaredExitCodes  the exit codes a probe declares as its success; empty for every other kind
     * @param exitReliable       whether the exit code of the job is reliable; {@code false} for a worker whose harness
     *                           profile declares it unreliable, and then the final result event replaces the exit code
     * @since 0.1
     */
    record ProcessEnded(JobId.Kind kind, Optional<Termination> termination, WaitStatus waitStatus, LogState log,
    ParseResult parse, Set<Integer> declaredExitCodes, boolean exitReliable) implements JobEvidence {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if a job other than a probe declares exit codes, or a job other than a
         *                                  worker has an unreliable exit code
         */
        public ProcessEnded {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(termination, "termination");
            Objects.requireNonNull(waitStatus, "waitStatus");
            Objects.requireNonNull(log, "log");
            Objects.requireNonNull(parse, "parse");
            declaredExitCodes = Set.copyOf(Objects.requireNonNull(declaredExitCodes, "declaredExitCodes"));
            if (!declaredExitCodes.isEmpty() && kind != JobId.Kind.PROBE) {
                throw new IllegalArgumentException("Only a probe declares its exit codes, not a " + kind.wireName());
            }
            if (!exitReliable && kind != JobId.Kind.LLM) {
                throw new IllegalArgumentException("Only a worker may have an unreliable exit code, not a "
                        + kind.wireName());
            }
        }
    }

    /**
     * An in-process operation ended.
     *
     * @param termination why the runtime cancelled the operation; empty when it did not
     * @param result      the class of the operation's result
     * @since 0.1
     */
    record InProcessEnded(Optional<Termination> termination, InProcessResult result) implements JobEvidence {

        /** @throws NullPointerException if a component is {@code null} */
        public InProcessEnded {
            Objects.requireNonNull(termination, "termination");
            Objects.requireNonNull(result, "result");
        }
    }
}
