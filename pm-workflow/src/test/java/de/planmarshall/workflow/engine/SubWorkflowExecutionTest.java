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
import static de.planmarshall.workflow.engine.EngineFixtures.planStatus;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
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
import org.junit.jupiter.params.provider.ValueSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.id.CommandId;
import de.planmarshall.core.id.CorrelationId;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.RetryCounters.RetryRule;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.BoundArg;
import de.planmarshall.core.store.WorkflowStatusCodec;
import de.planmarshall.workflow.ast.CallFrame;
import de.planmarshall.workflow.ast.CallValue;
import de.planmarshall.workflow.ast.ResultGuard;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.engine.EngineFixtures.StepOutcome;
import de.planmarshall.workflow.verify.VerificationRule;
import de.planmarshall.workflow.verify.Violation;
import de.planmarshall.workflow.verify.WorkflowCatalog;

/**
 * Sub-workflow calls (PM-WF-16): a call pushes a frame with its bound arguments and {@code pushed_at}, the callee's
 * states inherit the caller's phase, the result guards of a returned {@code done} partition its routes, a step-out
 * unwinds exactly one frame and the caller routes it, a spent condition unwinds every frame to the fallback in one
 * transaction without any caller's {@code stepped_out} route, and calls nest four deep.
 */
@EnableGeneratorController
@DisplayName("Sub-workflow execution")
class SubWorkflowExecutionTest {

    private static final String SUB_ARGS = """
            ---
            name: sub.args
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            input: mode
            input_type: enum[strict|lenient]
            input: rounds
            input_type: int[1..5]
            input: targets
            input_type: list<enum[origin|mirror]>
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.args version 1 format 1
            scope subworkflow
            entry main -> sub.wait

            state sub.wait
                job test.build
                    succeeded -> sub.done
                    failed -> sub.stepped-out
                    cancelled -> sub.stepped-out

            state sub.done terminal done success

            state sub.stepped-out terminal stepped_out stepped_out
            """;

    private static final String SUB_INNER = """
            ---
            name: sub.inner
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.inner version 1 format 1
            scope subworkflow
            entry main -> inner.choose

            state inner.choose
                skill pm/test/choose
                link next proceed ()
                    do test.proceed
                        done -> inner.done
                        failed -> inner.stepped-out

            state inner.done terminal done success

            state inner.stepped-out terminal stepped_out stepped_out
            """;

    /**
     * Routes the step-out of {@code sub.inner} to a state of its own, {@code outer.recover}, which the step-out tests
     * observe. Rule 6 admits as that target in a sub-workflow only a terminal of class {@code stepped_out} or
     * {@code failure}, which would unwind the frame of {@code sub.outer} too, or a link state, whose own step-out
     * continues the unwinding; so {@code outer.recover} is a link state, and the frame of {@code sub.outer} stays.
     */
    private static final String SUB_OUTER = """
            ---
            name: sub.outer
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.outer version 1 format 1
            scope subworkflow
            entry main -> outer.invoke

            state outer.invoke
                call sub.inner
                    done -> outer.done
                    stepped_out -> outer.recover

            state outer.recover
                skill pm/test/recover
                link next proceed ()
                    do test.proceed
                        done -> outer.done
                        failed -> outer.stepped-out

            state outer.done terminal done success

            state outer.stepped-out terminal stepped_out stepped_out
            """;

    private static final String ENTITY_TOP = """
            ---
            name: ent.top
            description: A unit written for the tests of pm-workflow.
            type: system
            icon: workflow.default
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow ent.top version 1 format 1
            scope entity
            start ent.invoke

            state ent.invoke
                call sub.outer
                    done -> ent.done
                    stepped_out -> ent.fallback

            state ent.fallback
                job test.build
                    succeeded -> ent.done
                    failed -> ent.done
                    cancelled -> ent.done

            state ent.done terminal done success
            """;

    /** A plan state that calls {@code sub.args} with every kind of bound argument. */
    private static final String CALL_ARGS = """
            state execute.invoke
                call sub.args(mode = strict, rounds = 2, targets = [origin, mirror])
                    done -> completed
                    stepped_out -> plan.return
            """;

    /**
     * A sub-workflow with two entries, each bound to a job state of its own, so a call rests in the state of the entry
     * it took.
     */
    private static final String SUB_TWO_ENTRIES = """
            ---
            name: sub.two
            description: A sub-workflow with two entries written for the engine tests.
            type: subworkflow
            icon: workflow.default
            entry: review
            entry_description: The change is ready for its review.
            entry: repair
            entry_description: The review rejected the change.
            exit: done
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.two version 1 format 1
            scope subworkflow
            entry review -> two.review
            entry repair -> two.repair

            state two.review
                job test.build
                    succeeded -> two.done
                    failed -> two.stepped-out
                    cancelled -> two.stepped-out

            state two.repair
                job test.build
                    succeeded -> two.done
                    failed -> two.stepped-out
                    cancelled -> two.stepped-out

            state two.done terminal done success

            state two.stepped-out terminal stepped_out stepped_out
            """;

    private final EngineFixtures fixtures = new EngineFixtures();

    /** An engine over a plan whose state {@code execute.invoke} calls {@code sub.two} with the given call line. */
    private WorkflowEngine twoEntriesEngine(String callLine) {
        return fixtures.engine(callingPlan("execute.invoke", """
                state execute.invoke
                    %s
                        done -> completed
                        stepped_out -> plan.return
                """.formatted(callLine)), parse("two.pmwf", SUB_TWO_ENTRIES));
    }

    /**
     * An engine over a plan whose state {@code execute.invoke} calls {@code sub.two} with the given call line, verified
     * against {@code verifiedCallee} while its catalog holds {@link #SUB_TWO_ENTRIES}.
     */
    private WorkflowEngine catalogEngine(String callLine, WorkflowDefinition verifiedCallee) {
        var plan = callingPlan("execute.invoke", """
                state execute.invoke
                    %s
                        done -> completed
                        stepped_out -> plan.return
                """.formatted(callLine));
        return new WorkflowEngine(plan, WorkflowCatalog.of(parse("two.pmwf", SUB_TWO_ENTRIES)),
                fixtures.validator(verifiedCallee), fixtures.registry(),
                (primitive, status) -> new EngineFixtures.NoParams(), fixtures.ports());
    }

    static Stream<Arguments> namedEntries() {
        return Stream.of(Arguments.of("review", "two.review"), Arguments.of("repair", "two.repair"));
    }

    private WorkflowEngine argsEngine() {
        return fixtures.engine(callingPlan("execute.invoke", CALL_ARGS), parse("sub.pmwf", SUB_ARGS));
    }

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
                ? """
                    call sub.d%d
                        done -> d%d.done
                        stepped_out -> d%d.stepped-out
                """.formatted(depth + 1, depth, depth)
                : """
                    job test.build
                        succeeded -> d4.done
                        failed -> d4.stepped-out
                        cancelled -> d4.stepped-out
                """;
        return parse("d%d.pmwf".formatted(depth), """
                ---
                name: sub.d%1$d
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow sub.d%1$d version 1 format 1
                scope subworkflow
                entry main -> d%1$d.step

                state d%1$d.step
                """.formatted(depth) + body + """

                state d%1$d.done terminal done success

                state d%1$d.stepped-out terminal stepped_out stepped_out
                """.formatted(depth));
    }

    @Nested
    @DisplayName("Call")
    class Call {

        @Test
        @DisplayName("Should push a frame with the sole entry, the arguments and pushed_at, and keep the caller's phase")
        void shouldPushFrame() {
            var pushedAt = fixtures.clock.tick(Duration.ofSeconds(5));
            var engine = argsEngine();

            var resting = assertInstanceOf(EngineStep.Resting.class,
                    engine.start(JournalScopeType.PLAN, PLAN_ID), "the call rests in the callee");

            var expectedArgs = new LinkedHashMap<String, BoundArg>();
            expectedArgs.put("mode", new BoundArg.StringArg("strict"));
            expectedArgs.put("rounds", new BoundArg.IntegerArg(2));
            expectedArgs.put("targets", new BoundArg.StringSetArg(new LinkedHashSet<>(List.of("origin", "mirror"))));
            var frame = resting.status().callStack().getFirst();
            assertAll(
                    () -> assertEquals("sub.wait", resting.stateId(), "call rests in the callee's entry state"),
                    () -> assertEquals(EXECUTE, resting.status().phase(), "call keeps the caller's phase"),
                    () -> assertEquals("plan", frame.callerWorkflowId(), "frame records the caller"),
                    () -> assertEquals("execute.invoke", frame.callerStateId(), "frame records the calling state"),
                    () -> assertEquals("sub.args", frame.subworkflowId(), "frame records the callee"),
                    () -> assertEquals("main", frame.entry(), "frame records the entry"),
                    () -> assertEquals(expectedArgs, frame.boundArgs(), "frame records the bound arguments"),
                    () -> assertEquals(pushedAt, frame.pushedAt(), "frame records the push instant"),
                    () -> assertTrue(CommandId.parse(frame.commandId().toString()).isPresent(),
                            "frame carries a command id of the grammar"),
                    () -> assertTrue(CorrelationId.parse(
                                    frame.correlationId().toString()).isPresent(),
                            "frame carries a correlation id of the grammar"));
        }

        @ParameterizedTest(name = "entry {0} -> {1}")
        @MethodSource("de.planmarshall.workflow.engine.SubWorkflowExecutionTest#namedEntries")
        @DisplayName("Should enter the state the named entry binds and push a frame with that entry")
        void shouldEnterNamedEntry(String entry, String expectedState) {
            var engine = twoEntriesEngine("call sub.two entry " + entry);

            var resting = assertInstanceOf(EngineStep.Resting.class,
                    engine.start(JournalScopeType.PLAN, PLAN_ID), "the call rests in the callee");

            assertAll(
                    () -> assertEquals(expectedState, resting.stateId(), "call rests in the state of its entry"),
                    () -> assertEquals(List.of("sub.two"), resting.status().callStack().stream()
                                    .map(WorkflowStatus.CallFrame::subworkflowId).toList(),
                            "frame names the called sub-workflow"),
                    () -> assertEquals(List.of(entry), resting.status().callStack().stream()
                            .map(WorkflowStatus.CallFrame::entry).toList(), "frame names the called entry"));
        }

        @ParameterizedTest(name = "entry {0}")
        @MethodSource("de.planmarshall.workflow.engine.SubWorkflowExecutionTest#namedEntries")
        @DisplayName("Should write the entry of the pushed frame with the status of the call")
        void shouldWriteEntryWithStatus(String entry, String expectedState) {
            var engine = twoEntriesEngine("call sub.two entry " + entry);

            var status = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            var written = new String(WorkflowStatusCodec.write(status), StandardCharsets.UTF_8);

            assertAll(
                    () -> assertEquals(expectedState, status.state(), "call rests in the state of its entry"),
                    () -> assertTrue(written.contains("\"subworkflow_id\":\"sub.two\",\"entry\":\"" + entry + "\""),
                            written));
        }

        @Test
        @DisplayName("Should enter the sole entry of a callee when the call names none, and record it on the frame")
        void shouldEnterSoleEntry() {
            var engine = argsEngine();
            var callee = parse("sub.pmwf", SUB_ARGS);

            var resting = assertInstanceOf(EngineStep.Resting.class,
                    engine.start(JournalScopeType.PLAN, PLAN_ID), "the call rests in the callee");

            assertAll(
                    () -> assertEquals(List.of("main"), List.copyOf(callee.entryBindings().keySet()),
                            "the callee declares its sole entry"),
                    () -> assertEquals(callee.entryBindings().get("main").stateId(), resting.stateId(),
                            "call rests in the state the sole entry binds"),
                    () -> assertEquals("main", resting.status().callStack().getFirst().entry(),
                            "frame records the sole entry"));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"call sub.two", "call sub.two entry recheck"})
        @DisplayName("Should refuse a caller whose call names no entry of several or an entry the callee lacks")
        void shouldRefuseCallerWithoutDeclaredEntry(String callLine) {
            var unverified = assertThrows(WorkflowEngine.UnverifiedUnitException.class,
                    () -> twoEntriesEngine(callLine), "a call without a declared entry is refused");

            assertEquals(List.of(VerificationRule.INTERFACE_CALL_ENTRY), unverified.verification().violations()
                    .stream().map(Violation::rule).toList(), "the verifier reports the missing call entry");
        }

        /*
         * A verified caller names an entry its callee declares, so the engine meets a call without an entry of several,
         * or through an entry the callee does not bind, only when its catalog holds another callee than the one the
         * verifier checked the caller against; it then fails instead of guessing a state.
         */
        @Test
        @DisplayName("Should fault on a call that names no entry of a catalog callee with several entries")
        void shouldFaultOnMissingEntryOfSeveral() {
            var verifiedCallee = parse("two.pmwf", SUB_TWO_ENTRIES
                    .replace("entry: repair\nentry_description: The review rejected the change.\n", ""));
            var engine = catalogEngine("call sub.two", verifiedCallee);

            var fault = assertThrows(InternalFaultException.class,
                    () -> engine.start(JournalScopeType.PLAN, PLAN_ID), "call naming no entry of several faults");

            assertTrue(fault.getMessage().contains("binds 2 entries"), fault::getMessage);
        }

        @Test
        @DisplayName("Should fault on a call through an entry the catalog callee does not bind")
        void shouldFaultOnUnboundEntry() {
            var verifiedCallee = parse("two.pmwf", SUB_TWO_ENTRIES
                    .replace("entry_description: The review rejected the change.\n",
                            "entry_description: The review rejected the change.\nentry: recheck\n"));
            var engine = catalogEngine("call sub.two entry recheck", verifiedCallee);

            var fault = assertThrows(InternalFaultException.class,
                    () -> engine.start(JournalScopeType.PLAN, PLAN_ID), "call through an unbound entry faults");

            assertTrue(fault.getMessage().contains("binds no entry 'recheck'"), fault::getMessage);
        }

        @Test
        @DisplayName("Should refuse to start a unit that binds entries, which is called and never started")
        void shouldRefuseToStartUnitWithEntries() {
            var engine = fixtures.engine(parse("two.pmwf", SUB_TWO_ENTRIES));

            assertThrows(IllegalArgumentException.class, () -> engine.start(JournalScopeType.PLAN, PLAN_ID),
                    "starting a unit that binds entries is refused");
        }

        @Test
        @DisplayName("Should nest calls four deep, each frame naming its caller")
        void shouldNestFourDeep() {
            var engine = fixtures.engine(callingPlan("execute.invoke", """
                    state execute.invoke
                        call sub.d1
                            done -> completed
                            stepped_out -> plan.return
                    """), depthUnit(1), depthUnit(2), depthUnit(3), depthUnit(4));

            var resting = engine.start(JournalScopeType.PLAN, PLAN_ID);

            var frames = resting.status().callStack();
            assertAll(
                    () -> assertEquals("d4.step", resting.status().state(), "the fourth callee rests on its job"),
                    () -> assertEquals(4, frames.size(), "four calls push four frames"),
                    () -> assertEquals(List.of("execute.invoke", "d1.step", "d2.step", "d3.step"),
                            frames.stream().map(WorkflowStatus.CallFrame::callerStateId).toList(),
                            "each frame names the caller state"),
                    () -> assertEquals(List.of("sub.d1", "sub.d2", "sub.d3", "sub.d4"),
                            frames.stream().map(WorkflowStatus.CallFrame::subworkflowId).toList(),
                            "the frames name the nested callees"),
                    () -> assertEquals(List.of("main", "main", "main", "main"),
                            frames.stream().map(WorkflowStatus.CallFrame::entry).toList(),
                            "every nested call takes the sole entry"));
        }
    }

    @Nested
    @DisplayName("Return")
    class Return {

        static Stream<Arguments> resultRecords() {
            var present = Generators.letterStrings(1, 8).next();
            return Stream.of(
                    Arguments.of(Map.of("pushed", true), "completed"),
                    Arguments.of(Map.of("pushed", false), "execute.local"),
                    Arguments.of(Map.of(), "execute.local"),
                    Arguments.of(Map.of("pushed", present), "completed"));
        }

        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("resultRecords")
        @DisplayName("Should route a returned done by the one result guard that holds over the result record")
        void shouldRouteDoneByResultGuard(Map<String, Object> result, String expectedState) {
            fixtures.mark.then(StepOutcome.DONE, result);
            var engine = fixtures.engine(callingPlan("execute.commit", """
                    state execute.commit
                        call sub.commit
                            done -> completed
                                guard result.pushed
                            done -> execute.local
                                guard not result.pushed
                            stepped_out -> plan.return

                    state execute.local
                        job test.build
                            succeeded -> completed
                            failed -> handed_back
                            cancelled -> handed_back
                    """), parse("sub.pmwf", SUB_COMMIT));

            var step = engine.start(JournalScopeType.PLAN, PLAN_ID);

            var held = Stream.of(new ResultGuard("pushed", false), new ResultGuard("pushed", true))
                    .filter(guard -> GuardEvaluator.holds(guard, result)).count();
            assertAll(
                    () -> assertEquals(expectedState, step.status().state(), "the holding guard routes the done"),
                    () -> assertEquals(1, held, "exactly one result guard holds"),
                    () -> assertTrue(step.status().callStack().isEmpty(), "the returned call pops its frame"));
        }

        @Test
        @DisplayName("Should pop exactly one frame on a server step-out, which the calling sub-workflow routes")
        void shouldPopOneFrameOnServerStepOut() {
            fixtures.mark.then(StepOutcome.FAILED);
            // Rule 6 admits as the step-out target of a sub-workflow a link state, whose own step-out continues the
            // unwinding, or a terminal of class stepped_out or failure, which would pop the frame of sub.mid too; so
            // mid.hold, where the popped frame must leave the scope, is a link state. Rule 10 refuses a plan or epic
            // unit that reaches a link state through a call, so the top-level unit is an entity unit.
            var engine = fixtures.engine(parse("ent.pmwf", ENTITY_TOP.replace("call sub.outer", "call sub.mid")),
                    parse("mid.pmwf", """
                            ---
                            name: sub.mid
                            description: A unit written for the tests of pm-workflow.
                            type: subworkflow
                            icon: workflow.default
                            entry: main
                            exit: done
                            modification_date: 2026-01-15
                            version: 0.1.0
                            author: plan-marshall
                            ---
                            workflow sub.mid version 1 format 1
                            scope subworkflow
                            entry main -> mid.invoke

                            state mid.invoke
                                call sub.commit
                                    done -> mid.done
                                    stepped_out -> mid.hold

                            state mid.hold
                                skill pm/test/hold
                                link next proceed ()
                                    do test.proceed
                                        done -> mid.done
                                        failed -> mid.stepped-out

                            state mid.done terminal done success

                            state mid.stepped-out terminal stepped_out stepped_out
                            """), parse("sub.pmwf", SUB_COMMIT));

            var resting = engine.start(JournalScopeType.PLAN, PLAN_ID);

            assertAll(
                    () -> assertEquals("mid.hold", resting.status().state(), "step-out enters the caller's link state"),
                    () -> assertEquals(List.of("sub.mid"), resting.status().callStack().stream()
                                    .map(WorkflowStatus.CallFrame::subworkflowId).toList(),
                            "only the middle frame remains"));
        }

        @Test
        @DisplayName("Should unwind exactly one frame on a model step-out, which the calling sub-workflow routes")
        void shouldUnwindOneFrameOnModelStepOut() {
            var engine = nestedEngine();
            var choosing = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var stepped = engine.stepOut(choosing);

            assertAll(
                    () -> assertEquals("inner.choose", choosing.state(), "the nested call rests on the inner link"),
                    () -> assertEquals(2, choosing.callStack().size(), "two frames are open before the step-out"),
                    () -> assertEquals("outer.recover", stepped.status().state(), "the caller routes the stepped frame"),
                    () -> assertEquals(List.of("sub.outer"), stepped.status().callStack().stream()
                            .map(WorkflowStatus.CallFrame::subworkflowId).toList(), "the outer frame stays open"),
                    // the root is an entity unit, which carries no phase of its own: the plan scope keeps the phase
                    // it opened at
                    () -> assertEquals("phase-1-init", stepped.status().phase(), "the plan scope keeps its phase"));
        }

        @Test
        @DisplayName("Should refuse a step-out outside a sub-workflow, under a condition, or from no link state")
        void shouldRefuseInvalidStepOut() {
            var engine = nestedEngine();
            var choosing = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            var raised = engine.raise(choosing, "job_incomplete", Map.of()).status();
            var outsideSubWorkflow = planStatus(EXECUTE, "ent.fallback");
            // The state a step-out enters inside a sub-workflow is a link state (Rule 6), so the state that rests
            // inside a sub-workflow on no link is the job state sub.wait of sub.args.
            var argsEngine = argsEngine();
            var waiting = argsEngine.start(JournalScopeType.PLAN, PLAN_ID).status();

            assertAll(
                    () -> assertThrows(IllegalStateException.class, () -> engine.stepOut(outsideSubWorkflow),
                            "step-out outside a sub-workflow is refused"),
                    () -> assertThrows(IllegalStateException.class, () -> engine.stepOut(raised),
                            "step-out under a condition is refused"),
                    () -> assertEquals("sub.wait", waiting.state(), "the call rests on the callee's job state"),
                    () -> assertThrows(IllegalStateException.class, () -> argsEngine.stepOut(waiting),
                            "step-out from no link state is refused"));
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
                    () -> assertEquals("ent.fallback", fallback.status().state(), "unwinding lands on the fallback"),
                    () -> assertTrue(fallback.status().callStack().isEmpty(), "no frame survives the unwinding"),
                    () -> assertTrue(fallback.status().condition().isEmpty(), "the spent condition is cleared"),
                    () -> assertEquals(0, counters.attemptsOf(RetryRule.RETRY_ONCE, "inner.choose"),
                            "the unwound retry count is dropped"),
                    () -> assertEquals(0, counters.attemptsOf(RetryRule.CONDITION_BUDGET, "outer.recover"),
                            "the unwound condition count is dropped"),
                    () -> assertEquals(1, counters.attemptsOf(RetryRule.OPEN_PR, "ent.invoke"),
                            "the top-level retry count is kept"),
                    () -> assertNotEquals("outer.recover", fallback.status().state(),
                            "the fallback is no caller state"));
        }

        @Test
        @DisplayName("Should refuse a fallback state the top-level unit does not have")
        void shouldRefuseUnknownFallback() {
            var engine = nestedEngine();
            var choosing = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            assertThrows(IllegalArgumentException.class, () -> engine.unwindToFallback(choosing, "outer.recover"),
                    "a fallback the top-level unit lacks is refused");
        }
    }

    @Nested
    @DisplayName("CallStack value")
    class CallStackValue {

        private final CallFrame frame = new CallFrame("plan", "execute.invoke", "sub.args", "main",
                Map.of("mode", new CallValue.EnumValue("strict")), null, null, T0.toString());

        @Test
        @DisplayName("Should push to five frames, the phase level adding one, and refuse a sixth")
        void shouldBoundDepth() {
            var full = CallStack.empty().push(frame).push(frame).push(frame).push(frame).push(frame);
            var sixFrames = List.of(frame, frame, frame, frame, frame, frame);

            assertAll(
                    () -> assertEquals(5, CallStack.MAX_DEPTH, "the phase level adds one frame to four"),
                    () -> assertEquals(CallStack.MAX_DEPTH, full.depth(), "five pushes reach the maximum depth"),
                    () -> assertThrows(IllegalStateException.class, () -> full.push(frame),
                            "a sixth push is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> new CallStack(sixFrames),
                            "a stored stack of six frames is refused"));
        }

        @Test
        @DisplayName("Should pop exactly one frame, unwind every frame top first, and refuse to pop an empty stack")
        void shouldPopAndUnwind() {
            var other = new CallFrame("sub.args", "sub.wait", "sub.commit", "main", Map.of(), null, null, T0.toString());
            var stack = CallStack.empty().push(frame).push(other);

            var popped = stack.pop();
            var empty = CallStack.empty();

            assertAll(
                    () -> assertEquals(other, popped.frame(), "popping returns the top frame"),
                    () -> assertEquals(List.of(frame), popped.remaining().frames(), "the remaining stack is shorter"),
                    () -> assertEquals(List.of(other, frame), stack.unwound(), "unwinding lists frames top first"),
                    () -> assertEquals(other, stack.top().orElseThrow(), "the top frame is the last pushed"),
                    () -> assertTrue(empty.top().isEmpty(), "an empty stack has no top frame"),
                    () -> assertThrows(IllegalStateException.class, empty::pop, "popping an empty stack is refused"));
        }

        @Test
        @DisplayName("Should convert the entry and every kind of bound argument to the store and back")
        void shouldRoundTripThroughStore() {
            var args = new LinkedHashMap<String, CallValue>();
            args.put("mode", new CallValue.EnumValue("strict"));
            args.put("rounds", new CallValue.IntValue(Generators.integers(1, 5).next()));
            args.put("targets", new CallValue.EnumList(List.of("origin", "mirror")));
            var stack = CallStack.empty().push(new CallFrame("plan", "execute.invoke", "sub.args", "repair",
                    args, CommandId.random(),
                    CorrelationId.random(), T0.toString()));

            var stored = stack.toStore();

            assertAll(
                    () -> assertEquals("repair", stored.getFirst().entry(), "the stored frame keeps the entry"),
                    () -> assertEquals(stack, CallStack.fromStore(stored), "the store round trip returns the stack"));
        }

        @Test
        @DisplayName("Should refuse a stored integer argument outside the range of a call value")
        void shouldRefuseOversizedInteger() {
            var stored = new WorkflowStatus.CallFrame("plan", "execute.invoke", "sub.args", "main",
                    Map.of("rounds", new BoundArg.IntegerArg(Long.MAX_VALUE)), Map.of(),
                    CommandId.random(),
                    CorrelationId.random(), T0);
            var storedFrames = List.of(stored);

            assertThrows(ArithmeticException.class, () -> CallStack.fromStore(storedFrames),
                    "an integer beyond the call value range is refused");
        }

        @Test
        @DisplayName("Should read the frames of a store status the engine resumes")
        void shouldReadFramesOfStatus() {
            var status = planStatus(EXECUTE, "sub.work").toBuilder()
                    .callStack(List.of(frame("plan", "execute.commit", "sub.commit"))).build();

            var stack = CallStack.fromStore(status.callStack());

            var top = stack.top().orElseThrow();
            assertAll(
                    () -> assertEquals("sub.commit", top.subWorkflowId(), "the resumed frame names its sub-workflow"),
                    () -> assertEquals(EngineFixtures.MAIN_ENTRY, top.entry(), "the resumed frame keeps its entry"));
        }
    }
}
