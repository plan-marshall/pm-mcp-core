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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.api.MachinePaths;
import de.planmarshall.core.log.PmMcpLogMessages.INFO;
import de.planmarshall.core.log.PmMcpLogMessages.WARN;

/**
 * The drain of the runtime, which the assembly calls on {@code SIGTERM}/{@code SIGINT} and on the stop endpoint
 * (doc/specification/runtime-model/02-startup-recovery-drain.adoc, Drain, Stop &amp; Upgrade; PM-ARCH-7). In order:
 * <ol>
 * <li>{@link DrainParticipant#stopAdmitting()} and {@link DrainParticipant#endIdle()} on every participant;</li>
 * <li>{@link DrainParticipant#awaitQuiet(java.time.Instant)} up to the drain timeout ({@link DrainTimeout}); a
 * timeout of {@code 0} does not wait;</li>
 * <li>on expiry, {@link DrainParticipant#terminateRemaining()} on every participant that did not get quiet;</li>
 * <li>{@link DrainParticipant#close()} on every participant in the given order, the web listener last;</li>
 * <li>removal of {@code state/runtime.json}, {@code run/runtime.token} and {@code run/runtime.sock};</li>
 * <li>release of the held singleton lock.</li>
 * </ol>
 * A failing participant or file operation is logged and recorded in the {@link Outcome}; the drain still completes
 * the file removal and the lock release, and the runtime exits with code {@value #EXIT_DRAINED}. The drain never
 * touches a lease store: merge-queue and review-bot leases are left in place for the next start.
 * <p>
 * The drain runs once. The first call runs it; every further call, also a concurrent one, waits for that run and
 * returns its outcome. The draining flag is set atomically before any participant is called (the admission contract
 * of {@link DrainParticipant}).
 *
 * @since 0.1
 */
public final class RuntimeDrain {

    private static final CuiLogger LOGGER = new CuiLogger(RuntimeDrain.class);

    /** The exit code of the runtime after a drain. */
    public static final int EXIT_DRAINED = 0;

    private final MachinePaths paths;
    private final RuntimeLock lock;
    private final List<DrainParticipant> participants;
    private final Duration timeout;
    private final Clock clock;
    private final AtomicReference<CompletableFuture<Outcome>> run = new AtomicReference<>();

    /**
     * What the drain did.
     *
     * @param exitCode   the exit code of the runtime, {@value #EXIT_DRAINED}
     * @param ended      the participants that got quiet within the drain timeout
     * @param terminated what participants terminated and recorded {@code killed} after the drain timeout
     * @param failures   the failures of participants and file operations, each naming what failed
     */
    public record Outcome(int exitCode, List<String> ended, List<String> terminated, List<String> failures) {

        /**
         * Copies the lists.
         */
        public Outcome {
            ended = List.copyOf(ended);
            terminated = List.copyOf(terminated);
            failures = List.copyOf(failures);
        }
    }

    /**
     * @param paths        the machine paths
     * @param lock         the singleton lock the runtime holds
     * @param participants the participants, in the order they are closed; the web listener last
     * @param timeout      the drain timeout ({@link DrainTimeout#read(Path)}), not negative
     * @param clock        the clock of the drain timeout
     * @throws IllegalArgumentException if the timeout is negative
     */
    public RuntimeDrain(MachinePaths paths, RuntimeLock lock, List<DrainParticipant> participants, Duration timeout,
            Clock clock) {
        this.paths = Objects.requireNonNull(paths, "paths");
        this.lock = Objects.requireNonNull(lock, "lock");
        this.participants = List.copyOf(participants);
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (timeout.isNegative()) {
            throw new IllegalArgumentException("negative drain timeout " + timeout);
        }
    }

    /**
     * @return {@code true} from the start of the drain on; serves {@code facts.runtime: draining}
     */
    public boolean isDraining() {
        return run.get() != null;
    }

    /**
     * Drains the runtime, or waits for the drain another call started.
     *
     * @return the outcome of the drain
     */
    public Outcome drain() {
        var mine = new CompletableFuture<Outcome>();
        if (!run.compareAndSet(null, mine)) {
            return run.get().join();
        }
        try {
            mine.complete(runOnce());
        } finally {
            // A run that ended without an outcome must not leave later callers waiting forever
            mine.cancel(false);
        }
        return mine.join();
    }

    private Outcome runOnce() {
        LOGGER.info(INFO.RUNTIME_DRAIN_STARTED, timeout.toSeconds());
        var failures = new ArrayList<String>();
        for (var participant : participants) {
            call(participant, "stopAdmitting", participant::stopAdmitting, failures);
            call(participant, "endIdle", participant::endIdle, failures);
        }
        var remaining = awaitQuiet(failures);
        var ended = participants.stream().filter(p -> !remaining.contains(p)).map(DrainParticipant::name).toList();
        var terminated = new ArrayList<String>();
        if (!remaining.isEmpty()) {
            LOGGER.warn(WARN.DRAIN_TIMEOUT_EXPIRED, timeout.toSeconds());
            for (var participant : remaining) {
                call(participant, "terminateRemaining", () -> terminated.addAll(participant.terminateRemaining()),
                        failures);
            }
        }
        for (var participant : participants) {
            call(participant, "close", participant::close, failures);
        }
        for (var file : List.of(paths.runtimeRecord(), paths.runtimeToken(), paths.socket())) {
            remove(file, failures);
        }
        try {
            lock.close();
        } catch (IOException e) {
            failures.add("release of the singleton lock: " + e.getMessage());
        }
        LOGGER.info(INFO.RUNTIME_DRAINED, ended, terminated, EXIT_DRAINED);
        return new Outcome(EXIT_DRAINED, ended, terminated, failures);
    }

    /** @return the participants that did not get quiet within the drain timeout */
    private List<DrainParticipant> awaitQuiet(List<String> failures) {
        if (timeout.isZero()) {
            return participants;
        }
        var deadline = clock.instant().plus(timeout);
        var remaining = new ArrayList<DrainParticipant>();
        for (var participant : participants) {
            try {
                if (!participant.awaitQuiet(deadline)) {
                    remaining.add(participant);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                remaining.add(participant);
                failure(participant, "awaitQuiet", e, failures);
            } catch (IOException e) {
                remaining.add(participant);
                failure(participant, "awaitQuiet", e, failures);
            }
        }
        return remaining;
    }

    private static void call(DrainParticipant participant, String phase, Phase action, List<String> failures) {
        try {
            action.run();
        } catch (IOException e) {
            failure(participant, phase, e, failures);
        }
    }

    private static void failure(DrainParticipant participant, String phase, Exception e, List<String> failures) {
        LOGGER.warn(e, WARN.DRAIN_PARTICIPANT_FAILED, participant.name(), phase, e.getMessage());
        failures.add("%s %s: %s".formatted(participant.name(), phase, e.getMessage()));
    }

    private static void remove(Path file, List<String> failures) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            failures.add("removal of %s: %s".formatted(file, e.getMessage()));
        }
    }

    /** One drain phase of a participant. */
    @FunctionalInterface
    private interface Phase {

        void run() throws IOException;
    }
}
