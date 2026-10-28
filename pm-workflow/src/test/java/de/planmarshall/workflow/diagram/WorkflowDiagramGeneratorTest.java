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
import java.util.List;
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
import de.planmarshall.workflow.plan.PlanInstance;
import de.planmarshall.workflow.plan.PlanInstanceHeader;
import de.planmarshall.workflow.plan.PruneReason;
import de.planmarshall.workflow.plan.PrunedElement;
import de.planmarshall.workflow.plan.WaivedGate;

/**
 * The generator of the documentation of a unit (PM-IMPL-5): each of the three forms of the dedicated fixture unit
 * equals its committed golden file byte for byte, two renderings give identical bytes and no line ends in a carriage
 * return; a present active state adds the Mermaid style and the terminal mark, an unknown one is refused; a task
 * state shows its role and its outcomes, never a link its unit does not declare. The three instance forms of the
 * fixture instance equal their golden files as well, name the same pruned elements and waived gates, and refuse an
 * active state the instance does not have.
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
                    ---
                    name: adjudicate.decisions
                    description: A unit written for the tests of pm-workflow.
                    type: subworkflow
                    icon: workflow.default
                    exit: done
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
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
    @DisplayName("Entries")
    class Entries {

        /** A sub-workflow with three entries, two of which bind the same state. */
        private static final WorkflowDefinition ENTRIES = DiagramFixtures.parse("entries.pmwf", """
                ---
                name: diagram.entries
                description: A sub-workflow with three entries written for the diagram tests.
                type: subworkflow
                icon: workflow.default
                entry: review
                entry: repair
                entry: recheck
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow diagram.entries version 1 format 1
                scope subworkflow
                entry review -> build.review
                entry repair -> build.repair
                entry recheck -> build.review

                state build.review
                    phase inherit
                    auto diagram.proceed
                        done -> build.done

                state build.repair
                    phase inherit
                    auto diagram.proceed
                        done -> build.review

                state build.done terminal done success
                    phase inherit
                """);

        @Test
        @DisplayName("Should draw an arrow from the start into every entry-bound state, labelled with its entry, and "
                + "no unlabelled start arrow")
        void shouldDrawEveryEntryAsStartingPoint() {
            var rendered = WorkflowDiagramGenerator.renderMermaid(ENTRIES, Optional.empty());

            assertAll(() -> assertTrue(rendered.contains("[*] --> build_review : entry review\n"), rendered),
                    () -> assertTrue(rendered.contains("[*] --> build_repair : entry repair\n"), rendered),
                    () -> assertTrue(rendered.contains("[*] --> build_review : entry recheck\n"), rendered),
                    () -> assertEquals(3, rendered.lines().filter(line -> line.contains("[*] -->")).count(),
                            rendered));
        }

        @Test
        @DisplayName("Should name every entry that binds a state in its Entered from column, before its sources")
        void shouldNameEntriesInEnteredFrom() {
            var rendered = WorkflowDiagramGenerator.renderAsciiDocTables(ENTRIES);

            assertAll(() -> assertTrue(rendered.contains("entry `review`, entry `recheck`, `build.repair`"), rendered),
                    () -> assertTrue(rendered.contains("|entry `repair`\n"), rendered),
                    () -> assertFalse(rendered.contains("|start"), rendered));
        }
    }

    @Nested
    @DisplayName("Mermaid identifiers")
    class MermaidIdentifiers {

        @Test
        @DisplayName("Should give colliding state identifiers distinct nodes in declaration order")
        void shouldSeparateCollidingStateIdentifiers() {
            var unit = DiagramFixtures.parse("collision.pmwf", """
                    ---
                    name: diagram.collision
                    description: A unit written for the tests of pm-workflow.
                    type: system
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
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
                    ---
                    name: diagram.phases
                    description: A unit written for the tests of pm-workflow.
                    type: system
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
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
                    ---
                    name: diagram.undeclared
                    description: A unit written for the tests of pm-workflow.
                    type: system
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
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
    @DisplayName("Instance forms")
    class InstanceForms {

        private final PlanInstance instance = DiagramFixtures.instance();

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.diagram.WorkflowDiagramGeneratorTest#instanceForms")
        @DisplayName("Should equal the golden file byte for byte")
        void shouldEqualGoldenFile(String form, String golden, InstanceForm render) {
            var expected = DiagramFixtures.read(golden).getBytes(StandardCharsets.UTF_8);

            var rendered = render.apply(instance, Optional.empty()).getBytes(StandardCharsets.UTF_8);

            assertArrayEquals(expected, rendered, () -> form + " differs from " + golden + ":\n"
                    + new String(rendered, StandardCharsets.UTF_8));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.diagram.WorkflowDiagramGeneratorTest#instanceForms")
        @DisplayName("Should give identical bytes on a second rendering and end every line with a line feed alone")
        void shouldRenderIdenticalBytesTwice(String form, String golden, InstanceForm render) {
            var first = render.apply(instance, Optional.empty());

            var second = render.apply(DiagramFixtures.instance(), Optional.empty());

            assertAll(form, () -> assertArrayEquals(first.getBytes(StandardCharsets.UTF_8),
                            second.getBytes(StandardCharsets.UTF_8)),
                    () -> assertFalse(first.contains("\r"), "carriage return"),
                    () -> assertTrue(first.endsWith("\n"), "last line feed"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.diagram.WorkflowDiagramGeneratorTest#instanceForms")
        @DisplayName("Should name every pruned element with its reason and source fact and every waived gate with its "
                + "waiver id")
        void shouldNameSameAnnotations(String form, String golden, InstanceForm render) {
            var rendered = render.apply(instance, Optional.empty());

            assertAll(form, Stream.concat(
                    DiagramFixtures.PRUNED.stream().flatMap(pruned -> Stream.of(pruned.element().wireName(),
                            pruned.id(), pruned.reason().wireName(), pruned.sourceFact())),
                    Stream.of(DiagramFixtures.WAIVED_GATE, DiagramFixtures.WAIVER_ID, "plan diagram-fixture v3 (plan)"))
                    .map(expected -> () -> assertTrue(rendered.contains(expected), expected + " missing in\n"
                            + rendered)));
        }

        @Test
        @DisplayName("Should style the active state in the instance Mermaid diagram")
        void shouldStyleActiveStateInMermaid() {
            var rendered = WorkflowDiagramGenerator.renderInstanceMermaid(instance,
                    Optional.of(DiagramFixtures.WAIVED_GATE));

            assertAll(() -> assertTrue(rendered.contains("""
                                classDef active fill:#90ee90
                                class finalize_merge_gate active
                            """), rendered),
                    () -> assertFalse(WorkflowDiagramGenerator.renderInstanceMermaid(instance, Optional.empty())
                            .contains("classDef"), "no style without an active state"));
        }

        @Test
        @DisplayName("Should name the active state in the instance AsciiDoc tables")
        void shouldNameActiveStateInAsciiDoc() {
            var rendered = WorkflowDiagramGenerator.renderInstanceAsciiDocTables(instance,
                    Optional.of(DiagramFixtures.WAIVED_GATE));

            assertAll(() -> assertTrue(rendered.contains("|Active state\n|`finalize.merge-gate`\n"), rendered),
                    () -> assertTrue(WorkflowDiagramGenerator.renderInstanceAsciiDocTables(instance, Optional.empty())
                            .contains("|Active state\n|-\n"), "no active state"));
        }

        @Test
        @DisplayName("Should mark the active state in the instance terminal tables")
        void shouldMarkActiveStateInTerminalTable() {
            var rendered = WorkflowDiagramGenerator.renderInstanceAscii(instance,
                    Optional.of(DiagramFixtures.WAIVED_GATE));

            assertAll(() -> assertTrue(rendered.contains("│ *finalize.merge-gate "), rendered),
                    () -> assertEquals(1, rendered.lines().filter(line -> line.contains("(ACTIVE)")).count()),
                    () -> assertFalse(WorkflowDiagramGenerator.renderInstanceAscii(instance, Optional.empty())
                            .contains("(ACTIVE)"), "no mark without an active state"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.diagram.WorkflowDiagramGeneratorTest#instanceForms")
        @DisplayName("Should refuse an active state the instance does not have, a pruned state included")
        @SuppressWarnings("java:S5778") // the lambdas name the refused call; hoisting its argument hides the case
        void shouldRefuseUnknownActiveState(String form, String golden, InstanceForm render) {
            assertAll(form,
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> render.apply(instance, Optional.of("finalize.unknown"))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> render.apply(instance, Optional.of("finalize.merge-dispatch"))));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.diagram.WorkflowDiagramGeneratorTest#instanceForms")
        @DisplayName("Should refuse a missing instance or active state")
        void shouldRefuseMissingArguments(String form, String golden, InstanceForm render) {
            assertAll(form,
                    () -> assertThrows(NullPointerException.class, () -> render.apply(null, Optional.empty())),
                    () -> assertThrows(NullPointerException.class, () -> render.apply(instance, null)));
        }

        @Test
        @DisplayName("Should leave out the annotations of an instance that prunes and waives nothing")
        void shouldOmitEmptyAnnotations() {
            var bare = bareInstance();

            var mermaid = WorkflowDiagramGenerator.renderInstanceMermaid(bare, Optional.empty());
            var asciiDoc = WorkflowDiagramGenerator.renderInstanceAsciiDocTables(bare, Optional.empty());
            var terminal = WorkflowDiagramGenerator.renderInstanceAscii(bare, Optional.empty());

            assertAll(() -> assertFalse(mermaid.contains("instance_pruned"), mermaid),
                    () -> assertFalse(mermaid.contains("instance_waived"), mermaid),
                    () -> assertFalse(asciiDoc.contains("pruned elements"), asciiDoc),
                    () -> assertFalse(asciiDoc.contains("waived gates"), asciiDoc),
                    () -> assertFalse(terminal.contains("Source fact"), terminal),
                    () -> assertFalse(terminal.contains("Waiver id"), terminal),
                    () -> assertTrue(asciiDoc.contains("|Lane\n|-\n"), asciiDoc),
                    () -> assertTrue(terminal.contains("│ Posture      │ -"), terminal));
        }

        @Test
        @DisplayName("Should give the annotations Mermaid identifiers that no state of the unit can hold")
        void shouldSeparateAnnotationIdentifiersFromUnit() {
            var source = """
                    ---
                    name: plan
                    description: A unit written for the tests of pm-workflow.
                    type: workflow
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
                    workflow plan version 3 format 1
                    scope plan
                    start instance.pruned

                    state instance.pruned
                        phase build
                        auto diagram.proceed
                            done -> instance-pruned

                    state instance-pruned
                        phase build
                        auto diagram.proceed
                            done -> instance_waived_1
                    """;
            var unit = DiagramFixtures.parse("annotation-collision.pmwf", source);

            var rendered = WorkflowDiagramGenerator.renderInstanceMermaid(
                    DiagramFixtures.instance(unit, source), Optional.empty());

            assertAll(() -> assertTrue(rendered.contains("state \"instance-pruned\" as instance_pruned_2\n"), rendered),
                    () -> assertTrue(rendered.contains("instance_pruned --> instance_pruned_2 : "), rendered),
                    () -> assertTrue(rendered.contains("state \"pruned\" as instance_pruned_6 {\n"), rendered),
                    () -> assertTrue(rendered.contains("as instance_pruned_1\n"), rendered),
                    () -> assertTrue(rendered.contains("as instance_pruned_2_2\n"), rendered),
                    () -> assertTrue(rendered.contains("state \"waived gates\" as instance_waived {\n"), rendered),
                    () -> assertTrue(rendered.contains("as instance_waived_1_6\n"), rendered));
        }

        @Test
        @DisplayName("Should escape a double quote, a line break and a hash in the text of a label")
        void shouldEscapeLabelText() {
            var fixture = DiagramFixtures.instance();
            var header = fixture.header();
            var hostile = new PlanInstanceHeader(header.planId(), header.version(), header.event(),
                    header.composedAt(), header.inputs(), header.lane(), header.posture(), header.steps(),
                    List.of(new PrunedElement(PrunedElement.Kind.STEP, "default:\"quoted\"\nstep",
                            PruneReason.LOCAL_ONLY, "fact #1;\r\nline")),
                    List.of(), List.of(new WaivedGate("gate \"x\"\nnext", "W-1")));
            var hostileLabels = new PlanInstance(fixture.version(), hostile, fixture.unit(), fixture.text());

            var rendered = WorkflowDiagramGenerator.renderInstanceMermaid(hostileLabels, Optional.empty());

            assertAll(
                    () -> assertTrue(rendered.contains("state \"step default:#quot;quoted#quot; step: local_only "
                            + "(fact #35;1; line)\" as instance_pruned_1\n"), rendered),
                    () -> assertTrue(rendered.contains("state \"gate #quot;x#quot; next: waived W-1\" as "
                            + "instance_waived_1\n"), rendered),
                    () -> assertTrue(rendered.lines().filter(line -> line.contains(" as instance_"))
                                    .allMatch(line -> line.chars().filter(character -> character == '"').count() == 2),
                            rendered));
        }

        @Test
        @DisplayName("Should write the front matter title as a YAML double-quoted scalar, a colon kept, a backslash "
                + "and a double quote escaped")
        void shouldQuoteFrontMatterTitle() {
            var rendered = WorkflowDiagramGenerator.renderInstanceMermaid(withPlanId("a: \"b\" \\c"),
                    Optional.empty());

            assertEquals("---\ntitle: \"plan a: \\\"b\\\" \\\\c v3 (plan)\"\n---\n", frontMatter(rendered),
                    rendered);
        }

        @Test
        @DisplayName("Should quote a plain title without escaping anything in it")
        void shouldQuotePlainTitle() {
            var rendered = WorkflowDiagramGenerator.renderInstanceMermaid(withPlanId("plain-plan"), Optional.empty());

            assertEquals("---\ntitle: \"plan plain-plan v3 (plan)\"\n---\n", frontMatter(rendered), rendered);
        }

        private PlanInstance withPlanId(String planId) {
            var header = instance.header();
            var renamed = new PlanInstanceHeader(planId, header.version(), header.event(), header.composedAt(),
                    header.inputs(), header.lane(), header.posture(), header.steps(), header.pruned(),
                    header.policyCalls(), header.waivedGates());
            return new PlanInstance(instance.version(), renamed, instance.unit(), instance.text());
        }

        private static String frontMatter(String rendered) {
            return rendered.substring(0, rendered.indexOf("stateDiagram-v2"));
        }

        @Test
        @DisplayName("Should keep the AsciiDoc tables well-formed for a cell with a pipe, a line feed or a CRLF")
        void shouldEscapeAsciiDocCellText() {
            var rendered = WorkflowDiagramGenerator.renderInstanceAsciiDocTables(hostileInstance(),
                    Optional.empty());
            var expectedDelimiters = WorkflowDiagramGenerator.renderInstanceAsciiDocTables(instance, Optional.empty())
                    .lines().filter("|==="::equals).count();

            assertAll(
                    () -> assertEquals(expectedDelimiters, rendered.lines().filter("|==="::equals).count(), rendered),
                    () -> assertTrue(rendered.contains("|`default:a\\|b c`\n|`local_only`\n|`fact \\| next line`\n"),
                            rendered),
                    () -> assertTrue(rendered.contains("|`x \\|===`\n|`W\\|1`\n"), rendered),
                    () -> assertFalse(rendered.contains("\r"), rendered));
        }

        @Test
        @DisplayName("Should keep the box of the terminal tables for a cell with a pipe, a line feed or a CRLF")
        void shouldKeepTerminalBoxForCellText() {
            var rendered = WorkflowDiagramGenerator.renderInstanceAscii(hostileInstance(), Optional.empty());

            var misaligned = Stream.of(rendered.split("\n\n")).filter(block -> block.lines()
                    .mapToInt(line -> line.codePointCount(0, line.length())).distinct().count() != 1).toList();

            assertAll(() -> assertEquals(List.of(), misaligned, rendered),
                    () -> assertTrue(rendered.contains("│ step    │ default:a|b c │ local_only │ fact | next line │\n"),
                            rendered),
                    () -> assertTrue(rendered.contains("│ x |=== │ W|1       │\n"), rendered),
                    () -> assertFalse(rendered.contains("\r"), rendered));
        }

        @Test
        @DisplayName("Should replace a C1 control character in a text of the header in every form")
        void shouldReplaceC1ControlCharacters() {
            var header = instance.header();
            var hostile = new PlanInstanceHeader(header.planId(), header.version(), header.event(),
                    header.composedAt(), header.inputs(), header.lane(), header.posture(), header.steps(),
                    List.of(new PrunedElement(PrunedElement.Kind.STEP, "default:x", PruneReason.LOCAL_ONLY,
                            "fact\u009B[31m\u0080end")),
                    List.of(), List.of());
            var c1Instance = new PlanInstance(instance.version(), hostile, instance.unit(), instance.text());

            var terminal = WorkflowDiagramGenerator.renderInstanceAscii(c1Instance, Optional.empty());
            var asciiDoc = WorkflowDiagramGenerator.renderInstanceAsciiDocTables(c1Instance, Optional.empty());
            var mermaid = WorkflowDiagramGenerator.renderInstanceMermaid(c1Instance, Optional.empty());

            assertAll(
                    () -> assertTrue(terminal.contains("│ fact [31m end │"), terminal),
                    () -> assertTrue(asciiDoc.contains("|`fact [31m end`\n"), asciiDoc),
                    () -> assertTrue(mermaid.contains("(fact [31m end)"), mermaid),
                    () -> assertTrue(Stream.of(terminal, asciiDoc, mermaid)
                            .noneMatch(form -> form.chars().anyMatch(character -> character >= 0x80
                                    && character <= 0x9F)), "no C1 control character may remain"));
        }

        private PlanInstance hostileInstance() {
            var header = instance.header();
            var hostile = new PlanInstanceHeader(header.planId(), header.version(), header.event(),
                    header.composedAt(), header.inputs(), header.lane(), header.posture(), header.steps(),
                    List.of(new PrunedElement(PrunedElement.Kind.STEP, "default:a|b\nc", PruneReason.LOCAL_ONLY,
                            "fact |\r\nnext\u2028line")),
                    List.of(), List.of(new WaivedGate("x\n|===", "W|1")));
            return new PlanInstance(instance.version(), hostile, instance.unit(), instance.text());
        }

        private static PlanInstance bareInstance() {
            var fixture = DiagramFixtures.instance();
            var header = fixture.header();
            var bare = new PlanInstanceHeader(header.planId(), header.version(), header.event(), header.composedAt(),
                    header.inputs(), Optional.empty(), Optional.empty(), header.steps(), List.of(), List.of(),
                    List.of());
            return new PlanInstance(fixture.version(), bare, fixture.unit(), fixture.text());
        }
    }

    static Stream<Arguments> instanceForms() {
        return Stream.of(
                Arguments.of("Mermaid", DiagramFixtures.INSTANCE_MERMAID_GOLDEN,
                        (InstanceForm) WorkflowDiagramGenerator::renderInstanceMermaid),
                Arguments.of("AsciiDoc", DiagramFixtures.INSTANCE_ASCIIDOC_GOLDEN,
                        (InstanceForm) WorkflowDiagramGenerator::renderInstanceAsciiDocTables),
                Arguments.of("terminal", DiagramFixtures.INSTANCE_ASCII_GOLDEN,
                        (InstanceForm) WorkflowDiagramGenerator::renderInstanceAscii));
    }

    /** One instance form of the generator. */
    @FunctionalInterface
    interface InstanceForm {

        String apply(PlanInstance instance, Optional<String> activeState);
    }

    @Nested
    @DisplayName("Link table")
    class LinkTable {

        @Test
        @DisplayName("Should print every parameter type and escape the cell separator")
        void shouldPrintEveryParameterType() {
            var unit = DiagramFixtures.parse("parameters.pmwf", """
                    ---
                    name: diagram.parameters
                    description: A unit written for the tests of pm-workflow.
                    type: system
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
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
