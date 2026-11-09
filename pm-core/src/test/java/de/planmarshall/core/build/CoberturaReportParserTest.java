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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported tests of {@link CoberturaReportParser}: each case reads one plain-file fixture below {@code ported/coverage},
 * whose expected values are read by hand from the original and its pytest case ({@code PROVENANCE.adoc}).
 *
 * @since 0.1
 */
@DisplayName("CoberturaReportParser")
class CoberturaReportParserTest {

    @Test
    @DisplayName("reads the root rates and the per-class rates")
    void readsTheRates() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                CoberturaReportParser.parse(CoverageReportFixtures.path("cobertura-high.xml")));

        assertAll(
                () -> assertEquals(CoverageFormat.COBERTURA, report.format(), "The report names its format"),
                () -> assertEquals(90.0, report.overall().line(), "The root line rate is 0.90"),
                () -> assertEquals(87.5, report.overall().branch(), "The root branch rate is 0.875"),
                () -> assertNull(report.overall().instruction(), "Cobertura carries no instruction rate"),
                () -> assertEquals(2, report.items().size(), "The report carries two classes"),
                () -> assertEquals("service.py", report.items().getFirst().name(), "The first class is service.py"),
                () -> assertEquals("myapp/service.py", report.items().getFirst().file(),
                        "The class names its source file"),
                () -> assertEquals(95.0, report.items().getFirst().line(), "The class line rate is 0.95"),
                () -> assertEquals(90.0, report.items().getFirst().branch(), "The class branch rate is 0.9"),
                () -> assertEquals(85.0, report.items().get(1).line(), "The second class line rate is 0.85"));
    }

    @Test
    @DisplayName("names the methods with a zero line rate as missed")
    void namesTheMissedMethods() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                CoberturaReportParser.parse(CoverageReportFixtures.path("cobertura-low.xml")));

        CoverageItem legacy = report.items().getFirst();

        assertAll(
                () -> assertEquals(45.0, report.overall().line(), "The root line rate is 0.45"),
                () -> assertEquals(37.5, report.overall().branch(), "The root branch rate is 0.375"),
                () -> assertEquals(30.0, legacy.line(), "The legacy class line rate is 0.3"),
                () -> assertEquals(List.of("old_process"), legacy.missedMethods(), "The zero-rate method is named"),
                () -> assertEquals(List.of(), report.items().get(1).missedMethods(),
                        "A class without a zero-rate method names none"));
    }

    @Test
    @DisplayName("refuses a report that uses an external entity")
    void refusesAnExternalEntity() {
        CoverageParseResult result =
                CoberturaReportParser.parse(CoverageReportFixtures.path("deliberate-cobertura-external-entity.xml"));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "An external entity is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura"),
                () -> assertTrue(refused.reason().startsWith("cobertura: "), "The reason carries the format id"));
    }

    @Test
    @DisplayName("refuses a truncated report")
    void refusesATruncatedReport() {
        CoverageParseResult result =
                CoberturaReportParser.parse(CoverageReportFixtures.path("deliberate-cobertura-truncated.xml"));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A truncated report is refused");

        assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura");
    }

    @Test
    @DisplayName("refuses a document that is not a Cobertura report")
    void refusesANonReportDocument() {
        CoverageParseResult result = CoberturaReportParser.parse("<report name=\"other\"/>");

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "Another root element is refused");

        assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura");
    }

    @Test
    @DisplayName("refuses a root without a line rate")
    void refusesARootWithoutLineRate() {
        CoverageParseResult result = CoberturaReportParser.parse("""
                <coverage version="7.4.0" branch-rate="0.5"/>
                """);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A missing line rate is refused");

        assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura");
    }

    @Test
    @DisplayName("refuses an unreadable report file")
    void refusesAnUnreadableFile() {
        CoverageParseResult result = CoberturaReportParser
                .parse(CoverageReportFixtures.path("cobertura-high.xml").resolveSibling("absent.xml"));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A missing report file is refused");

        assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura");
    }
}
