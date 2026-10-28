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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.LinkSpec;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;
import de.planmarshall.workflow.verify.InvariantFixtures;

/**
 * The pruner arms the composer tests leave open (PM-IMPL-5): it calls {@link InstancePruner#prune} directly with the
 * passing units of {@link InvariantFixtures} and explicit {@link RuleEffects}, so each test asserts what the pruner
 * leaves out and why, not only that a state is absent. The returned {@link InstancePruner.Pruned#steps()} record is the
 * one the instance header carries as {@code header().steps()}.
 */
@DisplayName("InstancePruner")
class InstancePrunerTest {

    private static final RuleEffects NOTHING = new RuleEffects(List.of(), List.of());

    /** The text of a resting link state with two links, written for these tests and parsed as a workspace unit. */
    private static final String LINK_UNIT = """
            ---
            name: Pruner link fixture
            description: A question state with a link, written for the pruner tests.
            type: system
            icon: workflow.default
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow pruner.fixture version 1 format 1
            scope workspace
            start setup.wait

            state setup.wait
                link next skip_wait ()
                    do setup.skip
                        done -> setup.left
                link next keep_going ()
                    do setup.skip
                        done -> setup.left

            state setup.left terminal left indeterminate
            """;

    private static WorkflowDefinition planTemplate() {
        return InvariantFixtures.passingUnits(UnitOrigin.DEFAULT).get("plan");
    }

    private static WorkflowDefinition linkTemplate() {
        var result = WorkflowParser.parse("pruner-link.pmwf", LINK_UNIT, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, result::toString).definition();
    }

    private static List<CompositionRequest.Step> finalizeSteps() {
        return InvariantFixtures.defaultRoster().stream()
                .map(step -> new CompositionRequest.Step(step.name(), step.order())).toList();
    }

    private static InstancePruner.Pruned prune(WorkflowDefinition template, RuleEffects effects,
            Optional<PlanInstanceHeader.Lane> lane) {
        return InstancePruner.prune(template, effects, List.of(), List.of(), PlanFixtures.VERIFICATION_STEPS,
                finalizeSteps(), lane);
    }

    @Test
    @DisplayName("the adopt rule's create-pr prune reaches the pruner and leaves the step out of the kept list")
    void adoptPruneReachesThePruner() {
        var adopt = new PrunedElement(PrunedElement.Kind.STEP, "default:create-pr", PruneReason.ADOPT, "lane");

        var pruned = prune(planTemplate(), new RuleEffects(List.of(adopt), List.of()),
                Optional.of(PlanInstanceHeader.Lane.ADOPT));

        assertAll(
                () -> assertEquals(List.of(adopt), pruned.pruned(), "the prune is listed with its reason"),
                () -> assertFalse(pruned.steps().phase6().contains("default:create-pr"),
                        pruned.steps().phase6()::toString),
                () -> assertTrue(pruned.steps().phase6().contains("default:branch-cleanup"),
                        pruned.steps().phase6()::toString));
    }

    @Test
    @DisplayName("a link selector leaves the link out of the kept state and is listed once")
    void linkSelectorPrunesTheLink() {
        var link = new PrunedElement(PrunedElement.Kind.LINK, "setup.wait/skip_wait", PruneReason.OTHER_LANE, "lane");

        var pruned = prune(linkTemplate(), new RuleEffects(List.of(link), List.of()), Optional.empty());

        var kept = assertInstanceOf(StateBehavior.InteractiveLinks.class,
                pruned.states().get("setup.wait").behavior());
        assertAll(
                () -> assertEquals(List.of(link), pruned.pruned(), "the link is listed once"),
                () -> assertEquals(List.of("keep_going"),
                        kept.links().stream().map(LinkSpec::idPattern).toList()));
    }

    @Test
    @DisplayName("an empty lane and the land and adopt lanes prune no state")
    void otherLaneGuardPrunesNothing() {
        var template = planTemplate();
        var lanes = List.of(Optional.<PlanInstanceHeader.Lane>empty(),
                Optional.of(PlanInstanceHeader.Lane.LAND), Optional.of(PlanInstanceHeader.Lane.ADOPT));

        assertAll(lanes.stream().map(lane -> () -> assertNoPrune(template, lane)));
    }

    private static void assertNoPrune(WorkflowDefinition template, Optional<PlanInstanceHeader.Lane> lane) {
        var pruned = prune(template, NOTHING, lane);

        assertAll("lane " + lane,
                () -> assertEquals(template.states().keySet(), pruned.states().keySet(), "no state is dropped"),
                () -> assertEquals(List.of(), pruned.pruned(), "no prune is listed"));
    }
}
