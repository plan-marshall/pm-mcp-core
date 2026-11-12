/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.envelope;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * The size-to-token table: the predicted tokens of each {@link CostSize} and the basis the values rest on.
 * <p>
 * The table is a value the caller passes in; it is never configured and never read from a store. The compiled defaults
 * are {@link #COMPILED_DEFAULTS} with the basis {@link Basis#UNCALIBRATED}; a table recalibrated from measured metrics
 * carries {@link Basis#CALIBRATED}. A predicted cost is a lookup in the table, never a computation, and carries the
 * basis of the table it came from.
 * <p>
 * Specification: {@code doc/specification/model-work/06-envelopes-questions-deadlines.adoc}, § Cost Sizing, item 3.
 *
 * @param tokens the predicted tokens per size; every size is present and no value is negative
 * @param basis  what the values rest on
 */
public record SizeTokenTable(Map<CostSize, Long> tokens, Basis basis) {

    /** The compiled defaults: 5K, 25K, 60K, 130K, 260K and 520K tokens from {@code XS} to {@code XXL}. */
    public static final SizeTokenTable COMPILED_DEFAULTS = new SizeTokenTable(Map.of(
            CostSize.XS, 5_000L,
            CostSize.S, 25_000L,
            CostSize.M, 60_000L,
            CostSize.L, 130_000L,
            CostSize.XL, 260_000L,
            CostSize.XXL, 520_000L), Basis.UNCALIBRATED);

    /** What the values of a table rest on, the {@code predicted_cost_basis} of a task record. */
    public enum Basis {

        /** The compiled defaults, not yet recalibrated from measured envelope tasks. */
        UNCALIBRATED,

        /** Values recalibrated from a measured population of envelope tasks. */
        CALIBRATED
    }

    /**
     * Checks that the table names every size with a value of zero or more and keeps an unmodifiable copy.
     *
     * @param tokens the predicted tokens per size
     * @param basis  what the values rest on
     * @throws IllegalArgumentException if a size is missing or a value is negative
     */
    public SizeTokenTable {
        Objects.requireNonNull(basis, "basis");
        var copy = new EnumMap<CostSize, Long>(CostSize.class);
        copy.putAll(Objects.requireNonNull(tokens, "tokens"));
        for (CostSize size : CostSize.values()) {
            Long value = copy.get(size);
            if (value == null) {
                throw new IllegalArgumentException("The size-to-token table has no value for " + size);
            }
            if (value < 0) {
                throw new IllegalArgumentException("The size-to-token table has a negative value for " + size);
            }
        }
        tokens = Map.copyOf(copy);
    }

    /**
     * Looks up the predicted tokens of a size.
     *
     * @param size the cost size
     * @return the predicted tokens of the size in this table
     */
    public long tokensFor(CostSize size) {
        return tokens.get(Objects.requireNonNull(size, "size"));
    }
}
