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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The deterministic scorer of a corpus item (Model Work § Grading): a closed answer is compared with the reference and
 * the accepted alternatives, a control is right when it reports nothing, and a rubric answer is right from the item's
 * pass threshold — a compiled {@code 0.5} where it declares none.
 */
@DisplayName("Scoring")
class ScoringTest {

    @Test
    @DisplayName("a closed answer is right when it is the reference or an accepted alternative")
    void closed() {
        var item = QualificationFixtures.item("review/clean-plan", QualificationFixtures.ROLE, "clean").build();

        assertAll(
                () -> assertTrue(Scoring.closed(item, "clean").judgedRight()),
                () -> assertFalse(Scoring.closed(item, "defective").judgedRight()),
                () -> assertTrue(Scoring.closed(item, " clean ").judgedRight()));
    }

    @Test
    @DisplayName("a control is right only when it reports nothing")
    void control() {
        var item = new ImportedItem("review/control", "review", QualificationFixtures.ROLE, "A control",
                List.of(), null,
                new ImportedItem.Input("Is it right?", "", List.of("yes", "no"), null),
                new ImportedItem.Target(null, "Nothing to report.", List.of(), List.of(), true),
                new ImportedItem.Grading(ImportedItem.GradingMethod.EXACT_CHOICE, null, null),
                new ImportedItem.Provenance(LocalDate.of(2026, 1, 1),
                        new ImportedItem.Origin(QualificationFixtures.DATASET, "b".repeat(40), "data/rows.csv", 1),
                        List.of("created")),
                new ImportedItem.Review(ImportedItem.ReviewStatus.CONFIRMED, "operator", null, null), null, "canary");

        assertAll(
                () -> assertTrue(Scoring.closed(item, "").judgedRight()),
                () -> assertFalse(Scoring.closed(item, "a defect").judgedRight()));
    }

    @Test
    @DisplayName("a rubric answer is right from the item's pass threshold")
    void rubric() {
        var item = QualificationFixtures.item("review/rubric", QualificationFixtures.ROLE, null)
                .method(ImportedItem.GradingMethod.RUBRIC_ITEMS).build();

        assertAll(
                () -> assertTrue(Scoring.right(item, new BigDecimal("0.5"))),
                () -> assertTrue(Scoring.right(item, BigDecimal.ONE)),
                () -> assertFalse(Scoring.right(item, new BigDecimal("0.4"))),
                () -> assertTrue(Scoring.judged(item)));
    }

    @Test
    @DisplayName("a closed item is never sent to the judge")
    void closedIsNotJudged() {
        var item = QualificationFixtures.item("review/clean-plan", QualificationFixtures.ROLE, "clean").build();

        assertFalse(Scoring.judged(item));
    }
}
