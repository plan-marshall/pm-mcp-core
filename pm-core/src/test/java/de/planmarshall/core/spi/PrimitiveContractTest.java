/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import de.planmarshall.core.spi.TestPrimitives.CompleteOutcome;
import de.planmarshall.core.spi.TestPrimitives.CompleteParams;
import de.planmarshall.core.spi.TestPrimitives.FailOutcome;
import de.planmarshall.core.spi.TestPrimitives.StartJobParams;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Contract of the primitive SPI")
class PrimitiveContractTest {

    private static final String PLAN_ID = "primitive-spi";

    static List<TestPrimitives.Case> oneForEveryOutcomeClass() {
        return TestPrimitives.oneForEveryOutcomeClass();
    }

    @Nested
    @DisplayName("A primitive of every outcome class runs through the SPI")
    class EveryOutcomeClass {

        @Test
        @DisplayName("the test primitives cover every outcome class exactly once")
        void fixtureCoversEveryOutcomeClass() {
            var covered = oneForEveryOutcomeClass().stream()
                    .map(TestPrimitives.Case::outcomeClass)
                    .toList();

            assertAll(
                    () -> assertEquals(OutcomeClass.values().length, covered.size()),
                    () -> assertEquals(EnumSet.allOf(OutcomeClass.class), EnumSet.copyOf(covered)));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.spi.PrimitiveContractTest#oneForEveryOutcomeClass")
        @DisplayName("execute returns the outcome with its class and its wire name")
        void returnsOutcomeOfItsClass(TestPrimitives.Case testCase) {
            var result = TestPrimitives.run(testCase.primitive(), TestPrimitives.scope(PLAN_ID),
                    testCase.parameters());

            assertAll(
                    () -> assertEquals(testCase.outcomeClass(), result.outcome().outcomeClass()),
                    () -> assertEquals(testCase.wireName(), result.outcome().wireName()));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.spi.PrimitiveContractTest#oneForEveryOutcomeClass")
        @DisplayName("the outcome a primitive returns is a constant of its declared outcome type")
        void outcomeBelongsToDeclaredType(TestPrimitives.Case testCase) {
            var result = TestPrimitives.run(testCase.primitive(), TestPrimitives.scope(PLAN_ID),
                    testCase.parameters());

            assertEquals(testCase.primitive().outcomeType(), result.outcome().getDeclaringClass());
        }

        /**
         * A step whose operation failed must never be recorded as done, and an operation whose result could not be
         * established must never be folded into a failure or a success (PM-IMPL-5). The step record is derived from
         * the class of the outcome, so the class has to reach the caller exactly as the primitive declared it.
         */
        @Test
        @DisplayName("a FAILURE and an INDETERMINATE outcome keep their own class")
        void failureAndIndeterminateKeepTheirClass() {
            var classesByWireName = oneForEveryOutcomeClass().stream()
                    .<PrimitiveOutcome>map(testCase -> TestPrimitives.run(testCase.primitive(), TestPrimitives.workspace(),
                            testCase.parameters()).outcome())
                    .collect(Collectors.toMap(PrimitiveOutcome::wireName, PrimitiveOutcome::outcomeClass));

            assertAll(
                    () -> assertEquals(OutcomeClass.FAILURE, classesByWireName.get("failed")),
                    () -> assertEquals(OutcomeClass.INDETERMINATE, classesByWireName.get("unobservable")),
                    () -> assertEquals(OutcomeClass.SUCCESS, classesByWireName.get("done")));
        }
    }

    @Nested
    @DisplayName("The context names the scope of the call")
    class Scope {

        @Test
        @DisplayName("a primitive reads the identifier of the plan or epic the call addresses")
        void planScope() {
            var result = new TestPrimitives.Complete().execute(TestPrimitives.scope(PLAN_ID),
                    new CompleteParams("request"));

            assertEquals(Map.of("subject", "request", "scope", PLAN_ID), result.updatedFacts());
        }

        @Test
        @DisplayName("the workspace scope has no identifier")
        void workspaceScope() {
            var result = new TestPrimitives.Complete().execute(TestPrimitives.workspace(),
                    new CompleteParams("request"));

            assertEquals(Map.of("subject", "request"), result.updatedFacts());
        }
    }

    @Nested
    @DisplayName("Defaults of the SPI")
    class Defaults {

        private final Primitive<?, ?> primitive = TestPrimitives.fixed("test.fail", TestPrimitives.FailParams.class,
                FailOutcome.class, FailOutcome.FAILED);

        @Test
        @DisplayName("a primitive that overrides nothing declares no side effect, no measure and no exhaustion")
        void primitiveDefaults() {
            assertAll(
                    () -> assertEquals(Set.of(), primitive.producedArtifacts()),
                    () -> assertEquals(Set.of(), primitive.requiredArtifacts()),
                    () -> assertEquals(Set.of(), primitive.waivableGates()),
                    () -> assertFalse(primitive.enqueuesJob()),
                    () -> assertEquals(CycleMeasure.NONE, primitive.cycleMeasure()),
                    () -> assertEquals(Optional.empty(), primitive.exhaustionOutcome()));
        }

        @Test
        @DisplayName("an outcome starts no job unless it says so")
        void outcomeStartsNoJob() {
            assertFalse(FailOutcome.FAILED.startsJob());
        }

        @Test
        @DisplayName("a job-starting primitive reports the job with its job-starting outcome")
        void jobStartingPrimitive() {
            var jobStarter = new TestPrimitives.StartJob();

            var result = jobStarter.execute(TestPrimitives.scope(PLAN_ID), new StartJobParams());

            assertAll(
                    () -> assertTrue(jobStarter.enqueuesJob()),
                    () -> assertTrue(result.outcome().startsJob()),
                    () -> assertEquals(Optional.of(TestPrimitives.SPAWNED_JOB_ID), result.spawnedJobId()));
        }
    }

    @Nested
    @DisplayName("The result of an execution")
    class Result {

        static Stream<Arguments> resultsWithNull() {
            var factWithoutValue = new HashMap<String, Object>();
            factWithoutValue.put("subject", null);
            var factWithoutName = new HashMap<String, Object>();
            factWithoutName.put(null, "request");
            return Stream.of(
                    Arguments.of("outcome", (Executable) () -> new PrimitiveResult<CompleteOutcome>(null, Map.of(),
                            Optional.empty())),
                    Arguments.of("updatedFacts", (Executable) () -> new PrimitiveResult<>(CompleteOutcome.DONE, null,
                            Optional.empty())),
                    Arguments.of("spawnedJobId", (Executable) () -> new PrimitiveResult<>(CompleteOutcome.DONE,
                            Map.of(), null)),
                    Arguments.of("value of a fact", (Executable) () -> new PrimitiveResult<>(CompleteOutcome.DONE,
                            factWithoutValue, Optional.empty())),
                    Arguments.of("name of a fact", (Executable) () -> new PrimitiveResult<>(CompleteOutcome.DONE,
                            factWithoutName, Optional.empty())));
        }

        @ParameterizedTest(name = "null {0}")
        @MethodSource("resultsWithNull")
        @DisplayName("refuses a null component and a null in a fact, naming it")
        void refusesNull(String refused, Executable construction) {
            var thrown = assertThrows(NullPointerException.class, construction);

            assertEquals(refused, thrown.getMessage());
        }

        @Test
        @DisplayName("keeps its own copy of the facts")
        void copiesFacts() {
            var facts = new HashMap<String, Object>();
            facts.put("subject", "request");
            var result = new PrimitiveResult<>(CompleteOutcome.DONE, facts, Optional.empty());

            facts.put("subject", "changed");
            facts.put("added", "later");

            assertEquals(Map.of("subject", "request"), result.updatedFacts());
        }

        @Test
        @DisplayName("hands out facts that cannot be changed")
        void factsAreUnmodifiable() {
            var result = new PrimitiveResult<>(CompleteOutcome.DONE, Map.of("subject", "request"), Optional.empty());
            var facts = result.updatedFacts();

            assertThrows(UnsupportedOperationException.class, () -> facts.put("added", "later"));
        }
    }
}
