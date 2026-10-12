/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

/**
 * How {@link TransitionFuzzer} walks a unit (PM-TEST-3): the seed of its generator, the number of runs and the
 * greatest number of steps of one run. The same configuration over the same unit gives the same report.
 *
 * @param seed     the seed of the random generator
 * @param runs     the number of runs, each with an assignment of its own; positive
 * @param maxSteps the greatest number of transitions one run takes; positive
 * @since 0.1
 */
public record FuzzConfiguration(long seed, int runs, int maxSteps) {

    /**
     * Refuses a count that is not positive.
     *
     * @param seed     the seed of the random generator
     * @param runs     the number of runs
     * @param maxSteps the greatest number of transitions one run takes
     * @throws IllegalArgumentException if {@code runs} or {@code maxSteps} is not positive
     */
    public FuzzConfiguration {
        if (runs <= 0) {
            throw new IllegalArgumentException("the number of runs must be positive, but is %d".formatted(runs));
        }
        if (maxSteps <= 0) {
            throw new IllegalArgumentException(
                    "the greatest number of steps must be positive, but is %d".formatted(maxSteps));
        }
    }
}
