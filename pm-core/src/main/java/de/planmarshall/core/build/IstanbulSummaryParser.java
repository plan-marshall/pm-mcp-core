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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The native Istanbul coverage summary parser ({@code coverage-summary.json}).
 * <p>
 * The reader streams the document with Jackson rather than binding it to a tree, and takes the {@code pct} of the
 * {@code lines}, {@code statements}, {@code functions} and {@code branches} metrics from the {@code total} object as the
 * overall percentages, with the overall line percentage equal to {@code lines.pct}. Every other top-level object is one
 * file item, with its {@code lines.pct} as the line percentage and its {@code branches.pct} as the branch percentage.
 * <p>
 * Each metric's {@code total} is read beside its {@code pct}. A total of {@code 0} measured nothing, so the metric is
 * absent whatever its {@code pct} says: a file whose {@code lines.total} is {@code 0} is not an item, a
 * {@code branches.total} of {@code 0} leaves the branch metric absent, and a summary whose {@code total.lines.total} is
 * {@code 0} is refused ({@code no lines measured}).
 * <p>
 * A malformed document — truncated JSON, a metric without a numeric {@code pct}, a missing {@code total} — is refused
 * naming the format, never read as a report of zeros.
 *
 * @since 0.1
 */
@UtilityClass
public class IstanbulSummaryParser {

    private static final CoverageFormat FORMAT = CoverageFormat.ISTANBUL;

    private static final String TOTAL = "total";

    /** The refusal reason of a summary whose {@code total.lines.total} is {@code 0}. */
    private static final String NO_LINES_MEASURED = "no lines measured";

    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /**
     * Parses an Istanbul summary from its text.
     *
     * @param json the document text
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(String json) {
        Objects.requireNonNull(json, "json");
        try (JsonParser parser = JSON_FACTORY.createParser(json)) {
            return parse(parser);
        } catch (IOException | IllegalArgumentException e) {
            return refusal(e);
        }
    }

    /**
     * Parses an Istanbul summary from a file.
     *
     * @param reportFile the report file
     * @return the parsed report, or a refusal naming the format and the reason
     */
    public static CoverageParseResult parse(Path reportFile) {
        Objects.requireNonNull(reportFile, "reportFile");
        try (InputStream source = Files.newInputStream(reportFile);
             JsonParser parser = JSON_FACTORY.createParser(source)) {
            return parse(parser);
        } catch (IOException | IllegalArgumentException e) {
            return refusal(e);
        }
    }

    private static CoverageParseResult parse(JsonParser parser) throws IOException {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw new IOException("the document root is not an object");
        }
        CoverageMetrics overall = null;
        List<CoverageItem> items = new ArrayList<>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            if (TOTAL.equals(field)) {
                overall = readOverall(parser);
            } else {
                readItem(parser, field).ifPresent(items::add);
            }
        }
        if (parser.currentToken() != JsonToken.END_OBJECT) {
            throw new IOException("the document is truncated");
        }
        if (overall == null) {
            throw new IOException("the document carries no total");
        }
        if (parser.nextToken() != null) {
            throw new IOException("content after the document");
        }
        return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, overall, items));
    }

    private static CoverageMetrics readOverall(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException("total is not an object");
        }
        Metric lines = Metric.MISSING;
        Metric statements = Metric.MISSING;
        Metric functions = Metric.MISSING;
        Metric branches = Metric.MISSING;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "lines" -> lines = readMetric(parser, "total.lines");
                case "statements" -> statements = readMetric(parser, "total.statements");
                case "functions" -> functions = readMetric(parser, "total.functions");
                case "branches" -> branches = readMetric(parser, "total.branches");
                default -> skip(parser);
            }
        }
        double line = lines.percentage("total.lines")
                .orElseThrow(() -> new IOException(NO_LINES_MEASURED));
        return CoverageMetrics.builder()
                .line(line)
                .statement(statements.percentage("total.statements").orElse(null))
                .function(functions.percentage("total.functions").orElse(null))
                .branch(branches.percentage("total.branches").orElse(null))
                .build();
    }

    private static Optional<CoverageItem> readItem(JsonParser parser, String file) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException("the coverage of " + file + " is not an object");
        }
        Metric lines = Metric.MISSING;
        Metric branches = Metric.ABSENT;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "lines" -> lines = readMetric(parser, file + ".lines");
                case "branches" -> branches = readMetric(parser, file + ".branches");
                default -> skip(parser);
            }
        }
        Optional<Double> line = lines.percentage(file + ".lines");
        if (line.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(CoverageItem.builder()
                .name(file)
                .file(file)
                .line(line.get())
                .branch(branches.percentage(file + ".branches").orElse(null))
                .missedMethods(List.of())
                .build());
    }

    /**
     * Reads one metric object, keeping its {@code total} beside its {@code pct}. The {@code pct} is validated only when
     * the total is not {@code 0}, because Istanbul prints a placeholder there for an empty population.
     *
     * @param parser the parser positioned on the metric object
     * @param metric the metric path for a refusal reason
     * @return the metric as read
     * @throws IOException if the metric is not an object or its {@code total} is not a whole number of zero or more
     */
    private static Metric readMetric(JsonParser parser, String metric) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException(metric + " is not an object");
        }
        Long total = null;
        Double pct = null;
        boolean pctSeen = false;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if ("pct".equals(field)) {
                pctSeen = true;
                pct = value == JsonToken.VALUE_NUMBER_FLOAT || value == JsonToken.VALUE_NUMBER_INT
                        ? parser.getDoubleValue()
                        : null;
            } else if ("total".equals(field)) {
                if (value != JsonToken.VALUE_NUMBER_INT || parser.getLongValue() < 0) {
                    throw new IOException(metric + ".total is not a whole number of zero or more");
                }
                total = parser.getLongValue();
            } else {
                skip(parser);
            }
        }
        return new Metric(total != null && total == 0, pctSeen, pct);
    }

    /**
     * One metric object as read.
     *
     * @param empty   whether the metric's {@code total} is {@code 0}, so nothing was measured
     * @param pctSeen whether the metric carries a {@code pct}
     * @param pct     the numeric {@code pct}, or {@code null} when it is absent or not a number
     */
    private record Metric(boolean empty, boolean pctSeen, @Nullable Double pct) {

        /** A metric the object does not carry, where the metric is required. */
        private static final Metric MISSING = new Metric(false, false, null);

        /** A metric the object does not carry, where the metric is optional. */
        private static final Metric ABSENT = new Metric(true, false, null);

        /**
         * Returns the percentage, or empty when the metric measured nothing.
         *
         * @param metric the metric path for a refusal reason
         * @return the percentage rounded to two decimals, or empty for a total of {@code 0}
         * @throws IOException if the {@code pct} is absent, not a number or outside {@code 0..100}
         */
        private Optional<Double> percentage(String metric) throws IOException {
            if (empty) {
                return Optional.empty();
            }
            if (!pctSeen) {
                throw new IOException(metric + ".pct is absent");
            }
            if (pct == null) {
                throw new IOException(metric + ".pct is not a number");
            }
            if (!Double.isFinite(pct) || pct < 0 || pct > 100) {
                throw new IOException(metric + ".pct is outside 0..100: " + pct);
            }
            return Optional.of(CoveragePercentages.round2(pct));
        }
    }

    private static void skip(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.START_OBJECT || parser.currentToken() == JsonToken.START_ARRAY) {
            parser.skipChildren();
        }
    }

    private static CoverageParseResult refusal(Exception cause) {
        return CoverageRefusals.of(FORMAT, cause);
    }
}
