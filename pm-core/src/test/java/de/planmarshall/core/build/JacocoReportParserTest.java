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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The ported tests of {@link JacocoReportParser}: each case reads one plain-file fixture below {@code ported/coverage},
 * whose expected values are read by hand from the original and its pytest case ({@code PROVENANCE.adoc}).
 *
 * @since 0.1
 */
@DisplayName("JacocoReportParser")
class JacocoReportParserTest {

    @Test
    @DisplayName("reads the overall counters of a fully covered report")
    void readsTheOverallCounters() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                JacocoReportParser.parse(CoverageReportFixtures.path("jacoco-high.xml")));

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, report.format(), "The report names its format"),
                () -> assertEquals(100.0, report.overall().line(), "The line counter is fully covered"),
                () -> assertEquals(100.0, report.overall().branch(), "The branch counter is fully covered"),
                () -> assertEquals(90.0, report.overall().instruction(), "The instruction counter is 18 of 20"),
                () -> assertEquals(100.0, report.overall().function(), "The method counter is fully covered"),
                () -> assertNull(report.overall().statement(), "JaCoCo carries no statement counter"));
    }

    @Test
    @DisplayName("reads the per-class counter and the missed methods")
    void readsTheClassCounterAndMissedMethods() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                JacocoReportParser.parse(CoverageReportFixtures.path("jacoco-low.xml")));

        CoverageItem item = report.items().getFirst();

        assertAll(
                () -> assertEquals(22.22, report.overall().line(), "The overall line percentage is 2 of 9"),
                () -> assertEquals(25.0, report.overall().branch(), "The overall branch percentage is 1 of 4"),
                () -> assertEquals(21.74, report.overall().instruction(), "The instruction percentage is 5 of 23"),
                () -> assertEquals(50.0, report.overall().function(), "The method percentage is 1 of 2"),
                () -> assertEquals(1, report.items().size(), "The report carries one class"),
                () -> assertEquals("com.example.LegacyService", item.name(), "The class name is dotted"),
                () -> assertEquals(22.22, item.line(), "The class line percentage is 2 of 9"),
                () -> assertNull(item.file(), "A JaCoCo class names no separate file"),
                () -> assertEquals(List.of("cleanup"), item.missedMethods(), "The missed method is cleanup"));
    }

    @Test
    @DisplayName("leaves the constructors out of the missed methods")
    void leavesTheConstructorsOut() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser
                .parse(CoverageReportFixtures.path("deliberate-jacoco-init-excluded.xml")));

        assertAll(
                () -> assertEquals(1, report.items().size(), "The report carries one class"),
                () -> assertEquals(List.of("run"), report.items().getFirst().missedMethods(),
                        "Only the real missed method is named"),
                () -> assertEquals(50.0, report.items().getFirst().line(), "The class line percentage is 1 of 2"));
    }

    @Test
    @DisplayName("passes the parsed fixture whose branch coverage is below")
    void passesTheParsedFixtureOnItsLine() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                JacocoReportParser.parse(CoverageReportFixtures.path("deliberate-branch-below-line-above.xml")));

        CoverageVerdict verdict = report.evaluate(80);

        assertAll(
                () -> assertEquals(33.33, report.overall().branch(), "The fixture branch is below 80"),
                () -> assertEquals(90.0, report.overall().line(), "The fixture line is above 80"),
                () -> assertTrue(verdict.passed(), "The parsed fixture passes on its line"),
                () -> assertTrue(verdict.lowCoverage().isEmpty(), "The parsed fixture lists no low item"));
    }

    @Test
    @DisplayName("fails the parsed fixture whose line coverage is below")
    void failsTheParsedFixtureOnItsLine() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                JacocoReportParser.parse(CoverageReportFixtures.path("deliberate-branch-above-line-below.xml")));

        CoverageVerdict verdict = report.evaluate(80);

        assertAll(
                () -> assertEquals(90.0, report.overall().branch(), "The fixture branch is above 80"),
                () -> assertEquals(40.0, report.overall().line(), "The fixture line is below 80"),
                () -> assertFalse(verdict.passed(), "The parsed fixture fails on its line"),
                () -> assertEquals(1, verdict.lowCoverage().size(), "The parsed fixture lists its low class"));
    }

    @Test
    @DisplayName("refuses a report that uses an external entity")
    void refusesAnExternalEntity() {
        CoverageParseResult result =
                JacocoReportParser.parse(CoverageReportFixtures.path("deliberate-jacoco-external-entity.xml"));

        CoverageParseResult.Refused refused =
                assertRefused(result, "An external entity is refused, not expanded");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertTrue(refused.reason().startsWith("jacoco: "), "The reason carries the format id"));
    }

    @Test
    @DisplayName("refuses a truncated report")
    void refusesATruncatedReport() {
        CoverageParseResult result =
                JacocoReportParser.parse(CoverageReportFixtures.path("deliberate-jacoco-truncated.xml"));

        CoverageParseResult.Refused refused = assertRefused(result, "A truncated report is refused, not zero");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    @Test
    @DisplayName("refuses an unreadable report file")
    void refusesAnUnreadableFile() {
        CoverageParseResult result =
                JacocoReportParser.parse(CoverageReportFixtures.path("jacoco-high.xml").resolveSibling("absent.xml"));

        CoverageParseResult.Refused refused = assertRefused(result, "A missing report file is refused");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    @Test
    @DisplayName("refuses an entity that is referenced in the element content")
    void refusesAContentEntity() {
        CoverageParseResult result = JacocoReportParser
                .parse(CoverageReportFixtures.path("deliberate-jacoco-external-entity-content.xml"));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A content entity is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertTrue(refused.reason().contains("internal subset"),
                        "The reason names the refused internal subset"));
    }

    @Test
    @DisplayName("keeps the metrics the report does not carry absent")
    void keepsTheAbsentMetricsNull() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser.parse("""
                <report name="line-only">
                  <counter type="LINE" missed="1" covered="1"/>
                </report>
                """));

        assertAll(
                () -> assertEquals(50.0, report.overall().line(), "The line counter is 1 of 2"),
                () -> assertNull(report.overall().instruction(), "An absent instruction counter is null"),
                () -> assertNull(report.overall().branch(), "An absent branch counter is null"),
                () -> assertNull(report.overall().function(), "An absent method counter is null"));
    }

    @Test
    @DisplayName("refuses a document that is not a JaCoCo report")
    void refusesANonReportDocument() {
        CoverageParseResult result =
                JacocoReportParser.parse("<coverage line-rate=\"0.5\" branch-rate=\"0.5\"/>");

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "Another root element is refused");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    @Test
    @DisplayName("refuses a report without a line counter")
    void refusesAReportWithoutLineCounter() {
        CoverageParseResult result = JacocoReportParser.parse("<report name=\"empty\"/>");

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A report without a LINE counter is refused");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    @Test
    @DisplayName("refuses a negative counter")
    void refusesANegativeCounter() {
        CoverageParseResult result = JacocoReportParser.parse("""
                <report name="negative">
                  <counter type="LINE" missed="-1" covered="1"/>
                </report>
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A negative counter is refused");

        assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo");
    }

    @Test
    @DisplayName("leaves a class without a line counter out of the measured items")
    void skipsAClassWithoutLineCounter() {
        CoverageReport report = CoverageReportFixtures.reportOf(JacocoReportParser.parse("""
                <report name="class-lines">
                  <counter type="LINE" missed="0" covered="1"/>
                  <package name="p">
                    <class name="p/A"/>
                  </package>
                </report>
                """));

        assertAll(
                () -> assertEquals(100.0, report.overall().line(), "The overall line stays measured"),
                () -> assertTrue(report.items().isEmpty(), "The unmeasured class is not listed as an item"));
    }

    @Test
    @DisplayName("refuses an internal subset behind a comment that mentions a DOCTYPE")
    void refusesAnInternalSubsetBehindAComment() {
        CoverageParseResult result = JacocoReportParser.parse("""
                <?xml version="1.0"?>
                <!-- <!DOCTYPE fake > -->
                <!DOCTYPE report [<!ENTITY probe SYSTEM "file:///nonexistent">]>
                <report name="a"><counter type="LINE" missed="0" covered="1"/></report>
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "The real DOCTYPE's internal subset is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.JACOCO, refused.format(), "The refusal names JaCoCo"),
                () -> assertTrue(refused.reason().contains("internal subset"),
                        "The reason names the refused internal subset"));
    }

    private static CoverageParseResult.Refused assertRefused(CoverageParseResult result, String message) {
        return assertInstanceOf(CoverageParseResult.Refused.class, result, message);
    }
}
