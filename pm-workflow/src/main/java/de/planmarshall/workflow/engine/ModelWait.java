/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A wait the server holds open for a model (PM-WF-8, PM-IMPL-4): either the bounded long-poll or the progress-reset
 * wait, selected by {@link WaitSelection} from the client profile and the request's progress token.
 * <p>
 * The wait is a value over the instant it started at and the window it may hold; it neither sleeps nor reads a clock
 * of its own, so a caller with a virtual clock decides when the wait is answered and when a progress frame is due.
 * A bounded wait answers at its window and emits no frame. A progress wait emits a frame at every interval after the
 * start that falls before the answer, at least one, and answers at its window, so no gap of its hold is longer than the
 * interval.
 *
 * @since 0.1
 */
public sealed interface ModelWait permits ModelWait.Bounded, ModelWait.Progress {

    /** @return the call the wait holds open */
    ModelCall call();

    /** @return the instant the wait started at */
    Instant startedAt();

    /** @return how long the wait may hold the call open */
    Duration window();

    /** @return the instant the call is answered at the latest */
    default Instant answerAt() {
        return startedAt().plus(window());
    }

    /**
     * @param instant an instant
     * @return whether the wait answers at that instant: it is reached or passed
     */
    default boolean answersAt(Instant instant) {
        return !Objects.requireNonNull(instant, "instant").isBefore(answerAt());
    }

    /** @return the instants of the progress frames, in order; empty for a bounded wait */
    default List<Instant> progressFrames() {
        return List.of();
    }

    /**
     * The bounded long-poll: it holds the call for its window and emits no progress frame.
     *
     * @param call      the call
     * @param startedAt the instant the wait started at
     * @param window    the bounded wait window
     * @since 0.1
     */
    record Bounded(ModelCall call, Instant startedAt, Duration window) implements ModelWait {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the window is not positive
         */
        public Bounded {
            requireComponents(call, startedAt, window);
        }
    }

    /**
     * The progress-reset wait: it holds the call for its window and emits a progress frame at every interval after the
     * start that falls before the answer, so that no gap the host reads as idle is longer than the interval.
     *
     * @param call      the call
     * @param startedAt the instant the wait started at
     * @param window    the window the wait may hold, the bounded window of a {@code pm_wait} or the progress upper
     *                  bound of a {@code pm_build}
     * @param interval  the interval between two progress frames
     * @since 0.1
     */
    record Progress(ModelCall call, Instant startedAt, Duration window, Duration interval) implements ModelWait {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the window is not positive or the interval is not between one second and
         *                                  below the window
         */
        public Progress {
            requireComponents(call, startedAt, window);
            Objects.requireNonNull(interval, "interval");
            if (interval.compareTo(WaitSettings.MIN_PROGRESS_INTERVAL) < 0 || interval.compareTo(window) >= 0) {
                throw new IllegalArgumentException(
                        "the progress interval %s is not between %s and below the window %s"
                                .formatted(interval, WaitSettings.MIN_PROGRESS_INTERVAL, window));
            }
        }

        @Override
        public List<Instant> progressFrames() {
            var frames = new ArrayList<Instant>();
            for (var frame = startedAt.plus(interval); frame.isBefore(answerAt()); frame = frame.plus(interval)) {
                frames.add(frame);
            }
            return List.copyOf(frames);
        }
    }

    private static void requireComponents(ModelCall call, Instant startedAt, Duration window) {
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(window, "window");
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("the window %s is not positive".formatted(window));
        }
    }
}
