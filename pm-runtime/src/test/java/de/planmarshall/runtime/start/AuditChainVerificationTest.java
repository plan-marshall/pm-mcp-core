/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.runtime.start.AuditChainVerification.BreakReason;
import de.planmarshall.runtime.start.AuditChainVerification.Outcome.Broken;

@EnableGeneratorController
@DisplayName("The audit chain verification seam")
class AuditChainVerificationTest {

    private static String diagnostic() {
        return Generators.nonEmptyStrings().next();
    }

    private static int line() {
        return Generators.integers(1, 100_000).next();
    }

    @Test
    @DisplayName("names the closed set of reasons of the specification by their wire names (contract literals)")
    void shouldNameReasonsByWireName() {
        assertEquals(List.of("link_broken", "missing", "reset", "truncated", "head_mismatch"),
                Arrays.stream(BreakReason.values()).map(BreakReason::wireName).toList(),
                "the reasons must carry the wire names of the spec");
    }

    @Test
    @DisplayName("NONE reports the chain as not verified (the reason is the specified contract text)")
    void shouldNotVerifyWithoutChain() {
        assertEquals(new AuditChainVerification.Outcome.NotVerified("no audit chain"),
                AuditChainVerification.NONE.verify(), "NONE must report the chain as not verified");
    }

    @Nested
    @DisplayName("a broken chain")
    class BrokenChain {

        @Test
        @DisplayName("a broken link carries its reason and first broken line")
        void shouldCarryLineOfBrokenLink() {
            var line = line();
            var diagnostic = diagnostic();

            var broken = Broken.linkBroken(line, diagnostic);

            assertAll(
                    () -> assertEquals(BreakReason.LINK_BROKEN, broken.reason(),
                            "a broken link must carry its reason"),
                    () -> assertEquals(Long.valueOf(line), broken.firstBrokenLine(),
                            "a broken link must carry its first broken line"),
                    () -> assertEquals(diagnostic, broken.diagnostic(),
                            "a broken link must carry its diagnostic"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = BreakReason.class, names = "LINK_BROKEN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("every other reason carries no line")
        void shouldCarryNoLineForOtherReasons(BreakReason reason) {
            var broken = Broken.withoutLine(reason, diagnostic());

            assertAll(
                    () -> assertEquals(reason, broken.reason(), "the broken chain must carry its reason"),
                    () -> assertNull(broken.firstBrokenLine(), "a reason other than link_broken has no line"));
        }

        @Test
        @DisplayName("refuses a broken link without a line, with the specified reason text")
        void shouldRefuseBrokenLinkWithoutLine() {
            var diagnostic = diagnostic();

            var withoutLine = assertThrows(IllegalArgumentException.class,
                    () -> Broken.withoutLine(BreakReason.LINK_BROKEN, diagnostic),
                    "a broken link without a line must be refused");
            var nullLine = assertThrows(IllegalArgumentException.class,
                    () -> new Broken(BreakReason.LINK_BROKEN, null, diagnostic),
                    "a broken link with a null line is refused");

            // the reason text is the contract of the constructor's refusal
            assertAll(
                    () -> assertEquals("A first broken line is given exactly for link_broken, not for link_broken",
                            withoutLine.getMessage(), "the factory refusal names the reason"),
                    () -> assertEquals(withoutLine.getMessage(), nullLine.getMessage(),
                            "the null line is refused like the factory"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = BreakReason.class, names = "LINK_BROKEN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("refuses a line for any reason other than link_broken, with the specified reason text")
        void shouldRefuseLineForOtherReasons(BreakReason reason) {
            var line = Long.valueOf(line());
            var diagnostic = diagnostic();

            var refusal = assertThrows(IllegalArgumentException.class, () -> new Broken(reason, line, diagnostic),
                    "a line for a reason other than link_broken is refused");

            // the reason text is the contract of the constructor's refusal
            assertEquals("A first broken line is given exactly for link_broken, not for " + reason.wireName(),
                    refusal.getMessage(), "the refusal names the reason that has no line");
        }

        @Test
        @DisplayName("refuses a first broken line below 1, with the specified reason text")
        void shouldRefuseLineBelowOne() {
            var line = Generators.integers(-100_000, 0).next();
            var diagnostic = diagnostic();

            var below = assertThrows(IllegalArgumentException.class, () -> Broken.linkBroken(line, diagnostic),
                    "a first broken line below 1 must be refused");
            // Contract literal: zero is the invalid boundary of the one-based line contract
            var zero = assertThrows(IllegalArgumentException.class, () -> Broken.linkBroken(0, diagnostic),
                    "a first broken line of zero must be refused");

            // the reason text is the contract of the constructor's refusal
            assertAll(
                    () -> assertEquals("The first broken line is counted from 1: " + line, below.getMessage(),
                            "the refusal names the line below 1"),
                    () -> assertEquals("The first broken line is counted from 1: 0", zero.getMessage(),
                            "the refusal names the line zero"));
        }

        @Test
        @DisplayName("refuses a missing reason or diagnostic, naming the missing component")
        void shouldRefuseMissingComponent() {
            var line = Long.valueOf(line());
            var lineNumber = line();
            var diagnostic = diagnostic();

            var missingReason = assertThrows(NullPointerException.class, () -> new Broken(null, line, diagnostic),
                    "a missing reason must be refused");
            var missingDiagnostic = assertThrows(NullPointerException.class,
                    () -> new Broken(BreakReason.LINK_BROKEN, line, null),
                    "a missing diagnostic must be refused");
            var factoryDiagnostic = assertThrows(NullPointerException.class,
                    () -> Broken.linkBroken(lineNumber, null),
                    "the factory refuses a missing diagnostic");

            // the component name is the contract of the constructor's refusal
            assertAll(
                    () -> assertEquals("reason", missingReason.getMessage(),
                            "the refusal names the missing reason"),
                    () -> assertEquals("diagnostic", missingDiagnostic.getMessage(),
                            "the refusal names the missing diagnostic"),
                    () -> assertEquals(missingDiagnostic.getMessage(), factoryDiagnostic.getMessage(),
                            "the factory refuses like the constructor"));
        }
    }
}
