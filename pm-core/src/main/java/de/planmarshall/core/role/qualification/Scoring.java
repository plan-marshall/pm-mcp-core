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

import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The deterministic scorer of a corpus item (Model Work § Grading; Workflow DSL § Role Configurations against the
 * Verification Corpus): an item with a closed answer is scored by comparison with its reference and the accepted
 * alternatives, and an item with a rubric answer is scored by a task of the judge role against the item's rubric, where
 * the item's pass threshold — a compiled {@code 0.5} where it declares none — decides whether the answer is right.
 *
 * @since 0.1
 */
public final class Scoring {

    /** The compiled pass threshold of a rubric item that declares none. */
    public static final BigDecimal DEFAULT_PASS_THRESHOLD = new BigDecimal("0.5");

    private Scoring() {
    }

    /**
     * @param item an item with a closed answer
     * @param answer the submitted answer
     * @return the grade, wholly right or wholly wrong
     * @throws IllegalArgumentException if the item is not scored by a closed method
     */
    public static Grade closed(ImportedItem item, String answer) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(answer, "answer");
        if (!item.grading().method().isClosed()) {
            throw new IllegalArgumentException("Not a closed item: " + item.id());
        }
        return Grade.of(right(item, answer));
    }

    /**
     * @param item   an item
     * @param answer the submitted answer
     * @return {@code true} if a closed answer is the reference or an accepted alternative, or a control reports nothing
     */
    static boolean right(ImportedItem item, String answer) {
        if (item.target().expectedEmpty()) {
            return answer.isBlank();
        }
        var given = answer.strip();
        if (item.target().answer() != null && given.equals(item.target().answer().strip())) {
            return true;
        }
        return item.target().alternatives().stream().anyMatch(alternative -> given.equals(alternative.strip()));
    }

    /**
     * @param item  an item with a rubric answer
     * @param score the judge's score
     * @return {@code true} if the score reaches the item's pass threshold
     */
    public static boolean right(ImportedItem item, BigDecimal score) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(score, "score");
        return score.compareTo(threshold(item)) >= 0;
    }

    /**
     * @param item an item
     * @return the share of the score that passes
     */
    public static BigDecimal threshold(ImportedItem item) {
        return item.grading().passThreshold() == null ? DEFAULT_PASS_THRESHOLD : item.grading().passThreshold();
    }

    /**
     * @param item an item
     * @return {@code true} if the item is scored by a task of the judge role
     */
    public static boolean judged(ImportedItem item) {
        return !item.grading().method().isClosed();
    }
}
