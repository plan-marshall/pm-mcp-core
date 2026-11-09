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
 * A malformed document — truncated JSON, a metric without a numeric {@code pct}, a missing {@code total} — is refused
 * naming the format, never read as a report of zeros.
 *
 * @since 0.1
 */
@UtilityClass
public class IstanbulSummaryParser {

    private static final CoverageFormat FORMAT = CoverageFormat.ISTANBUL;

    private static final String TOTAL = "total";

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
                items.add(readItem(parser, field));
            }
        }
        if (parser.currentToken() != JsonToken.END_OBJECT) {
            throw new IOException("the document is truncated");
        }
        if (overall == null) {
            throw new IOException("the document carries no total");
        }
        return new CoverageParseResult.Parsed(new CoverageReport(FORMAT, overall, items));
    }

    private static CoverageMetrics readOverall(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException("total is not an object");
        }
        Double lines = null;
        Double statements = null;
        Double functions = null;
        Double branches = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "lines" -> lines = readPct(parser, "total.lines").orElse(null);
                case "statements" -> statements = readPct(parser, "total.statements").orElse(null);
                case "functions" -> functions = readPct(parser, "total.functions").orElse(null);
                case "branches" -> branches = readPct(parser, "total.branches").orElse(null);
                default -> skip(parser);
            }
        }
        return CoverageMetrics.builder()
                .line(CoveragePercentages.round2(requireMetric(lines, "total.lines")))
                .statement(CoveragePercentages.round2(requireMetric(statements, "total.statements")))
                .function(CoveragePercentages.round2(requireMetric(functions, "total.functions")))
                .branch(CoveragePercentages.round2(requireMetric(branches, "total.branches")))
                .build();
    }

    private static CoverageItem readItem(JsonParser parser, String file) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException("the coverage of " + file + " is not an object");
        }
        Double lines = null;
        Double branches = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            parser.nextToken();
            switch (field) {
                case "lines" -> lines = readPct(parser, file + ".lines").orElse(null);
                case "branches" -> branches = readPct(parser, file + ".branches").orElse(null);
                default -> skip(parser);
            }
        }
        return CoverageItem.builder()
                .name(file)
                .file(file)
                .line(CoveragePercentages.round2(requireMetric(lines, file + ".lines")))
                .branch(branches == null ? null : CoveragePercentages.round2(branches))
                .missedMethods(List.of())
                .build();
    }

    private static Optional<Double> readPct(JsonParser parser, String metric) throws IOException {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new IOException(metric + " is not an object");
        }
        Double pct = null;
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if ("pct".equals(field)) {
                if (value != JsonToken.VALUE_NUMBER_FLOAT && value != JsonToken.VALUE_NUMBER_INT) {
                    throw new IOException(metric + ".pct is not a number");
                }
                pct = parser.getDoubleValue();
            } else {
                skip(parser);
            }
        }
        return pct == null ? Optional.empty() : Optional.of(pct);
    }

    private static double requireMetric(@Nullable Double value, String metric) throws IOException {
        if (value == null) {
            throw new IOException(metric + ".pct is absent");
        }
        return value;
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
