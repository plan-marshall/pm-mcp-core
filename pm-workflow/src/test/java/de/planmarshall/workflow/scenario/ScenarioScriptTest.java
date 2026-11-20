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
import de.planmarshall.workflow.scenario.ScenarioDirective.EndTurn;
import de.planmarshall.workflow.scenario.ScenarioDirective.KillRelay;
import de.planmarshall.workflow.scenario.ScenarioDirective.KillWorker;
import de.planmarshall.workflow.scenario.ScenarioDirective.OperatorDo;
import de.planmarshall.workflow.scenario.ScenarioDirective.RestartRuntime;
import de.planmarshall.workflow.scenario.ScenarioDirective.StallWorker;
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
                Arguments.of("kill_worker", "missing argument"),
                Arguments.of("kill_worker reviewer extra", "surplus argument 'extra'"),
                Arguments.of("kill_worker Reviewer", "role is no role name"),
                Arguments.of("end_turn", "missing argument"),
                Arguments.of("kill_relay Reviewer", "role is no role name"),
                Arguments.of("stall_worker reviewer", "missing argument"),
                Arguments.of("stall_worker reviewer soon", "'soon' is no number of seconds"),
                Arguments.of("stall_worker reviewer 0", "a stall is counted from 1 second"),
                Arguments.of("stall_worker reviewer 30 extra", "surplus argument 'extra'"),
                Arguments.of("restart_runtime now", "surplus argument 'now'"),
                Arguments.of("restart_runtime {}", "takes no JSON argument"),
                Arguments.of("worker_do", "missing argument"),
                Arguments.of("worker_do reviewer", "'reviewer' is no worker action"),
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
                    () -> assertEquals("every-directive", script.name(),
                            "the script keeps the name of its scenario"),
                    () -> assertEquals(List.of(new Worker(2, "reviewer", List.of("submitted", "lost"))),
                            script.workers(), "the worker directive reads with its line"),
                    () -> assertEquals(List.of(
                                    new Start(5, JournalScopeType.PLAN, "plan-a"),
                                    new AssertState(6, "execute.review"),
                                    new CompleteJob(7, "b-00000000000000a1", "succeeded"),
                                    new AssertTaskOutcome(8, "reviewer", "submitted"),
                                    new AnswerQuestion(9, "Q-003", "web", "approve"),
                                    new OperatorDo(10, "choose", "cli", Map.of("note", "go")),
                                    new AssertState(11, "completed"),
                                    new AssertOutcome(12, "completed")), script.directives(),
                            "every supported directive reads with its line"));
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
                            workspace.directives(), "a workspace start carries no scope id"),
                    () -> assertEquals(List.of(new Start(2, JournalScopeType.EPIC, "epic-a")), epic.directives(),
                            "an epic start names its epic scope"));
        }

        @Test
        @DisplayName("Should keep a hash inside a quoted string and drop it outside")
        void shouldKeepHashInQuotedString() {
            var script = ScenarioScript.read("""
                    scenario hash
                    answer_question Q-001 web {"branch_key":"appr\\"ove#1"}  # comment
                    """);

            assertEquals(new AnswerQuestion(2, "Q-001", "web", "appr\"ove#1"),
                    script.directives().getFirst(), "the quoted hash stays inside the branch key");
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

            assertEquals(ScenarioScript.read(lf), ScenarioScript.read(crlf),
                    "the CRLF twin reads like the LF script");
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
                            script.worker("author"), "the worker of role author reads with its line"),
                    () -> assertEquals(Optional.empty(), script.worker("planner"),
                            "no worker is found for a role not declared"));
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
                    """), "the protocol worker script reads without a refusal");

            assertAll(
                    () -> assertEquals(List.of(
                                    new WorkerWait(2, "reviewer"),
                                    new WorkerDo(3, new Call(null, new Action.Ack(), Map.of())),
                                    new WorkerDo(4, new Call("reviewer", new Action.Ack(), Map.of())),
                                    new WorkerDo(5, new Call("reviewer", new Action.Submit("change"),
                                            Map.of("consultation_ids", "qt-0000000000000002,qt-0000000000000003"))),
                                    new WorkerDo(6, new Call("reviewer", new Action.Infeasible(), Map.of())),
                                    new WorkerDo(7, new Call("reviewer", new Action.Consult("architect"),
                                            Map.of("question", "is it safe?")))), script.directives(),
                            "both worker_do forms read their lines"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("worker_wait"),
                            "worker_wait is no longer refused"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("worker_do"),
                            "worker_do is no longer refused"));
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
                    """), "the supervision script reads without a refusal");

            assertAll(
                    () -> assertEquals(List.of(
                                    new WorkerWait(2, "reviewer"),
                                    new WorkerDo(3, new Call(null, new Action.Ack(), Map.of())),
                                    new AssertWorkerState(4, "reviewer", "acknowledged"),
                                    new WorkerDo(5, new Call("reviewer", new Action.Submit("change"), Map.of())),
                                    new WorkerDo(6, new Call("reviewer", new Action.Ack(), Map.of())),
                                    new AssertRefused(7, "stale_entity")), script.directives(),
                            "the assertions read with their lines"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("assert_worker_state"),
                            "assert_worker_state is no longer refused"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("assert_refused"),
                            "assert_refused is no longer refused"));
        }

        @Test
        @DisplayName("Should read the fault directives and the runtime restart with their roles and times")
        void shouldReadTheFaultDirectives() {
            var script = assertDoesNotThrow(() -> ScenarioScript.read("""
                    scenario faults
                    worker_wait reviewer
                    worker_do ack {}
                    kill_worker reviewer
                    end_turn reviewer
                    kill_relay reviewer
                    stall_worker reviewer 30
                    restart_runtime
                    """), "the fault directives read without a refusal");

            assertAll(
                    () -> assertEquals(List.of(
                            new WorkerWait(2, "reviewer"),
                            new WorkerDo(3, new Call(null, new Action.Ack(), Map.of())),
                            new KillWorker(4, "reviewer"),
                            new EndTurn(5, "reviewer"),
                            new KillRelay(6, "reviewer"),
                            new StallWorker(7, "reviewer", 30),
                            new RestartRuntime(8)), script.directives(), "the faults read with their lines"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("kill_worker"),
                            "kill_worker is no longer refused"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("stall_worker"),
                            "stall_worker is no longer refused"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("end_turn"), "end_turn is no longer refused"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("kill_relay"), "kill_relay is no longer refused"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("restart_runtime"),
                            "restart_runtime is no longer refused"));
        }

        @Test
        @DisplayName("Should refuse a fault of a role the script never waits for, when it is read")
        void shouldRefuseAFaultWithoutItsWait() {
            var kill = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-wait-fault
                    kill_worker reviewer
                    """), "a fault without its wait is refused");
            var stall = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-wait-stall
                    stall_worker reviewer 30
                    worker_wait reviewer
                    """), "a later wait does not redeem the fault");

            assertAll(
                    () -> assertEquals("line 2: kill_worker names the role reviewer, which the script never declares "
                            + "with worker_wait before it", kill.getMessage(), "the fault needs a wait before it"),
                    () -> assertEquals("line 2: stall_worker names the role reviewer, which the script never declares "
                            + "with worker_wait before it", stall.getMessage(), "a later wait does not count"));
        }

        @Test
        @DisplayName("Should accept every refusal of TaskRefusal and no other reason")
        void shouldAcceptTheRefusalSetOfTheProtocol() {
            var reasons = Arrays.stream(TaskRefusal.values()).map(TaskRefusal::wireName).toList();

            assertAll(
                    () -> assertEquals(ScenarioDirective.REFUSALS, reasons,
                            "the refusal set is derived from the enum"),
                    () -> reasons.forEach(reason -> assertDoesNotThrow(() -> new AssertRefused(1, reason),
                            "every refusal of the enum is accepted")),
                    () -> assertThrows(IllegalArgumentException.class, () -> new AssertRefused(1, "vanished"),
                            "a word outside the enum is refused"));
        }

        @Test
        @DisplayName("Should refuse a protocol call or assertion the script never waits for, when it is read")
        void shouldRefuseAProtocolStepWithoutItsWait() {
            var call = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-wait
                    worker_do reviewer ack {}
                    """), "a call before its wait is refused");
            var roleless = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-wait-roleless
                    worker_do ack {}
                    """), "a role-less call before any wait is refused");
            var late = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario wait-later
                    worker_do reviewer ack {}
                    worker_wait reviewer
                    """), "a call before a later wait is refused");
            var state = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-wait-state
                    assert_worker_state reviewer offered
                    """), "an assertion before its wait is refused");
            var refused = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario no-call
                    worker_wait reviewer
                    assert_refused stale_entity
                    """), "assert_refused without a call is refused");
            var serviceCall = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario answer-then-refused
                    worker_wait reviewer
                    worker_do reviewer submit:answer {"answer":"yes"}
                    assert_refused stale_entity
                    """), "assert_refused after an answer is refused");
            var both = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario both-forms
                    worker reviewer submitted
                    worker_wait reviewer
                    """), "a role declared in both forms is refused");
            var apart = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario refusal-then-assertion
                    worker_wait reviewer
                    worker_do reviewer submit:change {}
                    assert_state execute.review
                    assert_refused link_not_available
                    """), "an intervening assertion breaks the adjacency");

            assertAll(
                    () -> assertEquals("line 2: worker_do names the role reviewer, which the script never declares with "
                            + "worker_wait before it", call.getMessage(), "the call names its role too early"),
                    () -> assertEquals("line 2: worker_do names no role, and no worker_wait precedes it",
                            roleless.getMessage(), "the role-less call needs a wait before it"),
                    () -> assertEquals("line 2: worker_do names the role reviewer, which the script never declares with "
                            + "worker_wait before it", late.getMessage(), "a later wait does not count"),
                    () -> assertEquals("line 2: assert_worker_state names the role reviewer, which the script never "
                            + "declares with worker_wait before it", state.getMessage(),
                            "the assertion needs a wait before it"),
                    () -> assertEquals("line 3: assert_refused must directly follow the worker_do whose refusal it "
                            + "asserts", refused.getMessage(), "an assertion needs a call before it"),
                    () -> assertEquals("line 4: assert_refused asserts the last worker_do's refusal, but that call "
                            + "makes no call of the task protocol", serviceCall.getMessage(),
                            "an answer is no protocol call"),
                    () -> assertEquals("line 5: assert_refused must directly follow the worker_do whose refusal it "
                            + "asserts", apart.getMessage(), "an intervening assertion breaks adjacency"),
                    () -> assertEquals("line 3: the worker of role reviewer is declared with worker and waits with "
                            + "worker_wait; a role uses one form", both.getMessage(), "a role uses one form"));
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

            var refused = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read(text),
                    "a directive the harness does not run is refused");

            assertAll(
                    () -> assertTrue(refused.getMessage().startsWith(expected),
                            "the refusal names the line and the directive"),
                    () -> assertTrue(refused.getMessage().endsWith(ScenarioScript.REFUSED.get(directive)),
                            "the refusal ends with the bringing package"));
        }

        @Test
        @DisplayName("Should name the work package that brings each refused directive")
        void shouldNamePackageOfRefusedDirectives() {
            assertAll(
                    () -> assertTrue(ScenarioScript.REFUSED.values().stream().noneMatch(String::isBlank),
                            "every refused directive names a package"),
                    () -> assertEquals("plan-instance", ScenarioScript.REFUSED.get("assert_instance_version"),
                            "the refused directive names its work package"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("worker_wait"),
                            "worker_wait left the refused set"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("worker_do"),
                            "worker_do left the refused set"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("assert_worker_state"),
                            "assert_worker_state left the refused set"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("assert_refused"),
                            "assert_refused left the refused set"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("kill_worker"),
                            "kill_worker left the refused set"),
                    () -> assertFalse(ScenarioScript.REFUSED.containsKey("restart_runtime"),
                            "restart_runtime left the refused set"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"expire_question Q-001", "expire_question", "finish_plan", "no_such_directive"})
        @DisplayName("Should refuse a directive this harness does not run, expire_question included, by name and line")
        void shouldRefuseUnknownDirective(String line) {
            var directive = line.split(" ")[0];
            var text = thirdLine(line);

            var refused = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read(text),
                    "an unknown directive is refused by name");

            assertAll(
                    () -> assertTrue(refused.getMessage().startsWith("line 3: "),
                            "the refusal names line 3"),
                    () -> assertTrue(refused.getMessage().endsWith("'%s' is no directive".formatted(directive)),
                            "the refusal names the unknown directive"));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.scenario.ScenarioScriptTest#malformedLines")
        @DisplayName("Should refuse a malformed line with a diagnostic naming the line")
        void shouldRefuseMalformedLine(String line, String reason) {
            var text = thirdLine(line);

            var refused = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read(text),
                    "a malformed line is refused with its reason");

            assertAll(
                    () -> assertTrue(refused.getMessage().startsWith("line 3: "),
                            "the diagnostic names line 3"),
                    () -> assertTrue(refused.getMessage().contains(reason),
                            "the diagnostic states the case's reason"));
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
                    """), "a script without its scenario line is refused");
            var empty = assertThrows(IllegalArgumentException.class,
                    () -> ScenarioScript.read("# nothing\n"), "an empty script is refused for its missing name");

            assertAll(
                    () -> assertEquals("line 2: a script starts with scenario <name>, not with 'start'",
                            noScenario.getMessage(), "the refusal names the missing scenario line"),
                    () -> assertTrue(empty.getMessage().contains("names no scenario"),
                            "the empty script names no scenario"));
        }

        @Test
        @DisplayName("Should refuse a malformed scenario name, a duplicate worker and a script without steps")
        void shouldRefuseMalformedFrame() {
            var badName = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario Happy_Path
                    start plan plan-a
                    """), "a scenario name outside the pattern is refused");
            var twice = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario twice
                    worker reviewer submitted
                    worker reviewer lost
                    start plan plan-a
                    """), "a worker role declared twice is refused");
            var noStep = assertThrows(IllegalArgumentException.class, () -> ScenarioScript.read("""
                    scenario idle
                    worker reviewer submitted
                    """), "a scenario without a step is refused");

            assertAll(
                    () -> assertTrue(badName.getMessage().startsWith("line 1: the scenario name does not match"),
                            "the refusal says the name does not match"),
                    () -> assertEquals("line 3: the worker of role reviewer is declared twice", twice.getMessage(),
                            "the refusal names the twice-declared role"),
                    () -> assertEquals("the scenario idle names no step", noStep.getMessage(),
                            "the refusal names the missing step"));
        }

        @Test
        @DisplayName("Should refuse directive values that break the closed sets of the engine events")
        void shouldRefuseInvalidDirectiveValues() {
            var noOutcomes = List.<String>of();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new AssertState(0, "completed"),
                            "a non-positive source line is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new AssertOutcome(1, "two words"),
                            "a two-word outcome is refused by the directive"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Start(1, JournalScopeType.PLAN, null),
                            "a plan start without a scope id is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Start(1, JournalScopeType.WORKSPACE, "w-1"),
                            "a workspace start with a scope id is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new Worker(1, "reviewer", noOutcomes),
                            "a worker without task outcomes is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new AnswerQuestion(1, "Q-001", " ", "approve"),
                            "an answer with a blank channel is refused"));
        }
    }
}
