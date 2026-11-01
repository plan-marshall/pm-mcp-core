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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.corpus.JsonValue;
import de.planmarshall.core.store.DerivedFact;
import de.planmarshall.core.store.DerivedFactKind;
import de.planmarshall.core.store.DerivedFactReading;
import de.planmarshall.workflow.ast.GuardResult;

/**
 * The bridge from a reading of a derived fact to the three-valued guard result (PM-IMPL-5; Store Schemas § Derived
 * Facts Store, bullet <em>Failure</em>): a current fact hands its value to the guard, and every other reading is
 * {@code CouldNotEvaluate}, never a pass.
 */
@EnableGeneratorController
@DisplayName("Derived fact guards")
class DerivedFactGuardsTest {

    private static final Instant NOW = Instant.parse("2026-10-29T12:00:00Z");

    /** A schema-valid payload; the member value is generated. */
    private static final String PAYLOAD = "{\"name\":\"" + Generators.letterStrings(1, 20).next() + "\"}";

    private static DerivedFact fact(DerivedFactKind kind, DerivedFact.State state, String value, String reason) {
        return DerivedFact.builder()
                .kind(kind)
                .value(value == null ? null : JsonValue.parse(value.getBytes(UTF_8)))
                .source(new DerivedFact.Source("fixture", DerivedFact.DerivationClass.LOCAL))
                .inputDigest("a".repeat(64))
                .derivedAt(NOW)
                .state(state)
                .failure(reason == null ? null : new DerivedFact.Failure(reason, NOW))
                .build();
    }

    private static GuardResult evaluatedCountingGuardCalls(DerivedFactReading reading, AtomicInteger calls) {
        return DerivedFactGuards.evaluate(reading, _ -> {
            calls.incrementAndGet();
            return new GuardResult.Held();
        });
    }

    @Nested
    @DisplayName("a current fact")
    class CurrentFact {

        @Test
        @DisplayName("hands its value to the guard and answers the guard's result")
        void handsTheValueToTheGuard() {
            var fact = fact(DerivedFactKind.PUSH_REMOTE, DerivedFact.State.CURRENT, PAYLOAD, null);
            var reading = new DerivedFactReading.Value(fact);
            var received = new AtomicReference<DerivedFact>();

            assertAll(
                    () -> assertInstanceOf(GuardResult.Held.class, DerivedFactGuards.evaluate(reading, passed -> {
                        received.set(passed);
                        return new GuardResult.Held();
                    }), "The guard's held result is answered"),
                    () -> assertSame(fact, received.get(), "The reading's fact is handed to the guard"),
                    () -> assertInstanceOf(GuardResult.NotHeld.class,
                            DerivedFactGuards.evaluate(reading, _ -> new GuardResult.NotHeld()),
                            "The guard's not-held result is answered"));
        }

        @Test
        @DisplayName("a guard that answers null is a programming fault")
        void nullGuardResultIsRefused() {
            var reading = new DerivedFactReading.Value(
                    fact(DerivedFactKind.PUSH_REMOTE, DerivedFact.State.CURRENT, PAYLOAD, null));

            assertThrows(NullPointerException.class, () -> DerivedFactGuards.evaluate(reading, _ -> null),
                    "A null guard result is refused");
        }
    }

    @Nested
    @DisplayName("a fact that cannot be evaluated")
    class CouldNotEvaluateFact {

        @Test
        @DisplayName("a stale fact maps to its reason")
        void stale() {
            var reading = new DerivedFactReading.Indeterminate("push_remote: the provider refused");
            var calls = new AtomicInteger();

            var result = assertInstanceOf(GuardResult.CouldNotEvaluate.class,
                    evaluatedCountingGuardCalls(reading, calls), "A stale fact cannot be evaluated");

            assertAll(
                    () -> assertEquals("push_remote: the provider refused", result.reason(),
                            "The reading's reason is carried"),
                    () -> assertEquals(0, calls.get(), "The guard is not invoked"));
        }

        @Test
        @DisplayName("a failed fact maps to its reason")
        void failed() {
            var reading = new DerivedFactReading.Indeterminate("push_remote: no remote configured");
            var calls = new AtomicInteger();

            var result = assertInstanceOf(GuardResult.CouldNotEvaluate.class,
                    evaluatedCountingGuardCalls(reading, calls), "A failed fact cannot be evaluated");

            assertAll(
                    () -> assertTrue(result.reason().contains("no remote configured"), "The failure text is carried"),
                    () -> assertEquals(0, calls.get(), "The guard is not invoked"));
        }

        @Test
        @DisplayName("an expired fact maps to its reason")
        void expired() {
            var reading = new DerivedFactReading.Expired();
            var calls = new AtomicInteger();

            var result = assertInstanceOf(GuardResult.CouldNotEvaluate.class,
                    evaluatedCountingGuardCalls(reading, calls), "An expired fact cannot be evaluated");

            assertAll(
                    () -> assertTrue(result.reason().contains("validity period"), "The expiry reason is carried"),
                    () -> assertEquals(0, calls.get(), "The guard is not invoked"));
        }

        @Test
        @DisplayName("an absent fact maps to its reason")
        void absent() {
            var reading = new DerivedFactReading.Absent();
            var calls = new AtomicInteger();

            var result = assertInstanceOf(GuardResult.CouldNotEvaluate.class,
                    evaluatedCountingGuardCalls(reading, calls), "An absent fact cannot be evaluated");

            assertAll(
                    () -> assertTrue(result.reason().contains("absent"), "The absence reason is carried"),
                    () -> assertEquals(0, calls.get(), "The guard is not invoked"));
        }
    }

    @Nested
    @DisplayName("the arguments")
    class Arguments {

        @Test
        @DisplayName("a missing argument is refused")
        void missingArgument() {
            var reading = new DerivedFactReading.Absent();

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> DerivedFactGuards.evaluate(null, _ -> {
                        return new GuardResult.Held();
                    }), "A missing reading is refused"),
                    () -> assertThrows(NullPointerException.class, () -> DerivedFactGuards.evaluate(reading, null),
                            "A missing guard is refused"));
        }
    }
}
