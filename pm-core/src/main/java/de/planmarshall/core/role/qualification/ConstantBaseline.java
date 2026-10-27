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
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The constant baseline of a topic (Model Work § Constant Baseline): the agreement of the best constant answerer, the
 * largest share of the topic's graded items whose reference is one option key of its {@code exact_choice} items. An
 * item graded otherwise counts as not agreeing, and an item with a rubric reference is in the denominator but never in a
 * numerator.
 * <p>
 * The baseline is recomputed from the item results of the form, so the repeated runs of the screener count run by run,
 * like the topic's agreement.
 *
 * @since 0.1
 */
public final class ConstantBaseline {

    private ConstantBaseline() {
    }

    /**
     * @param items   the counted items of the topic
     * @param results the graded item results of the topic
     * @return the largest share of the graded items whose reference is one option key, in percent; {@code 0} when the
     *         topic has no {@code exact_choice} item
     */
    public static BigDecimal of(List<ImportedItem> items, List<QualificationRecord.ItemResult> results) {
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(results, "results");
        var byId = new HashMap<String, ImportedItem>();
        for (var item : items) {
            byId.put(item.id(), item);
        }
        var references = new HashMap<String, Integer>();
        var graded = 0;
        for (var result : results) {
            if (result.score().isUngraded()) {
                continue;
            }
            graded++;
            var item = byId.get(result.itemId());
            if (item != null && item.grading().method() == ImportedItem.GradingMethod.EXACT_CHOICE
                    && item.target().answer() != null) {
                references.merge(item.target().answer(), 1, Integer::sum);
            }
        }
        if (graded == 0 || references.isEmpty()) {
            return BigDecimal.ZERO;
        }
        var largest = references.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        var percent = percent(largest, graded);
        return percent.scale() < 0 ? percent.setScale(0) : percent;
    }

    /**
     * @param part  the count
     * @param total the denominator
     * @return the share in percent, rounded half up to two decimals
     */
    public static BigDecimal percent(int part, int total) {
        if (total <= 0) {
            throw new IllegalArgumentException("The denominator must be positive: " + total);
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .add(BigDecimal.ZERO);
    }
}
