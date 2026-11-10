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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The two states of a coverage parse result.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("CoverageParseResult")
class CoverageParseResultTest {

    @Test
    @DisplayName("carries the parsed report as its value")
    void carriesTheParsedReport() {
        CoverageReport report = new CoverageReport(CoverageFormat.LCOV,
                CoverageMetrics.builder().line(50).build(), List.of());

        CoverageParseResult.Parsed parsed = assertInstanceOf(CoverageParseResult.Parsed.class,
                new CoverageParseResult.Parsed(report), "A parsed result is a Parsed");

        assertEquals(report, parsed.report(), "The parsed result keeps the report");
    }

    @Test
    @DisplayName("refuses a parsed result without a report")
    void refusesAParsedResultWithoutReport() {
        assertThrows(NullPointerException.class, () -> new CoverageParseResult.Parsed(null),
                "A parsed result needs a report");
    }

    @Test
    @DisplayName("refuses a refusal without a reason or format")
    void refusesARefusalWithoutReasonOrFormat() {
        assertThrows(IllegalArgumentException.class,
                () -> new CoverageParseResult.Refused(CoverageFormat.LCOV, " "),
                "A refusal needs a non-blank reason");
        assertThrows(NullPointerException.class,
                () -> new CoverageParseResult.Refused(null, "broken"),
                "A refusal names its format");
    }

    @Test
    @DisplayName("names the format and the reason of a refusal")
    void namesTheFormatAndReason() {
        String reason = Generators.nonEmptyStrings().next();

        CoverageParseResult.Refused refused =
                new CoverageParseResult.Refused(CoverageFormat.ISTANBUL, reason);

        assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names its format");
        assertEquals(reason, refused.reason(), "The refusal keeps its reason");
    }
}
