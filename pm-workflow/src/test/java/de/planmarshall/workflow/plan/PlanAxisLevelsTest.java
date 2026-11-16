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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.plan.PlanInstanceHeader.Posture;

/**
 * The reader of the three levels of the plan behaviour axes (Workflow DSL § Plan Behaviour Axes, item 2): the machine's
 * {@code plan_defaults}, the project's values and the plan's own overrides, each read from its own document and mapped
 * onto the axes; a key or a value the axes do not admit is refused with its level, and a level that records nothing
 * defers to the next. The posture resolves by the same levels, and a plan that no level sets it for asks it.
 */
@DisplayName("Plan axis levels")
class PlanAxisLevelsTest {

    private static Map<String, String> values(String... pairs) {
        var values = new LinkedHashMap<String, String>();
        for (var index = 0; index < pairs.length; index += 2) {
            values.put(pairs[index], pairs[index + 1]);
        }
        return values;
    }

    @Test
    @DisplayName("the machine's plan defaults are read as the machine level of the axes")
    void machineLevel() {
        var levels = PlanAxisLevels.of(PlanFacts.Level.MACHINE,
                values("landing", "landing.main", "method", "method.test-first", "asking", "ask.key-points"));
        var facts = levels.applyTo(PlanFacts.builder()).build();

        assertAll(
                () -> assertEquals(PlanFacts.Level.MACHINE, facts.bound(PlanFacts.Axis.LANDING).level(),
                        "The landing level is the machine's"),
                () -> assertEquals("landing.main", facts.bound(PlanFacts.Axis.LANDING).variant(),
                        "The machine's landing variant is bound"),
                () -> assertEquals("method.test-first", facts.bound(PlanFacts.Axis.METHOD).variant(),
                        "The machine's method variant is bound"),
                () -> assertEquals("ask.key-points", facts.bound(PlanFacts.Axis.ASKING).variant(),
                        "The machine's asking variant is bound"));
    }

    @Test
    @DisplayName("the nearest level that sets an axis wins and an axis no level sets stays shipped")
    void precedence() {
        var levels = PlanAxisLevels.builder()
                .level(PlanFacts.Level.MACHINE, values("landing", "landing.files", "method", "method.test-first"))
                .level(PlanFacts.Level.PROJECT, values("landing", "landing.main"))
                .build();
        var facts = levels.applyTo(PlanFacts.builder()).build();

        assertAll(
                () -> assertEquals("landing.main", facts.bound(PlanFacts.Axis.LANDING).variant(),
                        "The project's landing wins over the machine's"),
                () -> assertEquals("method.test-first", facts.bound(PlanFacts.Axis.METHOD).variant(),
                        "The method defers to the machine's level"),
                () -> assertEquals(PlanFacts.Axis.ASKING.shipped(), facts.bound(PlanFacts.Axis.ASKING).variant(),
                        "An asking no level sets stays the shipped default"));
    }

    @Test
    @DisplayName("the posture resolves by the same levels and keeps the shipped no-value where none sets it")
    void posture() {
        var fromMachine = PlanAxisLevels.of(PlanFacts.Level.MACHINE, values("posture", "standard"));
        var fromProject = PlanAxisLevels.builder()
                .level(PlanFacts.Level.MACHINE, values("posture", "standard"))
                .level(PlanFacts.Level.PROJECT, values("posture", "full"))
                .build();
        var unset = PlanAxisLevels.of(PlanFacts.Level.PLAN, null);
        var applied = fromMachine.applyTo(PlanFacts.builder()).build();

        assertAll(
                () -> assertEquals(Optional.of(Posture.STANDARD), fromMachine.posture(),
                        "The machine's posture is read"),
                () -> assertEquals(Optional.of(Posture.FULL), fromProject.posture(),
                        "The project's posture wins over the machine's"),
                () -> assertEquals(Optional.empty(), unset.posture(),
                        "A posture no level sets stays unset, so a plan asks it"),
                () -> assertEquals(Posture.STANDARD, applied.posture(),
                        "The applied posture is the reader's"));
    }

    @Test
    @DisplayName("a key or a value the axes do not admit is refused with its level")
    void refusals() {
        var unknownKey = values("landing_axis", "landing.main");
        var unknownVariant = values("landing", "landing.squash");
        var unknownPosture = values("posture", "relaxed");
        var shipped = values("landing", "landing.main");

        var keyRefusal = assertThrows(IllegalArgumentException.class,
                () -> PlanAxisLevels.of(PlanFacts.Level.PROJECT, unknownKey), "An unknown key is refused");
        var variantRefusal = assertThrows(IllegalArgumentException.class,
                () -> PlanAxisLevels.of(PlanFacts.Level.MACHINE, unknownVariant), "An unknown variant is refused");
        var postureRefusal = assertThrows(IllegalArgumentException.class,
                () -> PlanAxisLevels.of(PlanFacts.Level.PLAN, unknownPosture), "An unknown posture is refused");
        var shippedRefusal = assertThrows(IllegalArgumentException.class,
                () -> PlanAxisLevels.of(PlanFacts.Level.SHIPPED, shipped),
                "The shipped level is no recorded level");

        assertAll(
                () -> assertTrue(keyRefusal.getMessage().contains("project"),
                        "The refusal names the level of the unknown key"),
                () -> assertTrue(variantRefusal.getMessage().contains("landing.squash"),
                        "The refusal names the refused variant"),
                () -> assertTrue(postureRefusal.getMessage().contains("relaxed"),
                        "The refusal names the refused posture"),
                () -> assertTrue(shippedRefusal.getMessage().contains("shipped"),
                        "The refusal names the shipped level"));
    }

    @Test
    @DisplayName("a level that records nothing contributes no axis and no posture")
    void emptyLevel() {
        var levels = PlanAxisLevels.builder().level(PlanFacts.Level.PROJECT, null).build();

        assertAll(
                () -> assertTrue(levels.axes().isEmpty(), "No axis is read from an empty level"),
                () -> assertEquals(Optional.empty(), levels.posture(), "No posture is read from an empty level"));
    }
}
