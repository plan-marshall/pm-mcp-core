/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.lsp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Proves that the builder of {@link LspClient.LspLocation} builds the value its canonical constructor builds. The URI
 * and the four 0-based positions are generated; the record refuses no component.
 */
@EnableGeneratorController
@DisplayName("LspClient.LspLocation builder")
class LspClientRecordsTest {

    @Test
    @DisplayName("the builder builds the lsp location the constructor builds")
    void lspLocationBuilderEqualsConstructor() {
        var uri = "file:///" + Generators.letterStrings(3, 20).next();
        var startLine = Generators.integers(0, 10_000).next();
        var startCharacter = Generators.integers(0, 500).next();
        var endLine = startLine + Generators.integers(0, 100).next();
        var endCharacter = Generators.integers(0, 500).next();

        var constructed = new LspClient.LspLocation(uri, startLine, startCharacter, endLine, endCharacter);
        var built = LspClient.LspLocation.builder()
                .uri(uri)
                .startLine(startLine)
                .startCharacter(startCharacter)
                .endLine(endLine)
                .endCharacter(endCharacter)
                .build();

        assertEquals(constructed, built, "the builder builds the same lsp location");
    }
}
