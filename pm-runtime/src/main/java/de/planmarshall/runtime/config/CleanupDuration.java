/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.config;

import org.jspecify.annotations.Nullable;

/**
 * One duration of the machine's cleanup, a value of {@code cleanup.*} of {@code machine-config.json} (Timeouts &amp;
 * Budgets § One Cleanup, Store Schemas § Machine Configuration Store): whole days, at least {@value #MIN_DAYS} so that
 * no window of the system view loses data, or {@code never}.
 *
 * @param days the days an item is kept after its end; {@code null} for {@code never}
 * @since 0.1
 */
public record CleanupDuration(@Nullable Integer days) {

    /** The shortest duration the schema admits, in days. */
    public static final int MIN_DAYS = 7;

    /** The schema default of a duration, in days. */
    public static final int DEFAULT_DAYS = 30;

    /**
     * @param days the days, {@code null} for {@code never}
     * @throws IllegalArgumentException if the days are below {@value #MIN_DAYS}
     */
    public CleanupDuration {
        if (days != null && days < MIN_DAYS) {
            throw new IllegalArgumentException("A cleanup duration is %d days or more, or never: %d"
                    .formatted(MIN_DAYS, days));
        }
    }

    /**
     * @param days the days an item is kept, at least {@value #MIN_DAYS}
     * @return the duration
     */
    public static CleanupDuration ofDays(int days) {
        return new CleanupDuration(days);
    }

    /**
     * @return the duration that never cleans the item up
     */
    public static CleanupDuration never() {
        return NEVER;
    }

    /** The duration that never cleans up. */
    private static final CleanupDuration NEVER = new CleanupDuration(null);
}