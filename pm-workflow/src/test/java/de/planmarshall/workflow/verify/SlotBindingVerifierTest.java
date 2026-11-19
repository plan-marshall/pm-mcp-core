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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.dsl.ParseResult;
import de.planmarshall.workflow.dsl.WorkflowParser;

/**
 * The Rule 6 slot binding of PLAN-V2-15 (Deliverable 2): a call that reads a slot the frontmatter does not declare, and
 * a slot type that does not fit the input type of the callee. The assignability of every input type is checked once
 * with a type that fits and once with one that does not. The units are written for the test.
 */
@DisplayName("Rule 6 slot binding at a call")
class SlotBindingVerifierTest {

    /** The callee: the one entry {@code main}, the one exit {@code done} and the input {@code value} of the type. */
    private static final String CALLEE = """
            ---
            name: callee.inputs
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            input: value
            input_type: %s
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow callee.inputs version 1 format 1
            scope subworkflow
            entry main -> callee.done

            state callee.done terminal done success
            """;

    /** The caller reads {@code slot.value} at its call; the parameter is the slot declaration, empty or a slot. */
    private static final String CALLER = """
            ---
            name: Inline unit
            description: A unit written for the verifier tests.
            type: workflow
            icon: workflow.default
            %smodification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow caller.inline version 1 format 1
            scope plan
            start caller.invoke

            state caller.invoke
                call callee.inputs(value = slot.value)
                    done -> caller.done
                    stepped_out -> plan.return

            state plan.return terminal returned failure

            state caller.done terminal done success
            """;

    /**
     * The caller of the matched control: an operator state fills the declared slot, and the call reads it, so the unit
     * passes every check.
     */
    private static final String FILLING_CALLER = """
            ---
            name: Inline unit
            description: A unit written for the verifier tests.
            type: workflow
            icon: workflow.default
            slot: value
            slot_type: text
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow caller.inline version 1 format 1
            scope plan
            start caller.answer

            state caller.answer
                fills slot.value from note
                link submit answer (note: text[1..80], comment: text)
                    do test.choose
                        chosen -> caller.invoke
                        declined -> caller.invoke

            state caller.invoke
                call callee.inputs(value = slot.value)
                    done -> caller.done
                    stepped_out -> plan.return

            state plan.return terminal returned failure

            state caller.done terminal done success
            """;

    private static WorkflowDefinition parse(String resource, String source) {
        var result = WorkflowParser.parse(resource, source, UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> resource + " was refused: " + result)
                .definition();
    }

    /**
     * @param slotDeclaration the slot lines of the caller's frontmatter, or the empty string
     * @param inputType       the type of the callee's input
     * @return the violations of the caller, verified against a catalog holding the callee
     */
    private static List<Violation> violations(String slotDeclaration, String inputType) {
        var callee = parse("callee.inputs.pmwf", CALLEE.formatted(inputType));
        var validator = new WorkflowValidator(TestPrimitives.registry(), List.of(), TestPrimitives.entityKinds(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), WorkflowCatalog.of(callee));
        return validator.verify(parse("caller.inline.pmwf", CALLER.formatted(slotDeclaration))).violations();
    }

    /** @return the violations the verifier reports for the message fragment */
    private static List<Violation> reporting(List<Violation> found, String fragment) {
        return found.stream().filter(violation -> violation.message().contains(fragment)).toList();
    }

    static Stream<Arguments> slotTypes() {
        return Stream.of(
                Arguments.of("enum[strict]", "enum[strict|lenient]", true),
                Arguments.of("enum[strict|lenient]", "enum[strict]", false),
                Arguments.of("int[2..3]", "int[1..5]", true),
                Arguments.of("int[1..10]", "int[1..5]", false),
                Arguments.of("int", "int[1..5]", false),
                Arguments.of("int", "int", true),
                Arguments.of("int[1..2]", "int[3..5]", false),
                Arguments.of("text[1..80]", "text", true),
                Arguments.of("text", "text[1..80]", false),
                Arguments.of("text", "text", true),
                Arguments.of("text[1..2]", "text", true),
                Arguments.of("text[1..2]", "text[3..5]", false),
                Arguments.of("commit_message[1..100]", "commit_message", true),
                Arguments.of("commit_message", "commit_message[1..100]", false),
                Arguments.of("commit_message[1..2]", "commit_message[3..5]", false),
                Arguments.of("path", "path", true),
                Arguments.of("path", "text", false),
                Arguments.of("list<path>", "list<path>", true),
                Arguments.of("list<path>", "list<enum[origin|mirror]>", false),
                Arguments.of("list<enum[origin]>", "list<enum[origin|mirror]>", true),
                Arguments.of("list<enum[origin|mirror]>", "list<enum[origin]>", false),
                Arguments.of("enum[strict]", "text", false),
                Arguments.of("int[1..5]", "text", false),
                Arguments.of("text", "path", false),
                Arguments.of("commit_message", "text", false),
                Arguments.of("list<enum[origin]>", "list<path>", false),
                Arguments.of("path", "list<path>", false),
                Arguments.of("list<path>", "path", false));
    }

    private static String slot(String slotType) {
        return "slot: value\nslot_type: " + slotType + "\n";
    }

    @Nested
    @DisplayName("Declaration")
    class Declaration {

        @Test
        @DisplayName("Should refuse a slot read at a call that the frontmatter declares nowhere")
        void shouldRefuseUndeclaredSlotRead() {
            var found = violations("", "text");

            var declaring = reporting(found, "is not declared");
            assertEquals(1, found.size(), () -> "only the undeclared slot is refused: " + found);
            assertEquals(1, declaring.size(), () -> "the undeclared slot is refused: " + found);
            assertTrue(declaring.getFirst().message().contains("slot 'value'"),
                    "the refusal names the slot the call reads");
        }

        @Test
        @DisplayName("Should pass a unit whose operator state fills the slot its call reads")
        void shouldPassAFilledSlot() {
            var callee = parse("callee.inputs.pmwf", CALLEE.formatted("text"));
            var validator = new WorkflowValidator(TestPrimitives.registry(), List.of(), TestPrimitives.entityKinds(),
                    QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), WorkflowCatalog.of(callee));

            var found = validator.verify(parse("caller.inline.pmwf", FILLING_CALLER)).violations();

            assertEquals(List.of(), found, () -> "the filled slot passes every check: " + found);
        }
    }

    @Nested
    @DisplayName("Assignability")
    class Assignability {

        @ParameterizedTest(name = "slot {0} into input {1}")
        @MethodSource("de.planmarshall.workflow.verify.SlotBindingVerifierTest#slotTypes")
        @DisplayName("Should admit a slot type that fits the input and refuse one that does not")
        void shouldCheckTheSlotType(String slotType, String inputType, boolean assignable) {
            var found = violations(slot(slotType), inputType);

            var mismatches = reporting(found, "is not assignable");
            if (assignable) {
                assertEquals(List.of(), mismatches, () -> "a fitting slot type is admitted: " + found);
            } else {
                assertEquals(1, mismatches.size(), () -> "the fitting slot type is refused: " + found);
                assertTrue(mismatches.getFirst().message().contains("not assignable to the input type"),
                        "the refusal names the two types of the binding");
            }
        }
    }
}
