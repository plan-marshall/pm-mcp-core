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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
@EnableGeneratorController
@DisplayName("Constant baseline")
class ConstantBaselineTest {

    @Test
    @DisplayName("eight of ten references the same key are a baseline of 80")
    void eightOfTen() {
        var topic = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var majority = "a" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var minority = "b" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var items = new ArrayList<ImportedItem>();
        var results = new ArrayList<QualificationRecord.ItemResult>();
        for (var index = 0; index < 10; index++) {
            var reference = index < 8 ? majority : minority;
            var item = QualificationFixtures.item(topic + "/item-" + index, QualificationFixtures.ROLE, reference)
                    .choices(reference, majority.equals(reference) ? minority : majority).build();
            items.add(item);
            results.add(result(item.id(), majority));
        }

        assertEquals(new BigDecimal("80"), ConstantBaseline.of(items, results));
    }

    @Test
    @DisplayName("a rubric item is in the denominator but never in a numerator")
    void rubricInDenominatorOnly() {
        var topic = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var answer = "a" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var other = "b" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var exact = QualificationFixtures.item(topic + "/exact", QualificationFixtures.ROLE, answer)
                .choices(answer, other).build();
        var rubric = QualificationFixtures.item(topic + "/rubric", QualificationFixtures.ROLE, null)
                .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build();

        var baseline = ConstantBaseline.of(List.of(exact, rubric),
                List.of(result(exact.id(), answer),
                        result(rubric.id(), "f" + Generators.letterStrings(3, 10).next())));

        assertEquals(new BigDecimal("50"), baseline);
    }

    @Test
    @DisplayName("the repeated runs of an item count run by run")
    void repeatedRuns() {
        var topic = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var reference = "r" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var other = "o" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var answer = "a" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var item = QualificationFixtures.item(topic + "/injected", QualificationFixtures.ROLE, reference)
                .choices(reference, other).build();
        var results = new ArrayList<QualificationRecord.ItemResult>();
        for (var run = 0; run < 5; run++) {
            results.add(result(item.id(), answer));
        }

        assertEquals(new BigDecimal("100"), ConstantBaseline.of(List.of(item), results));
    }

    @Test
    @DisplayName("a topic without an exact-choice item has no constant answerer")
    void noExactChoice() {
        var topic = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var rubric = QualificationFixtures.item(topic + "/rubric", QualificationFixtures.ROLE, null)
                .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build();

        assertEquals(BigDecimal.ZERO, ConstantBaseline.of(List.of(rubric),
                List.of(result(rubric.id(), "f" + Generators.letterStrings(3, 10).next()))),
                "Rubric-only topic has zero baseline");
    }

    @Test
    @DisplayName("the share is rounded to two decimals")
    void rounding() {
        assertEquals(new BigDecimal("33.33"), ConstantBaseline.percent(1, 3));
    }

    private static QualificationRecord.ItemResult result(String itemId, String answer) {
        return new QualificationRecord.ItemResult(itemId, RoleForm.FRESH, Checkout.CURRENT, JsonNode.string(answer),
                Grade.of(true), TokenTotal.measured(Generators.integers(1, 1_000).next()),
                (long) Generators.integers(1, 100).next());
    }
}
