/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builders of {@link LessonFrontMatter} and of its nested records build the values the canonical constructors
 * build, and an absent required component is refused with the same refusal.
 */
@DisplayName("Lesson front matter builders")
class LessonFrontMatterBuilderContractTest {

    @Test
    @DisplayName("the front matter builder builds the constructor's value and refuses an absent lesson id")
    void shouldBuildFrontMatterLikeTheConstructorAndRefuseAnAbsentLessonId() {
        var canonical = LessonFixtures.frontMatter();
        var built = LessonFrontMatter.builder().formatVersion(canonical.formatVersion())
                .lessonId(canonical.lessonId()).title(canonical.title()).component(canonical.component())
                .category(canonical.category()).bundle(canonical.bundle()).rule(canonical.rule())
                .remedy(canonical.remedy()).recurrenceCount(canonical.recurrenceCount())
                .lastSeen(canonical.lastSeen()).status(canonical.status()).provenance(canonical.provenance())
                .derivedFromUntrusted(canonical.derivedFromUntrusted()).audience(canonical.audience())
                .lastFilingFailure(canonical.lastFilingFailure()).createdAt(canonical.createdAt())
                .toolVersion(canonical.toolVersion()).promotions(canonical.promotions())
                .revision(canonical.revision()).sources(canonical.sources()).revisions(canonical.revisions())
                .mergedFrom(canonical.mergedFrom()).claim(canonical.claim()).retired(canonical.retired()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LessonFrontMatter(canonical.formatVersion(), null, canonical.title(),
                                    canonical.component(), canonical.category(), canonical.bundle(),
                                    canonical.rule(), canonical.remedy(), canonical.recurrenceCount(),
                                    canonical.lastSeen(), canonical.status(), canonical.provenance(),
                                    canonical.derivedFromUntrusted(), canonical.audience(),
                                    canonical.lastFilingFailure(), canonical.createdAt(), canonical.toolVersion(),
                                    canonical.promotions(), canonical.revision(), canonical.sources(),
                                    canonical.revisions(), canonical.mergedFrom(), canonical.claim(),
                                    canonical.retired()),
                            "the constructor refuses a null lesson id");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LessonFixtures.frontMatterBuilder().lessonId(null).build(),
                            "the builder refuses a missing lesson id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent lesson id with the same message");
                });
    }

    @Test
    @DisplayName("the revision builder builds the constructor's value and refuses an absent instant")
    void shouldBuildRevisionLikeTheConstructorAndRefuseAnAbsentInstant() {
        var canonical = LessonFixtures.revision(1);
        var built = LessonFrontMatter.Revision.builder().n(canonical.n()).at(canonical.at())
                .sources(canonical.sources()).bodySha256(canonical.bodySha256()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LessonFrontMatter.Revision(1, null, canonical.sources(),
                                    canonical.bodySha256()),
                            "the constructor refuses a null instant");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LessonFrontMatter.Revision.builder().n(1).sources(canonical.sources())
                                    .bodySha256(canonical.bodySha256()).build(),
                            "the builder refuses a missing instant");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent instant with the same message");
                });
    }

    @Test
    @DisplayName("the retired builder builds the constructor's value and refuses an absent instant")
    void shouldBuildRetiredLikeTheConstructorAndRefuseAnAbsentInstant() {
        var fixture = LessonFixtures.retirement(LessonFrontMatter.Retired.Verdict.APPLIED).build();
        var canonical = new LessonFrontMatter.Retired(fixture.at(), fixture.by(), fixture.verdict(), fixture.into(),
                fixture.reason(), fixture.coveringClause(), fixture.coveringInput(), fixture.lessonState());
        var built = LessonFrontMatter.Retired.builder().at(canonical.at()).by(canonical.by())
                .verdict(canonical.verdict()).into(canonical.into()).reason(canonical.reason())
                .coveringClause(canonical.coveringClause()).coveringInput(canonical.coveringInput())
                .lessonState(canonical.lessonState()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LessonFrontMatter.Retired(null, canonical.by(), canonical.verdict(),
                                    canonical.into(), canonical.reason(), canonical.coveringClause(),
                                    canonical.coveringInput(), canonical.lessonState()),
                            "the constructor refuses a null instant");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> LessonFrontMatter.Retired.builder().by(canonical.by())
                                    .verdict(canonical.verdict()).reason(canonical.reason())
                                    .lessonState(canonical.lessonState()).build(),
                            "the builder refuses a missing instant");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent instant with the same message");
                });
    }
}
