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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The virtual clock and timers of the supervisor scenarios (PM-WORK-3, ADR 004): a clock a scenario moves to a deadline,
 * and the timers that fire exactly when the clock reaches them.
 * <p>
 * A scenario asserts the state at deadline−1, at the deadline and after it without a sleep: it moves the clock, and
 * every deadline the supervisor or its loss detector scheduled at or before the new instant fires in order. A timer
 * scheduled while the clock is being moved is picked up by the same move, so a re-armed deadline is never missed.
 *
 * @since 0.1
 */
final class VirtualClock implements InstantSource, WorkerTimers {

    private final List<Entry> entries = new ArrayList<>();

    private Instant now;

    /**
     * @param start the instant the clock starts at
     * @throws NullPointerException if the start is {@code null}
     */
    VirtualClock(Instant start) {
        this.now = Objects.requireNonNull(start, "start");
    }

    @Override
    public Instant instant() {
        return now;
    }

    @Override
    public Cancellable schedule(Instant at, Runnable action) {
        var entry = new Entry(Objects.requireNonNull(at, "at"), Objects.requireNonNull(action, "action"));
        entries.add(entry);
        return () -> entry.cancelled = true;
    }

    /**
     * @param duration the time to move forward
     * @throws NullPointerException if the duration is {@code null}
     */
    void advance(Duration duration) {
        moveTo(now.plus(Objects.requireNonNull(duration, "duration")));
    }

    /**
     * @param at the instant to move to
     * @throws NullPointerException if the instant is {@code null}
     */
    void moveTo(Instant at) {
        now = Objects.requireNonNull(at, "at");
        var fired = true;
        while (fired) {
            fired = false;
            for (var entry : new ArrayList<>(entries)) {
                if (!entry.cancelled && !entry.at.isAfter(now)) {
                    entry.cancelled = true;
                    entry.action.run();
                    fired = true;
                }
            }
        }
    }

    /** One scheduled action. */
    private static final class Entry {

        private final Instant at;

        private final Runnable action;

        private boolean cancelled;

        private Entry(Instant at, Runnable action) {
            this.at = at;
            this.action = action;
        }
    }
}
