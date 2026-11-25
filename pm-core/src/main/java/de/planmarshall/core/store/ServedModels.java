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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * The three-valued value of a task row's {@code served_models} column (PM-WORK-7, The Model That Answered): the distinct
 * models the row's turns were answered by, in order of first appearance, or {@code unmeasured} when no turn reported one.
 * <p>
 * The column has the same rule as every figure of the row: a harness that reports no model is recorded {@code
 * unmeasured}, never with the launch model and never as an empty list. An empty list is therefore not a value; the
 * constant {@link #unmeasured()} is.
 *
 * @param identifiers the distinct identifiers in order of first appearance, or {@code null} when none was reported
 * @since 0.1
 */
public record ServedModels(@Nullable List<String> identifiers) {

    /** The one value of a row no turn reported a model for. */
    private static final ServedModels UNMEASURED = new ServedModels(null);

    /** The longest identifier a value of the column admits. */
    public static final int MAX_MODEL = MetricsRow.MAX_MODEL;

    /**
     * @throws IllegalArgumentException if the list is empty, because an empty list is not a value of the column
     * @throws NullPointerException     if the list holds {@code null}
     */
    public ServedModels {
        if (identifiers != null) {
            if (identifiers.isEmpty()) {
                throw new IllegalArgumentException("A measured 'served_models' is a non-empty list");
            }
            for (var model : identifiers) {
                if (model.isEmpty() || model.length() > MetricsRow.MAX_MODEL) {
                    throw new IllegalArgumentException(
                            "A served model has 1 to %d characters".formatted(MetricsRow.MAX_MODEL));
                }
            }
            identifiers = List.copyOf(new LinkedHashSet<>(identifiers));
        }
    }

    /**
     * @return the value of a row no turn reported a model for
     */
    public static ServedModels unmeasured() {
        return UNMEASURED;
    }

    /**
     * @param models the models the row's turns were answered by, in the order they appeared
     * @return the value naming the distinct identifiers in order of first appearance
     * @throws NullPointerException     if the list is {@code null}
     * @throws IllegalArgumentException if the list is empty
     */
    public static ServedModels of(List<String> models) {
        Objects.requireNonNull(models, "models");
        return new ServedModels(models);
    }

    /**
     * @return whether no turn of the row reported a model
     */
    public boolean isUnmeasured() {
        return identifiers == null;
    }

    /**
     * @return the distinct identifiers in order of first appearance; empty when the value is {@link #unmeasured()}
     */
    public Optional<List<String>> measured() {
        return Optional.ofNullable(identifiers);
    }
}
