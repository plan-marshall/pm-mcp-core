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

import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;


import de.planmarshall.runtime.start.AuditChainVerification.BreakReason;
import de.planmarshall.runtime.start.AuditChainVerification.Outcome.Broken;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@DisplayName("The audit chain verification seam")
class AuditChainVerificationTest {

    private static final String DIAGNOSTIC = "logs/audit.jsonl line 3";

    @Test
    @DisplayName("names the closed set of reasons of the specification by their wire names")
    void shouldNameReasonsByWireName() {
        assertEquals(List.of("link_broken", "missing", "reset", "truncated", "head_mismatch"),
                Arrays.stream(BreakReason.values()).map(BreakReason::wireName).toList());
    }

    @Test
    @DisplayName("NONE reports the chain as not verified")
    void shouldNotVerifyWithoutChain() {
        assertEquals(new AuditChainVerification.Outcome.NotVerified("no audit chain"),
                AuditChainVerification.NONE.verify());
    }

    @Nested
    @DisplayName("a broken chain")
    class BrokenChain {

        @Test
        @DisplayName("a broken link carries its reason and first broken line")
        void shouldCarryLineOfBrokenLink() {
            var broken = Broken.linkBroken(3, DIAGNOSTIC);

            assertAll(
                    () -> assertEquals(BreakReason.LINK_BROKEN, broken.reason()),
                    () -> assertEquals(OptionalLong.of(3), broken.firstBrokenLine()),
                    () -> assertEquals(DIAGNOSTIC, broken.diagnostic()));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = BreakReason.class, names = "LINK_BROKEN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("every other reason carries no line")
        void shouldCarryNoLineForOtherReasons(BreakReason reason) {
            var broken = Broken.withoutLine(reason, DIAGNOSTIC);

            assertAll(
                    () -> assertEquals(reason, broken.reason()),
                    () -> assertEquals(OptionalLong.empty(), broken.firstBrokenLine()));
        }

        @Test
        @DisplayName("refuses a broken link without a line")
        void shouldRefuseBrokenLinkWithoutLine() {
            assertThrows(IllegalArgumentException.class,
                    () -> Broken.withoutLine(BreakReason.LINK_BROKEN, DIAGNOSTIC));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = BreakReason.class, names = "LINK_BROKEN", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("refuses a line for any reason other than link_broken")
        void shouldRefuseLineForOtherReasons(BreakReason reason) {
            var line = OptionalLong.of(3);

            assertThrows(IllegalArgumentException.class, () -> new Broken(reason, line, DIAGNOSTIC));
        }

        @Test
        @DisplayName("refuses a first broken line below 1")
        void shouldRefuseLineBelowOne() {
            assertThrows(IllegalArgumentException.class, () -> Broken.linkBroken(0, DIAGNOSTIC));
        }

        @Test
        @DisplayName("refuses a missing component")
        void shouldRefuseMissingComponent() {
            var line = OptionalLong.of(3);

            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new Broken(null, line, DIAGNOSTIC)),
                    () -> assertThrows(NullPointerException.class,
                            () -> new Broken(BreakReason.LINK_BROKEN, null, DIAGNOSTIC)),
                    () -> assertThrows(NullPointerException.class, () -> Broken.linkBroken(3, null)));
        }
    }
}
