/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;

/**
 * The usage pointers of a client profile (PM-CLIENT-2): a recorded harness output lands in the metrics columns of the
 * same name, and a figure the harness does not report is unmeasured with its reason, never a zero.
 */
@DisplayName("Usage pointers")
class UsagePointersTest {

    /** A turn of the recorded {@code opencode} output of the profile verification (Gate 13). */
    private static final String OPENCODE_TURN = """
            {"type":"step_finish","timestamp":1758700000000,
             "part":{"tokens":{"input":1200,"output":340,"reasoning":150,
                               "cache":{"read":5000,"write":800}}},
             "tool_uses":7}""";

    /** A turn of the recorded {@code claude} output: its output figure already contains the thinking tokens. */
    private static final String CLAUDE_TURN = """
            {"type":"assistant","message":{"usage":{"input_tokens":900,"output_tokens":410,
             "cache_read_input_tokens":3000,"cache_creation_input_tokens":700}},
             "tool_uses":4}""";

    private static final UsagePointers OPENCODE = new UsagePointers("/part/tokens/input", "/part/tokens/output",
            "/part/tokens/cache/read", "/part/tokens/cache/write", "/part/tokens/reasoning", null, "/tool_uses");

    private static final UsagePointers CLAUDE = new UsagePointers("/message/usage/input_tokens",
            "/message/usage/output_tokens", "/message/usage/cache_read_input_tokens",
            "/message/usage/cache_creation_input_tokens", UsagePointers.NOT_APPLICABLE, null, "/tool_uses");

    private static JsonNode parse(String document) {
        return JsonDocument.parse(document.getBytes(UTF_8), "turn");
    }

    @Nested
    @DisplayName("a recorded harness output")
    class RecordedOutput {

        @Test
        @DisplayName("lands in the metrics row with each column")
        void recordedHarnessOutputLandsInTheMetricsRowWithEachColumn() {
            var usage = OPENCODE.read(parse(OPENCODE_TURN));

            assertAll(
                    () -> assertEquals(Measurement.measured(1200), usage.inputTokens()),
                    () -> assertEquals(Measurement.measured(340), usage.outputTokens()),
                    () -> assertEquals(Measurement.measured(5000), usage.cacheReadInputTokens()),
                    () -> assertEquals(Measurement.measured(800), usage.cacheCreationInputTokens()),
                    () -> assertEquals(Measurement.measured(150), usage.reasoningTokens()),
                    () -> assertEquals(Measurement.measured(7), usage.toolUses()),
                    () -> assertEquals(Optional.empty(), usage.costUsd(), "opencode reports no cost"),
                    () -> assertEquals(OptionalLong.of(7490), usage.totalTokens()),
                    () -> assertEquals(Optional.empty(), usage.unmeasuredReason()));
        }

        @Test
        @DisplayName("a reasoning figure the harness does not report apart is not_applicable and counts zero")
        void notApplicableCountsZero() {
            var usage = CLAUDE.read(parse(CLAUDE_TURN));

            assertAll(
                    () -> assertTrue(usage.reasoningTokens().isNotApplicable()),
                    () -> assertEquals(OptionalLong.of(900 + 410 + 3000 + 700), usage.totalTokens()),
                    () -> assertEquals(Optional.empty(), usage.unmeasuredReason()));
        }

        @Test
        @DisplayName("the cost stays recorded data, read from its own pointer")
        void cost() {
            var pointers = new UsagePointers("/part/tokens/input", "/part/tokens/output",
                    "/part/tokens/cache/read", "/part/tokens/cache/write", "/part/tokens/reasoning",
                    "/part/cost", "/tool_uses");
            var document = """
                    {"part":{"tokens":{"input":1,"output":1,"reasoning":0,"cache":{"read":0,"write":0}},
                     "cost":0.0123},"tool_uses":1}""";

            var usage = pointers.read(parse(document));

            assertEquals(Optional.of(new BigDecimal("0.0123")), usage.costUsd());
        }
    }

    @Nested
    @DisplayName("a missing figure")
    class MissingFigure {

        @Test
        @DisplayName("is recorded unmeasured with the reason not_reported_by_harness")
        void missingFigureIsRecordedUnmeasuredWithItsReason() {
            var pointers = new UsagePointers("/part/tokens/input", "/part/tokens/output", null, null, null, null,
                    "/tool_uses");

            var usage = pointers.read(parse(OPENCODE_TURN));

            assertAll(
                    () -> assertEquals(Measurement.unmeasured(), usage.cacheReadInputTokens()),
                    () -> assertEquals(Measurement.unmeasured(), usage.cacheCreationInputTokens()),
                    () -> assertEquals(Measurement.unmeasured(), usage.reasoningTokens()),
                    () -> assertEquals(Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS),
                            usage.unmeasuredReason()),
                    () -> assertEquals(OptionalLong.empty(), usage.totalTokens()));
        }

        @Test
        @DisplayName("a pointer that does not resolve is unmeasured, not a zero")
        void unresolvedPointer() {
            var pointers = new UsagePointers("/part/tokens/input", "/part/tokens/output", "/part/tokens/cache/absent",
                    UsagePointers.NOT_APPLICABLE, UsagePointers.NOT_APPLICABLE, null, "/tool_uses");

            var usage = pointers.read(parse(OPENCODE_TURN));

            assertAll(
                    () -> assertEquals(Measurement.unmeasured(), usage.cacheReadInputTokens()),
                    () -> assertEquals(OptionalLong.empty(), usage.totalTokens()));
        }

        @Test
        @DisplayName("a figure whose output is not an integer is unmeasured with the reason output_unparsed")
        void unparsedFigure() {
            var pointers = new UsagePointers("/part/tokens/input", "/part/tokens/output", null, null, null, null,
                    "/tool_uses");
            var document = """
                    {"part":{"tokens":{"input":"many","output":1}},"tool_uses":1}""";

            var usage = pointers.read(parse(document));

            assertAll(
                    () -> assertEquals(Measurement.unmeasured(), usage.inputTokens()),
                    () -> assertEquals(Optional.of(UnmeasuredReason.OUTPUT_UNPARSED), usage.unmeasuredReason()));
        }
    }

    @Nested
    @DisplayName("a turn without usage")
    class NoUsage {

        @Test
        @DisplayName("makes every applicable component unmeasured and the total unmeasured")
        void emptyTurn() {
            var pointers = new UsagePointers("/part/tokens/input", "/part/tokens/output", null, null, null, null,
                    null);

            var usage = pointers.read(parse("{\"type\":\"text\"}"));

            assertAll(
                    () -> assertEquals(Measurement.unmeasured(), usage.inputTokens()),
                    () -> assertEquals(Measurement.unmeasured(), usage.outputTokens()),
                    () -> assertFalse(usage.totalTokens().isPresent()));
        }
    }

    @Nested
    @DisplayName("the profile declaration")
    class Declaration {

        @Test
        @DisplayName("is read from the headless.usage block, a null member meaning not reported")
        void readBlock() {
            var block = JsonDocument.parse("""
                    {"input_tokens":"/a","output_tokens":"/b","cache_read":null,
                     "cache_creation":"not_applicable","reasoning":"/c","cost_usd":null,"tool_uses":"/d"}"""
                    .getBytes(UTF_8), "usage");

            var pointers = UsagePointers.of(block);

            assertAll(
                    () -> assertEquals("/a", pointers.inputTokens()),
                    () -> assertEquals("/b", pointers.outputTokens()),
                    () -> assertNull(pointers.cacheRead()),
                    () -> assertEquals(UsagePointers.NOT_APPLICABLE, pointers.cacheCreation()),
                    () -> assertEquals("/c", pointers.reasoning()),
                    () -> assertNull(pointers.costUsd()),
                    () -> assertEquals("/d", pointers.toolUses()));
        }

        @Test
        @DisplayName("refuses not_applicable for input and output tokens, and a missing member")
        void refusals() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new UsagePointers(UsagePointers.NOT_APPLICABLE, "/b", null, null, null, null, null)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new UsagePointers("/a", UsagePointers.NOT_APPLICABLE, null, null, null, null, null)),
                    () -> assertThrows(IllegalArgumentException.class, () -> UsagePointers.of(JsonDocument.parse(
                            "{\"input_tokens\":\"/a\"}".getBytes(UTF_8), "usage"))));
        }
    }
}
