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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * The cost of Rule 6's slot dominance, measured instead of argued: the walk is one breadth-first pass over the unit's
 * states per slot a call reads. The measured figure is published to the test report and recorded in ADR
 * {@code workflow-verify/039}.
 * <p>
 * The unit deliberately fails the dominance check: the fill sits on one branch, and a bypass chain of plain auto steps
 * reaches the call on the other, so the walk must cross the whole chain before it finds the call. The failure is the
 * point — the walk is only complete when it reaches the call it cannot dominate.
 */
@DisplayName("Slot dominance cost")
class SlotDominanceCostTest {

    /** The states of the bypass chain: the size the walk must cross. */
    private static final int BYPASS_STATES = 2_000;

    private static final String CALLEE_ID = "measure.callee";

    private final WorkflowCatalog catalog = WorkflowCatalog.of(parse(CALLEE_ID + ".pmwf", callee()));

    private final WorkflowValidator validator = new WorkflowValidator(TestPrimitives.registry(),
            List.of("verify.entry"), TestPrimitives.entityKinds(), QuestionClassRegistry.defaults(),
            SchemaRegistry.defaults(), catalog);

    @Test
    @DisplayName("the dominance walk crosses a 2000-state bypass chain and publishes its figure")
    void dominanceWalkIsMeasured(TestReporter reporter) {
        var definition = parse("measure-plan.pmwf", plan(BYPASS_STATES));

        var started = System.nanoTime();
        var result = validator.verify(definition);
        var elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        reporter.publishEntry("slot_dominance_walk_ms", Long.toString(elapsedMillis));
        reporter.publishEntry("slot_dominance_walk_states", Integer.toString(definition.states().size()));

        assertTrue(result.violations().stream()
                        .anyMatch(violation -> violation.message().contains("not filled on every path")),
                "the bypass path is refused by the dominance check");
    }

    /** @return the callee of the call: one commit-message input over a job state, resting on the stack */
    private static String callee() {
        return """
                ---
                name: measure.callee
                description: A unit written for the verifier tests.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                input: message
                input_type: commit_message[1..2000]
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow measure.callee version 1 format 1
                scope subworkflow
                entry main -> callee.wait

                state callee.wait
                    job test.build
                        succeeded -> callee.done
                        failed -> callee.stepped-out
                        cancelled -> callee.stepped-out

                state callee.done terminal done success

                state callee.stepped-out terminal stepped_out stepped_out
                """;
    }

    /**
     * @param bypassStates the number of plain auto states on the bypass chain
     * @return the text of the measured plan unit
     */
    private static String plan(int bypassStates) {
        var states = new StringBuilder("""
                state verify.entry
                    auto test.ready
                        held -> measure.fill
                        not_held -> measure.bypass-1

                state measure.fill
                    fills slot.message from message
                    job llm change role reviewer
                        approved -> measure.invoke
                        lost -> measure.handed-back

                """);
        for (var index = 1; index <= bypassStates; index++) {
            var next = index == bypassStates ? "measure.invoke" : "measure.bypass-" + (index + 1);
            states.append("state measure.bypass-").append(index)
                    .append("\n    auto test.proceed\n        done -> ").append(next).append("\n\n");
        }
        states.append("""

                state measure.invoke
                    call measure.callee(message = slot.message)
                        done -> measure.completed
                        stepped_out -> measure.handed-back

                state measure.completed terminal done success

                state measure.handed-back terminal handed_back stepped_out
                """);
        return """
                ---
                name: plan
                description: A unit written for the verifier tests.
                type: workflow
                icon: workflow.default
                slot: message
                slot_type: commit_message[1..2000]
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow measure.plan version 1 format 1
                scope plan
                start verify.entry

                %s""".formatted(states);
    }

    /**
     * @param name   the name its source locations carry
     * @param source the text of the unit
     * @return the tree of the unit
     */
    private static WorkflowDefinition parse(String name, String source) {
        var result = WorkflowParser.parse(name, source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result,
                () -> "the unit follows the grammar: " + result).definition();
    }
}
