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

import static de.planmarshall.workflow.engine.EngineFixtures.CHANGE_KIND;
import static de.planmarshall.workflow.engine.EngineFixtures.EXECUTE;
import static de.planmarshall.workflow.engine.EngineFixtures.OTHER_FATES;
import static de.planmarshall.workflow.engine.EngineFixtures.PLAN_ID;
import static de.planmarshall.workflow.engine.EngineFixtures.parse;
import static de.planmarshall.workflow.engine.EngineFixtures.plan;
import static de.planmarshall.workflow.engine.EngineFixtures.planStatus;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import de.cuioss.test.generator.Generators;
import de.planmarshall.core.spi.Provenance.Trigger;
import de.planmarshall.core.spi.QuestionRecordReader.QuestionRead;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import org.jspecify.annotations.Nullable;

/**
 * The fixtures of the command dispatch tests: units written for the test, a recording parameter binder, and one way
 * to drive the engine through each cause of an advance. A unit chains three {@code auto} primitives; one plan rests in
 * each state an {@link EngineEvent} resumes and then runs a primitive; an entity unit calls, through a second
 * sub-workflow, a sub-workflow that rests on a link, for the step-out and the fallback. The primitives, the ports and the static helpers come from
 * {@link EngineFixtures}, which this class composes and does not change.
 * <p>
 * One instance serves one test: the binder keeps every record it returned, in order.
 */
// Every datum in this class is a contract: the DSL documents, the state and link ids, and the wire names they bind.
final class CommandDispatchFixtures {

    /** The first state of {@link #chainUnit()}. */
    static final String FIRST_STATE = "execute.one";

    /** The operator wait of the operator plan. */
    static final String HOLD_STATE = "execute.hold";

    /** The link of {@link #HOLD_STATE}. */
    static final String PROCEED_LINK = "proceed";

    /** The state of the top-level entity unit that a step-out and a fallback enter. */
    static final String ENTITY_FALLBACK = "ent.fallback";

    /** The {@code auto} state every resumed plan runs after the state that waited. */
    private static final String AFTER = """

            state execute.after
                auto test.step
                    done -> completed
                    failed -> handed_back
            """;

    /** Three {@code auto} states in a row, each running another primitive. */
    private static final String CHAIN_STATES = """
            state execute.one
                auto test.step
                    done -> execute.two
                    failed -> handed_back

            state execute.two
                auto test.mark
                    done -> execute.three
                    failed -> handed_back

            state execute.three
                auto test.proceed
                    done -> completed
                    failed -> handed_back
            """;

    /** A job state that waits for the job the starter starts. */
    private static final String JOB_STATES = """
            state execute.run
                job test.fate
                    succeeded -> execute.after
                    killed -> handed_back
            %s""".formatted(OTHER_FATES) + AFTER;

    /** A task state of the entity kind {@code change}. */
    private static final String TASK_STATES = """
            state execute.review
                job llm change role reviewer
                    approved -> execute.after
                    lost -> handed_back
            """ + AFTER;

    /** A question state of the class {@code clarification}. */
    private static final String QUESTION_STATES = """
            state execute.ask
                question clarification
                    answered -> execute.after
            """ + AFTER;

    /** An operator wait whose link runs {@code test.proceed}. */
    private static final String OPERATOR_STATES = """
            state execute.hold
                link next proceed ()
                    do test.proceed
                        done -> execute.after
                        failed -> handed_back
            """ + AFTER;

    /** A composite job state that runs {@code test.backoff} when time passed. */
    private static final String TIME_STATES = """
            state execute.backoff
                composite_job test.backoff
                    succeeded -> execute.after
                    failed -> handed_back
                    cancelled -> handed_back
            """ + AFTER;

    /**
     * An entity unit that calls {@code sub.outer}. Rule 6 admits only a resting state as the target of an entity's
     * {@code stepped_out} route, so the {@code auto} state {@link #ENTITY_FALLBACK} is reached by the {@code failed}
     * outcome {@code sub.outer} returns when the step-out of {@code sub.choose} ends it in its failure terminal.
     */
    private static final String ENTITY_SOURCE = """
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
                    failed -> ent.fallback
                    stepped_out -> ent.hold

            state ent.fallback
                auto test.step
                    done -> ent.done
                    failed -> ent.done

            state ent.hold
                job test.build
                    succeeded -> ent.done
                    failed -> ent.done
                    cancelled -> ent.done

            state ent.done terminal done success
            """;

    /** A sub-workflow that calls {@code sub.choose} and ends in its failure terminal when that is stepped out of. */
    private static final String OUTER_SOURCE = """
            ---
            name: sub.outer
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            exit: failed
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.outer version 1 format 1
            scope subworkflow
            entry main -> outer.invoke

            state outer.invoke
                call sub.choose
                    done -> outer.done
                    stepped_out -> outer.failed

            state outer.done terminal done success

            state outer.failed terminal failed failure
            """;

    /** A sub-workflow that rests on a link state. */
    private static final String SUB_SOURCE = """
            ---
            name: sub.choose
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.choose version 1 format 1
            scope subworkflow
            entry main -> choose.wait

            state choose.wait
                skill pm/test/choose
                link next proceed ()
                    do test.proceed
                        done -> choose.done
                        failed -> choose.stepped-out

            state choose.done terminal done success

            state choose.stepped-out terminal stepped_out stepped_out
            """;

    /** The fixtures whose primitives, ports and helpers the engine is assembled from. */
    final EngineFixtures fixtures = new EngineFixtures();

    /** The records the binder returned, in the order it returned them. */
    final List<Record> boundRecords = new ArrayList<>();

    /**
     * What driving the engine through one cause produced: the step, and the reference the provenance names.
     *
     * @param step      the step the advance ended in
     * @param reference the reference of the cause; {@code null} when the cause names none
     */
    record Driven(EngineStep step, @Nullable String reference) {
    }

    /** @return the plan whose start state {@link #FIRST_STATE} chains three {@code auto} primitives to its end */
    static WorkflowDefinition chainUnit() {
        return plan(FIRST_STATE, CHAIN_STATES);
    }

    /**
     * Assembles an engine over the primitives and ports of {@link EngineFixtures} with the recording binder.
     *
     * @param unit    the top-level unit
     * @param callees the sub-workflows the unit calls
     * @return the engine
     */
    WorkflowEngine engine(WorkflowDefinition unit, WorkflowDefinition... callees) {
        return new WorkflowEngine(unit, WorkflowCatalog.of(callees), fixtures.validator(callees), fixtures.registry(),
                recordingBinder(), fixtures.ports());
    }

    /**
     * Drives the engine through the entry point or the event of one cause; every way runs at least one primitive.
     *
     * @param trigger the cause of the advance
     * @return the step and the reference the provenance of the cause names
     */
    Driven drive(Trigger trigger) {
        return switch (trigger) {
            case START -> new Driven(engine(chainUnit()).start(JournalScopeType.PLAN, PLAN_ID), null);
            case RESUME -> new Driven(engine(chainUnit()).advance(planStatus(EXECUTE, FIRST_STATE)), null);
            case JOB_SETTLED -> {
                var engine = engine(plan("execute.run", JOB_STATES));
                var resting = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
                var jobId = resting.activeJobBinding().orElseThrow().jobId();
                yield new Driven(engine.advance(resting, new EngineEvent.JobSettled(jobId, "succeeded")), jobId);
            }
            case TASKS_SETTLED -> {
                var engine = engine(plan("execute.review", TASK_STATES));
                var resting = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
                yield new Driven(engine.advance(resting, tasksSettled()), null);
            }
            case QUESTION_RESOLVED -> {
                var engine = engine(plan("execute.ask", QUESTION_STATES));
                var resting = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
                var resolved = questionResolved();
                fixtures.answerQuestion(new QuestionRead.Resolved(resolved.outcome()));
                yield new Driven(engine.advance(resting, resolved), resolved.questionId());
            }
            case OPERATOR_ACT -> {
                var engine = engine(plan(HOLD_STATE, OPERATOR_STATES));
                var resting = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
                yield new Driven(engine.advance(resting, operatorAct()), HOLD_STATE + "/" + PROCEED_LINK);
            }
            case TIME_ELAPSED -> {
                var engine = engine(plan("execute.backoff", TIME_STATES));
                var resting = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
                yield new Driven(engine.advance(resting, new EngineEvent.TimeElapsed(fixtures.clock.now())), null);
            }
            case STEP_OUT -> {
                var engine = entityEngine();
                yield new Driven(engine.stepOut(engine.start(JournalScopeType.PLAN, PLAN_ID).status()), null);
            }
            case FALLBACK -> {
                var engine = entityEngine();
                var resting = engine.start(JournalScopeType.PLAN, PLAN_ID).status();
                yield new Driven(engine.unwindToFallback(resting, ENTITY_FALLBACK), null);
            }
        };
    }

    /**
     * One event of every permitted subclass of {@link EngineEvent}, keyed by its class, with generated ids.
     *
     * @return the sample events by their class
     */
    Map<Class<?>, EngineEvent> sampleEvents() {
        var events = new LinkedHashMap<Class<?>, EngineEvent>();
        List.of(new EngineEvent.JobSettled("b-%016x".formatted(Generators.integers(0, Integer.MAX_VALUE).next()),
                "succeeded"), tasksSettled(), questionResolved(), operatorAct(),
                new EngineEvent.TimeElapsed(fixtures.clock.now()))
                .forEach(event -> events.put(event.getClass(), event));
        return events;
    }

    private static EngineEvent.TasksSettled tasksSettled() {
        return new EngineEvent.TasksSettled(Map.of(CHANGE_KIND, "submitted"), "approved");
    }

    private static EngineEvent.QuestionResolved questionResolved() {
        return new EngineEvent.QuestionResolved("Q-%06d".formatted(Generators.integers(1, 999_999).next()),
                "answered");
    }

    private static EngineEvent.OperatorAct operatorAct() {
        return new EngineEvent.OperatorAct(HOLD_STATE, PROCEED_LINK);
    }

    private WorkflowEngine entityEngine() {
        return engine(parse("ent.pmwf", ENTITY_SOURCE), parse("outer.pmwf", OUTER_SOURCE),
                parse("sub.pmwf", SUB_SOURCE));
    }

    /**
     * @return an EasyMock binder that returns a new empty parameter record for every primitive and keeps it in
     *         {@link #boundRecords}
     */
    private WorkflowEngine.ParameterBinder recordingBinder() {
        WorkflowEngine.ParameterBinder binder = createMock(WorkflowEngine.ParameterBinder.class);
        expect(binder.bind(anyObject(), anyObject())).andAnswer(this::bound).anyTimes();
        expect(binder.bind(anyObject(), anyObject(), anyObject())).andAnswer(this::bound).anyTimes();
        replay(binder);
        return binder;
    }

    private Record bound() {
        var parameters = new EngineFixtures.NoParams();
        boundRecords.add(parameters);
        return parameters;
    }
}
