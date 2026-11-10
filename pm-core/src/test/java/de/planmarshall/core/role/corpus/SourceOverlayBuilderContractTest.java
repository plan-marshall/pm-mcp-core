/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link SourceOverlay} and of its nested {@link SourceOverlay.Row} build the values the canonical
 * constructors build, and a missing required component is refused with the same refusal.
 */
@DisplayName("Source overlay builders")
class SourceOverlayBuilderContractTest {

    @Test
    @DisplayName("the overlay builder builds the constructor's value and refuses an absent source alike")
    void shouldBuildOverlayLikeTheConstructorAndRefuseAnAbsentSourceAlike() {
        var canonical = new SourceOverlay("corpus", "data/rows.csv", null, LocalDate.of(2026, 10, 14),
                List.of(row()));
        var built = SourceOverlay.builder().source(canonical.source()).file(canonical.file())
                .keyField(canonical.keyField()).created(canonical.created()).rows(canonical.rows()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new SourceOverlay(null, canonical.file(), null, canonical.created(),
                                    canonical.rows()),
                            "the constructor refuses a null source");
                    var incomplete = SourceOverlay.builder().file(canonical.file()).created(canonical.created())
                            .rows(canonical.rows());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing source");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent source with the same message");
                });
    }

    @Test
    @DisplayName("the row builder builds the constructor's value and refuses an absent key alike")
    void shouldBuildRowLikeTheConstructorAndRefuseAnAbsentKeyAlike() {
        var canonical = row();
        var built = SourceOverlay.Row.builder().key(canonical.key()).slug(canonical.slug())
                .topic(canonical.topic()).role(canonical.role()).title(canonical.title()).tags(canonical.tags())
                .group(canonical.group()).question(canonical.question()).facts(canonical.facts())
                .choices(canonical.choices()).answerSchema(canonical.answerSchema()).target(canonical.target())
                .grading(canonical.grading()).review(canonical.review())
                .contamination(canonical.contamination()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new SourceOverlay.Row(null, canonical.slug(), canonical.topic(),
                                    canonical.role(), canonical.title(), canonical.tags(), canonical.group(),
                                    canonical.question(), canonical.facts(), canonical.choices(),
                                    canonical.answerSchema(), canonical.target(), canonical.grading(),
                                    canonical.review(), canonical.contamination()),
                            "the constructor refuses a null key");
                    var incomplete = SourceOverlay.Row.builder().slug(canonical.slug()).topic(canonical.topic())
                            .role(canonical.role()).title(canonical.title()).tags(canonical.tags())
                            .question(canonical.question()).facts(canonical.facts())
                            .target(canonical.target()).grading(canonical.grading())
                            .review(canonical.review());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing key");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent key with the same message");
                });
    }

    private static SourceOverlay.Row row() {
        return new SourceOverlay.Row("1", "sample-slug", "screening", "review-code", "A sample question",
                List.of("tag"), null, "Which holds?", "Subject: logic", List.of("first", "second"), null,
                new ImportedItem.Target("first", "the first holds", List.of(), List.of(), false),
                new ImportedItem.Grading(ImportedItem.GradingMethod.EXACT_CHOICE, null, null),
                new ImportedItem.Review(ImportedItem.ReviewStatus.DRAFT, "operator", null, null), null);
    }
}
