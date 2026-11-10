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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link Diagnostic} builds the value the canonical constructor builds, and a missing required component
 * is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Diagnostic builder")
class DiagnosticBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's value and refuses alike")
    void shouldBuildLikeTheConstructorAndRefuseAlike() {
        var file = Generators.letterStrings(3, 20).next() + ".java";
        var message = Generators.letterStrings(5, 40).next();

        var canonical = new Diagnostic(file, 12, 3, message, Category.COMPILATION_ERROR, Severity.ERROR, null);
        var built = Diagnostic.builder().file(canonical.file()).line(canonical.line()).column(canonical.column())
                .message(canonical.message()).category(canonical.category()).severity(canonical.severity())
                .stackTrace(canonical.stackTrace()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new Diagnostic(file, 12, 3, null, Category.COMPILATION_ERROR, Severity.ERROR,
                                    null),
                            "the constructor refuses a null message");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> Diagnostic.builder().file(file).line(12).column(3)
                                    .category(Category.COMPILATION_ERROR).severity(Severity.ERROR).build(),
                            "the builder refuses a missing message");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent message with the same message");
                });
    }
}
