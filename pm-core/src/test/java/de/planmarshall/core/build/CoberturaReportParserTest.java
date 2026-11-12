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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
                // The reason text of the entity-reference backstop is the contract.
                () -> assertEquals("cobertura: entity reference refused", refused.reason(),
                        "The parser refuses the entity reference"));
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
    @DisplayName("refuses a method line rate outside the rate range")
    void refusesAnOutOfRangeMethodRate() {
        CoverageParseResult result = CoberturaReportParser.parse("""
                <coverage line-rate="0.5" branch-rate="0.5">
                  <packages><package name="p"><classes>
                    <class name="a" filename="a" line-rate="0.5">
                      <methods><method name="m" line-rate="1.5"/></methods>
                    </class>
                  </classes></package></packages>
                </coverage>
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A method rate above 1 is refused");

        assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura");
    }

    // The root attributes are the contract: lines-valid="0" measured no line.
    @Test
    @DisplayName("refuses a root whose lines-valid is 0")
    void refusesARootWithoutMeasuredLines() {
        CoverageParseResult result = CoberturaReportParser.parse("""
                <coverage lines-valid="0" line-rate="0" branches-valid="2" branch-rate="0.5"/>
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A root with no measured line is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura"),
                () -> assertEquals("cobertura: no lines measured", refused.reason(), "The reason says no line measured"));
    }

    // The root attribute is the contract: branches-valid="0" and no branches-valid both measured no branch.
    @ParameterizedTest
    @ValueSource(strings = {"branches-valid=\"0\"", ""})
    @DisplayName("keeps every branch metric absent under a root that measured no branch")
    void keepsTheBranchesAbsentWithoutMeasuredBranches(String branchesValid) {
        CoverageReport report = CoverageReportFixtures.reportOf(CoberturaReportParser.parse("""
                <coverage lines-valid="2" line-rate="0.5" %s branch-rate="0">
                  <packages><package name="p"><classes>
                    <class name="a" filename="a" line-rate="0.5" branch-rate="0"/>
                  </classes></package></packages>
                </coverage>
                """.formatted(branchesValid)));

        assertAll(
                () -> assertEquals(50.0, report.overall().line(), "The root line rate is 0.5"),
                () -> assertNull(report.overall().branch(), "The overall branch metric is null"),
                () -> assertNull(report.items().getFirst().branch(), "The class branch metric is null"));
    }

    // The root attribute is the contract: under branches-valid="4" a branch-rate of 0 measured four misses.
    @Test
    @DisplayName("reads a branch rate of 0 under a root that measured branches as a measured zero")
    void readsAZeroBranchRateAsMeasured() {
        CoverageReport report = CoverageReportFixtures.reportOf(CoberturaReportParser.parse("""
                <coverage lines-valid="2" line-rate="0.5" branches-valid="4" branch-rate="0">
                  <packages><package name="p"><classes>
                    <class name="a" filename="a" line-rate="0.5" branch-rate="0"/>
                  </classes></package></packages>
                </coverage>
                """));

        assertAll(
                () -> assertEquals(0.0, report.overall().branch(), "The overall branch is a measured zero"),
                () -> assertEquals(0.0, report.items().getFirst().branch(), "The class branch is a measured zero"));
    }

    // The rate texts are the contract: each is a double literal but not a plain decimal number.
    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "0x1p-1"})
    @DisplayName("refuses a rate that is not a plain decimal number")
    void refusesANonDecimalRate(String rate) {
        CoverageParseResult result = CoberturaReportParser.parse("""
                <coverage lines-valid="2" line-rate="%s"/>
                """.formatted(rate));

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A non-decimal rate is refused");

        assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura");
    }

    // The reason text "absent.xml: no such file" is the contract: the file name, never the directory.
    @Test
    @DisplayName("refuses an unreadable report file, naming its file name only")
    void refusesAnUnreadableFile() {
        Path absent = CoverageReportFixtures.path("cobertura-high.xml").resolveSibling("absent.xml");

        CoverageParseResult result = CoberturaReportParser.parse(absent);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A missing report file is refused");
        assertAll(
                () -> assertEquals(CoverageFormat.COBERTURA, refused.format(), "The refusal names Cobertura"),
                () -> assertEquals("cobertura: absent.xml: no such file", refused.reason(),
                        "The reason names the file name only"),
                () -> assertFalse(refused.reason().contains(absent.getParent().toString()),
                        "The reason never names the directory"));
    }
}
