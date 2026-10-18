/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import java.time.Duration;
import java.util.Objects;

import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;

/**
 * The budget of one job as the runner receives it and records it with the job (PM-EXEC-4): the budget key, the
 * budget in seconds and how the budget was derived ({@code budget_key}, {@code budget_seconds},
 * {@code budget_derivation} of the job record). The runner neither allocates nor learns a budget; it starts the
 * budget at the spawn of the job, so the time a job waits before it is spawned lies outside it.
 * <p>
 * Specification: {@code doc/specification/timeouts/01-job-budgets-and-waits.adoc} (Job Budgets).
 *
 * @param key        the budget key of the job, for example {@code build:verify}
 * @param seconds    the budget in seconds, at least 1
 * @param derivation how the budget was derived
 * @since 0.1
 */
public record JobBudget(String key, long seconds, BudgetDerivation derivation) {

    /**
     * @throws NullPointerException     if the key or the derivation is {@code null}
     * @throws IllegalArgumentException if the key is blank or the budget is less than one second
     */
    public JobBudget {
        Objects.requireNonNull(key, "key");
        if (key.isBlank()) {
            throw new IllegalArgumentException("The budget key must not be blank");
        }
        if (seconds < 1) {
            throw new IllegalArgumentException("A budget is at least one second: " + seconds);
        }
        Objects.requireNonNull(derivation, "derivation");
    }

    /**
     * @param request a start request
     * @return the budget the request names
     */
    public static JobBudget of(JobRunner.StartRequest<?> request) {
        Objects.requireNonNull(request, "request");
        return new JobBudget(request.budgetKey(), request.budgetSeconds(), request.budgetDerivation());
    }

    /** @return the budget as a duration, counted from the spawn of the job */
    public Duration duration() {
        return Duration.ofSeconds(seconds);
    }
}
