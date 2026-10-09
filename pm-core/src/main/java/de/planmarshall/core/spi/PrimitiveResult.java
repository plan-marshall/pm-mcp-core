/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What one execution of a primitive returns (PM-IMPL-5): one of its closed outcomes, the facts it changed, and the
 * job it started, if any.
 *
 * @param outcome the outcome of the execution
 * @param updatedFacts the facts the execution changed, by name; empty when it changed none. The record keeps an
 *        unmodifiable copy in the order of the argument, so a later change of the argument does not reach it. A
 *        fact has no {@code null} name and no {@code null} value.
 * @param spawnedJobId the identifier of the job the execution started; empty when it started none
 * @param <R> the closed outcome enum of the primitive
 * @since 0.1
 */
public record PrimitiveResult<R extends Enum<R> & PrimitiveOutcome>(
        R outcome,
        Map<String, Object> updatedFacts,
        Optional<String> spawnedJobId) {

    /**
     * @throws NullPointerException if a component, a fact name or a fact value is {@code null}
     */
    public PrimitiveResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(updatedFacts, "updatedFacts");
        Objects.requireNonNull(spawnedJobId, "spawnedJobId");
        var copy = new LinkedHashMap<String, Object>();
        updatedFacts.forEach((name, value) -> copy.put(
                Objects.requireNonNull(name, "name of a fact"),
                Objects.requireNonNull(value, "value of a fact")));
        updatedFacts = Collections.unmodifiableMap(copy);
    }
}
