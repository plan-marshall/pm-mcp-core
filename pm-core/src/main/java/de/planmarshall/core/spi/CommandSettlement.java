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

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

/**
 * What one run of a primitive settled to (PM-IMPL-5): the {@link Command} that ran and the result it returned, as
 * flat plain values. The engine appends one settlement per primitive it runs, on the routed exit and on the exit that
 * raises a condition, and returns them on the step of the advance; it never persists them.
 * <p>
 * The outcome and the reason are wire names. An outcome follows the grammar of an outcome's wire name,
 * {@code [a-z][a-z0-9_-]{0,31}}; a reason follows the narrower grammar the history line can store,
 * {@code [a-z][a-z0-9_]{0,31}}, which admits no hyphen. A raised condition is one a primitive can originate
 * ({@link OutcomeReason#MAPPABLE_CONDITION_KINDS}) and is only ever raised for a reason.
 * <p>
 * The facts are the raw facts the result changed, kept as an unmodifiable copy in the order of the argument, exactly
 * as {@link PrimitiveResult} keeps them. A fact value may hold submitted text, so {@link #toString()} prints the fact
 * names only and no exception message of the constructor quotes a value.
 * <p>
 * The record has eight components, so it carries a {@code @Builder} beside its validating canonical constructor.
 *
 * @param command         the command that ran
 * @param outcome         the wire name of the outcome the primitive returned
 * @param outcomeClass    the class of the outcome
 * @param reason          the wire name of the reason of the outcome, or {@code null} when the outcome declares none
 * @param facts           the facts the run changed, by name; empty when it changed none. A fact has no {@code null}
 *                        name and no {@code null} value
 * @param spawnedJobId    the id of the job the run started, or {@code null} when it started none
 * @param raisedCondition the kind of the condition the reason raised, or {@code null} when the run raised none
 * @param settledAt       the instant the engine settled the run, read from its clock
 * @since 0.1
 */
@Builder
public record CommandSettlement(
Command command,
String outcome,
OutcomeClass outcomeClass,
@Nullable String reason,
Map<String, Object> facts,
@Nullable String spawnedJobId,
@Nullable String raisedCondition,
Instant settledAt) {

    private static final Pattern OUTCOME_WIRE_NAME = Pattern.compile("[a-z][a-z0-9_-]{0,31}");

    private static final Pattern REASON_WIRE_NAME = Pattern.compile("[a-z][a-z0-9_]{0,31}");

    /**
     * @throws NullPointerException     if a required component, a fact name or a fact value is {@code null}
     * @throws IllegalArgumentException if the outcome or the reason breaks its grammar, if the raised condition is no
     *                                  kind a primitive can originate or comes without a reason
     */
    public CommandSettlement {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(outcomeClass, "outcomeClass");
        Objects.requireNonNull(facts, "facts");
        Objects.requireNonNull(settledAt, "settledAt");
        if (!OUTCOME_WIRE_NAME.matcher(outcome).matches()) {
            throw new IllegalArgumentException(
                    "the outcome '%s' is not a wire name ([a-z][a-z0-9_-]{0,31})".formatted(outcome));
        }
        if (reason != null && !REASON_WIRE_NAME.matcher(reason).matches()) {
            throw new IllegalArgumentException(
                    "the reason '%s' is not a reason wire name ([a-z][a-z0-9_]{0,31})".formatted(reason));
        }
        if (raisedCondition != null) {
            if (!OutcomeReason.MAPPABLE_CONDITION_KINDS.contains(raisedCondition)) {
                throw new IllegalArgumentException(
                        "the condition '%s' is no kind a primitive can originate".formatted(raisedCondition));
            }
            if (reason == null) {
                throw new IllegalArgumentException(
                        "the condition '%s' is raised only for a reason".formatted(raisedCondition));
            }
        }
        var copy = new LinkedHashMap<String, Object>();
        facts.forEach((name, value) -> copy.put(
                Objects.requireNonNull(name, "name of a fact"),
                Objects.requireNonNull(value, "value of a fact")));
        facts = Collections.unmodifiableMap(copy);
    }

    /**
     * @return the command, the outcome, its class, the reason, the fact names, the spawned job, the raised condition
     *         and the instant; never a fact value
     */
    @Override
    public String toString() {
        return ("CommandSettlement[command=%s, outcome=%s, outcomeClass=%s, reason=%s, facts=%s, spawnedJobId=%s,"
                + " raisedCondition=%s, settledAt=%s]").formatted(command, outcome, outcomeClass, reason,
                        facts.keySet(), spawnedJobId, raisedCondition, settledAt);
    }
}
