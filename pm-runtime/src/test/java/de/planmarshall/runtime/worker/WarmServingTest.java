/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.corpus.JsonValue;

/**
 * The contamination rule of a warm run (Model Work § Grading): a warm worker serves the items in order, and two items whose
 * {@code contamination} notes name each other never share it.
 */
@EnableGeneratorController
@DisplayName("Warm serving")
class WarmServingTest {

    @Test
    @DisplayName("an item without a note may share the worker")
    void noNoteShares() {
        assertAll(
                () -> assertTrue(WarmServing.mayShare(null, List.of("review-code/first")),
                        "no note names anything"),
                () -> assertFalse(WarmServing.names(List.of(item("review-code/first", null)),
                        item("review-code/second", null)),
                        "two items without notes never name each other"));
    }

    @Test
    @DisplayName("an item whose note names a served item may not share the worker")
    void ownNoteCloses() {
        assertFalse(WarmServing.mayShare("contaminated by review-code/first", List.of("review-code/first")),
                "the candidate's own note names the served item");
    }

    @Test
    @DisplayName("a served item's note that names the candidate closes the worker")
    void servedNoteCloses() {
        var served = item("review-code/first", "contaminated by review-code/second");

        assertTrue(WarmServing.names(List.of(served), item("review-code/second", null)),
                "the served item's note names the candidate");
    }

    private static ImportedItem item(String id, String contamination) {
        var input = new ImportedItem.Input("question", "", null,
                JsonValue.parse("{\"type\":\"string\"}".getBytes(StandardCharsets.UTF_8)));
        var target = new ImportedItem.Target("right", "", List.of(), List.of(), false);
        var grading = new ImportedItem.Grading(ImportedItem.GradingMethod.EXACT_CHOICE, null, null);
        var provenance = new ImportedItem.Provenance(LocalDate.ofEpochDay(19_000),
                new ImportedItem.Origin(URI.create("https://example.org/corpus"), "%040x".formatted(1L),
                        "rows.csv", 1),
                List.of("generated"));
        var review = new ImportedItem.Review(ImportedItem.ReviewStatus.CONFIRMED, "corpus", null, "");
        return new ImportedItem(id, id.substring(0, id.lastIndexOf('/')), "review-code", "an item", List.of(), null,
                input, target, grading, provenance, review, contamination, "canary");
    }
}
