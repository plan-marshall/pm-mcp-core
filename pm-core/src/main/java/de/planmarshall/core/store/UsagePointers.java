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

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.JsonPointer;
import de.planmarshall.core.profile.ProfileRefusal;
import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;

/**
 * The usage pointers of a client profile's {@code headless.usage} block (PM-CLIENT-2, Client Profiles § Tokens, Not
 * Money): where a harness's output carries each usage figure.
 * <p>
 * A pointer is a JSON Pointer into a turn line and fills the metrics column of the same name, except
 * {@code cache_read} ({@code cache_read_input_tokens}), {@code cache_creation}
 * ({@code cache_creation_input_tokens}) and {@code reasoning} ({@code reasoning_tokens}). A pointer {@code null} means
 * the harness does not report the figure, which is recorded {@code unmeasured} with the reason
 * {@code not_reported_by_harness}; the value {@value #NOT_APPLICABLE} means the figure does not exist for the harness
 * by design, which is recorded {@code not_applicable} and counts {@code 0}. There is no pointer for the total: it is
 * derived from the five components.
 * <p>
 * The reader never turns a figure into a default: a pointer that does not resolve in the turn, or resolves to
 * something that is not a non-negative integer, yields {@code unmeasured} and names why. That is the one place the
 * three-valued measurement is decided from a harness output.
 *
 * @param inputTokens      the pointer of the input tokens, or {@code null}
 * @param outputTokens     the pointer of the output tokens, or {@code null}
 * @param cacheRead        the pointer of the cache-read input tokens, {@value #NOT_APPLICABLE}, or {@code null}
 * @param cacheCreation    the pointer of the cache-creation input tokens, {@value #NOT_APPLICABLE}, or {@code null}
 * @param reasoning        the pointer of the reasoning tokens, {@value #NOT_APPLICABLE}, or {@code null}
 * @param costUsd          the pointer of the harness-reported cost, or {@code null}
 * @param toolUses         the pointer of the tool calls, or {@code null}
 * @since 0.1
 */
public record UsagePointers(String inputTokens, String outputTokens, String cacheRead, String cacheCreation,
String reasoning, String costUsd, String toolUses) {

    /** The declaration of a figure that does not exist for a harness by design. */
    public static final String NOT_APPLICABLE = "not_applicable";

    /**
     * @throws IllegalArgumentException if input or output tokens declare {@value #NOT_APPLICABLE}, or
     *                                  {@code tool_uses} does
     */
    public UsagePointers {
        if (NOT_APPLICABLE.equals(inputTokens) || NOT_APPLICABLE.equals(outputTokens)
                || NOT_APPLICABLE.equals(toolUses)) {
            throw new IllegalArgumentException(
                    "'input_tokens', 'output_tokens' and 'tool_uses' are never not_applicable");
        }
    }

    /**
     * @param usage the {@code headless.usage} object of a client profile
     * @return the pointers of the block
     * @throws IllegalArgumentException if the value is no object or a member is missing
     */
    public static UsagePointers of(JsonNode usage) {
        Objects.requireNonNull(usage, "usage");
        if (!usage.isObject()) {
            throw new IllegalArgumentException("'headless.usage' must be an object");
        }
        return new UsagePointers(member(usage, "input_tokens"), member(usage, "output_tokens"),
                member(usage, "cache_read"), member(usage, "cache_creation"), member(usage, "reasoning"),
                member(usage, "cost_usd"), member(usage, "tool_uses"));
    }

    /**
     * Reads one turn of a harness output through the pointers.
     *
     * @param turn one parsed turn line of the harness output
     * @return the usage of the turn, each figure measured, not applicable, or unmeasured with its reason
     */
    public HarnessUsage read(JsonNode turn) {
        Objects.requireNonNull(turn, "turn");
        var unparsed = new boolean[1];
        var input = component(turn, inputTokens, unparsed);
        var output = component(turn, outputTokens, unparsed);
        var cacheReadTokens = component(turn, cacheRead, unparsed);
        var cacheCreationTokens = component(turn, cacheCreation, unparsed);
        var reasoningValues = component(turn, reasoning, unparsed);
        var tools = component(turn, toolUses, unparsed);
        var cost = cost(turn, unparsed);
        var reason = reason(unparsed, input, output, cacheReadTokens, cacheCreationTokens, reasoningValues, tools);
        return new HarnessUsage(input, output, cacheReadTokens, cacheCreationTokens, reasoningValues, tools, cost,
                reason);
    }

    private static String member(JsonNode usage, String name) {
        var value = usage.member(name).orElseThrow(
                () -> new IllegalArgumentException("'headless.usage.%s' is missing".formatted(name)));
        if (value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException("'headless.usage.%s' must be a pointer or null".formatted(name));
        }
        return value.text().orElseThrow();
    }

    private static Measurement component(JsonNode turn, String pointer, boolean[] unparsed) {
        if (pointer == null) {
            return Measurement.unmeasured();
        }
        if (NOT_APPLICABLE.equals(pointer)) {
            return Measurement.notApplicable();
        }
        final JsonNode value;
        try {
            value = JsonPointer.resolve(turn, pointer, "harness output");
        } catch (ProfileRefusal _) {
            return Measurement.unmeasured();
        }
        if (!value.isInteger() || value.integerValue().orElseThrow() < 0) {
            unparsed[0] = true;
            return Measurement.unmeasured();
        }
        return Measurement.measured(value.integerValue().orElseThrow());
    }

    private Optional<BigDecimal> cost(JsonNode turn, boolean[] unparsed) {
        if (costUsd == null) {
            return Optional.empty();
        }
        final JsonNode value;
        try {
            value = JsonPointer.resolve(turn, costUsd, "harness output");
        } catch (ProfileRefusal _) {
            return Optional.empty();
        }
        if (!value.isNumber() || value.decimalValue().orElseThrow().signum() < 0) {
            unparsed[0] = true;
            return Optional.empty();
        }
        return value.decimalValue();
    }

    private static Optional<UnmeasuredReason> reason(boolean[] unparsed, Measurement... measurements) {
        if (unparsed[0]) {
            return Optional.of(UnmeasuredReason.OUTPUT_UNPARSED);
        }
        for (var measurement : measurements) {
            if (measurement.state() == Measurement.State.UNMEASURED) {
                return Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS);
            }
        }
        return Optional.empty();
    }
}
