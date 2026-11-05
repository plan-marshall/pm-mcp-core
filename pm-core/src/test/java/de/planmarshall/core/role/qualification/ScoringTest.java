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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The deterministic scorer of a corpus item (Model Work § Grading): a closed answer is compared with the reference and
 * the accepted alternatives, a control is right when it reports nothing, and a rubric answer is right from the item's
 * pass threshold — a declared threshold where it declares one, the compiled {@code 0.5} where it declares none.
 */
@EnableGeneratorController
@DisplayName("Scoring")
class ScoringTest {

    @Test
    @DisplayName("a closed answer is right when it is the reference or an accepted alternative")
    void closed() {
        var topic = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var reference = "a" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var other = "b" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var item = QualificationFixtures.item(topic + "/clean-plan", QualificationFixtures.ROLE, reference).build();

        assertAll(
                () -> assertTrue(Scoring.closed(item, reference).judgedRight()),
                () -> assertFalse(Scoring.closed(item, other).judgedRight()),
                () -> assertTrue(Scoring.closed(item, " " + reference + " ").judgedRight()));
    }

    @Test
    @DisplayName("a control is right only when it reports nothing")
    void control() {
        var topic = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var other = "b" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var item = new ImportedItem(topic + "/control", topic, QualificationFixtures.ROLE,
                Generators.letterStrings(5, 30).next(), List.of(), null,
                new ImportedItem.Input(Generators.letterStrings(5, 30).next(), "",
                        List.of("y" + Generators.letterStrings(2, 6).next(),
                                "n" + Generators.letterStrings(2, 6).next()), null),
                new ImportedItem.Target(null, Generators.letterStrings(5, 30).next(), List.of(), List.of(), true),
                new ImportedItem.Grading(ImportedItem.GradingMethod.EXACT_CHOICE, null, null),
                new ImportedItem.Provenance(LocalDate.ofEpochDay(Generators.longs(19_000, 21_000).next()),
                        new ImportedItem.Origin(QualificationFixtures.DATASET,
                                "%040x".formatted(Generators.longs().next()),
                                Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT) + "/rows.csv",
                                Generators.integers(1, 100).next()),
                        List.of(Generators.letterStrings(5, 30).next())),
                new ImportedItem.Review(ImportedItem.ReviewStatus.CONFIRMED,
                        Generators.letterStrings(3, 10).next(), null, null),
                null, "canary");

        assertAll(
                () -> assertTrue(Scoring.closed(item, "").judgedRight()),
                () -> assertFalse(Scoring.closed(item, other).judgedRight()));
    }

    @Test
    @DisplayName("a rubric answer is right from the item's pass threshold")
    void rubric() {
        var topic = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var item = QualificationFixtures.item(topic + "/rubric", QualificationFixtures.ROLE, null)
                .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build();

        assertAll(
                () -> assertTrue(Scoring.right(item, new BigDecimal("0.5"))),
                () -> assertTrue(Scoring.right(item, BigDecimal.ONE)),
                () -> assertFalse(Scoring.right(item, new BigDecimal("0.4"))),
                () -> assertTrue(Scoring.judged(item)));
    }

    @Test
    @DisplayName("a declared pass threshold wins over the default")
    void declaredThresholdWins() {
        var above = BigDecimal.valueOf(Generators.integers(6, 10).next()).movePointLeft(1);
        var below = BigDecimal.valueOf(Generators.integers(1, 4).next()).movePointLeft(1);
        var itemAbove = rubricItemWith(above);
        var itemBelow = rubricItemWith(below);

        assertAll(
                () -> assertEquals(above, Scoring.threshold(itemAbove),
                        "a threshold above the default is kept"),
                () -> assertEquals(below, Scoring.threshold(itemBelow),
                        "a threshold below the default is kept"),
                () -> assertTrue(Scoring.right(itemAbove, above), "a score at the declared threshold passes"),
                () -> assertFalse(Scoring.right(itemAbove, Scoring.DEFAULT_PASS_THRESHOLD),
                        "the compiled default does not pass above it"),
                () -> assertTrue(Scoring.right(itemBelow, below), "a lower declared threshold passes"),
                () -> assertFalse(Scoring.right(itemBelow, BigDecimal.ZERO),
                        "a score below a lower declared threshold fails"));
    }

    @Test
    @DisplayName("the default pass threshold is 0.5 when the grading declares none")
    void defaultThresholdWhenNoneDeclared() {
        var item = QualificationFixtures.item("review/rubric", QualificationFixtures.ROLE, null)
                .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build();

        assertAll(
                () -> assertEquals(new BigDecimal("0.5"), Scoring.threshold(item),
                        "the compiled default threshold is one half"),
                () -> assertTrue(Scoring.right(item, Scoring.DEFAULT_PASS_THRESHOLD),
                        "a score at the compiled default passes"));
    }

    @Test
    @DisplayName("a closed item is never sent to the judge")
    void closedIsNotJudged() {
        var topic = "t" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var item = QualificationFixtures.item(topic + "/clean-plan", QualificationFixtures.ROLE,
                "a" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT)).build();

        assertFalse(Scoring.judged(item));
    }

    private static ImportedItem rubricItemWith(BigDecimal passThreshold) {
        return withPassThreshold(QualificationFixtures.item("review/rubric", QualificationFixtures.ROLE, null)
                .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build(), passThreshold);
    }

    private static ImportedItem withPassThreshold(ImportedItem item, BigDecimal passThreshold) {
        return new ImportedItem(item.id(), item.topic(), item.role(), item.title(), item.tags(), item.group(),
                item.input(), item.target(),
                new ImportedItem.Grading(item.grading().method(), item.grading().judge(), passThreshold),
                item.provenance(), item.review(), item.contamination(), item.canary());
    }
}
