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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.InternalFaultException.Reason;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.RetryCounters.RetryRule;
import de.planmarshall.core.store.WorkflowStatus;
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
@DisplayName("WorkflowEngine")
class WorkflowEngineTest {

    private static final String CHAIN = """
            state execute.first
                phase execute
                auto test.step
                    done -> execute.second
                    failed -> handed_back

            state execute.second
                phase execute
                auto test.mark
                    done -> completed
                    failed -> handed_back
            """;

    private static final String CHAIN_TO_JOB = """
            state execute.first
                phase execute
                auto test.step
                    done -> execute.second
                    failed -> execute.build

            state execute.second
                phase execute
                auto test.mark
                    done -> execute.build
                    failed -> execute.build

            state execute.build
                phase execute
                job test.build
                    succeeded -> completed
                    failed -> handed_back
                    cancelled -> handed_back
            """;

    private static final String CALL_COMMIT = """
            state execute.commit
                phase execute
                call sub.commit
                    done -> completed
                    stepped_out -> handed_back
            """;

    private final EngineFixtures fixtures = new EngineFixtures();

    @Nested
    @DisplayName("Ends")
    class Ends {

        @Test
        @DisplayName("Should end a top-level unit in its success terminal after chaining both auto states")
        void shouldEndInSuccessTerminal() {
            var engine = fixtures.engine(plan("execute.first", CHAIN));

            var ended = assertInstanceOf(EngineStep.Ended.class, engine.start(JournalScopeType.PLAN, PLAN_ID));

            assertAll(
                    () -> assertEquals("merged", ended.outcomeId()),
                    () -> assertEquals(OutcomeClass.SUCCESS, ended.outcomeClass()),
                    () -> assertEquals("completed", ended.status().state()),
                    () -> assertEquals("completed", ended.status().phase()),
                    () -> assertEquals(List.of(Optional.of(PLAN_ID)), fixtures.step.scopes()),
                    () -> assertEquals(1, fixtures.mark.calls()));
        }

        @Test
        @DisplayName("Should end in the failure terminal the failed outcome routes to, without running the next state")
        void shouldEndInFailureTerminal() {
            fixtures.step.then(StepOutcome.FAILED);
            var engine = fixtures.engine(plan("execute.first", CHAIN));

            var ended = assertInstanceOf(EngineStep.Ended.class, engine.start(JournalScopeType.PLAN, PLAN_ID));

            assertAll(
                    () -> assertEquals("handed_back", ended.outcomeId()),
                    () -> assertEquals(OutcomeClass.FAILURE, ended.outcomeClass()),
                    () -> assertEquals(0, fixtures.mark.calls()));
        }

        @Test
        @DisplayName("Should end an epic in the abort terminal its one declared transition enters")
        void shouldEndInAbortTerminal() {
            var engine = fixtures.engine(parse("epic.pmwf", """
                    workflow epic version 1 format 1
                    scope epic
                    start epic.next-work

                    state epic.next-work
                        phase epic
                        auto test.next-work
                            abort_landed -> epic.aborted

                    state epic.aborted terminal aborted failure
                        phase aborted
                    """));

            var ended = assertInstanceOf(EngineStep.Ended.class, engine.start(JournalScopeType.EPIC, "engine-epic"));

            assertAll(
                    () -> assertEquals("aborted", ended.outcomeId()),
                    () -> assertEquals(OutcomeClass.FAILURE, ended.outcomeClass()),
                    () -> assertEquals("aborted", ended.status().phase()),
                    () -> assertEquals(1, fixtures.nextWork.calls()));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("de.planmarshall.workflow.engine.WorkflowEngineTest#subWorkflowExits")
        @DisplayName("Should return each exit of a sub-workflow to its caller, which routes it")
        void shouldReturnSubWorkflowExitToCaller(StepOutcome outcome, String terminal) {
            fixtures.mark.then(outcome);
            var engine = fixtures.engine(plan("execute.commit", CALL_COMMIT), parse("sub.pmwf", SUB_COMMIT));

            var ended = assertInstanceOf(EngineStep.Ended.class, engine.start(JournalScopeType.PLAN, PLAN_ID));

            assertAll(
                    () -> assertEquals(terminal, ended.status().state()),
                    () -> assertTrue(ended.status().callStack().isEmpty()));
        }
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
            fixtures.clock.tick(Duration.ofSeconds(7));
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));

            var resting = assertInstanceOf(EngineStep.Resting.class, engine.start(JournalScopeType.PLAN, PLAN_ID));

            assertAll(
                    () -> assertEquals("execute.build", resting.stateId()),
                    () -> assertEquals(AwaitedEvent.JOB_SETTLED, resting.awaitedEvent()),
                    () -> assertEquals(EXECUTE, resting.status().phase()),
                    () -> assertEquals(T0.plusSeconds(7), resting.status().updatedAt()),
                    () -> assertEquals(1, fixtures.step.calls()),
                    () -> assertEquals(1, fixtures.mark.calls()));
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
                    () -> assertSame(resting, engine.advance(resting).status()),
                    () -> assertSame(ended, engine.advance(ended).status()),
                    () -> assertSame(raised, assertInstanceOf(EngineStep.ConditionRaised.class,
                            engine.advance(raised)).status()),
                    () -> assertEquals(0, fixtures.step.calls()),
                    () -> assertTrue(fixtures.jobStarter.requests.isEmpty()));
        }

        @Test
        @DisplayName("Should run from an auto state the status names")
        void shouldRunFromPersistedAutoState() {
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));

            var step = engine.advance(planStatus(EXECUTE, "execute.second"));

            assertAll(
                    () -> assertEquals("execute.build", step.status().state()),
                    () -> assertEquals(0, fixtures.step.calls()),
                    () -> assertEquals(1, fixtures.mark.calls()));
        }

        @Test
        @DisplayName("Should start an entity unit that inherits its phase in the workspace scope only")
        void shouldInheritWorkspacePhaseAtStart() {
            var engine = fixtures.engine(parse("entity.pmwf", """
                    workflow ent.inherited version 1 format 1
                    scope entity
                    start ent.wait

                    state ent.wait
                        phase inherit
                        job test.build
                            succeeded -> ent.done
                            failed -> ent.done
                            cancelled -> ent.done

                    state ent.done terminal done success
                        phase inherit
                    """));

            var resting = engine.start(JournalScopeType.WORKSPACE, null);

            assertAll(
                    () -> assertEquals("workspace", resting.status().phase()),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> engine.start(JournalScopeType.PLAN, PLAN_ID)));
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
                        phase execute
                        auto test.step
                            done -> completed
                    """);
            var corrected = plan("execute.first", """
                    state execute.first
                        phase execute
                        auto test.step
                            done -> completed
                            failed -> handed_back
                    """);

            var refused = assertThrows(WorkflowEngine.UnverifiedUnitException.class,
                    () -> fixtures.engine(unverified));

            assertAll(
                    () -> assertEquals("plan", refused.unitId()),
                    () -> assertTrue(refused.verification().violations().stream()
                            .anyMatch(violation -> violation.rule() == VerificationRule.OUTCOME_COVERAGE)),
                    () -> assertEquals("plan", fixtures.engine(corrected).unit().id()));
        }

        @Test
        @DisplayName("Should refuse a unit whose called sub-workflow has a violation")
        void shouldRefuseUnverifiedCallee() {
            var callee = parse("sub.pmwf", SUB_COMMIT.replace("        failed -> sub.stepped-out\n", ""));

            var refused = assertThrows(WorkflowEngine.UnverifiedUnitException.class,
                    () -> fixtures.engine(plan("execute.commit", CALL_COMMIT), callee));

            assertEquals("sub.commit", refused.unitId());
        }

        @Test
        @DisplayName("Should refuse a unit whose callee the engine's catalog does not hold")
        void shouldRefuseCalleeMissingFromCatalog() {
            var callee = parse("sub.pmwf", SUB_COMMIT);

            var refused = assertThrows(WorkflowEngine.UnverifiedUnitException.class,
                    () -> new WorkflowEngine(plan("execute.commit", CALL_COMMIT), WorkflowCatalog.empty(),
                            fixtures.validator(callee), fixtures.registry(), (primitive, status) -> null,
                            fixtures.ports()));

            assertAll(
                    () -> assertEquals("sub.commit", refused.unitId()),
                    () -> assertTrue(refused.verification().passed()));
        }
    }

    @Nested
    @DisplayName("Retry counts")
    class RetryCounts {

        private static final String SUB_RETRY = """
                workflow sub.retry version 1 format 1
                scope subworkflow
                start sub.attempt

                state sub.attempt
                    phase inherit
                    auto test.cap
                        again -> sub.attempt
                        spent -> sub.wait

                state sub.wait
                    phase inherit
                    job test.build
                        succeeded -> sub.finish
                        failed -> sub.stepped-out
                        cancelled -> sub.stepped-out

                state sub.finish
                    phase inherit
                    auto test.step
                        done -> sub.done
                        failed -> sub.stepped-out

                state sub.done terminal done success
                    phase inherit

                state sub.stepped-out terminal stepped_out stepped_out
                    phase inherit
                """;

        private static final String CALL_RETRY = """
                state execute.retry-call
                    phase execute
                    call sub.retry
                        done -> completed
                        stepped_out -> handed_back
                """;

        private final WorkflowEngine engine = fixtures.engine(plan("execute.retry-call", CALL_RETRY),
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
                    () -> assertEquals("sub.wait", resting.status().state()),
                    () -> assertEquals(2, fixtures.cap.calls()),
                    () -> assertEquals(counted, resting.status().retryCounters()));
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
                    () -> assertEquals("completed", ended.status().state()),
                    () -> assertEquals(0, counters.attemptsOf(RetryRule.PUSH_FAILED, "sub.attempt")),
                    () -> assertEquals(0, counters.attemptsOf(RetryRule.OPEN_PR, "sub.finish")),
                    () -> assertEquals(1, counters.attemptsOf(RetryRule.SYNC_FAILED, "execute.retry-call")));
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
                                phase execute
                                auto test.step
                                    done -> %s
                                    failed -> %s

                            state %s
                                phase execute
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
                    assertEquals(1, declared.size(), () -> state.id() + " routes " + outcome + " once");
                    var target = unit.states().get(declared.getFirst().targetStateId());
                    fixtures.step.then(outcome);

                    var step = engine.advance(planStatus(EXECUTE, state.id()));

                    if (target.behavior() instanceof StateBehavior.JobStep) {
                        assertEquals(target.id(), step.status().state(), () -> state.id() + " on " + outcome);
                    } else {
                        assertFalse(step.status().state().startsWith("execute.hold-"
                                + state.id().substring("execute.s".length())), () -> state.id() + " on " + outcome);
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
                            () -> engine.advance(unknownState)).reason()),
                    () -> assertEquals(Reason.UNRESOLVED_NAME, assertThrows(InternalFaultException.class,
                            () -> engine.advance(unknownFrame)).reason()));
        }

        @Test
        @DisplayName("Should fail with unresolved_name for a phase the status store has no value for")
        void shouldFaultOnUnknownPhase() {
            var engine = fixtures.engine(plan("review.first", CHAIN.replace("execute", "review")));

            var fault = assertThrows(InternalFaultException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID));

            assertEquals(Reason.UNRESOLVED_NAME, fault.reason());
        }

        @Test
        @DisplayName("Should fail with undeclared_outcome when no result guard of the returned outcome holds")
        void shouldFaultOnUndeclaredOutcome() {
            var engine = fixtures.engine(plan("execute.commit", """
                    state execute.commit
                        phase execute
                        call sub.commit
                            done -> completed
                                guard result.pushed
                            stepped_out -> handed_back
                    """), parse("sub.pmwf", SUB_COMMIT));

            var fault = assertThrows(InternalFaultException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID));

            assertEquals(Reason.UNDECLARED_OUTCOME, fault.reason());
        }

        @Test
        @DisplayName("Should fail with undeclared_outcome when a refused start has no refused route in its job state")
        void shouldFaultOnRefusedStartWithoutRoute() {
            fixtures.jobStarter.refusing = true;
            var engine = fixtures.engine(plan("execute.build", """
                    state execute.build
                        phase execute
                        job test.build
                            succeeded -> completed
                            failed -> handed_back
                            cancelled -> handed_back
                    """));

            var fault = assertThrows(InternalFaultException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID));

            assertEquals(Reason.UNDECLARED_OUTCOME, fault.reason());
        }

        @Test
        @DisplayName("Should fail with unbounded_chain when a capped primitive never takes its exhaustion outcome")
        void shouldFaultOnUnboundedChain() {
            var engine = fixtures.engine(plan("execute.loop", """
                    state execute.loop
                        phase execute
                        auto test.cap
                            again -> execute.loop
                            spent -> execute.after

                    state execute.after
                        phase execute
                        auto test.step
                            done -> completed
                            failed -> handed_back
                    """));

            var fault = assertThrows(InternalFaultException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID));

            assertAll(
                    () -> assertEquals(Reason.UNBOUNDED_CHAIN, fault.reason()),
                    () -> assertEquals(WorkflowEngine.MAX_CHAIN, fixtures.cap.calls()));
        }

        @Test
        @DisplayName("Should refuse a parameter record of another type than the primitive takes")
        void shouldRefuseWrongParameterRecord() {
            var engine = new WorkflowEngine(plan("execute.first", CHAIN), WorkflowCatalog.empty(),
                    fixtures.validator(), fixtures.registry(), (primitive, status) -> new Unexpected(),
                    fixtures.ports());

            assertThrows(IllegalStateException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID));
        }
    }

    @Nested
    @DisplayName("Conditions")
    class Conditions {

        @Test
        @DisplayName("Should raise a condition with its facts and leave state, phase and call stack as they are")
        void shouldRaiseCondition() {
            fixtures.clock.tick(Duration.ofMinutes(1));
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));
            var resting = planStatus(EXECUTE, "execute.build");

            var raised = engine.raise(resting, "precondition_unmet", Map.of("fact", "push_remote"));

            var condition = raised.status().condition().orElseThrow();
            assertAll(
                    () -> assertEquals("precondition_unmet", raised.kind()),
                    () -> assertEquals("{\"fact\":\"push_remote\"}", raised.facts()),
                    () -> assertEquals(T0.plusSeconds(60), condition.raisedAt()),
                    () -> assertEquals(0, condition.remedyAttempts()),
                    () -> assertEquals(resting.state(), raised.status().state()),
                    () -> assertEquals(resting.phase(), raised.status().phase()),
                    () -> assertEquals(resting.callStack(), raised.status().callStack()));
        }

        @Test
        @DisplayName("Should refuse a second condition, the integrity breach and an unknown kind")
        void shouldRefuseInvalidRaise() {
            var engine = fixtures.engine(plan("execute.first", CHAIN_TO_JOB));
            var resting = planStatus(EXECUTE, "execute.build");
            var raised = engine.raise(resting, "job_incomplete", Map.of()).status();

            assertAll(
                    () -> assertThrows(IllegalStateException.class,
                            () -> engine.raise(raised, "job_incomplete", Map.of())),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> engine.raise(resting, "integrity_breach", Map.of())),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> engine.raise(resting, "unknown_kind", Map.of())));
        }

        @Test
        @DisplayName("Should refuse steps that contradict the status they carry")
        void shouldRefuseContradictingSteps() {
            var resting = planStatus(EXECUTE, "execute.build");
            var framed = resting.toBuilder().callStack(List.of(frame("plan", "execute.invoke", "sub.commit"))).build();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineStep.Resting(resting, "execute.other", AwaitedEvent.JOB_SETTLED)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineStep.Ended(framed, "done", OutcomeClass.SUCCESS)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new EngineStep.ConditionRaised(resting, "job_incomplete", "{}")),
                    () -> assertThrows(IllegalArgumentException.class, () -> EngineStep.ConditionRaised.of(resting)));
        }
    }

    /** A parameter record no fixture primitive takes. */
    record Unexpected() {
    }
}
