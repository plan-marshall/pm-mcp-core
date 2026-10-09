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

import java.util.Optional;
import java.util.Set;

/**
 * One deterministic server operation (PM-IMPL-5): a canonical name, a typed parameter record, a closed outcome
 * enumeration, and the side effects it declares. A primitive is stateless, so one instance serves every call.
 * <p>
 * What a primitive does beyond returning an outcome it declares here, where it can be checked before the primitive
 * ever runs: the artifacts it produces and requires, the job it enqueues, the gates it may look a waiver up for, the
 * event it waits for, and the measure that ends a cycle through its state. A {@link PrimitiveRegistry} refuses a
 * primitive whose declarations contradict each other.
 *
 * @param <P> the parameter record
 * @param <R> the closed outcome enum
 * @since 0.1
 */
public interface Primitive<P extends Record, R extends Enum<R> & PrimitiveOutcome> {

    /**
     * @return the canonical name: two lower-case identifiers joined by a dot, for example
     *         {@code git.stage-and-commit}; unique among the primitives of a registry
     */
    String id();

    /**
     * @return the record class of the parameters, the free ones and the ones the engine resolves from state
     */
    Class<P> parameterType();

    /**
     * @return the enum class whose constants are all the outcomes of this primitive
     */
    Class<R> outcomeType();

    /**
     * Runs the operation and returns one of its outcomes.
     *
     * @param context the call the operation runs for
     * @param parameters the bound parameters
     * @return the outcome with the facts the operation changed and the job it started
     */
    PrimitiveResult<R> execute(WorkflowExecutionContext context, P parameters);

    /**
     * @return the event this primitive waits for before its outcome can change; {@link AwaitedEvent#NONE} for a
     *         primitive whose outcome depends only on state it reads in the same transaction
     */
    AwaitedEvent awaitedEvent();

    /**
     * @return the artifacts this primitive produces as side effects, by name, for example {@code pr_title}
     */
    default Set<String> producedArtifacts() {
        return Set.of();
    }

    /**
     * @return the artifacts that must exist before this primitive can pass, by name
     */
    default Set<String> requiredArtifacts() {
        return Set.of();
    }

    /**
     * @return the identifiers of the gates this primitive may look a waiver up for. A primitive never writes a
     *         waiver.
     */
    default Set<String> waivableGates() {
        return Set.of();
    }

    /**
     * @return {@code true} for a primitive that enqueues a job and reports it in
     *         {@link PrimitiveResult#spawnedJobId()} with an outcome whose {@link PrimitiveOutcome#startsJob()} is
     *         {@code true}
     */
    default boolean enqueuesJob() {
        return false;
    }

    /**
     * @return the measure that strictly decreases on every pass of a chained cycle through the state of this
     *         primitive
     */
    default CycleMeasure cycleMeasure() {
        return CycleMeasure.NONE;
    }

    /**
     * @return the wire name of the outcome a primitive with the measure {@link CycleMeasure#ATTEMPT_CAP} returns
     *         once its cap is spent, an outcome of class {@link OutcomeClass#FAILURE}; empty for every other
     *         primitive
     */
    default Optional<String> exhaustionOutcome() {
        return Optional.empty();
    }
}
