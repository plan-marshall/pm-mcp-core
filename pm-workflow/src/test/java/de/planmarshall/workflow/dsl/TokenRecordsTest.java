/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link Token} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Token record builder")
class TokenRecordsTest {

    @Test
    @DisplayName("Token builder builds the canonical constructor's value")
    void tokenBuilderEqualsConstructor() {
        var kind = Token.Kind.IDENT;
        var text = Generators.letterStrings(1, 20).next();
        var line = Generators.integers(1, 500).next();
        var column = Generators.integers(1, 80).next();
        var constructed = new Token(kind, text, line, column);
        var built = Token.builder()
                .kind(kind)
                .text(text)
                .line(line)
                .column(column)
                .build();

        assertEquals(constructed, built, "the builder builds the same token");
    }
}
