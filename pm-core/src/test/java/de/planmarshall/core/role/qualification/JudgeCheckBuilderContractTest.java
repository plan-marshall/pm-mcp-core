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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link JudgeCheck.Record}, {@link JudgeCheck.ItemScore} and {@link JudgeCheck.Item} build what their
 * canonical constructors build, and a missing required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Judge check builders")
class JudgeCheckBuilderContractTest {

    @Test
    @DisplayName("the record builder builds the constructor's value and refuses an absent digest alike")
    void shouldBuildRecordLikeTheConstructorAndRefuseAnAbsentDigestAlike() {
        var score = new JudgeCheck.ItemScore("judge/q-1", JudgeCheck.Label.RIGHT, Grade.of(true),
                Grade.scored(new BigDecimal("0.9")), TokenTotal.measured(1_200), 800L);
        var canonical = new JudgeCheck.Record("%064x".formatted(Generators.longs().next()), "judge", "uniform",
                "corpus-1", new BigDecimal("90"), new BigDecimal("40"), new BigDecimal("50"), 0,
                Instant.parse("2026-10-14T10:00:00Z"), List.of(score));
        var built = JudgeCheck.Record.builder().qualificationDigest(canonical.qualificationDigest())
                .harness(canonical.harness()).model(canonical.model())
                .corpusRevision(canonical.corpusRevision()).oracleAccuracy(canonical.oracleAccuracy())
                .blindAccuracy(canonical.blindAccuracy()).constantBaseline(canonical.constantBaseline())
                .unscored(canonical.unscored()).checkedAt(canonical.checkedAt()).items(canonical.items()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new JudgeCheck.Record(null, canonical.harness(), canonical.model(),
                                    canonical.corpusRevision(), canonical.oracleAccuracy(),
                                    canonical.blindAccuracy(), canonical.constantBaseline(), canonical.unscored(),
                                    canonical.checkedAt(), canonical.items()),
                            "the constructor refuses a null digest");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> JudgeCheck.Record.builder().harness(canonical.harness())
                                    .model(canonical.model()).corpusRevision(canonical.corpusRevision())
                                    .oracleAccuracy(canonical.oracleAccuracy())
                                    .blindAccuracy(canonical.blindAccuracy())
                                    .constantBaseline(canonical.constantBaseline())
                                    .unscored(canonical.unscored()).checkedAt(canonical.checkedAt())
                                    .items(canonical.items()).build(),
                            "the builder refuses a missing digest");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent digest with the same message");
                });
    }

    @Test
    @DisplayName("the item score builder builds the constructor's value and refuses an absent item id alike")
    void shouldBuildItemScoreLikeTheConstructorAndRefuseAnAbsentItemIdAlike() {
        var canonical = new JudgeCheck.ItemScore("judge/q-1", JudgeCheck.Label.RIGHT, Grade.of(true),
                Grade.scored(new BigDecimal("0.9")), TokenTotal.measured(1_200), 800L);
        var built = JudgeCheck.ItemScore.builder().itemId(canonical.itemId()).label(canonical.label())
                .oracleScore(canonical.oracleScore()).blindScore(canonical.blindScore())
                .totalTokens(canonical.totalTokens()).durationMs(canonical.durationMs()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new JudgeCheck.ItemScore(null, JudgeCheck.Label.RIGHT, Grade.of(true),
                                    Grade.of(false), TokenTotal.measured(1_200), 800L),
                            "the constructor refuses a null item id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> JudgeCheck.ItemScore.builder().label(JudgeCheck.Label.RIGHT)
                                    .oracleScore(Grade.of(true)).blindScore(Grade.of(false))
                                    .totalTokens(TokenTotal.measured(1_200)).durationMs(800L).build(),
                            "the builder refuses a missing item id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent item id with the same message");
                });
    }

    @Test
    @DisplayName("the item builder builds the constructor's value and refuses an absent item id alike")
    void shouldBuildItemLikeTheConstructorAndRefuseAnAbsentItemIdAlike() {
        var canonical = new JudgeCheck.Item("judge/q-1", "Is it right?", "Subject", "right", "right answer",
                JudgeCheck.Label.RIGHT);
        var built = JudgeCheck.Item.builder().itemId(canonical.itemId()).question(canonical.question())
                .facts(canonical.facts()).reference(canonical.reference()).answer(canonical.answer())
                .label(canonical.label()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new JudgeCheck.Item(null, "Is it right?", "Subject", "right", "right answer",
                                    JudgeCheck.Label.RIGHT),
                            "the constructor refuses a null item id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> JudgeCheck.Item.builder().question("Is it right?").facts("Subject")
                                    .reference("right").answer("right answer").label(JudgeCheck.Label.RIGHT)
                                    .build(),
                            "the builder refuses a missing item id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent item id with the same message");
                });
    }
}
