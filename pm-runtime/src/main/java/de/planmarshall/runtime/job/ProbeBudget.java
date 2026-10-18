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

import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.JobRecord.BudgetDerivation;

/**
 * The fixed budgets of the probe jobs (PM-EXEC-4), one per kind of probe under the budget key {@code probe:<kind>}. A
 * probe runs before its program is enrolled and has no project store, so its budget is never learned and never
 * escalated: it is a compiled constant, recorded with the derivation {@code floor_default}. A probe over its budget
 * ends {@code timed_out} and is not started again by the runner.
 * <p>
 * Specification: {@code doc/specification/timeouts/01-job-budgets-and-waits.adoc} (Job Budgets, the probe budget and
 * the budget key {@code probe:<probe>}).
 *
 * @since 0.1
 */
@UtilityClass
public class ProbeBudget {

    /** The budget of a version probe, {@code probe:version}, in seconds (PM-EXEC-4). */
    public static final long VERSION_SECONDS = 15;

    /** The budget of a models probe, {@code probe:models}, in seconds (PM-EXEC-4). */
    public static final long MODELS_SECONDS = 30;

    /** The budget of a login probe, {@code probe:login}, in seconds (PM-EXEC-4). */
    public static final long LOGIN_SECONDS = 120;

    /** The budget of a script probe, {@code probe:script}, in seconds (PM-EXEC-4). */
    public static final long SCRIPT_SECONDS = 600;

    private static final BudgetDerivation FIXED = new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null);

    /**
     * @param kind the kind of a probe
     * @return the fixed budget of a probe of that kind
     */
    public static JobBudget of(ProbeCandidate.Kind kind) {
        Objects.requireNonNull(kind, "kind");
        var seconds = switch (kind) {
            case VERSION -> VERSION_SECONDS;
            case MODELS -> MODELS_SECONDS;
            case LOGIN -> LOGIN_SECONDS;
            case SCRIPT -> SCRIPT_SECONDS;
        };
        return new JobBudget(kind.budgetKey(), seconds, FIXED);
    }
}
