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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Function;

/**
 * The usage of one harness output, as the profile's {@code headless.usage} pointers read it (PM-CLIENT-2, PM-WF-14):
 * the five token components, {@code tool_uses} and the harness-reported cost, each with the three-valued measurement of
 * {@link Measurement}.
 * <p>
 * The total is not reported by a harness and is never parsed: it is the sum of the five token components, a
 * {@code not_applicable} one counting {@code 0}, when every applicable component is measured, and {@code unmeasured}
 * otherwise. A derived total of {@code 0} is recorded {@code unmeasured} too, because a worker that ran consumed
 * tokens and a total keeps its lower bound of {@code 1} (Model Work § Worker Result &amp; Usage Parsing).
 * <p>
 * The reason a figure is unmeasured is one per usage, not one per column: a harness that does not report a figure, an
 * output that could not be parsed and a worker the supervisor ended are three distinct reasons for the same row.
 *
 * @param inputTokens             the input tokens; never {@code not_applicable}
 * @param outputTokens            the output tokens; never {@code not_applicable}
 * @param cacheReadInputTokens    the cache-read input tokens
 * @param cacheCreationInputTokens the cache-write input tokens
 * @param reasoningTokens         the reasoning or thinking tokens a harness reports apart from its output
 * @param toolUses                the tool calls the harness reports
 * @param costUsd                 the harness-reported cost; empty when none was reported
 * @param unmeasuredReason        why a figure of the usage is unmeasured; empty when every applicable figure is
 *                                measured
 * @since 0.1
 */
public record HarnessUsage(Measurement inputTokens, Measurement outputTokens, Measurement cacheReadInputTokens,
Measurement cacheCreationInputTokens, Measurement reasoningTokens, Measurement toolUses,
Optional<BigDecimal> costUsd, Optional<UnmeasuredReason> unmeasuredReason) {

    /** The reason a figure of a metrics row is unmeasured. The set is closed. */
    public enum UnmeasuredReason {
        /** The harness does not report the figure. */
        NOT_REPORTED_BY_HARNESS("not_reported_by_harness"),
        /** The harness output could not be parsed. */
        OUTPUT_UNPARSED("output_unparsed"),
        /** The worker was ended before it finished, so its run usage is unknown. */
        TERMINATED("terminated");

        private final String wireName;

        UnmeasuredReason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<UnmeasuredReason> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if input, output or tool uses are {@code not_applicable}
     */
    public HarnessUsage {
        Objects.requireNonNull(inputTokens, "inputTokens");
        Objects.requireNonNull(outputTokens, "outputTokens");
        Objects.requireNonNull(cacheReadInputTokens, "cacheReadInputTokens");
        Objects.requireNonNull(cacheCreationInputTokens, "cacheCreationInputTokens");
        Objects.requireNonNull(reasoningTokens, "reasoningTokens");
        Objects.requireNonNull(toolUses, "toolUses");
        Objects.requireNonNull(costUsd, "costUsd");
        Objects.requireNonNull(unmeasuredReason, "unmeasuredReason");
        if (inputTokens.isNotApplicable() || outputTokens.isNotApplicable()) {
            throw new IllegalArgumentException("'input_tokens' and 'output_tokens' are never not_applicable");
        }
        if (toolUses.isNotApplicable()) {
            throw new IllegalArgumentException("'tool_uses' is never not_applicable");
        }
    }

    /** @return the usage of a run whose output could not be parsed */
    public static HarnessUsage unparsed() {
        return unmeasured(UnmeasuredReason.OUTPUT_UNPARSED);
    }

    /**
     * @param reason why the figures are unknown
     * @return the usage with every applicable figure unmeasured for that reason
     */
    public static HarnessUsage unmeasured(UnmeasuredReason reason) {
        var unmeasured = Measurement.unmeasured();
        return new HarnessUsage(unmeasured, unmeasured, unmeasured, unmeasured, unmeasured, unmeasured,
                Optional.empty(), Optional.of(reason));
    }

    /** @return the usage of a worker the supervisor ended before it finished */
    public static HarnessUsage terminated() {
        return unmeasured(UnmeasuredReason.TERMINATED);
    }

    /**
     * The usage of one answer of an endpoint harness (Client Profiles § The Request): the {@code usage} object of the
     * answer carries a prompt and a completion count, and a count the answer lacks is {@code unmeasured}. The endpoint
     * is sent no tool, so it reports no tool use; the cache and reasoning figures are not part of the protocol's
     * {@code usage} object and are {@code not_applicable}.
     *
     * @param promptTokens     the answer's {@code prompt_tokens}; empty when the answer lacks the figure
     * @param completionTokens the answer's {@code completion_tokens}; empty when the answer lacks the figure
     * @return the usage of the answer
     */
    public static HarnessUsage fromEndpoint(OptionalLong promptTokens, OptionalLong completionTokens) {
        Objects.requireNonNull(promptTokens, "promptTokens");
        Objects.requireNonNull(completionTokens, "completionTokens");
        var missing = promptTokens.isEmpty() || completionTokens.isEmpty();
        return new HarnessUsage(
                promptTokens.isPresent() ? Measurement.measured(promptTokens.getAsLong()) : Measurement.unmeasured(),
                completionTokens.isPresent() ? Measurement.measured(completionTokens.getAsLong())
                        : Measurement.unmeasured(),
                Measurement.notApplicable(), Measurement.notApplicable(), Measurement.notApplicable(),
                Measurement.measured(0), Optional.empty(),
                missing ? Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS) : Optional.empty());
    }

    /**
     * @return the usage of a task that never ran: every figure unmeasured, with no reason, because there was no
     *         attempt to measure
     */
    public static HarnessUsage none() {
        var unmeasured = Measurement.unmeasured();
        return new HarnessUsage(unmeasured, unmeasured, unmeasured, unmeasured, unmeasured, unmeasured,
                Optional.empty(), Optional.empty());
    }

    /**
     * @return the total tokens: the sum of the five components, a {@code not_applicable} one counting {@code 0}, when
     *         every applicable component is measured and the sum is at least {@code 1}; empty, which is recorded
     *         {@code unmeasured}, otherwise
     */
    public OptionalLong totalTokens() {
        var components = List.of(inputTokens, outputTokens, cacheReadInputTokens, cacheCreationInputTokens,
                reasoningTokens);
        long sum = 0;
        for (var component : components) {
            if (component.isMeasured()) {
                sum += component.value();
            } else if (!component.isNotApplicable()) {
                return OptionalLong.empty();
            }
        }
        return sum < 1 ? OptionalLong.empty() : OptionalLong.of(sum);
    }

    /**
     * The split a view shows as {@code tokens_in} (PM-WF-14, Client Profiles § Tokens, Not Money): the input, the
     * cache-read and the cache-creation tokens, a {@code not_applicable} one counting {@code 0}. It is derived on read
     * and never persisted; a component that is unmeasured makes the split unmeasured, because a partial sum is never
     * recorded.
     *
     * @return the input split, or empty when a component of it is unmeasured
     */
    public OptionalLong tokensIn() {
        return splitOf(inputTokens, cacheReadInputTokens, cacheCreationInputTokens);
    }

    /**
     * The split a view shows as {@code tokens_out} (PM-WF-14, Client Profiles § Tokens, Not Money): the output and the
     * reasoning tokens, a {@code not_applicable} one counting {@code 0}. It is derived on read and never persisted; a
     * component that is unmeasured makes the split unmeasured.
     *
     * @return the output split, or empty when a component of it is unmeasured
     */
    public OptionalLong tokensOut() {
        return splitOf(outputTokens, reasoningTokens);
    }

    private static OptionalLong splitOf(Measurement... components) {
        long sum = 0;
        for (var component : components) {
            if (component.isMeasured()) {
                sum += component.value();
            } else if (!component.isNotApplicable()) {
                return OptionalLong.empty();
            }
        }
        return OptionalLong.of(sum);
    }

    /**
     * @param usages the usages of the turns of one task, in their order
     * @return the sum of the turns, component by component: measured when every turn measured it, {@code
     *         not_applicable} when every turn declares it absent, and {@code unmeasured} otherwise; the cost is summed
     *         only when every turn reported one
     */
    public static HarnessUsage sum(List<HarnessUsage> usages) {
        Objects.requireNonNull(usages, "usages");
        if (usages.isEmpty()) {
            return unmeasured(UnmeasuredReason.NOT_REPORTED_BY_HARNESS);
        }
        var reason = usages.stream().map(HarnessUsage::unmeasuredReason).flatMap(Optional::stream).findFirst();
        var cost = usages.stream().map(HarnessUsage::costUsd).allMatch(Optional::isPresent)
                ? Optional.of(usages.stream().map(usage -> usage.costUsd().orElseThrow()).reduce(BigDecimal.ZERO,
                BigDecimal::add))
                : Optional.<BigDecimal>empty();
        return new HarnessUsage(sumOf(usages, HarnessUsage::inputTokens), sumOf(usages, HarnessUsage::outputTokens),
                sumOf(usages, HarnessUsage::cacheReadInputTokens), sumOf(usages, HarnessUsage::cacheCreationInputTokens),
                sumOf(usages, HarnessUsage::reasoningTokens), sumOf(usages, HarnessUsage::toolUses), cost, reason);
    }

    private static Measurement sumOf(List<HarnessUsage> usages,
            Function<HarnessUsage, Measurement> component) {
        long sum = 0;
        var measured = 0;
        var unknown = 0;
        for (var usage : usages) {
            var measurement = component.apply(usage);
            if (measurement.isMeasured()) {
                sum += measurement.value();
                measured++;
            } else if (!measurement.isNotApplicable()) {
                unknown++;
            }
        }
        if (unknown > 0) {
            return Measurement.unmeasured();
        }
        if (measured == 0) {
            return Measurement.notApplicable();
        }
        return Measurement.measured(sum);
    }
}
