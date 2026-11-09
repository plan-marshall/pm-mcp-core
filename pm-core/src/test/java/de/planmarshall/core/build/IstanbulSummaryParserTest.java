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

    @ParameterizedTest(name = "{0}")
    @MethodSource("malformedDocuments")
    @DisplayName("refuses a malformed or incomplete document")
    void refusesAMalformedOrIncompleteDocument(String reason, String json) {
        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, IstanbulSummaryParser.parse(json), reason);

        assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names Istanbul");
    }

    static Stream<Arguments> malformedDocuments() {
        return Stream.of(
                Arguments.of("A missing total is refused", "{\"src/a.js\": {\"lines\": {\"pct\": 90}}}"),
                Arguments.of("A non-object root is refused", "[]"),
                Arguments.of("A string pct is refused", """
                        {"total": {"lines": {"pct": "90"}, "statements": {"pct": 90},
                         "functions": {"pct": 90}, "branches": {"pct": 90}}}
                        """),
                Arguments.of("A branches object without pct is refused", """
                        {"total": {"lines": {"pct": 90}, "statements": {"pct": 90},
                         "functions": {"pct": 90}, "branches": {"pct": 90}},
                         "src/a.js": {"lines": {"pct": 90}, "branches": {}}}
                        """),
                Arguments.of("Content after the document is refused", """
                        {"total": {"lines": {"pct": 90}, "statements": {"pct": 90},
                         "functions": {"pct": 90}, "branches": {"pct": 90}}}
                        {}
                        """));
    }

    @Test
    @DisplayName("refuses an unreadable report file")
    void refusesAnUnreadableFile() {
        CoverageParseResult result = IstanbulSummaryParser
                .parse(CoverageReportFixtures.path("jest-high.json").resolveSibling("absent.json"));

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A missing report file is refused");

        assertEquals(CoverageFormat.ISTANBUL, refused.format(), "The refusal names Istanbul");
    }
}
