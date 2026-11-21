/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.engine;

import static de.planmarshall.workflow.engine.EngineFixtures.EXECUTE;
import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.SUB_COMMIT;
import static de.planmarshall.workflow.engine.EngineFixtures.T0;
import static de.planmarshall.workflow.engine.EngineFixtures.callingPlan;
import static de.planmarshall.workflow.engine.EngineFixtures.frame;
import static de.planmarshall.workflow.engine.EngineFixtures.parse;
import static de.planmarshall.workflow.engine.EngineFixtures.plan;
import static de.planmarshall.workflow.engine.EngineFixtures.planStatus;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CommandSettlement;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.RetryCounters.RetryRule;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.testing.generators.CoreGenerators;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.engine.EngineFixtures.CapOutcome;
import de.planmarshall.workflow.engine.EngineFixtures.StepOutcome;
import de.planmarshall.workflow.verify.VerificationRule;
import de.planmarshall.workflow.verify.WorkflowCatalog;

/**
 * The interpreter (PM-IMPL-5, PM-WF-1, PM-WF-16, PM-ARCH-4): fixture units run to every kind of end, auto states chain
 * in one advance, an unverified unit is refused, the engine keeps the retry counts of a frame while its states are
 * entered and left and drops them when the frame is popped, and every outcome of a verified unit takes exactly the one
 * transition the unit declares for it.
 */
@EnableGeneratorController
@DisplayName("WorkflowEngine")
class WorkflowEngineTest {

    private static final String CHAIN = """
            state execute.first
                auto test.step
                    done -> execute.second
                    failed -> handed_back

            state execute.second
                auto test.mark
                    done -> completed
                    failed -> handed_back
            """;

    private static final String CHAIN_TO_JOB = """
            state execute.first
                auto test.step
                    done -> execute.second
                    failed -> execute.build

            state execute.second
                auto test.mark
                    done -> execute.build
                    failed -> execute.build

            state execute.build
                job test.build
                    succeeded -> completed
                    failed -> handed_back
                    cancelled -> handed_back
            """;

    /** A call whose step-out enters the plan's fallback state, the one target Rule 6 admits in a plan unit. */
    private static final String CALL_COMMIT = """
            state execute.commit
                call sub.commit
                    done -> completed
                    stepped_out -> plan.return
            """;

    private final EngineFixtures fixtures = new EngineFixtures();

    @Nested
    @DisplayName("Ends")
    class Ends {

        @Test
        @DisplayName("Should end a top-level unit in its success terminal after chaining both auto states")
        void shouldEndInSuccessTerminal() {
            var engine = fixtures.engine(plan("execute.first", CHAIN));

            var ended = assertInstanceOf(EngineStep.Ended.class, engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "the chain ends in the success terminal");

            assertAll(
                    () -> assertEquals("merged", ended.outcomeId(), "the chain ends in the merged outcome"),
                    () -> assertEquals(OutcomeClass.SUCCESS, ended.outcomeClass(), "the end class is success"),
                    () -> assertEquals("completed", ended.status().state(), "the end state is completed"),
                    () -> assertEquals("completed", ended.status().phase(), "the end phase is completed"),
                    () -> assertEquals(List.of(Optional.of(PLAN_ID)), fixtures.step.scopes(),
                            "the plan scope is the only recorded one"),
                    () -> assertEquals(1, fixtures.mark.calls(), "the mark primitive ran once"));
        }

        @Test
        @DisplayName("Should end in the failure terminal the failed outcome routes to, without running the next state")
        void shouldEndInFailureTerminal() {
            fixtures.step.then(StepOutcome.FAILED);
            var engine = fixtures.engine(plan("execute.first", CHAIN));

            var ended = assertInstanceOf(EngineStep.Ended.class, engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "the failure ends in the handed_back terminal");

            assertAll(
                    () -> assertEquals("handed_back", ended.outcomeId(), "the failure reports handed_back"),
                    () -> assertEquals(OutcomeClass.FAILURE, ended.outcomeClass(), "the end class is failure"),
                    () -> assertEquals(0, fixtures.mark.calls(), "the next auto state never ran"));
        }

        @Test
        @DisplayName("Should end an epic in the abort terminal its one declared transition enters")
        void shouldEndInAbortTerminal() {
            var engine = fixtures.engine(parse("epic.pmwf", """
                    ---
                    name: epic
                    description: A unit written for the tests of pm-workflow.
                    type: workflow
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
                    workflow epic version 1 format 1
                    scope epic
                    start epic.next-work

                    state epic.next-work
                        auto test.next-work
                            abort_landed -> epic.aborted

                    state epic.aborted terminal aborted failure
                    """));

            var ended = assertInstanceOf(EngineStep.Ended.class,
                    engine.start(JournalScopeType.EPIC,
                            "scope-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT)),
                    "the epic ends in its abort terminal");

            assertAll(
                    () -> assertEquals("aborted", ended.outcomeId(), "the epic ends in the aborted outcome"),
                    () -> assertEquals(OutcomeClass.FAILURE, ended.outcomeClass(), "the abort class is failure"),
                    () -> assertEquals("aborted", ended.status().phase(), "the abort phase is aborted"),
                    () -> assertEquals(1, fixtures.nextWork.calls(), "the next-work primitive ran once"));
        }

        @ParameterizedTest(name = "terminal {0} ({1})")
        @MethodSource("de.planmarshall.workflow.engine.WorkflowEngineTest#deliveryTerminals")
        @DisplayName("Should end a plan in each delivery terminal (contract ids completed, handed_back, returned) with "
                + "phase completed")
        void shouldEndDeliveryTerminalWithPhaseCompleted(String outcomeId, OutcomeClass outcomeClass) {
            var engine = fixtures.engine(endingPlan("plan.end", outcomeId, outcomeClass, ""));

            var ended = assertInstanceOf(EngineStep.Ended.class, engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "the delivery terminal ends the plan");

            assertAll(
                    () -> assertEquals(outcomeId, ended.outcomeId(), "the end reports the terminal's outcome"),
                    () -> assertEquals(outcomeClass, ended.outcomeClass(), "the end reports the outcome class"),
                    () -> assertEquals("completed", ended.status().phase(), "a delivery terminal takes completed"),
                    () -> assertTrue(ended.status().callStack().isEmpty(), "no call stack is left at the end"));
        }

        @Test
        @DisplayName("Should end a plan the engine set at plan.aborted (contract outcome id aborted) with phase aborted")
        void shouldEndPlanAbortWithPhaseAborted() {
            var engine = fixtures.engine(endingPlan("completed", "completed", OutcomeClass.SUCCESS,
                    "\nstate plan.aborted terminal aborted failure\n"));

            var ended = assertInstanceOf(EngineStep.Ended.class,
                    engine.advance(planStatus(EXECUTE, "plan.aborted")), "the abort terminal ends the plan");

            assertAll(
                    () -> assertEquals("aborted", ended.outcomeId(), "the abort reports the outcome aborted"),
                    () -> assertEquals(OutcomeClass.FAILURE, ended.outcomeClass(), "the abort reports failure"),
                    () -> assertEquals("aborted", ended.status().phase(), "only the outcome aborted takes aborted"));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("de.planmarshall.workflow.engine.WorkflowEngineTest#subWorkflowExits")
        @DisplayName("Should return each exit of a sub-workflow to its caller, which routes it")
        void shouldReturnSubWorkflowExitToCaller(StepOutcome outcome, String terminal) {
            fixtures.mark.then(outcome);
            var engine = fixtures.engine(callingPlan("execute.commit", CALL_COMMIT), parse("sub.pmwf", SUB_COMMIT));

            var ended = assertInstanceOf(EngineStep.Ended.class, engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "the sub-workflow exit reaches the caller");

            assertAll(
                    () -> assertEquals(terminal, ended.status().state(), "the exit lands in the routed terminal"),
                    () -> assertTrue(ended.status().callStack().isEmpty(), "the caller's call stack is empty"));
        }
    }

    /** The delivery terminals of a plan with the outcome class each reports; contract ids of the Workflow DSL. */
    static Stream<Arguments> deliveryTerminals() {
        return Stream.of(Arguments.of("completed", OutcomeClass.SUCCESS),
                Arguments.of("handed_back", OutcomeClass.FAILURE), Arguments.of("returned", OutcomeClass.FAILURE));
    }

    /**
     * @param terminal     the id of the one terminal state
     * @param outcomeId    the outcome the terminal reports
     * @param outcomeClass the class of that outcome
     * @param more         further states, which no transition enters
     * @return a plan unit whose start state enters the terminal on every outcome of {@code test.step}
     */
    private static WorkflowDefinition endingPlan(String terminal, String outcomeId, OutcomeClass outcomeClass,
            String more) {
        return parse("plan.pmwf", """
                ---
                name: plan
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow plan version 1 format 1
                scope plan
                start execute.first

                state execute.first
                    auto test.step
                        done -> %1$s
                        failed -> %1$s

                state %1$s terminal %2$s %3$s
                %4$s""".formatted(terminal, outcomeId, outcomeClass.name().toLowerCase(Locale.ROOT), more));
    }

    static Stream<Arguments> subWorkflowExits() {
        return Stream.of(Arguments.of(StepOutcome.DONE, "completed"), Arguments.of(StepOutcome.FAILED, "handed_back"));
    }

    @Nested
    @DisplayName("Advance")
    class Advance {

        @Test
        @DisplayName("Should chain every auto state in one advance and rest at the first job state")
        void shouldChainToFirstRestingState() {
            var tick = Generators.integers(1, 120).next();
            fixtures.clock.tick(Duration.ofSeconds(tick));
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));

            var resting = assertInstanceOf(EngineStep.Resting.class, engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "the chain rests at the first job state");

            assertAll(
                    () -> assertEquals("execute.build", resting.stateId(), "the chain rests at execute.build"),
                    () -> assertEquals(AwaitedEvent.JOB_SETTLED, resting.awaitedEvent(), "the state awaits the job"),
                    () -> assertEquals(EXECUTE, resting.status().phase(), "the phase stays at execute"),
                    () -> assertEquals(T0.plusSeconds(tick), resting.status().updatedAt(), "the tick sets updatedAt"),
                    () -> assertEquals(1, fixtures.step.calls(), "the step primitive ran once"),
                    () -> assertEquals(1, fixtures.mark.calls(), "the second auto state ran once"));
        }

        @Test
        @DisplayName("Should return a resting, an ended and a condition-carrying status unchanged")
        void shouldReturnSettledStatusUnchanged() {
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));
            var resting = planStatus(EXECUTE, "execute.build").toBuilder()
                    .activeJob(new WorkflowStatus.ActiveJob("execute.build", "b-00000000000000aa")).build();
            var ended = planStatus("completed", "completed");
            var raised = engine.raise(resting, "precondition_unmet", Map.of("fact", "push_remote")).status();

            assertAll(
                    () -> assertSame(resting, engine.advance(resting).status(),
                            "a resting status advances unchanged"),
                    () -> assertSame(ended, engine.advance(ended).status(), "an ended status advances unchanged"),
                    () -> assertSame(raised, assertInstanceOf(EngineStep.ConditionRaised.class,
                                    engine.advance(raised), "the advance raises a condition").status(),
                            "a condition-carrying status is unchanged"),
                    () -> assertEquals(0, fixtures.step.calls(), "the settled start runs no step"),
                    () -> assertTrue(fixtures.jobRequests.getValues().isEmpty(),
                            "a settled start starts no job"));
        }

        @Test
        @DisplayName("Should run from an auto state the status names")
        void shouldRunFromPersistedAutoState() {
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));

            var step = engine.advance(planStatus(EXECUTE, "execute.second"));

            assertAll(
                    () -> assertEquals("execute.build", step.status().state(), "execution resumes at execute.build"),
                    () -> assertEquals(0, fixtures.step.calls(), "the persisted start runs no step"),
                    () -> assertEquals(1, fixtures.mark.calls(), "the mark primitive ran once"));
        }

        @Test
        @DisplayName("Should start a unit that carries no phase at the workspace phase")
        void shouldInheritWorkspacePhaseAtStart() {
            var engine = fixtures.engine(parse("entity.pmwf", """
                    ---
                    name: ent.inherited
                    description: A unit written for the tests of pm-workflow.
                    type: system
                    icon: workflow.default
                    modification_date: 2026-01-15
                    version: 0.1.0
                    author: plan-marshall
                    ---
                    workflow ent.inherited version 1 format 1
                    scope entity
                    start ent.wait

                    state ent.wait
                        job test.build
                            succeeded -> ent.done
                            failed -> ent.done
                            cancelled -> ent.done

                    state ent.done terminal done success
                    """));

            var resting = engine.start(JournalScopeType.WORKSPACE, null);

            assertAll(
                    () -> assertEquals("workspace", resting.status().phase(), "a phase-less unit takes workspace"));
        }
    }

    @Nested
    @DisplayName("Verification")
    class Verification {

        @Test
        @DisplayName("Should refuse a unit with a violation and accept the corrected unit")
        void shouldRefuseUnverifiedUnit() {
            var unverified = plan("execute.first", """
                    state execute.first
                        auto test.step
                            done -> completed
                    """);
            var corrected = plan("execute.first", """
                    state execute.first
                        auto test.step
                            done -> completed
                            failed -> handed_back
                    """);

            var refused = assertThrows(WorkflowEngine.UnverifiedUnitException.class,
                    () -> fixtures.engine(unverified), "a violation refuses the unit");

            assertAll(
                    () -> assertEquals("plan", refused.unitId(), "the refused unit is the plan"),
                    () -> assertTrue(refused.verification().violations().stream()
                                    .anyMatch(violation -> violation.rule() == VerificationRule.OUTCOME_COVERAGE),
                            "the violation is a coverage gap"),
                    () -> assertEquals("plan", fixtures.engine(corrected).unit().id(), "the corrected unit verifies"));
        }

        @Test
        @DisplayName("Should refuse a unit whose called sub-workflow has a violation")
        void shouldRefuseUnverifiedCallee() {
            var callee = parse("sub.pmwf", SUB_COMMIT.replace("        failed -> sub.stepped-out\n", ""));

            var refused = assertThrows(WorkflowEngine.UnverifiedUnitException.class,
                    () -> fixtures.engine(callingPlan("execute.commit", CALL_COMMIT), callee),
                    "a callee violation refuses the caller");

            assertEquals("sub.commit", refused.unitId(), "the refused callee is sub.commit");
        }

        @Test
        @DisplayName("Should refuse a unit whose callee the engine's catalog does not hold")
        void shouldRefuseCalleeMissingFromCatalog() {
            var callee = parse("sub.pmwf", SUB_COMMIT);

            var refused = assertThrows(WorkflowEngine.UnverifiedUnitException.class,
                    () -> new WorkflowEngine(callingPlan("execute.commit", CALL_COMMIT), WorkflowCatalog.empty(),
                            fixtures.validator(callee), fixtures.registry(), (primitive, status) -> null,
                            fixtures.ports()), "a missing callee refuses the unit");

            assertAll(
                    () -> assertEquals("sub.commit", refused.unitId(), "the missing callee is sub.commit"),
                    () -> assertTrue(refused.verification().passed(), "the caller itself verifies cleanly"));
        }
    }

    @Nested
    @DisplayName("Retry counts")
    class RetryCounts {

        private static final String SUB_RETRY = """
                ---
                name: sub.retry
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow sub.retry version 1 format 1
                scope subworkflow
                entry main -> sub.attempt

                state sub.attempt
                    auto test.cap
                        again -> sub.attempt
                        spent -> sub.wait

                state sub.wait
                    job test.build
                        succeeded -> sub.finish
                        failed -> sub.stepped-out
                        cancelled -> sub.stepped-out

                state sub.finish
                    auto test.step
                        done -> sub.done
                        failed -> sub.stepped-out

                state sub.done terminal done success

                state sub.stepped-out terminal stepped_out stepped_out
                """;

        private static final String CALL_RETRY = """
                state execute.retry-call
                    call sub.retry
                        done -> completed
                        stepped_out -> plan.return
                """;

        private final WorkflowEngine engine = fixtures.engine(callingPlan("execute.retry-call", CALL_RETRY),
                parse("sub.pmwf", SUB_RETRY));

        private final RetryCounters counted = RetryCounters.empty()
                .attempt(RetryRule.PUSH_FAILED, "sub.attempt")
                .attempt(RetryRule.OPEN_PR, "sub.finish")
                .attempt(RetryRule.SYNC_FAILED, "execute.retry-call");

        @Test
        @DisplayName("Should keep the counts of a frame while its states are re-entered and left")
        void shouldKeepCountsWithinFrame() {
            fixtures.cap.then(CapOutcome.AGAIN).then(CapOutcome.SPENT);
            var status = planStatus(EXECUTE, "sub.attempt").toBuilder()
                    .callStack(List.of(frame("plan", "execute.retry-call", "sub.retry")))
                    .retryCounters(counted)
                    .build();

            var resting = engine.advance(status);

            assertAll(
                    () -> assertEquals("sub.wait", resting.status().state(), "the retry frame rests at sub.wait"),
                    () -> assertEquals(2, fixtures.cap.calls(), "the cap primitive ran twice"),
                    () -> assertEquals(counted, resting.status().retryCounters(), "the counts stay in the frame"));
        }

        @Test
        @DisplayName("Should drop the counts of the popped sub-workflow's states and keep the caller's")
        void shouldDropCountsOnPop() {
            var status = planStatus(EXECUTE, "sub.finish").toBuilder()
                    .callStack(List.of(frame("plan", "execute.retry-call", "sub.retry")))
                    .retryCounters(counted)
                    .build();

            var ended = engine.advance(status);

            var counters = ended.status().retryCounters();
            assertAll(
                    () -> assertEquals("completed", ended.status().state(), "the popped sub-workflow completes"),
                    () -> assertEquals(0, counters.attemptsOf(RetryRule.PUSH_FAILED, "sub.attempt"),
                            "push failures are dropped on pop"),
                    () -> assertEquals(0, counters.attemptsOf(RetryRule.OPEN_PR, "sub.finish"),
                            "open-pr retries are dropped on pop"),
                    () -> assertEquals(1, counters.attemptsOf(RetryRule.SYNC_FAILED, "execute.retry-call"),
                            "the caller's retry count is kept"));
        }
    }

    @Nested
    @DisplayName("Exactly one transition per outcome")
    class ExactlyOneTransition {

        /**
         * Generates verified plan units: a chain of {@code length} auto states, each routing one of its two outcomes
         * to the next state and the other, alternating by position, to a job state of its own.
         */
        static Stream<WorkflowDefinition> generatedUnits() {
            return IntStream.rangeClosed(1, 5).mapToObj(length -> {
                var states = new StringBuilder();
                for (var index = 1; index <= length; index++) {
                    var next = index == length ? "completed" : "execute.s" + (index + 1);
                    var held = "execute.hold-" + index;
                    var doneTarget = index % 2 == 0 ? held : next;
                    var failedTarget = index % 2 == 0 ? next : held;
                    states.append("""
                            state execute.s%d
                                auto test.step
                                    done -> %s
                                    failed -> %s

                            state %s
                                job test.build
                                    succeeded -> completed
                                    failed -> handed_back
                                    cancelled -> handed_back

                            """.formatted(index, doneTarget, failedTarget, held));
                }
                return plan("execute.s1", states.toString());
            });
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("generatedUnits")
        @DisplayName("Should route every outcome of every auto state to the one transition its unit declares for it")
        void shouldRouteEveryOutcomeToItsOneTransition(WorkflowDefinition unit) {
            var engine = fixtures.engine(unit);
            for (var state : unit.states().values()) {
                if (!(state.behavior() instanceof StateBehavior.AutoStep auto)) {
                    continue;
                }
                for (var outcome : StepOutcome.values()) {
                    var declared = auto.transitions().stream()
                            .filter(transition -> transition.outcomeName().equals(outcome.wireName())).toList();
                    assertEquals(1, declared.size(), "each outcome of the state has one transition");
                    var target = unit.states().get(declared.getFirst().targetStateId());
                    fixtures.step.then(outcome);

                    var step = engine.advance(planStatus(EXECUTE, state.id()));

                    if (target.behavior() instanceof StateBehavior.JobStep) {
                        assertEquals(target.id(), step.status().state(), "the outcome rests in its job target");
                    } else {
                        assertFalse(step.status().state().startsWith("execute.hold-"
                                        + state.id().substring("execute.s".length())),
                                "the outcome does not rest in the state's hold");
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Faults")
    class Faults {

        @Test
        @DisplayName("Should fail with unresolved_name for a state or a frame the verified units do not hold")
        void shouldFaultOnUnresolvedName() {
            var engine = fixtures.engine(plan("execute.first", CHAIN));
            var unknownFrame = planStatus(EXECUTE, "sub.work").toBuilder()
                    .callStack(List.of(frame("plan", "execute.first", "sub.unknown"))).build();
            var unknownState = planStatus(EXECUTE, "execute.nowhere");

            assertAll(
                    () -> assertEquals(Reason.UNRESOLVED_NAME, assertThrows(InternalFaultException.class,
                                    () -> engine.advance(unknownState), "an unknown state faults").reason(),
                            "an unknown state is unresolved"),
                    () -> assertEquals(Reason.UNRESOLVED_NAME, assertThrows(InternalFaultException.class,
                                    () -> engine.advance(unknownFrame), "an unknown frame faults").reason(),
                            "an unknown frame is unresolved"));
        }

        /*
         * A returned outcome no result guard routes is not reachable in a verified unit: Rule 6 admits a guarded 'done'
         * only as the complementary pair, of which exactly one guard holds over any result record. A returned outcome
         * the calling state does not route at all is reachable when the engine's catalog holds another callee than the
         * one the verifier checked the caller against; the engine then fails instead of guessing a route.
         */
        @Test
        @DisplayName("Should fail with undeclared_outcome when the catalog's callee returns an outcome the caller lacks")
        void shouldFaultOnOutcomeOutsideVerifiedContract() {
            var verifiedCallee = parse("sub.pmwf", SUB_COMMIT);
            var catalogCallee = parse("sub.pmwf", SUB_COMMIT.replace("terminal done success", "terminal pushed success")
                    .replace("exit: done", "exit: pushed"));
            var engine = new WorkflowEngine(callingPlan("execute.commit", CALL_COMMIT),
                    WorkflowCatalog.of(catalogCallee), fixtures.validator(verifiedCallee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(), fixtures.ports());

            var fault = assertThrows(InternalFaultException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "the catalog callee faults the caller");

            assertAll(
                    () -> assertEquals(Reason.UNDECLARED_OUTCOME, fault.reason(), "an undeclared outcome is reported"),
                    () -> assertEquals(1, fixtures.mark.calls(), "the mark primitive ran once"));
        }

        @Test
        @DisplayName("Should fail with undeclared_outcome when a refused start has no refused route in its job state")
        void shouldFaultOnRefusedStartWithoutRoute() {
            fixtures.refusing = true;
            var engine = fixtures.engine(plan("execute.build", """
                    state execute.build
                        job test.build
                            succeeded -> completed
                            failed -> handed_back
                            cancelled -> handed_back
                    """));

            var fault = assertThrows(InternalFaultException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "a refused start without route faults");

            assertEquals(Reason.UNDECLARED_OUTCOME, fault.reason(), "a refused start has no route");
        }

        @Test
        @DisplayName("Should fail with unbounded_chain when a capped primitive never takes its exhaustion outcome")
        void shouldFaultOnUnboundedChain() {
            var engine = fixtures.engine(plan("execute.loop", """
                    state execute.loop
                        auto test.cap
                            again -> execute.loop
                            spent -> execute.after

                    state execute.after
                        auto test.step
                            done -> completed
                            failed -> handed_back
                    """));

            var fault = assertThrows(InternalFaultException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "the looping chain faults at the cap");

            assertAll(
                    () -> assertEquals(Reason.UNBOUNDED_CHAIN, fault.reason(), "the capped chain never exhausts"),
                    () -> assertEquals(WorkflowEngine.MAX_CHAIN, fixtures.cap.calls(), "capped at the chain limit"));
        }

        @Test
        @DisplayName("Should refuse a parameter record of another type than the primitive takes")
        void shouldRefuseWrongParameterRecord() {
            var engine = new WorkflowEngine(plan("execute.first", CHAIN), WorkflowCatalog.empty(),
                    fixtures.validator(), fixtures.registry(), (primitive, status) -> new Unexpected(),
                    fixtures.ports());

            assertThrows(IllegalStateException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "a wrong parameter record is refused");
        }
    }

    @Nested
    @DisplayName("Conditions")
    class Conditions {

        @Test
        @DisplayName("Should raise a condition with its facts and leave state, phase and call stack as they are")
        void shouldRaiseCondition() {
            var tick = Generators.integers(1, 120).next();
            fixtures.clock.tick(Duration.ofSeconds(tick));
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));
            var resting = planStatus(EXECUTE, "execute.build");

            var raised = engine.raise(resting, "precondition_unmet", Map.of("fact", "push_remote"));

            var condition = raised.status().condition().orElseThrow();
            assertAll(
                    () -> assertEquals("precondition_unmet", raised.kind(), "the condition is precondition_unmet"),
                    () -> assertEquals("{\"fact\":\"push_remote\"}", raised.facts(), "the facts carry push_remote"),
                    () -> assertEquals(T0.plusSeconds(tick), condition.raisedAt(), "the raise stamps the clock tick"),
                    () -> assertEquals(0, condition.remedyAttempts(), "no remedy attempt is recorded"),
                    () -> assertEquals(resting.state(), raised.status().state(), "the state is left as it was"),
                    () -> assertEquals(resting.phase(), raised.status().phase(), "the phase is left as it was"),
                    () -> assertEquals(resting.callStack(), raised.status().callStack(), "the call stack is kept"));
        }

        @Test
        @DisplayName("Should refuse a second condition, the integrity breach and an unknown kind")
        void shouldRefuseInvalidRaise() {
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));
            var resting = planStatus(EXECUTE, "execute.build");
            var raised = engine.raise(resting, "job_incomplete", Map.of()).status();

            assertAll(
                    () -> assertThrows(IllegalStateException.class,
                            () -> engine.raise(raised, "job_incomplete", Map.of()),
                            "a second condition is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> engine.raise(resting, "integrity_breach", Map.of()),
                            "an integrity breach is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> engine.raise(resting, "unknown_kind", Map.of()),
                            "an unknown condition kind is refused"));
        }

        @Test
        @DisplayName("Should refuse steps that contradict the status they carry")
        void shouldRefuseContradictingSteps() {
            var resting = planStatus(EXECUTE, "execute.build");
            var framed = resting.toBuilder().callStack(List.of(frame("plan", "execute.invoke", "sub.commit"))).build();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineStep.Resting(resting, "execute.other", AwaitedEvent.JOB_SETTLED,
                                    List.of()),
                            "a resting step with another state is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineStep.Ended(framed, "done", OutcomeClass.SUCCESS, List.of()),
                            "an ended step with a call stack is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineStep.ConditionRaised(resting, "job_incomplete", "{}", List.of()),
                            "a contradicting condition step is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> EngineStep.ConditionRaised.of(resting),
                            "a condition step without facts is refused"));
        }
    }

    @Nested
    @DisplayName("Settlements of a step")
    class Settlements {

        @Test
        @DisplayName("Should carry the settlements on all three variants, as a copy the argument does not reach")
        void shouldCarryACopyOnAllThreeVariants() {
            var settlement = settlement();
            var argument = new ArrayList<>(List.of(settlement));
            var steps = steps(argument);

            argument.clear();

            assertAll(steps.stream().<Executable>map(step -> () -> assertEquals(List.of(settlement),
                    step.settlements(), "the step keeps a copy of its settlements")));
        }

        @Test
        @DisplayName("Should keep the settlements of every variant unmodifiable")
        void shouldKeepTheSettlementsUnmodifiable() {
            var steps = steps(List.of(settlement()));

            assertAll(steps.stream().<Executable>map(step -> () -> assertThrows(UnsupportedOperationException.class,
                    step.settlements()::clear, "the settlements of a step are unmodifiable")));
        }

        @Test
        @DisplayName("Should refuse a null list of settlements on every variant")
        void shouldRefuseANullList() {
            var resting = planStatus(EXECUTE, "execute.build");
            var raised = fixtures.engine(plan("execute.first", CHAIN_TO_JOB))
                    .raise(resting, "job_incomplete", Map.of()).status();

            assertAll(
                    () -> assertThrows(NullPointerException.class,
                            () -> new EngineStep.Resting(resting, "execute.build", AwaitedEvent.JOB_SETTLED, null),
                            "a resting step refuses null settlements"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new EngineStep.Ended(resting, "done", OutcomeClass.SUCCESS, null),
                            "an ended step refuses null settlements"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new EngineStep.ConditionRaised(raised, "job_incomplete", "{}", null),
                            "a condition step refuses null settlements"));
        }

        @Test
        @DisplayName("Should give the out-of-chain raise and the step of a status's condition no settlements")
        void shouldGiveOutOfChainStepsNoSettlements() {
            var resting = planStatus(EXECUTE, "execute.build");
            var raised = fixtures.engine(plan("execute.first", CHAIN_TO_JOB))
                    .raise(resting, "job_incomplete", Map.of());

            assertAll(
                    () -> assertEquals(List.of(), raised.settlements(), "the public raise carries no settlement"),
                    () -> assertEquals(List.of(), EngineStep.ConditionRaised.of(raised.status()).settlements(),
                            "the condition's own step carries none"));
        }

        private List<EngineStep> steps(List<CommandSettlement> settlements) {
            var resting = planStatus(EXECUTE, "execute.build");
            var raised = fixtures.engine(plan("execute.first", CHAIN_TO_JOB))
                    .raise(resting, "job_incomplete", Map.of()).status();
            var built = List.<EngineStep>of(
                    EngineStep.Resting.builder().status(resting).stateId("execute.build")
                            .awaitedEvent(AwaitedEvent.JOB_SETTLED).settlements(settlements).build(),
                    EngineStep.Ended.builder().status(resting).outcomeId("done").outcomeClass(OutcomeClass.SUCCESS)
                            .settlements(settlements).build(),
                    EngineStep.ConditionRaised.builder().status(raised).kind("job_incomplete").facts("{}")
                            .settlements(settlements).build());
            assertEquals(EngineStep.class.getPermittedSubclasses().length, built.size(),
                    "the fixture covers every EngineStep variant");
            return built;
        }

        private static CommandSettlement settlement() {
            return CommandSettlement.builder()
                    .command(CoreGenerators.commands().next())
                    .outcome(Generators.letterStrings(1, 20).next().toLowerCase(Locale.ROOT))
                    .outcomeClass(Generators.enumValues(OutcomeClass.class).next())
                    .facts(Map.of())
                    .settledAt(T0)
                    .build();
        }
    }

    /** A parameter record no fixture primitive takes. */
    record Unexpected() {
    }
}
