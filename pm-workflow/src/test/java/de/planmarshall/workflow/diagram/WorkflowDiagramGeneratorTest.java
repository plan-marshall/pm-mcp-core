/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.workflow.ast.WorkflowDefinition;

/**
 * The generator of the documentation of a unit (PM-IMPL-5): each of the three forms of the dedicated fixture unit
 * equals its committed golden file byte for byte, two renderings give identical bytes and no line ends in a carriage
 * return; a present active state adds the Mermaid style and the terminal mark, an unknown one is refused; a task
 * state shows its role and its outcomes, never a link its unit does not declare.
 */
@DisplayName("WorkflowDiagramGenerator")
class WorkflowDiagramGeneratorTest {

    private static final WorkflowDefinition FIXTURE = DiagramFixtures.fixture();

    static Stream<Arguments> forms() {
        return Stream.of(
                Arguments.of("Mermaid", DiagramFixtures.MERMAID_GOLDEN,
                        (Function<WorkflowDefinition, String>) unit -> WorkflowDiagramGenerator.renderMermaid(unit,
                                Optional.empty())),
                Arguments.of("AsciiDoc", DiagramFixtures.ASCIIDOC_GOLDEN,
                        (Function<WorkflowDefinition, String>) WorkflowDiagramGenerator::renderAsciiDocTables),
                Arguments.of("terminal", DiagramFixtures.ASCII_GOLDEN,
                        (Function<WorkflowDefinition, String>) unit -> WorkflowDiagramGenerator.renderAscii(unit,
                                Optional.empty())));
    }

    @Nested
    @DisplayName("Byte stability")
    class ByteStability {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.diagram.WorkflowDiagramGeneratorTest#forms")
        @DisplayName("Should equal the golden file byte for byte")
        void shouldEqualGoldenFile(String form, String golden, Function<WorkflowDefinition, String> render) {
            var expected = DiagramFixtures.read(golden).getBytes(StandardCharsets.UTF_8);

            var rendered = render.apply(FIXTURE).getBytes(StandardCharsets.UTF_8);

            assertArrayEquals(expected, rendered, () -> form + " differs from " + golden + ":\n"
                    + new String(rendered, StandardCharsets.UTF_8));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.diagram.WorkflowDiagramGeneratorTest#forms")
        @DisplayName("Should give identical bytes on a second rendering")
        void shouldRenderIdenticalBytesTwice(String form, String golden, Function<WorkflowDefinition, String> render) {
            var first = render.apply(FIXTURE).getBytes(StandardCharsets.UTF_8);

            var second = render.apply(DiagramFixtures.fixture()).getBytes(StandardCharsets.UTF_8);

            assertArrayEquals(first, second, form);
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.diagram.WorkflowDiagramGeneratorTest#forms")
        @DisplayName("Should end every line with a line feed alone")
        void shouldUseLineFeedsOnly(String form, String golden, Function<WorkflowDefinition, String> render) {
            var rendered = render.apply(FIXTURE);

            assertAll(form, () -> assertFalse(rendered.contains("\r"), "carriage return"),
                    () -> assertTrue(rendered.endsWith("\n"), "last line feed"));
        }
    }

    @Nested
    @DisplayName("Active state")
    class ActiveState {

        @Test
        @DisplayName("Should style the active state in the Mermaid diagram")
        void shouldStyleActiveStateInMermaid() {
            var rendered = WorkflowDiagramGenerator.renderMermaid(FIXTURE, Optional.of("build.judge"));

            assertTrue(rendered.endsWith("""
                        classDef active fill:#90ee90
                        class build_judge active
                    """), rendered);
        }

        @Test
        @DisplayName("Should define no style without an active state")
        void shouldDefineNoStyleWithoutActiveState() {
            var rendered = WorkflowDiagramGenerator.renderMermaid(FIXTURE, Optional.empty());

            assertFalse(rendered.contains("classDef"), rendered);
        }

        @Test
        @DisplayName("Should mark the active state in the terminal table")
        void shouldMarkActiveStateInTerminalTable() {
            var rendered = WorkflowDiagramGenerator.renderAscii(FIXTURE, Optional.of("build.judge"));

            assertAll(() -> assertTrue(rendered.contains("│ *build.judge "), rendered),
                    () -> assertTrue(rendered.contains("│  (ACTIVE) "), rendered),
                    () -> assertEquals(1, rendered.lines().filter(line -> line.contains("(ACTIVE)")).count()));
        }

        @Test
        @DisplayName("Should mark no state without an active state")
        void shouldMarkNoStateWithoutActiveState() {
            var rendered = WorkflowDiagramGenerator.renderAscii(FIXTURE, Optional.empty());

            assertAll(() -> assertFalse(rendered.contains("(ACTIVE)"), rendered),
                    () -> assertFalse(rendered.contains("│ *"), rendered));
        }

        @Test
        @DisplayName("Should refuse an active state the unit does not have")
        void shouldRefuseUnknownActiveState() {
            var unknown = Optional.of("build.unknown");

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> WorkflowDiagramGenerator.renderMermaid(FIXTURE, unknown)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> WorkflowDiagramGenerator.renderAscii(FIXTURE, unknown)));
        }

        @Test
        @DisplayName("Should refuse a missing unit or active state")
        void shouldRefuseMissingArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> WorkflowDiagramGenerator.renderMermaid(null, Optional.empty())),
                    () -> assertThrows(NullPointerException.class,
                            () -> WorkflowDiagramGenerator.renderAscii(FIXTURE, null)),
                    () -> assertThrows(NullPointerException.class,
                            () -> WorkflowDiagramGenerator.renderAsciiDocTables(null)));
        }
    }

    @Nested
    @DisplayName("Task state")
    class TaskState {

        @Test
        @DisplayName("Should show the role and the outcomes of a task state in the terminal table")
        void shouldShowRoleAndOutcomesInTerminalTable() {
            var rendered = WorkflowDiagramGenerator.renderAscii(FIXTURE, Optional.empty());

            var block = rendered.lines().dropWhile(line -> !line.startsWith("│ build.judge "))
                    .takeWhile(line -> !line.startsWith("├") && !line.startsWith("└")).toList();
            assertAll(() -> assertEquals(2, block.size(), String.join("\n", block)),
                    () -> assertTrue(block.get(1).startsWith("│  task reviewer "), block.get(1)),
                    () -> assertTrue(block.stream().allMatch(line -> line.contains(" │ - ")), "no task link"),
                    () -> assertTrue(block.get(0).contains("approved -> build.commit"), block.get(0)),
                    () -> assertTrue(block.get(1).contains("lost -> diagram.failed"), block.get(1)));
        }

        @Test
        @DisplayName("Should label the transitions of a task state with its role in the Mermaid diagram")
        void shouldLabelTaskTransitionsWithRole() {
            var rendered = WorkflowDiagramGenerator.renderMermaid(FIXTURE, Optional.empty());

            assertAll(() -> assertTrue(rendered.contains("build_judge --> build_commit : task reviewer / approved"),
                            rendered),
                    () -> assertTrue(rendered.contains(
                            "build_implement : task implementer each diagram.list-changes"), rendered));
        }

        @Test
        @DisplayName("Should label a task state whose roles a primitive returns")
        void shouldLabelRoleEachTaskState() {
            var unit = DiagramFixtures.parse("role-each.pmwf", """
                    workflow adjudicate.decisions version 1 format 1
                    scope subworkflow
                    start decide.vote

                    state decide.vote
                        phase inherit
                        job llm decision role each diagram.list-roles
                            approved -> decide.done

                    state decide.done terminal done success
                        phase inherit
                    """);

            var rendered = WorkflowDiagramGenerator.renderMermaid(unit, Optional.empty());

            assertTrue(rendered.contains("decide_vote --> decide_done : task role each diagram.list-roles / approved"),
                    rendered);
        }
    }

    @Nested
    @DisplayName("Mermaid identifiers")
    class MermaidIdentifiers {

        @Test
        @DisplayName("Should give colliding state identifiers distinct nodes in declaration order")
        void shouldSeparateCollidingStateIdentifiers() {
            var unit = DiagramFixtures.parse("collision.pmwf", """
                    workflow diagram.collision version 1 format 1
                    scope workspace
                    start build.judge

                    state build.judge
                        phase build
                        auto diagram.proceed
                            done -> build-judge

                    state build-judge
                        phase build
                        auto diagram.proceed
                            done -> build_judge

                    state build_judge
                        phase build
                        auto diagram.proceed
                            done -> build.judge
                    """);

            var rendered = WorkflowDiagramGenerator.renderMermaid(unit, Optional.of("build_judge"));

            assertAll(() -> assertTrue(rendered.contains("state \"build.judge\" as build_judge\n"), rendered),
                    () -> assertTrue(rendered.contains("state \"build-judge\" as build_judge_2\n"), rendered),
                    () -> assertTrue(rendered.contains("state \"build_judge\" as build_judge_3\n"), rendered),
                    () -> assertTrue(rendered.contains("build_judge --> build_judge_2 : "), rendered),
                    () -> assertTrue(rendered.contains("build_judge_2 --> build_judge_3 : "), rendered),
                    () -> assertTrue(rendered.contains("build_judge_3 --> build_judge : "), rendered),
                    () -> assertTrue(rendered.contains("[*] --> build_judge\n"), rendered),
                    () -> assertTrue(rendered.endsWith("class build_judge_3 active\n"), rendered));
        }

        @Test
        @DisplayName("Should give a phase composite an identifier no state holds, never reusing an allocated one")
        void shouldSeparatePhaseFromStateAlias() {
            var unit = DiagramFixtures.parse("phase-collision.pmwf", """
                    workflow diagram.phases version 1 format 1
                    scope workspace
                    start phase-build

                    state phase-build
                        phase build
                        auto diagram.proceed
                            done -> phase_build-2

                    state phase_build-2
                        phase review
                        auto diagram.proceed
                            done -> phase-build
                    """);

            var rendered = WorkflowDiagramGenerator.renderMermaid(unit, Optional.of("phase-build"));

            assertAll(() -> assertTrue(rendered.contains("state \"phase-build\" as phase_build\n"), rendered),
                    () -> assertTrue(rendered.contains("state \"phase_build-2\" as phase_build_2\n"), rendered),
                    () -> assertTrue(rendered.contains("state phase_build_3 {\n"), rendered),
                    () -> assertTrue(rendered.contains("state phase_review {\n"), rendered),
                    () -> assertTrue(rendered.contains("phase_build --> phase_build_2 : "), rendered),
                    () -> assertTrue(rendered.contains("phase_build_2 --> phase_build : "), rendered),
                    () -> assertTrue(rendered.contains("[*] --> phase_build\n"), rendered),
                    () -> assertTrue(rendered.endsWith("class phase_build active\n"), rendered));
        }

        @Test
        @DisplayName("Should give an undeclared target a node of its own after the declared states")
        void shouldSeparateUndeclaredTarget() {
            var unit = DiagramFixtures.parse("undeclared.pmwf", """
                    workflow diagram.undeclared version 1 format 1
                    scope workspace
                    start build_missing

                    state build_missing
                        phase build
                        auto diagram.proceed
                            done -> build.missing
                    """);

            var rendered = WorkflowDiagramGenerator.renderMermaid(unit, Optional.empty());

            assertAll(() -> assertTrue(rendered.contains("state \"build_missing\" as build_missing\n"), rendered),
                    () -> assertTrue(rendered.contains("build_missing --> build_missing_2 : "), rendered));
        }
    }

    @Nested
    @DisplayName("Link table")
    class LinkTable {

        @Test
        @DisplayName("Should print every parameter type and escape the cell separator")
        void shouldPrintEveryParameterType() {
            var unit = DiagramFixtures.parse("parameters.pmwf", """
                    workflow diagram.parameters version 1 format 1
                    scope workspace
                    start setup.choose

                    state setup.choose
                        phase setup
                        link fix fix:lint (choice: enum[FIX|SUPPRESS], body: schema[fix-plan], count: int[0..5], \
                    size: int, items: list<text>, note: text[1..80]?)
                            do setup.fix
                                done -> setup.choose
                    """);

            var rendered = WorkflowDiagramGenerator.renderAsciiDocTables(unit);

            assertTrue(rendered.contains("|`choice: enum[FIX\\|SUPPRESS]`, `body: schema[fix-plan]`, "
                    + "`count: int[0..5]`, `size: int`, `items: list<text>`, `note: text[1..80]?`\n"), rendered);
        }
    }
}
