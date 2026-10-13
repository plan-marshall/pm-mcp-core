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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.api.MachinePaths;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

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
 * the file removal and the lock release, and the runtime exits with code {@value #EXIT_DRAINED}. This holds for an
 * unchecked failure of a participant too: whatever a participant throws in one of its phases is logged and recorded
 * like a checked failure, and the drain continues with the next phase. Should the drain itself end exceptionally, the
 * file removal and the lock release still run, and every caller of {@link #drain()}, a concurrent one included, gets
 * the cause as a {@link java.util.concurrent.CompletionException}, never a bare cancellation. The drain never touches a
 * lease store: merge-queue and review-bot leases are left in place for the next start.
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
     * @throws java.util.concurrent.CompletionException if the drain ended exceptionally; the runtime files are
     *                                                  removed and the lock is released all the same, and every caller
     *                                                  gets the same cause
     */
    public Outcome drain() {
        var mine = new CompletableFuture<Outcome>();
        if (!run.compareAndSet(null, mine)) {
            return run.get().join();
        }
        // The run is taken on the calling thread; an exceptional end completes the shared future with its cause, so
        // a concurrent caller is told the cause instead of waiting forever or seeing a bare cancellation
        CompletableFuture.supplyAsync(this::runOnce, Runnable::run).whenComplete((outcome, failure) -> {
            if (failure == null) {
                mine.complete(outcome);
            } else {
                mine.completeExceptionally(cause(failure));
            }
        });
        return mine.join();
    }

    private Outcome runOnce() {
        LOGGER.info(INFO.RUNTIME_DRAIN_STARTED, timeout.toSeconds());
        var failures = new ArrayList<String>();
        var ended = new ArrayList<String>();
        var terminated = new ArrayList<String>();
        try {
            drainParticipants(failures, ended, terminated);
        } finally {
            // Whatever happened to the participants, the runtime files go and the singleton lock is released
            for (var file : List.of(paths.runtimeRecord(), paths.runtimeToken(), paths.socket())) {
                remove(file, failures);
            }
            try {
                lock.close();
            } catch (IOException e) {
                failures.add("release of the singleton lock: " + e.getMessage());
            }
        }
        LOGGER.info(INFO.RUNTIME_DRAINED, ended, terminated, EXIT_DRAINED);
        return new Outcome(EXIT_DRAINED, ended, terminated, failures);
    }

    private void drainParticipants(List<String> failures, List<String> ended, List<String> terminated) {
        for (var participant : participants) {
            call(participant, "stopAdmitting", () -> {
                participant.stopAdmitting();
                return Boolean.TRUE;
            }, failures);
            call(participant, "endIdle", () -> {
                participant.endIdle();
                return Boolean.TRUE;
            }, failures);
        }
        var remaining = awaitQuiet(failures);
        participants.stream().filter(p -> !remaining.contains(p)).map(DrainParticipant::name).forEach(ended::add);
        if (!remaining.isEmpty()) {
            LOGGER.warn(WARN.DRAIN_TIMEOUT_EXPIRED, timeout.toSeconds());
            for (var participant : remaining) {
                var killed = call(participant, "terminateRemaining", participant::terminateRemaining, failures);
                if (killed.value() != null) {
                    terminated.addAll(killed.value());
                }
            }
        }
        for (var participant : participants) {
            call(participant, "close", () -> {
                participant.close();
                return Boolean.TRUE;
            }, failures);
        }
    }

    /** @return the participants that did not get quiet within the drain timeout */
    private List<DrainParticipant> awaitQuiet(List<String> failures) {
        if (timeout.isZero()) {
            return participants;
        }
        var now = clock.instant();
        // A deadline beyond the last representable instant is that instant: the drain never fails for its timeout
        var deadline = timeout.compareTo(Duration.between(now, Instant.MAX)) > 0 ? Instant.MAX : now.plus(timeout);
        var remaining = new ArrayList<DrainParticipant>();
        for (var participant : participants) {
            var quiet = call(participant, "awaitQuiet", () -> participant.awaitQuiet(deadline), failures);
            if (!Boolean.TRUE.equals(quiet.value())) {
                remaining.add(participant);
            }
        }
        return remaining;
    }

    /**
     * Runs one phase of a participant and records its failure, checked or unchecked. The phase runs on the calling
     * thread; an unchecked failure is received by the future's handler, so it is contained without a catch clause.
     *
     * @return the result of the phase; its value is {@code null} when the phase failed
     */
    private static <T> Attempt<T> call(DrainParticipant participant, String phase, Phase<T> action,
            List<String> failures) {
        var attempt = CompletableFuture.supplyAsync(() -> attempt(action), Runnable::run)
                .exceptionally(thrown -> new Attempt<>(null, cause(thrown)))
                .join();
        if (attempt.failure() != null) {
            failure(participant, phase, attempt.failure(), failures);
        }
        return attempt;
    }

    private static <T> Attempt<T> attempt(Phase<T> action) {
        try {
            return new Attempt<>(action.run(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Attempt<>(null, e);
        } catch (IOException e) {
            return new Attempt<>(null, e);
        }
    }

    private static Throwable cause(Throwable thrown) {
        return thrown instanceof CompletionException && thrown.getCause() != null ? thrown.getCause() : thrown;
    }

    private static void failure(DrainParticipant participant, String phase, Throwable thrown, List<String> failures) {
        var message = thrown.getMessage() != null ? thrown.getMessage() : thrown.toString();
        LOGGER.warn(thrown, WARN.DRAIN_PARTICIPANT_FAILED, participant.name(), phase, message);
        failures.add("%s %s: %s".formatted(participant.name(), phase, message));
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
    private interface Phase<T> {

        T run() throws IOException, InterruptedException;
    }

    /**
     * The result of one phase: its value, or the failure that ended it.
     *
     * @param value   the value of the phase, {@code null} when it failed
     * @param failure what the phase threw, {@code null} when it succeeded
     */
    private record Attempt<T>(T value, Throwable failure) {
    }
}
