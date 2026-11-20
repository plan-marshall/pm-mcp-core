/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.id;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code correlation_id} of a workflow engine advance (PLAN-V2-15, Deliverable 6): the grammar
 * {@code ^cor-[0-9a-f]{16}$}, the random draw, and the round-trip through {@link CorrelationId#parse(String)}.
 */
@EnableGeneratorController
@DisplayName("Correlation id")
class CorrelationIdTest {

    private static final Pattern FORM = Pattern.compile("^cor-[0-9a-f]{16}$");

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"cor-", "cor-0123456789abcde", "cor-0123456789abcdef0", "cor-0123456789ABCDEF",
                "cor-0123456789abcdeg", "cmd-0123456789abcdef", "x-0123456789abcdef", "", "cor- 0123456789abcdef",
                " cor-0123456789abcdef", "cor-0123456789abcdef "})
        @DisplayName("a value outside the grammar is no correlation id")
        void foreign(String value) {
            assertAll("A foreign value is refused as a correlation id",
                    () -> assertThrows(IllegalArgumentException.class, () -> new CorrelationId(value),
                            "The constructor refuses the foreign value"),
                    () -> assertEquals(Optional.empty(), CorrelationId.parse(value),
                            "Parsing the foreign value yields empty"));
        }

        @Test
        @DisplayName("a missing value is refused")
        void missing() {
            assertAll("A missing value is refused",
                    () -> assertThrows(NullPointerException.class, () -> new CorrelationId(null),
                            "A null value is refused by the constructor"),
                    () -> assertEquals(Optional.empty(), CorrelationId.parse(null),
                            "Parsing a null value yields empty"),
                    () -> assertThrows(NullPointerException.class, () -> CorrelationId.random(null),
                            "A null generator is refused by the draw"));
        }
    }

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @Test
        @DisplayName("a value of the grammar is accepted")
        void accepted() {
            var id = new CorrelationId("cor-0123456789abcdef");

            assertAll("An accepted value round-trips",
                    () -> assertTrue(FORM.matcher(id.value()).matches(), "The value matches the grammar"),
                    () -> assertEquals("cor-0123456789abcdef", id.toString(), "The string form is the value"),
                    () -> assertEquals(Optional.of(id), CorrelationId.parse("cor-0123456789abcdef"),
                            "Parsing yields the id"));
        }

        @Test
        @DisplayName("random() draws a value of the grammar")
        void drawn() {
            var id = CorrelationId.random();

            assertTrue(FORM.matcher(id.value()).matches(), "A drawn id matches the grammar");
        }

        @Test
        @DisplayName("random(seeded) is deterministic")
        void seeded() {
            var id = CorrelationId.random(new SplittableRandom(42L));

            assertAll("A seeded draw is deterministic",
                    () -> assertTrue(FORM.matcher(id.value()).matches(), "The drawn id matches the grammar"),
                    () -> assertEquals(id, CorrelationId.random(new SplittableRandom(42L)),
                            "Two draws with the same seed agree"));
        }

        @Test
        @DisplayName("every drawn value parses back to itself")
        void roundTrip() {
            var id = CorrelationId.random(new SplittableRandom(7L));

            assertEquals(Optional.of(id), CorrelationId.parse(id.toString()),
                    "A drawn id parses back to itself");
        }

        @Test
        @DisplayName("random(null) is refused")
        void nullGenerator() {
            assertThrows(NullPointerException.class, () -> CorrelationId.random((RandomGenerator) null),
                    "A null generator is refused");
        }

        @Test
        @DisplayName("a generated value of the grammar is accepted")
        void generatedAccepted() {
            var digits = new StringBuilder();
            for (var index = 0; index < 16; index++) {
                digits.append(Character.forDigit(Generators.integers(0, 15).next(), 16));
            }
            var value = "cor-" + digits;

            assertEquals(Optional.of(new CorrelationId(value)), CorrelationId.parse(value),
                    "A generated value of the grammar parses back to itself");
        }
    }
}