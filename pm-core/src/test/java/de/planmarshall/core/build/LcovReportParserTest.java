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
 * The ported tests of {@link LcovReportParser}: each case reads one plain-file fixture below {@code ported/coverage},
 * whose expected values are read by hand from the original and its pytest case ({@code PROVENANCE.adoc}).
 *
 * @since 0.1
 */
@DisplayName("LcovReportParser")
class LcovReportParserTest {

    @Test
    @DisplayName("sums the file records into the overall percentages")
    void sumsTheFileRecords() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                LcovReportParser.parse(CoverageReportFixtures.path("sample.lcov")));

        assertAll(
                () -> assertEquals(CoverageFormat.LCOV, report.format(), "The report names its format"),
                () -> assertEquals(66.67, report.overall().line(), "Twenty of thirty lines is 66.67"),
                () -> assertEquals(61.11, report.overall().branch(), "Eleven of eighteen branches is 61.11"),
                () -> assertEquals(62.5, report.overall().function(), "Five of eight functions is 62.5"),
                () -> assertEquals(66.67, report.overall().statement(), "The statement equals the line"),
                () -> assertEquals(3, report.items().size(), "The report carries three files"));
    }

    @Test
    @DisplayName("reads the per-file line and branch percentages")
    void readsThePerFilePercentages() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                LcovReportParser.parse(CoverageReportFixtures.path("sample.lcov")));

        assertAll(
                () -> assertEquals("src/utils/formatter.js", report.items().getFirst().name(),
                        "The first file is named"),
                () -> assertEquals(80.0, report.items().getFirst().line(), "Eight of ten lines is 80"),
                () -> assertEquals(83.33, report.items().getFirst().branch(), "Five of six branches is 83.33"),
                () -> assertEquals(90.0, report.items().get(1).line(), "Nine of ten lines is 90"),
                () -> assertEquals(100.0, report.items().get(1).branch(), "Four of four branches is 100"),
                () -> assertEquals(30.0, report.items().get(2).line(), "Three of ten lines is 30"),
                () -> assertEquals(25.0, report.items().get(2).branch(), "Two of eight branches is 25"),
                () -> assertEquals(List.of(), report.items().getFirst().missedMethods(),
                        "LCOV names no missed method"));
    }

    @Test
    @DisplayName("never lets a DA record enter a percentage")
    void ignoresTheLineDetail() {
        String reportText = """
                SF:src/a.js
                LF:10
                LH:8
                BRF:4
                BRH:2
                DA:1,1
                DA:2,0
                DA:3,1
                end_of_record
                """;

        CoverageReport report = CoverageReportFixtures.reportOf(LcovReportParser.parse(reportText));

        assertAll(
                () -> assertEquals(80.0, report.overall().line(), "The DA records do not change the line"),
                () -> assertEquals(50.0, report.overall().branch(), "The DA records do not change the branch"),
                () -> assertEquals(80.0, report.items().getFirst().line(), "The file line stays eight of ten"));
    }

    @Test
    @DisplayName("refuses counters that disagree with each other")
    void refusesAnInconsistentCounter() {
        CoverageParseResult result =
                LcovReportParser.parse(CoverageReportFixtures.path("deliberate-lcov-inconsistent.info"));

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A covered count above the total is refused");

        assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV");
    }

    @Test
    @DisplayName("keeps an absent branch and function metric null")
    void keepsAnAbsentMetricNull() {
        CoverageReport report = CoverageReportFixtures.reportOf(LcovReportParser.parse("""
                SF:src/a.js
                LF:10
                LH:8
                end_of_record
                """));

        assertAll(
                () -> assertEquals(80.0, report.overall().line(), "The line percentage is eight of ten"),
                () -> assertNull(report.overall().branch(), "A report without BRF has no branch metric"),
                () -> assertNull(report.overall().function(), "A report without FNF has no function metric"),
                () -> assertEquals(80.0, report.overall().statement(), "The statement equals the line"),
                () -> assertNull(report.items().getFirst().branch(), "The record without BRF has no branch"));
    }

    @Test
    @DisplayName("refuses an incomplete branch counter pair")
    void refusesAnIncompleteBranchPair() {
        CoverageParseResult result = LcovReportParser.parse("""
                SF:src/a.js
                LF:10
                LH:8
                BRF:4
                end_of_record
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A BRF without BRH is refused");

        assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV");
    }

    @Test
    @DisplayName("refuses a report that ends inside a record")
    void refusesATruncatedReport() {
        CoverageParseResult result =
                LcovReportParser.parse(CoverageReportFixtures.path("deliberate-lcov-truncated.info"));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A truncated record is refused");

        assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV");
    }

    @Test
    @DisplayName("refuses a report without a file record")
    void refusesAReportWithoutRecord() {
        CoverageParseResult result = LcovReportParser.parse("TN:\n");

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "An empty report is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV"),
                () -> assertTrue(refused.reason().contains("lcov"), "The reason names the format"));
    }

    @Test
    @DisplayName("refuses an unreadable report file")
    void refusesAnUnreadableFile() {
        CoverageParseResult result =
                LcovReportParser.parse(CoverageReportFixtures.path("sample.lcov").resolveSibling("absent.info"));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A missing report file is refused");

        assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV");
    }
}
