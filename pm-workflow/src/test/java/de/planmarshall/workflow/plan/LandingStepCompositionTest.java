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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The landing axis composed into the finalize phase (Workflow DSL § Plan Behaviour Axes, item 1): the bound variant's
 * step states are composed into the phase's file, the phase's own states stay, the variants not bound are pruned states
 * with the reason {@code plan_default} and the level that bound the variant as source fact, and a state two owners
 * declare is refused rather than silently overridden.
 */
@DisplayName("Landing step composition")
class LandingStepCompositionTest {

    private static WorkflowDefinition phase(String... extraStates) {
        var body = new StringBuilder("""
                ---
                name: phase-6-finalize
                description: The finalize phase written for the landing composition test.
                type: subworkflow
                icon: phase.finalize
                entry: start
                exit: completed
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow phase-6-finalize version 1 format 1
                scope subworkflow
                entry start -> finalize.merge-gate

                state finalize.merge-gate
                    auto test.proceed
                        done -> completed

                state completed terminal completed success
                """);
        for (var state : extraStates) {
            body.append("\nstate ").append(state).append("""
                    
                        auto test.proceed
                            done -> completed
                    """);
        }
        return PlanFixtures.parse("phase-6-finalize.pmwf", body.toString(), UnitOrigin.DEFAULT);
    }

    private static WorkflowDefinition landing(String id, String... states) {
        var prefix = id.replace('.', '-');
        var body = new StringBuilder("""
                ---
                name: %1$s
                description: A landing variant written for the landing composition test.
                type: subworkflow
                icon: workflow.default
                entry: start
                exit: continue
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow %1$s version 1 format 1
                scope subworkflow
                entry start -> %2$s.begin

                state %2$s.begin
                    auto test.proceed
                        done -> %2$s.done

                state %2$s.done terminal continue success
                """.formatted(id, prefix));
        for (var state : states) {
            body.append("\nstate ").append(state).append("""
                    
                        auto test.proceed
                            done -> %s.done
                    """.formatted(prefix));
        }
        return PlanFixtures.parse(id.replace('.', '-') + ".pmwf", body.toString(), UnitOrigin.DEFAULT);
    }

    private static PlanFacts.BoundVariant bound(String variant, PlanFacts.Level level) {
        return new PlanFacts.BoundVariant(PlanFacts.Axis.LANDING, variant, level);
    }

    @Test
    @DisplayName("the bound variant's states compose into the phase and the others are pruned plan_default")
    void mergesBoundAndPrunesOthers() {
        var merged = LandingStepComposition.merge(phase(), bound("landing.main", PlanFacts.Level.PROJECT),
                List.of(landing("landing.pull-request", "finalize.push-barrier", "finalize.create-pr"),
                        landing("landing.main", "finalize.land-main"),
                        landing("landing.files", "finalize.land-files")));

        assertAll(
                () -> assertTrue(merged.phase().states().containsKey("finalize.land-main"),
                        "The bound variant's state is composed into the phase"),
                () -> assertTrue(merged.phase().states().containsKey("finalize.merge-gate"),
                        "The phase's own states stay"),
                () -> assertFalse(merged.phase().states().containsKey("finalize.push-barrier"),
                        "A variant not bound contributes no state"),
                () -> assertFalse(merged.phase().states().containsKey("finalize.land-files"),
                        "The other variant contributes no state either"),
                () -> assertEquals(List.of("finalize.push-barrier", "finalize.create-pr", "finalize.land-files"),
                        merged.pruned().stream().map(PrunedElement::id).toList(),
                        "Every state of the variants not bound is listed"),
                () -> assertTrue(merged.pruned().stream()
                                .allMatch(prune -> prune.reason() == PruneReason.PLAN_DEFAULT),
                        "The prunes carry the plan_default reason"),
                () -> assertTrue(merged.pruned().stream()
                                .allMatch(prune -> "project.axes.landing".equals(prune.sourceFact())),
                        "The prunes carry the binding level as source fact"));
    }

    @Test
    @DisplayName("the phase's identity and its step-facing declarations stay as they are")
    void keepsPhaseIdentity() {
        var phase = phase();
        var merged = LandingStepComposition.merge(phase, bound("landing.files", PlanFacts.Level.PLAN),
                List.of(landing("landing.files", "finalize.land-files")));

        assertAll(
                () -> assertEquals(phase.id(), merged.phase().id(), "The composed file keeps the phase's id"),
                () -> assertEquals(phase.scope(), merged.phase().scope(), "The composed file keeps the scope"),
                () -> assertEquals(phase.startState(), merged.phase().startState(), "The start state stays"),
                () -> assertEquals(phase.entryBindings(), merged.phase().entryBindings(), "The entries stay"),
                () -> assertTrue(merged.pruned().isEmpty(), "A single bound variant prunes nothing"));
    }

    @Test
    @DisplayName("a state the phase declares itself is refused, never silently overridden")
    void refusesCollision() {
        var collision = assertThrows(IllegalArgumentException.class,
                () -> LandingStepComposition.merge(phase(), bound("landing.main", PlanFacts.Level.MACHINE),
                        List.of(landing("landing.main", "finalize.merge-gate"))),
                "A step state has exactly one owner");

        assertTrue(collision.getMessage().contains("finalize.merge-gate"),
                "The refusal names the state declared twice");
    }

    @Test
    @DisplayName("a bound variant whose tree is not supplied is refused")
    void refusesMissingTree() {
        var missing = assertThrows(IllegalArgumentException.class,
                () -> LandingStepComposition.merge(phase(), bound("landing.main", PlanFacts.Level.MACHINE),
                        List.of(landing("landing.files", "finalize.land-files"))),
                "The bound variant's tree must be supplied");

        assertTrue(missing.getMessage().contains("landing.main"), "The refusal names the variant");
    }

    @Test
    @DisplayName("a tree that is no landing variant, and another axis, are refused")
    void refusesWrongAxis() {
        var wrongAxis = assertThrows(IllegalArgumentException.class,
                () -> LandingStepComposition.merge(phase(),
                        new PlanFacts.BoundVariant(PlanFacts.Axis.ASKING,
                                PlanFacts.Axis.ASKING.shipped(), PlanFacts.Level.PLAN),
                        List.of(landing("landing.main", "finalize.land-main"))),
                "The merge composes the landing axis only");
        var wrongTree = assertThrows(IllegalArgumentException.class,
                () -> LandingStepComposition.merge(phase(), bound("landing.main", PlanFacts.Level.PLAN),
                        List.of(PlanFixtures.parse("ask.questions-only.pmwf", """
                                ---
                                name: ask.questions-only
                                description: A variant of another axis.
                                type: subworkflow
                                icon: workflow.default
                                entry: start
                                exit: continue
                                modification_date: 2026-01-15
                                version: 0.1.0
                                author: plan-marshall
                                ---
                                workflow ask.questions-only version 1 format 1
                                scope subworkflow
                                entry start -> ask.begin

                                state ask.begin
                                    auto test.proceed
                                        done -> ask.done

                                state ask.done terminal continue success
                                """, UnitOrigin.DEFAULT), landing("landing.main", "finalize.land-main"))),
                "A tree of another axis is no landing variant");

        assertAll(
                () -> assertTrue(wrongAxis.getMessage().contains("asking"), "The refusal names the axis"),
                () -> assertTrue(wrongTree.getMessage().contains("ask.questions-only"),
                        "The refusal names the unit"));
    }

    @Test
    @DisplayName("only the finalize phase holds the landing step states")
    void holdsLandingSteps() {
        assertAll(
                () -> assertTrue(LandingStepComposition.holdsLandingSteps(phase()), "The phase is the landing's home"),
                () -> assertFalse(LandingStepComposition.holdsLandingSteps(landing("landing.main")),
                        "A landing variant is not the phase"));
    }
}
