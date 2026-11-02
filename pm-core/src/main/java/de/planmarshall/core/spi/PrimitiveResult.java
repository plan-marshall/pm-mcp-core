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
    @SuppressWarnings({"java:S2583", "java:S2789", "java:S1905"})
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
        // A primitive compiled against a raw PrimitiveOutcome can answer a null Optional or a payload that is no
        // Class; both are refused as a pairing error instead of escaping as a ClassCastException. The cast to the
        // wildcard Optional is deliberate: it erases the implicit cast to Class, so the payload is inspected first.
        var declared = (Optional<?>) outcome.reasonType();
        if (declared == null) {
            declared = Optional.empty();
        }
        var declaredType = declared.map(payload -> reasonTypeOf(outcome, payload)).orElse(null);
        if (reason.isPresent() != (declaredType != null)) {
            throw new IllegalArgumentException(reason.isPresent()
                    ? "the outcome %s declares no reason type, so it cannot carry a reason".formatted(outcome)
                    : "the outcome %s declares the reason type %s, so it must carry a reason"
                    .formatted(outcome, declaredType.getName()));
        }
        if (reason.isPresent() && !declaredType.isInstance(reason.get())) {
            throw new IllegalArgumentException("the reason %s is not a constant of the declared reason type %s"
                    .formatted(reason.get(), declaredType.getName()));
        }
    }

    private static Class<?> reasonTypeOf(PrimitiveOutcome outcome, Object payload) {
        if (!(payload instanceof Class<?> type)) {
            throw new IllegalArgumentException(
                    "the outcome %s declares a reason type that is no class".formatted(outcome));
        }
        return type;
    }

    /**
     * @return a builder whose optional components default to empty, so a reasonless result names only its outcome
     * @param <R> the closed outcome enum of the primitive
     */
    public static <R extends Enum<R> & PrimitiveOutcome> Builder<R> builder() {
        return new Builder<>();
    }

    /**
     * Builds a result component by component. Both optional components default to empty and the facts to an empty
     * map, so a caller names only what it has; the canonical constructor still enforces the pairing invariant.
     *
     * @param <R> the closed outcome enum of the primitive
     */
    public static final class Builder<R extends Enum<R> & PrimitiveOutcome> {

        private R outcome;
        private Map<String, Object> updatedFacts = Map.of();
        private Optional<String> spawnedJobId = Optional.empty();
        private Optional<OutcomeReason> reason = Optional.empty();

        /**
         * The builder collects the required outcome through its {@code outcome} setter before {@link #build()} hands
         * it to the canonical constructor, so the field is unset until then by design.
         */
        @SuppressWarnings("java:S2637")
        private Builder() {
        }

        /**
         * @param outcome the outcome of the execution
         * @return this builder
         */
        public Builder<R> outcome(R outcome) {
            this.outcome = outcome;
            return this;
        }

        /**
         * @param updatedFacts the facts the execution changed
         * @return this builder
         */
        public Builder<R> updatedFacts(Map<String, Object> updatedFacts) {
            this.updatedFacts = updatedFacts;
            return this;
        }

        /**
         * @param spawnedJobId the identifier of the job the execution started, if any
         * @return this builder
         */
        public Builder<R> spawnedJobId(Optional<String> spawnedJobId) {
            this.spawnedJobId = spawnedJobId;
            return this;
        }

        /**
         * @param reason the reason of the outcome, if the outcome declares one
         * @return this builder
         */
        public Builder<R> reason(Optional<OutcomeReason> reason) {
            this.reason = reason;
            return this;
        }

        /**
         * @return the result the named components describe
         * @throws NullPointerException if a component, a fact name or a fact value is {@code null}
         * @throws IllegalArgumentException if the reason does not pair with the outcome
         */
        public PrimitiveResult<R> build() {
            return new PrimitiveResult<>(outcome, updatedFacts, spawnedJobId, reason);
        }
    }
}
