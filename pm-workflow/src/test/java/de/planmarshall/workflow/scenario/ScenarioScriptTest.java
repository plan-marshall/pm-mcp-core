/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.scenario;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.workflow.scenario.ScenarioDirective.AnswerQuestion;
import de.planmarshall.workflow.scenario.ScenarioDirective.AssertOutcome;
import de.planmarshall.workflow.scenario.ScenarioDirective.AssertState;
import de.planmarshall.workflow.scenario.ScenarioDirective.AssertTaskOutcome;
import de.planmarshall.workflow.scenario.ScenarioDirective.CompleteJob;
import de.planmarshall.workflow.scenario.ScenarioDirective.ExpireQuestion;
import de.planmarshall.workflow.scenario.ScenarioDirective.Start;
import de.planmarshall.workflow.scenario.ScenarioDirective.Worker;

/**
 * The scenario script of {@code pm-scenario} (PM-TEST-4): the supported directives are read with their arguments and
 * lines, and every directive the harness specification names but the harness does not run is refused by name with its
 * line, never skipped, so a script cannot pass by leaving a step out.
 */
@DisplayName("Scenario script")
class ScenarioScriptTest {

    /** @return a script whose third line is the given one */
    private static String thirdLine(String line) {
        return """
                scenario refusal
                start plan plan-a
                %s
                assert_state completed
                """.formatted(line);
    }

    static Stream<String> refusedDirectives() {
        return ScenarioScript.REFUSED.keySet().stream().sorted();
    }

    static Stream<Arguments> malformedLines() {
        return Stream.of(
                Arguments.of("assert_state", "missing argument"),
                Arguments.of("assert_state execute.review completed", "surplus argument 'completed'"),
                Arguments.of("assert_outcome", "missing argument"),
                Arguments.of("assert_task_outcome reviewer", "missing argument"),
                Arguments.of("assert_task_outcome reviewer done", "'done' is no outcome of a task"),
                Arguments.of("expire_question Q-1", "question id does not match"),
                Arguments.of("complete_job x-0000000000000001 {\"outcome\":\"succeeded\"}", "job id does not match"),
                Arguments.of("complete_job b-0000000000000001 {\"outcome\":\"vanished\"}", "'vanished' is no fate"),
                Arguments.of("complete_job b-0000000000000001", "missing argument"),
                Arguments.of("complete_job b-0000000000000001 {\"fate\":\"succeeded\"}", "the JSON member 'outcome'"),
                Arguments.of("complete_job b-0000000000000001 {\"outcome\":", "the JSON argument is malformed"),
                Arguments.of("complete_job b-0000000000000001 {\"outcome\":{\"x\":1}}", "is no scalar"),
                Arguments.of("complete_job b-0000000000000001 {} {}", "not one object"),
                Arguments.of("answer_question Q-001 {\"branch_key\":\"approve\"}", "missing argument"),
                Arguments.of("answer_question Q-1 web {\"branch_key\":\"approve\"}", "question id does not match"),
                Arguments.of("assert_state completed {\"x\":\"y\"}", "takes no JSON argument"),
                Arguments.of("start", "missing argument"),
                Arguments.of("start request \"Add a parser\" slug parser", "'request' is no scope"),
                Arguments.of("start lessons", "'lessons' is no scope"),
                Arguments.of("start epic", "missing argument"),
                Arguments.of("start workspace w-1", "surplus argument 'w-1'"),
                Arguments.of("worker reviewer", "missing argument"),
                Arguments.of("worker reviewer submitted finished", "'finished' is no outcome of a task"),
                Arguments.of("{\"outcome\":\"succeeded\"}", "a line starts with a directive"),
                Arguments.of("scenario other", "a script names one scenario"),
                Arguments.of("finish_plan", "'finish_plan' is no directive"));
    }

    @Nested
    @DisplayName("Supported directives")
    class Supported {

        @Test
        @DisplayName("Should read every supported directive with its arguments and its line")
        void shouldReadSupportedDirectives() {
            var script = ScenarioScript.read("""
                    scenario every-directive   # the name
                    worker reviewer submitted lost

                    # a comment line
                    start plan plan-a
                    assert_state execute.review
                    complete_job b-00000000000000a1 {"outcome":"succeeded","worktree_sha":"e3b0c442"}
                    assert_task_outcome reviewer submitted
                    answer_question Q-003 web {"branch_key":"approve"}   # the review question
                    expire_question Q-004
                    assert_state completed
                    assert_outcome completed
                    """);

            assertAll(
                    () -> assertEquals("every-directive", script.name()),
                    () -> assertEquals(List.of(new Worker(2, "reviewer", List.of("submitted", "lost"))),
                            script.workers()),
                    () -> assertEquals(List.of(
                            new Start(5, JournalScopeType.PLAN, "plan-a"),
                            new AssertState(6, "execute.review"),
                            new CompleteJob(7, "b-00000000000000a1", "succeeded"),
                            new AssertTaskOutcome(8, "reviewer", "submitted"),
                            new AnswerQuestion(9, "Q-003", "web", "approve"),
                            new ExpireQuestion(10, "Q-004"),
                            new AssertState(11, "completed"),
                            new AssertOutcome(12, "completed")), script.directives()));
        }

        @Test
        @DisplayName("Should start a workspace without a scope id and an epic with one")
        void shouldReadScopesOfStart() {
            var workspace = ScenarioScript.read("""
                    scenario workspace
                    start workspace
                    """);
            var epic = ScenarioScript.read("""
                    scenario epic
                    start epic epic-a
                    """);

            assertAll(
                    () -> assertEquals(List.of(new Start(2, JournalScopeType.WORKSPACE, null)),
                            workspace.directives()),
                    () -> assertEquals(List.of(new Start(2, JournalScopeType.EPIC, "epic-a")), epic.directives()));
        }

        @Test
        @DisplayName("Should keep a hash inside a quoted string and drop it outside")
        void shouldKeepHashInQuotedString() {
            var script = ScenarioScript.read("""
                    scenario hash
                    answer_question Q-001 web {"branch_key":"appr\\"ove#1"}  # comment
                    """);

            assertEquals(new AnswerQuestion(2, "Q-001", "web", "appr\"ove#1"),
                    script.directives().getFirst());
        }

        @Test
        @DisplayName("Should find the worker declaration of a role, and none for an undeclared role")
        void shouldFindWorkerOfRole() {
            var script = ScenarioScript.read("""
                    scenario workers
                    worker reviewer submitted
                    worker author infeasible asked
                    start plan plan-a
                    """);

            assertAll(
                    () -> assertEquals(Optional.of(new Worker(3, "author", List.of("infeasible", "asked"))),
                            script.worker("author")),
                    () -> assertEquals(Optional.empty(), script.worker("planner")));
        }
    }

    @Nested
    @DisplayName("Refused directives")
    class Refused {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.scenario.ScenarioScriptTest#refusedDirectives")
        @DisplayName("Should refuse a specified directive the harness does not run, by name and line")
        void shouldRefuseUnsupportedDirective(String directive) {
            var text = thirdLine(directive + " reviewer");
            var expected = "line 3: '%s' is not supported by this harness yet".formatted(directive);

            var refused = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read(text));

            assertAll(
                    () -> assertTrue(refused.getMessage().startsWith(expected), refused.getMessage()),
                    () -> assertTrue(refused.getMessage().endsWith(ScenarioScript.REFUSED.get(directive)),
                            refused.getMessage()));
        }

        @Test
        @DisplayName("Should name the work package that brings each refused directive")
        void shouldNamePackageOfRefusedDirectives() {
            assertAll(
                    () -> assertTrue(ScenarioScript.REFUSED.values().stream().noneMatch(String::isBlank),
                            ScenarioScript.REFUSED.toString()),
                    () -> assertEquals("supervision-scenarios", ScenarioScript.REFUSED.get("kill_worker")),
                    () -> assertEquals("task-queue-and-protocol", ScenarioScript.REFUSED.get("worker_wait")),
                    () -> assertEquals("plan-instance", ScenarioScript.REFUSED.get("assert_instance_version")));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.scenario.ScenarioScriptTest#malformedLines")
        @DisplayName("Should refuse a malformed line with a diagnostic naming the line")
        void shouldRefuseMalformedLine(String line, String reason) {
            var text = thirdLine(line);

            var refused = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read(text));

            assertAll(
                    () -> assertTrue(refused.getMessage().startsWith("line 3: "), refused.getMessage()),
                    () -> assertTrue(refused.getMessage().contains(reason), refused.getMessage()));
        }
    }

    @Nested
    @DisplayName("Script frame")
    class Frame {

        @Test
        @DisplayName("Should refuse a script that does not start with scenario <name>")
        void shouldRefuseMissingScenarioLine() {
            var noScenario = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    # only a comment
                    start plan plan-a
                    """));
            var empty = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("# nothing\n"));

            assertAll(
                    () -> assertEquals("line 2: a script starts with scenario <name>, not with 'start'",
                            noScenario.getMessage()),
                    () -> assertTrue(empty.getMessage().contains("names no scenario"), empty.getMessage()));
        }

        @Test
        @DisplayName("Should refuse a malformed scenario name, a duplicate worker and a script without steps")
        void shouldRefuseMalformedFrame() {
            var badName = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario Happy_Path
                    start plan plan-a
                    """));
            var twice = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario twice
                    worker reviewer submitted
                    worker reviewer lost
                    start plan plan-a
                    """));
            var noStep = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario idle
                    worker reviewer submitted
                    """));

            assertAll(
                    () -> assertTrue(badName.getMessage().startsWith("line 1: the scenario name does not match"),
                            badName.getMessage()),
                    () -> assertEquals("line 3: the worker of role reviewer is declared twice", twice.getMessage()),
                    () -> assertEquals("the scenario idle names no step", noStep.getMessage()));
        }

        @Test
        @DisplayName("Should refuse directive values that break the closed sets of the engine events")
        void shouldRefuseInvalidDirectiveValues() {
            var noOutcomes = List.<String>of();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new AssertState(0, "completed")),
                    () -> assertThrows(IllegalArgumentException.class, () -> new AssertOutcome(1, "two words")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Start(1, JournalScopeType.PLAN, null)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Start(1, JournalScopeType.WORKSPACE, "w-1")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Worker(1, "reviewer", noOutcomes)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new AnswerQuestion(1, "Q-001", " ", "approve")),
                    () -> assertThrows(NullPointerException.class, () -> new ExpireQuestion(1, null)));
        }
    }
}
