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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.RetryCounters.RetryRule;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.BoundArg;
import de.planmarshall.workflow.ast.CallFrame;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.ResultGuard;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.engine.EngineFixtures.StepOutcome;

/**
 * Sub-workflow calls (PM-WF-16): a call pushes a frame with its bound arguments and {@code pushed_at}, the callee's
 * states inherit the caller's phase, the result guards of a returned {@code done} partition its routes, a step-out
 * unwinds exactly one frame and the caller routes it, a spent condition unwinds every frame to the fallback in one
 * transaction without any caller's {@code stepped_out} route, and calls nest four deep.
 */
@DisplayName("Sub-workflow execution")
class SubWorkflowExecutionTest {

    private static final String SUB_ARGS = """
            workflow sub.args version 1 format 1
            scope subworkflow
            param mode : enum[strict|lenient]
            param rounds : int[1..5]
            param targets : list<enum[origin|mirror]>
            start sub.wait

            state sub.wait
                phase inherit
                job test.build
                    succeeded -> sub.done
                    failed -> sub.stepped-out
                    cancelled -> sub.stepped-out

            state sub.done terminal done success
                phase inherit

            state sub.stepped-out terminal stepped_out stepped_out
                phase inherit
            """;

    private static final String SUB_INNER = """
            workflow sub.inner version 1 format 1
            scope subworkflow
            start inner.choose

            state inner.choose
                phase inherit
                skill pm/test/choose
                link next proceed ()
                    do test.proceed
                        done -> inner.done
                        failed -> inner.stepped-out

            state inner.done terminal done success
                phase inherit

            state inner.stepped-out terminal stepped_out stepped_out
                phase inherit
            """;

    private static final String SUB_OUTER = """
            workflow sub.outer version 1 format 1
            scope subworkflow
            start outer.invoke

            state outer.invoke
                phase inherit
                call sub.inner
                    done -> outer.done
                    stepped_out -> outer.recover

            state outer.recover
                phase inherit
                job test.build
                    succeeded -> outer.done
                    failed -> outer.stepped-out
                    cancelled -> outer.stepped-out

            state outer.done terminal done success
                phase inherit

            state outer.stepped-out terminal stepped_out stepped_out
                phase inherit
            """;

    private static final String ENTITY_TOP = """
            workflow ent.top version 1 format 1
            scope entity
            start ent.invoke

            state ent.invoke
                phase execute
                call sub.outer
                    done -> ent.done
                    stepped_out -> ent.fallback

            state ent.fallback
                phase execute
                job test.build
                    succeeded -> ent.done
                    failed -> ent.done
                    cancelled -> ent.done

            state ent.done terminal done success
                phase execute
            """;

    private final EngineFixtures fixtures = new EngineFixtures();

    private WorkflowEngine nestedEngine() {
        return fixtures.engine(parse("ent.pmwf", ENTITY_TOP), parse("outer.pmwf", SUB_OUTER),
                parse("inner.pmwf", SUB_INNER));
    }

    /**
     * @param depth 1 to 4
     * @return the sub-workflow {@code sub.d<depth>}, which calls the next one down to depth 4, which rests on a job
     */
    private static WorkflowDefinition depthUnit(int depth) {
        var body = depth < 4
                ? "    call sub.d%d\n        done -> d%d.done\n        stepped_out -> d%d.stepped-out\n"
                .formatted(depth + 1, depth, depth)
                : "    job test.build\n        succeeded -> d4.done\n        failed -> d4.stepped-out\n"
                + "        cancelled -> d4.stepped-out\n";
        return parse("d%d.pmwf".formatted(depth), """
                workflow sub.d%1$d version 1 format 1
                scope subworkflow
                start d%1$d.step

                state d%1$d.step
                    phase inherit
                """.formatted(depth) + body + """

                state d%1$d.done terminal done success
                    phase inherit

                state d%1$d.stepped-out terminal stepped_out stepped_out
                    phase inherit
                """.formatted(depth));
    }

    @Nested
    @DisplayName("Call")
    class Call {

        @Test
        @DisplayName("Should push a frame with the bound arguments and pushed_at, and keep the caller's phase")
        void shouldPushFrame() {
            var pushedAt = fixtures.clock.tick(Duration.ofSeconds(5));
            var engine = fixtures.engine(plan("execute.invoke", """
                    state execute.invoke
                        phase execute
                        call sub.args(mode = strict, rounds = 2, targets = [origin, mirror])
                            done -> completed
                            stepped_out -> handed_back
                    """), parse("sub.pmwf", SUB_ARGS));

            var resting = assertInstanceOf(EngineStep.Resting.class, engine.start(JournalScopeType.PLAN, PLAN_ID));

            var expectedArgs = new LinkedHashMap<String, BoundArg>();
            expectedArgs.put("mode", new BoundArg.StringArg("strict"));
            expectedArgs.put("rounds", new BoundArg.IntegerArg(2));
            expectedArgs.put("targets", new BoundArg.StringSetArg(new LinkedHashSet<>(List.of("origin", "mirror"))));
            assertAll(
                    () -> assertEquals("sub.wait", resting.stateId()),
                    () -> assertEquals(EXECUTE, resting.status().phase()),
                    () -> assertEquals(List.of(new WorkflowStatus.CallFrame("plan", "execute.invoke", EXECUTE,
                            "sub.args", expectedArgs, pushedAt)), resting.status().callStack()));
        }

        @Test
        @DisplayName("Should nest calls four deep, each frame naming its caller")
        void shouldNestFourDeep() {
            var engine = fixtures.engine(plan("execute.invoke", """
                    state execute.invoke
                        phase execute
                        call sub.d1
                            done -> completed
                            stepped_out -> handed_back
                    """), depthUnit(1), depthUnit(2), depthUnit(3), depthUnit(4));

            var resting = engine.start(JournalScopeType.PLAN, PLAN_ID);

            var frames = resting.status().callStack();
            assertAll(
                    () -> assertEquals("d4.step", resting.status().state()),
                    () -> assertEquals(4, frames.size()),
                    () -> assertEquals(List.of("execute.invoke", "d1.step", "d2.step", "d3.step"),
                            frames.stream().map(WorkflowStatus.CallFrame::callerStateId).toList()),
                    () -> assertEquals(List.of("sub.d1", "sub.d2", "sub.d3", "sub.d4"),
                            frames.stream().map(WorkflowStatus.CallFrame::subworkflowId).toList()));
        }
    }

    @Nested
    @DisplayName("Return")
    class Return {

        static Stream<Arguments> resultRecords() {
            return Stream.of(
                    Arguments.of(Map.of("pushed", true), "completed"),
                    Arguments.of(Map.of("pushed", false), "execute.local"),
                    Arguments.of(Map.of(), "execute.local"),
                    Arguments.of(Map.of("pushed", "a1b2c3"), "completed"));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("resultRecords")
        @DisplayName("Should route a returned done by the one result guard that holds over the result record")
        void shouldRouteDoneByResultGuard(Map<String, Object> result, String expectedState) {
            fixtures.mark.then(StepOutcome.DONE, result);
            var engine = fixtures.engine(plan("execute.commit", """
                    state execute.commit
                        phase execute
                        call sub.commit
                            done -> completed
                                guard result.pushed
                            done -> execute.local
                                guard not result.pushed
                            stepped_out -> handed_back

                    state execute.local
                        phase execute
                        job test.build
                            succeeded -> completed
                            failed -> handed_back
                            cancelled -> handed_back
                    """), parse("sub.pmwf", SUB_COMMIT));

            var step = engine.start(JournalScopeType.PLAN, PLAN_ID);

            var held = Stream.of(new ResultGuard("pushed", false), new ResultGuard("pushed", true))
                    .filter(guard -> GuardEvaluator.holds(guard, result)).count();
            assertAll(
                    () -> assertEquals(expectedState, step.status().state()),
                    () -> assertEquals(1, held, "exactly one result guard holds"),
                    () -> assertTrue(step.status().callStack().isEmpty()));
        }

        @Test
        @DisplayName("Should pop exactly one frame on a server step-out, which the calling sub-workflow routes")
        void shouldPopOneFrameOnServerStepOut() {
            fixtures.mark.then(StepOutcome.FAILED);
            var engine = fixtures.engine(plan("execute.invoke", """
                    state execute.invoke
                        phase execute
                        call sub.mid
                            done -> completed
                            stepped_out -> handed_back
                    """), parse("mid.pmwf", """
                    workflow sub.mid version 1 format 1
                    scope subworkflow
                    start mid.invoke

                    state mid.invoke
                        phase inherit
                        call sub.commit
                            done -> mid.done
                            stepped_out -> mid.hold

                    state mid.hold
                        phase inherit
                        job test.build
                            succeeded -> mid.done
                            failed -> mid.stepped-out
                            cancelled -> mid.stepped-out

                    state mid.done terminal done success
                        phase inherit

                    state mid.stepped-out terminal stepped_out stepped_out
                        phase inherit
                    """), parse("sub.pmwf", SUB_COMMIT));

            var resting = engine.start(JournalScopeType.PLAN, PLAN_ID);

            assertAll(
                    () -> assertEquals("mid.hold", resting.status().state()),
                    () -> assertEquals(List.of("sub.mid"), resting.status().callStack().stream()
                            .map(WorkflowStatus.CallFrame::subworkflowId).toList()));
        }

        @Test
        @DisplayName("Should unwind exactly one frame on a model step-out, which the calling sub-workflow routes")
        void shouldUnwindOneFrameOnModelStepOut() {
            var engine = nestedEngine();
            var choosing = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var stepped = engine.stepOut(choosing);

            assertAll(
                    () -> assertEquals("inner.choose", choosing.state()),
                    () -> assertEquals(2, choosing.callStack().size()),
                    () -> assertEquals("outer.recover", stepped.status().state()),
                    () -> assertEquals(List.of("sub.outer"), stepped.status().callStack().stream()
                            .map(WorkflowStatus.CallFrame::subworkflowId).toList()),
                    () -> assertEquals(EXECUTE, stepped.status().phase()));
        }

        @Test
        @DisplayName("Should refuse a step-out outside a sub-workflow, under a condition, or from no link state")
        void shouldRefuseInvalidStepOut() {
            var engine = nestedEngine();
            var choosing = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            var recovering = engine.stepOut(choosing).status();
            var raised = engine.raise(choosing, "job_incomplete", Map.of()).status();

            assertAll(
                    () -> assertThrows(IllegalStateException.class,
                            () -> engine.stepOut(planStatus(EXECUTE, "ent.fallback"))),
                    () -> assertThrows(IllegalStateException.class, () -> engine.stepOut(raised)),
                    () -> assertThrows(IllegalStateException.class, () -> engine.stepOut(recovering)));
        }
    }

    @Nested
    @DisplayName("Condition unwind")
    class ConditionUnwind {

        @Test
        @DisplayName("Should unwind every frame in one transaction to the fallback, dropping the unwound retry counts")
        void shouldUnwindEveryFrameToFallback() {
            var engine = nestedEngine();
            var choosing = engine.start(JournalScopeType.PLAN, PLAN_ID).status().toBuilder()
                    .retryCounters(RetryCounters.empty()
                            .attempt(RetryRule.RETRY_ONCE, "inner.choose")
                            .attempt(RetryRule.CONDITION_BUDGET, "outer.recover")
                            .attempt(RetryRule.OPEN_PR, "ent.invoke"))
                    .build();
            var raised = engine.raise(choosing, "job_incomplete", Map.of("task", "qt-0000000000000001"));

            var fallback = engine.unwindToFallback(raised.status(), "ent.fallback");

            var counters = fallback.status().retryCounters();
            assertAll(
                    () -> assertEquals("ent.fallback", fallback.status().state()),
                    () -> assertTrue(fallback.status().callStack().isEmpty()),
                    () -> assertTrue(fallback.status().condition().isEmpty()),
                    () -> assertEquals(0, counters.attemptsOf(RetryRule.RETRY_ONCE, "inner.choose")),
                    () -> assertEquals(0, counters.attemptsOf(RetryRule.CONDITION_BUDGET, "outer.recover")),
                    () -> assertEquals(1, counters.attemptsOf(RetryRule.OPEN_PR, "ent.invoke")),
                    () -> assertNotEquals("outer.recover", fallback.status().state()));
        }

        @Test
        @DisplayName("Should refuse a fallback state the top-level unit does not have")
        void shouldRefuseUnknownFallback() {
            var engine = nestedEngine();
            var choosing = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            assertThrows(IllegalArgumentException.class, () -> engine.unwindToFallback(choosing, "outer.recover"));
        }
    }

    @Nested
    @DisplayName("CallStack value")
    class CallStackValue {

        private final CallFrame frame = new CallFrame("plan", "execute.invoke", EXECUTE, "sub.args",
                Map.of("mode", new CallValue.EnumValue("strict")), T0.toString());

        @Test
        @DisplayName("Should push to four frames and refuse a fifth")
        void shouldBoundDepth() {
            var full = CallStack.empty().push(frame).push(frame).push(frame).push(frame);

            assertAll(
                    () -> assertEquals(CallStack.MAX_DEPTH, full.depth()),
                    () -> assertThrows(IllegalStateException.class, () -> full.push(frame)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new CallStack(List.of(frame, frame, frame, frame, frame))));
        }

        @Test
        @DisplayName("Should pop exactly one frame, unwind every frame top first, and refuse to pop an empty stack")
        void shouldPopAndUnwind() {
            var other = new CallFrame("sub.args", "sub.wait", EXECUTE, "sub.commit", Map.of(), T0.toString());
            var stack = CallStack.empty().push(frame).push(other);

            var popped = stack.pop();

            assertAll(
                    () -> assertEquals(other, popped.frame()),
                    () -> assertEquals(List.of(frame), popped.remaining().frames()),
                    () -> assertEquals(List.of(other, frame), stack.unwound()),
                    () -> assertEquals(other, stack.top().orElseThrow()),
                    () -> assertTrue(CallStack.empty().top().isEmpty()),
                    () -> assertThrows(IllegalStateException.class, () -> CallStack.empty().pop()));
        }

        @Test
        @DisplayName("Should convert every kind of bound argument to the store and back")
        void shouldRoundTripThroughStore() {
            var args = new LinkedHashMap<String, CallValue>();
            args.put("mode", new CallValue.EnumValue("strict"));
            args.put("rounds", new CallValue.IntValue(3));
            args.put("targets", new CallValue.EnumList(List.of("origin", "mirror")));
            var stack = CallStack.empty().push(new CallFrame("plan", "execute.invoke", EXECUTE, "sub.args", args,
                    T0.toString()));

            assertEquals(stack, CallStack.fromStore(stack.toStore()));
        }

        @Test
        @DisplayName("Should refuse a stored integer argument outside the range of a call value")
        void shouldRefuseOversizedInteger() {
            var stored = new WorkflowStatus.CallFrame("plan", "execute.invoke", EXECUTE, "sub.args",
                    Map.of("rounds", new BoundArg.IntegerArg(Long.MAX_VALUE)), T0);

            assertThrows(ArithmeticException.class, () -> CallStack.fromStore(List.of(stored)));
        }

        @Test
        @DisplayName("Should read the frames of a store status the engine resumes")
        void shouldReadFramesOfStatus() {
            var status = planStatus(EXECUTE, "sub.work").toBuilder()
                    .callStack(List.of(frame("plan", "execute.commit", "sub.commit"))).build();

            var stack = CallStack.fromStore(status.callStack());

            assertEquals("sub.commit", stack.top().orElseThrow().subWorkflowId());
        }
    }
}
