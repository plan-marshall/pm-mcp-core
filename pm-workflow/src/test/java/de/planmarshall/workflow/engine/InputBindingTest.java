/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.callingPlan;
import static de.planmarshall.workflow.engine.EngineFixtures.parse;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.id.CommandId;
import de.planmarshall.core.id.CorrelationId;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The identity a call frame carries (D3/D10 of PLAN-V2-15): the engine draws a {@code command_id} per pushed frame and a
 * {@code correlation_id} per advance, both of the grammar of the identifiers registry, from the {@code ids} port; a
 * frame in the store names both; and two engines built with identically seeded generators push identical frames.
 * <p>
 * The full slot-to-frame bind and the fill of a slot from a task or operator answer are covered by the codec and
 * sub-workflow tests; this class pins the identity the frame carries.
 */
@EnableGeneratorController
@DisplayName("Input binding: the identity of a call frame")
class InputBindingTest {

    private static final String SUB_ARGS = """
            ---
            name: sub.args
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            input: mode
            input_type: enum[strict|lenient]
            input: rounds
            input_type: int[1..5]
            input: targets
            input_type: list<enum[origin|mirror]>
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.args version 1 format 1
            scope subworkflow
            entry main -> sub.wait

            state sub.wait
                job test.build
                    succeeded -> sub.done
                    failed -> sub.stepped-out
                    cancelled -> sub.stepped-out

            state sub.done terminal done success

            state sub.stepped-out terminal stepped_out stepped_out
            """;

    private static final String CALL_ARGS = """
            state execute.invoke
                call sub.args(mode = strict, rounds = 2, targets = [origin, mirror])
                    done -> completed
                    stepped_out -> plan.return
            """;

    private static WorkflowEngine argsEngine(EngineFixtures fixtures) {
        WorkflowDefinition callee = parse("sub.pmwf", SUB_ARGS);
        return fixtures.engine(callingPlan("execute.invoke", CALL_ARGS), callee);
    }

    @Test
    @DisplayName("a pushed frame carries command and correlation ids of the grammar")
    void idsMatchTheGrammar() {
        var fixtures = new EngineFixtures();
        var status = assertInstanceOf(EngineStep.Resting.class,
                argsEngine(fixtures).start(JournalScopeType.PLAN, PLAN_ID), "the call rests in the callee").status();

        var frame = status.callStack().getFirst();
        assertAll(
                () -> assertTrue(CommandId.parse(frame.commandId().toString()).isPresent(),
                        "the command id parses back to itself"),
                () -> assertTrue(CorrelationId.parse(frame.correlationId().toString()).isPresent(),
                        "the correlation id parses back to itself"));
    }

    @Test
    @DisplayName("two engines with identically seeded generators push identical frames")
    void identicalSeedsPushIdenticalFrames() {
        var first = argsEngine(new EngineFixtures()).start(JournalScopeType.PLAN, PLAN_ID);
        var second = argsEngine(new EngineFixtures()).start(JournalScopeType.PLAN, PLAN_ID);

        var firstFrame = assertInstanceOf(EngineStep.Resting.class, first, "the first call rests").status()
                .callStack().getFirst();
        var secondFrame = assertInstanceOf(EngineStep.Resting.class, second, "the second call rests").status()
                .callStack().getFirst();
        assertAll(
                () -> assertEquals(firstFrame.commandId(), secondFrame.commandId(),
                        "the same seed draws the same command id"),
                () -> assertEquals(firstFrame.correlationId(), secondFrame.correlationId(),
                        "the same seed draws the same correlation id"));
    }

    @Test
    @DisplayName("the frame in the store names the ids the engine drew")
    void storeFrameCarriesTheIds() {
        var fixtures = new EngineFixtures();
        var status = assertInstanceOf(EngineStep.Resting.class,
                argsEngine(fixtures).start(JournalScopeType.PLAN, PLAN_ID), "the call rests in the callee").status();

        WorkflowStatus.CallFrame stored = status.callStack().getFirst();
        assertAll(
                () -> assertTrue(CommandId.parse(stored.commandId().toString()).isPresent(),
                        "the stored frame names a command id"),
                () -> assertTrue(CorrelationId.parse(stored.correlationId().toString()).isPresent(),
                        "the stored frame names a correlation id"));
    }
}