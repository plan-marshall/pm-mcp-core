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

import java.time.Duration;
import java.time.InstantSource;
import java.util.Objects;
import java.util.OptionalInt;

import de.planmarshall.runtime.job.ProcessGroupSignals.Answer;
import de.planmarshall.runtime.job.ProcessGroupSignals.Delivered;
import de.planmarshall.runtime.job.ProcessGroupSignals.NoSuchProcess;
import de.planmarshall.runtime.job.ProcessGroupSignals.Refused;
import de.planmarshall.runtime.job.ProcessGroupSignals.Signal;

/**
 * Steps 1 to 4 of the termination protocol of a job (PM-EXEC-4), for every cause the runtime terminates a job for: an
 * engine cancel, a pause of the plan that holds now, the end of a worker by the supervisor, budget expiry, drain and the
 * termination of a live orphan group.
 * <ol>
 * <li>{@code SIGTERM} to the job's process group, whose id is the job PID;</li>
 * <li>the termination grace period of {@value #GRACE_PERIOD_SECONDS} seconds, in which the group may exit by itself;</li>
 * <li>{@code SIGKILL} to the group when any of its members still lives, also when the leader has exited and only
 * descendants remain, because the group exists as long as one member does;</li>
 * <li>the cancel before the group exists: {@code pm-exec} calls {@code setpgid} after it starts, so a termination that
 * reaches the job in that window gets {@code ESRCH} from the group. The protocol then sends {@code SIGTERM} to the PID
 * itself and retries against the group every {@value #GROUP_RETRY_INTERVAL_MILLIS} ms for at most
 * {@value #GROUP_RETRY_ATTEMPTS} attempts; once the group answers, steps 2 and 3 follow. The report says
 * {@link Report#cancelBeforeGroup()} either way.</li>
 * </ol>
 * <p>
 * Every signal goes through {@link ProcessGroupSignals}, the lowest signalling primitive. A process that no longer
 * exists when the protocol starts ({@code ESRCH} from the group and from the PID) is not in the window of step 4: it has
 * ended, nothing is signalled further, and the job is settled from its exit status. An interrupt of the waiting thread
 * cuts the wait short and goes straight to {@code SIGKILL}, so no group is left running; the interrupt status is kept.
 * <p>
 * Step 3a — a worker's or a probe's descendant that left the group or started a new session — is not part of this
 * protocol: the runtime records that tree separately ({@link ProcessTreeTracker}) and ends it at the job's end.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Process Termination &
 * Cancellation Protocol, steps 1 to 4); {@code doc/specification/timeouts/01-job-budgets-and-waits.adoc} (Termination
 * grace period). The retry of step 4 is carried by the constants of this class alone.
 *
 * @since 0.1
 */
public final class TerminationProtocol {

    /** The termination grace period between {@code SIGTERM} and {@code SIGKILL}, in seconds (PM-EXEC-4). */
    public static final long GRACE_PERIOD_SECONDS = 5;

    /** The termination grace period between {@code SIGTERM} and {@code SIGKILL} to the job's group (PM-EXEC-4). */
    public static final Duration GRACE_PERIOD = Duration.ofSeconds(GRACE_PERIOD_SECONDS);

    /** The delay between two attempts against the group of a job cancelled before it exists, in ms (PM-EXEC-4). */
    public static final long GROUP_RETRY_INTERVAL_MILLIS = 100;

    /** The delay between two attempts against the group of a job cancelled before it exists (PM-EXEC-4). */
    public static final Duration GROUP_RETRY_INTERVAL = Duration.ofMillis(GROUP_RETRY_INTERVAL_MILLIS);

    /** The most attempts against the group of a job cancelled before it exists (PM-EXEC-4). */
    public static final int GROUP_RETRY_ATTEMPTS = 10;

    /** How often the group is probed during the grace period, so a group that exits early is not waited for. */
    static final Duration LIVENESS_INTERVAL = Duration.ofMillis(100);

    private final ProcessGroupSignals signals;
    private final InstantSource clock;
    private final Pause pause;

    /**
     * Waits in real time.
     *
     * @param signals the signalling primitive
     * @throws NullPointerException if the primitive is {@code null}
     */
    public TerminationProtocol(ProcessGroupSignals signals) {
        this(signals, InstantSource.system(), Thread::sleep);
    }

    /**
     * @param signals the signalling primitive
     * @param clock   the clock the grace period is measured on
     * @param pause   how the protocol waits
     * @throws NullPointerException if an argument is {@code null}
     */
    TerminationProtocol(ProcessGroupSignals signals, InstantSource clock, Pause pause) {
        this.signals = Objects.requireNonNull(signals, "signals");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pause = Objects.requireNonNull(pause, "pause");
    }

    /** @return the signalling primitive, so the process-tree sweep of a job uses the same one */
    ProcessGroupSignals signals() {
        return signals;
    }

    /**
     * How the protocol waits.
     *
     * @since 0.1
     */
    @FunctionalInterface
    interface Pause {

        /**
         * @param duration how long to wait
         * @throws InterruptedException if the waiting thread is interrupted
         */
        void sleep(Duration duration) throws InterruptedException;
    }

    /**
     * How the protocol ended.
     *
     * @since 0.1
     */
    public enum Ending {
        /** The group received {@code SIGTERM} and no member was left at the end of the grace period. */
        GROUP_EXITED,
        /** The group received {@code SIGTERM}, and {@code SIGKILL} after the grace period because a member lived. */
        GROUP_KILLED,
        /**
         * No signal reached the group: the process had already ended, or the group did not exist after the last
         * attempt of step 4.
         */
        GROUP_NOT_REACHED,
        /** A {@code kill} call failed with an {@code errno} other than {@code ESRCH}; the protocol stopped there. */
        SIGNAL_REFUSED
    }

    /**
     * The report of one termination.
     *
     * @param ending            how the protocol ended
     * @param cancelBeforeGroup whether the termination reached the job before its group existed (step 4), recorded as
     *                          {@code cancel_before_group} in the job record
     * @param errno             the {@code errno} of the failed call for {@link Ending#SIGNAL_REFUSED}; empty otherwise
     * @since 0.1
     */
    public record Report(Ending ending, boolean cancelBeforeGroup, OptionalInt errno) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the {@code errno} is present exactly when the ending is not
         *                                  {@link Ending#SIGNAL_REFUSED}
         */
        public Report {
            Objects.requireNonNull(ending, "ending");
            Objects.requireNonNull(errno, "errno");
            if (errno.isPresent() != (ending == Ending.SIGNAL_REFUSED)) {
                throw new IllegalArgumentException("An errno belongs to a refused signal only: " + ending);
            }
        }
    }

    /**
     * Terminates the process group of a job, steps 1 to 4.
     *
     * @param pid the job PID, which is its process-group id
     * @return how the termination ended
     * @throws IllegalArgumentException if the id is no PID of a job
     */
    public Report terminate(long pid) {
        return terminate(pid, true);
    }

    /**
     * Terminates the process group of a job, steps 1 to 4, for a caller that knows whether the job's process still
     * lives.
     *
     * @param pid           the job PID, which is its process-group id
     * @param leaderMayLive whether the job's process may still live; {@code false} skips step 4, the {@code SIGTERM} to
     *                      the bare PID, because a PID whose process ended may have been reused by another process
     * @return how the termination ended
     * @throws IllegalArgumentException if the id is no PID of a job
     */
    public Report terminate(long pid, boolean leaderMayLive) {
        var answer = signals.signalGroup(pid, Signal.SIGTERM);
        if (answer instanceof NoSuchProcess) {
            return leaderMayLive ? cancelBeforeGroup(pid)
                    : new Report(Ending.GROUP_NOT_REACHED, false, OptionalInt.empty());
        }
        if (answer instanceof Refused(var errno)) {
            return refused(errno, false);
        }
        return awaitThenKill(pid, false);
    }

    /** Step 4: {@code SIGTERM} to the PID, then the bounded retries against the group. */
    private Report cancelBeforeGroup(long pid) {
        var direct = signals.signalProcess(pid, Signal.SIGTERM);
        if (direct instanceof NoSuchProcess) {
            return new Report(Ending.GROUP_NOT_REACHED, false, OptionalInt.empty());
        }
        if (direct instanceof Refused(var errno)) {
            return refused(errno, true);
        }
        Answer answer = new NoSuchProcess();
        var interrupted = false;
        for (var attempt = 0; attempt < GROUP_RETRY_ATTEMPTS && answer instanceof NoSuchProcess && !interrupted;
             attempt++) {
            interrupted = !sleep(GROUP_RETRY_INTERVAL);
            answer = signals.signalGroup(pid, Signal.SIGTERM);
        }
        restoreInterrupt(interrupted);
        return switch (answer) {
            case Delivered _ -> awaitThenKill(pid, true);
            case NoSuchProcess _ -> new Report(Ending.GROUP_NOT_REACHED, true, OptionalInt.empty());
            case Refused(var errno) -> refused(errno, true);
        };
    }

    /** Steps 2 and 3: the grace period, then {@code SIGKILL} when any member of the group lives. */
    private Report awaitThenKill(long pid, boolean cancelBeforeGroup) {
        var deadline = clock.instant().plus(GRACE_PERIOD);
        var interrupted = false;
        while (!interrupted && clock.instant().isBefore(deadline)) {
            if (signals.signalGroup(pid, Signal.PROBE) instanceof NoSuchProcess) {
                return new Report(Ending.GROUP_EXITED, cancelBeforeGroup, OptionalInt.empty());
            }
            var remaining = Duration.between(clock.instant(), deadline);
            interrupted = !sleep(remaining.compareTo(LIVENESS_INTERVAL) < 0 ? remaining : LIVENESS_INTERVAL);
        }
        var answer = signals.signalGroup(pid, Signal.SIGKILL);
        restoreInterrupt(interrupted);
        return switch (answer) {
            case Delivered _ -> new Report(Ending.GROUP_KILLED, cancelBeforeGroup, OptionalInt.empty());
            case NoSuchProcess _ -> new Report(Ending.GROUP_EXITED, cancelBeforeGroup, OptionalInt.empty());
            case Refused(var errno) -> refused(errno, cancelBeforeGroup);
        };
    }

    /**
     * @return {@code false} if the waiting thread was interrupted; the caller restores the interrupt with
     *         {@link #restoreInterrupt(boolean)} once the escalation is over
     */
    // java:S2142 does not apply: the interrupt is reported to the caller, not swallowed. The caller stops waiting, still
    // sends the signal the step owes, and only then restores the interrupt (restoreInterrupt), so none is lost.
    @SuppressWarnings("java:S2142")
    private boolean sleep(Duration duration) {
        if (duration.isNegative() || duration.isZero()) {
            return true;
        }
        try {
            pause.sleep(duration);
            return true;
        } catch (InterruptedException _) {
            return false;
        }
    }

    private static void restoreInterrupt(boolean interrupted) {
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static Report refused(int errno, boolean cancelBeforeGroup) {
        return new Report(Ending.SIGNAL_REFUSED, cancelBeforeGroup, OptionalInt.of(errno));
    }
}
