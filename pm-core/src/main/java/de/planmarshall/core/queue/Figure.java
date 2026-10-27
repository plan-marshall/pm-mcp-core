/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import java.util.Objects;

/**
 * One navigation figure of a task (Model Work § Navigation Figures): its value, the population it counts over and, when
 * the population cannot be read, the sentinel that says so instead of a fabricated zero.
 * <p>
 * A figure is evidence for a comparison, never a limit. A measured figure carries a finite value and a population of at
 * least one; {@code indeterminate} is a population that could not be read, and {@code unmeasured} is a figure the
 * harness reports no data for (the cache-read share of a harness without cache reads). Neither sentinel is ever
 * {@code 0}.
 *
 * @param name       the figure's name, as the completion report shows it
 * @param value      the value; {@code null} when the figure is a sentinel
 * @param population the population the value counts over; {@code 0} for a sentinel
 * @param sentinel   {@code indeterminate}, {@code unmeasured} or {@code null} for a measured figure
 * @since 0.1
 */
public record Figure(String name, Double value, long population, String sentinel) {

    /** The population of the figure cannot be read. */
    public static final String SENTINEL_INDETERMINATE = "indeterminate";

    /** The harness reports no data for the figure. */
    public static final String SENTINEL_UNMEASURED = "unmeasured";

    /**
     * @throws NullPointerException     if the name is {@code null}
     * @throws IllegalArgumentException if the shape is neither measured nor one of the two sentinels
     */
    public Figure {
        Objects.requireNonNull(name, "name");
        if (sentinel == null) {
            if (value == null || !Double.isFinite(value) || value < 0 || population < 1) {
                throw new IllegalArgumentException("A measured figure has a finite value and a population");
            }
        } else if (!SENTINEL_INDETERMINATE.equals(sentinel) && !SENTINEL_UNMEASURED.equals(sentinel)) {
            throw new IllegalArgumentException("Not a figure sentinel: " + sentinel);
        } else if (value != null || population != 0) {
            throw new IllegalArgumentException("A sentinel figure carries neither a value nor a population");
        }
    }

    /**
     * @param name       the figure's name
     * @param value      the measured value
     * @param population the population it counts over
     * @return the measured figure
     */
    public static Figure measured(String name, double value, long population) {
        return new Figure(name, value, population, null);
    }

    /**
     * @param name the figure's name
     * @return the figure whose population cannot be read
     */
    public static Figure indeterminate(String name) {
        return new Figure(name, null, 0, SENTINEL_INDETERMINATE);
    }

    /**
     * @param name the figure's name
     * @return the figure the harness reports no data for
     */
    public static Figure unmeasured(String name) {
        return new Figure(name, null, 0, SENTINEL_UNMEASURED);
    }

    /** @return whether the figure is measured */
    public boolean isMeasured() {
        return sentinel == null;
    }
}
