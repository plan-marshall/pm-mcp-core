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

import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.callingPlan;
import static de.planmarshall.workflow.engine.EngineFixtures.parse;
import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatus.BoundArg;
import de.planmarshall.workflow.ast.InputType;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.plan.InstanceUnits;
import de.planmarshall.workflow.verify.ConfigKeyRegistry;
import de.planmarshall.workflow.verify.ConfigKeyRegistry.ConfigKey;
import de.planmarshall.workflow.verify.WorkflowCatalog;

/**
 * The call-time bind of PLAN-V2-15 as PLAN-V2-157 finishes it: a call resolves every input of the callee in the order of
 * ADR {@code workflow-dsl/014}, resolves a slot reference to the slot's value and consumes it, validates the value and
 * refuses an unresolvable or invalid one with {@code precondition_unmet}, and records the provenance of every bound
 * value in the frame, which survives the store.
 * <p>
 * The units are written for the test; every value is generated where it is not the contract (a Conventional Commit
 * header, a JSON array text, a fixture path).
 */
@EnableGeneratorController
@DisplayName("Input binding: the call-time bind")
class InputBindingTest {

    /** The conventional-commit type of a generated message: the grammar is the contract. */
    private static final String COMMIT_TYPE = "fix(engine): ";

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

    /** A callee whose one input is a commit message, and which rests in a job state so its frame stays on the stack. */
    private static final String SUB_COMMIT = """
            ---
            name: sub.commit
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            input: message
            input_type: commit_message[1..2000]
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.commit version 1 format 1
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

    /** A callee whose one input is a path list. */
    private static final String SUB_FILES = """
            ---
            name: sub.files
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            input: files
            input_type: list<path>[0..500]
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.files version 1 format 1
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

    /** A callee with the four sources of the resolution order, the fallback key included. */
    private static final String SUB_OPTIONS = """
            ---
            name: sub.opts
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            reads_config: test.commit.mode
            input: bound
            input_type: enum[strict|lenient]
            input: from_config
            input_type: enum[step_or_fallback|auto_deliverable|interactive]
            input_fallback: test.commit.mode
            input: with_default
            input_type: enum[origin|mirror]
            input_default: origin
            input: optional_absent
            input_type: text[1..100]?
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.opts version 1 format 1
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

    /** A callee whose one input carries both a fallback key and a compiled default. */
    private static final String SUB_FALLBACK_DEFAULT = """
            ---
            name: sub.fallback
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            reads_config: test.commit.mode
            input: mode
            input_type: enum[step_or_fallback|auto_deliverable|interactive]
            input_default: auto_deliverable
            input_fallback: test.commit.mode
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.fallback version 1 format 1
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

    /** The configuration key the {@link #SUB_OPTIONS} input falls back to. */
    private static final ConfigKey MODE_KEY = new ConfigKey("test.commit.mode",
            new InputType.Enumeration(List.of("step_or_fallback", "auto_deliverable", "interactive")), true);

    /** A callee with a commit-message input and a path-list input. */
    private static final String SUB_TWO = """
            ---
            name: sub.two
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            input: first
            input_type: commit_message[1..2000]
            input: second
            input_type: list<path>[0..500]
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.two version 1 format 1
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

    /** A callee with one path input. */
    private static final String SUB_PATH = """
            ---
            name: sub.path
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            input: file
            input_type: path
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.path version 1 format 1
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

    /** A plan that fills the path slot from a task submission and binds it at the next call. */
    private static final String FILL_PATH_AND_CALL = """
            state execute.fill
                fills slot.file from file
                job llm change role reviewer
                    approved -> execute.invoke
                    lost -> handed_back

            state execute.invoke
                call sub.path(file = slot.file)
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A plan that fills the commit-message slot from a task submission and binds it at the next call. */
    private static final String FILL_AND_CALL = """
            state execute.fill
                fills slot.message from message
                job llm change role reviewer
                    approved -> execute.invoke
                    lost -> execute.invoke

            state execute.invoke
                call sub.commit(message = slot.message)
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A plan that fills the path-list slot from a task submission and binds it at the next call. */
    private static final String FILL_FILES_AND_CALL = """
            state execute.fill
                fills slot.files from files
                job llm change role reviewer
                    approved -> execute.invoke
                    lost -> handed_back

            state execute.invoke
                call sub.files(files = slot.files)
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A plan that binds the slot once, returns, and binds the same slot a second time. */
    private static final String FILL_AND_CALL_TWICE = """
            state execute.fill
                fills slot.message from message
                job llm change role reviewer
                    approved -> execute.invoke
                    lost -> handed_back

            state execute.invoke
                call sub.commit(message = slot.message)
                    done -> execute.invoke-again
                    stepped_out -> plan.return

            state execute.invoke-again
                call sub.commit(message = slot.message)
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A plan that fills the path-list slot, rests, and binds it in a later advance. */
    private static final String FILL_FILES_REST_AND_CALL = """
            state execute.fill
                fills slot.files from files
                job llm change role reviewer
                    approved -> execute.pause
                    lost -> handed_back

            state execute.pause
                link next proceed ()
                    do test.proceed
                        done -> execute.invoke
                        failed -> handed_back

            state execute.invoke
                call sub.files(files = slot.files)
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A plan that fills the slot, rests, and binds it in a later advance. */
    private static final String FILL_REST_AND_CALL = """
            state execute.fill
                fills slot.message from message
                job llm change role reviewer
                    approved -> execute.pause
                    lost -> handed_back

            state execute.pause
                link next proceed ()
                    do test.proceed
                        done -> execute.invoke
                        failed -> handed_back

            state execute.invoke
                call sub.commit(message = slot.message)
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A plan that binds the options callee with one literal input and lets the other three resolve. */
    private static final String CALL_OPTIONS = """
            state execute.invoke
                call sub.opts(bound = strict)
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A plan that makes two calls, one advance apart, both through {@link #SUB_MID}. */
    private static final String TWO_CALLS = """
            state execute.invoke
                call sub.mid()
                    done -> execute.invoke-again
                    stepped_out -> plan.return

            state execute.invoke-again
                call sub.mid()
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A callee that calls {@link #SUB_ARGS} with a literal, so one advance pushes two frames. */
    private static final String SUB_MID = """
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
                call sub.args(mode = strict, rounds = 2, targets = [origin, mirror])
                    done -> mid.done
                    stepped_out -> mid.stepped-out

            state mid.done terminal done success

            state mid.stepped-out terminal stepped_out stepped_out
            """;

    /**
     * A plan unit that declares one path-list slot and the states written for the test.
     */
    private static WorkflowDefinition fileListPlan(String states) {
        return parse("plan.pmwf", """
                ---
                name: plan
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                slot: files
                slot_type: list<path>[0..500]
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow plan version 1 format 1
                scope plan
                start %s

                %s%s%s""".formatted(firstState(states), states, EngineFixtures.PLAN_RETURN,
                EngineFixtures.PLAN_TERMINALS));
    }

    /** @return the identifier of the first state of a fixture source */
    private static String firstState(String states) {
        var start = states.indexOf("state ") + "state ".length();
        return states.substring(start, states.indexOf('\n', start)).strip();
    }

    /**
     * A plan unit that declares one slot of the commit-message type and the states written for the test.
     *
     * @param states the states of the plan
     * @return the tree of the unit
     */
    private static WorkflowDefinition slotPlan(String states) {
        return parse("plan.pmwf", """
                ---
                name: plan
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                slot: message
                slot_type: commit_message[1..2000]
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow plan version 1 format 1
                scope plan
                start %s

                %s%s%s""".formatted(firstState(states), states, EngineFixtures.PLAN_RETURN,
                EngineFixtures.PLAN_TERMINALS));
    }

    /**
     * A plan unit that declares the commit-message and the path-list slot and the states written for the test.
     *
     * @param states the states of the plan
     * @return the tree of the unit
     */
    private static WorkflowDefinition twoSlotPlan(String states) {
        return parse("plan.pmwf", """
                ---
                name: plan
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                slot: message
                slot_type: commit_message[1..2000]
                slot: files
                slot_type: list<path>[0..500]
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow plan version 1 format 1
                scope plan
                start %s

                %s%s%s""".formatted(firstState(states), states, EngineFixtures.PLAN_RETURN,
                EngineFixtures.PLAN_TERMINALS));
    }

    /**
     * A plan unit that declares one path slot and the states written for the test.
     *
     * @param states the states of the plan
     * @return the tree of the unit
     */
    private static WorkflowDefinition pathSlotPlan(String states) {
        return parse("plan.pmwf", """
                ---
                name: plan
                description: A unit written for the tests of pm-workflow.
                type: workflow
                icon: workflow.default
                slot: file
                slot_type: path
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow plan version 1 format 1
                scope plan
                start %s

                %s%s%s""".formatted(firstState(states), states, EngineFixtures.PLAN_RETURN,
                EngineFixtures.PLAN_TERMINALS));
    }

    private static String generatedMessage() {
        return COMMIT_TYPE + Generators.letterStrings(1, 40).next();
    }

    private static EngineEvent.TasksSettled submitted(String parameter, String value) {
        return new EngineEvent.TasksSettled(Map.of("change", "submitted"), Map.of("change", Map.of(parameter, value)),
                "approved");
    }

    private static Map<String, String> refusalFacts(EngineStep step) {
        var raised = assertInstanceOf(EngineStep.ConditionRaised.class, step, "the bind refuses the call");
        return Map.of("kind", raised.kind(), "facts", raised.facts());
    }

    /**
     * An engine over a clean unit whose version in force carries another unit, unverified: the shape a composed version
     * reaches the bind through.
     *
     * @param clean   the unit the engine is built and verified with
     * @param inForce the plan unit the version in force carries
     * @param callee  the called unit
     * @return the engine
     */
    private static WorkflowEngine versioned(WorkflowDefinition clean, WorkflowDefinition inForce,
            WorkflowDefinition callee) {
        var fixtures = new EngineFixtures();
        InstanceUnits units = createMock(InstanceUnits.class);
        expect(units.planUnit(1)).andReturn(Optional.of(inForce)).anyTimes();
        expect(units.composedUnit(1, callee.id())).andReturn(Optional.of(callee)).anyTimes();
        expect(units.subUnit(anyInt(), anyString())).andReturn(Optional.empty()).anyTimes();
        replay(units);
        return new WorkflowEngine(clean, WorkflowCatalog.of(callee), fixtures.validator(callee), fixtures.registry(),
                (primitive, status) -> new EngineFixtures.NoParams(), fixtures.instancePorts(null, units));
    }

    @Nested
    @DisplayName("The frame's identity")
    class FrameIdentity {

        private static WorkflowEngine argsEngine(EngineFixtures fixtures) {
            WorkflowDefinition callee = parse("sub.args.pmwf", SUB_ARGS);
            return fixtures.engine(
                    callingPlan("execute.invoke", """
                            state execute.invoke
                                call sub.args(mode = strict, rounds = 2, targets = [origin, mirror])
                                    done -> completed
                                    stepped_out -> plan.return
                            """), callee);
        }

        @Test
        @DisplayName("two engines with identically seeded generators push identical frames")
        void identicalSeedsPushIdenticalFrames() {
            var first = argsEngine(new EngineFixtures()).start(JournalScopeType.PLAN, PLAN_ID);
            var second = argsEngine(new EngineFixtures()).start(JournalScopeType.PLAN, PLAN_ID);

            var firstFrame = assertInstanceOf(EngineStep.Resting.class, first, "the first call rests").status()
                    .callStack().getFirst();
            var secondFrame = assertInstanceOf(EngineStep.Resting.class, second, "the second call rests").status()
                    .callStack().getFirst();
            assertAll(
                    () -> assertEquals(firstFrame.commandId(), secondFrame.commandId(),
                            "the same seed draws the same command id"),
                    () -> assertEquals(firstFrame.correlationId(), secondFrame.correlationId(),
                            "the same seed draws the same correlation id"));
        }

        @Test
        @DisplayName("the two frames of one advance share the correlation and differ in command")
        void framesOfOneAdvanceShareTheCorrelation() {
            var fixtures = new EngineFixtures();
            var engine = fixtures.engine(callingPlan("execute.invoke", TWO_CALLS), parse("sub.mid.pmwf", SUB_MID),
                    parse("sub.args.pmwf", SUB_ARGS));

            var status = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var outer = status.callStack().get(0);
            var inner = status.callStack().get(1);
            assertAll(
                    () -> assertEquals(2, status.callStack().size(), "one advance pushes two frames"),
                    () -> assertEquals(outer.correlationId(), inner.correlationId(),
                            "the frames of one advance share the correlation"),
                    () -> assertNotEquals(outer.commandId(), inner.commandId(),
                            "each pushed frame draws its own command id"));
        }

        @Test
        @DisplayName("the next advance draws another correlation")
        void theNextAdvanceDrawsAnotherCorrelation() {
            var fixtures = new EngineFixtures();
            var engine = fixtures.engine(callingPlan("execute.invoke", TWO_CALLS), parse("sub.mid.pmwf", SUB_MID),
                    parse("sub.args.pmwf", SUB_ARGS));

            var first = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            var job = first.activeJobBinding().orElseThrow().jobId();
            var second = engine.advance(first, new EngineEvent.JobSettled(job, "succeeded")).status();

            assertAll(
                    () -> assertEquals(2, second.callStack().size(), "the second advance pushes two frames"),
                    () -> assertNotEquals(first.callStack().getFirst().correlationId(),
                            second.callStack().getFirst().correlationId(),
                            "a later advance draws another correlation"));
        }

        @Test
        @DisplayName("the frame in the store keeps the provenance of a resumed advance")
        void theStoredFrameKeepsTheProvenance() {
            var fixtures = new EngineFixtures();
            var engine = fixtures.engine(callingPlan("execute.invoke",
                    """
                            state execute.invoke
                                call sub.args(mode = strict, rounds = 2, targets = [origin, mirror])
                                    done -> completed
                                    stepped_out -> plan.return
                            """), parse("sub.args.pmwf", SUB_ARGS));

            var status = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            var stored = status.callStack().getFirst();
            var roundTripped = CallStack.fromStore(List.of(stored)).toStore().getFirst();

            assertAll(
                    () -> assertEquals(Map.of("mode", new WorkflowStatus.CallFrame.ArgSource("literal", null),
                                    "rounds", new WorkflowStatus.CallFrame.ArgSource("literal", null),
                                    "targets", new WorkflowStatus.CallFrame.ArgSource("literal", null)),
                            stored.argSources(), "the frame names the provenance of every literal"),
                    () -> assertEquals(stored.argSources(), roundTripped.argSources(),
                            "the provenance survives the store round trip"));
        }
    }

    @Nested
    @DisplayName("The slot bind")
    class TheSlotBind {

        @Test
        @DisplayName("Should bind the filled slot into the frame and consume it")
        void shouldBindTheFilledSlot() {
            var fixtures = new EngineFixtures();
            var message = generatedMessage();
            var engine = fixtures.engine(slotPlan(FILL_AND_CALL), parse("sub.commit.pmwf", SUB_COMMIT));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var status = assertInstanceOf(EngineStep.Resting.class, engine.advance(atTask, submitted("message", message)),
                    "the call rests in the callee").status();

            var frame = status.callStack().getFirst();
            var bound = assertInstanceOf(BoundArg.StringArg.class, frame.boundArgs().get("message"),
                    "the callee input is bound");
            assertAll(
                    () -> assertEquals(message, bound.value(), "the frame carries the slot value itself"),
                    () -> assertEquals("task_submit", frame.argSources().get("message").source(),
                            "the frame names the task submission"),
                    () -> assertEquals("change", frame.argSources().get("message").sourceRef(),
                            "the frame names the submitting entity"),
                    () -> assertTrue(status.slots().isEmpty(), "the bound slot is consumed"));
        }

        @Test
        @DisplayName("Should store the slot under the declaring unit and its name")
        void shouldStoreTheSlotUnderTheDeclaringUnit() {
            var fixtures = new EngineFixtures();
            var message = generatedMessage();
            var engine = fixtures.engine(slotPlan(FILL_REST_AND_CALL),
                    parse("sub.commit.pmwf", SUB_COMMIT));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var paused = engine.advance(atTask, submitted("message", message)).status();

            assertAll(
                    () -> assertEquals(Set.of("plan:message"), paused.slots().keySet(),
                            "the slot key names the declaring unit"),
                    () -> assertEquals(message, paused.slots().get("plan:message").value(),
                            "the slot carries the submitted message"),
                    () -> assertEquals("task_submit", paused.slots().get("plan:message").source(),
                            "the slot records the task submission"));
        }

        @Test
        @DisplayName("Should refuse a call that binds an unfilled slot")
        void shouldRefuseAnUnfilledSlot() {
            var fixtures = new EngineFixtures();
            var engine = fixtures.engine(slotPlan(FILL_AND_CALL), parse("sub.commit.pmwf", SUB_COMMIT));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var step = engine.advance(atTask, new EngineEvent.TasksSettled(Map.of("change", "lost"), Map.of(), "lost"));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertEquals("precondition_unmet", facts.get("kind"), "the refusal raises the condition"),
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_unbound\""),
                            "the refusal names the unbound input"),
                    () -> assertTrue(facts.get("facts").contains("sub.commit.input.message"),
                            "the refusal names the qualified input"),
                    () -> assertTrue(step.status().callStack().isEmpty(), "the refused call pushes no frame"));
        }

        @Test
        @DisplayName("Should bind a slot filled before a restart in the resumed engine")
        void shouldBindASlotFilledBeforeARestart() {
            var message = generatedMessage();
            var before = new EngineFixtures().engine(slotPlan(FILL_REST_AND_CALL),
                    parse("sub.commit.pmwf", SUB_COMMIT));
            var atTask = before.start(JournalScopeType.PLAN, PLAN_ID).status();
            var paused = before.advance(atTask, submitted("message", message)).status();

            var resumed = new EngineFixtures().engine(slotPlan(FILL_REST_AND_CALL),
                    parse("sub.commit.pmwf", SUB_COMMIT));
            var status = assertInstanceOf(EngineStep.Resting.class,
                    resumed.advance(paused, new EngineEvent.OperatorAct("execute.pause", "proceed")),
                    "the resumed engine rests in the callee").status();

            var bound = assertInstanceOf(BoundArg.StringArg.class,
                    status.callStack().getFirst().boundArgs().get("message"), "the callee input is bound");
            assertAll(
                    () -> assertEquals(message, bound.value(), "the resumed bind carries the stored value"),
                    () -> assertEquals("task_submit", status.callStack().getFirst().argSources().get("message").source(),
                            "the resumed frame keeps the provenance"),
                    () -> assertTrue(status.slots().isEmpty(), "the resumed bind consumes the slot"));
        }

        @Test
        @DisplayName("Should refuse a filled path the staging set no longer holds")
        void shouldRefuseAStagingSetThatChanged() {
            var before = bindEngine(new EngineFixtures(), FILL_FILES_REST_AND_CALL, SUB_FILES,
                    Set.of("src/A.java"), Map.of());
            var atTask = before.start(JournalScopeType.PLAN, PLAN_ID).status();
            var paused = before.advance(atTask, submitted("files", "[\"src/A.java\"]")).status();
            var after = bindEngine(new EngineFixtures(), FILL_FILES_REST_AND_CALL, SUB_FILES, Set.of(), Map.of());

            var step = after.advance(paused, new EngineEvent.OperatorAct("execute.pause", "proceed"));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_refused\""),
                            "the changed set refuses the filled path"),
                    () -> assertTrue(facts.get("facts").contains("path.staging"),
                            "the refusal names the staging rule"));
        }

        @Test
        @DisplayName("Should refuse a path that is not in the staging set")
        void shouldRefuseAPathOutsideTheStagingSet() {
            var fixtures = new EngineFixtures();
            var engine = bindEngine(fixtures, FILL_FILES_AND_CALL, SUB_FILES, Set.of("src/B.java"), Map.of());
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var step = engine.advance(atTask, submitted("files", "[\"src/A.java\"]"));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertEquals("precondition_unmet", facts.get("kind"), "the refusal raises the condition"),
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_refused\""),
                            "the refusal names the refused input"),
                    () -> assertTrue(facts.get("facts").contains("path.staging"),
                            "the refusal names the staging rule"),
                    () -> assertTrue(step.status().callStack().isEmpty(), "the refused call pushes no frame"));
        }

        @Test
        @DisplayName("Should refuse an explicitly empty files list")
        void shouldRefuseAnEmptyFilesList() {
            var fixtures = new EngineFixtures();
            var engine = bindEngine(fixtures, FILL_FILES_AND_CALL, SUB_FILES, Set.of("src/A.java"), Map.of());
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var step = engine.advance(atTask, submitted("files", "[]"));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertEquals("precondition_unmet", facts.get("kind"), "the refusal raises the condition"),
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_refused\""),
                            "the empty list is refused at the bind"),
                    () -> assertTrue(facts.get("facts").contains("input.empty"),
                            "the refusal names the empty rule"));
        }

        @Test
        @DisplayName("Should refuse a second call that binds a consumed slot")
        void shouldRefuseAConsumedSlot() {
            // the version in force carries the two-read unit; Rule 6 refuses that shape, so the bind is the safety net here
            var engine = versioned(slotPlan(FILL_AND_CALL), slotPlan(FILL_AND_CALL_TWICE),
                    parse("sub.commit.pmwf", SUB_COMMIT));
            var message = generatedMessage();
            var atTask = engine.advance(EngineFixtures.planStatus("phase-1-init", "execute.fill", 1));
            var first = engine.advance(atTask.status(), submitted("message", message)).status();
            var job = first.activeJobBinding().orElseThrow().jobId();

            var step = engine.advance(first, new EngineEvent.JobSettled(job, "succeeded"));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertEquals("precondition_unmet", facts.get("kind"), "the second bind refuses"),
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_unbound\""),
                            "the consumed slot is no value"));
        }

        @Test
        @DisplayName("Should leave a filled slot unread when a later input of the call is refused")
        void shouldLeaveTheSlotFilledOnALaterRefusal() {
            var fixtures = new EngineFixtures();
            var message = generatedMessage();
            var callee = parse("sub.two.pmwf", SUB_TWO);
            var engine = new WorkflowEngine(twoSlotPlan("""
                    state execute.fill-message
                        fills slot.message from message
                        job llm change role reviewer
                            approved -> execute.fill-files
                            lost -> handed_back

                    state execute.fill-files
                        fills slot.files from files
                        job llm change role reviewer
                            approved -> execute.invoke
                            lost -> handed_back

                    state execute.invoke
                        call sub.two(first = slot.message, second = slot.files)
                            done -> completed
                            stepped_out -> plan.return
                    """), WorkflowCatalog.of(callee), fixtures.validator(callee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(),
                    fixtures.ports(EngineFixtures.bindingContext(Path.of("").toAbsolutePath(),
                            Set.of("src/B.java"), Map.of())));
            var atFirstTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            var atSecondTask = engine.advance(atFirstTask, submitted("message", message)).status();

            var step = engine.advance(atSecondTask, submitted("files", "[\"src/A.java\"]"));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_refused\""),
                            "the second input is refused at the bind"),
                    () -> assertTrue(facts.get("facts").contains("path.staging"),
                            "the refusal names the staging rule"),
                    () -> assertTrue(step.status().slots().containsKey("plan:message"),
                            "the first input's slot stays filled"),
                    () -> assertTrue(step.status().callStack().isEmpty(), "the refused call pushes no frame"));
        }

        private WorkflowEngine bindEngine(EngineFixtures fixtures, String planStates, String callee,
                Set<String> staging, Map<String, String> config) {
            var context = EngineFixtures.bindingContext(Path.of("").toAbsolutePath(), staging, config);
            var calleeUnit = parse("callee.pmwf", callee);
            return new WorkflowEngine(fileListPlan(planStates), WorkflowCatalog.of(calleeUnit),
                    fixtures.validator(calleeUnit), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(), fixtures.ports(context));
        }
    }

    @Nested
    @DisplayName("The resolution order")
    class TheResolutionOrder {

        private WorkflowEngine optionsEngine(Map<String, String> config) {
            var fixtures = new EngineFixtures();
            var callee = parse("sub.opts.pmwf", SUB_OPTIONS);
            var context = EngineFixtures.bindingContext(Path.of("").toAbsolutePath(), Set.of(), config);
            return new WorkflowEngine(callingPlan("execute.invoke", CALL_OPTIONS), WorkflowCatalog.of(callee),
                    fixtures.validator(ConfigKeyRegistry.of(List.of(MODE_KEY)), callee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(), fixtures.ports(context));
        }

        @Test
        @DisplayName("Should resolve the call site, the config value, the default and the optional absence")
        void shouldResolveInTheDocumentedOrder() {
            // The configuration value is one of the closed enum values: the type is the contract.
            var config = "step_or_fallback";
            var engine = optionsEngine(Map.of(MODE_KEY.key(), config));

            var status = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var frame = status.callStack().getFirst();
            var fromConfig = assertInstanceOf(BoundArg.StringArg.class, frame.boundArgs().get("from_config"),
                    "the fallback key resolves the input");
            var withDefault = assertInstanceOf(BoundArg.StringArg.class, frame.boundArgs().get("with_default"),
                    "the default resolves the input");
            assertAll(
                    () -> assertEquals(config, fromConfig.value(), "the configuration value is bound"),
                    () -> assertEquals("config", frame.argSources().get("from_config").source(),
                            "the frame names the configuration as source"),
                    () -> assertEquals("origin", withDefault.value(), "the compiled default is bound"),
                    () -> assertEquals("default", frame.argSources().get("with_default").source(),
                            "the frame names the default as source"),
                    () -> assertEquals("literal", frame.argSources().get("bound").source(),
                            "a call-site value is a literal"),
                    () -> assertFalse(frame.boundArgs().containsKey("optional_absent"),
                            "an absent optional input is not bound"));
        }

        @Test
        @DisplayName("Should take the compiled default when the fallback key has no value")
        void shouldTakeTheDefaultWhenTheFallbackIsAbsent() {
            var fixtures = new EngineFixtures();
            var callee = parse("sub.fallback.pmwf", SUB_FALLBACK_DEFAULT);
            var context = EngineFixtures.bindingContext(Path.of("").toAbsolutePath(), Set.of(), Map.of());
            var engine = new WorkflowEngine(callingPlan("execute.invoke", """
                    state execute.invoke
                        call sub.fallback()
                            done -> completed
                            stepped_out -> plan.return
                    """), WorkflowCatalog.of(callee),
                    fixtures.validator(ConfigKeyRegistry.of(List.of(MODE_KEY)), callee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(), fixtures.ports(context));

            var status = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var bound = assertInstanceOf(BoundArg.StringArg.class,
                    status.callStack().getFirst().boundArgs().get("mode"), "the default resolves the input");
            assertAll(
                    () -> assertEquals("auto_deliverable", bound.value(), "the compiled default is bound"),
                    () -> assertEquals("default", status.callStack().getFirst().argSources().get("mode").source(),
                            "the frame names the default as source"));
        }

        @Test
        @DisplayName("Should refuse a fallback whose key supplies nothing and no default can")
        void shouldRefuseAnUnsuppliedFallback() {
            var engine = optionsEngine(Map.of());

            var step = engine.start(JournalScopeType.PLAN, PLAN_ID);

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertEquals("precondition_unmet", facts.get("kind"), "the missing fallback refuses"),
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"missing_param\""),
                            "the absent input names the missing parameter"),
                    () -> assertTrue(facts.get("facts").contains("sub.opts.input.from_config"),
                            "the refusal names the fallback input"));
        }
    }

    /**
     * The bind's runtime validation of the arguments themselves: a call may carry a name the callee does not declare,
     * or omit a required one, when the version in force carries a plan unit the verifier never saw. The bind refuses
     * both before it pushes a frame; Rule 6 refuses the same two shapes of a unit the verifier does see.
     */
    @Nested
    @DisplayName("The argument validation at the bind")
    class TheArgumentValidation {

        private static final String PHASE = "phase-1-init";

        /** The clean plan of the versioned fixture: every argument declared, every required one bound. */
        private static final String CLEAN_INVOKE = """
                state execute.invoke
                    call sub.args(mode = strict, rounds = 2, targets = [origin, mirror])
                        done -> completed
                        stepped_out -> plan.return
                """;

        @Test
        @DisplayName("Should refuse an undeclared argument of a unit the verifier never saw")
        void shouldRefuseAnUndeclaredArgument() {
            var engine = versionedEngine("""
                    state execute.invoke
                        call sub.args(mode = strict, rounds = 2, targets = [origin, mirror], extra = 3)
                            done -> completed
                            stepped_out -> plan.return
                    """);

            var step = engine.advance(EngineFixtures.planStatus(PHASE, "execute.invoke", 1));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertEquals("precondition_unmet", facts.get("kind"),
                            "the undeclared argument refuses the call"),
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"undeclared_param\""),
                            "the refusal names the undeclared argument"),
                    () -> assertTrue(facts.get("facts").contains("sub.args.input.extra"),
                            "the refusal names the argument and its callee"),
                    () -> assertTrue(step.status().callStack().isEmpty(), "the refused call pushes no frame"));
        }

        @Test
        @DisplayName("Should refuse a call of that unit that omits a required input")
        void shouldRefuseAMissingRequiredInput() {
            var engine = versionedEngine("""
                    state execute.invoke
                        call sub.args(mode = strict)
                            done -> completed
                            stepped_out -> plan.return
                    """);

            var step = engine.advance(EngineFixtures.planStatus(PHASE, "execute.invoke", 1));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertEquals("precondition_unmet", facts.get("kind"),
                            "the missing required input refuses the call"),
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"missing_param\""),
                            "the refusal names the missing parameter"),
                    () -> assertTrue(facts.get("facts").contains("sub.args.input.rounds"),
                            "the refusal names the first missing input"),
                    () -> assertTrue(step.status().callStack().isEmpty(), "the refused call pushes no frame"));
        }

        @Test
        @DisplayName("matched control: the clean unit of the same version rests in the callee")
        void shouldBindTheCleanUnit() {
            var engine = versionedEngine(CLEAN_INVOKE);

            var step = engine.advance(EngineFixtures.planStatus(PHASE, "execute.invoke", 1));

            var status = assertInstanceOf(EngineStep.Resting.class, step, "the clean call rests in the callee")
                    .status();
            assertAll(
                    () -> assertEquals(1, status.callStack().size(), "the clean call pushes its frame"),
                    () -> assertEquals("sub.args", status.callStack().getFirst().subworkflowId(),
                            "the frame names the callee"));
        }

        /**
         * @param invokeStates the states of the plan unit the version in force carries
         * @return an engine over a clean unit whose version in force carries the given unit, unverified
         */
        private WorkflowEngine versionedEngine(String invokeStates) {
            var fixtures = new EngineFixtures();
            var callee = parse("sub.args.pmwf", SUB_ARGS);
            InstanceUnits units = createMock(InstanceUnits.class);
            expect(units.planUnit(1)).andReturn(Optional.of(callingPlan("execute.invoke", invokeStates))).anyTimes();
            expect(units.composedUnit(1, callee.id())).andReturn(Optional.of(callee)).anyTimes();
            expect(units.subUnit(anyInt(), anyString())).andReturn(Optional.empty()).anyTimes();
            replay(units);
            return new WorkflowEngine(callingPlan("execute.invoke", CLEAN_INVOKE), WorkflowCatalog.of(callee),
                    fixtures.validator(callee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(), fixtures.instancePorts(null, units));
        }
    }

    /** The bind of a scope whose engine carries no binding context: a fallback key cannot be read. */
    @Nested
    @DisplayName("The absent binding context")
    class TheAbsentBindingContext {

        @Test
        @DisplayName("Should refuse a fallback input when no binding context is mounted")
        void shouldRefuseAFallbackWithoutABindingContext() {
            var fixtures = new EngineFixtures();
            var callee = parse("sub.opts.pmwf", SUB_OPTIONS);
            var engine = new WorkflowEngine(callingPlan("execute.invoke", CALL_OPTIONS), WorkflowCatalog.of(callee),
                    fixtures.validator(ConfigKeyRegistry.of(List.of(MODE_KEY)), callee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(), fixtures.ports());

            var step = engine.start(JournalScopeType.PLAN, PLAN_ID);

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertEquals("precondition_unmet", facts.get("kind"),
                            "the absent context refuses the call"),
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_refused\""),
                            "the refusal names the refused input"),
                    () -> assertTrue(facts.get("facts").contains("input.no-context"),
                            "the refusal names the missing binding context"));
        }

        @Test
        @DisplayName("Should bind the compiled default when no context is mounted")
        void shouldBindTheDefaultWithoutABindingContext() {
            var fixtures = new EngineFixtures();
            var callee = parse("sub.fallback.pmwf", SUB_FALLBACK_DEFAULT);
            var engine = new WorkflowEngine(callingPlan("execute.invoke", """
                    state execute.invoke
                        call sub.fallback()
                            done -> completed
                            stepped_out -> plan.return
                    """), WorkflowCatalog.of(callee),
                    fixtures.validator(ConfigKeyRegistry.of(List.of(MODE_KEY)), callee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(), fixtures.ports());

            var status = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var bound = assertInstanceOf(BoundArg.StringArg.class,
                    status.callStack().getFirst().boundArgs().get("mode"), "the default resolves the input");
            assertEquals("auto_deliverable", bound.value(), "the default needs no binding context");
        }

        @Test
        @DisplayName("Should refuse a path input when no binding context is mounted")
        void shouldRefuseAPathWithoutABindingContext() {
            var fixtures = new EngineFixtures();
            var callee = parse("sub.path.pmwf", SUB_PATH);
            var engine = new WorkflowEngine(pathSlotPlan(FILL_PATH_AND_CALL), WorkflowCatalog.of(callee),
                    fixtures.validator(callee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(), fixtures.ports());
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var step = engine.advance(atTask, submitted("file", "src/A.java"));

            var facts = refusalFacts(step);
            assertAll(
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_refused\""),
                            "the path is refused without a context"),
                    () -> assertTrue(facts.get("facts").contains("path.no-context"),
                            "the refusal names the missing binding context"));
        }
    }

    /** The bind's confinement of a path: a symlink that leaves the worktree is refused before the frame is pushed. */
    @Nested
    @DisplayName("The confinement of a bound path")
    class TheConfinement {

        @TempDir
        Path root;

        @Test
        @DisplayName("Should refuse a path that leaves the worktree through a directory symlink")
        void shouldRefuseADirectorySymlink() throws IOException {
            var worktree = Files.createDirectories(root.resolve("worktree"));
            var secret = Files.writeString(Files.createDirectories(root.resolve("outside")).resolve("secret.txt"),
                    "secret");
            Files.createSymbolicLink(worktree.resolve("link"), secret.getParent());

            var step = bind(worktree, "link/secret.txt");

            assertRefusedWith(step, "path.confinement");
        }

        @Test
        @DisplayName("Should refuse a path that leaves the worktree through a file symlink")
        void shouldRefuseAFileSymlink() throws IOException {
            var worktree = Files.createDirectories(root.resolve("worktree"));
            var secret = Files.writeString(Files.createDirectories(root.resolve("outside")).resolve("secret.txt"),
                    "secret");
            Files.createSymbolicLink(worktree.resolve("file-out"), secret);

            var step = bind(worktree, "file-out");

            assertRefusedWith(step, "path.confinement");
        }

        private EngineStep bind(Path worktree, String path) {
            var fixtures = new EngineFixtures();
            var callee = parse("sub.path.pmwf", SUB_PATH);
            var engine = new WorkflowEngine(pathSlotPlan(FILL_PATH_AND_CALL), WorkflowCatalog.of(callee),
                    fixtures.validator(callee), fixtures.registry(),
                    (primitive, status) -> new EngineFixtures.NoParams(),
                    fixtures.ports(EngineFixtures.bindingContext(worktree, Set.of(path), Map.of())));
            var atTask = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
            return engine.advance(atTask, submitted("file", path));
        }

        private void assertRefusedWith(EngineStep step, String rule) {
            var facts = refusalFacts(step);
            assertAll(
                    () -> assertTrue(facts.get("facts").contains("\"reason\":\"input_refused\""),
                            "the path is refused at the bind"),
                    () -> assertTrue(facts.get("facts").contains(rule), "the refusal names the confinement rule"),
                    () -> assertTrue(step.status().callStack().isEmpty(), "the refused call pushes no frame"));
        }
    }
}
