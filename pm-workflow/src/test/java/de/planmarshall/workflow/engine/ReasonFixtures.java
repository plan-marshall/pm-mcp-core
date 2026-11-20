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

import static de.planmarshall.workflow.engine.EngineFixtures.callingPlan;
import static de.planmarshall.workflow.engine.EngineFixtures.parse;
import static de.planmarshall.workflow.engine.EngineFixtures.plan;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonToken;
import de.planmarshall.core.spi.AwaitedEvent;
import de.planmarshall.core.spi.CycleMeasure;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.OutcomeReason;
import de.planmarshall.core.spi.Primitive;
import de.planmarshall.core.spi.PrimitiveOutcome;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.spi.WorkflowExecutionContext;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.ConfigKeyRegistry;
import de.planmarshall.workflow.verify.FactKindRegistry;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;
import de.planmarshall.workflow.verify.WorkflowCatalog;
import de.planmarshall.workflow.verify.WorkflowValidator;
import org.jspecify.annotations.Nullable;

/**
 * The fixtures of the reason tests: primitives whose failure outcome carries a reason enum built on
 * {@link OutcomeReason}, one reason mapped to {@code footprint_violation} and one mapped to none, the units that run
 * them from an {@code auto} state, a composite job state, an operator link and a sub-workflow frame, a call whose
 * {@code done} routes a caller's result guard on a field named {@code reason} splits, and an engine over
 * them assembled from the static helpers and the ports of {@link EngineFixtures}, which this class reads and does not
 * change.
 * <p>
 * One instance serves one test. The outcome of {@code reason.switch} answers the reason type the test switched it to,
 * a static switch that {@link #resetSwitch()} restores after each test.
 */
// Every datum in this class is a contract: the DSL documents, the wire names they bind and the condition kind.
final class ReasonFixtures {

    /** The condition kind the mapped reason raises: a member of {@link OutcomeReason#MAPPABLE_CONDITION_KINDS}. */
    static final String FOOTPRINT_VIOLATION = "footprint_violation";

    /** The state of {@link #autoUnit()} that runs {@code reason.check}. */
    static final String CHECK_STATE = "execute.check";

    /** The composite job state of {@link #compositeUnit()} that runs {@code reason.wait} on time. */
    static final String WAIT_STATE = "execute.wait";

    /** The operator state of {@link #operatorUnit()} whose link runs {@code reason.answer}. */
    static final String ANSWER_STATE = "execute.answer";

    /** The link of {@link #ANSWER_STATE}. */
    static final String ANSWER_LINK = "answer";

    /** The calling state of {@link #callingUnit()}. */
    static final String INVOKE_STATE = "execute.invoke";

    /** The state of {@link #subUnit()} that runs {@code reason.check} inside the frame. */
    static final String SUB_CHECK_STATE = "sub.check";

    /** The state of {@link #switchUnit()} that runs {@code reason.switch}. */
    static final String SWITCH_STATE = "execute.switch";

    /** The sub-workflow {@link #callingUnit()} calls. */
    static final String SUB_UNIT = "sub.reason";

    /** The calling state of {@link #routingUnit()}, whose {@code done} routes are split by a guard on the reason. */
    static final String ROUTE_STATE = "execute.route";

    /** The key the operator unit's slot is stored under: the declaring unit's id and the slot name. */
    static final String SLOT_KEY = "plan:commit_message";

    /** The three routes of a {@link CheckOutcome} primitive, written under the state that runs it. */
    private static final String CHECK_ROUTES = """
                    succeeded -> completed
                    failed -> handed_back
                    cancelled -> handed_back
            """;

    /** The {@code auto} state that runs {@code reason.check}. */
    private static final String AUTO_STATES = """
            state execute.check
                auto reason.check
            """ + CHECK_ROUTES;

    /** The composite job state that runs {@code reason.wait} when time passed. */
    private static final String COMPOSITE_STATES = """
            state execute.wait
                composite_job reason.wait
            """ + CHECK_ROUTES;

    /** The {@code auto} state that runs {@code reason.switch}. */
    private static final String SWITCH_STATES = """
            state execute.switch
                auto reason.switch
                    done -> completed
                    failed -> handed_back
            """;

    /** The calling state of the plan that enters {@link #SUB_UNIT}. */
    private static final String CALLING_STATES = """
            state execute.invoke
                call sub.reason
                    done -> completed
                    stepped_out -> plan.return
            """;

    /** A sub-workflow whose only state runs {@code reason.check}. */
    private static final String SUB_SOURCE = """
            ---
            name: sub.reason
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.reason version 1 format 1
            scope subworkflow
            entry main -> sub.check

            state sub.check
                auto reason.check
                    succeeded -> sub.done
                    failed -> sub.stepped-out
                    cancelled -> sub.stepped-out

            state sub.done terminal done success

            state sub.stepped-out terminal stepped_out stepped_out
            """;

    /**
     * The calling state of the plan that enters {@code sub.routed}: its {@code done} is split by the result guard on
     * a field named {@code reason}, so the route shows whether the result record carries one.
     */
    private static final String ROUTING_STATES = """
            state execute.route
                call sub.routed
                    done -> completed
                        guard result.reason
                    done -> handed_back
                        guard not result.reason
                    stepped_out -> plan.return
            """;

    /**
     * A sub-workflow with a result schema whose only state runs {@code reason.check} and ends in {@code done} on its
     * {@code failed} outcome, so a routed reason returns to the caller with the result record.
     */
    private static final String ROUTED_SOURCE = """
            ---
            name: sub.routed
            description: A unit written for the tests of pm-workflow.
            type: subworkflow
            icon: workflow.default
            entry: main
            exit: done
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow sub.routed version 1 format 1
            scope subworkflow
            result schema[commit-result]
            entry main -> routed.check

            state routed.check
                auto reason.check
                    succeeded -> routed.done
                    failed -> routed.done
                    cancelled -> routed.stepped-out

            state routed.done terminal done success

            state routed.stepped-out terminal stepped_out stepped_out
            """;

    /** A plan whose operator state fills its slot from the {@code note} of the link that runs {@code reason.answer}. */
    private static final String OPERATOR_SOURCE = """
            ---
            name: plan
            description: A unit written for the tests of pm-workflow.
            type: workflow
            icon: workflow.default
            slot: commit_message
            slot_type: commit_message[1..2000]
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            workflow plan version 1 format 1
            scope plan
            start execute.answer

            state execute.answer
                fills slot.commit_message from note
                link submit answer (note: text[1..80])
                    do reason.answer
                        succeeded -> completed
                        failed -> handed_back
                        cancelled -> handed_back

            state completed terminal merged success

            state handed_back terminal handed_back failure
            """;

    /** The reason type the outcome {@link SwitchOutcome#FAILED} answers; {@link #resetSwitch()} restores it. */
    private static volatile Class<? extends OutcomeReason> switchedReasonType = CheckReason.class;

    /** The fixtures whose static helpers and ports the engine is assembled from. */
    final EngineFixtures fixtures = new EngineFixtures();

    /** An {@code auto} primitive whose {@code failed} carries a {@link CheckReason}. */
    final Reasoned<EngineFixtures.NoParams, CheckOutcome> check = new Reasoned<>("reason.check",
            EngineFixtures.NoParams.class, CheckOutcome.class, AwaitedEvent.NONE);

    /** A composite job primitive that waits for time; its {@code failed} carries a {@link CheckReason}. */
    final Reasoned<EngineFixtures.NoParams, CheckOutcome> waiting = new Reasoned<>("reason.wait",
            EngineFixtures.NoParams.class, CheckOutcome.class, AwaitedEvent.TIME);

    /** A link primitive with the free parameter {@code note}; its {@code failed} carries a {@link CheckReason}. */
    final Reasoned<EngineFixtures.NoteParams, CheckOutcome> answer = new Reasoned<>("reason.answer",
            EngineFixtures.NoteParams.class, CheckOutcome.class, AwaitedEvent.NONE);

    /** An {@code auto} primitive whose {@code failed} answers the reason type the test switched it to. */
    final Reasoned<EngineFixtures.NoParams, SwitchOutcome> switching = new Reasoned<>("reason.switch",
            EngineFixtures.NoParams.class, SwitchOutcome.class, AwaitedEvent.NONE);

    /**
     * Switches the reason type {@link SwitchOutcome#FAILED} answers, for a result that was built before the switch.
     *
     * @param reasonType the reason type the outcome answers from now on
     */
    static void switchReasonType(Class<? extends OutcomeReason> reasonType) {
        switchedReasonType = reasonType;
    }

    /** Restores the reason type {@link SwitchOutcome#FAILED} answers to {@link CheckReason}. */
    static void resetSwitch() {
        switchedReasonType = CheckReason.class;
    }

    /** @return the plan whose start state {@link #CHECK_STATE} runs {@code reason.check} */
    static WorkflowDefinition autoUnit() {
        return plan(CHECK_STATE, AUTO_STATES);
    }

    /** @return the plan whose start state {@link #WAIT_STATE} runs {@code reason.wait} on time */
    static WorkflowDefinition compositeUnit() {
        return plan(WAIT_STATE, COMPOSITE_STATES);
    }

    /** @return the plan whose start state {@link #SWITCH_STATE} runs {@code reason.switch} */
    static WorkflowDefinition switchUnit() {
        return plan(SWITCH_STATE, SWITCH_STATES);
    }

    /** @return the plan whose operator state {@link #ANSWER_STATE} fills its slot and runs {@code reason.answer} */
    static WorkflowDefinition operatorUnit() {
        return parse("plan.pmwf", OPERATOR_SOURCE);
    }

    /** @return the plan whose start state {@link #INVOKE_STATE} calls {@link #SUB_UNIT} */
    static WorkflowDefinition callingUnit() {
        return callingPlan(INVOKE_STATE, CALLING_STATES);
    }

    /** @return the sub-workflow {@link #SUB_UNIT} */
    static WorkflowDefinition subUnit() {
        return parse("sub.pmwf", SUB_SOURCE);
    }

    /** @return the plan whose start state {@link #ROUTE_STATE} calls {@code sub.routed} under a guard on the reason */
    static WorkflowDefinition routingUnit() {
        return callingPlan(ROUTE_STATE, ROUTING_STATES);
    }

    /** @return the sub-workflow {@code sub.routed}, which returns {@code done} on a failed {@code reason.check} */
    static WorkflowDefinition routedSubUnit() {
        return parse("routed.pmwf", ROUTED_SOURCE);
    }

    /** @return the registry of the reason primitives and of {@code test.step}, which the fallback of a call runs */
    PrimitiveRegistry registry() {
        return new PrimitiveRegistry(List.of(fixtures.step.primitive(), check.primitive(), waiting.primitive(),
                answer.primitive(), switching.primitive()));
    }

    /**
     * Assembles an engine over the reason primitives with the ports of {@link EngineFixtures#ports()}.
     *
     * @param unit    the top-level unit
     * @param callees the sub-workflows the unit calls
     * @return the engine
     */
    WorkflowEngine engine(WorkflowDefinition unit, WorkflowDefinition... callees) {
        var registry = registry();
        var validator = new WorkflowValidator(registry, List.of(), EngineFixtures.entityKinds(),
                QuestionClassRegistry.defaults(), SchemaRegistry.defaults(), WorkflowCatalog.of(callees),
                ConfigKeyRegistry.empty(), FactKindRegistry.empty());
        return new WorkflowEngine(unit, WorkflowCatalog.of(callees), validator, registry, binder(),
                fixtures.ports());
    }

    /**
     * @return an EasyMock binder that binds the operator's {@code note} for a primitive that takes
     *         {@link EngineFixtures.NoteParams} and the empty record for every other primitive
     */
    @SuppressWarnings("unchecked")
    private static WorkflowEngine.ParameterBinder binder() {
        WorkflowEngine.ParameterBinder binder = createMock(WorkflowEngine.ParameterBinder.class);
        expect(binder.bind(anyObject(), anyObject())).andReturn(new EngineFixtures.NoParams()).anyTimes();
        expect(binder.bind(anyObject(), anyObject(), anyObject())).andAnswer(() -> {
            var primitive = (Primitive<?, ?>) getCurrentArguments()[0];
            var parameters = (Map<String, String>) getCurrentArguments()[2];
            return EngineFixtures.NoteParams.class.equals(primitive.parameterType())
                    ? new EngineFixtures.NoteParams(parameters.get("note"))
                    : new EngineFixtures.NoParams();
        }).anyTimes();
        replay(binder);
        return binder;
    }

    /**
     * Reads the facts of a raised condition, the JSON object its status carries.
     *
     * @param json the facts as the condition carries them
     * @return the facts by name, in their order
     */
    static Map<String, String> factsOf(String json) {
        var facts = new LinkedHashMap<String, String>();
        try (var parser = new JsonFactory().createParser(json)) {
            parser.nextToken();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var name = parser.currentName();
                parser.nextToken();
                facts.put(name, parser.getText());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("the facts are no JSON object: " + json, e);
        }
        return facts;
    }

    /** The reasons of {@link CheckOutcome#FAILED}: one maps to {@code footprint_violation}, one to none. */
    enum CheckReason implements OutcomeReason {
        OUTSIDE_FOOTPRINT("outside_footprint", FOOTPRINT_VIOLATION), TESTS_RED("tests_red", null);

        private final String wireName;
        private final @Nullable String conditionKind;

        CheckReason(String wireName, @Nullable String conditionKind) {
            this.wireName = wireName;
            this.conditionKind = conditionKind;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public Optional<String> conditionKind() {
            return Optional.ofNullable(conditionKind);
        }
    }

    /** A reason type no outcome of the fixtures declares: the foreign type {@code reason.switch} is switched to. */
    enum ForeignReason implements OutcomeReason {
        ELSEWHERE;

        @Override
        public String wireName() {
            return "elsewhere";
        }
    }

    /** The outcomes of {@code reason.check}, {@code reason.wait} and {@code reason.answer}. */
    enum CheckOutcome implements PrimitiveOutcome {
        SUCCEEDED("succeeded", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE),
        CANCELLED("cancelled", OutcomeClass.INDETERMINATE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        CheckOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return this == FAILED ? Optional.of(CheckReason.class) : Optional.empty();
        }
    }

    /** The outcomes of {@code reason.switch}: {@code failed} answers the switched reason type. */
    enum SwitchOutcome implements PrimitiveOutcome {
        DONE("done", OutcomeClass.SUCCESS), FAILED("failed", OutcomeClass.FAILURE);

        private final String wireName;
        private final OutcomeClass outcomeClass;

        SwitchOutcome(String wireName, OutcomeClass outcomeClass) {
            this.wireName = wireName;
            this.outcomeClass = outcomeClass;
        }

        @Override
        public String wireName() {
            return wireName;
        }

        @Override
        public OutcomeClass outcomeClass() {
            return outcomeClass;
        }

        @Override
        public Optional<Class<? extends OutcomeReason>> reasonType() {
            return this == FAILED ? Optional.of(switchedReasonType) : Optional.empty();
        }
    }

    /**
     * A scripted primitive whose results may carry a reason: the results a test scripts, in order, then a reasonless
     * result of its first outcome; it counts its calls. The double is an EasyMock primitive; this holder is the script
     * it was recorded with.
     *
     * @param <P> the parameter record
     * @param <R> the outcome enum
     */
    static final class Reasoned<P extends Record, R extends Enum<R> & PrimitiveOutcome> {

        private final String id;
        private final Class<P> parameterType;
        private final Class<R> outcomeType;
        private final AwaitedEvent awaitedEvent;
        private final Deque<PrimitiveResult<R>> script = new ArrayDeque<>();
        private final Primitive<P, R> mock;
        private int calls;

        Reasoned(String id, Class<P> parameterType, Class<R> outcomeType, AwaitedEvent awaitedEvent) {
            this.id = id;
            this.parameterType = parameterType;
            this.outcomeType = outcomeType;
            this.awaitedEvent = awaitedEvent;
            mock = mock();
        }

        @SuppressWarnings("unchecked")
        private Primitive<P, R> mock() {
            Primitive<P, R> primitive = createMock(Primitive.class);
            expect(primitive.id()).andReturn(id).anyTimes();
            expect(primitive.parameterType()).andReturn(parameterType).anyTimes();
            expect(primitive.outcomeType()).andReturn(outcomeType).anyTimes();
            expect(primitive.awaitedEvent()).andReturn(awaitedEvent).anyTimes();
            expect(primitive.producedArtifacts()).andReturn(Set.of()).anyTimes();
            expect(primitive.requiredArtifacts()).andReturn(Set.of()).anyTimes();
            expect(primitive.waivableGates()).andReturn(Set.of()).anyTimes();
            expect(primitive.enqueuesJob()).andReturn(false).anyTimes();
            expect(primitive.cycleMeasure()).andReturn(CycleMeasure.NONE).anyTimes();
            expect(primitive.exhaustionOutcome()).andReturn(Optional.empty()).anyTimes();
            expect(primitive.execute(anyObject(WorkflowExecutionContext.class), anyObject(parameterType)))
                    .andAnswer(() -> {
                        calls++;
                        var next = script.poll();
                        return next != null ? next
                                : new PrimitiveResult<>(outcomeType.getEnumConstants()[0], Map.of(), null);
                    }).anyTimes();
            replay(primitive);
            return primitive;
        }

        /**
         * @param result the result of the next call
         * @return this primitive
         */
        Reasoned<P, R> then(PrimitiveResult<R> result) {
            script.add(result);
            return this;
        }

        /**
         * @param outcome the outcome of the next call
         * @param reason  the reason of the outcome; {@code null} for an outcome that declares none
         * @param facts   the facts the next call returns
         * @return this primitive
         */
        Reasoned<P, R> then(R outcome, @Nullable OutcomeReason reason, Map<String, Object> facts) {
            return then(PrimitiveResult.<R>builder().outcome(outcome).reason(reason).updatedFacts(facts).build());
        }

        /** @return how often the primitive ran */
        int calls() {
            return calls;
        }

        /** @return the EasyMock primitive the registry holds */
        Primitive<P, R> primitive() {
            return mock;
        }
    }
}
