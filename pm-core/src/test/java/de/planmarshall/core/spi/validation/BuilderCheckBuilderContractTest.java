/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi.validation;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link BuilderCheck.Question} builds the value the canonical constructor builds, and a missing sort is
 * refused with the same refusal.
 */
@DisplayName("Builder check builder")
class BuilderCheckBuilderContractTest {

    @Test
    @DisplayName("the question builder builds the constructor's value and refuses an absent sort alike")
    void shouldBuildQuestionLikeTheConstructorAndRefuseAnAbsentSortAlike() {
        var canonical = new BuilderCheck.Question(BuilderCheck.Sort.OPERATOR, "Which constraint holds?",
                List.of("CN-1"), null);
        var built = BuilderCheck.Question.builder().sort(canonical.sort()).text(canonical.text())
                .items(canonical.items()).decision(canonical.decision()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new BuilderCheck.Question(null, "Which constraint holds?", List.of("CN-1"), null),
                            "the constructor refuses a null sort");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> BuilderCheck.Question.builder().text("Which constraint holds?")
                                    .items(List.of("CN-1")).build(),
                            "the builder refuses a missing sort");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent sort with the same message");
                });
    }
}
