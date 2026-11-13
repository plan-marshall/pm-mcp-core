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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The native LCOV coverage report parser ({@code lcov.info}).
 * <p>
 * The parser reads the line-oriented per-file records from {@code SF:} to {@code end_of_record} and sums the counters of
 * every record into the overall percentages: {@code line} from {@code LH/LF}, {@code branch} from {@code BRH/BRF} and
 * {@code function} from {@code FNH/FNF}, with {@code statement} equal to {@code line} because LCOV carries no statement
 * counter. Each record is one item, with its own line and branch percentages. A {@code DA:} record is per-line detail and
 * never enters a percentage.
 * <p>
 * A zero denominator measured nothing, so it is absent rather than a {@code 0.0}: a record with {@code LF:0} is not an
 * item, a {@code BRF:0} or {@code FNF:0} leaves that metric absent overall or for the record, and a report whose summed
 * {@code LF} is {@code 0} is refused ({@code no lines measured}).
 * <p>
 * A report that ends inside a record, carries no record at all, prints a counter that is not a number or covers more than
 * it found is refused, naming the format, rather than read as a report of zeros. A record that carries one of the counter
 * lines {@code LF}, {@code LH}, {@code BRF}, {@code BRH}, {@code FNF} or {@code FNH} twice is refused, naming the
 * repeated line type, so a later line can never silently override an earlier one.
 * <p>
 * The report is read line by line, never whole: a file is streamed within the bound of {@link CoverageReportStream}, and
 * a line longer than {@link CoverageReportStream#MAX_LINE_LENGTH} characters is refused, naming the limit, before it is
 * read whole.
 *
 * @since 0.1
 */
@UtilityClass
public class LcovReportParser {

    private static final CoverageFormat FORMAT = CoverageFormat.LCOV;

    /** The refusal reason of a report whose summed {@code LH/LF} is {@code 0/0}. */
    private static final String NO_LINES_MEASURED = "no lines measured";

    /**
     * Parses an LCOV report from its text.
     *
     * @param report the report text
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(String report) {
        Objects.requireNonNull(report, "report");
        try (BufferedReader source = new BufferedReader(new StringReader(report))) {
            return new Walk().parse(source);
        } catch (IOException | IllegalArgumentException e) {
            return CoverageRefusals.of(FORMAT, e);
        }
    }

    /**
     * Parses an LCOV report from a file, streaming it as UTF-8 within the bound of {@link CoverageReportStream}.
     *
     * @param reportFile the report file
     * @return the parsed report, or a refusal naming the format and the reason; a file that cannot be read is refused
     *         naming its file name only
     */
    public static CoverageParseResult parse(Path reportFile) {
        Objects.requireNonNull(reportFile, "reportFile");
        try (BufferedReader source = new BufferedReader(new InputStreamReader(
                     new CoverageReportStream(Files.newInputStream(reportFile)), StandardCharsets.UTF_8.newDecoder()))) {
            return new Walk().parse(source);
        } catch (IOException e) {
            return CoverageRefusals.of(FORMAT, reportFile, e);
        } catch (IllegalArgumentException e) {
            return CoverageRefusals.of(FORMAT, e);
        }
    }

    /**
     * Whether a line being read is already past the line limit: a line may carry one character more than the limit while
     * it is read, its carriage return, which is dropped once its end is reached. The sum is taken as a {@code long}, so a
     * limit of {@link Integer#MAX_VALUE} does not overflow into a negative bound that refuses every line.
     *
     * @param length the characters read of the line so far
     * @param limit  the most characters a line may hold
     * @return whether the line is longer than the limit and its carriage return
     */
    static boolean pastTheReadLimit(int length, int limit) {
        return length > (long) limit + 1;
    }

    /**
     * The state of one walk over a report.
     */
    private static final class Walk {

        private final List<CoverageItem> items = new ArrayList<>();
        private int records;
        private long linesFound;
        private long linesHit;
        private long branchesFound;
        private long branchesHit;
        private long functionsFound;
        private long functionsHit;
        private boolean branchesSeen;
        private boolean functionsSeen;
        private @Nullable Record current;

        private CoverageParseResult parse(BufferedReader source) throws IOException {
            Optional<String> raw = nextLine(source);
            while (raw.isPresent()) {
                accept(raw.get().strip());
                raw = nextLine(source);
            }
            if (current != null) {
                throw new IOException("the report ends inside the record " + current.file);
            }
            if (records == 0) {
                throw new IOException("the report carries no file record");
            }
            return result();
        }

        /**
         * Reads the next line, without its line terminator, refusing one longer than
         * {@link CoverageReportStream#MAX_LINE_LENGTH} characters before it is read whole.
         *
         * @param source the report text
         * @return the line, or empty at the end of the text
         * @throws IOException if the line is longer than the limit, or the text cannot be read
         */
        private static Optional<String> nextLine(BufferedReader source) throws IOException {
            var line = new StringBuilder();
            int next = source.read();
            if (next < 0) {
                return Optional.empty();
            }
            while (next >= 0 && next != '\n') {
                line.append((char) next);
                if (pastTheReadLimit(line.length(), CoverageReportStream.MAX_LINE_LENGTH)) {
                    throw tooLong();
                }
                next = source.read();
            }
            if (!line.isEmpty() && line.charAt(line.length() - 1) == '\r') {
                line.setLength(line.length() - 1);
            }
            if (line.length() > CoverageReportStream.MAX_LINE_LENGTH) {
                throw tooLong();
            }
            return Optional.of(line.toString());
        }

        private static IOException tooLong() {
            return new IOException("a line is longer than the limit of " + CoverageReportStream.MAX_LINE_LENGTH
                    + " characters");
        }

        private void accept(String line) throws IOException {
            if (line.startsWith("SF:")) {
                start(line);
            } else if ("end_of_record".equals(line)) {
                end();
            } else if (current != null) {
                readCounter(line);
            }
        }

        private void start(String line) throws IOException {
            if (current != null) {
                throw new IOException("the record " + current.file + " is not terminated by end_of_record");
            }
            current = new Record(line.substring(3).strip());
        }

        private void end() throws IOException {
            if (current == null) {
                throw new IOException("end_of_record appears without an SF record");
            }
            current.item().ifPresent(items::add);
            records++;
            linesFound += current.linesFound;
            linesHit += current.linesHit;
            branchesSeen |= current.branchesFoundSeen || current.branchesHitSeen;
            branchesFound += current.branchesFound;
            branchesHit += current.branchesHit;
            functionsSeen |= current.functionsFoundSeen || current.functionsHitSeen;
            functionsFound += current.functionsFound;
            functionsHit += current.functionsHit;
            current = null;
        }

        private void readCounter(String line) throws IOException {
            if (line.startsWith("LF:")) {
                once(current.linesFoundSeen, "LF");
                current.linesFound = number(line, 3);
                current.linesFoundSeen = true;
            } else if (line.startsWith("LH:")) {
                once(current.linesHitSeen, "LH");
                current.linesHit = number(line, 3);
                current.linesHitSeen = true;
            } else if (line.startsWith("BRF:")) {
                once(current.branchesFoundSeen, "BRF");
                current.branchesFound = number(line, 4);
                current.branchesFoundSeen = true;
            } else if (line.startsWith("BRH:")) {
                once(current.branchesHitSeen, "BRH");
                current.branchesHit = number(line, 4);
                current.branchesHitSeen = true;
            } else if (line.startsWith("FNF:")) {
                once(current.functionsFoundSeen, "FNF");
                current.functionsFound = number(line, 4);
                current.functionsFoundSeen = true;
            } else if (line.startsWith("FNH:")) {
                once(current.functionsHitSeen, "FNH");
                current.functionsHit = number(line, 4);
                current.functionsHitSeen = true;
            }
        }

        /**
         * Refuses a counter line the current record already carries, so a later line can never override an earlier one.
         *
         * @param seen whether the record already carries the counter line
         * @param type the counter line type, for the refusal reason
         * @throws IOException if the record already carries the counter line
         */
        private void once(boolean seen, String type) throws IOException {
            if (seen) {
                throw new IOException("the record " + current.file + " carries the " + type + " line twice");
            }
        }

        private CoverageParseResult result() throws IOException {
            double line = CoveragePercentages.percentage(linesHit, linesFound)
                    .orElseThrow(() -> new IOException(NO_LINES_MEASURED));
            CoverageMetrics overall = CoverageMetrics.builder()
                    .line(line)
                    .branch(branchesSeen ? CoveragePercentages.percentage(branchesHit, branchesFound).orElse(null) : null)
                    .function(functionsSeen
                            ? CoveragePercentages.percentage(functionsHit, functionsFound).orElse(null)
                            : null)
                    .statement(line)
                    .build();
            return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, overall, items));
        }

        private int number(String line, int prefixLength) throws IOException {
            try {
                int value = Integer.parseInt(line.substring(prefixLength).strip());
                if (value < 0) {
                    throw new IOException("the counter " + line.substring(0, prefixLength) + " is negative");
                }
                return value;
            } catch (NumberFormatException _) {
                throw new IOException("the counter " + line.substring(0, prefixLength) + " is not a number");
            }
        }
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
        private boolean branchesFoundSeen;
        private boolean branchesHitSeen;
        private int functionsFound;
        private int functionsHit;
        private boolean functionsFoundSeen;
        private boolean functionsHitSeen;

        private Record(String file) {
            this.file = file;
        }

        /**
         * Validates the record and returns its item, or empty for a record with {@code LF:0}, which measured no line.
         *
         * @return the item, or empty for a record without measured lines
         * @throws IOException if the record is incomplete or covers more than it found
         */
        private Optional<CoverageItem> item() throws IOException {
            if (file.isEmpty()) {
                throw new IOException("an SF record carries no file name");
            }
            if (!linesFoundSeen || !linesHitSeen) {
                throw new IOException("the record " + file + " carries no LF/LH counters");
            }
            if (linesHit > linesFound) {
                throw new IOException("the record " + file + " covers more lines than it found");
            }
            if (branchesFoundSeen != branchesHitSeen) {
                throw new IOException("the record " + file + " carries an incomplete branch counter pair");
            }
            if (branchesHit > branchesFound) {
                throw new IOException("the record " + file + " covers more branches than it found");
            }
            if (functionsFoundSeen != functionsHitSeen) {
                throw new IOException("the record " + file + " carries an incomplete function counter pair");
            }
            if (functionsHit > functionsFound) {
                throw new IOException("the record " + file + " covers more functions than it found");
            }
            return CoveragePercentages.percentage(linesHit, linesFound)
                    .map(line -> CoverageItem.builder()
                            .name(file)
                            .file(file)
                            .line(line)
                            .branch(CoveragePercentages.percentage(branchesHit, branchesFound).orElse(null))
                            .missedMethods(List.of())
                            .build());
        }
    }
}
