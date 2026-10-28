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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.ast.WorkflowScope;

/**
 * The resident machine unit (Workflow DSL, Machine Scope, item 7; Static AST Verification Rules, Rule 3): the machine
 * scope runs a unit with no terminal that rests in {@code onboard.completed}, and every state returns to that resting
 * state.
 */
@DisplayName("Machine scope unit")
class MachineResidentUnitTest {

    private final WorkflowValidator validator = new WorkflowValidator(TestPrimitives.registry(), List.of(),
            TestPrimitives.entityKinds(), QuestionClassRegistry.defaults(), SchemaRegistry.defaults(),
            TestPrimitives.catalog());

    @Test
    @DisplayName("Should accept a resident machine unit whose every state returns to onboard.completed")
    void residentUnitPasses() {
        var unit = machineUnit("""
                state onboard.entry
                    link next start ()
                        do test.proceed
                            done -> onboard.completed

                state onboard.completed
                    link next reconfigure ()
                        do test.proceed
                            done -> onboard.entry
                """);

        assertEquals(WorkflowScope.MACHINE, unit.scope());
        assertEquals(List.of(), validator.verify(unit).violations());
    }

    @Test
    @DisplayName("Should refuse a machine unit whose state has no path back to onboard.completed")
    void stateWithoutPathBackIsRefused() {
        var unit = machineUnit("""
                state onboard.entry
                    link next start ()
                        do test.proceed
                            done -> onboard.completed
                    link alt stray ()
                        do test.proceed
                            done -> onboard.orphan

                state onboard.completed
                    link next reconfigure ()
                        do test.proceed
                            done -> onboard.entry

                state onboard.orphan
                    link next loop ()
                        do test.proceed
                            done -> onboard.orphan
                """);

        var violations = validator.verify(unit).violations();

        assertTrue(violations.stream().anyMatch(violation -> violation.rule() == VerificationRule.EXIT_PATH
                && "onboard.orphan".equals(violation.stateId())), violations::toString);
    }

    @Test
    @DisplayName("Should refuse a machine unit that declares no resting state")
    void missingRestingStateIsRefused() {
        var unit = machineUnit("""
                state onboard.entry
                    link next start ()
                        do test.proceed
                            done -> onboard.entry
                """);

        var violations = validator.verify(unit).violations();

        assertTrue(violations.stream().anyMatch(violation -> violation.rule() == VerificationRule.EXIT_PATH
                && violation.message().contains("onboard.completed")), violations::toString);
    }

    @Test
    @DisplayName("Should refuse a machine unit that declares a terminal")
    void terminalIsRefused() {
        var unit = machineUnit("""
                state onboard.entry
                    link next start ()
                        do test.proceed
                            done -> onboard.completed

                state onboard.completed
                    link next reconfigure ()
                        do test.proceed
                            done -> onboard.entry

                state onboard.stopped terminal stopped failure
                """);

        var violations = validator.verify(unit).violations();

        assertTrue(violations.stream().anyMatch(violation ->
                violation.rule() == VerificationRule.INTERFACE_STOP_POINTS_OR_EXITS
                        && "onboard.stopped".equals(violation.stateId())), violations::toString);
    }

    private static WorkflowDefinition machineUnit(String states) {
        return VerifyFixtures.parseSource("machine.pmwf", """
                ---
                name: onboarding
                description: The resident machine unit, written for the verifier tests.
                type: system
                icon: workflow.default
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow onboarding version 1 format 1
                scope machine
                start onboard.entry

                """ + states);
    }
}
