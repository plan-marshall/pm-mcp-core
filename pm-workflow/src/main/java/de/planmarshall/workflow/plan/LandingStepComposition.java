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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import lombok.Builder;
import lombok.experimental.UtilityClass;

/**
 * The landing axis composed into the finalize phase (Workflow DSL § Plan Behaviour Axes, item 1): unlike the method and
 * the asking variants, which are called at a call site, a landing variant <em>holds the step states of the landing
 * orders</em>, and the instance composes the bound variant's states into the composed file of {@code phase-6-finalize}.
 * The phase's own states — its checks, its reviews and its post-merge and post-run bands — stay, the step states keep
 * their {@code finalize.} identifiers, and the phase's dispatch hub fires them like its own steps.
 * <p>
 * The composition leaves out the states of the variants not bound, each a pruned state with the reason
 * {@code plan_default} and the level that bound the variant as source fact, so the process in force shows the choice.
 * A state two landing variants declare, and a state the phase itself declares, are refused: a step state has exactly one
 * owner, and a silent override would hide a defect of the units.
 * <p>
 * The phase's step list is the composition's own roster ({@code CompositionRequest#finalizeSteps}); this class composes
 * the states and leaves the roster to the version's header.
 *
 * @since 0.1
 */
@UtilityClass
public class LandingStepComposition {

    /** The unit whose composed file holds the landing step states. */
    public static final String PHASE = "phase-6-finalize";

    /** The prefix every step state of the finalize phase keeps. */
    private static final String STEP_PREFIX = "finalize.";

    /**
     * @param phase  the phase with the bound variant's states composed into it
     * @param pruned the states the variants not bound contribute, each with the reason {@code plan_default}
     * @since 0.1
     */
    @Builder
    public record Merged(WorkflowDefinition phase, List<PrunedElement> pruned) {

        /** Keeps the pruned elements unmodifiable. */
        public Merged {
            Objects.requireNonNull(phase, "phase");
            pruned = List.copyOf(pruned);
        }
    }

    /**
     * @param unit a unit
     * @return whether the unit is the finalize phase the landing step states compose into
     */
    public static boolean holdsLandingSteps(WorkflowDefinition unit) {
        return PHASE.equals(Objects.requireNonNull(unit, "unit").id());
    }

    /**
     * Composes the bound landing variant's states into the finalize phase and prunes the states of the variants not
     * bound.
     *
     * @param phase    the finalize phase's tree
     * @param landing  the variant bound on the landing axis, with the level that bound it
     * @param variants the trees of the landing axis's variants the request supplies
     * @return the phase with the bound variant's states and the pruned states of the others
     * @throws IllegalArgumentException if the bound variant is no landing variant, a supplied tree is no landing
     *                                  variant, the bound variant's tree is not supplied, a state is declared twice, or
     *                                  the bound variant declares a state the phase declares itself
     */
    public static Merged merge(WorkflowDefinition phase, PlanFacts.BoundVariant landing,
            List<WorkflowDefinition> variants) {
        Objects.requireNonNull(phase, "phase");
        Objects.requireNonNull(landing, "landing");
        Objects.requireNonNull(variants, "variants");
        if (landing.axis() != PlanFacts.Axis.LANDING) {
            throw new IllegalArgumentException("the axis '%s' is not the landing axis"
                    .formatted(landing.axis().wireName()));
        }
        var byId = new LinkedHashMap<String, WorkflowDefinition>();
        for (var variant : variants) {
            if (PlanFacts.Axis.ofVariant(variant.id()).orElse(null) != PlanFacts.Axis.LANDING) {
                throw new IllegalArgumentException("'%s' is no landing variant".formatted(variant.id()));
            }
            if (byId.put(variant.id(), variant) != null) {
                throw new IllegalArgumentException("the landing variant '%s' is supplied twice".formatted(variant.id()));
            }
        }
        var bound = byId.get(landing.variant());
        if (bound == null) {
            throw new IllegalArgumentException("the request supplies no tree of the landing variant '%s'"
                    .formatted(landing.variant()));
        }
        var states = new LinkedHashMap<>(phase.states());
        var declared = new HashSet<String>();
        byId.values().forEach(variant -> variant.states().keySet().forEach(id -> {
            if (!declared.add(id)) {
                throw new IllegalArgumentException("the state '%s' is declared by more than one landing variant"
                        .formatted(id));
            }
        }));
        var boundSteps = bound.states().entrySet().stream().filter(entry -> isStepState(entry.getKey())).toList();
        if (boundSteps.isEmpty()) {
            throw new IllegalArgumentException("the landing variant '%s' declares no step state of the finalize phase"
                    .formatted(landing.variant()));
        }
        boundSteps.forEach(entry -> {
            if (states.containsKey(entry.getKey())) {
                throw new IllegalArgumentException("the state '%s' is declared twice in the finalize phase"
                        .formatted(entry.getKey()));
            }
            states.put(entry.getKey(), entry.getValue());
        });
        var pruned = new ArrayList<PrunedElement>();
        byId.forEach((id, variant) -> {
            if (!id.equals(landing.variant())) {
                variant.states().keySet().stream().filter(LandingStepComposition::isStepState)
                        .forEach(state -> pruned.add(PrunedElement.builder()
                                .element(PrunedElement.Kind.STATE)
                                .id(state)
                                .reason(PruneReason.PLAN_DEFAULT)
                                .sourceFact(landing.sourceFact())
                                .build()));
            }
        });
        return Merged.builder()
                .phase(withStates(phase, states))
                .pruned(pruned)
                .build();
    }

    /**
     * @param stateId a state of a landing variant
     * @return whether the state is one of the phase's step states, whose id keeps the {@code finalize.} prefix
     */
    private static boolean isStepState(String stateId) {
        return stateId.startsWith(STEP_PREFIX);
    }

    private static WorkflowDefinition withStates(WorkflowDefinition phase, LinkedHashMap<String, StateNode> states) {
        return WorkflowDefinition.builder()
                .frontmatter(phase.frontmatter())
                .id(phase.id())
                .version(phase.version())
                .scope(phase.scope())
                .resultSchema(phase.resultSchema())
                .instance(phase.instance())
                .declarations(phase.declarations())
                .decisions(phase.decisions())
                .formatVersion(phase.formatVersion())
                .origin(phase.origin())
                .derivedFrom(phase.derivedFrom())
                .startState(phase.startState())
                .entryBindings(phase.entryBindings())
                .migrations(phase.migrations())
                .states(states)
                .location(phase.location())
                .build();
    }
}
