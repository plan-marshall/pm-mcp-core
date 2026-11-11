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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * The last verification result of one provider, kept with the machine's provider defaults (Store Schemas § Machine
 * Provider Defaults, Credentials &amp; Enrolment § Provider Verification): whether every check passed, when it ran, and
 * the name and outcome of each check.
 * <p>
 * The result carries the check names and their {@code ok} flags only, never the provider's answers, so nothing a
 * provider said is ever stored. A default recorded available carries a passed result; one recorded not available on the
 * operator's word carries a failed result or none.
 *
 * @param passed whether every check of the verification passed
 * @param at     the instant of the verification
 * @param checks the checks, one per line of the verification, in order
 * @since 0.1
 */
public record VerificationResult(boolean passed, Instant at, List<Check> checks) {

    /** Rejects a {@code null} instant, keeps the checks unmodifiable, and ties {@code passed} to the flags. */
    public VerificationResult {
        Objects.requireNonNull(at, "at");
        checks = List.copyOf(checks);
        if (passed != (!checks.isEmpty() && checks.stream().allMatch(Check::ok))) {
            throw new IllegalArgumentException(
                    "'passed' must equal: at least one check, and every one of them passed");
        }
    }

    /**
     * One check of the verification.
     *
     * @param check the name of the check, as the verification line names it
     * @param ok    whether it passed
     * @since 0.1
     */
    public record Check(String check, boolean ok) {

        /** Rejects a {@code null} check name. */
        public Check {
            Objects.requireNonNull(check, "check");
        }
    }
}
