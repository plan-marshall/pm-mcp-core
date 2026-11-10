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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link ImportedItem} and of its nested values build what their canonical constructors build, and a
 * missing required component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Imported item builders")
class ImportedItemBuilderContractTest {

    @Test
    @DisplayName("the item builder builds the constructor's value and refuses an absent role alike")
    void shouldBuildItemLikeTheConstructorAndRefuseAnAbsentRoleAlike() {
        var canonical = ImportedItemCodecTest.closedItem();
        var built = ImportedItem.builder().id(canonical.id()).topic(canonical.topic()).role(canonical.role())
                .title(canonical.title()).tags(canonical.tags()).group(canonical.group()).input(canonical.input())
                .target(canonical.target()).grading(canonical.grading()).provenance(canonical.provenance())
                .review(canonical.review()).contamination(canonical.contamination()).canary(canonical.canary())
                .build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ImportedItem(canonical.id(), canonical.topic(), null, canonical.title(),
                                    canonical.tags(), canonical.group(), canonical.input(), canonical.target(),
                                    canonical.grading(), canonical.provenance(), canonical.review(),
                                    canonical.contamination(), canonical.canary()),
                            "the constructor refuses a null role");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ImportedItem.builder().id(canonical.id()).topic(canonical.topic())
                                    .title(canonical.title()).tags(canonical.tags()).group(canonical.group())
                                    .input(canonical.input()).target(canonical.target())
                                    .grading(canonical.grading()).provenance(canonical.provenance())
                                    .review(canonical.review()).contamination(canonical.contamination())
                                    .canary(canonical.canary()).build(),
                            "the builder refuses a missing role");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent role with the same message");
                });
    }

    @Test
    @DisplayName("the input builder builds the constructor's value and refuses an absent question alike")
    void shouldBuildInputLikeTheConstructorAndRefuseAnAbsentQuestionAlike() {
        var canonical = new ImportedItem.Input("Which holds?", "Subject: logic", List.of("first", "second"), null);
        var built = ImportedItem.Input.builder().question(canonical.question()).facts(canonical.facts())
                .choices(canonical.choices()).answerSchema(canonical.answerSchema()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ImportedItem.Input(null, "Subject: logic", null, null),
                            "the constructor refuses a null question");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ImportedItem.Input.builder().facts("Subject: logic").build(),
                            "the builder refuses a missing question");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent question with the same message");
                });
    }

    @Test
    @DisplayName("the target builder builds the constructor's value and refuses an absent rationale alike")
    void shouldBuildTargetLikeTheConstructorAndRefuseAnAbsentRationaleAlike() {
        var canonical = new ImportedItem.Target("first", "the first holds", List.of("second"),
                List.of(new ImportedItem.RubricItem("r1", "the first option")), false);
        var built = ImportedItem.Target.builder().answer(canonical.answer()).rationale(canonical.rationale())
                .alternatives(canonical.alternatives()).items(canonical.items())
                .expectedEmpty(canonical.expectedEmpty()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ImportedItem.Target("first", null, List.of(), List.of(), false),
                            "the constructor refuses a null rationale");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ImportedItem.Target.builder().answer("first").alternatives(List.of())
                                    .items(List.of()).expectedEmpty(false).build(),
                            "the builder refuses a missing rationale");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent rationale with the same message");
                });
    }

    @Test
    @DisplayName("the origin builder builds the constructor's value and refuses an absent repository alike")
    void shouldBuildOriginLikeTheConstructorAndRefuseAnAbsentRepositoryAlike() {
        var canonical = new ImportedItem.Origin(URI.create("https://github.com/example/sample-set"),
                "%040x".formatted(Generators.longs().next()), "data/test.csv", 2);
        var built = ImportedItem.Origin.builder().repository(canonical.repository()).commit(canonical.commit())
                .path(canonical.path()).line(canonical.line()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ImportedItem.Origin(null, canonical.commit(), canonical.path(),
                                    canonical.line()),
                            "the constructor refuses a null repository");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ImportedItem.Origin.builder().commit(canonical.commit()).path(canonical.path())
                                    .line(canonical.line()).build(),
                            "the builder refuses a missing repository");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent repository with the same message");
                });
    }

    @Test
    @DisplayName("the review builder builds the constructor's value and refuses an absent status alike")
    void shouldBuildReviewLikeTheConstructorAndRefuseAnAbsentStatusAlike() {
        var canonical = new ImportedItem.Review(ImportedItem.ReviewStatus.CONFIRMED, "operator",
                LocalDate.of(2026, 10, 15), "checked");
        var built = ImportedItem.Review.builder().status(canonical.status()).by(canonical.by())
                .date(canonical.date()).notes(canonical.notes()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ImportedItem.Review(null, "operator", null, "checked"),
                            "the constructor refuses a null status");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ImportedItem.Review.builder().by("operator").notes("checked").build(),
                            "the builder refuses a missing status");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent status with the same message");
                });
    }
}
