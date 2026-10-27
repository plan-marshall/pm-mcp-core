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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The constant baseline of a topic (Model Work § Constant Baseline): the agreement of the best constant answerer over
 * the graded items, computed over the {@code exact_choice} references of the topic, so a topic whose references are
 * too uniform cannot qualify a configuration.
 */
@DisplayName("Constant baseline")
class ConstantBaselineTest {

    @Test
    @DisplayName("eight of ten references the same key are a baseline of 80")
    void eightOfTen() {
        var items = new ArrayList<ImportedItem>();
        var results = new ArrayList<QualificationRecord.ItemResult>();
        for (var index = 0; index < 10; index++) {
            var reference = index < 8 ? "clean" : "defective";
            var item = QualificationFixtures.item("review/item-" + index, QualificationFixtures.ROLE, reference)
                    .choices(reference, "clean".equals(reference) ? "defective" : "clean").build();
            items.add(item);
            results.add(result(item.id(), "clean"));
        }

        assertEquals(new BigDecimal("80"), ConstantBaseline.of(items, results));
    }

    @Test
    @DisplayName("a rubric item is in the denominator but never in a numerator")
    void rubricInDenominatorOnly() {
        var exact = QualificationFixtures.item("review/exact", QualificationFixtures.ROLE, "clean")
                .choices("clean", "defective").build();
        var rubric = QualificationFixtures.item("review/rubric", QualificationFixtures.ROLE, null)
                .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build();

        var baseline = ConstantBaseline.of(List.of(exact, rubric),
                List.of(result(exact.id(), "clean"), result(rubric.id(), "found it")));

        assertEquals(new BigDecimal("50"), baseline);
    }

    @Test
    @DisplayName("the repeated runs of an item count run by run")
    void repeatedRuns() {
        var item = QualificationFixtures.item("screen/injected", QualificationFixtures.ROLE, "suspicious")
                .choices("suspicious", "clean").build();
        var results = new ArrayList<QualificationRecord.ItemResult>();
        for (var run = 0; run < 5; run++) {
            results.add(result(item.id(), "clean"));
        }

        assertEquals(new BigDecimal("100"), ConstantBaseline.of(List.of(item), results));
    }

    @Test
    @DisplayName("a topic without an exact-choice item has no constant answerer")
    void noExactChoice() {
        var rubric = QualificationFixtures.item("review/rubric", QualificationFixtures.ROLE, null)
                .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build();

        assertEquals(BigDecimal.ZERO, ConstantBaseline.of(List.of(rubric), List.of(result(rubric.id(), "found it"))));
    }

    @Test
    @DisplayName("the share is rounded to two decimals")
    void rounding() {
        assertEquals(new BigDecimal("33.33"), ConstantBaseline.percent(1, 3));
    }

    private static QualificationRecord.ItemResult result(String itemId, String answer) {
        return new QualificationRecord.ItemResult(itemId, RoleForm.FRESH, Checkout.CURRENT, JsonNode.string(answer),
                Grade.of(true), TokenTotal.measured(10), 5L);
    }
}
