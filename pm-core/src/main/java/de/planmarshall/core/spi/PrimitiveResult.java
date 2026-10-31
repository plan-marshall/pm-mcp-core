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
import lombok.Builder;

/**
 * What one execution of a primitive returns (PM-IMPL-5): one of its closed outcomes, the facts it changed, the job it
 * started, if any, and the reason of the outcome when the outcome declares reasons.
 *
 * @param outcome the outcome of the execution
 * @param updatedFacts the facts the execution changed, by name; empty when it changed none. The record keeps an
 *        unmodifiable copy in the order of the argument, so a later change of the argument does not reach it. A
 *        fact has no {@code null} name and no {@code null} value.
 * @param spawnedJobId the identifier of the job the execution started; empty when it started none
 * @param reason the reason of the outcome; empty when the outcome declares none, and present exactly when it does.
 *        A present reason is a constant of the enum {@link PrimitiveOutcome#reasonType()} declares. The pairing and
 *        the closed table to conditions are stated in
 *        {@code doc/specification/workflow-dsl/05-primitives.adoc#outcomes-and-reasons}.
 * @param <R> the closed outcome enum of the primitive
 * @since 0.1
 */
@Builder
public record PrimitiveResult<R extends Enum<R> & PrimitiveOutcome>(
R outcome,
Map<String, Object> updatedFacts,
Optional<String> spawnedJobId,
Optional<OutcomeReason> reason) {

    /**
     * The form every existing call site uses: a result whose outcome declares no reason.
     *
     * @param outcome the outcome of the execution
     * @param updatedFacts the facts the execution changed
     * @param spawnedJobId the identifier of the job the execution started, if any
     * @throws IllegalArgumentException if the outcome declares a reason type, because then a reason is required
     */
    public PrimitiveResult(R outcome, Map<String, Object> updatedFacts, Optional<String> spawnedJobId) {
        this(outcome, updatedFacts, spawnedJobId, Optional.empty());
    }

    /**
     * @throws NullPointerException if a component, a fact name or a fact value is {@code null}
     * @throws IllegalArgumentException if the reason does not pair with the outcome: present when the outcome
     *         declares no reason type, absent when it declares one, or not a constant of the declared type
     */
    @SuppressWarnings({"java:S2583", "java:S2789"})
    public PrimitiveResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(updatedFacts, "updatedFacts");
        Objects.requireNonNull(spawnedJobId, "spawnedJobId");
        Objects.requireNonNull(reason, "reason");
        var copy = new LinkedHashMap<String, Object>();
        updatedFacts.forEach((name, value) -> copy.put(
                Objects.requireNonNull(name, "name of a fact"),
                Objects.requireNonNull(value, "value of a fact")));
        updatedFacts = Collections.unmodifiableMap(copy);
        var declared = outcome.reasonType();
        // A primitive compiled against a raw PrimitiveOutcome can answer a null Optional; it declares none.
        if (declared == null) {
            declared = Optional.empty();
        }
        if (reason.isPresent() != declared.isPresent()) {
            throw new IllegalArgumentException(reason.isPresent()
                    ? "the outcome %s declares no reason type, so it cannot carry a reason".formatted(outcome)
                    : "the outcome %s declares the reason type %s, so it must carry a reason"
                    .formatted(outcome, declared.orElseThrow().getName()));
        }
        if (reason.isPresent() && !declared.orElseThrow().isInstance(reason.get())) {
            throw new IllegalArgumentException("the reason %s is not a constant of the declared reason type %s"
                    .formatted(reason.get(), declared.orElseThrow().getName()));
        }
    }
}
