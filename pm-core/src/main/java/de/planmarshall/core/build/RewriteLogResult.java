/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * The result of the OpenRewrite log parse: a fail-closed verdict, the findings of an observed run and the reason of a
 * non-observed one.
 * <p>
 * The verdict and the findings cannot disagree: findings exist only under {@link RewriteVerdict#OBSERVED}, and a reason
 * exists only outside it.
 *
 * @param verdict  the verdict of the parse, never null
 * @param findings the recipe findings in log order, empty unless the verdict is {@code OBSERVED}
 * @param reason   why the run observed nothing, {@code null} for an observed run
 * @since 0.1
 */
public record RewriteLogResult(RewriteVerdict verdict, List<RewriteFinding> findings, @Nullable String reason) {

    /**
     * Validates the result and freezes the findings.
     *
     * @throws IllegalArgumentException if findings and verdict disagree, or a reason is missing or surplus
     * @throws NullPointerException     if the verdict or an element of the findings is null
     */
    public RewriteLogResult {
        Objects.requireNonNull(verdict, "verdict");
        findings = List.copyOf(findings);
        boolean observed = verdict == RewriteVerdict.OBSERVED;
        if (!observed && !findings.isEmpty()) {
            throw new IllegalArgumentException("only an observed run carries findings, not " + verdict);
        }
        if (!observed && (reason == null || reason.isBlank())) {
            throw new IllegalArgumentException("a non-observed run must state its reason, not " + verdict);
        }
        if (observed && reason != null) {
            throw new IllegalArgumentException("an observed run carries no reason");
        }
    }

    /**
     * Counts the findings the recipe newly detected in this run.
     *
     * @return the number of newly detected findings
     */
    public int newlyDetectedCount() {
        return count(RewriteClassification.NEWLY_DETECTED);
    }

    /**
     * Counts the findings that were already present before this run.
     *
     * @return the number of pre-existing findings
     */
    public int preExistingCount() {
        return count(RewriteClassification.PRE_EXISTING);
    }

    private int count(RewriteClassification classification) {
        return (int) findings.stream().filter(finding -> finding.classification() == classification).count();
    }
}
