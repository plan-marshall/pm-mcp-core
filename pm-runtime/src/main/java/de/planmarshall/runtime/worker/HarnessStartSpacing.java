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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * The machine-wide start spacing of one harness (PM-WORK-3, Starts of One Harness): concurrent starts of the same
 * harness can collide in what the harness shares besides its per-worker data home — its caches, its login refresh — so
 * the workers of one harness start one after another on the machine.
 * <p>
 * The next start waits until the previous worker of the harness made its first call or ended, at most the compiled
 * bound of {@value #SPACING_BOUND}. A collision that still makes a worker exit before its first call is a start failure
 * and counts for the {@link StartFailureGuard}, which is why the spacing only ever delays a start and never refuses one:
 * past the bound the next start proceeds.
 * <p>
 * The instance is shared by every supervisor of the runtime, so the spacing spans the supervisors of all projects: the
 * class keys on the harness id of the harness alone and holds no per-project state. Each turn has an OWNER — the id of
 * the start it was reserved for — and only that owner releases it: {@link #awaitTurn} reserves the turn for an owner,
 * {@link #firstCall} and {@link #ended} release it for that owner and are a no-op for any other. So an unrelated end of
 * the harness — a generation that already made its first call, another project's worker — releases nothing, and once
 * the bound handed the turn to the next start, the former owner's late release leaves it alone. A release is
 * idempotent.
 * <p>
 * The wait happens outside the monitors of the caller that the pending worker's first call needs: the spacing's own
 * monitor is the only one released while the thread waits, so the first call reaches the release without the caller's
 * lock in its way.
 *
 * @since 0.1
 */
public final class HarnessStartSpacing {

    /** The compiled cap on the wait for the previous worker of the harness. */
    public static final Duration SPACING_BOUND = Duration.ofSeconds(30);

    private final Duration bound;

    private final Object monitor = new Object();

    /** The owner of each harness's turn: the harnesses whose worker was started and has not made its first call or ended. */
    private final Map<String, String> pending = new HashMap<>();

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
     * Waits until the previous start of the harness made its first call or ended, at most the bound, and then records
     * the turn as this owner's. A harness with no pending start proceeds at once; a turn this owner already holds is
     * not waited for again. Past the bound the caller proceeds and becomes the turn's owner, so the former owner's
     * later release is a no-op. The wait releases the spacing's monitor, so the pending worker's first call — which
     * needs the callers' own monitors, not this one — releases it.
     *
     * @param harness the harness id of the harness the worker starts on
     * @param owner   the id of the start that takes the turn, its generation id
     * @throws InterruptedException if the wait is interrupted
     * @throws NullPointerException if the harness or the owner is {@code null}
     */
    public void awaitTurn(String harness, String owner) throws InterruptedException {
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(owner, "owner");
        synchronized (monitor) {
            var deadline = System.nanoTime() + bound.toNanos();
            while (heldByAnother(harness, owner)) {
                var remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    break;
                }
                TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
            }
            pending.put(harness, owner);
        }
    }

    /**
     * Records that the pending worker of the harness made its first call, which releases the next start at once. A call
     * of an owner that no longer holds the turn releases nothing.
     *
     * @param harness the harness id of the harness
     * @param owner   the id of the start whose worker called
     * @throws NullPointerException if the harness or the owner is {@code null}
     */
    public void firstCall(String harness, String owner) {
        release(harness, owner);
    }

    /**
     * Records that the start the harness's turn belongs to is over — its worker ended, or the runtime refused the
     * launch after the turn was taken, so no worker of it is left to make a first call. The next start is released at
     * once. An end of any other start releases nothing.
     *
     * @param harness the harness id of the harness
     * @param owner   the id of the start whose turn is over
     * @throws NullPointerException if the harness or the owner is {@code null}
     */
    public void ended(String harness, String owner) {
        release(harness, owner);
    }

    private boolean heldByAnother(String harness, String owner) {
        var current = pending.get(harness);
        return current != null && !current.equals(owner);
    }

    private void release(String harness, String owner) {
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(owner, "owner");
        synchronized (monitor) {
            if (owner.equals(pending.get(harness))) {
                pending.remove(harness);
                monitor.notifyAll();
            }
        }
    }
}
