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
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The machine-wide start spacing of one harness (PM-WORK-3, Starts of One Harness): concurrent starts of the same
 * harness can collide in what the harness shares besides its per-worker data home — its caches, its login refresh — so
 * the workers of one harness start one after another on the machine.
 * <p>
 * The next start waits until the previous worker of the harness made its first call or ended, at most {@link
 * #SPACING_BOUND}. A collision that still makes a worker exit before its first call is a start failure and counts for
 * the {@link StartFailureGuard}, which is why the spacing only ever delays a start and never refuses one: past the
 * bound the next start proceeds.
 * <p>
 * The instance is shared by every supervisor of the runtime, so the spacing spans the supervisors of all projects: the
 * class keys on the client id of the harness alone and holds no per-project state. A caller announces the start's
 * outcome through {@link #firstCall} or {@link #ended}; both release the next waiter at once.
 *
 * @since 0.1
 */
public final class HarnessStartSpacing {

    /** The compiled cap on the wait for the previous worker of the harness. */
    public static final Duration SPACING_BOUND = Duration.ofSeconds(30);

    private final Duration bound;

    private final Object monitor = new Object();

    /** The harnesses whose worker was started and has not made its first call or ended yet. */
    private final Set<String> pending = new HashSet<>();

    /** The spacing of the production runtime: the compiled {@link #SPACING_BOUND}. */
    public HarnessStartSpacing() {
        this(SPACING_BOUND);
    }

    /**
     * @param bound the cap on the wait for the previous worker, which a test lowers to run its scenario without a
     *              sleep of the compiled value
     * @throws NullPointerException     if the bound is {@code null}
     * @throws IllegalArgumentException if the bound is not positive
     */
    public HarnessStartSpacing(Duration bound) {
        this.bound = Objects.requireNonNull(bound, "bound");
        if (bound.isZero() || bound.isNegative()) {
            throw new IllegalArgumentException("'bound' must be positive: " + bound);
        }
    }

    /**
     * Waits until the previous worker of the harness made its first call or ended, at most the bound, and then records
     * this caller's start as the harness's pending one. A start with no pending worker proceeds at once.
     *
     * @param harness the client id of the harness the worker starts on
     * @throws InterruptedException if the wait is interrupted
     * @throws NullPointerException if the harness is {@code null}
     */
    public void awaitTurn(String harness) throws InterruptedException {
        Objects.requireNonNull(harness, "harness");
        synchronized (monitor) {
            var deadline = System.nanoTime() + bound.toNanos();
            while (pending.contains(harness)) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
            }
            pending.add(harness);
        }
    }

    /**
     * Records that the pending worker of the harness made its first call, which releases the next start at once.
     *
     * @param harness the client id of the harness
     * @throws NullPointerException if the harness is {@code null}
     */
    public void firstCall(String harness) {
        release(harness);
    }

    /**
     * Records that the pending start of the harness is over — its worker ended, or the runtime refused the launch
     * before its spawn, so no worker of it is left to make a first call. The next start is released at once.
     *
     * @param harness the client id of the harness
     * @throws NullPointerException if the harness is {@code null}
     */
    public void ended(String harness) {
        release(harness);
    }

    private void release(String harness) {
        Objects.requireNonNull(harness, "harness");
        synchronized (monitor) {
            if (pending.remove(harness)) {
                monitor.notifyAll();
            }
        }
    }
}
