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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
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
 * <p>
 * The recorded harness outputs and the pointers into them are golden wire documents of the profile verification and
 * stay literal, and so do the member names of the {@code headless.usage} block; the pointers of a declaration, the cost
 * and the figures of the documents written for a test are generated.
 */
@EnableGeneratorController
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

    // Pointers into the recorded opencode output.
    private static final String INPUT = "/part/tokens/input";
    private static final String OUTPUT = "/part/tokens/output";
    private static final String TOOL_USES = "/tool_uses";

    private static final UsagePointers OPENCODE = new UsagePointers(INPUT, OUTPUT,
            "/part/tokens/cache/read", "/part/tokens/cache/write", "/part/tokens/reasoning", null, TOOL_USES);

    private static final UsagePointers CLAUDE = new UsagePointers("/message/usage/input_tokens",
            "/message/usage/output_tokens", "/message/usage/cache_read_input_tokens",
            "/message/usage/cache_creation_input_tokens", UsagePointers.NOT_APPLICABLE, null, TOOL_USES);

    private static JsonNode parse(String document) {
        return JsonDocument.parse(document.getBytes(UTF_8), "turn");
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static String pointer() {
        return "/" + lowerName();
    }

    @Nested
    @DisplayName("a recorded harness output")
    class RecordedOutput {

        @Test
        @DisplayName("lands in the metrics row with each column (golden recorded output)")
        void recordedHarnessOutputLandsInTheMetricsRowWithEachColumn() {
            var usage = OPENCODE.read(parse(OPENCODE_TURN));

            assertAll(
                    () -> assertEquals(Measurement.measured(1200), usage.inputTokens(),
                            "the input should be read from the output"),
                    () -> assertEquals(Measurement.measured(340), usage.outputTokens(),
                            "the output should be read from the output"),
                    () -> assertEquals(Measurement.measured(5000), usage.cacheReadInputTokens(),
                            "the cache read should be read from the output"),
                    () -> assertEquals(Measurement.measured(800), usage.cacheCreationInputTokens(),
                            "the cache write should be read from the output"),
                    () -> assertEquals(Measurement.measured(150), usage.reasoningTokens(),
                            "the reasoning should be read from the output"),
                    () -> assertEquals(Measurement.measured(7), usage.toolUses(),
                            "the tool uses should be read from the output"),
                    () -> assertNull(usage.costUsd(), "opencode should report no cost"),
                    () -> assertEquals(OptionalLong.of(7490), usage.totalTokens(),
                            "the total should sum the five components"),
                    () -> assertNull(usage.unmeasuredReason(),
                            "no unmeasured reason should be given"));
        }

        @Test
        @DisplayName("a reasoning figure the harness does not report apart is not_applicable and counts zero")
        void notApplicableCountsZero() {
            var usage = CLAUDE.read(parse(CLAUDE_TURN));

            assertAll(
                    () -> assertTrue(usage.reasoningTokens().isNotApplicable(),
                            "the reasoning should be not_applicable"),
                    () -> assertEquals(OptionalLong.of(900 + 410 + 3000 + 700), usage.totalTokens(),
                            "not_applicable should count zero in the total"),
                    () -> assertNull(usage.unmeasuredReason(),
                            "no unmeasured reason should be given"));
        }

        @Test
        @DisplayName("the cost stays recorded data, read from its own pointer")
        void cost() {
            // the prefix keeps the cost member apart from the "part" member of the document
            var costPointer = "/cost-" + lowerName();
            var pointers = new UsagePointers(INPUT, OUTPUT, "/part/tokens/cache/read", "/part/tokens/cache/write",
                    "/part/tokens/reasoning", costPointer, TOOL_USES);
            var cost = BigDecimal.valueOf(Generators.integers(1, 99_999).next(), 4);
            var document = "{\"part\":{\"tokens\":{\"input\":" + Generators.integers(0, 1000).next()
                    + ",\"output\":" + Generators.integers(0, 1000).next()
                    + ",\"reasoning\":0,\"cache\":{\"read\":0,\"write\":0}}},\"" + costPointer.substring(1) + "\":"
                    + cost.toPlainString() + ",\"tool_uses\":1}";

            var usage = pointers.read(parse(document));

            assertEquals(cost, usage.costUsd(), "the cost should be read from its pointer");
        }
    }

    @Nested
    @DisplayName("a missing figure")
    class MissingFigure {

        @Test
        @DisplayName("is recorded unmeasured with the reason not_reported_by_harness")
        void missingFigureIsRecordedUnmeasuredWithItsReason() {
            var pointers = new UsagePointers(INPUT, OUTPUT, null, null, null, null, TOOL_USES);

            var usage = pointers.read(parse(OPENCODE_TURN));

            assertAll(
                    () -> assertEquals(Measurement.unmeasured(), usage.cacheReadInputTokens(),
                            "the cache read should be unmeasured"),
                    () -> assertEquals(Measurement.unmeasured(), usage.cacheCreationInputTokens(),
                            "the cache write should be unmeasured"),
                    () -> assertEquals(Measurement.unmeasured(), usage.reasoningTokens(),
                            "the reasoning should be unmeasured"),
                    () -> assertEquals(UnmeasuredReason.NOT_REPORTED_BY_HARNESS, usage.unmeasuredReason(),
                            "the reason should be not_reported_by_harness"),
                    () -> assertEquals(OptionalLong.empty(), usage.totalTokens(), "the total should be unmeasured"));
        }

        @Test
        @DisplayName("a pointer that does not resolve is unmeasured, not a zero")
        void unresolvedPointer() {
            var pointers = new UsagePointers(INPUT, OUTPUT, "/part/tokens/cache/absent-" + lowerName(),
                    UsagePointers.NOT_APPLICABLE, UsagePointers.NOT_APPLICABLE, null, TOOL_USES);

            var usage = pointers.read(parse(OPENCODE_TURN));

            assertAll(
                    () -> assertEquals(Measurement.unmeasured(), usage.cacheReadInputTokens(),
                            "an unresolved pointer should be unmeasured"),
                    () -> assertEquals(OptionalLong.empty(), usage.totalTokens(), "the total should be unmeasured"));
        }

        @Test
        @DisplayName("a figure whose output is not an integer is unmeasured with the reason output_unparsed")
        void unparsedFigure() {
            var pointers = new UsagePointers(INPUT, OUTPUT, null, null, null, null, TOOL_USES);
            var document = "{\"part\":{\"tokens\":{\"input\":\"" + lowerName() + "\",\"output\":"
                    + Generators.integers(0, 1000).next() + "}},\"tool_uses\":1}";

            var usage = pointers.read(parse(document));

            assertAll(
                    () -> assertEquals(Measurement.unmeasured(), usage.inputTokens(),
                            "a text figure should be unmeasured"),
                    () -> assertEquals(UnmeasuredReason.OUTPUT_UNPARSED, usage.unmeasuredReason(),
                            "the reason should be output_unparsed"));
        }
    }

    @Nested
    @DisplayName("a turn without usage")
    class NoUsage {

        @Test
        @DisplayName("makes every applicable component unmeasured and the total unmeasured")
        void emptyTurn() {
            var pointers = new UsagePointers(INPUT, OUTPUT, null, null, null, null, null);

            var usage = pointers.read(parse("{\"type\":\"" + lowerName() + "\"}"));

            assertAll(
                    () -> assertEquals(Measurement.unmeasured(), usage.inputTokens(),
                            "the input should be unmeasured"),
                    () -> assertEquals(Measurement.unmeasured(), usage.outputTokens(),
                            "the output should be unmeasured"),
                    () -> assertFalse(usage.totalTokens().isPresent(), "the total should be unmeasured"));
        }
    }

    @Nested
    @DisplayName("the sum of turns")
    class Sum {

        @Test
        @DisplayName("is measured per component when every turn measured it, and unmeasured when one did not")
        void sumOfTurns() {
            long input1 = Generators.longs(0L, 10_000L).next();
            long output1 = Generators.longs(0L, 10_000L).next();
            long tools1 = Generators.longs(0L, 50L).next();
            long input2 = Generators.longs(0L, 10_000L).next();
            long output2 = Generators.longs(0L, 10_000L).next();
            long tools2 = Generators.longs(0L, 50L).next();
            var measured = new HarnessUsage(Measurement.measured(input1), Measurement.measured(output1),
                    Measurement.notApplicable(), Measurement.notApplicable(), Measurement.notApplicable(),
                    Measurement.measured(tools1), null, null);
            var other = new HarnessUsage(Measurement.measured(input2), Measurement.measured(output2),
                    Measurement.notApplicable(), Measurement.notApplicable(), Measurement.notApplicable(),
                    Measurement.measured(tools2), null, null);

            var sum = HarnessUsage.sum(List.of(measured, other));
            var partial = HarnessUsage.sum(List.of(measured, HarnessUsage.unparsed()));

            assertAll(
                    () -> assertEquals(Measurement.measured(input1 + input2), sum.inputTokens(),
                            "the inputs should be summed"),
                    () -> assertEquals(Measurement.measured(output1 + output2), sum.outputTokens(),
                            "the outputs should be summed"),
                    () -> assertEquals(Measurement.measured(tools1 + tools2), sum.toolUses(),
                            "the tool uses should be summed"),
                    () -> assertEquals(Measurement.notApplicable(), sum.reasoningTokens(),
                            "the reasoning should stay not_applicable"),
                    () -> assertNull(sum.unmeasuredReason(),
                            "no unmeasured reason should be given"),
                    () -> assertEquals(OptionalLong.of(input1 + input2 + output1 + output2), sum.totalTokens(),
                            "the total should sum both turns"),
                    () -> assertEquals(Measurement.unmeasured(), partial.inputTokens(),
                            "an unparsed turn should leave input unmeasured"),
                    () -> assertEquals(UnmeasuredReason.OUTPUT_UNPARSED, partial.unmeasuredReason(),
                            "the reason should be output_unparsed"));
        }
    }

    @Nested
    @DisplayName("a figure of the schema's measure")
    class Measure {

        @Test
        @DisplayName("tool uses are never not_applicable")
        void toolUsesRefused() {
            var unmeasured = Measurement.unmeasured();
            var notApplicable = Measurement.notApplicable();
            var input = pointer();
            var output = pointer();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new UsagePointers(input, output, null,
                                    null, null, null, UsagePointers.NOT_APPLICABLE),
                            "a not_applicable tool-use pointer should fail"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new HarnessUsage(unmeasured, unmeasured,
                            notApplicable, notApplicable, notApplicable, notApplicable, null,
                            null), "a not_applicable tool-use figure should fail"));
        }
    }

    @Nested
    @DisplayName("the profile declaration")
    class Declaration {

        @Test
        @DisplayName("is read from the headless.usage block, a null member meaning not reported (member names are wire)")
        void readBlock() {
            var input = pointer();
            var output = pointer();
            var reasoning = pointer();
            var toolUses = pointer();
            var block = JsonDocument.parse(("{\"input_tokens\":\"" + input + "\",\"output_tokens\":\"" + output
                    + "\",\"cache_read\":null,\"cache_creation\":\"not_applicable\",\"reasoning\":\"" + reasoning
                    + "\",\"cost_usd\":null,\"tool_uses\":\"" + toolUses + "\"}").getBytes(UTF_8), "usage");

            var pointers = UsagePointers.of(block);

            assertAll(
                    () -> assertEquals(input, pointers.inputTokens(), "the input pointer should be read"),
                    () -> assertEquals(output, pointers.outputTokens(), "the output pointer should be read"),
                    () -> assertNull(pointers.cacheRead(), "a null cache read should be not reported"),
                    () -> assertEquals(UsagePointers.NOT_APPLICABLE, pointers.cacheCreation(),
                            "the cache creation should be not_applicable"),
                    () -> assertEquals(reasoning, pointers.reasoning(), "the reasoning pointer should be read"),
                    () -> assertNull(pointers.costUsd(), "a null cost should be not reported"),
                    () -> assertEquals(toolUses, pointers.toolUses(), "the tool-use pointer should be read"));
        }

        @Test
        @DisplayName("refuses not_applicable for input and output tokens, and a missing member")
        void refusals() {
            var notApplicable = UsagePointers.NOT_APPLICABLE;
            var input = pointer();
            var output = pointer();
            var withoutMember = JsonDocument.parse(("{\"input_tokens\":\"" + input + "\"}").getBytes(UTF_8),
                    "usage");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new UsagePointers(notApplicable, output, null, null, null, null, null),
                            "a not_applicable input should be refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new UsagePointers(input, notApplicable, null, null, null, null, null),
                            "a not_applicable output should be refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> UsagePointers.of(withoutMember),
                            "a block without a member should be refused"));
        }
    }
}
