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
import static de.planmarshall.workflow.engine.EngineFixtures.T0;
import static de.planmarshall.workflow.engine.EngineFixtures.planStatus;
import static de.planmarshall.workflow.engine.ReasonFixtures.ANSWER_LINK;
import static de.planmarshall.workflow.engine.ReasonFixtures.ANSWER_STATE;
import static de.planmarshall.workflow.engine.ReasonFixtures.CHECK_STATE;
import static de.planmarshall.workflow.engine.ReasonFixtures.FOOTPRINT_VIOLATION;
import static de.planmarshall.workflow.engine.ReasonFixtures.INVOKE_STATE;
import static de.planmarshall.workflow.engine.ReasonFixtures.ROUTE_STATE;
import static de.planmarshall.workflow.engine.ReasonFixtures.SLOT_KEY;
import static de.planmarshall.workflow.engine.ReasonFixtures.SUB_CHECK_STATE;
import static de.planmarshall.workflow.engine.ReasonFixtures.SUB_UNIT;
import static de.planmarshall.workflow.engine.ReasonFixtures.SWITCH_STATE;
import static de.planmarshall.workflow.engine.ReasonFixtures.WAIT_STATE;
import static de.planmarshall.workflow.engine.ReasonFixtures.factsOf;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.spi.PrimitiveResult;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.PlanHistory;
import de.planmarshall.core.store.PlanHistoryLine;
import de.planmarshall.core.store.ScopeTransaction;
import de.planmarshall.core.store.WorkflowStatus;
import de.planmarshall.core.store.WorkflowStatusCodec;
import de.planmarshall.core.store.WorkflowStatusStore;
import de.planmarshall.workflow.engine.ReasonFixtures.CheckOutcome;
import de.planmarshall.workflow.engine.ReasonFixtures.CheckReason;
import de.planmarshall.workflow.engine.ReasonFixtures.ForeignReason;
import de.planmarshall.workflow.engine.ReasonFixtures.SwitchOutcome;

/**
 * The engine's use of the reason of a primitive's result (PM-IMPL-5, § Outcomes and Reasons of the primitive
 * interface): a reason that maps to no condition takes the transition of its outcome as an outcome without a reason
 * does; a reason that maps to a condition kind raises that condition instead of the transition, with the wire names of
 * the primitive, the outcome and the reason and the bounded facts of the result, from an {@code auto} state, a
 * sub-workflow frame, a resumed composite job state and an operator link alike, and binds no job the result spawned; a
 * reason routed inside a sub-workflow stays out of the result record a caller's result guard reads; a reason the
 * outcome does not declare is an internal fault.
 * <p>
 * The wire names of the fixture primitives, outcomes and reasons and the condition kind are the contract; the facts of
 * a result are generated.
 */
@EnableGeneratorController
@DisplayName("Reasons and conditions of the engine")
class ReasonConditionTest {

    /** The bound of a fact value, the cut marker included: the contract of ADR {@code workflow-engine/020}. */
    private static final int MAX_FACT_LENGTH = 256;

    /** The elements of a collection fact rendered at most: the contract of ADR {@code workflow-engine/020}. */
    private static final int MAX_FACT_ELEMENTS = 50;

    /** The marker that ends a cut fact: the contract of ADR {@code workflow-engine/020}. */
    private static final String ELLIPSIS = "…";

    @TempDir
    Path base;

    private final ReasonFixtures fixtures = new ReasonFixtures();

    @AfterEach
    void resetSwitch() {
        ReasonFixtures.resetSwitch();
    }

    /** @return a generated fact name that is none of the three names the engine writes first */
    private static String factName() {
        return "fact_" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    /** @return a generated fact value of plain letters, short enough to stay uncut */
    private static String factValue() {
        return Generators.letterStrings(1, 40).next();
    }

    /** @return the status of the fixture plan at the state, in the execute phase */
    private static WorkflowStatus at(String state) {
        return planStatus(EXECUTE, state);
    }

    /** @return the condition of the step's status */
    private static WorkflowStatus.PendingCondition conditionOf(EngineStep step) {
        return step.status().condition().orElseThrow();
    }

    @Nested
    @DisplayName("A reason without a condition")
    class ReasonWithoutCondition {

        @Test
        @DisplayName("takes the transition of its outcome with the same status as an outcome without a reason")
        void routesLikeAnOutcomeWithoutReason() {
            var facts = Map.<String, Object>of(factName(), factValue());
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.TESTS_RED, facts)
                    .then(CheckOutcome.CANCELLED, null, facts);
            var engine = fixtures.engine(ReasonFixtures.autoUnit());

            var withReason = engine.advance(at(CHECK_STATE));
            var withoutReason = engine.advance(at(CHECK_STATE));

            assertAll(
                    () -> assertInstanceOf(EngineStep.Ended.class, withReason, "the failed outcome ends the plan"),
                    () -> assertEquals("handed_back", withReason.status().state(), "failed routes to handed_back"),
                    () -> assertTrue(withReason.status().condition().isEmpty(), "a reasonless route raises nothing"),
                    () -> assertEquals(withoutReason.status(), withReason.status(),
                            "the reason changes nothing of the status"));
        }
    }

    @Nested
    @DisplayName("A reason mapped to a condition")
    class ReasonMappedToCondition {

        @Test
        @DisplayName("raises the condition with the wire names first and the result facts after them")
        void raisesTheCondition() {
            var name = factName();
            var value = factValue();
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, Map.of(name, value));
            var engine = fixtures.engine(ReasonFixtures.autoUnit());
            var before = at(CHECK_STATE);

            var step = engine.advance(before);

            var raised = assertInstanceOf(EngineStep.ConditionRaised.class, step, "the reason raises a condition");
            var expected = new LinkedHashMap<String, String>();
            expected.put("primitive", "reason.check");
            expected.put("outcome", "failed");
            expected.put("reason", "outside_footprint");
            expected.put(name, value);
            assertAll(
                    () -> assertEquals(FOOTPRINT_VIOLATION, raised.kind(), "the condition is the mapped kind"),
                    () -> assertEquals(List.copyOf(expected.entrySet()),
                            List.copyOf(factsOf(raised.facts()).entrySet()), "the facts are named in their order"),
                    () -> assertEquals(CHECK_STATE, raised.status().state(), "the state stays where it was"),
                    () -> assertEquals(before.phase(), raised.status().phase(), "the phase stays as it was"),
                    () -> assertTrue(raised.status().callStack().isEmpty(), "the call stack stays empty"),
                    () -> assertEquals(T0, conditionOf(raised).raisedAt(), "the raise is stamped by the clock"));
        }

        @Test
        @DisplayName("drops a result fact named like one of the three wire-name facts")
        void dropsAShadowingFact() {
            var shadow = factValue();
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, Map.of("reason", shadow));
            var engine = fixtures.engine(ReasonFixtures.autoUnit());

            var facts = factsOf(conditionOf(engine.advance(at(CHECK_STATE))).facts());

            assertAll(
                    () -> assertEquals("outside_footprint", facts.get("reason"), "the reason fact is the wire name"),
                    () -> assertEquals(3, facts.size(), "the shadowing result fact is dropped"));
        }

        @Test
        @DisplayName("binds no job of a result that carries a spawned job")
        void bindsNoSpawnedJob() {
            var jobId = "job-" + Generators.letterStrings(4, 12).next();
            fixtures.check.then(PrimitiveResult.<CheckOutcome>builder().outcome(CheckOutcome.FAILED)
                    .reason(CheckReason.OUTSIDE_FOOTPRINT).spawnedJobId(jobId)
                    .updatedFacts(Map.of(factName(), factValue())).build());
            var engine = fixtures.engine(ReasonFixtures.autoUnit());

            var step = engine.advance(at(CHECK_STATE));

            var raised = assertInstanceOf(EngineStep.ConditionRaised.class, step, "the reason raises a condition");
            var written = new String(WorkflowStatusCodec.write(raised.status()), StandardCharsets.UTF_8);
            assertAll(
                    () -> assertTrue(raised.status().activeJobBinding().isEmpty(), "the raised status binds no job"),
                    () -> assertTrue(raised.status().activeJobs().isEmpty(), "the raised status lists no active job"),
                    () -> assertFalse(fixtures.fixtures.jobRequests.hasCaptured(), "the job starter is never asked"),
                    () -> assertFalse(written.contains(jobId), "the spawned job id is written nowhere"));
        }

        @Test
        @DisplayName("writes the entered line of the state and no left line")
        void writesNoLeftLine() throws IOException {
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, Map.of(factName(), factValue()));
            var engine = fixtures.engine(ReasonFixtures.autoUnit());
            var scope = JournalScope.plan(base, "pm-mcp-core", PLAN_ID);
            Files.createDirectories(scope.journalFile().getParent());
            var history = new PlanHistory(scope);
            var store = new WorkflowStatusStore(scope);

            try (var lock = EngineFixtures.holdingEverything()) {
                var transaction = ScopeTransaction.open(lock, scope, event -> {
                    // no audit record is owed
                }, Clock.fixed(T0, ZoneOffset.UTC));
                engine.advance(at(CHECK_STATE), store, transaction, history);
                transaction.commit();
            }

            var events = history.read().records().stream().map(PlanHistoryLine::event).toList();
            assertAll(
                    () -> assertEquals(List.of(PlanHistoryLine.Event.ENTERED), events, "only the entered line"),
                    () -> assertTrue(store.read().orElseThrow().condition().isPresent(),
                            "the stored status carries the condition"));
        }

        @Test
        @DisplayName("returns the raised condition unchanged on a second advance without running the primitive")
        void secondAdvanceReturnsTheCondition() {
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, Map.of(factName(), factValue()));
            var engine = fixtures.engine(ReasonFixtures.autoUnit());
            var raised = engine.advance(at(CHECK_STATE)).status();

            var again = engine.advance(raised);

            assertAll(
                    () -> assertSame(raised, again.status(), "the second advance returns the status as is"),
                    () -> assertEquals(1, fixtures.check.calls(), "the primitive ran once"));
        }

        @Test
        @DisplayName("runs the primitive again once the condition was cleared")
        void clearedConditionRunsAgain() {
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, Map.of(factName(), factValue()));
            var engine = fixtures.engine(ReasonFixtures.autoUnit());
            var raised = engine.advance(at(CHECK_STATE)).status();

            var next = engine.advance(raised.toBuilder().pendingCondition(null).build());

            assertAll(
                    () -> assertEquals(2, fixtures.check.calls(), "the primitive runs again after the clear"),
                    () -> assertEquals("completed", next.status().state(), "the reasonless succeeded routes"));
        }
    }

    @Nested
    @DisplayName("Where the raise lands")
    class WhereTheRaiseLands {

        @Test
        @DisplayName("inside a sub-workflow frame the scope stays in the callee")
        void insideASubWorkflowFrame() {
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, Map.of(factName(), factValue()));
            var engine = fixtures.engine(ReasonFixtures.callingUnit(), ReasonFixtures.subUnit());

            var step = engine.advance(at(INVOKE_STATE));

            var raised = assertInstanceOf(EngineStep.ConditionRaised.class, step, "the callee raises a condition");
            assertAll(
                    () -> assertEquals(FOOTPRINT_VIOLATION, raised.kind(), "the condition is the mapped kind"),
                    () -> assertEquals(SUB_CHECK_STATE, raised.status().state(), "the scope rests in the callee"),
                    () -> assertEquals(List.of(SUB_UNIT), raised.status().callStack().stream()
                            .map(WorkflowStatus.CallFrame::subworkflowId).toList(), "the frame stays pushed"));
        }

        @Test
        @DisplayName("from a resumed composite job state the condition is raised over the resting status")
        void fromAResumedCompositeJob() {
            var engine = fixtures.engine(ReasonFixtures.compositeUnit());
            var resting = engine.advance(at(WAIT_STATE)).status();
            fixtures.waiting.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT,
                    Map.of(factName(), factValue()));

            var step = engine.advance(resting, new EngineEvent.TimeElapsed(T0.plusSeconds(
                    Generators.integers(1, 120).next())));

            var raised = assertInstanceOf(EngineStep.ConditionRaised.class, step, "the resumed job raises");
            assertAll(
                    () -> assertEquals(FOOTPRINT_VIOLATION, raised.kind(), "the condition is the mapped kind"),
                    () -> assertEquals(WAIT_STATE, raised.status().state(), "the state stays the composite job"),
                    () -> assertEquals(resting.activeJob(), raised.status().activeJob(),
                            "the composite job stays bound"),
                    () -> assertEquals("reason.wait", factsOf(raised.facts()).get("primitive"),
                            "the facts name the composite primitive"));
        }

        @Test
        @DisplayName("from an operator link the condition is raised and the fills slot is not written")
        void fromAnOperatorLink() {
            var note = "fix(engine): " + Generators.letterStrings(1, 40).next();
            fixtures.answer.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, Map.of(factName(), factValue()));
            var engine = fixtures.engine(ReasonFixtures.operatorUnit());
            var atLink = engine.start(JournalScopeType.PLAN, PLAN_ID).status();

            var step = engine.advance(atLink, new EngineEvent.OperatorAct(ANSWER_STATE, ANSWER_LINK,
                    Map.of("note", note)));

            var raised = assertInstanceOf(EngineStep.ConditionRaised.class, step, "the link's primitive raises");
            assertAll(
                    () -> assertEquals(FOOTPRINT_VIOLATION, raised.kind(), "the condition is the mapped kind"),
                    () -> assertEquals(ANSWER_STATE, raised.status().state(), "the scope stays at the link"),
                    () -> assertFalse(raised.status().slots().containsKey(SLOT_KEY), "the fills slot stays unwritten"),
                    () -> assertEquals(atLink.slots(), raised.status().slots(), "the slots are those before the act"));
        }
    }

    @Nested
    @DisplayName("The bounds of the facts")
    class FactBounds {

        /** @return the facts of the condition a mapped reason raises over the given result facts */
        private Map<String, String> raisedFacts(Map<String, Object> resultFacts) {
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, resultFacts);
            var engine = fixtures.engine(ReasonFixtures.autoUnit());
            return factsOf(conditionOf(engine.advance(at(CHECK_STATE))).facts());
        }

        @Test
        @DisplayName("a collection over 50 elements keeps the first 50 and ends in the marker")
        void cutsALongCollection() {
            var name = factName();
            var elements = new ArrayList<String>();
            var size = Generators.integers(MAX_FACT_ELEMENTS + 1, MAX_FACT_ELEMENTS + 30).next();
            for (var index = 0; index < size; index++) {
                elements.add(Generators.letterStrings(1, 3).next());
            }

            var facts = raisedFacts(Map.of(name, elements));

            var kept = elements.stream().limit(MAX_FACT_ELEMENTS).collect(Collectors.joining(","));
            assertEquals(kept + "," + ELLIPSIS, facts.get(name), "the collection is cut at 50 elements");
        }

        @Test
        @DisplayName("a collection of 50 elements or fewer is joined whole")
        void joinsAShortCollection() {
            var name = factName();
            var elements = List.of(factValue(), factValue(), factValue());

            var facts = raisedFacts(Map.of(name, elements));

            assertEquals(String.join(",", elements), facts.get(name), "a short collection is joined whole");
        }

        @Test
        @DisplayName("a 300-character text is cut to 256 characters ending in the marker")
        void cutsALongText() {
            var name = factName();
            var text = Generators.letterStrings(300, 300).next();

            var facts = raisedFacts(Map.of(name, text));

            assertAll(
                    () -> assertEquals(MAX_FACT_LENGTH, facts.get(name).length(), "the cut text has the bound"),
                    () -> assertEquals(text.substring(0, MAX_FACT_LENGTH - 1) + ELLIPSIS, facts.get(name),
                            "the cut text keeps its start and the marker"));
        }

        @Test
        @DisplayName("a text whose cut falls inside a surrogate pair is cut before the pair")
        void cutsBeforeASurrogatePair() {
            var name = factName();
            var head = Generators.letterStrings(MAX_FACT_LENGTH - 2, MAX_FACT_LENGTH - 2).next();
            var pair = new String(Character.toChars(Generators.integers(Character.MIN_SUPPLEMENTARY_CODE_POINT,
                    Character.MAX_CODE_POINT).next()));
            var text = head + pair + Generators.letterStrings(10, 40).next();

            var facts = raisedFacts(Map.of(name, text));

            assertAll(
                    () -> assertTrue(Character.isHighSurrogate(text.charAt(MAX_FACT_LENGTH - 2)),
                            "the plain cut would split the pair"),
                    () -> assertEquals(head + ELLIPSIS, facts.get(name), "the cut drops the whole pair"),
                    () -> assertEquals(MAX_FACT_LENGTH - 1, facts.get(name).length(),
                            "the cut text is one under the bound"));
        }

        @Test
        @DisplayName("a number and a boolean are rendered as their text")
        void rendersScalars() {
            var number = Generators.integers(0, 10_000).next();
            var flag = Generators.booleans().next();

            var resultFacts = new LinkedHashMap<String, Object>();
            resultFacts.put("fact_number", number);
            resultFacts.put("fact_flag", flag);

            var facts = raisedFacts(resultFacts);

            assertAll(
                    () -> assertEquals(String.valueOf(number), facts.get("fact_number"), "a number is its text"),
                    () -> assertEquals(String.valueOf(flag), facts.get("fact_flag"), "a boolean is its text"));
        }
    }

    @Nested
    @DisplayName("A routed reason inside a sub-workflow")
    class RoutedReasonInSubWorkflow {

        @Test
        @DisplayName("stays out of the result record the caller's result guard reads")
        void staysOutOfTheResultRecord() {
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.TESTS_RED, Map.of(factName(), factValue()));
            var engine = fixtures.engine(ReasonFixtures.routingUnit(), ReasonFixtures.routedSubUnit());

            var step = engine.advance(at(ROUTE_STATE));

            assertAll(
                    () -> assertInstanceOf(EngineStep.Ended.class, step, "the returned done ends the plan"),
                    () -> assertEquals("handed_back", step.status().state(), "the guard on result.reason fails"),
                    () -> assertTrue(step.status().callStack().isEmpty(), "the returned call pops its frame"));
        }

        @Test
        @DisplayName("leaves the result facts in the record, so a fact named reason is read by the guard")
        void keepsTheResultFacts() {
            fixtures.check.then(CheckOutcome.FAILED, CheckReason.TESTS_RED, Map.of("reason", factValue()));
            var engine = fixtures.engine(ReasonFixtures.routingUnit(), ReasonFixtures.routedSubUnit());

            var step = engine.advance(at(ROUTE_STATE));

            assertAll(
                    () -> assertInstanceOf(EngineStep.Ended.class, step, "the returned done ends the plan"),
                    () -> assertEquals("completed", step.status().state(), "the guard reads the result fact"));
        }
    }

    @Nested
    @DisplayName("A foreign reason")
    class ForeignReasonCase {

        @Test
        @DisplayName("is an internal fault UNDECLARED_OUTCOME naming the primitive and the state")
        void isAnInternalFault() {
            fixtures.switching.then(PrimitiveResult.<SwitchOutcome>builder().outcome(SwitchOutcome.FAILED)
                    .reason(CheckReason.TESTS_RED).build());
            ReasonFixtures.switchReasonType(ForeignReason.class);
            var engine = fixtures.engine(ReasonFixtures.switchUnit());
            var start = at(SWITCH_STATE);

            var fault = assertThrows(InternalFaultException.class, () -> engine.advance(start),
                    "a foreign reason is an internal fault");

            assertAll(
                    () -> assertEquals(InternalFaultException.Reason.UNDECLARED_OUTCOME, fault.reason(),
                            "the fault is an undeclared outcome"),
                    () -> assertTrue(fault.getMessage().contains("'reason.switch'"), "the fault names the primitive"),
                    () -> assertTrue(fault.getMessage().contains("'" + SWITCH_STATE + "'"),
                            "the fault names the state"),
                    () -> assertTrue(fault.getMessage().contains("'tests_red'"), "the fault names the reason"));
        }
    }
}
