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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.Phase;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.TerminalOutcome;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * The shape of the default plan unit (PM-WF-16): the plan unit calls each phase at the entry its call state names, each
 * of the four lanes {@code deep}, {@code light}, {@code land} and {@code adopt} enters exactly its phases over the
 * call-resolved graph, the land lane calls {@code land} within the phase {@code execute} straight from
 * {@code phases.init} and reaches none of the phases it skips, the phase of every phase unit is its own, and the
 * seeded fuzzer finds no dead state in any default unit.
 *
 * @since 0.1
 */
// Every datum in this class is a contract: unit ids, phase names, lane outcomes, entry bindings and the fuzzer seed.
@DisplayName("Default plan unit")
class DefaultPlanUnitTest {

    private static final String PHASE_FIVE = "phase-5-execute";

    /** The steps a lane walk may take before it is taken to loop. */
    private static final int MAX_WALK = 500;

    /** The outcomes a lane walk never takes: the loop-backs and the fallbacks. */
    private static final Set<String> NOT_DELIVERING = Set.of("rework", "reentry", "returned", "stepped_out",
            "exhausted");

    private static WorkflowDefinition unit(String id) {
        return InvariantFixtures.defaultUnits().get(id);
    }

    private static Map<String, WorkflowDefinition> defaults() {
        return InvariantFixtures.defaultUnits();
    }

    @Test
    @DisplayName("the plan unit calls each phase at the entry its call state names")
    void entersEveryPhaseAtItsEntry() {
        var plan = unit("plan");
        var calls = plan.states().values().stream()
                .filter(state -> state.behavior() instanceof StateBehavior.SubWorkflowCall)
                .map(state -> (StateBehavior.SubWorkflowCall) state.behavior()).toList();

        assertAll(
                () -> assertEquals(List.of("phase-1-init", "phase-2-refine", "phase-3-outline", "phase-3-outline",
                        "phase-3-outline", "phase-4-plan", "phase-5-execute", "phase-5-execute", "phase-5-execute",
                        "phase-6-finalize", "phase-6-finalize", "phase-6-finalize"), calls.stream()
                        .map(StateBehavior.SubWorkflowCall::subWorkflowId).toList(),
                        "the plan unit calls the phases in declaration order"),
                () -> assertEquals(List.of("start", "start", "start", "light", "rework", "start", "start", "land",
                        "reentry", "start", "adopt", "hand_back"), calls.stream()
                        .map(StateBehavior.SubWorkflowCall::entry).toList(),
                        "each call names the entry it enters the phase at"));
    }

    @Test
    @DisplayName("the land lane calls land within the phase execute, straight from phases.init, and its call routes to"
            + " no phase it skips")
    void landLaneCallsLandWithinExecute() {
        var plan = unit("plan");
        var execute = unit(PHASE_FIVE);
        var init = (StateBehavior.SubWorkflowCall) plan.states().get("phases.init").behavior();
        var landCall = (StateBehavior.SubWorkflowCall) plan.states().get("phases.execute.land").behavior();
        var landEntry = (StateBehavior.SubWorkflowCall) execute.states().get("execute.land-entry").behavior();

        assertAll(
                () -> assertTrue(init.transitions().stream().anyMatch(transition -> "land".equals(
                        transition.outcomeName()) && "phases.execute.land".equals(transition.targetStateId())),
                        "phases.init routes land to the land call"),
                () -> assertTrue(plan.states().values().stream().filter(state -> !"phases.init".equals(state.id()))
                        .flatMap(state -> UnitGraph.allTransitions(state.behavior()).stream())
                        .noneMatch(transition -> "phases.execute.land".equals(transition.targetStateId())),
                        "no other state of the plan enters the land call"),
                () -> assertEquals("phase-5-execute", landCall.subWorkflowId()),
                () -> assertEquals("land", landCall.entry(), "the land call enters the entry land"),
                () -> assertEquals("land", landEntry.subWorkflowId()),
                () -> assertEquals("start", landEntry.entry(), "the land entry calls land at start"),
                () -> assertEquals(List.of("done", "rework", "returned", "stepped_out"),
                        landCall.transitions().stream().map(OutcomeTransition::outcomeName).toList()),
                () -> assertEquals("phases.finalize", landCall.transitions().getFirst().targetStateId()),
                () -> assertEquals(List.of("done", "handed_back", "stepped_out"),
                        landEntry.transitions().stream().map(OutcomeTransition::outcomeName).toList()));
    }

    @Test
    @DisplayName("the light route reaches the light outline entry and no refine call, and every phase unit carries its"
            + " own phase")
    void lanesAndPhaseIdentities() {
        var plan = unit("plan");
        var init = (StateBehavior.SubWorkflowCall) plan.states().get("phases.init").behavior();
        var light = (StateBehavior.SubWorkflowCall) plan.states().get("phases.outline.light").behavior();
        var refine = (StateBehavior.SubWorkflowCall) plan.states().get("phases.refine").behavior();

        assertAll(
                () -> assertEquals(Map.of("deep", "phases.refine", "light", "phases.outline.light", "land",
                        "phases.execute.land", "adopt", "phases.finalize.adopt"),
                        init.transitions().stream().filter(transition -> !"stepped_out".equals(
                                transition.outcomeName())).collect(Collectors.toMap(
                                OutcomeTransition::outcomeName, OutcomeTransition::targetStateId)),
                        "phases.init routes each of the four lanes"),
                () -> assertEquals("light", light.entry(), "the light route enters the entry light"),
                () -> assertEquals("start", refine.entry(), "the refine call enters the entry start"),
                () -> assertEquals(Phase.unitIds(), defaults().keySet().stream()
                        .filter(id -> Phase.ofUnit(id).isPresent()).toList()),
                () -> assertEquals(WorkflowStatus.PHASES, Set.of("phase-1-init", "phase-2-refine", "phase-3-outline", "phase-4-plan",
                        "phase-5-execute", "phase-6-finalize", "completed", "aborted", "epic", "workspace",
                        "machine")),
                () -> assertTrue(WorkflowStatus.PHASES.containsAll(Phase.unitIds())),
                () -> assertTrue(Phase.ofUnit("plan").isEmpty()));
    }

    @ParameterizedTest(name = "lane {0}")
    @MethodSource("lanes")
    @DisplayName("each lane (contract outcomes of init.ready) enters exactly its phases, each at its entry")
    void laneEntersItsPhases(String lane, List<String> entered) {
        var walked = walk(defaults(), lane);

        assertEquals(entered, walked, "the lane enters its phases at their entries");
    }

    static Stream<Arguments> lanes() {
        return Stream.of(
                Arguments.of("deep", List.of("phase-1-init@start", "phase-2-refine@start", "phase-3-outline@start",
                        "phase-4-plan@start", "phase-5-execute@start", "phase-6-finalize@start")),
                Arguments.of("light", List.of("phase-1-init@start", "phase-3-outline@light", "phase-4-plan@start",
                        "phase-5-execute@start", "phase-6-finalize@start")),
                Arguments.of("land", List.of("phase-1-init@start", "phase-5-execute@land",
                        "phase-6-finalize@start")),
                Arguments.of("adopt", List.of("phase-1-init@start", "phase-6-finalize@adopt")));
    }

    @Test
    @DisplayName("the land lane enters none of refine, outline and plan")
    void landLaneSkipsThePlanningPhases() {
        var walked = phasesOf(walk(defaults(), "land"));

        assertAll(
                () -> assertFalse(walked.contains("phase-2-refine"), "the land lane enters no refine phase"),
                () -> assertFalse(walked.contains("phase-3-outline"), "the land lane enters no outline phase"),
                () -> assertFalse(walked.contains("phase-4-plan"), "the land lane enters no plan phase"));
    }

    @Test
    @DisplayName("matched control: a land route through phases.plan makes the land walk enter the plan phase")
    void landRouteThroughPlanEntersThePlanPhase() {
        var throughPlan = new InvariantFixtures.Mutation("the land route through the plan phase",
                InvariantFixtures.PLAN_FILE, "        land -> phases.execute.land\n", "        land -> phases.plan\n");

        var mutated = phasesOf(walk(InvariantFixtures.mutatedUnits(UnitOrigin.DEFAULT, List.of(throughPlan)),
                "land"));
        var passing = phasesOf(walk(defaults(), "land"));

        assertAll(
                () -> assertTrue(mutated.contains("phase-4-plan"), "the mutated land walk enters the plan phase"),
                () -> assertFalse(passing.contains("phase-4-plan"), "the passing land walk skips the plan phase"));
    }

    /** @return the phase units of a walk, without their entries */
    private static List<String> phasesOf(List<String> walked) {
        return walked.stream().map(entered -> entered.substring(0, entered.indexOf('@'))).toList();
    }

    /**
     * Walks the call-resolved graph of the plan unit from its start state along one lane: the lane's outcome at
     * {@code init.ready}, and at every other state the first delivering outcome, that is no loop-back
     * ({@code rework}, {@code reentry}) and no fallback ({@code returned}, {@code stepped_out}, {@code exhausted}),
     * preferring a target the walk has not yet entered within the same frame so a loop is left. A call enters its
     * callee at the entry it names and a terminal returns to the calling state's transition of its outcome.
     *
     * @param units the units by id, the plan unit among them
     * @param lane  the outcome of {@code init.ready}
     * @return every phase unit the walk entered, as {@code unit@entry}, in order
     */
    private static List<String> walk(Map<String, WorkflowDefinition> units, String lane) {
        var expansion = CallGraphExpansion.of(units.get("plan"), units);
        var entered = new ArrayList<String>();
        var stack = new ArrayDeque<StateNode>();
        var visited = new HashSet<String>();
        var current = expansion.units().get("plan").startState();
        for (var step = 0; step < MAX_WALK; step++) {
            var state = expansion.states().get(current);
            var frame = stack.stream().map(StateNode::id).collect(Collectors.joining("/"));
            visited.add(frame + ">" + current);
            if (state.behavior() instanceof StateBehavior.SubWorkflowCall call) {
                var callee = expansion.units().get(call.subWorkflowId());
                var entry = call.entry() != null ? call.entry()
                        : callee.entryBindings().keySet().iterator().next();
                if (Phase.ofUnit(callee.id()).isPresent()) {
                    entered.add(callee.id() + "@" + entry);
                }
                stack.push(state);
                current = callee.entryBindings().get(entry).stateId();
            } else if (state.terminalOutcome() instanceof TerminalOutcome terminal) {
                if (stack.isEmpty()) {
                    return entered;
                }
                var outcome = terminal.id();
                current = UnitGraph.allTransitions(stack.pop().behavior()).stream()
                        .filter(transition -> outcome.equals(transition.outcomeName())).findFirst().orElseThrow()
                        .targetStateId();
            } else {
                var allowed = UnitGraph.allTransitions(state.behavior()).stream()
                        .filter(transition -> "init.ready".equals(state.id())
                                ? lane.equals(transition.outcomeName())
                                : !NOT_DELIVERING.contains(transition.outcomeName()))
                        .toList();
                current = allowed.stream().filter(transition -> !visited.contains(frame + ">"
                        + transition.targetStateId())).findFirst().orElse(allowed.getFirst()).targetStateId();
            }
        }
        throw new IllegalStateException("the walk of lane " + lane + " did not end");
    }

    @Test
    @DisplayName("the seeded fuzzer finds no dead state in any default unit")
    void fuzzesEveryDefaultUnit() {
        var configuration = new FuzzConfiguration(20261028L, 200, 80);

        assertAll(defaults().entrySet().stream().map(entry -> () -> assertEquals(List.of(),
                new TransitionFuzzer().fuzz(entry.getValue(), configuration).deadStates(),
                entry.getKey() + " has a dead state")));
    }

    @Test
    @DisplayName("the removed phase clause is refused, at the line that states it")
    void refusesTheRemovedPhaseClause() {
        var source = """
                ---
                name: test.phase-clause
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow test.phase-clause version 1 format 1
                scope subworkflow
                entry main -> demo.begin

                state demo.begin
                    phase inherit
                    auto demo.run
                        done -> demo.end

                state demo.end terminal done success
                """;

        var refused = assertInstanceOf(
                ParseResult.Refused.class,
                WorkflowParser.parse("phase-clause.pmwf", source,
                        UnitOrigin.DEFAULT));

        assertEquals(17, refused.diagnostic().line(), refused.diagnostic()::toString);
    }
}
