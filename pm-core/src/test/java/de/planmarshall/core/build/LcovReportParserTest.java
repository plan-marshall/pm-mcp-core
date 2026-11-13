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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The ported tests of {@link LcovReportParser}: each case reads one plain-file fixture below {@code ported/coverage},
 * whose expected values are read by hand from the original and its pytest case ({@code PROVENANCE.adoc}).
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("LcovReportParser")
class LcovReportParserTest {

    /** A record of eight lines hit out of ten; the counters are the contract. */
    private static final String MEASURED_RECORD = """
            SF:src/a.js
            LF:10
            LH:8
            end_of_record
            """;

    @TempDir
    Path directory;

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
    @DisplayName("refuses a hit counter without its found counter")
    void refusesAHitWithoutFound() {
        CoverageParseResult branch = LcovReportParser.parse("""
                SF:src/a.js
                LF:10
                LH:8
                BRH:0
                end_of_record
                """);
        CoverageParseResult function = LcovReportParser.parse("""
                SF:src/a.js
                LF:10
                LH:8
                FNH:0
                end_of_record
                """);
        CoverageParseResult functionFound = LcovReportParser.parse("""
                SF:src/a.js
                LF:10
                LH:8
                FNF:2
                end_of_record
                """);

        assertAll(
                () -> assertEquals(CoverageFormat.LCOV,
                        assertInstanceOf(CoverageParseResult.Refused.class, branch, "BRH alone is refused").format(),
                        "The refusal names LCOV"),
                () -> assertEquals(CoverageFormat.LCOV,
                        assertInstanceOf(CoverageParseResult.Refused.class, function, "FNH alone is refused").format(),
                        "The refusal names LCOV"),
                () -> assertEquals(CoverageFormat.LCOV,
                        assertInstanceOf(CoverageParseResult.Refused.class, functionFound, "FNF alone is refused").format(),
                        "The refusal names LCOV"));
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
    @DisplayName("cuts a hostile long SF: name to the echo bound in the reason")
    void cutsAHostileFileNameInTheReason() {
        String name = Generators.letterStrings(2_000, 4_000).next();

        CoverageParseResult result = LcovReportParser.parse("SF:" + name + "\nLF:1\n");

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A report ending inside a record is refused");
        String detail = refused.reason().substring("lcov: ".length());
        assertAll(
                () -> assertTrue(refused.reason().startsWith("lcov: the report ends inside the record "),
                        "The reason keeps its prefix and its sentence"),
                () -> assertEquals(CoverageRefusals.MAX_DETAIL_LENGTH, detail.length(),
                        "The echoed detail is cut to the echo bound"),
                () -> assertFalse(refused.reason().contains(name), "The whole hostile name is never echoed"));
    }

    // The limits are the contract edge: a limit of Integer.MAX_VALUE plus the carriage return overflows an int.
    @Test
    @DisplayName("compares the line length at the int edge without overflow")
    void comparesTheLineLengthAtTheIntEdge() {
        int limit = Generators.integers(1, 10_000).next();

        assertAll(
                () -> assertFalse(LcovReportParser.pastTheReadLimit(Integer.MAX_VALUE, Integer.MAX_VALUE),
                        "A line at a limit of Integer.MAX_VALUE is in range"),
                () -> assertFalse(LcovReportParser.pastTheReadLimit(limit + 1, limit),
                        "The carriage return may follow a full line"),
                () -> assertTrue(LcovReportParser.pastTheReadLimit(limit + 2, limit),
                        "Two characters past the limit are refused"));
    }

    // The counters are the contract: every record found no line, so the summed LF is 0.
    @Test
    @DisplayName("refuses a report whose summed LF is 0")
    void refusesAReportWithoutMeasuredLines() {
        CoverageParseResult result = LcovReportParser.parse("""
                SF:src/a.js
                LF:0
                LH:0
                end_of_record
                """);

        CoverageParseResult.Refused refused = assertInstanceOf(CoverageParseResult.Refused.class, result,
                "A report with no measured line is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV"),
                () -> assertEquals("lcov: no lines measured", refused.reason(), "The reason says no line measured"));
    }

    // The counters are the contract: a record with LF:0 measured no line and is no item.
    @Test
    @DisplayName("omits a record whose LF is 0")
    void omitsARecordWithoutMeasuredLines() {
        CoverageReport report = CoverageReportFixtures.reportOf(LcovReportParser.parse("""
                SF:src/empty.js
                LF:0
                LH:0
                end_of_record
                SF:src/a.js
                LF:10
                LH:8
                end_of_record
                """));

        assertAll(
                () -> assertEquals(1, report.items().size(), "Only the measured record is an item"),
                () -> assertEquals("src/a.js", report.items().getFirst().name(), "The measured record is kept"),
                () -> assertEquals(80.0, report.overall().line(), "The overall line is eight of ten"));
    }

    // The counters are the contract: BRF:0 and FNF:0 measured no branch and no function.
    @Test
    @DisplayName("keeps a branch and function metric of BRF:0 and FNF:0 absent")
    void keepsTheEmptyBranchAndFunctionAbsent() {
        CoverageReport report = CoverageReportFixtures.reportOf(LcovReportParser.parse("""
                SF:src/a.js
                LF:10
                LH:8
                BRF:0
                BRH:0
                FNF:0
                FNH:0
                end_of_record
                """));

        assertAll(
                () -> assertNull(report.overall().branch(), "An overall BRF of 0 has no branch metric"),
                () -> assertNull(report.overall().function(), "An overall FNF of 0 has no function metric"),
                () -> assertNull(report.items().getFirst().branch(), "A record BRF of 0 has no branch"),
                () -> assertEquals(80.0, report.overall().statement(), "The statement equals the line"));
    }

    // The reason text "absent.info: no such file" is the contract: the file name, never the directory.
    @Test
    @DisplayName("refuses an unreadable report file, naming its file name only")
    void refusesAnUnreadableFile() {
        Path absent = CoverageReportFixtures.path("sample.lcov").resolveSibling("absent.info");

        CoverageParseResult result = LcovReportParser.parse(absent);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A missing report file is refused");
        assertAll(
                () -> assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV"),
                () -> assertEquals("lcov: absent.info: no such file", refused.reason(),
                        "The reason names the file name only"),
                () -> assertFalse(refused.reason().contains(absent.getParent().toString()),
                        "The reason never names the directory"));
    }

    // The line limit of 8192 characters is the contract.
    @Test
    @DisplayName("refuses a line longer than 8192 characters, naming the limit")
    void refusesALineOverTheLimit() {
        CoverageParseResult result = LcovReportParser.parse(withTestName(8190) + MEASURED_RECORD);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A line over the limit is refused");
        assertEquals("lcov: a line is longer than the limit of 8192 characters", refused.reason(),
                "The reason names the line limit");
    }

    // The line limit of 8192 characters is the contract.
    @Test
    @DisplayName("reads a line of exactly 8192 characters, also before a CRLF")
    void readsALineAtTheLimit() {
        CoverageReport unix = CoverageReportFixtures.reportOf(
                LcovReportParser.parse(withTestName(8189) + MEASURED_RECORD));
        CoverageReport windows = CoverageReportFixtures.reportOf(LcovReportParser.parse(
                (withTestName(8189) + MEASURED_RECORD).replace("\n", "\r\n")));

        assertAll(
                () -> assertEquals(80.0, unix.overall().line(), "A line at the limit is read"),
                () -> assertEquals(80.0, windows.overall().line(), "A CRLF line at the limit is read"));
    }

    // The line limit of 8192 characters is the contract.
    @Test
    @DisplayName("refuses a line over 8192 characters in a report file")
    void refusesALineOverTheLimitInAFile() throws Exception {
        Path reportFile = Files.writeString(directory.resolve("long.info"), withTestName(20_000) + MEASURED_RECORD);

        CoverageParseResult result = LcovReportParser.parse(reportFile);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A long line in a file is refused");
        assertTrue(refused.reason().contains("limit of 8192 characters"), "The reason names the line limit");
    }

    @Test
    @DisplayName("reads a report file without a trailing line break unchanged")
    void readsAReportFileWithoutATrailingLineBreak() throws Exception {
        Path reportFile = Files.writeString(directory.resolve("plain.info"), MEASURED_RECORD.strip());

        CoverageReport report = CoverageReportFixtures.reportOf(LcovReportParser.parse(reportFile));

        assertEquals(80.0, report.overall().line(), "The last line without a break is read");
    }

    // The counters are the contract: a second LH line would turn a 0-hit record into a fully covered one.
    @Test
    @DisplayName("refuses a record that carries the LH line twice")
    void refusesARepeatedLinesHit() {
        CoverageParseResult result = LcovReportParser.parse("""
                SF:src/a.js
                LF:10
                LH:0
                LH:10
                end_of_record
                """);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A repeated LH line is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV"),
                () -> assertEquals("lcov: the record src/a.js carries the LH line twice", refused.reason(),
                        "The reason names the repeated LH line"));
    }

    // The counters are the contract: a second LF line would replace the record's found count.
    @Test
    @DisplayName("refuses a record that carries the LF line twice")
    void refusesARepeatedLinesFound() {
        CoverageParseResult result = LcovReportParser.parse("""
                SF:src/a.js
                LF:10
                LF:20
                LH:8
                end_of_record
                """);

        CoverageParseResult.Refused refused =
                assertInstanceOf(CoverageParseResult.Refused.class, result, "A repeated LF line is refused");

        assertAll(
                () -> assertEquals(CoverageFormat.LCOV, refused.format(), "The refusal names LCOV"),
                () -> assertEquals("lcov: the record src/a.js carries the LF line twice", refused.reason(),
                        "The reason names the repeated LF line"));
    }

    // The counters are the contract: each counter line once per record, also across two records.
    @Test
    @DisplayName("reads records that carry each counter line once")
    void readsEachCounterLineOnce() {
        CoverageReport report = CoverageReportFixtures.reportOf(LcovReportParser.parse("""
                SF:src/a.js
                FNF:2
                FNH:1
                LF:10
                LH:8
                BRF:4
                BRH:2
                end_of_record
                SF:src/b.js
                FNF:2
                FNH:1
                LF:10
                LH:8
                BRF:4
                BRH:2
                end_of_record
                """));

        assertAll(
                () -> assertEquals(80.0, report.overall().line(), "The overall line is 16 of 20"),
                () -> assertEquals(50.0, report.overall().branch(), "The overall branch is 4 of 8"),
                () -> assertEquals(50.0, report.overall().function(), "The overall function is 2 of 4"),
                () -> assertEquals(2, report.items().size(), "Both records are items"));
    }

    /**
     * Returns a {@code TN:} line whose content after the prefix has the given length, so the line is three longer.
     *
     * @param length the length of the test name
     * @return the line with its line break
     */
    private static String withTestName(int length) {
        return "TN:" + Generators.letterStrings(length, length).next() + "\n";
    }
}
