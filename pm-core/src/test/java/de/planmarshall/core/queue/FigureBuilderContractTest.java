/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link Figure} builds the value the canonical constructor builds, and a missing name is refused with
 * the same refusal.
 */
@DisplayName("Navigation figure builder")
class FigureBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var canonical = new Figure("revisits", 0.5, 10, null);
        var built = Figure.builder().name(canonical.name()).value(canonical.value())
                .population(canonical.population()).sentinel(canonical.sentinel()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new Figure(null, 0.5, 10, null), "the constructor refuses a null name");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> Figure.builder().value(0.5).population(10).build(),
                            "the builder refuses a missing name");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent name with the same message");
                });
    }
}
