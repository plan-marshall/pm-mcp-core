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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * The native LCOV coverage report parser ({@code lcov.info}).
 * <p>
 * The parser reads the line-oriented per-file records from {@code SF:} to {@code end_of_record} and sums the counters of
 * every record into the overall percentages: {@code line} from {@code LH/LF}, {@code branch} from {@code BRH/BRF} and
 * {@code function} from {@code FNH/FNF}, with {@code statement} equal to {@code line} because LCOV carries no statement
 * counter. Each record is one item, with its own line and branch percentages. A {@code DA:} record is per-line detail and
 * never enters a percentage.
 * <p>
 * A report that ends inside a record, carries no record at all or prints a counter that is not a number is refused,
 * naming the format, rather than read as a report of zeros.
 *
 * @since 0.1
 */
@UtilityClass
public class LcovReportParser {

    private static final CoverageFormat FORMAT = CoverageFormat.LCOV;

    /**
     * Parses an LCOV report from its text.
     *
     * @param report the report text
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(String report) {
        Objects.requireNonNull(report, "report");
        try {
            return parseRecords(report);
        } catch (IOException e) {
            return refusal(e);
        }
    }

    /**
     * Parses an LCOV report from a file.
     *
     * @param reportFile the report file
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(Path reportFile) {
        Objects.requireNonNull(reportFile, "reportFile");
        try {
            return parseRecords(Files.readString(reportFile));
        } catch (IOException e) {
            return refusal(e);
        }
    }

    private static CoverageParseResult parseRecords(String report) throws IOException {
        List<CoverageItem> items = new ArrayList<>();
        long linesFound = 0;
        long linesHit = 0;
        long branchesFound = 0;
        long branchesHit = 0;
        long functionsFound = 0;
        long functionsHit = 0;
        Record current = null;
        for (String raw : report.split("\n", -1)) {
            String line = raw.strip();
            if (line.startsWith("SF:")) {
                if (current != null) {
                    throw new IOException("the record " + current.file + " is not terminated by end_of_record");
                }
                current = new Record(line.substring(3).strip());
            } else if ("end_of_record".equals(line)) {
                if (current == null) {
                    throw new IOException("end_of_record appears without an SF record");
                }
                items.add(current.item());
                linesFound += current.linesFound;
                linesHit += current.linesHit;
                branchesFound += current.branchesFound;
                branchesHit += current.branchesHit;
                functionsFound += current.functionsFound;
                functionsHit += current.functionsHit;
                current = null;
            } else if (current != null) {
                readCounter(line, current);
            }
        }
        if (current != null) {
            throw new IOException("the report ends inside the record " + current.file);
        }
        if (items.isEmpty()) {
            throw new IOException("the report carries no file record");
        }
        double line = CoveragePercentages.percentage(linesHit, linesFound);
        CoverageMetrics overall = CoverageMetrics.builder()
                .line(line)
                .branch(CoveragePercentages.percentage(branchesHit, branchesFound))
                .function(CoveragePercentages.percentage(functionsHit, functionsFound))
                .statement(line)
                .build();
        return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, overall, items));
    }

    private static void readCounter(String line, Record current) throws IOException {
        if (line.startsWith("LF:")) {
            current.linesFound = number(line, 3);
            current.linesFoundSeen = true;
        } else if (line.startsWith("LH:")) {
            current.linesHit = number(line, 3);
            current.linesHitSeen = true;
        } else if (line.startsWith("BRF:")) {
            current.branchesFound = number(line, 4);
        } else if (line.startsWith("BRH:")) {
            current.branchesHit = number(line, 4);
        } else if (line.startsWith("FNF:")) {
            current.functionsFound = number(line, 4);
        } else if (line.startsWith("FNH:")) {
            current.functionsHit = number(line, 4);
        }
    }

    private static int number(String line, int prefixLength) throws IOException {
        try {
            return Integer.parseInt(line.substring(prefixLength).strip());
        } catch (NumberFormatException e) {
            throw new IOException("the counter " + line.substring(0, prefixLength) + " is not a number");
        }
    }

    private static CoverageParseResult refusal(Exception cause) {
        String message = cause.getMessage();
        String detail = message == null ? cause.getClass().getSimpleName() : message;
        return new CoverageParseResult.Refused(FORMAT, FORMAT.id() + ": " + detail);
    }

    /**
     * One file record of the report.
     */
    private static final class Record {

        private final String file;
        private int linesFound;
        private int linesHit;
        private boolean linesFoundSeen;
        private boolean linesHitSeen;
        private int branchesFound;
        private int branchesHit;
        private int functionsFound;
        private int functionsHit;

        private Record(String file) {
            this.file = file;
        }

        private CoverageItem item() throws IOException {
            if (file.isEmpty()) {
                throw new IOException("an SF record carries no file name");
            }
            if (!linesFoundSeen || !linesHitSeen) {
                throw new IOException("the record " + file + " carries no LF/LH counters");
            }
            return CoverageItem.builder()
                    .name(file)
                    .file(file)
                    .line(CoveragePercentages.percentage(linesHit, linesFound))
                    .branch(CoveragePercentages.percentage(branchesHit, branchesFound))
                    .missedMethods(List.of())
                    .build();
        }
    }
}
