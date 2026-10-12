/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

/**
 * The closed set of checks {@link WorkflowValidator} runs (PM-IMPL-5). Every {@link Violation} names exactly one of
 * them, and each carries the number of the static verification rule of the specification it implements a part of.
 * This enum is the only source of the rule vocabulary.
 *
 * @since 0.1
 */
public enum VerificationRule {
    /** Every primitive a state or a link names, a {@code predicate:} guard included, is registered. */
    PRIMITIVE_BINDING(1),
    /** The free parameters of a link equal the {@code @FreeParam} components of its primitive's parameter record. */
    PARAMETER_EQUIVALENCE(1),
    /** Every entity kind resolves to a registered kind with its primitive, each is registered, and ask is admitted. */
    ENTITY_KIND_BINDING(1),
    /** Every role name of a {@code job llm} state and of a {@code consult role} clause matches the role-name grammar. */
    ROLE_NAME_GRAMMAR(1),
    /** Every {@code schema[...]} names a registered parameter schema. */
    SCHEMA_BINDING(1),
    /** Every question clause names a registered question class. */
    QUESTION_CLASS_BINDING(1),
    /** A primitive declaring {@code enqueuesJob()} declares job-starting outcomes and only they target job states. */
    JOB_STARTING_OUTCOME(1),
    /** The transitions of an {@code auto}, {@code job} or link action route every outcome of its primitive once. */
    OUTCOME_COVERAGE(2),
    /** The transitions of a {@code job llm} state form a bijection with the evaluation outcomes plus {@code lost}. */
    MODEL_JOB_OUTCOMES(2),
    /** The transitions of a question clause form a bijection with the closed resolution outcomes of its class. */
    QUESTION_OUTCOMES(2),
    /** State identifiers are unique across the unit, the engine-made {@code <state>.asked} included. */
    STATE_ID_UNIQUE(3),
    /** The start state and every transition target exist in the unit's states. */
    STATE_EXISTS(3),
    /** Every state is reachable from the start state. */
    REACHABILITY(3),
    /** Every state and transition is reachable under every combination of {@code depends_on_config} values. */
    CONFIG_REACHABILITY(3),
    /** {@code plan.aborted} is never targeted, and {@code epic.aborted} only by {@code abort_landed} of {@code epic.next-work}. */
    ABORT_TERMINAL_TARGETING(3),
    /** Transitions must not target condition kinds or reserved state identifiers. */
    TARGET_NOT_RESERVED(3),
    /** Every non-terminal state has a path to a terminal state, in a workspace unit back to the start state. */
    EXIT_PATH(3),
    /** The link identifiers of a state are unique, the implicit links included. */
    LINK_ID_UNIQUE(3),
    /** Worker units contain no model jobs, and consultation tasks offer no consult links. */
    LEAF_TASK_CONSTRAINT(4),
    /** Every released state name is a state, or is migrated directly to a state. */
    RELEASED_STATE(5),
    /** Sub-workflow exit contracts, parameter bindings, phase inherit, and guided step-in skills. */
    SUBWORKFLOW_CALL_CONTRACT(6),
    /** The static call graph forms an acyclic DAG with maximum depth at most 4. */
    CALL_GRAPH_DAG(7),
    /** Every path to a guard or state with required artifacts passes a producer state. */
    GATE_PRODUCER_COVERAGE(8),
    /** Step completion once per firing, outcome classes admit status, and cycle measures fit. */
    STEP_COMPLETION(9),
    /** Resting link states and declared links on question states exist only in session-driven units. */
    SESSION_DRIVEN_LINKS(10),
    /** Every cycle contains an awaited event (11b) or an engine-chained cycle measure (11a). */
    CYCLE_AWAITED_EVENT(11),
    /** Link relation and form compatibility, parameter requirements, and reserved relations. */
    LINK_COMPATIBILITY(12);

    private final int specificationRule;

    VerificationRule(int specificationRule) {
        this.specificationRule = specificationRule;
    }

    /**
     * @return the number of the static verification rule of the specification this check implements a part of
     */
    public int specificationRule() {
        return specificationRule;
    }
}
