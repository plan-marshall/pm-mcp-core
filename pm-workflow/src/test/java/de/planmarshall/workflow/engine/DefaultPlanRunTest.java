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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.ast.StateNode;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.InvariantFixtures;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;
import de.planmarshall.workflow.verify.WorkflowValidator;

/**
 * The fixture plan unit of {@code unit-fixtures/defaults/} run end to end through the engine (PM-IMPL-5, PM-WF-16):
 * the plan unit with the other eleven units and the entity unit as its catalog, the scripted primitives of
 * {@link DefaultPlanPrimitives}, and the ports of {@link EngineFixtures}. A driver answers every rest with the event the
 * resting state awaits until the plan ends, and records the phase unit on the bottom frame of every status the run
 * passes, with the entry of its call, and the deepest call stack.
 * <p>
 * Each lane runs from {@code start} to the terminal {@code completed}, and the rework loop-back, the finalize re-entry
 * and the hand-back after an exhausted gate each run to their terminal. Every delivery terminal ends with the phase
 * {@code completed}. The corpus nests at most three frames (execute, the Q-Gate remediation, the commit), which the
 * rework run reaches through the remediation's fix and pops.
 */
// Every datum in this class is a contract: unit, state, entry and primitive ids and the outcome wire names of the corpus.
@EnableGeneratorController
@DisplayName("Default plan run")
class DefaultPlanRunTest {

    private static final String PLAN = "plan";

    private static final String COMPLETED = "completed";

    private static final String NEXT_TASK = "test.next-task";

    private static final String DELIVERABLE_DONE = "deliverable_done";

    private static final String DELIVER = "test.deliver";

    /** The rests a run may take before it is taken to loop. */
    private static final int MAX_RESTS = 200;

    /** The deepest call stack of the corpus: execute, the Q-Gate remediation and the commit it calls. */
    private static final int CORPUS_DEPTH = 3;

    private final EngineFixtures fixtures = new EngineFixtures();

    private final DefaultPlanPrimitives primitives = new DefaultPlanPrimitives();

    private final Map<String, WorkflowDefinition> units = InvariantFixtures.passingUnits(UnitOrigin.DEFAULT);

    private final Map<String, List<String>> fates = new LinkedHashMap<>();

    @ParameterizedTest(name = "lane {0}")
    @MethodSource("lanes")
    @DisplayName("each lane (contract outcomes of test.lane) runs from start to completed through its phases")
    void laneRunsToCompleted(String lane, List<String> entered, int deepest) {
        primitives.script(NEXT_TASK, DELIVERABLE_DONE);

        var run = run(lane);

        assertAll(
                () -> assertEquals(entered, run.entered(), "the lane enters its phases at their entries"),
                () -> assertDelivered(run, COMPLETED),
                () -> assertEquals(deepest, run.deepest(), "the lane reaches its deepest call stack"),
                () -> assertEquals(List.of(Optional.of(run.scopeId())), primitives.scopes("test.lane"),
                        "the lane is chosen once, for the plan's scope"));
    }

    static Stream<Arguments> lanes() {
        return Stream.of(
                Arguments.of("deep", List.of("phase-1-init@start", "phase-2-refine@start", "phase-3-outline@start",
                        "phase-4-plan@start", "phase-5-execute@start", "phase-6-finalize@start"), 2),
                Arguments.of("light", List.of("phase-1-init@start", "phase-3-outline@light", "phase-4-plan@start",
                        "phase-5-execute@start", "phase-6-finalize@start"), 2),
                Arguments.of("land", List.of("phase-1-init@start", "phase-5-execute@land",
                        "phase-6-finalize@start"), 2),
                Arguments.of("adopt", List.of("phase-1-init@start", "phase-6-finalize@adopt"), 1));
    }

    @Test
    @DisplayName("the rework loop-back: the remediation of execute fixes, then loops back, and outline runs again at"
            + " rework")
    void reworkLoopBackRunsToCompleted() {
        primitives.script(NEXT_TASK, DELIVERABLE_DONE, DELIVERABLE_DONE)
                .script("test.evaluate-decision", "fix", "loop_back");
        fates.put("execute.verify-sweep", new ArrayList<>(List.of("failed", "succeeded")));

        var run = run("deep");

        assertAll(
                () -> assertEquals(List.of("phase-1-init@start", "phase-2-refine@start", "phase-3-outline@start",
                                "phase-4-plan@start", "phase-5-execute@start", "phase-3-outline@rework",
                                "phase-4-plan@start", "phase-5-execute@start", "phase-6-finalize@start"), run.entered(),
                        "outline is entered again at its rework entry"),
                () -> assertDelivered(run, COMPLETED),
                () -> assertEquals(CORPUS_DEPTH, run.deepest(), "the fix reaches the deepest call stack"));
    }

    @Test
    @DisplayName("the finalize re-entry: finalize ends reentry, the gate retries and execute runs again at reentry")
    void finalizeReentryRunsToCompleted() {
        primitives.script(NEXT_TASK, DELIVERABLE_DONE, DELIVERABLE_DONE).script(DELIVER, "reentry", COMPLETED);

        var run = run("deep");

        assertAll(
                () -> assertEquals(List.of("phase-1-init@start", "phase-2-refine@start", "phase-3-outline@start",
                                "phase-4-plan@start", "phase-5-execute@start", "phase-6-finalize@start",
                                "phase-5-execute@reentry", "phase-6-finalize@start"), run.entered(),
                        "execute is entered again at its reentry entry"),
                () -> assertDelivered(run, COMPLETED),
                () -> assertEquals(2, run.deepest(), "the re-entry nests the commit within a phase"));
    }

    @Test
    @DisplayName("the hand-back: the re-entry gate exhausts and finalize hands the plan back at hand_back")
    void exhaustedGateHandsBack() {
        primitives.script(NEXT_TASK, DELIVERABLE_DONE).script(DELIVER, "reentry", "handed_back")
                .script("test.capped", "exhausted");

        var run = run("deep");

        assertAll(
                () -> assertEquals(List.of("phase-1-init@start", "phase-2-refine@start", "phase-3-outline@start",
                                "phase-4-plan@start", "phase-5-execute@start", "phase-6-finalize@start",
                                "phase-6-finalize@hand_back"), run.entered(),
                        "finalize is entered again at its hand_back entry"),
                () -> assertDelivered(run, "handed_back"),
                () -> assertEquals(OutcomeClass.FAILURE, run.ended().outcomeClass(),
                        "the hand-back reports the class failure"));
    }

    /** Asserts that the run ended in the delivery terminal of the id with the phase completed and no stack left. */
    private static void assertDelivered(Run run, String terminal) {
        var status = run.ended().status();
        assertAll(
                () -> assertEquals(terminal, status.state(), "the run ends in its delivery terminal"),
                () -> assertEquals(terminal, run.ended().outcomeId(), "the run ends with the terminal's outcome"),
                () -> assertEquals(COMPLETED, status.phase(), "a delivery terminal ends with phase completed"),
                () -> assertTrue(status.callStack().isEmpty(), "every frame is popped at the end of the run"));
    }

    /**
     * Runs the plan unit from its start state along a lane, answering every rest with the event it awaits.
     *
     * @param lane the outcome of {@code test.lane} at {@code init.ready}
     * @return what the run entered and how it ended
     */
    private Run run(String lane) {
        primitives.script("test.lane", lane);
        var scopeId = "plan-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
        var recorder = new Recorder();
        var engine = engine(recorder);
        var step = engine.start(JournalScopeType.PLAN, scopeId);
        for (var rests = 0; rests < MAX_RESTS; rests++) {
            recorder.observe(step.status());
            if (step instanceof EngineStep.Ended ended) {
                return new Run(scopeId, List.copyOf(recorder.entered), recorder.deepest, ended);
            }
            var resting = assertInstanceOf(EngineStep.Resting.class, step, "a run either rests or ends");
            step = engine.advance(resting.status(), answer(engine, resting.status()));
        }
        return fail("the run of lane " + lane + " did not end within " + MAX_RESTS + " rests");
    }

    /** @return the engine of the fixture plan unit over its catalog, the scripted primitives and the fixture ports */
    private WorkflowEngine engine(Recorder recorder) {
        var callees = units.values().stream().filter(unit -> !PLAN.equals(unit.id())).toList();
        var catalog = InvariantFixtures.catalog(callees);
        var validator = new WorkflowValidator(primitives.registry(), List.of(), InvariantFixtures.entityKinds(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), catalog);
        return new WorkflowEngine(units.get(PLAN), catalog, validator, primitives.registry(),
                (primitive, status) -> {
                    recorder.observe(status);
                    return new EngineFixtures.NoParams();
                }, fixtures.ports());
    }

    /** @return the event the resting state of the status awaits */
    private EngineEvent answer(WorkflowEngine engine, WorkflowStatus status) {
        var wait = engine.restingWait(status).orElseThrow();
        var state = stateOf(wait.stateId());
        return switch (wait.awaitedEvent()) {
            case JOB_SETTLED -> new EngineEvent.JobSettled(wait.jobIds().iterator().next(), fate(wait.stateId()));
            case TASK_SETTLED -> tasksSettled(wait, state, status);
            case QUESTION_ANSWER -> questionResolved(state);
            case TIME -> new EngineEvent.TimeElapsed(fixtures.clock.tick(Duration.ofMinutes(1)));
            case OPERATOR_ACT -> new EngineEvent.OperatorAct(wait.stateId(), assertInstanceOf(
                    StateBehavior.InteractiveLinks.class, state.behavior()).links().getFirst().idPattern());
            default -> fail("the state " + wait.stateId() + " awaits " + wait.awaitedEvent());
        };
    }

    /** @return the next scripted fate of the job state, {@code succeeded} when none is scripted */
    private String fate(String stateId) {
        var scripted = fates.getOrDefault(stateId, List.of());
        return scripted.isEmpty() ? "succeeded" : fates.get(stateId).removeFirst();
    }

    /** @return the settlement of the state's tasks, evaluated by the return evaluation of its entity kind */
    private EngineEvent tasksSettled(RestingWait wait, StateNode state, WorkflowStatus status) {
        var job = assertInstanceOf(StateBehavior.ModelJob.class, state.behavior());
        var kind = InvariantFixtures.entityKinds().find(job.entityKind()).orElseThrow();
        var outcomes = new LinkedHashMap<String, String>();
        wait.taskEntities().forEach(entity -> outcomes.put(entity, "submitted"));
        return new EngineEvent.TasksSettled(outcomes,
                primitives.evaluate(kind.returnEvaluationPrimitiveId(), Optional.ofNullable(status.scopeId())));
    }

    /** @return the resolution of the question with the first outcome its state routes */
    private EngineEvent questionResolved(StateNode state) {
        var outcome = assertInstanceOf(StateBehavior.QuestionWait.class, state.behavior()).transitions().getFirst()
                .outcomeName();
        fixtures.questionReader.answer = new QuestionRead.Resolved(outcome);
        return new EngineEvent.QuestionResolved("Q-001", outcome);
    }

    private StateNode stateOf(String stateId) {
        return units.values().stream().map(unit -> unit.states().get(stateId)).filter(Objects::nonNull)
                .findFirst().orElseThrow();
    }

    /**
     * What a run entered and how it ended.
     *
     * @param scopeId the plan the run belongs to
     * @param entered every phase unit on the bottom frame, as {@code unit@entry}, once per call, in order
     * @param deepest the deepest call stack the run passed
     * @param ended   the end of the run
     */
    private record Run(String scopeId, List<String> entered, int deepest, EngineStep.Ended ended) {
    }

    /** Records the bottom frame of every status a run passes and the deepest call stack. */
    private static final class Recorder {

        private final List<String> entered = new ArrayList<>();

        private String bottom = "";

        private int deepest;

        void observe(WorkflowStatus status) {
            var stack = status.callStack();
            deepest = Math.max(deepest, stack.size());
            if (stack.isEmpty()) {
                bottom = "";
                return;
            }
            var frame = stack.getFirst();
            var call = frame.callerStateId() + ">" + frame.subworkflowId() + "@" + frame.entry();
            if (!call.equals(bottom)) {
                entered.add(frame.subworkflowId() + "@" + frame.entry());
                bottom = call;
            }
        }
    }
}
