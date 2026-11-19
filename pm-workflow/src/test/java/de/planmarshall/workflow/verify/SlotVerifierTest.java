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

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * The Rule 6 slot checks of PLAN-V2-15 (Deliverable 2): a fills clause naming an undeclared slot, and a slot read at a
 * call that the unit never fills. The units are written for the test.
 */
@DisplayName("Rule 6 slot checks")
class SlotVerifierTest {

    private static WorkflowDefinition parse(String source) {
        var result = WorkflowParser.parse("slot-verify.pmwf", source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> "the unit was refused: " + result).definition();
    }

    private List<Violation> violations(String source) {
        var validator = new WorkflowValidator(TestPrimitives.registry(), List.of(), TestPrimitives.entityKinds(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), WorkflowCatalog.of());
        return validator.verify(parse(source)).violations();
    }

    @Test
    @DisplayName("a fills clause naming an undeclared slot is refused")
    void undeclaredSlotFilled() {
        var found = violations("""
                ---
                name: Slot verify
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow demo.slots version 1 format 1
                scope workspace
                start demo.choose

                state demo.choose
                    fills slot.commit_message from message
                    job llm change role reviewer
                        submitted -> demo.done
                        lost -> demo.done

                state demo.done terminal done success
                """);

        assertTrue(found.stream().anyMatch(violation -> violation.message().contains("undeclared slot")),
                "the verifier refuses a fills of an undeclared slot");
    }

    @Test
    @DisplayName("a slot read at a call but never filled is refused")
    void readButNeverFilled() {
        var found = violations("""
                ---
                name: Slot verify
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                slot: commit_message
                slot_type: commit_message[1..2000]
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow demo.slots version 1 format 1
                scope workspace
                start demo.invoke

                state demo.invoke
                    call git.commit_and_push(message = slot.commit_message)
                        done -> demo.done
                        stepped_out -> demo.done

                state demo.done terminal done success
                """);

        assertTrue(found.stream().anyMatch(violation -> violation.message().contains("is never filled")),
                "the verifier refuses a slot read but never filled");
    }
}