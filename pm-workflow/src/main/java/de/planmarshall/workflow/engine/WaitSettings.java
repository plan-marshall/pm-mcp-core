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
import java.util.Objects;

/**
 * The two machine-wide values a wait is built from (PM-WF-8; Timeouts § Waits): the progress wait upper bound
 * {@code progress_wait_max_seconds} and the progress notification interval.
 *
 * @param progressWaitMax the progress wait upper bound, 60 to 7200 seconds
 * @param progressInterval the interval between two progress frames, 1 to 60 seconds
 * @since 0.1
 */
public record WaitSettings(Duration progressWaitMax, Duration progressInterval) {

    /** The shipped progress wait upper bound, {@code progress_wait_max_seconds}. */
    public static final Duration DEFAULT_PROGRESS_WAIT_MAX = Duration.ofSeconds(1800);

    /** The shortest progress wait upper bound the machine configuration admits. */
    public static final Duration MIN_PROGRESS_WAIT_MAX = Duration.ofSeconds(60);

    /** The longest progress wait upper bound the machine configuration admits. */
    public static final Duration MAX_PROGRESS_WAIT_MAX = Duration.ofSeconds(7200);

    /** The fixed interval between two progress frames, {@code 5}. */
    public static final Duration DEFAULT_PROGRESS_INTERVAL = Duration.ofSeconds(5);

    /** The longest gap a progress wait ever leaves between two frames, {@code 60}. */
    public static final Duration MAX_PROGRESS_INTERVAL = Duration.ofSeconds(60);

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the upper bound is outside 60 to 7200 seconds, or the interval is outside 1
     *                                  to 60 seconds or longer than the upper bound
     */
    public WaitSettings {
        Objects.requireNonNull(progressWaitMax, "progressWaitMax");
        Objects.requireNonNull(progressInterval, "progressInterval");
        if (progressWaitMax.compareTo(MIN_PROGRESS_WAIT_MAX) < 0 || progressWaitMax.compareTo(MAX_PROGRESS_WAIT_MAX) > 0) {
            throw new IllegalArgumentException("the progress wait upper bound %s is outside %s to %s"
                    .formatted(progressWaitMax, MIN_PROGRESS_WAIT_MAX, MAX_PROGRESS_WAIT_MAX));
        }
        if (progressInterval.isNegative() || progressInterval.isZero()
                || progressInterval.compareTo(MAX_PROGRESS_INTERVAL) > 0) {
            throw new IllegalArgumentException("the progress interval %s is outside 1 to %s"
                    .formatted(progressInterval, MAX_PROGRESS_INTERVAL));
        }
        if (progressInterval.compareTo(progressWaitMax) > 0) {
            throw new IllegalArgumentException("the progress interval %s is longer than the upper bound %s"
                    .formatted(progressInterval, progressWaitMax));
        }
    }

    /** @return the shipped values: an upper bound of 1800 seconds and an interval of 5 seconds */
    public static WaitSettings defaults() {
        return new WaitSettings(DEFAULT_PROGRESS_WAIT_MAX, DEFAULT_PROGRESS_INTERVAL);
    }
}
