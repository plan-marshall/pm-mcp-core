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

import java.math.BigDecimal;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleForm;

/**
 * The builders of {@link QualificationRecord} and of its nested results build what their canonical constructors build,
 * and a missing required component is refused with the same refusal.
 */
@DisplayName("Qualification record builders")
class QualificationRecordBuilderContractTest {

    @Test
    @DisplayName("the record builder builds the constructor's value and refuses an absent role alike")
    void shouldBuildRecordLikeTheConstructorAndRefuseAnAbsentRoleAlike() {
        var canonical = QualificationFixtures.qualification(Map.of(Topic.OWN, new BigDecimal("90")));
        var built = QualificationRecord.builder().role(canonical.role())
                .qualificationDigest(canonical.qualificationDigest()).configuration(canonical.configuration())
                .corpusRevision(canonical.corpusRevision()).missedSources(canonical.missedSources())
                .forms(canonical.forms()).warmFreshGap(canonical.warmFreshGap()).ungraded(canonical.ungraded())
                .gradedAt(canonical.gradedAt()).items(canonical.items()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QualificationRecord(null, canonical.qualificationDigest(),
                                    canonical.configuration(), canonical.corpusRevision(),
                                    canonical.missedSources(), canonical.forms(), canonical.warmFreshGap(),
                                    canonical.ungraded(), canonical.gradedAt(), canonical.items()),
                            "the constructor refuses a null role");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QualificationRecord.builder()
                                    .qualificationDigest(canonical.qualificationDigest())
                                    .configuration(canonical.configuration())
                                    .corpusRevision(canonical.corpusRevision())
                                    .missedSources(canonical.missedSources()).forms(canonical.forms())
                                    .warmFreshGap(canonical.warmFreshGap()).ungraded(canonical.ungraded())
                                    .gradedAt(canonical.gradedAt()).items(canonical.items()).build(),
                            "the builder refuses a missing role");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent role with the same message");
                });
    }

    @Test
    @DisplayName("the form result builder builds the constructor's value and refuses absent topics alike")
    void shouldBuildFormResultLikeTheConstructorAndRefuseAbsentTopicsAlike() {
        var canonical = new QualificationRecord.FormResult(
                Map.of(Topic.OWN, new QualificationRecord.TopicResult(new BigDecimal("90"), BigDecimal.ZERO, 5)),
                5, TokenTotal.measured(1_200), 800);
        var built = QualificationRecord.FormResult.builder().topics(canonical.topics()).items(canonical.items())
                .totalTokens(canonical.totalTokens()).durationMs(canonical.durationMs()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QualificationRecord.FormResult(null, 5, TokenTotal.measured(1_200), 800),
                            "the constructor refuses null topics");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QualificationRecord.FormResult.builder().items(5)
                                    .totalTokens(TokenTotal.measured(1_200)).durationMs(800).build(),
                            "the builder refuses missing topics");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse absent topics with the same message");
                });
    }

    @Test
    @DisplayName("the item result builder builds the constructor's value and refuses an absent item id alike")
    void shouldBuildItemResultLikeTheConstructorAndRefuseAnAbsentItemIdAlike() {
        var canonical = new QualificationRecord.ItemResult("review/sample/q-1", RoleForm.FRESH, Checkout.CURRENT,
                JsonNode.nullValue(), Grade.of(true), TokenTotal.measured(1_200), 800L);
        var built = QualificationRecord.ItemResult.builder().itemId(canonical.itemId()).form(canonical.form())
                .checkout(canonical.checkout()).answer(canonical.answer()).score(canonical.score())
                .totalTokens(canonical.totalTokens()).durationMs(canonical.durationMs()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new QualificationRecord.ItemResult(null, RoleForm.FRESH, Checkout.CURRENT,
                                    JsonNode.nullValue(), Grade.of(true), TokenTotal.measured(1_200), 800L),
                            "the constructor refuses a null item id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> QualificationRecord.ItemResult.builder().form(RoleForm.FRESH)
                                    .checkout(Checkout.CURRENT).answer(JsonNode.nullValue()).score(Grade.of(true))
                                    .totalTokens(TokenTotal.measured(1_200)).durationMs(800L).build(),
                            "the builder refuses a missing item id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent item id with the same message");
                });
    }
}
