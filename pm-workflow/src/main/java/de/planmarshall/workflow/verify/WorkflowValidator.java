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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.FreeParam;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.workflow.ast.LinkAction;
import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.ParamSpec;
import de.planmarshall.workflow.ast.ParamType;
import de.planmarshall.workflow.ast.SourceLocation;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateMigration;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The static verifier of one workflow unit (PM-IMPL-5, PM-TEST-3): it runs every check of {@link VerificationRule}
 * over a parsed {@link WorkflowDefinition} and returns every violation it finds as a value.
 * <p>
 * The verifier reads nothing on its own: the primitives a unit may name come from the {@link PrimitiveRegistry} and
 * the released state names from the baseline its caller passes. No check stops at the first violation, so the result
 * lists all of them, ordered by the declaration order of their state and then by rule.
 * <p>
 * Reachability is graph reachability over every transition: a guard's configuration keys are known to the tree, but
 * not the values they can take, so configuration combinations are not enumerated here.
 *
 * @since 0.1
 */
public final class WorkflowValidator {

    /**
     * The abort terminals. The engine enters them by the operator's abort, a transition no unit declares, so they
     * count as reachable from every state and are exempt from {@link VerificationRule#REACHABILITY}.
     */
    private static final Set<String> ABORT_TERMINALS = Set.of("plan.aborted", "epic.aborted");

    /** The identifier of the link the engine renders on every link state of a sub-workflow. */
    private static final String STEP_OUT_LINK = "step_out";

    private final PrimitiveRegistry registry;

    private final List<String> releasedStates;

    /**
     * @param registry       the primitives a unit may name
     * @param releasedStates the state names of earlier releases, in the order they are reported
     * @throws NullPointerException if an argument or a released state name is {@code null}
     */
    public WorkflowValidator(PrimitiveRegistry registry, Collection<String> releasedStates) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.releasedStates = List.copyOf(new LinkedHashSet<>(List.copyOf(releasedStates)));
    }

    /**
     * Runs every check over the unit.
     *
     * @param definition the parsed unit
     * @return every violation of the unit; {@link VerificationResult#passed()} when there is none
     * @throws NullPointerException if the definition is {@code null}
     */
    public VerificationResult verify(WorkflowDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        var violations = new ArrayList<Violation>();
        var graph = UnitGraph.of(definition);
        for (var state : definition.states().values()) {
            checkPrimitiveBinding(state, violations);
            checkParameterEquivalence(state, violations);
            checkOutcomeCoverage(state, violations);
            checkLinkIdentifiers(definition.scope(), state, violations);
        }
        checkReachability(definition, graph, violations);
        checkExitPaths(definition, graph, violations);
        checkReleasedStates(definition, violations);
        return new VerificationResult(ordered(definition, violations));
    }

    private void checkPrimitiveBinding(StateNode state, List<Violation> violations) {
        state.factsPrimitiveId().ifPresent(id -> requireRegistered(state, id, "facts", state.location(), violations));
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto ->
                requireRegistered(state, auto.primitiveId(), "auto", state.location(), violations);
            case StateBehavior.JobStep job -> requireRegistered(state, job.primitiveId(),
                    job.composite() ? "composite_job" : "job", state.location(), violations);
            case StateBehavior.InteractiveLinks _, StateBehavior.ModelJob _, StateBehavior.SubWorkflowCall _,
                StateBehavior.QuestionWait _, StateBehavior.TerminalState _ -> {
                // no primitive of its own: the links are checked below, the rest is outside these checks
            }
        }
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            requireRegistered(state, primitiveOf(link), "do of the link '%s'".formatted(link.idPattern()),
                    link.location(), violations);
            link.guard().ifPresent(guard -> UnitGraph.predicatesOf(guard.expr()).forEach(id -> requireRegistered(state, id,
                    "predicate of the link '%s'".formatted(link.idPattern()), link.location(), violations)));
        }
    }

    private void requireRegistered(StateNode state, String primitiveId, String site, SourceLocation location,
            List<Violation> violations) {
        if (registry.find(primitiveId).isEmpty()) {
            violations.add(new Violation(VerificationRule.PRIMITIVE_BINDING, state.id(), location,
                    "the primitive '%s' of the %s is not registered".formatted(primitiveId, site)));
        }
    }

    private void checkParameterEquivalence(StateNode state, List<Violation> violations) {
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            registry.find(primitiveOf(link))
                    .ifPresent(primitive -> compareFreeParameters(state, link, primitive, violations));
        }
    }

    private static void compareFreeParameters(StateNode state, LinkSpec link, Primitive<?, ?> primitive,
            List<Violation> violations) {
        var expected = new LinkedHashMap<String, FreeParam>();
        for (var component : primitive.parameterType().getRecordComponents()) {
            var freeParam = component.getAnnotation(FreeParam.class);
            if (freeParam != null) {
                expected.put(component.getName(), freeParam);
            }
        }
        var declared = new HashSet<String>();
        for (var param : link.freeParams()) {
            declared.add(param.name());
            var freeParam = expected.get(param.name());
            if (freeParam == null) {
                violations.add(parameterViolation(state, param.location(), link, primitive,
                        "declares the free parameter '%s', which the parameter record has not".formatted(param.name())));
            } else {
                compareType(state, link, primitive, param, freeParam, violations);
            }
        }
        expected.keySet().stream().filter(name -> !declared.contains(name))
                .forEach(name -> violations.add(parameterViolation(state, link.location(), link, primitive,
                        "omits the free parameter '%s' of the parameter record".formatted(name))));
    }

    private static void compareType(StateNode state, LinkSpec link, Primitive<?, ?> primitive, ParamSpec param,
            FreeParam freeParam, List<Violation> violations) {
        if (!(param.type() instanceof ParamType.Text(var minLength, var maxLength))) {
            violations.add(parameterViolation(state, param.location(), link, primitive,
                    "declares the free parameter '%s' as %s, but the parameter record declares a text"
                            .formatted(param.name(), param.type())));
            return;
        }
        var min = minLength.orElse(0);
        var max = maxLength.orElse(Integer.MAX_VALUE);
        if (min != freeParam.minLength() || max != freeParam.maxLength()) {
            violations.add(parameterViolation(state, param.location(), link, primitive,
                    "declares the free parameter '%s' with the length %d..%d, but the parameter record declares %d..%d"
                            .formatted(param.name(), min, max, freeParam.minLength(), freeParam.maxLength())));
        }
    }

    private static Violation parameterViolation(StateNode state, SourceLocation location, LinkSpec link,
            Primitive<?, ?> primitive, String detail) {
        return new Violation(VerificationRule.PARAMETER_EQUIVALENCE, state.id(), location,
                "the link '%s' of the primitive '%s' %s".formatted(link.idPattern(), primitive.id(), detail));
    }

    private void checkOutcomeCoverage(StateNode state, List<Violation> violations) {
        switch (state.behavior()) {
            case StateBehavior.AutoStep auto ->
                coverOutcomes(state, auto.primitiveId(), auto.transitions(), state.location(), violations);
            case StateBehavior.JobStep job ->
                coverOutcomes(state, job.primitiveId(), job.transitions(), state.location(), violations);
            case StateBehavior.InteractiveLinks _, StateBehavior.ModelJob _, StateBehavior.SubWorkflowCall _,
                StateBehavior.QuestionWait _, StateBehavior.TerminalState _ -> {
                // a task, a call and a question route outcomes that no primitive of the registry declares
            }
        }
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            coverOutcomes(state, primitiveOf(link), link.transitions(), link.location(), violations);
        }
    }

    private void coverOutcomes(StateNode state, String primitiveId, List<OutcomeTransition> transitions,
            SourceLocation siteLocation, List<Violation> violations) {
        var primitive = registry.find(primitiveId);
        if (primitive.isEmpty()) {
            return;
        }
        var wireNames = wireNamesOf(primitive.get());
        var routed = new HashSet<String>();
        for (var transition : transitions) {
            var outcome = transition.outcomeName();
            if (!wireNames.contains(outcome)) {
                violations.add(outcomeViolation(state, transition.location(),
                        "routes the outcome '%s', which the primitive '%s' does not have".formatted(outcome,
                                primitiveId)));
            } else if (!routed.add(outcome)) {
                violations.add(outcomeViolation(state, transition.location(),
                        "routes the outcome '%s' of the primitive '%s' twice".formatted(outcome, primitiveId)));
            }
        }
        wireNames.stream().filter(outcome -> !routed.contains(outcome))
                .forEach(outcome -> violations.add(outcomeViolation(state, siteLocation,
                        "does not route the outcome '%s' of the primitive '%s'".formatted(outcome, primitiveId))));
    }

    private static Violation outcomeViolation(StateNode state, SourceLocation location, String detail) {
        return new Violation(VerificationRule.OUTCOME_COVERAGE, state.id(), location,
                "the state '%s' %s".formatted(state.id(), detail));
    }

    private static Set<String> wireNamesOf(Primitive<?, ?> primitive) {
        var wireNames = new LinkedHashSet<String>();
        for (var constant : primitive.outcomeType().getEnumConstants()) {
            wireNames.add(((PrimitiveOutcome) constant).wireName());
        }
        return wireNames;
    }

    private static void checkLinkIdentifiers(WorkflowScope scope, StateNode state, List<Violation> violations) {
        var identifiers = new HashSet<String>();
        if (UnitGraph.hasImplicitExit(scope, state) && state.behavior() instanceof StateBehavior.InteractiveLinks) {
            identifiers.add(STEP_OUT_LINK);
        }
        for (var link : UnitGraph.declaredLinks(state.behavior())) {
            if (!identifiers.add(link.idPattern())) {
                var implicit = STEP_OUT_LINK.equals(link.idPattern()) ? " the implicit" : " another";
                violations.add(new Violation(VerificationRule.LINK_ID_UNIQUE, state.id(), link.location(),
                        "the link '%s' of the state '%s' repeats the identifier of%s link".formatted(link.idPattern(),
                                state.id(), implicit)));
            }
        }
    }

    private static void checkReachability(WorkflowDefinition definition, UnitGraph graph,
            List<Violation> violations) {
        var reached = graph.forwardClosure(Set.of(definition.startState()));
        for (var state : definition.states().values()) {
            if (!reached.contains(state.id()) && !ABORT_TERMINALS.contains(state.id())) {
                violations.add(new Violation(VerificationRule.REACHABILITY, state.id(), state.location(),
                        "the state '%s' is not reachable from the start state '%s'".formatted(state.id(),
                                definition.startState())));
            }
        }
    }

    private static void checkExitPaths(WorkflowDefinition definition, UnitGraph graph, List<Violation> violations) {
        if (definition.scope() == WorkflowScope.WORKSPACE) {
            var returning = graph.backwardClosure(Set.of(definition.startState()));
            definition.states().values().stream()
                    .filter(state -> !isTerminal(state) && !returning.contains(state.id()))
                    .forEach(state -> violations.add(new Violation(VerificationRule.EXIT_PATH, state.id(),
                            state.location(), "the state '%s' has no path back to the start state '%s'"
                            .formatted(state.id(), definition.startState()))));
            return;
        }
        var exits = new LinkedHashSet<String>();
        for (var state : definition.states().values()) {
            if (isTerminal(state) || UnitGraph.hasImplicitExit(definition.scope(), state)) {
                exits.add(state.id());
            }
        }
        var leaving = graph.backwardClosure(exits);
        definition.states().values().stream().filter(state -> !leaving.contains(state.id()))
                .forEach(state -> violations.add(new Violation(VerificationRule.EXIT_PATH, state.id(),
                        state.location(), "the state '%s' has no path to a terminal state".formatted(state.id()))));
    }

    private void checkReleasedStates(WorkflowDefinition definition, List<Violation> violations) {
        for (var released : releasedStates) {
            if (definition.states().containsKey(released)) {
                continue;
            }
            var migrations = definition.migrations().stream()
                    .filter(migration -> migration.oldStateId().equals(released)).toList();
            if (migrations.stream().anyMatch(migration -> definition.states().containsKey(migration.newStateId()))) {
                continue;
            }
            var location = migrations.stream().findFirst().map(StateMigration::location)
                    .orElse(definition.location());
            var detail = migrations.isEmpty() ? "no migrate declaration names it"
                    : "its migrate declaration targets '%s', which is not a state"
                    .formatted(migrations.getFirst().newStateId());
            violations.add(new Violation(VerificationRule.RELEASED_STATE, released, location,
                    "the released state '%s' is not a state of the unit and %s".formatted(released, detail)));
        }
    }

    private static boolean isTerminal(StateNode state) {
        return state.behavior() instanceof StateBehavior.TerminalState;
    }

    private static String primitiveOf(LinkSpec link) {
        return switch (link.action()) {
            case LinkAction.ExecutePrimitive execute -> execute.primitiveId();
        };
    }

    /**
     * Orders the violations by the declaration order of their state, released names after every state, and within a
     * state by rule; the sort is stable, so violations of one state and rule keep the order they were found in.
     */
    private static List<Violation> ordered(WorkflowDefinition definition, List<Violation> violations) {
        Map<String, Integer> declarationIndex = new HashMap<>();
        for (var stateId : definition.states().keySet()) {
            declarationIndex.put(stateId, declarationIndex.size());
        }
        var afterEveryState = declarationIndex.size();
        var sorted = new ArrayList<>(violations);
        sorted.sort(Comparator
                .comparingInt((Violation violation) -> Optional.ofNullable(declarationIndex.get(violation.stateId()))
                        .orElse(afterEveryState))
                .thenComparing(Violation::rule));
        return sorted;
    }
}
