/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.build;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link ParsedBuild} builds the value the canonical constructor builds, and a missing required component
 * is refused with the same refusal.
 */
@DisplayName("Parsed build builder")
class ParsedBuildBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var canonical = new ParsedBuild(List.of(), BuildStatus.SUCCESS, true, null, false);
        var built = ParsedBuild.builder().diagnostics(canonical.diagnostics()).status(canonical.status())
                .successMarker(canonical.successMarker()).testCounts(canonical.testCounts())
                .noTestsRan(canonical.noTestsRan()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ParsedBuild(List.of(), null, true, null, false),
                            "the constructor refuses a null status");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> ParsedBuild.builder().diagnostics(List.of()).successMarker(true).noTestsRan(false)
                                    .build(),
                            "the builder refuses a missing status");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent status with the same message");
                });
    }
}
