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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.planmarshall.core.analysis.AnalysisResult.Comparator;
import de.planmarshall.core.analysis.AnalysisResult.Condition;
import de.planmarshall.core.analysis.AnalysisResult.CountStatus;
import de.planmarshall.core.analysis.AnalysisResult.Gate;
import de.planmarshall.core.analysis.AnalysisResult.GateResult;
import de.planmarshall.core.findings.FindingScope;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The canonical JSON line of an analysis result (PM-IMPL-2): one shape written, exactly that shape read.
 */
@DisplayName("Analysis result codec")
class AnalysisResultCodecTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    private static final AnalysisResult PASSED_COMMIT = new AnalysisResult(1, "sonar", FindingScope.commit(SHA),
            AnalysisStatus.PASSED, null, CountStatus.MEASURED, 0L, 0, false, "AY-analysis-1",
            Instant.parse("2026-10-11T12:00:00Z"));
    private static final String PASSED_COMMIT_LINE = "{\"format_version\":1,\"provider\":\"sonar\","
            + "\"target\":{\"kind\":\"commit\",\"commit_sha\":\"" + SHA + "\",\"pr_number\":null},"
            + "\"status\":\"passed\",\"gate\":null,\"count_status\":\"measured\",\"new_code_issue_count\":0,"
            + "\"issues_fetched\":0,\"work_performed\":false,\"analysis_ref\":\"AY-analysis-1\","
            + "\"observed_at\":\"2026-10-11T12:00:00Z\"}";

    private static final AnalysisResult GATE_FAILED_PULL_REQUEST = new AnalysisResult(1, "code-scan",
            FindingScope.pullRequest(SHA, 42), AnalysisStatus.GATE_FAILED,
            new Gate(GateResult.FAILED, List.of(
                    new Condition("new_coverage", Comparator.LT, "80", "71.4", GateResult.FAILED),
                    new Condition("new_duplicated_lines", Comparator.GT, "3", null, GateResult.PASSED))),
            CountStatus.MEASURED, 3L, 3, true, "AY-analysis-2", Instant.parse("2026-10-11T12:30:15.250Z"));
    private static final String GATE_FAILED_PULL_REQUEST_LINE = "{\"format_version\":1,\"provider\":\"code-scan\","
            + "\"target\":{\"kind\":\"pull_request\",\"commit_sha\":\"" + SHA + "\",\"pr_number\":42},"
            + "\"status\":\"gate_failed\",\"gate\":{\"result\":\"failed\",\"conditions\":["
            + "{\"metric\":\"new_coverage\",\"comparator\":\"lt\",\"threshold\":\"80\",\"actual\":\"71.4\","
            + "\"result\":\"failed\"},"
            + "{\"metric\":\"new_duplicated_lines\",\"comparator\":\"gt\",\"threshold\":\"3\",\"actual\":null,"
            + "\"result\":\"passed\"}]},"
            + "\"count_status\":\"measured\",\"new_code_issue_count\":3,\"issues_fetched\":3,\"work_performed\":true,"
            + "\"analysis_ref\":\"AY-analysis-2\",\"observed_at\":\"2026-10-11T12:30:15.250Z\"}";

    private static final AnalysisResult UNDECIDABLE_COMMIT = new AnalysisResult(1, "sonar",
            FindingScope.commit(SHA), AnalysisStatus.UNDECIDABLE, new Gate(GateResult.PASSED, List.of()),
            CountStatus.INDETERMINATE, null, 0, false, null, Instant.parse("2026-10-11T13:00:00Z"));
    private static final String UNDECIDABLE_COMMIT_LINE = "{\"format_version\":1,\"provider\":\"sonar\","
            + "\"target\":{\"kind\":\"commit\",\"commit_sha\":\"" + SHA + "\",\"pr_number\":null},"
            + "\"status\":\"undecidable\",\"gate\":{\"result\":\"passed\",\"conditions\":[]},"
            + "\"count_status\":\"indeterminate\",\"new_code_issue_count\":null,\"issues_fetched\":0,"
            + "\"work_performed\":false,\"analysis_ref\":null,\"observed_at\":\"2026-10-11T13:00:00Z\"}";

    static Stream<Arguments> fixtures() {
        return Stream.of(
                Arguments.of("passed commit without gate", PASSED_COMMIT, PASSED_COMMIT_LINE),
                Arguments.of("gate_failed pull request with conditions", GATE_FAILED_PULL_REQUEST,
                        GATE_FAILED_PULL_REQUEST_LINE),
                Arguments.of("undecidable commit with indeterminate count", UNDECIDABLE_COMMIT,
                        UNDECIDABLE_COMMIT_LINE));
    }

    static Stream<Arguments> refusedLines() {
        return Stream.of(
                Arguments.of("an undeclared member",
                        PASSED_COMMIT_LINE.replace("\"gate\":null", "\"gate\":null,\"extra\":1"),
                        "undeclared member 'extra'"),
                Arguments.of("an undeclared member of the target",
                        PASSED_COMMIT_LINE.replace("\"pr_number\":null", "\"pr_number\":null,\"branch\":\"main\""),
                        "undeclared member 'branch'"),
                Arguments.of("an undeclared member of a condition",
                        GATE_FAILED_PULL_REQUEST_LINE.replace("\"actual\":null", "\"actual\":null,\"unit\":\"%\""),
                        "undeclared member 'unit'"),
                Arguments.of("a duplicated member",
                        PASSED_COMMIT_LINE.replace("\"gate\":null", "\"gate\":null,\"provider\":\"sonar\""),
                        "member 'provider' twice"),
                Arguments.of("a missing member",
                        PASSED_COMMIT_LINE.replace(",\"analysis_ref\":\"AY-analysis-1\"", ""),
                        "member 'analysis_ref' of the analysis result is missing"),
                Arguments.of("a missing member of the target",
                        PASSED_COMMIT_LINE.replace(",\"pr_number\":null", ""),
                        "member 'pr_number' of the target is missing"),
                Arguments.of("a missing member of the gate",
                        UNDECIDABLE_COMMIT_LINE.replace(",\"conditions\":[]", ""),
                        "member 'conditions' of the gate is missing"),
                Arguments.of("an integer member written as a string",
                        PASSED_COMMIT_LINE.replace("\"issues_fetched\":0", "\"issues_fetched\":\"0\""),
                        "'issues_fetched' must be an integer"),
                Arguments.of("a boolean member written as a string",
                        PASSED_COMMIT_LINE.replace("\"work_performed\":false", "\"work_performed\":\"false\""),
                        "'work_performed' must be a boolean"),
                Arguments.of("a string member written as a number",
                        PASSED_COMMIT_LINE.replace("\"provider\":\"sonar\"", "\"provider\":7"),
                        "'provider' must be a string"),
                Arguments.of("the format version written as a decimal",
                        PASSED_COMMIT_LINE.replace("\"format_version\":1", "\"format_version\":1.0"),
                        "'format_version' must be an integer"),
                Arguments.of("the target written as a string",
                        PASSED_COMMIT_LINE.replace("\"target\":{\"kind\":\"commit\",\"commit_sha\":\"" + SHA
                                + "\",\"pr_number\":null}", "\"target\":\"" + SHA + "\""),
                        "'target' must be an object"),
                Arguments.of("the conditions written as an object",
                        UNDECIDABLE_COMMIT_LINE.replace("\"conditions\":[]", "\"conditions\":{}"),
                        "'conditions' must be an array"),
                Arguments.of("an undeclared status value",
                        PASSED_COMMIT_LINE.replace("\"status\":\"passed\"", "\"status\":\"PASSED\""),
                        "'status' has the undeclared value 'PASSED'"),
                Arguments.of("an instant in another spelling of the same moment",
                        PASSED_COMMIT_LINE.replace("2026-10-11T12:00:00Z", "2026-10-11T12:00:00+00:00"),
                        "'observed_at' is not"),
                Arguments.of("a format version other than 1",
                        PASSED_COMMIT_LINE.replace("\"format_version\":1", "\"format_version\":2"),
                        "'format_version' must be 1, not 2"),
                Arguments.of("a line that breaks a consistency rule",
                        PASSED_COMMIT_LINE.replace("\"new_code_issue_count\":0", "\"new_code_issue_count\":1"),
                        "status passed requires"),
                Arguments.of("a target that breaks a rule",
                        PASSED_COMMIT_LINE.replace("\"pr_number\":null", "\"pr_number\":7"),
                        "the target breaks a rule"),
                Arguments.of("content after the object", PASSED_COMMIT_LINE + "{}",
                        "content after the analysis result object"),
                Arguments.of("a line that is not a JSON object", "[]", "'analysis result' must be an object"),
                Arguments.of("a line that is not JSON", PASSED_COMMIT_LINE.substring(0, 40), "the line is not JSON"));
    }

    @Nested
    @DisplayName("round trip")
    class RoundTrip {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.analysis.AnalysisResultCodecTest#fixtures")
        @DisplayName("a result is written as its fixed canonical line")
        void writesCanonicalLine(String name, AnalysisResult result, String line) {
            assertEquals(line, new String(AnalysisResultCodec.write(result), UTF_8));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.analysis.AnalysisResultCodecTest#fixtures")
        @DisplayName("a canonical line reads back equal and is written again byte for byte")
        void readsBackByteIdentical(String name, AnalysisResult result, String line) {
            var bytes = line.getBytes(UTF_8);

            var read = AnalysisResultCodec.read(bytes);

            assertAll(
                    () -> assertEquals(result, read),
                    () -> assertArrayEquals(bytes, AnalysisResultCodec.write(read)));
        }

        @Test
        @DisplayName("the written line ends without a line break")
        void noTrailingLineBreak() {
            var line = AnalysisResultCodec.write(PASSED_COMMIT);

            assertEquals('}', line[line.length - 1]);
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.analysis.AnalysisResultCodecTest#refusedLines")
        @DisplayName("a line outside the one shape is refused with a message naming what is wrong")
        void refused(String name, String line, String expected) {
            var bytes = line.getBytes(UTF_8);

            var refusal = assertThrows(IllegalArgumentException.class, () -> AnalysisResultCodec.read(bytes));

            assertTrue(refusal.getMessage().contains(expected),
                    () -> "expected '%s' in: %s".formatted(expected, refusal.getMessage()));
        }

        @Test
        @DisplayName("an empty line is refused")
        void emptyLine() {
            var empty = new byte[0];

            assertThrows(IllegalArgumentException.class, () -> AnalysisResultCodec.read(empty));
        }

        @Test
        @DisplayName("a missing result or line is refused")
        void missingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> AnalysisResultCodec.write(null)),
                    () -> assertThrows(NullPointerException.class, () -> AnalysisResultCodec.read(null)));
        }
    }
}
