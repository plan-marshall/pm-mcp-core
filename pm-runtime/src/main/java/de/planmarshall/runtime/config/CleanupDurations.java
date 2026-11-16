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

import java.util.Objects;
import lombok.Builder;

/**
 * The durations of the one cleanup, {@code cleanup} of {@code machine-config.json} (Timeouts &amp; Budgets § One
 * Cleanup, Store Schemas § Machine Configuration Store): per kind the days an item is kept after its end, or
 * {@code never}, and after how many days a postponed cleanup asks again. The machine's settings record them and the
 * cleanup reads them; a project's {@code local-config.json} overrides the first three for its items.
 *
 * @param plansAfterDays      the days a finished plan is kept
 * @param epicsAfterDays      the days a finished epic is kept
 * @param logsAfterDays       the days a log is kept
 * @param reportsAfterDays    the days a read report is kept; machine only
 * @param unenrolledAfterDays the days the machine store of an unenrolled project is kept; machine only
 * @param remindAfterDays     after how many days a postponed cleanup asks again, at least {@value #MIN_REMIND_DAYS}
 * @since 0.1
 */
@Builder
public record CleanupDurations(CleanupDuration plansAfterDays, CleanupDuration epicsAfterDays,
CleanupDuration logsAfterDays, CleanupDuration reportsAfterDays, CleanupDuration unenrolledAfterDays,
int remindAfterDays) {

    /** The shortest reminder interval the schema admits, in days. */
    public static final int MIN_REMIND_DAYS = 1;

    /** The schema default of {@code remind_after_days}. */
    public static final int DEFAULT_REMIND_DAYS = 3;

    /**
     * @param plansAfterDays      the days a finished plan is kept
     * @param epicsAfterDays      the days a finished epic is kept
     * @param logsAfterDays       the days a log is kept
     * @param reportsAfterDays    the days a read report is kept
     * @param unenrolledAfterDays the days the machine store of an unenrolled project is kept
     * @param remindAfterDays     after how many days a postponed cleanup asks again
     * @throws NullPointerException     if a duration is {@code null}
     * @throws IllegalArgumentException if the reminder interval is below {@value #MIN_REMIND_DAYS}
     */
    public CleanupDurations {
        Objects.requireNonNull(plansAfterDays, "plansAfterDays");
        Objects.requireNonNull(epicsAfterDays, "epicsAfterDays");
        Objects.requireNonNull(logsAfterDays, "logsAfterDays");
        Objects.requireNonNull(reportsAfterDays, "reportsAfterDays");
        Objects.requireNonNull(unenrolledAfterDays, "unenrolledAfterDays");
        if (remindAfterDays < MIN_REMIND_DAYS) {
            throw new IllegalArgumentException("A reminder interval is %d days or more: %d"
                    .formatted(MIN_REMIND_DAYS, remindAfterDays));
        }
    }

    /**
     * @return the durations the schema applies while the machine records none
     */
    public static CleanupDurations defaults() {
        var days = CleanupDuration.ofDays(CleanupDuration.DEFAULT_DAYS);
        return new CleanupDurations(days, days, days, days, days, DEFAULT_REMIND_DAYS);
    }
}
