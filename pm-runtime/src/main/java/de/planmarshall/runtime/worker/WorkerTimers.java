/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * The timers the supervisor's deadlines and probe backoff use (PM-WORK-3, ADR 004): a cancellation handle around a
 * one-shot action scheduled for an instant, so every deadline is driven from an injected clock and a test's virtual
 * clock runs the scenarios without a sleep.
 *
 * @since 0.1
 */
public interface WorkerTimers {

    /**
     * A scheduled action that has not run yet.
     *
     * @since 0.1
     */
    @FunctionalInterface
    interface Cancellable {

        /** Cancels the action; a running action is not interrupted. */
        void cancel();
    }

    /**
     * @param at     the instant the action runs
     * @param action the action
     * @return the handle that cancels the action
     */
    Cancellable schedule(Instant at, Runnable action);

    /**
     * The production timers: one daemon scheduler thread for the supervisor, its delays measured from the injected
     * clock so a test that moves the clock moves the deadlines with it.
     *
     * @param clock the clock the delays are measured from
     * @return the timers of one supervisor
     * @throws NullPointerException if the clock is {@code null}
     */
    static WorkerTimers system(InstantSource clock) {
        Objects.requireNonNull(clock, "clock");
        var executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            var thread = new Thread(runnable, "worker-timers");
            thread.setDaemon(true);
            return thread;
        });
        return (at, action) -> {
            Objects.requireNonNull(at, "at");
            Objects.requireNonNull(action, "action");
            var delay = Math.max(0L, Duration.between(clock.instant(), at).toMillis());
            var future = executor.schedule(action, delay, TimeUnit.MILLISECONDS);
            return () -> future.cancel(false);
        };
    }
}
