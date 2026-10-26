/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * The grade an item received (Store Schemas § Role Qualification Record, {@code score}): a number from {@code 0} to
 * {@code 1}, or the explicit mark {@code ungraded} when the task of the judge role closed {@code lost}.
 *
 * @param score the score; {@code null} when ungraded
 * @since 0.1
 */
public record Grade(BigDecimal score) {

    /** The score at which a judge counts an answer as judged right (Model Work § Judge Instrument Check). */
    public static final BigDecimal RIGHT_FROM = new BigDecimal("0.5");

    /** The score of an answer that is entirely right. */
    public static final BigDecimal RIGHT = BigDecimal.ONE;

    /** The score of an answer that is entirely wrong. */
    public static final BigDecimal WRONG = BigDecimal.ZERO;

    /** The one word a record writes for an ungraded score. */
    public static final String UNGRADED_MARK = "ungraded";

    /**
     * @throws IllegalArgumentException if the score lies outside {@code 0} to {@code 1}
     */
    public Grade {
        if (score != null && (score.signum() < 0 || score.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("A score lies between 0 and 1: " + score);
        }
    }

    /**
     * @param score the score
     * @return the grade
     */
    public static Grade scored(BigDecimal score) {
        return new Grade(Objects.requireNonNull(score, "score"));
    }

    /**
     * @param right whether the answer is right
     * @return a wholly right or wholly wrong grade
     */
    public static Grade of(boolean right) {
        return new Grade(right ? RIGHT : WRONG);
    }

    /** @return the ungraded mark */
    public static Grade ungraded() {
        return new Grade(null);
    }

    /** @return {@code true} if the item carries no score */
    public boolean isUngraded() {
        return score == null;
    }

    /** @return {@code true} if a judge would count the answer as judged right */
    public boolean judgedRight() {
        return score != null && score.compareTo(RIGHT_FROM) >= 0;
    }
}
