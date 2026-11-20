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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The {@code command_id} of a call frame (PLAN-V2-15, Deliverable 6): the grammar {@code ^cmd-[0-9a-f]{16}$},
 * the random draw, and the round-trip through {@link CommandId#parse(String)}.
 */
@EnableGeneratorController
@DisplayName("Command id")
class CommandIdTest {

    private static final Pattern FORM = Pattern.compile("^cmd-[0-9a-f]{16}$");

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"cmd-", "cmd-0123456789abcde", "cmd-0123456789abcdef0", "cmd-0123456789ABCDEF",
                "cmd-0123456789abcdeg", "cor-0123456789abcdef", "x-0123456789abcdef", "", "cmd- 0123456789abcdef",
                " cmd-0123456789abcdef", "cmd-0123456789abcdef "})
        @DisplayName("a value outside the grammar is no command id")
        void foreign(String value) {
            assertAll("A foreign value is refused as a command id",
                    () -> assertThrows(IllegalArgumentException.class, () -> new CommandId(value),
                            "The constructor refuses the foreign value"),
                    () -> assertEquals(Optional.empty(), CommandId.parse(value),
                            "Parsing the foreign value yields empty"));
        }

        @Test
        @DisplayName("a missing value is refused")
        void missing() {
            assertAll("A missing value is refused",
                    () -> assertThrows(NullPointerException.class, () -> new CommandId(null),
                            "A null value is refused by the constructor"),
                    () -> assertEquals(Optional.empty(), CommandId.parse(null),
                            "Parsing a null value yields empty"),
                    () -> assertThrows(NullPointerException.class, () -> CommandId.random(null),
                            "A null generator is refused by the draw"));
        }
    }

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @Test
        @DisplayName("a value of the grammar is accepted")
        void accepted() {
            var id = new CommandId("cmd-0123456789abcdef");

            assertAll("An accepted value round-trips",
                    () -> assertTrue(FORM.matcher(id.value()).matches(), "The value matches the grammar"),
                    () -> assertEquals("cmd-0123456789abcdef", id.toString(), "The string form is the value"),
                    () -> assertEquals(Optional.of(id), CommandId.parse("cmd-0123456789abcdef"),
                            "Parsing yields the id"));
        }

        @Test
        @DisplayName("random() draws a value of the grammar")
        void drawn() {
            var id = CommandId.random();

            assertTrue(FORM.matcher(id.value()).matches(), "A drawn id matches the grammar");
        }

        @Test
        @DisplayName("random(seeded) is deterministic")
        void seeded() {
            var gen = new SplittableRandom(42L);
            var id = CommandId.random(gen);

            assertAll("A seeded draw is deterministic",
                    () -> assertTrue(FORM.matcher(id.value()).matches(), "The drawn id matches the grammar"),
                    () -> assertEquals(id, CommandId.random(new SplittableRandom(42L)),
                            "Two draws with the same seed agree"));
        }

        @Test
        @DisplayName("every drawn value parses back to itself")
        void roundTrip() {
            var id = CommandId.random(new SplittableRandom(7L));

            assertEquals(Optional.of(id), CommandId.parse(id.toString()),
                    "A drawn id parses back to itself");
        }

        @Test
        @DisplayName("random(null) is refused")
        void nullGenerator() {
            assertThrows(NullPointerException.class, () -> CommandId.random((RandomGenerator) null),
                    "A null generator is refused");
        }

        @Test
        @DisplayName("a generated value of the grammar is accepted")
        void generatedAccepted() {
            var digits = new StringBuilder();
            for (var index = 0; index < 16; index++) {
                digits.append(Character.forDigit(Generators.integers(0, 15).next(), 16));
            }
            var value = "cmd-" + digits;

            assertEquals(Optional.of(new CommandId(value)), CommandId.parse(value),
                    "A generated value of the grammar parses back to itself");
        }
    }
}