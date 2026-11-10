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

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link NavigationVocabulary} builds the value the canonical constructor builds, and a missing
 * file-read set is refused with the same refusal.
 */
@DisplayName("Navigation vocabulary builder")
class NavigationVocabularyBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var canonical = new NavigationVocabulary(Set.of("read_file"), Set.of("read_file"),
                Set.of("search_code"), "pm_lsp", "pm_skill");
        var built = NavigationVocabulary.builder().fileReadTools(canonical.fileReadTools())
                .wholeFileReadTools(canonical.wholeFileReadTools()).searchTools(canonical.searchTools())
                .languageServerTool(canonical.languageServerTool()).skillTool(canonical.skillTool()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new NavigationVocabulary(null, Set.of(), Set.of(), "pm_lsp", "pm_skill"),
                            "the constructor refuses a null fileReadTools");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> NavigationVocabulary.builder().wholeFileReadTools(Set.of())
                                    .searchTools(Set.of()).languageServerTool("pm_lsp").skillTool("pm_skill")
                                    .build(),
                            "the builder refuses a missing fileReadTools");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent fileReadTools with the same message");
                });
    }
}
