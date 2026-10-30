/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.analysis.AnalysisResult.Comparator;
import de.planmarshall.core.analysis.AnalysisResult.Condition;
import de.planmarshall.core.analysis.AnalysisResult.CountStatus;
import de.planmarshall.core.analysis.AnalysisResult.Gate;
import de.planmarshall.core.analysis.AnalysisResult.GateResult;
import de.planmarshall.core.findings.FindingScope;

/**
 * Writes and reads the canonical JSON line of an {@link AnalysisResult} (PM-IMPL-2), one line of
 * {@code analysis.jsonl}.
 * <p>
 * The writer produces exactly one shape: one JSON object, UTF-8 encoded, without a trailing line break, with every
 * member of the schema {@code AnalysisResult} present and in schema order, an absent value written as {@code null},
 * and {@code observed_at} written as {@link Instant#toString()} writes it.
 * <p>
 * The reader accepts exactly that shape and never tolerates another: only the declared members, each at most once,
 * every member present, each with its one JSON type, {@code format_version} exactly
 * {@value AnalysisResult#FORMAT_VERSION}, an instant only in the spelling the writer produces, and nothing after the
 * object. A line that breaks a rule of the record is refused like a malformed one. Last, the reader writes the result
 * again and refuses the line unless it is byte for byte the line the writer produces: a line with whitespace between
 * tokens, the members in another order, a string escape the writer does not use (such as a six-character Unicode
 * escape for a plain character, or the escaped solidus {@code \/}) or an integer spelling such as {@code -0} is refused as not in the canonical spelling.
 * Every refusal is an {@link IllegalArgumentException} naming what is wrong, so a line that was read is written again
 * byte for byte.
 *
 * @since 0.1
 */
@UtilityClass
public class AnalysisResultCodec {

    private static final JsonFactory JSON = new JsonFactory();

    private static final String FIELD_FORMAT_VERSION = "format_version";
    private static final String FIELD_PROVIDER = "provider";
    private static final String FIELD_TARGET = "target";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_GATE = "gate";
    private static final String FIELD_COUNT_STATUS = "count_status";
    private static final String FIELD_NEW_CODE_ISSUE_COUNT = "new_code_issue_count";
    private static final String FIELD_ISSUES_FETCHED = "issues_fetched";
    private static final String FIELD_WORK_PERFORMED = "work_performed";
    private static final String FIELD_ANALYSIS_REF = "analysis_ref";
    private static final String FIELD_OBSERVED_AT = "observed_at";
    private static final String FIELD_KIND = "kind";
    private static final String FIELD_COMMIT_SHA = "commit_sha";
    private static final String FIELD_PR_NUMBER = "pr_number";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_CONDITIONS = "conditions";
    private static final String FIELD_METRIC = "metric";
    private static final String FIELD_COMPARATOR = "comparator";
    private static final String FIELD_THRESHOLD = "threshold";
    private static final String FIELD_ACTUAL = "actual";

    /**
     * @param result the analysis result to write
     * @return its canonical JSON line, UTF-8 encoded, without a trailing line break
     */
    public static byte[] write(AnalysisResult result) {
        Objects.requireNonNull(result, FIELD_RESULT);
        var out = new ByteArrayOutputStream();
        try (var generator = JSON.createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, result.formatVersion());
            generator.writeStringField(FIELD_PROVIDER, result.provider());
            generator.writeFieldName(FIELD_TARGET);
            writeTarget(generator, result.target());
            generator.writeStringField(FIELD_STATUS, result.status().wireName());
            generator.writeFieldName(FIELD_GATE);
            writeGate(generator, result.gate());
            generator.writeStringField(FIELD_COUNT_STATUS, result.countStatus().wireName());
            if (result.newCodeIssueCount() == null) {
                generator.writeNullField(FIELD_NEW_CODE_ISSUE_COUNT);
            } else {
                generator.writeNumberField(FIELD_NEW_CODE_ISSUE_COUNT, result.newCodeIssueCount().longValue());
            }
            generator.writeNumberField(FIELD_ISSUES_FETCHED, result.issuesFetched());
            generator.writeBooleanField(FIELD_WORK_PERFORMED, result.workPerformed());
            writeNullableString(generator, FIELD_ANALYSIS_REF, result.analysisRef());
            generator.writeStringField(FIELD_OBSERVED_AT, result.observedAt().toString());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Analysis result line cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param line the canonical JSON line of an analysis result, UTF-8 encoded
     * @return the analysis result of the line
     * @throws IllegalArgumentException if the line does not have the one shape of an analysis result line, breaks
     *                                  a rule of the record or is not in the canonical spelling the writer produces;
     *                                  the message names what is wrong
     */
    public static AnalysisResult read(byte[] line) {
        Objects.requireNonNull(line, "line");
        try (var parser = JSON.createParser(line)) {
            parser.nextToken();
            var result = readResult(parser);
            if (parser.nextToken() != null) {
                throw refused("content after the analysis result object");
            }
            if (!Arrays.equals(write(result), line)) {
                throw refused("the line is not in the canonical spelling");
            }
            return result;
        } catch (IOException e) {
            throw refused("the line is not JSON: " + e.getMessage(), e);
        }
    }

    private static void writeTarget(JsonGenerator generator, FindingScope target) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_KIND, target.kind().wireName());
        generator.writeStringField(FIELD_COMMIT_SHA, target.commitSha());
        if (target.prNumber() == null) {
            generator.writeNullField(FIELD_PR_NUMBER);
        } else {
            generator.writeNumberField(FIELD_PR_NUMBER, target.prNumber().intValue());
        }
        generator.writeEndObject();
    }

    private static void writeGate(JsonGenerator generator, Gate gate) throws IOException {
        if (gate == null) {
            generator.writeNull();
            return;
        }
        generator.writeStartObject();
        generator.writeStringField(FIELD_RESULT, gate.result().wireName());
        generator.writeArrayFieldStart(FIELD_CONDITIONS);
        for (var condition : gate.conditions()) {
            generator.writeStartObject();
            generator.writeStringField(FIELD_METRIC, condition.metric());
            generator.writeStringField(FIELD_COMPARATOR, condition.comparator().wireName());
            generator.writeStringField(FIELD_THRESHOLD, condition.threshold());
            writeNullableString(generator, FIELD_ACTUAL, condition.actual());
            generator.writeStringField(FIELD_RESULT, condition.result().wireName());
            generator.writeEndObject();
        }
        generator.writeEndArray();
        generator.writeEndObject();
    }

    private static void writeNullableString(JsonGenerator generator, String field, String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static AnalysisResult readResult(JsonParser parser) throws IOException {
        requireObject(parser, "analysis result");
        long formatVersion = 0;
        String provider = null;
        FindingScope target = null;
        AnalysisStatus status = null;
        Gate gate = null;
        CountStatus countStatus = null;
        Long newCodeIssueCount = null;
        long issuesFetched = 0;
        var workPerformed = false;
        String analysisRef = null;
        Instant observedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> formatVersion = integer(parser, field);
                case FIELD_PROVIDER -> provider = string(parser, field);
                case FIELD_TARGET -> target = readTarget(parser);
                case FIELD_STATUS -> status = wireValue(parser, field, AnalysisResultCodec::analysisStatus);
                case FIELD_GATE -> gate = readGate(parser).orElse(null);
                case FIELD_COUNT_STATUS -> countStatus = wireValue(parser, field, CountStatus::fromWireName);
                case FIELD_NEW_CODE_ISSUE_COUNT -> newCodeIssueCount = nullableInteger(parser, field);
                case FIELD_ISSUES_FETCHED -> issuesFetched = integer(parser, field);
                case FIELD_WORK_PERFORMED -> workPerformed = bool(parser, field);
                case FIELD_ANALYSIS_REF -> analysisRef = nullableString(parser, field);
                case FIELD_OBSERVED_AT -> observedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "analysis result", FIELD_FORMAT_VERSION, FIELD_PROVIDER, FIELD_TARGET, FIELD_STATUS,
                FIELD_GATE, FIELD_COUNT_STATUS, FIELD_NEW_CODE_ISSUE_COUNT, FIELD_ISSUES_FETCHED,
                FIELD_WORK_PERFORMED, FIELD_ANALYSIS_REF, FIELD_OBSERVED_AT);
        if (formatVersion != AnalysisResult.FORMAT_VERSION) {
            throw refused("'format_version' must be %d, not %d".formatted(AnalysisResult.FORMAT_VERSION,
                    formatVersion));
        }
        try {
            return new AnalysisResult((int) formatVersion, provider, target, status, gate, countStatus,
                    newCodeIssueCount, issuesFetched, workPerformed, analysisRef, observedAt);
        } catch (IllegalArgumentException e) {
            throw refused("the analysis result breaks a rule: " + e.getMessage(), e);
        }
    }

    private static FindingScope readTarget(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_TARGET);
        FindingScope.Kind kind = null;
        String commitSha = null;
        Long prNumber = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KIND -> kind = wireValue(parser, field, FindingScope.Kind::fromWireName);
                case FIELD_COMMIT_SHA -> commitSha = string(parser, field);
                case FIELD_PR_NUMBER -> prNumber = nullableInteger(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_TARGET, FIELD_KIND, FIELD_COMMIT_SHA, FIELD_PR_NUMBER);
        if (prNumber != null && (prNumber < Integer.MIN_VALUE || prNumber > Integer.MAX_VALUE)) {
            throw refused("'pr_number' is out of range: " + prNumber);
        }
        try {
            return new FindingScope(kind, commitSha, prNumber == null ? null : prNumber.intValue());
        } catch (IllegalArgumentException e) {
            throw refused("the target breaks a rule: " + e.getMessage(), e);
        }
    }

    private static Optional<Gate> readGate(JsonParser parser) throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            return Optional.empty();
        }
        requireObject(parser, FIELD_GATE);
        GateResult result = null;
        List<Condition> conditions = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_RESULT -> result = wireValue(parser, field, GateResult::fromWireName);
                case FIELD_CONDITIONS -> conditions = readConditions(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_GATE, FIELD_RESULT, FIELD_CONDITIONS);
        try {
            return Optional.of(new Gate(result, conditions));
        } catch (IllegalArgumentException e) {
            throw refused("the gate breaks a rule: " + e.getMessage(), e);
        }
    }

    private static List<Condition> readConditions(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw refused("'conditions' must be an array");
        }
        var conditions = new ArrayList<Condition>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            conditions.add(readCondition(parser));
        }
        return conditions;
    }

    private static Condition readCondition(JsonParser parser) throws IOException {
        requireObject(parser, "condition");
        String metric = null;
        Comparator comparator = null;
        String threshold = null;
        String actual = null;
        GateResult result = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_METRIC -> metric = string(parser, field);
                case FIELD_COMPARATOR -> comparator = wireValue(parser, field, Comparator::fromWireName);
                case FIELD_THRESHOLD -> threshold = string(parser, field);
                case FIELD_ACTUAL -> actual = nullableString(parser, field);
                case FIELD_RESULT -> result = wireValue(parser, field, GateResult::fromWireName);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "condition", FIELD_METRIC, FIELD_COMPARATOR, FIELD_THRESHOLD, FIELD_ACTUAL,
                FIELD_RESULT);
        try {
            return new Condition(metric, comparator, threshold, actual, result);
        } catch (IllegalArgumentException e) {
            throw refused("a condition breaks a rule: " + e.getMessage(), e);
        }
    }

    private static Optional<AnalysisStatus> analysisStatus(String wireName) {
        return Arrays.stream(AnalysisStatus.values()).filter(value -> value.wireName().equals(wireName))
                .findFirst();
    }

    private static String nextField(JsonParser parser, Set<String> seen) throws IOException {
        var field = parser.currentName();
        if (!seen.add(field)) {
            throw refused("member '%s' twice".formatted(field));
        }
        parser.nextToken();
        return field;
    }

    private static void requireObject(JsonParser parser, String name) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw refused("'%s' must be an object".formatted(name));
        }
    }

    private static void requireFields(Set<String> seen, String object, String... required) {
        for (var field : required) {
            if (!seen.contains(field)) {
                throw refused("member '%s' of the %s is missing".formatted(field, object));
            }
        }
    }

    private static String string(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw refused("'%s' must be a string".formatted(field));
        }
        return parser.getText();
    }

    private static String nullableString(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : string(parser, field);
    }

    private static long integer(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw refused("'%s' must be an integer".formatted(field));
        }
        return parser.getLongValue();
    }

    private static Long nullableInteger(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : integer(parser, field);
    }

    private static boolean bool(JsonParser parser, String field) {
        var token = parser.currentToken();
        if (token != JsonToken.VALUE_TRUE && token != JsonToken.VALUE_FALSE) {
            throw refused("'%s' must be a boolean".formatted(field));
        }
        return token == JsonToken.VALUE_TRUE;
    }

    private static <E> E wireValue(JsonParser parser, String field, Function<String, Optional<E>> lookup)
            throws IOException {
        var wireName = string(parser, field);
        return lookup.apply(wireName)
                .orElseThrow(() -> refused("'%s' has the undeclared value '%s'".formatted(field, wireName)));
    }

    /** Accepts the one spelling the writer produces, so reading and writing an instant never changes its bytes. */
    private static Instant instant(JsonParser parser, String field) throws IOException {
        var text = string(parser, field);
        try {
            var instant = Instant.parse(text);
            if (instant.toString().equals(text)) {
                return instant;
            }
        } catch (DateTimeException e) {
            throw refused("'%s' is not an instant: %s".formatted(field, text), e);
        }
        throw refused("'%s' is not in the one shape of an instant (RFC 3339, UTC, suffix Z): %s".formatted(field,
                text));
    }

    private static IllegalArgumentException undeclared(String field) {
        return refused("undeclared member '%s'".formatted(field));
    }

    private static IllegalArgumentException refused(String detail) {
        return new IllegalArgumentException("Analysis result line refused: " + detail);
    }

    private static IllegalArgumentException refused(String detail, Throwable cause) {
        return new IllegalArgumentException("Analysis result line refused: " + detail, cause);
    }
}
