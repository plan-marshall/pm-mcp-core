/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.envelope;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.workflow.envelope.EnvelopeBudget.Resolution;
import de.planmarshall.workflow.envelope.EnvelopeBudget.Source;

/**
 * The resolution of the per-envelope budget: the precedence of its sources, the cap of the fallbacks at the
 * long-context threshold and the flag on kept values above it.
 *
 * @since 0.1
 */
@EnableGeneratorController
@DisplayName("EnvelopeBudget")
class EnvelopeBudgetTest {

    /** The compiled fallback of the specification, a contract value. */
    private static final long COMPILED_FALLBACK = 400_000L;

    private static long tokens() {
        return Generators.longs(1, 2_000_000).next();
    }

    private static long above(long threshold) {
        return threshold + Generators.longs(1, 1_000_000).next();
    }

    private static long atMost(long threshold) {
        return Generators.longs(1, threshold).next();
    }

    private static void assertResolution(Resolution resolution, long tokens, Source source, boolean capped,
            boolean aboveThreshold) {
        assertAll("The resolved budget",
                () -> assertEquals(tokens, resolution.tokens(), "The budget has the expected tokens"),
                () -> assertEquals(source, resolution.source(), "The budget comes from the expected source"),
                () -> assertEquals(capped, resolution.capped(), "The budget is capped only where expected"),
                () -> assertEquals(aboveThreshold, resolution.aboveThreshold(),
                        "The above-threshold flag is as expected"));
    }

    @Nested
    @DisplayName("the precedence")
    class Precedence {

        @Test
        @DisplayName("takes the operator value over the role and the profile")
        void takesTheOperatorValue() {
            long operator = tokens();

            var resolution = EnvelopeBudget.resolve(operator, tokens(), tokens(), null);

            assertResolution(resolution, operator, Source.OPERATOR, false, false);
        }

        @Test
        @DisplayName("takes the role value over the profile without an operator value")
        void takesTheRoleValue() {
            long role = tokens();

            var resolution = EnvelopeBudget.resolve(null, role, tokens(), null);

            assertResolution(resolution, role, Source.ROLE, false, false);
        }

        @Test
        @DisplayName("takes the profile value over the compiled fallback without operator and role")
        void takesTheProfileValue() {
            long profile = tokens();

            var resolution = EnvelopeBudget.resolve(null, null, profile, null);

            assertResolution(resolution, profile, Source.PROFILE, false, false);
        }

        @Test
        @DisplayName("falls back to the compiled 400 000 tokens, contract value")
        void fallsBackToTheCompiledValue() {
            var resolution = EnvelopeBudget.resolve(null, null, null, null);

            assertAll("The compiled fallback",
                    () -> assertEquals(COMPILED_FALLBACK, EnvelopeBudget.COMPILED_FALLBACK_TOKENS,
                            "The compiled fallback is 400 000 tokens"),
                    () -> assertResolution(resolution, COMPILED_FALLBACK, Source.COMPILED, false, false));
        }
    }

    @Nested
    @DisplayName("the cap of the fallbacks")
    class Cap {

        @Test
        @DisplayName("caps a profile value above the threshold at the threshold")
        void capsTheProfileValue() {
            long threshold = tokens();

            var resolution = EnvelopeBudget.resolve(null, null, above(threshold), threshold);

            assertResolution(resolution, threshold, Source.PROFILE, true, false);
        }

        @Test
        @DisplayName("keeps a profile value up to the threshold")
        void keepsTheProfileValueUpToTheThreshold() {
            long threshold = tokens();
            long profile = atMost(threshold);

            var resolution = EnvelopeBudget.resolve(null, null, profile, threshold);

            assertResolution(resolution, profile, Source.PROFILE, false, false);
        }

        @Test
        @DisplayName("caps the compiled fallback at a threshold below it")
        void capsTheCompiledFallback() {
            long threshold = Generators.longs(1, COMPILED_FALLBACK - 1).next();

            var resolution = EnvelopeBudget.resolve(null, null, null, threshold);

            assertResolution(resolution, threshold, Source.COMPILED, true, false);
        }

        @Test
        @DisplayName("keeps the compiled fallback at a threshold not below it")
        void keepsTheCompiledFallback() {
            long threshold = COMPILED_FALLBACK + Generators.longs(0, 1_000_000).next();

            var resolution = EnvelopeBudget.resolve(null, null, null, threshold);

            assertResolution(resolution, COMPILED_FALLBACK, Source.COMPILED, false, false);
        }
    }

    @Nested
    @DisplayName("the values above the threshold")
    class AboveThreshold {

        @Test
        @DisplayName("keeps an operator value above the threshold and flags it")
        void keepsAndFlagsTheOperatorValue() {
            long threshold = tokens();
            long operator = above(threshold);

            var resolution = EnvelopeBudget.resolve(operator, null, null, threshold);

            assertResolution(resolution, operator, Source.OPERATOR, false, true);
        }

        @Test
        @DisplayName("keeps a role value above the threshold and flags it")
        void keepsAndFlagsTheRoleValue() {
            long threshold = tokens();
            long role = above(threshold);

            var resolution = EnvelopeBudget.resolve(null, role, null, threshold);

            assertResolution(resolution, role, Source.ROLE, false, true);
        }

        @Test
        @DisplayName("does not flag an operator or role value up to the threshold")
        void doesNotFlagAValueUpToTheThreshold() {
            long threshold = tokens();

            var operator = EnvelopeBudget.resolve(atMost(threshold), null, null, threshold);
            var role = EnvelopeBudget.resolve(null, atMost(threshold), null, threshold);

            assertAll("Values up to the threshold",
                    () -> assertFalse(operator.aboveThreshold(), "An operator value in range is not flagged"),
                    () -> assertFalse(role.aboveThreshold(), "A role value in range is not flagged"));
        }

        @Test
        @DisplayName("neither caps nor flags any value without a threshold")
        void neitherCapsNorFlagsWithoutAThreshold() {
            long large = above(COMPILED_FALLBACK);

            var operator = EnvelopeBudget.resolve(large, null, null, null);
            var profile = EnvelopeBudget.resolve(null, null, large, null);

            assertAll("Values without a threshold",
                    () -> assertResolution(operator, large, Source.OPERATOR, false, false),
                    () -> assertResolution(profile, large, Source.PROFILE, false, false));
        }
    }

    @Nested
    @DisplayName("the refusal")
    class Refusal {

        @ParameterizedTest
        @ValueSource(strings = {"operator", "role", "profile", "threshold"})
        @DisplayName("refuses a given value that is not positive")
        void refusesANonPositiveValue(String input) {
            long notPositive = -Generators.longs(0, 1_000_000).next();

            var thrown = assertThrows(IllegalArgumentException.class, () -> EnvelopeBudget.resolve(
                    "operator".equals(input) ? notPositive : null,
                    "role".equals(input) ? notPositive : null,
                    "profile".equals(input) ? notPositive : null,
                    "threshold".equals(input) ? notPositive : null), "A value of zero or less is refused");

            assertTrue(thrown.getMessage().contains(String.valueOf(notPositive)),
                    "The refusal names the refused value");
        }
    }
}
