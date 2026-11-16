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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.workflow.scenario.ScenarioDirective.Action;
import de.planmarshall.workflow.scenario.ScenarioDirective.AnswerQuestion;
import de.planmarshall.workflow.scenario.ScenarioDirective.AssertOutcome;
import de.planmarshall.workflow.scenario.ScenarioDirective.AssertRefused;
import de.planmarshall.workflow.scenario.ScenarioDirective.AssertState;
import de.planmarshall.workflow.scenario.ScenarioDirective.AssertTaskOutcome;
import de.planmarshall.workflow.scenario.ScenarioDirective.AssertWorkerState;
import de.planmarshall.workflow.scenario.ScenarioDirective.Call;
import de.planmarshall.workflow.scenario.ScenarioDirective.CompleteJob;
import de.planmarshall.workflow.scenario.ScenarioDirective.OperatorDo;
import de.planmarshall.workflow.scenario.ScenarioDirective.Start;
import de.planmarshall.workflow.scenario.ScenarioDirective.Worker;
import de.planmarshall.workflow.scenario.ScenarioDirective.WorkerDo;
import de.planmarshall.workflow.scenario.ScenarioDirective.WorkerWait;
import de.planmarshall.workflow.task.TaskRefusal;

/**
 * The scenario script of {@code pm-scenario} (PM-TEST-4): the supported directives are read with their arguments and
 * lines, and every directive the harness specification names but the harness does not run is refused by name with its
 * line, never skipped, so a script cannot pass by leaving a step out.
 */
// Every datum in this class is a contract: the script documents under test and their exact refusal messages.
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
                Arguments.of("operator_do", "missing argument"),
                Arguments.of("operator_do proceed", "missing argument"),
                Arguments.of("operator_do proceed terminal", "'terminal' is no operator channel"),
                Arguments.of("operator_do proceed web {\"note\":{\"x\":1}}", "is no scalar"),
                Arguments.of("worker_wait", "missing argument"),
                Arguments.of("worker_wait reviewer extra", "surplus argument 'extra'"),
                Arguments.of("worker_do reviewer", "missing argument"),
                Arguments.of("worker_do reviewer ack extra", "surplus argument 'extra'"),
                Arguments.of("worker_do reviewer dance", "'dance' is no worker action"),
                Arguments.of("worker_do reviewer submit:answer {}", "missing argument: submit:answer"),
                Arguments.of("worker_do reviewer consult:architect", "missing argument: consult:architect"),
                Arguments.of("assert_worker_state reviewer", "missing argument"),
                Arguments.of("assert_worker_state reviewer open", "'open' is no state of a worker's task"),
                Arguments.of("assert_refused", "missing argument"),
                Arguments.of("assert_refused vanished", "'vanished' is no refusal of a task"),
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
                    operator_do choose cli {"note":"go"}
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
                            new OperatorDo(10, "choose", "cli", Map.of("note", "go")),
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
        @DisplayName("Should read a script with CRLF line endings exactly like the same script with LF")
        void shouldReadCrlfTwin() {
            var lf = """
                    scenario crlf-twin
                    worker reviewer submitted
                    start plan plan-a
                    assert_state execute.review
                    """;
            var crlf = lf.replace("\n", "\r\n");

            assertEquals(ScenarioScript.read(lf), ScenarioScript.read(crlf));
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
    @DisplayName("Protocol worker directives")
    class ProtocolWorkerDirectives {

        @Test
        @DisplayName("Should read worker_wait and worker_do as the protocol worker's wait and calls")
        void shouldReadTheProtocolWorkerDirectives() {
            var script = assertDoesNotThrow(() -> ScenarioScript.read("""
                    scenario protocol
                    worker_wait reviewer
                    worker_do ack {}
                    worker_do reviewer ack {}
                    worker_do reviewer submit:change {"consultation_ids":"qt-0000000000000002,qt-0000000000000003"}
                    worker_do reviewer infeasible
                    worker_do reviewer consult:architect {"question":"is it safe?"}
                    """));

            assertAll(
                    () -> assertEquals(List.of(
                            new WorkerWait(2, "reviewer"),
                            new WorkerDo(3, new Call(null, new Action.Ack(), Map.of())),
                            new WorkerDo(4, new Call("reviewer", new Action.Ack(), Map.of())),
                            new WorkerDo(5, new Call("reviewer", new Action.Submit("change"),
                                    Map.of("consultation_ids", "qt-0000000000000002,qt-0000000000000003"))),
                            new WorkerDo(6, new Call("reviewer", new Action.Infeasible(), Map.of())),
                            new WorkerDo(7, new Call("reviewer", new Action.Consult("architect"),
                                    Map.of("question", "is it safe?")))), script.directives()),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("worker_wait")),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("worker_do")));
        }

        @Test
        @DisplayName("Should read assert_worker_state and assert_refused as the supervision assertions")
        void shouldReadTheSupervisionAssertions() {
            var script = assertDoesNotThrow(() -> ScenarioScript.read("""
                    scenario supervision
                    worker_wait reviewer
                    worker_do ack {}
                    assert_worker_state reviewer acknowledged
                    worker_do reviewer submit:change {}
                    worker_do reviewer ack {}
                    assert_refused stale_entity
                    """));

            assertAll(
                    () -> assertEquals(List.of(
                            new WorkerWait(2, "reviewer"),
                            new WorkerDo(3, new Call(null, new Action.Ack(), Map.of())),
                            new AssertWorkerState(4, "reviewer", "acknowledged"),
                            new WorkerDo(5, new Call("reviewer", new Action.Submit("change"), Map.of())),
                            new WorkerDo(6, new Call("reviewer", new Action.Ack(), Map.of())),
                            new AssertRefused(7, "stale_entity")), script.directives()),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("assert_worker_state")),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("assert_refused")));
        }

        @Test
        @DisplayName("Should accept every refusal of TaskRefusal and no other reason")
        void shouldAcceptTheRefusalSetOfTheProtocol() {
            var reasons = Arrays.stream(TaskRefusal.values()).map(TaskRefusal::wireName).toList();

            assertAll(
                    () -> assertEquals(ScenarioDirective.REFUSALS, reasons),
                    () -> reasons.forEach(reason -> assertDoesNotThrow(() -> new AssertRefused(1, reason))),
                    () -> assertThrows(IllegalArgumentException.class, () -> new AssertRefused(1, "vanished")));
        }

        @Test
        @DisplayName("Should refuse a protocol call or assertion the script never waits for, when it is read")
        void shouldRefuseAProtocolStepWithoutItsWait() {
            var call = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-wait
                    worker_do reviewer ack {}
                    """));
            var roleless = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-wait-roleless
                    worker_do ack {}
                    """));
            var late = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario wait-later
                    worker_do reviewer ack {}
                    worker_wait reviewer
                    """));
            var state = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-wait-state
                    assert_worker_state reviewer offered
                    """));
            var refused = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-call
                    worker_wait reviewer
                    assert_refused stale_entity
                    """));
            var serviceCall = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario answer-then-refused
                    worker_wait reviewer
                    worker_do reviewer submit:answer {"answer":"yes"}
                    assert_refused stale_entity
                    """));
            var both = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario both-forms
                    worker reviewer submitted
                    worker_wait reviewer
                    """));

            assertAll(
                    () -> assertEquals("line 2: worker_do names the role reviewer, which the script never declares with "
                            + "worker_wait before it", call.getMessage()),
                    () -> assertEquals("line 2: worker_do names no role, and no worker_wait precedes it",
                            roleless.getMessage()),
                    () -> assertEquals("line 2: worker_do names the role reviewer, which the script never declares with "
                            + "worker_wait before it", late.getMessage()),
                    () -> assertEquals("line 2: assert_worker_state names the role reviewer, which the script never "
                            + "declares with worker_wait before it", state.getMessage()),
                    () -> assertEquals("line 3: assert_refused asserts the last worker_do, but no worker_do precedes it",
                            refused.getMessage()),
                    () -> assertEquals("line 4: assert_refused asserts the last worker_do's refusal, but that call "
                            + "makes no call of the task protocol", serviceCall.getMessage()),
                    () -> assertEquals("line 3: the worker of role reviewer is declared with worker and waits with "
                            + "worker_wait; a role uses one form", both.getMessage()));
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
                    () -> assertEquals("plan-instance", ScenarioScript.REFUSED.get("assert_instance_version")),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("worker_wait")),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("worker_do")),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("assert_worker_state")),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("assert_refused")));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"expire_question Q-001", "expire_question", "finish_plan", "no_such_directive"})
        @DisplayName("Should refuse a directive this harness does not run, expire_question included, by name and line")
        void shouldRefuseUnknownDirective(String line) {
            var directive = line.split(" ")[0];
            var text = thirdLine(line);

            var refused = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read(text));

            assertAll(
                    () -> assertTrue(refused.getMessage().startsWith("line 3: "), refused.getMessage()),
                    () -> assertTrue(refused.getMessage().endsWith("'%s' is no directive".formatted(directive)),
                            refused.getMessage()));
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
                            () -> new AnswerQuestion(1, "Q-001", " ", "approve")));
        }
    }
}
