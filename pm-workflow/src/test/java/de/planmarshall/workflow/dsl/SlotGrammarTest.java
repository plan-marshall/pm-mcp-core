/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.dsl;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.InputType;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The slot grammar of PLAN-V2-15 (Deliverable 2): a {@code slot}/{@code slot_type}/{@code slot_description} block, a
 * {@code fills slot.<x> from <param>} clause on a state, and a {@code slot.<x>} reference at a call site. The unit is
 * written for the test, not taken from any default unit.
 */
@DisplayName("Slot and fills grammar")
class SlotGrammarTest {

    private static final String SOURCE = """
            ---
            name: Slot demo
            description: A unit written for the tests of pm-workflow.
            type: workflow
            icon: workflow.default
            slot: commit_message
            slot_type: commit_message[1..2000]
            slot_description: The message the caller supplies.
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
                    submitted -> demo.invoke
                    lost -> demo.done

            state demo.invoke
                call git.commit_and_push(message = slot.commit_message)
                    done -> demo.done
                    stepped_out -> demo.done

            state demo.done terminal done success
            """;

    private static WorkflowDefinition parse() {
        return parse(SOURCE);
    }

    private static WorkflowDefinition parse(String source) {
        var result = WorkflowParser.parse("slot.pmwf", source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> "the unit was refused: " + result).definition();
    }

    @Test
    @DisplayName("the frontmatter declares the slot with its type and description")
    void slotDeclaration() {
        var slots = parse().frontmatter().slots();

        assertAll(
                () -> assertEquals(1, slots.size(), "the frontmatter declares one slot"),
                () -> assertEquals("commit_message", slots.getFirst().name(), "the slot keeps its name"),
                () -> assertEquals(new InputType.CommitMessage(1, 2000), slots.getFirst().type(),
                        "the slot keeps its type"),
                () -> assertFalse(slots.getFirst().optional(), "a slot is never optional"));
    }

    @Test
    @DisplayName("the state carries its fills clause")
    void fillsClause() {
        StateNode.Fills fills = parse().states().get("demo.choose").fills();

        assertAll(
                () -> assertEquals("commit_message", fills.slotName(), "the fills clause names the slot"),
                () -> assertEquals("message", fills.fromParam(), "the fills clause names the from-parameter"));
    }

    @Test
    @DisplayName("the call site binds the slot reference")
    void slotReference() {
        var invoke = assertInstanceOf(StateBehavior.SubWorkflowCall.class,
                parse().states().get("demo.invoke").behavior(), "the invoke state is a call");

        assertEquals(new CallValue.SlotRef("commit_message"), invoke.paramBindings().get("message"),
                "the call binds the slot reference");
    }

    @Test
    @DisplayName("the unit with a slot and a fills clause prints and reparses to the same text")
    void printsCanonically() {
        var printed = WorkflowPrinter.print(parse());
        var reparsed = parse(printed);
        var reprinted = WorkflowPrinter.print(reparsed);

        assertEquals(printed, reprinted, "the printer is canonical for the slot grammar");
        assertEquals("slot: commit_message", line(printed, "slot: "), "the printed slot line is kept");
        assertEquals("fills slot.commit_message from message", line(printed.trim(), "fills "),
                "the printed fills line is kept");
    }

    private static String line(String text, String prefix) {
        return text.lines().filter(candidate -> candidate.trim().startsWith(prefix) || candidate.startsWith(prefix))
                .map(String::trim).findFirst().orElse("");
    }
}