/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.Optional;

import de.planmarshall.core.store.ScopeTransaction;

/**
 * The port through which the engine composes and records the versions of a plan instance (PM-IMPL-5, Workflow DSL §
 * Versions &amp; Composition Events).
 * <p>
 * The engine owns the transaction of an advance and the position of the scope; the composer owns the facts, the
 * sub-units and the templates a composition reads. This port is the seam between them, so the engine never reads a
 * store of its own and the implementation lives with the assembly that wires the plan's facts
 * ({@link PlanInstanceComposer} behind it).
 * <p>
 * A mounted port makes the engine compose a server event in the transaction that enters its target state
 * ({@link #serverEventAt(String)}) and expose the operator and configuration events through
 * {@link de.planmarshall.workflow.engine.WorkflowEngine#composeOperatorEvent}. Without a port the engine runs the
 * verified unit it was built with, unchanged.
 *
 * @since 0.1
 */
public interface InstanceComposition {

    /**
     * @param stateId the identifier of a state of the unit the scope is in
     * @return the server event whose target state the state is, so that entering it composes the event's version; empty
     *         for a state that composes none. Only a {@link CompositionEvent.Trigger#SERVER} event may be returned;
     *         an operator or configuration event enters through
     *         {@link de.planmarshall.workflow.engine.WorkflowEngine#composeOperatorEvent}
     */
    Optional<CompositionEvent> serverEventAt(String stateId);

    /**
     * Composes the version of the event at the position of the scope.
     *
     * @param event          the event
     * @param position       the position of the scope, the target state of a server event included
     * @param versionInForce the instance version in force; {@code 0} when no version is in force
     * @return the composed version, or the refusal as a value
     */
    CompositionResult compose(CompositionEvent event, InstancePosition position, int versionInForce);

    /**
     * The units of a version the composition just returned, for the run that continues in the transaction that composed
     * it: the version's files are staged and not yet committed, so the plan's store cannot read them back, and the chain
     * that follows the composing transition would fault on a version that is not on disk. The implementation answers
     * from the composition it just made, so the same advance runs the plan unit, the composed files and the stored texts
     * of the digest-bound sub-units; it must therefore keep what {@code compose} produced (the request's sub-units
     * included, which the instance does not carry) until {@code record} has written it, and it is called after
     * {@code record} in that transaction. A run from a committed version reads {@link InstanceUnits} instead.
     *
     * @param composed the composed version
     * @return the units of that version, readable before the transaction commits
     */
    InstanceUnits unitsOf(CompositionResult.Composed composed);

    /**
     * Records the outcome of a composition in the transaction of the advance: the composed version with the texts of
     * the sub-units it names and its line of the composition log, or the log line of a refused attempt.
     *
     * @param transaction the transaction of the advance
     * @param result      the composed version, or the refusal
     */
    void record(ScopeTransaction transaction, CompositionResult result);

    /**
     * Records a configuration candidate that composed no version, so the log shows the adoption was resolved without
     * one: {@link CompositionRecord.Result#KEPT} or {@link CompositionRecord.Result#IDENTICAL_ADOPTED}. The decision
     * between the two belongs to the caller that compares the candidate with the version in force, never to the
     * engine.
     *
     * @param transaction the transaction of the advance
     * @param event       the configuration event
     * @param result      {@link CompositionRecord.Result#KEPT} or
     *                    {@link CompositionRecord.Result#IDENTICAL_ADOPTED}
     */
    void recordKept(ScopeTransaction transaction, CompositionEvent event, CompositionRecord.Result result);
}
