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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.spi.CommandSettlement;
import de.planmarshall.core.spi.OutcomeClass;
import de.planmarshall.core.spi.Provenance;
import de.planmarshall.core.spi.Provenance.Trigger;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.workflow.engine.EngineFixtures.StepOutcome;
import de.planmarshall.workflow.engine.ReasonFixtures.CheckOutcome;
import de.planmarshall.workflow.engine.ReasonFixtures.CheckReason;

/**
 * Proves that the engine runs every primitive as a {@link de.planmarshall.core.spi.Command} and returns one
 * {@link CommandSettlement} per run on the step of the advance, on the routed and on the raised exit (PM-IMPL-5).
 */
@EnableGeneratorController
@DisplayName("Command dispatch of the workflow engine")
class CommandDispatchTest {

    @Nested
    @DisplayName("Settlements of a chain")
    class Chain {

        @Test
        @DisplayName("Should settle three chained primitives in run order, one command each, one correlation")
        void shouldSettleChainInRunOrder() {
            var dispatch = new CommandDispatchFixtures();
            var facts = List.of(fact(), fact(), fact());
            dispatch.fixtures.step.then(StepOutcome.DONE, facts.get(0));
            dispatch.fixtures.mark.then(StepOutcome.DONE, facts.get(1));
            dispatch.fixtures.proceed.then(StepOutcome.DONE, facts.get(2));
            var at = dispatch.fixtures.clock.tick(Duration.ofSeconds(Generators.integers(1, 3_600).next()));

            var step = dispatch.engine(CommandDispatchFixtures.chainUnit()).start(JournalScopeType.PLAN, PLAN_ID);

            var settlements = step.settlements();
            assertAll(
                    () -> assertInstanceOf(EngineStep.Ended.class, step, "the chain ends in its terminal state"),
                    // the primitive ids are the fixture ids of the three chained states
                    () -> assertEquals(List.of("test.step", "test.mark", "test.proceed"),
                            settlements.stream().map(settlement -> settlement.command().primitiveId()).toList(),
                            "the settlements follow the order of the run"),
                    () -> assertEquals(3, settlements.stream().map(settlement -> settlement.command().commandId())
                            .distinct().count(), "every run draws a command id of its own"),
                    () -> assertEquals(1, settlements.stream().map(settlement -> settlement.command().correlationId())
                            .distinct().count(), "one advance shares one correlation id"),
                    () -> assertTrue(settlements.stream().allMatch(settlement -> at.equals(settlement.command()
                            .issuedAt())), "each command is issued at the clock's instant"),
                    () -> assertTrue(settlements.stream().allMatch(settlement -> at.equals(settlement.settledAt())),
                            "each run is settled at the clock's instant"),
                    () -> assertTrue(settlements.stream().allMatch(settlement -> StepOutcome.DONE.wireName()
                            .equals(settlement.outcome())), "each settlement carries the outcome done"),
                    () -> assertTrue(settlements.stream().allMatch(settlement -> settlement.outcomeClass()
                            == OutcomeClass.SUCCESS), "each settlement carries the success class"),
                    () -> assertTrue(settlements.stream().allMatch(settlement -> settlement.reason() == null
                            && settlement.raisedCondition() == null), "a routed run carries no reason or condition"),
                    () -> assertEquals(facts, settlements.stream().map(CommandSettlement::facts).toList(),
                            "each settlement carries the facts of its run"),
                    () -> assertEquals(3, dispatch.boundRecords.size(), "the binder bound one record per run"));
            for (var index = 0; index < settlements.size(); index++) {
                assertSame(dispatch.boundRecords.get(index), settlements.get(index).command().input(),
                        "the command input is the binder's own record");
            }
        }

        @Test
        @DisplayName("Should give equal settlements and statuses to two engines with equally seeded generators")
        void shouldBeDeterministicUnderEqualSeeds() {
            var facts = fact();
            var first = new CommandDispatchFixtures();
            var second = new CommandDispatchFixtures();
            first.fixtures.mark.then(StepOutcome.DONE, facts);
            second.fixtures.mark.then(StepOutcome.DONE, facts);

            var one = first.engine(CommandDispatchFixtures.chainUnit()).start(JournalScopeType.PLAN, PLAN_ID);
            var other = second.engine(CommandDispatchFixtures.chainUnit()).start(JournalScopeType.PLAN, PLAN_ID);

            assertAll(
                    () -> assertEquals(one.settlements(), other.settlements(), "equal seeds give equal settlements"),
                    () -> assertEquals(one.status(), other.status(), "equal seeds give equal statuses"));
        }
    }

    @Nested
    @DisplayName("Provenance of a command")
    class Provenances {

        @ParameterizedTest(name = "{0}")
        @EnumSource(Trigger.class)
        @DisplayName("Should give every command the cause of its advance, with the reference of the cause")
        void shouldCarryTheCauseOfTheAdvance(Trigger trigger) {
            var driven = new CommandDispatchFixtures().drive(trigger);

            var settlements = driven.step().settlements();
            var expected = new Provenance(trigger, driven.reference());
            assertAll(
                    () -> assertFalse(settlements.isEmpty(), "the entry point runs at least one primitive"),
                    () -> assertTrue(settlements.stream().allMatch(settlement -> expected.equals(settlement.command()
                            .provenance())), "every command carries the cause of the advance"));
        }

        @Test
        @DisplayName("Should name exactly the triggers of the entry points and of every permitted engine event")
        void shouldNameEveryTriggerOfEntryPointsAndEvents() {
            var samples = new CommandDispatchFixtures().sampleEvents();
            var permitted = Set.of(EngineEvent.class.getPermittedSubclasses());

            var derived = EnumSet.of(Trigger.START, Trigger.RESUME, Trigger.STEP_OUT, Trigger.FALLBACK);
            samples.values().forEach(event -> derived.add(WorkflowEngine.provenanceOf(event).trigger()));

            assertAll(
                    () -> assertEquals(permitted, samples.keySet(), "a sample exists for every engine event kind"),
                    () -> assertEquals(EnumSet.allOf(Trigger.class), derived,
                            "the triggers are the entry points and the events"));
        }
    }

    @Nested
    @DisplayName("Settlements of a reason")
    class Reasons {

        @Test
        @DisplayName("Should settle a mapped reason with its raised condition and take no transition")
        void shouldSettleMappedReasonWithCondition() {
            var reasons = new ReasonFixtures();
            var facts = fact();
            reasons.check.then(CheckOutcome.FAILED, CheckReason.OUTSIDE_FOOTPRINT, facts);

            var step = reasons.engine(ReasonFixtures.autoUnit()).start(JournalScopeType.PLAN, PLAN_ID);

            var settlement = step.settlements().getFirst();
            assertAll(
                    () -> assertInstanceOf(EngineStep.ConditionRaised.class, step,
                            "the mapped reason raises a condition"),
                    () -> assertEquals(ReasonFixtures.CHECK_STATE, step.status().state(),
                            "the raised condition takes no transition"),
                    () -> assertEquals(1, step.settlements().size(), "the raised exit leaves one settlement"),
                    () -> assertEquals(CheckOutcome.FAILED.wireName(), settlement.outcome(),
                            "the settlement carries the failed outcome"),
                    () -> assertEquals(CheckReason.OUTSIDE_FOOTPRINT.wireName(), settlement.reason(),
                            "the settlement carries the result's reason"),
                    () -> assertEquals(ReasonFixtures.FOOTPRINT_VIOLATION, settlement.raisedCondition(),
                            "the settlement names the raised condition"),
                    () -> assertEquals(facts, settlement.facts(), "the settlement carries the raw result facts"));
        }

        @Test
        @DisplayName("Should settle an unmapped reason on the routed exit without a raised condition")
        void shouldSettleUnmappedReasonWithoutCondition() {
            var reasons = new ReasonFixtures();
            reasons.check.then(CheckOutcome.FAILED, CheckReason.TESTS_RED, Map.of());

            var step = reasons.engine(ReasonFixtures.autoUnit()).start(JournalScopeType.PLAN, PLAN_ID);

            var settlement = step.settlements().getFirst();
            assertAll(
                    () -> assertInstanceOf(EngineStep.Ended.class, step, "the unmapped reason takes its transition"),
                    () -> assertEquals(CheckReason.TESTS_RED.wireName(), settlement.reason(),
                            "the settlement carries the result's reason"),
                    () -> assertNull(settlement.raisedCondition(), "the routed exit raises no condition"));
        }
    }

    private static Map<String, Object> fact() {
        return Map.of(Generators.letterStrings(3, 12).next(), Generators.letterStrings(1, 40).next());
    }
}
