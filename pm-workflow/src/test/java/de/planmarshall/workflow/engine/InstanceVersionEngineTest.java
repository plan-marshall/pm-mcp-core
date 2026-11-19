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
import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;


import org.easymock.Capture;
import org.easymock.CaptureType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.PlanHistory;
import de.planmarshall.core.store.PlanHistoryLine;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.RetryCounters.RetryRule;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.ast.UnitOrigin;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.plan.CompositionEvent;
import de.planmarshall.workflow.plan.CompositionInputs;
import de.planmarshall.workflow.plan.CompositionResult;
import de.planmarshall.workflow.plan.InstanceComposition;
import de.planmarshall.workflow.plan.InstancePosition;
import de.planmarshall.workflow.plan.InstanceUnits;
import de.planmarshall.workflow.plan.PlanInstance;
import de.planmarshall.workflow.plan.PlanInstanceComposer;
import de.planmarshall.workflow.plan.PlanInstanceHeader;
import de.planmarshall.workflow.plan.UnitReference;

/**
 * The composition events of a plan instance and the version in force (Workflow DSL § Versions &amp; Composition
 * Events): the engine composes a server event in the transaction that enters its target state and runs the rest of the
 * chain from the version, an operator event takes force from the next transition, a refused version raises
 * {@code precondition_unmet} or is returned, and a version in force runs its composed units and its stored sub-unit
 * texts although another copy changed.
 */
@EnableGeneratorController
@DisplayName("Instance versions in the engine")
class InstanceVersionEngineTest {

    private static final String EXECUTE = "phase-5-execute";

    private static final String SHA = "0".repeat(64);

    private static final Instant T0 = Instant.parse("2026-11-18T10:00:00Z");

    @TempDir
    Path base;

    private final EngineFixtures fixtures = new EngineFixtures();

    private JournalScope scope;

    private PlanHistory history;

    private WorkflowStatusStore store;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, "pm-mcp-core", PLAN_ID);
        Files.createDirectories(scope.journalFile().getParent());
        history = new PlanHistory(scope);
        store = new WorkflowStatusStore(scope);
    }

    /** @return the plan unit the engine is built with: {@code s.one} ends the plan in {@code completed} */
    private static WorkflowDefinition builtUnit() {
        return fullUnit(1, """
                state s.one
                    auto test.mark
                        done -> completed
                        failed -> completed

                state completed terminal merged success
                """);
    }

    /**
     * @param version the version the unit is
     * @return a plan unit of the version whose {@code s.one} selects {@code s.composed} instead
     */
    private static WorkflowDefinition composedUnit(int version) {
        return fullUnit(version, """
                state s.one
                    auto test.mark
                        done -> s.composed
                        failed -> s.composed

                state s.composed terminal merged success
                """);
    }

    /** The frontmatter of a fixture unit, written for the test. */
    private static final String FRONTMATTER = """
            ---
            name: %s
            description: A unit written for the tests of pm-workflow.
            type: %s
            icon: workflow.default
            modification_date: 2026-01-15
            version: 0.1.0
            author: plan-marshall
            ---
            """;

    /**
     * @param version the version the unit is
     * @param states  the states of the plan unit
     * @return a complete plan unit of the version, frontmatter included
     */
    private static WorkflowDefinition fullUnit(int version, String states) {
        return EngineFixtures.parse("plan v" + version, FRONTMATTER.formatted("plan", "workflow") + """
                workflow plan version %d format 1
                scope plan
                start s.one

                %s""".formatted(version, states));
    }

    /**
     * @param start the start state of the sub-workflow
     * @return a complete sub-workflow unit with its entry, frontmatter included
     */
    private static WorkflowDefinition subUnit(String name, String start, String terminal) {
        return EngineFixtures.parse(name, """
                ---
                name: sub
                description: A unit written for the tests of pm-workflow.
                type: subworkflow
                icon: workflow.default
                entry: main
                exit: done
                modification_date: 2026-01-15
                version: 0.1.0
                author: plan-marshall
                ---
                workflow sub version 1 format 1
                scope subworkflow
                entry main -> %s

                state %s terminal %s
                """.formatted(start, start, terminal));
    }

    private static PlanInstance instance(int version, CompositionEvent event, WorkflowDefinition unit,
            List<UnitReference> subUnits) {
        var inputs = CompositionInputs.builder()
                .configHash(SHA)
                .templates(List.of(new UnitReference(PlanInstanceComposer.PLAN_UNIT,
                        "instance/plan.v%d.pmwf".formatted(version), SHA, UnitOrigin.DEFAULT)))
                .subUnits(subUnits)
                .compositionRulesRevision("1")
                .compositionRulesDigest(SHA)
                .planFactsDigest(SHA)
                .build();
        var header = PlanInstanceHeader.builder()
                .planId(PLAN_ID)
                .version(version)
                .event(event)
                .composedAt(T0)
                .inputs(inputs)
                .steps(new PlanInstanceHeader.Steps(List.of(), List.of()))
                .pruned(List.of())
                .policyCalls(List.of())
                .waivedGates(List.of())
                .build();
        return PlanInstance.builder().version(version).header(header).unit(unit).text("plan v" + version)
                .units(List.of()).build();
    }

    private static CompositionResult.Composed composed(int version, CompositionEvent event) {
        return new CompositionResult.Composed(instance(version, event, composedUnit(version), List.of()));
    }

    private void committed(Consumer<ScopeTransaction> writing) {
        try (var lock = EngineFixtures.holdingEverything()) {
            var transaction = ScopeTransaction.open(lock, scope, event -> {
                // no audit record is owed
            }, Clock.fixed(T0, ZoneOffset.UTC));
            writing.accept(transaction);
            transaction.commit();
        }
    }

    private EngineStep committedStep(Function<ScopeTransaction, EngineStep> writing) {
        var step = new EngineStep[1];
        committed(transaction -> step[0] = writing.apply(transaction));
        return step[0];
    }

    private WorkflowEngine.OperatorComposition committedOperator(WorkflowEngine engine, WorkflowStatus status,
            CompositionEvent event) {
        var result = new WorkflowEngine.OperatorComposition[1];
        committed(transaction -> result[0] = engine.composeOperatorEvent(status, event, store, transaction, history));
        return result[0];
    }

    /** The results the composition port recorded, in the order the engine recorded them. */
    private final Capture<CompositionResult> recorded = newCapture(CaptureType.ALL);

    /** The positions the composition port was handed, in the order the engine composed. */
    private final Capture<InstancePosition> positions = newCapture(CaptureType.ALL);

    /** The units the composition port answers for a version it composed, by version. */
    private final Map<Integer, InstanceUnits> composedUnits = new HashMap<>();

    /**
     * @param triggers the server event of each state that composes one
     * @param results  the results the composition returns, in order; the last one for every later call
     * @return the composition port: an EasyMock double, so a call it was not recorded for fails the test
     */
    private InstanceComposition composition(Map<String, CompositionEvent> triggers, CompositionResult... results) {
        InstanceComposition composition = createMock(InstanceComposition.class);
        triggers.forEach((stateId, event) ->
                expect(composition.serverEventAt(stateId)).andReturn(Optional.of(event)).anyTimes());
        expect(composition.serverEventAt(anyString())).andReturn(Optional.empty()).anyTimes();
        for (var index = 0; index < results.length; index++) {
            var returned = expect(composition.compose(anyObject(CompositionEvent.class),
                    capture(positions), anyInt())).andReturn(results[index]);
            if (index < results.length - 1) {
                returned.times(1);
            } else {
                returned.anyTimes();
            }
        }
        composition.recordOutcome(anyObject(ScopeTransaction.class), capture(recorded));
        expectLastCall().anyTimes();
        expect(composition.unitsOf(anyObject(CompositionResult.Composed.class))).andAnswer(
                () -> composedUnits.get(((CompositionResult.Composed) getCurrentArguments()[0]).instance().version()))
                .anyTimes();
        replay(composition);
        return composition;
    }

    /**
     * @param version the version the composition returns
     * @param units   the units the port answers for that version, as {@link InstanceComposition#unitsOf} does
     * @return this fixture
     */
    private InstanceVersionEngineTest composedUnitsOf(int version, InstanceUnits units) {
        composedUnits.put(version, units);
        return this;
    }

    /** @return the outcomes the composition port recorded, in order */
    private List<CompositionResult> recorded() {
        return recorded.getValues();
    }

    /**
     * @return the server events of {@link CompositionEvent}, derived so a new event joins the cases
     */
    static Stream<CompositionEvent> serverEvents() {
        return Stream.of(CompositionEvent.values())
                .filter(event -> event.trigger() == CompositionEvent.Trigger.SERVER);
    }

    /**
     * @return the operator and configuration events of {@link CompositionEvent}, derived so a new event joins the cases
     */
    static Stream<CompositionEvent> operatorEvents() {
        return Stream.of(CompositionEvent.values())
                .filter(event -> event.trigger() == CompositionEvent.Trigger.OPERATOR);
    }

    /**
     * @return the units port of a plan whose version's files are staged and not yet readable, as the store answers inside
     *         the transaction that composed the version
     */
    private static InstanceUnits noFiles() {
        return units(Map.of(), Map.of());
    }

    /**
     * @param plans    the plan unit of every version the engine may read
     * @param subUnits the stored text of each sub-unit, by unit id
     * @return the units port: an EasyMock double over the versions, with no composed file
     */
    private static InstanceUnits units(Map<Integer, WorkflowDefinition> plans,
            Map<String, WorkflowDefinition> subUnits) {
        InstanceUnits units = createMock(InstanceUnits.class);
        plans.forEach((version, unit) -> expect(units.planUnit(version)).andReturn(Optional.of(unit)).anyTimes());
        expect(units.planUnit(anyInt())).andReturn(Optional.empty()).anyTimes();
        subUnits.forEach((unit, definition) -> expect(units.subUnit(anyInt(), eq(unit)))
                .andReturn(Optional.of(definition)).anyTimes());
        expect(units.subUnit(anyInt(), anyString())).andReturn(Optional.empty()).anyTimes();
        expect(units.composedUnit(anyInt(), anyString())).andReturn(Optional.empty()).anyTimes();
        replay(units);
        return units;
    }

    @Nested
    @DisplayName("a server event")
    class ServerEvents {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.engine.InstanceVersionEngineTest#serverEvents")
        @DisplayName("composes its version in the entering transaction and the chain selects from it")
        void composesInTheEnteringTransaction(CompositionEvent event) {
            composedUnitsOf(2, units(Map.of(2, composedUnit(2)), Map.of()));
            var composition = composition(Map.of("s.one", event), composed(2, event));
            var engine = fixtures.engine(builtUnit(), fixtures.instancePorts(composition, noFiles()));

            var step = committedStep(transaction -> engine.advance(EngineFixtures.planStatus(EXECUTE, "s.one"),
                    store, transaction, history));

            assertAll(
                    () -> assertEquals(1, recorded().size(),
                            "the version was recorded in the advance's transaction"),
                    () -> assertInstanceOf(CompositionResult.Composed.class, recorded().getFirst(),
                            "the recorded outcome is the composed version"),
                    () -> assertEquals(2, store.read().orElseThrow().instanceVersion().intValue(),
                            "the version in force is the composed one"),
                    () -> assertEquals("s.composed", ((EngineStep.Ended) step).status().state(),
                            "the transition was selected from the version in force"),
                    () -> assertEquals(InstancePosition.at("s.one"), positions.getValues().getFirst(),
                            "the port reads the position at the entering state"),
                    () -> assertTrue(history.read().records().stream()
                                    .anyMatch(line -> line.event() == PlanHistoryLine.Event.INSTANCE),
                            "the version's history line was written"));
        }

        @Test
        @DisplayName("reads the position from the top frame of the call stack")
        void positionFromTheCallStack() {
            var callee = subUnit("sub (stored text)", "s.called", "done success");
            var built = EngineFixtures.callingPlan("s.invoke", """
                    state s.invoke
                        call sub entry main
                            done -> completed
                            stepped_out -> plan.return
                    """);
            composedUnitsOf(2, units(Map.of(2, built), Map.of("sub", callee)));
            var composition = composition(Map.of("s.called", CompositionEvent.PLAN), composed(2, CompositionEvent.PLAN));
            var engine = fixtures.engine(built, fixtures.instancePorts(composition, noFiles()), callee);
            var inFrame = EngineFixtures.planStatus(EXECUTE, "s.called").toBuilder()
                    .callStack(List.of(EngineFixtures.frame("plan", "s.invoke", "sub")))
                    .build();

            committedStep(transaction -> engine.advance(inFrame, store, transaction, history));

            assertEquals(new InstancePosition(new InstancePosition.UnitState("sub", "s.called"),
                            List.of(new InstancePosition.UnitState("plan", "s.invoke"))),
                    positions.getValues().getFirst(), "the position names the top frame and the caller");
        }

        @Test
        @DisplayName("a second composing state in one advance reports precondition_unmet")
        void secondCompositionInOneAdvanceIsRefused() {
            var unit = EngineFixtures.plan("s.one", """
                    state s.one
                        auto test.mark
                            done -> s.two
                            failed -> s.two

                    state s.two
                        auto test.step
                            done -> completed
                            failed -> handed_back
                    """);
            composedUnitsOf(2, units(Map.of(2, unit), Map.of()));
            var composition = composition(Map.of("s.one", CompositionEvent.OUTLINE, "s.two", CompositionEvent.PLAN),
                    composed(2, CompositionEvent.OUTLINE), composed(3, CompositionEvent.PLAN));
            var engine = fixtures.engine(unit, fixtures.instancePorts(composition, noFiles()));

            var raised = assertInstanceOf(EngineStep.ConditionRaised.class,
                    committedStep(transaction -> engine.advance(EngineFixtures.planStatus(EXECUTE, "s.one"), store,
                            transaction, history)),
                    "the second composition is a condition, not a fault");

            assertAll(
                    () -> assertEquals(WorkflowEngine.PRECONDITION_UNMET, raised.kind(),
                            "the condition is precondition_unmet"),
                    () -> assertTrue(raised.facts().contains("must be committed"),
                            "the condition names the commit the first version needs"));
        }

        @Test
        @DisplayName("whose composition is refused raises precondition_unmet on the composing transition")
        void refusedRaisesPreconditionUnmet() {
            var refusal = new CompositionResult.Refused(CompositionResult.Refusal.POSITION_NOT_CONTAINED,
                    List.of("position: version 2 does not contain the state 's.one' of the unit 'plan'"));
            var composition = composition(Map.of("s.one", CompositionEvent.OUTLINE), refusal);
            var engine = fixtures.engine(builtUnit(), fixtures.instancePorts(composition, units(Map.of(), Map.of())));

            var raised = assertInstanceOf(EngineStep.ConditionRaised.class,
                    committedStep(transaction -> engine.advance(EngineFixtures.planStatus(EXECUTE, "s.one"), store,
                            transaction, history)),
                    "a refused composition raises the condition");

            assertAll(
                    () -> assertEquals(WorkflowEngine.PRECONDITION_UNMET, raised.kind(),
                            "the condition is precondition_unmet"),
                    () -> assertTrue(raised.facts().contains("position_not_contained"),
                            "the condition carries the refusal reason"),
                    () -> assertEquals("s.one", raised.status().state(),
                            "the scope stays at the composing transition's target"),
                    () -> assertTrue(store.read().orElseThrow().instanceVersionInForce().isEmpty(),
                            "no version took force"));
        }
    }

    @Nested
    @DisplayName("an operator event")
    class OperatorEvents {

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.workflow.engine.InstanceVersionEngineTest#operatorEvents")
        @DisplayName("takes force from the next transition, never mid-state")
        void takesForceFromTheNextTransition(CompositionEvent event) {
            var units = units(Map.of(2, composedUnit(2)), Map.of());
            composedUnitsOf(2, units);
            var engine = fixtures.engine(builtUnit(),
                    fixtures.instancePorts(composition(Map.of(), composed(2, event)), units));

            var after = committedOperator(engine, EngineFixtures.planStatus(EXECUTE, "s.one"), event);
            var next = committedStep(transaction -> engine.advance(after.status(), store, transaction, history));

            assertAll(
                    () -> assertEquals("s.one", after.status().state(), "the state did not move"),
                    () -> assertEquals(2, after.status().instanceVersion(), "the version took force"),
                    () -> assertInstanceOf(CompositionResult.Composed.class, after.result(),
                            "the composition composed a version"),
                    () -> assertEquals(2, store.read().orElseThrow().instanceVersion(),
                            "the status store carries the version"),
                    () -> assertEquals("s.composed", ((EngineStep.Ended) next).status().state(),
                            "the next transition runs the new version"));
        }

        @Test
        @DisplayName("whose version fails the position rule is refused and its attempt logged")
        void refusedIsReturnedAndLogged() {
            var refusal = new CompositionResult.Refused(CompositionResult.Refusal.POSITION_NOT_CONTAINED,
                    List.of("position: version 2 does not contain the state 's.one' of the unit 'plan'"));
            var composition = composition(Map.of(), refusal);
            var engine = fixtures.engine(builtUnit(), fixtures.instancePorts(composition, units(Map.of(), Map.of())));

            var after = committedOperator(engine, EngineFixtures.planStatus(EXECUTE, "s.one"),
                    CompositionEvent.OVERRIDE);

            assertAll(
                    () -> assertInstanceOf(CompositionResult.Refused.class, after.result(),
                            "the refusal is returned to the caller"),
                    () -> assertEquals(1, recorded().size(),
                            "the refused attempt was recorded in the transaction"),
                    () -> assertInstanceOf(CompositionResult.Refused.class, recorded().getFirst(),
                            "the recorded attempt is the refusal"),
                    () -> assertTrue(after.status().instanceVersionInForce().isEmpty(),
                            "no version took force"));
        }
    }

    @Nested
    @DisplayName("the version in force")
    class VersionInForce {

        @Test
        @DisplayName("runs the stored sub-unit text although another copy of the unit changed")
        void runsTheStoredSubUnitText() {
            var digest = "a".repeat(64);
            var reference = new UnitReference("sub", "mcp/units/" + digest + ".pmwf", digest, UnitOrigin.DEFAULT);
            var built = EngineFixtures.callingPlan("s.invoke", """
                    state s.invoke
                        call sub entry main
                            done -> completed
                            stepped_out -> plan.return
                    """);
            var projectCopy = subUnit("sub (project copy)", "sub.old", "done success");
            var storedText = subUnit("sub (stored text)", "sub.new", "done success");
            var composedPlan = fullUnit(2, """
                    state s.invoke
                        call sub entry main
                            done -> completed
                            stepped_out -> plan.return

                    state completed terminal merged success
                    """);
            var composition = composition(Map.of("s.invoke", CompositionEvent.PLAN),
                    new CompositionResult.Composed(instance(2, CompositionEvent.PLAN, composedPlan,
                            List.of(reference))));
            var units = units(Map.of(2, composedPlan), Map.of("sub", storedText));
            composedUnitsOf(2, units);
            var engine = fixtures.engine(built, fixtures.instancePorts(composition, noFiles()), projectCopy);

            committedStep(transaction -> engine.advance(EngineFixtures.planStatus(EXECUTE, "s.invoke"),
                    store, transaction, history));

            var written = history.read().records();
            assertAll(
                    () -> assertEquals(2, store.read().orElseThrow().instanceVersion().intValue(),
                            "the version took force"),
                    () -> assertTrue(written.stream().anyMatch(line -> line.event() == PlanHistoryLine.Event.ENTERED
                                    && "sub.new".equals(line.node())),
                            "the stored text ran, not the project's copy"),
                    () -> assertTrue(written.stream().noneMatch(line -> line.event() == PlanHistoryLine.Event.ENTERED
                                    && "sub.old".equals(line.node())),
                            "the project copy never ran"));
        }

        @Test
        @DisplayName("runs a version already in force from the units port")
        void runsTheVersionInForceFromThePort() {
            var engine = fixtures.engine(builtUnit(),
                    fixtures.instancePorts(null, units(Map.of(2, composedUnit(2)), Map.of())));

            var step = committedStep(transaction -> engine.advance(
                    EngineFixtures.planStatus(EXECUTE, "s.one", 2), store, transaction, history));

            assertEquals("s.composed", ((EngineStep.Ended) step).status().state(),
                    "the committed version's plan unit ran");
        }

        @Test
        @DisplayName("writes its history line and resets no counter of the version before it")
        void rewritesNoHistoryAndNoCounter() {
            var units = units(Map.of(2, composedUnit(2), 3, composedUnit(3)), Map.of());
            composedUnitsOf(2, units);
            composedUnitsOf(3, units);
            var composition = composition(Map.of("s.one", CompositionEvent.OUTLINE),
                    composed(2, CompositionEvent.OUTLINE), composed(3, CompositionEvent.OVERRIDE));
            var engine = fixtures.engine(builtUnit(), fixtures.instancePorts(composition,
                    units(Map.of(2, composedUnit(2)), Map.of())));
            var first = EngineFixtures.planStatus(EXECUTE, "s.one").toBuilder()
                    .retryCounters(RetryCounters.empty().attempt(RetryRule.PUSH_FAILED, "s.one"))
                    .build();

            var step = committedStep(transaction -> engine.advance(first, store, transaction, history));
            var after = store.read().orElseThrow();
            var written = history.read().records();
            var next = committedOperator(engine, after.toBuilder().state("s.one").build(),
                    CompositionEvent.OVERRIDE);

            assertAll(
                    () -> assertEquals("s.composed", ((EngineStep.Ended) step).status().state(),
                            "the first advance ran the version"),
                    () -> assertEquals(first.retryCounters(), after.retryCounters(),
                            "the composition resets no retry counter"),
                    () -> assertEquals(written, history.read().records().subList(0, written.size()),
                            "the earlier history lines stay as written"),
                    () -> assertInstanceOf(CompositionResult.Composed.class, next.result(),
                            "the operator event composed the next version"));
        }
    }
}
