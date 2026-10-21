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
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import de.planmarshall.runtime.job.ProcessGroupSignals.Signal;

/**
 * Records the process tree of a job while it runs and ends the descendants that are still alive when it ends
 * (termination step 3a of the job runtime specification, on the platform that has no child subreaper).
 * <p>
 * A descendant that left the job's process group or started a new session is no longer reached by
 * {@code kill(-pgid, sig)}. It is still the job's descendant to others: {@link ProcessHandle#descendants()} lists it
 * while it hangs below the job, whatever group it moved to. The tracker is refreshed while the job runs and remembers
 * every PID it saw with the process start it saw it with, so a member that detaches between two refreshes is still
 * known at the job's end. The sweep sends {@code SIGTERM} to every remembered member that still runs, waits the sweep
 * grace period, and sends {@code SIGKILL} to those left; a PID whose process started at another instant was reused and
 * is never signalled.
 * <p>
 * On Linux the specification's mechanism is the launcher: {@code pm-exec} marks itself child subreaper and starts the
 * command as its child, so no descendant can leave its reach. That change lives in the launcher, not in this
 * repository; the tracker is the runtime side of the same step and covers what the launcher's reach does not, on every
 * platform.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Process Termination &
 * Cancellation Protocol, step 3a).
 *
 * @since 0.1
 */
public final class ProcessTreeTracker {

    /** How often the tree is refreshed while the job runs: the specification's at least once per second. */
    public static final Duration REFRESH_INTERVAL = Duration.ofSeconds(1);

    /** How long the sweep waits between {@code SIGTERM} and {@code SIGKILL}. */
    public static final Duration SWEEP_GRACE = Duration.ofSeconds(1);

    /** How often the sweep probes its members during the grace period. */
    static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    /** One descendant as it was seen: its PID and the start instant that tells a reused PID from it. */
    public record Member(long pid, Instant startedAt) {

        /** @throws NullPointerException if the start instant is {@code null} */
        public Member {
            Objects.requireNonNull(startedAt, "startedAt");
        }
    }

    /**
     * The descendants of a process.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface Descendants {

        /**
         * @param root the PID of the job's process
         * @return its descendants, at one instant
         */
        Set<Member> of(long root);

        /** @return the descendants the process table answers for a PID */
        static Descendants system() {
            return root -> ProcessHandle.of(root).map(handle -> handle.descendants()
                    .map(member -> member.info().startInstant()
                            .map(startedAt -> new Member(member.pid(), startedAt)))
                    .flatMap(Optional::stream)
                    .collect(Collectors.toUnmodifiableSet())).orElse(Set.of());
        }
    }

    /** @return the start instant the process table answers for a PID; empty for a process that does not exist */
    @FunctionalInterface
    interface Starts {
        Optional<Instant> of(long pid);

        Starts SYSTEM = pid -> ProcessHandle.of(pid).flatMap(handle -> handle.info().startInstant());
    }

    private final long root;
    private final Descendants descendants;
    private final Starts starts;
    private final ProcessGroupSignals signals;
    private final InstantSource clock;
    private final TerminationProtocol.Pause pause;
    private final Map<Long, Instant> members = new ConcurrentHashMap<>();

    /**
     * @param root     the job PID, the root of the tree
     * @param signals  the signalling primitive, the same one the termination uses
     * @throws NullPointerException if an argument is {@code null}
     */
    public ProcessTreeTracker(long root, ProcessGroupSignals signals) {
        this(root, Descendants.system(), Starts.SYSTEM, signals, InstantSource.system(), Thread::sleep);
    }

    /**
     * @param root        the job PID, the root of the tree
     * @param descendants where the tree is read
     * @param starts      where the start instant of a PID is read
     * @param signals     the signalling primitive
     * @param clock       the clock of the grace period
     * @param pause       how the sweep waits
     * @throws NullPointerException if an argument is {@code null}
     */
    ProcessTreeTracker(long root, Descendants descendants, Starts starts, ProcessGroupSignals signals,
            InstantSource clock, TerminationProtocol.Pause pause) {
        this.root = root;
        this.descendants = Objects.requireNonNull(descendants, "descendants");
        this.starts = Objects.requireNonNull(starts, "starts");
        this.signals = Objects.requireNonNull(signals, "signals");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.pause = Objects.requireNonNull(pause, "pause");
    }

    /** Remembers the descendants the job has now; a member seen earlier stays remembered. */
    public void refresh() {
        for (var member : descendants.of(root)) {
            members.put(member.pid(), member.startedAt());
        }
    }

    /** @return the PIDs remembered so far, whether the process still hangs below the job or not */
    public Set<Long> members() {
        return Set.copyOf(members.keySet());
    }

    /**
     * Ends the remembered descendants that are still alive: {@code SIGTERM} to each, the sweep grace period, and
     * {@code SIGKILL} to those left. A remembered PID whose process started at another instant was reused and is not
     * signalled.
     */
    public void sweep() {
        var survivors = survivors();
        if (survivors.isEmpty()) {
            return;
        }
        for (var pid : survivors) {
            signals.signalProcess(pid, Signal.SIGTERM);
        }
        var interrupted = false;
        var deadline = clock.instant().plus(SWEEP_GRACE);
        while (!interrupted && clock.instant().isBefore(deadline) && !survivors().isEmpty()) {
            var remaining = Duration.between(clock.instant(), deadline);
            interrupted = !sleep(remaining.compareTo(POLL_INTERVAL) < 0 ? remaining : POLL_INTERVAL);
        }
        for (var pid : survivors()) {
            signals.signalProcess(pid, Signal.SIGKILL);
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** @return the remembered PID of every member that is still the process it was, and still runs */
    private Set<Long> survivors() {
        return members.entrySet().stream()
                .filter(member -> starts.of(member.getKey()).filter(member.getValue()::equals).isPresent())
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * @return {@code false} if the waiting thread was interrupted; the caller restores the interrupt once the sweep
     *         has sent its {@code SIGKILL}
     */
    // java:S2142 does not apply: the interrupt is reported to the caller, not swallowed; the sweep still sends the
    // signal it owes and restores the interrupt after it.
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
}
