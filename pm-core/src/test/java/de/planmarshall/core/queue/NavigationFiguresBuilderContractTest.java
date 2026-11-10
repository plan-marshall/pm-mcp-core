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

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link NavigationFigures} builds the value the canonical constructor builds, and a missing cache-read
 * share is refused with the same refusal.
 */
@DisplayName("Navigation figures builder")
class NavigationFiguresBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var cacheReadShare = Figure.measured("cache_read_share", 0.25, 4);
        var revisits = Figure.measured("revisits", 0.5, 2);
        var languageServerUse = Figure.measured("language_server_use", 0.75, 4);
        var fallbackToReadRate = Figure.measured("fallback_to_read_rate", 0.1, 10);

        var canonical = new NavigationFigures(cacheReadShare, revisits, languageServerUse, fallbackToReadRate,
                List.of());
        var built = NavigationFigures.builder().cacheReadShare(canonical.cacheReadShare())
                .revisits(canonical.revisits()).languageServerUse(canonical.languageServerUse())
                .fallbackToReadRate(canonical.fallbackToReadRate()).residency(canonical.residency()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new NavigationFigures(null, revisits, languageServerUse, fallbackToReadRate,
                                    List.of()),
                            "the constructor refuses a null cacheReadShare");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> NavigationFigures.builder().revisits(revisits)
                                    .languageServerUse(languageServerUse)
                                    .fallbackToReadRate(fallbackToReadRate).residency(List.of()).build(),
                            "the builder refuses a missing cacheReadShare");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent cacheReadShare with the same message");
                });
    }
}
