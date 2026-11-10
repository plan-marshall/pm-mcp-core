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
 * The builder of {@link LessonStore.NewLesson} builds the value the canonical constructor builds, and an absent title
 * is refused with the same refusal.
 */
@DisplayName("New lesson builder")
class LessonStoreBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent title")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentTitle() {
        var fixture = LessonFixtures.newLesson().build();
        var canonical = new LessonStore.NewLesson(fixture.title(), fixture.component(), fixture.category(),
                fixture.bundle(), fixture.rule(), fixture.remedy(), fixture.provenance(), fixture.audience(),
                fixture.derivedFromUntrusted(), fixture.toolVersion(), fixture.sources(), fixture.body());
        var built = LessonStore.NewLesson.builder().title(canonical.title()).component(canonical.component())
                .category(canonical.category()).bundle(canonical.bundle()).rule(canonical.rule())
                .remedy(canonical.remedy()).provenance(canonical.provenance()).audience(canonical.audience())
                .derivedFromUntrusted(canonical.derivedFromUntrusted()).toolVersion(canonical.toolVersion())
                .sources(canonical.sources()).body(canonical.body()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new LessonStore.NewLesson(null, canonical.component(), canonical.category(),
                                    canonical.bundle(), canonical.rule(), canonical.remedy(),
                                    canonical.provenance(), canonical.audience(),
                                    canonical.derivedFromUntrusted(), canonical.toolVersion(), canonical.sources(),
                                    canonical.body()),
                            "the constructor refuses a null title");
                    var incomplete = LessonStore.NewLesson.builder().component(canonical.component())
                            .category(canonical.category()).remedy(canonical.remedy())
                            .provenance(canonical.provenance()).audience(canonical.audience())
                            .toolVersion(canonical.toolVersion()).sources(canonical.sources())
                            .body(canonical.body());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing title");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent title with the same message");
                });
    }
}
