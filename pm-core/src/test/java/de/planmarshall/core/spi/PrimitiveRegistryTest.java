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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.spi.PrimitiveRegistrationException.Reason;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayName("Registry of primitives")
class PrimitiveRegistryTest {

    static List<TestPrimitives.Refusal> refused() {
        return TestPrimitives.refused();
    }

    private static List<Primitive<?, ?>> wellFormedWithJobStarting() {
        var primitives = new ArrayList<>(TestPrimitives.wellFormed());
        primitives.add(new TestPrimitives.StartJob());
        return primitives;
    }

    private static PrimitiveRegistrationException refusalOf(List<? extends Primitive<?, ?>> primitives) {
        return assertThrows(PrimitiveRegistrationException.class, () -> new PrimitiveRegistry(primitives));
    }

    @Nested
    @DisplayName("Well-formed primitives register")
    class Registration {

        @Test
        @DisplayName("a primitive of every outcome class and a job-starting one are found by their ids")
        void findsEveryRegisteredPrimitive() {
            var primitives = wellFormedWithJobStarting();

            var registry = new PrimitiveRegistry(primitives);

            assertAll(primitives.stream().<Executable>map(primitive -> () -> assertSame(primitive,
                    registry.find(primitive.id()).orElseThrow(), primitive.id())));
        }

        @Test
        @DisplayName("the ids are those of the primitives, in the order they were passed")
        void listsIdsInOrder() {
            var registry = new PrimitiveRegistry(wellFormedWithJobStarting());

            assertEquals(List.of("test.complete", "test.skip-absent", "test.fail", "test.observe", "test.hand-back",
                    "test.leave", "test.start-job"), List.copyOf(registry.ids()));
        }

        @Test
        @DisplayName("an id no primitive has finds nothing")
        void findsNothingForUnknownId() {
            var registry = new PrimitiveRegistry(TestPrimitives.wellFormed());

            assertEquals(Optional.empty(), registry.find("test.unknown"));
        }

        @Test
        @DisplayName("no primitives make an empty registry")
        void acceptsNoPrimitives() {
            var registry = new PrimitiveRegistry(List.of());

            assertEquals(Set.of(), registry.ids());
        }

        @Test
        @DisplayName("the set of ids cannot be changed")
        void idsAreUnmodifiable() {
            var ids = new PrimitiveRegistry(TestPrimitives.wellFormed()).ids();

            assertAll(
                    () -> assertThrows(UnsupportedOperationException.class, () -> ids.add("test.added")),
                    () -> assertThrows(UnsupportedOperationException.class, () -> ids.remove("test.fail")));
        }

        @Test
        @DisplayName("a primitive added to the collection afterwards is not registered")
        void keepsItsOwnPrimitives() {
            var primitives = new ArrayList<>(TestPrimitives.wellFormed());
            var registry = new PrimitiveRegistry(primitives);

            primitives.add(new TestPrimitives.StartJob());

            assertEquals(Optional.empty(), registry.find("test.start-job"));
        }

        @Test
        @DisplayName("a primitive with an attempt cap registers when its exhaustion outcome is a failure")
        void acceptsAttemptCapWithFailureExhaustion() {
            var capped = TestPrimitives.capped();

            var registry = new PrimitiveRegistry(List.of(capped));

            assertSame(capped, registry.find("test.retry-capped").orElseThrow());
        }

        @Test
        @DisplayName("a missing collection and a missing primitive are refused")
        void refusesNull() {
            var withNull = Arrays.<Primitive<?, ?>>asList(TestPrimitives.capped(), null);

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new PrimitiveRegistry(null)),
                    () -> assertThrows(NullPointerException.class, () -> new PrimitiveRegistry(withNull)));
        }
    }

    @Nested
    @DisplayName("Malformed primitives are refused at registration")
    class Refusals {

        @Test
        @DisplayName("the malformed primitives cover every reason of a refusal")
        void fixtureCoversEveryReason() {
            var covered = refused().stream().map(TestPrimitives.Refusal::reason).toList();

            assertEquals(EnumSet.allOf(Reason.class), EnumSet.copyOf(covered));
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("de.planmarshall.core.spi.PrimitiveRegistryTest#refused")
        @DisplayName("the refusal carries its reason and the id of the refused primitive")
        void refusesWithReason(TestPrimitives.Refusal refusal) {
            var thrown = refusalOf(refusal.primitives());

            assertAll(
                    () -> assertEquals(refusal.reason(), thrown.getReason()),
                    () -> assertEquals(refusal.refusedId(), thrown.getPrimitiveId()),
                    () -> assertTrue(thrown.getMessage().startsWith(refusal.reason() + ": primitive '"
                            + refusal.refusedId() + "' "), thrown.getMessage()));
        }

        @Test
        @DisplayName("a second primitive with the id of the first is refused")
        void refusesDuplicateId() {
            var first = TestPrimitives.fixed("git.commit", TestPrimitives.FailParams.class,
                    TestPrimitives.FailOutcome.class, TestPrimitives.FailOutcome.FAILED);
            var second = TestPrimitives.fixed("git.commit", TestPrimitives.SkipParams.class,
                    TestPrimitives.SkipOutcome.class, TestPrimitives.SkipOutcome.SKIPPED_NO_INPUT);

            var thrown = refusalOf(List.of(first, second));

            assertAll(
                    () -> assertEquals(Reason.DUPLICATE_ID, thrown.getReason()),
                    () -> assertEquals("git.commit", thrown.getPrimitiveId()));
        }

        @Test
        @DisplayName("an outcome type without a constant is refused as an open outcome set")
        void refusesOpenOutcomeSet() {
            var open = new TestPrimitives.Fixed<TestPrimitives.FailParams, TestPrimitives.NoOutcome>("git.commit",
                    TestPrimitives.FailParams.class, TestPrimitives.NoOutcome.class, null);

            var thrown = refusalOf(List.of(open));

            assertEquals(Reason.OPEN_OUTCOME_SET, thrown.getReason());
        }

        @Test
        @DisplayName("a primitive that enqueues a job without a job-starting outcome is refused")
        void refusesUndeclaredJobSideEffect() {
            var thrown = refusalOf(List.of(TestPrimitives.enqueuingWithoutJobStartingOutcome()));

            assertEquals(Reason.UNDECLARED_JOB_SIDE_EFFECT, thrown.getReason());
        }

        /**
         * A cycle the engine chains ends only because the measure of a primitive on it runs out. With an attempt
         * cap that is the outcome the primitive returns once the cap is spent: if that outcome were a success, the
         * workflow could route it back into the cycle and the cap would end nothing (PM-IMPL-5).
         */
        @Test
        @DisplayName("an attempt cap whose exhaustion outcome is not of class FAILURE is refused")
        void refusesAttemptCapWithoutFailureExhaustion() {
            var thrown = refusalOf(List.of(TestPrimitives.cappedWithSuccessAsExhaustion()));

            assertEquals(Reason.ATTEMPT_CAP_WITHOUT_FAILURE_EXHAUSTION, thrown.getReason());
        }

        /**
         * A retry is worth a pass only when an event can have changed the outcome since the last one. A primitive
         * that leaves the event open cannot be told from one that waits for nothing, so it is refused before a
         * workflow can retry it (PM-IMPL-5).
         */
        @Test
        @DisplayName("a primitive that declares no awaited event is refused")
        void refusesMissingAwaitedEvent() {
            var thrown = refusalOf(List.of(TestPrimitives.awaitingNothingDeclared()));

            assertEquals(Reason.MISSING_AWAITED_EVENT, thrown.getReason());
        }

        @Test
        @DisplayName("one malformed primitive after well-formed ones leaves no registry, and is the one named")
        void refusesAllForOneMalformed() {
            var primitives = wellFormedWithJobStarting();
            primitives.add(TestPrimitives.awaitingNothingDeclared());

            var thrown = refusalOf(primitives);

            assertEquals("test.await-undeclared", thrown.getPrimitiveId());
        }
    }
}
