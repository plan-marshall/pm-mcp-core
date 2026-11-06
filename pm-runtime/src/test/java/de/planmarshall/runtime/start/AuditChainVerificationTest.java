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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
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
                    () -> assertEquals(OptionalLong.of(line), broken.firstBrokenLine(),
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
                    () -> assertEquals(OptionalLong.empty(), broken.firstBrokenLine(),
                            "a reason other than link_broken has no line"));
        }

        @Test
        @DisplayName("refuses a broken link without a line")
        void shouldRefuseBrokenLinkWithoutLine() {
            var diagnostic = diagnostic();

            assertThrows(IllegalArgumentException.class, () -> Broken.withoutLine(BreakReason.LINK_BROKEN, diagnostic),
                    "a broken link without a line must be refused");
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = BreakReason.class, names = "LINK_BROKEN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("refuses a line for any reason other than link_broken")
        void shouldRefuseLineForOtherReasons(BreakReason reason) {
            var line = OptionalLong.of(line());
            var diagnostic = diagnostic();

            assertThrows(IllegalArgumentException.class, () -> new Broken(reason, line, diagnostic),
                    "a line for a reason other than link_broken is refused");
        }

        @Test
        @DisplayName("refuses a first broken line below 1")
        void shouldRefuseLineBelowOne() {
            var line = Generators.integers(-100_000, 0).next();
            var diagnostic = diagnostic();

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> Broken.linkBroken(line, diagnostic),
                            "a first broken line below 1 must be refused"),
                    // Contract literal: zero is the invalid boundary of the one-based line contract
                    () -> assertThrows(IllegalArgumentException.class, () -> Broken.linkBroken(0, diagnostic),
                            "a first broken line of zero must be refused"));
        }

        @Test
        @DisplayName("refuses a missing component")
        void shouldRefuseMissingComponent() {
            var line = OptionalLong.of(line());
            var lineNumber = line();
            var diagnostic = diagnostic();

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new Broken(null, line, diagnostic),
                            "a missing reason must be refused"),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Broken(BreakReason.LINK_BROKEN, null, diagnostic),
                            "a missing line must be refused"),
                    () -> assertThrows(NullPointerException.class, () -> Broken.linkBroken(lineNumber, null),
                            "a missing diagnostic must be refused"));
        }
    }
}
