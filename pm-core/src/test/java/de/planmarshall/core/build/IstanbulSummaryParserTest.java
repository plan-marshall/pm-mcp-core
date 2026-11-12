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

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The ported tests of {@link IstanbulSummaryParser}: each case reads one plain-file fixture below {@code ported/coverage},
 * whose expected values are read by hand from the original and its pytest case ({@code PROVENANCE.adoc}).
 *
 * @since 0.1
 */
@DisplayName("IstanbulSummaryParser")
class IstanbulSummaryParserTest {

    @Test
    @DisplayName("reads the total percentages of a fully covered summary")
    void readsTheTotalPercentages() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                IstanbulSummaryParser.parse(CoverageReportFixtures.path("jest-high.json")));

        assertAll(
                () -> assertEquals(CoverageFormat.ISTANBUL, report.format(), "The report names its format"),
                () -> assertEquals(90.0, report.overall().line(), "The total lines.pct is 90"),
                () -> assertEquals(84.0, report.overall().branch(), "The total branches.pct is 84"),
                () -> assertEquals(90.0, report.overall().function(), "The total functions.pct is 90"),
                () -> assertEquals(90.0, report.overall().statement(), "The total statements.pct is 90"),
                () -> assertNull(report.overall().instruction(), "Istanbul carries no instruction percentage"));
    }

    @Test
    @DisplayName("reads one item per file beside the total")
    void readsOneItemPerFile() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                IstanbulSummaryParser.parse(CoverageReportFixtures.path("jest-high.json")));

        assertAll(
                () -> assertEquals(2, report.items().size(), "The summary carries two files"),
                () -> assertEquals("/src/utils/helper.js", report.items().getFirst().name(), "The file is named"),
                () -> assertEquals(95.0, report.items().getFirst().line(), "The file lines.pct is 95"),
                () -> assertEquals(88.0, report.items().getFirst().branch(), "The file branches.pct is 88"),
                () -> assertEquals("/src/utils/format.js", report.items().get(1).name(), "The second file is named"),
                () -> assertEquals(85.0, report.items().get(1).line(), "The second file lines.pct is 85"),
                () -> assertEquals(80.0, report.items().get(1).branch(), "The second file branches.pct is 80"),
                () -> assertEquals(List.of(), report.items().getFirst().missedMethods(),
                        "Istanbul names no missed method"));
    }

    @Test
    @DisplayName("reads the low summary with its two files")
    void readsTheLowSummary() {
        CoverageReport report = CoverageReportFixtures.reportOf(
                IstanbulSummaryParser.parse(CoverageReportFixtures.path("jest-low.json")));

        assertAll(
                () -> assertEquals(50.0, report.overall().line(), "The total lines.pct is 50"),
                () -> assertEquals(40.0, report.overall().branch(), "The total branches.pct is 40"),
                () -> assertEquals(40.0, report.items().getFirst().line(), "Widget.js is 40 percent"),
                () -> assertEquals(32.0, report.items().getFirst().branch(), "Widget.js branches are 32"),
                () -> assertEquals(60.0, report.items().get(1).line(), "api.js is 60 percent"),
                () -> assertEquals(48.0, report.items().get(1).branch(), "api.js branches are 48"));
    }

    @Test
    @DisplayName("refuses a truncated document")
    void refusesATruncatedDocument() {
        CoverageParseResult result =
                IstanbulSummaryParser.parse(CoverageReportFixtures.path("deliberate-istanbul-malformed.json"));

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A truncated document is refused");

        assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names Istanbul");
    }

    // The JSON documents and the reason fragments are the contract: every metric object carries a total, so each
    // document is refused for the rule its name states and not because a total is absent.
    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedDocuments")
    @DisplayName("refuses a malformed or incomplete document")
    void refusesAMalformedOrIncompleteDocument(String name, String json, String expectedReason) {
        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, IstanbulSummaryParser.parse(json), name);

        assertAll(
                () -> assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names Istanbul"),
                () -> assertTrue(refused.reason().contains(expectedReason),
                        "The reason names the rule the document breaks"));
    }

    static Stream<Arguments> malformedDocuments() {
        return Stream.of(
                Arguments.of("A missing total is refused",
                        "{\"src/a.js\": {\"lines\": {\"total\": 10, \"pct\": 90}}}",
                        "the document carries no total"),
                Arguments.of("A non-object root is refused", "[]", "the document root is not an object"),
                Arguments.of("A string pct is refused", """
                        {"total": {"lines": {"total": 10, "pct": "90"}, "statements": {"total": 10, "pct": 90},
                         "functions": {"total": 10, "pct": 90}, "branches": {"total": 10, "pct": 90}}}
                        """, "total.lines.pct is not a number"),
                Arguments.of("A branches object without pct is refused", """
                        {"total": {"lines": {"total": 10, "pct": 90}, "statements": {"total": 10, "pct": 90},
                         "functions": {"total": 10, "pct": 90}, "branches": {"total": 10, "pct": 90}},
                         "src/a.js": {"lines": {"total": 10, "pct": 90}, "branches": {"total": 10}}}
                        """, "src/a.js.branches.pct is absent"),
                Arguments.of("Content after the document is refused", """
                        {"total": {"lines": {"total": 10, "pct": 90}, "statements": {"total": 10, "pct": 90},
                         "functions": {"total": 10, "pct": 90}, "branches": {"total": 10, "pct": 90}}}
                        {}
                        """, "content after the document"),
                Arguments.of("A duplicate total key is refused", """
                        {"total": {"lines": {"total": 10, "pct": 10}, "statements": {"total": 10, "pct": 10},
                         "functions": {"total": 10, "pct": 10}, "branches": {"total": 10, "pct": 10}},
                         "total": {"lines": {"total": 10, "pct": 90}, "statements": {"total": 10, "pct": 90},
                         "functions": {"total": 10, "pct": 90}, "branches": {"total": 10, "pct": 90}}}
                        """, "Duplicate field 'total'"),
                Arguments.of("A duplicate metric total is refused", """
                        {"total": {"lines": {"total": 0, "total": 10, "pct": 90}, "statements": {"pct": 90},
                         "functions": {"pct": 90}, "branches": {"pct": 90}}}
                        """, "Duplicate field 'total'"));
    }

    // The keys are the contract: a second "total" must never override the first one.
    @Test
    @DisplayName("refuses a duplicate key, naming it in the reason")
    void refusesADuplicateKey() {
        CoverageParseResult result = IstanbulSummaryParser.parse("""
                {"total": {"lines": {"total": 10, "pct": 10}, "statements": {"total": 10, "pct": 10},
                 "functions": {"total": 10, "pct": 10}, "branches": {"total": 10, "pct": 10}},
                 "total": {"lines": {"total": 10, "pct": 90}, "statements": {"total": 10, "pct": 90},
                 "functions": {"total": 10, "pct": 90}, "branches": {"total": 10, "pct": 90}}}
                """);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A duplicate key is refused");
        assertTrue(refused.reason().contains("Duplicate field 'total'"), "The reason names the duplicate key");
    }

    // The document and the reason text are the contract: a pct without a total measured nothing that can be counted.
    @Test
    @DisplayName("refuses an overall metric object that carries a pct but no total")
    void refusesAnOverallMetricWithoutTotal() {
        CoverageParseResult result = IstanbulSummaryParser.parse("{\"total\":{\"lines\":{\"pct\":100}}}");

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A metric object without total is refused");
        assertAll(
                () -> assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names Istanbul"),
                () -> assertEquals("istanbul: total.lines.total is absent", refused.reason(),
                        "The reason names the missing metric total"));
    }

    // The document and the reason text are the contract: a file metric is never read from its pct alone either.
    @Test
    @DisplayName("refuses a file metric object that carries a pct but no total")
    void refusesAFileMetricWithoutTotal() {
        CoverageParseResult result = IstanbulSummaryParser.parse("""
                {"total": {"lines": {"total": 10, "pct": 90}, "statements": {"total": 10, "pct": 90},
                 "functions": {"total": 10, "pct": 90}, "branches": {"total": 10, "pct": 90}},
                 "src/a.js": {"lines": {"pct": 100}}}
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A file metric without total is refused");
        assertAll(
                () -> assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names Istanbul"),
                () -> assertEquals("istanbul: src/a.js.lines.total is absent", refused.reason(),
                        "The reason names the missing file metric total"));
    }

    // The totals are the contract: total.lines.total 0 measured no line, whatever pct Istanbul printed beside it.
    @Test
    @DisplayName("refuses a summary whose total.lines.total is 0")
    void refusesASummaryWithoutMeasuredLines() {
        CoverageParseResult result = IstanbulSummaryParser.parse("""
                {"total": {"lines": {"total": 0, "covered": 0, "pct": "Unknown"},
                 "statements": {"total": 0, "covered": 0, "pct": 100},
                 "functions": {"total": 0, "covered": 0, "pct": 100},
                 "branches": {"total": 0, "covered": 0, "pct": 100}}}
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A summary with no measured line is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names Istanbul"),
                () -> assertEquals("istanbul: no lines measured", refused.reason(), "The reason says no line measured"));
    }

    // The totals are the contract: a file with lines.total 0 is no item, a branches.total of 0 is no branch metric.
    @Test
    @DisplayName("omits a file whose lines.total is 0 and keeps a branches.total of 0 absent")
    void omitsAFileWithoutMeasuredLinesAndKeepsEmptyBranchesAbsent() {
        CoverageReport report = CoverageReportFixtures.reportOf(IstanbulSummaryParser.parse("""
                {"total": {"lines": {"total": 10, "covered": 8, "pct": 80},
                 "statements": {"total": 10, "covered": 8, "pct": 80},
                 "functions": {"total": 2, "covered": 1, "pct": 50},
                 "branches": {"total": 0, "covered": 0, "pct": 100}},
                 "src/empty.js": {"lines": {"total": 0, "covered": 0, "pct": 100},
                  "branches": {"total": 0, "covered": 0, "pct": 100}},
                 "src/a.js": {"lines": {"total": 10, "covered": 8, "pct": 80},
                  "branches": {"total": 0, "covered": 0, "pct": "Unknown"}}}
                """));

        assertAll(
                () -> assertEquals(80.0, report.overall().line(), "The total lines.pct is 80"),
                () -> assertNull(report.overall().branch(), "A total branches.total of 0 is null"),
                () -> assertEquals(1, report.items().size(), "Only the measured file is an item"),
                () -> assertEquals("src/a.js", report.items().getFirst().name(), "The measured file is kept"),
                () -> assertNull(report.items().getFirst().branch(), "A file branches.total of 0 is null"));
    }

    // The reason text "absent.json: no such file" is the contract: the file name, never the directory.
    @Test
    @DisplayName("refuses an unreadable report file, naming its file name only")
    void refusesAnUnreadableFile() {
        Path absent = CoverageReportFixtures.path("jest-high.json").resolveSibling("absent.json");

        CoverageParseResult result = IstanbulSummaryParser.parse(absent);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A missing report file is refused");
        assertAll(
                () -> assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names Istanbul"),
                () -> assertEquals("istanbul: absent.json: no such file", refused.reason(),
                        "The reason names the file name only"),
                () -> assertFalse(refused.reason().contains(absent.getParent().toString()),
                        "The reason never names the directory"));
    }
}
