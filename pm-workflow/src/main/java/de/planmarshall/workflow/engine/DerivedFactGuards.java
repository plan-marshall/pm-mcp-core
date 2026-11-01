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

import java.util.Objects;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.DerivedFact;
import de.planmarshall.core.store.DerivedFactReading;
import de.planmarshall.workflow.ast.GuardResult;

/**
 * The bridge from a derived fact to the three-valued guard result (PM-IMPL-5; Store Schemas § Derived Facts Store,
 * bullet <em>Failure</em>; Timeouts &amp; Budgets § Derived Facts): a current fact hands its value to the guard, and a
 * stale, failed or expired fact — and an absent one — is {@link GuardResult.CouldNotEvaluate}, never a pass.
 * <p>
 * A gate that depends on a fact reads it through {@link de.planmarshall.core.store.DerivedFactStore#reading(
 * de.planmarshall.core.store.DerivedFactKey, java.time.Instant)}, and this class decides what the guard may see. The
 * mapping lives in {@code pm-workflow} because {@code pm-workflow} depends on {@code pm-core} and never the other way
 * round; {@link GuardEvaluator} is not edited.
 *
 * @since 0.1
 */
@UtilityClass
public class DerivedFactGuards {

    /**
     * Evaluates a guard over a reading of a derived fact.
     *
     * @param reading the reading of the fact
     * @param guard   the guard to evaluate when the fact has a current value
     * @return the guard's result for a current fact, otherwise {@link GuardResult.CouldNotEvaluate} with the reading's
     *         reason
     * @throws NullPointerException if an argument is {@code null}, or the guard answers {@code null}
     */
    public static GuardResult evaluate(DerivedFactReading reading, Function<DerivedFact, GuardResult> guard) {
        Objects.requireNonNull(guard, "guard");
        if (Objects.requireNonNull(reading, "reading") instanceof DerivedFactReading.Value(var fact)) {
            return Objects.requireNonNull(guard.apply(fact), "The guard result");
        }
        return new GuardResult.CouldNotEvaluate(
                reading.couldNotEvaluate().orElseThrow(() -> new IllegalStateException(
                        "Only a current fact has no could-not-evaluate reason")));
    }
}
