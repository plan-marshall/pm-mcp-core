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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.InputType;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The slot block and the text, commit_message, path and list&lt;path&gt; input types of the frontmatter reader
 * (PLAN-V2-15): the accepted forms and every refusal of their grammar. The units are written for the test, and every
 * frontmatter line, type form and refusal keyword is a literal because it is the contract the grammar states.
 */
@DisplayName("Slot and new input-type frontmatter")
class UnitFrontmatterSlotTest {

    private static final String RESOURCE = "slot-input.pmwf";

    /** The body every accepted unit of this test carries: the smallest workflow that parses. */
    private static final String BODY = """
            workflow demo.unit version 1 format 1
            scope workspace
            start demo.done

            state demo.done terminal done success
            """;

    /** The body of the slot-reference test: one call that binds the slot it declares. */
    private static final String CALL_BODY = """
            workflow demo.unit version 1 format 1
            scope workspace
            start demo.invoke

            state demo.invoke
                call git.commit_and_push(paths = slot.files)
                    done -> demo.done
                    stepped_out -> demo.done

            state demo.done terminal done success
            """;

    /** The fields every frontmatter of this test opens with. */
    private static final List<String> HEAD = List.of(
            "name: Slot and input demo",
            "description: A unit written for the tests of pm-workflow.",
            "type: workflow",
            "icon: workflow.default");

    /** The fields every frontmatter of this test closes with. */
    private static final List<String> TAIL = List.of(
            "modification_date: 2026-01-15",
            "version: 0.1.0",
            "author: plan-marshall");

    private static String unit(List<String> lines, String body) {
        var frontmatter = new ArrayList<String>(HEAD);
        frontmatter.addAll(lines);
        frontmatter.addAll(TAIL);
        return "---\n" + String.join("\n", frontmatter) + "\n---\n" + body;
    }

    private static String unit(List<String> lines) {
        return unit(lines, BODY);
    }

    private static WorkflowDefinition parsed(List<String> lines) {
        var result = WorkflowParser.parse(RESOURCE, unit(lines), UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Parsed.class, result, () -> "the unit was refused: " + result)
                .definition();
    }

    private static String refusal(List<String> lines) {
        var result = WorkflowParser.parse(RESOURCE, unit(lines), UnitOrigin.DEFAULT);
        return assertInstanceOf(ParseResult.Refused.class, result, "the unit was accepted")
                .diagnostic().message();
    }

    private static void assertRefused(List<String> lines, String keyword) {
        assertTrue(refusal(lines).contains(keyword),
                () -> "the refusal does not name '" + keyword + "'");
    }

    @Nested
    @DisplayName("Accepted input types")
    class AcceptedInputTypes {

        static Stream<Arguments> acceptedTypes() {
            return Stream.of(
                    Arguments.of("text", new InputType.Text(null, null)),
                    Arguments.of("text[1..2000]", new InputType.Text(1, 2000)),
                    Arguments.of("commit_message", new InputType.CommitMessage(null, null)),
                    Arguments.of("commit_message[1..2000]", new InputType.CommitMessage(1, 2000)),
                    Arguments.of("path", new InputType.Path()),
                    Arguments.of("list<path>", new InputType.PathList(null, null)),
                    Arguments.of("list<path>[0..500]", new InputType.PathList(0, 500)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("acceptedTypes")
        @DisplayName("an input_type reads every text, commit_message, path and list<path> form")
        void readsInputType(String sourceType, InputType expected) {
            var inputs = parsed(List.of("input: payload", "input_type: " + sourceType)).frontmatter().inputs();

            assertAll(
                    () -> assertEquals(1, inputs.size(), "the frontmatter declares one input"),
                    () -> assertEquals(expected, inputs.getFirst().type(),
                            "the input keeps the type of its source form"));
        }
    }

    @Nested
    @DisplayName("Accepted slot forms")
    class AcceptedSlotForms {

        static Stream<Arguments> acceptedTypes() {
            return Stream.of(
                    Arguments.of("text", new InputType.Text(null, null)),
                    Arguments.of("text[1..2000]", new InputType.Text(1, 2000)),
                    Arguments.of("commit_message", new InputType.CommitMessage(null, null)),
                    Arguments.of("commit_message[1..2000]", new InputType.CommitMessage(1, 2000)),
                    Arguments.of("path", new InputType.Path()),
                    Arguments.of("list<path>", new InputType.PathList(null, null)),
                    Arguments.of("list<path>[0..500]", new InputType.PathList(0, 500)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("acceptedTypes")
        @DisplayName("a slot carries every text, commit_message, path and list<path> slot_type")
        void readsSlotType(String sourceType, InputType expected) {
            var slots = parsed(List.of(
                    "slot: subject",
                    "slot_type: " + sourceType,
                    "slot_description: The value the caller supplies.")).frontmatter().slots();

            assertAll(
                    () -> assertEquals(1, slots.size(), "the frontmatter declares one slot"),
                    () -> assertEquals("subject", slots.getFirst().name(), "the slot keeps the name of its source"),
                    () -> assertEquals(expected, slots.getFirst().type(),
                            "the slot keeps the type of its source form"),
                    () -> assertEquals("The value the caller supplies.", slots.getFirst().description(),
                            "the slot keeps the description of its source"),
                    () -> assertFalse(slots.getFirst().optional(), "a slot is never optional"));
        }

        @Test
        @DisplayName("a call binds a slot.<name> of a list<path> slot as its SlotRef value")
        void bindsSlotReference() {
            var result = WorkflowParser.parse(RESOURCE, unit(List.of(
                    "slot: files",
                    "slot_type: list<path>[0..500]",
                    "slot_description: The paths the caller supplies."), CALL_BODY), UnitOrigin.DEFAULT);
            var definition = assertInstanceOf(ParseResult.Parsed.class, result,
                    () -> "the unit was refused: " + result).definition();
            var invoke = assertInstanceOf(StateBehavior.SubWorkflowCall.class,
                    definition.states().get("demo.invoke").behavior(), "the invoke state carries the call");

            assertEquals(new CallValue.SlotRef("files"), invoke.paramBindings().get("paths"),
                    "the call binds the slot reference of the slot");
        }
    }

    @Nested
    @DisplayName("Refused slot declarations")
    class RefusedSlotDeclarations {

        static Stream<Arguments> refusedSlots() {
            return Stream.of(
                    Arguments.of("a slot name with a hyphen", "is no name of lowercase letters",
                            List.of("slot: Bad-Name")),
                    Arguments.of("a slot name that is a reserved keyword", "is a reserved keyword",
                            List.of("slot: slot")),
                    Arguments.of("a slot name declared twice", "is declared twice",
                            List.of("slot: subject", "slot_type: text",
                                    "slot: subject", "slot_type: text")),
                    Arguments.of("a slot without its slot_type", "lacks its slot_type",
                            List.of("slot: subject")),
                    Arguments.of("a slot_type after the slot_description", "stands out of the order",
                            List.of("slot: subject", "slot_description: The subject line.",
                                    "slot_type: text")),
                    Arguments.of("a slot_description before the slot_type", "stands out of the order",
                            List.of("slot: subject", "slot_description: The subject line.")),
                    Arguments.of("a slot_type that carries the optional '?'", "a slot_type must not be optional",
                            List.of("slot: subject", "slot_type: text?")),
                    Arguments.of("a slot_type without a slot line", "does not stand below a 'slot' line",
                            List.of("slot_type: text")),
                    Arguments.of("a slot_description without a slot line", "does not stand below a 'slot' line",
                            List.of("slot_description: The subject line.")),
                    Arguments.of("a slot that repeats an input name", "is the name of an input of this unit",
                            List.of("input: subject", "input_type: text", "slot: subject", "slot_type: text")),
                    Arguments.of("an input that repeats a slot name", "is the name of a slot of this unit",
                            List.of("slot: subject", "slot_type: text", "input: subject", "input_type: text")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedSlots")
        @DisplayName("a slot form outside the grammar is refused by its keyword")
        void refusesSlot(String description, String keyword, List<String> lines) {
            assertRefused(lines, keyword);
        }

        @Test
        @DisplayName("more than 16 slots are refused where the grammar bounds a frontmatter")
        void refusesMoreThanSixteenSlots() {
            var lines = new ArrayList<String>();
            for (var index = 0; index < 17; index++) {
                lines.add("slot: slot_" + index);
                lines.add("slot_type: text");
            }

            assertTrue(refusal(lines).contains("more than 16 slots"),
                    "the refusal names the bound of the slots");
        }
    }

    @Nested
    @DisplayName("Refused new input types")
    class RefusedInputTypes {

        static Stream<Arguments> refusedTypes() {
            return Stream.of(
                    Arguments.of("a text range without '..'", "is no '[<low>..<high>]'",
                            List.of("input: payload", "input_type: text[x]")),
                    Arguments.of("a list<path> range without '..'", "is no '[<low>..<high>]'",
                            List.of("input: payload", "input_type: list<path>[0]")),
                    Arguments.of("a text range with a non-numeric high bound", "is no '[<low>..<high>]'",
                            List.of("input: payload", "input_type: text[1..x]")),
                    Arguments.of("a text range whose bounds are inverted", "has its low bound above its high bound",
                            List.of("input: payload", "input_type: text[5..1]")),
                    Arguments.of("a list<path> range whose bounds are inverted",
                            "has its low bound above its high bound",
                            List.of("input: payload", "input_type: list<path>[5..1]")),
                    Arguments.of("a text range beyond the integer bounds", "is out of range",
                            List.of("input: payload", "input_type: text[0..99999999999999]")),
                    Arguments.of("an unknown input_type", "is none of enum[...], int, text, commit_message, path",
                            List.of("input: payload", "input_type: bogus")),
                    Arguments.of("a list<path> with trailing content", "is no list<path> or list<path>",
                            List.of("input: payload", "input_type: list<pathx>")),
                    Arguments.of("a text input with a default outside its type", "is outside the type text",
                            List.of("input: payload", "input_type: text", "input_default: Hello")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("refusedTypes")
        @DisplayName("an input_type outside the grammar is refused by its keyword")
        void refusesInputType(String description, String keyword, List<String> lines) {
            assertRefused(lines, keyword);
        }
    }
}
