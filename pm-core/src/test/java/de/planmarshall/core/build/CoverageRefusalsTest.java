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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The shared refusal construction of the coverage parsers.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageRefusals")
class CoverageRefusalsTest {

    @Test
    @DisplayName("opens the reason with the format identifier")
    void opensTheReasonWithTheFormat() {
        String message = Generators.nonEmptyStrings().next();

        CoverageParseResult.Refused refused = CoverageRefusals.of(CoverageFormat.LCOV, new IOException(message));

        assertTrue(refused.reason().startsWith("lcov: "), "The reason opens with the format id");
        assertEquals("lcov: " + message, refused.reason(), "The reason carries the cause's message");
    }

    @Test
    @DisplayName("names the failure class when the cause has no message")
    void namesTheFailureClassWithoutMessage() {
        CoverageParseResult.Refused refused = CoverageRefusals.of(CoverageFormat.ISTANBUL, new IOException());

        assertEquals("istanbul: IOException", refused.reason(), "A message-less cause names its class");
    }
}
