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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.ast.OutcomeTransition;
import de.planmarshall.workflow.ast.Phase;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * The shape of the default plan unit (PM-WF-16): the plan unit calls each phase at the entry its call state names, the
 * land lane calls {@code land} within the phase {@code execute} and reaches none of the phases it skips, the phase of
 * every phase unit is its own, and the seeded fuzzer finds no dead state in any default unit.
 *
 * @since 0.1
 */
@DisplayName("Default plan unit")
class DefaultPlanUnitTest {

    private static final String PHASE_FIVE = "phase-5-execute";
    private static final String LAND = "land";

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
                        "phase-6-finalize", "phase-6-finalize"), calls.stream()
                        .map(StateBehavior.SubWorkflowCall::subWorkflowId).toList()),
                () -> assertEquals(List.of("start", "start", "start", "light", "rework", "start", "start", "land",
                        "reentry", "start", "hand_back"), calls.stream()
                        .map(call -> call.entry().orElseThrow()).toList()));
    }

    @Test
    @DisplayName("the land lane calls land within the phase execute and its call routes to no phase it skips")
    void landLaneCallsLandWithinExecute() {
        var plan = unit("plan");
        var execute = unit(PHASE_FIVE);
        var landCall = (StateBehavior.SubWorkflowCall) plan.states().get("phases.execute.land").behavior();
        var landEntry = (StateBehavior.SubWorkflowCall) execute.states().get("execute.land-entry").behavior();

        assertAll(
                () -> assertEquals("phase-5-execute", landCall.subWorkflowId()),
                () -> assertEquals("land", landCall.entry().orElseThrow()),
                () -> assertEquals("land", landEntry.subWorkflowId()),
                () -> assertEquals("start", landEntry.entry().orElseThrow()),
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
                () -> assertEquals(Map.of("deep", "phases.refine", "light", "phases.outline.light"),
                        init.transitions().stream().filter(transition -> !"stepped_out".equals(
                                transition.outcomeName())).collect(Collectors.toMap(
                                OutcomeTransition::outcomeName, OutcomeTransition::targetStateId))),
                () -> assertEquals("light", light.entry().orElseThrow()),
                () -> assertEquals("start", refine.entry().orElseThrow()),
                () -> assertEquals(Phase.unitIds(), defaults().keySet().stream()
                        .filter(id -> Phase.ofUnit(id).isPresent()).toList()),
                () -> assertTrue(WorkflowStatus.PHASES.containsAll(Phase.unitIds())),
                () -> assertEquals(new LinkedHashSet<>(Phase.unitIds()),
                        new LinkedHashSet<>(WorkflowStatus.PHASES.stream()
                                .filter(id -> id.startsWith("phase-")).toList())),
                () -> assertTrue(Phase.ofUnit("plan").isEmpty()));
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
