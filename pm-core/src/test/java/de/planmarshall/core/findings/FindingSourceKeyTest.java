/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.findings;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The key of the findings registry over the store's source vocabulary (PM-IMPL-1).
 */
@DisplayName("Finding source key")
class FindingSourceKeyTest {

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"abc", "analysis-provider", "a1-b2", "0x9"})
        @DisplayName("an analysis key carries a provider id of the pattern")
        void analysisKey(String provider) {
            var key = FindingSourceKey.analysis(provider);

            assertAll(
                    () -> assertEquals(FindingSourceKind.ANALYSIS, key.kind()),
                    () -> assertEquals(provider, key.provider()),
                    () -> assertTrue(FindingSourceKey.isProviderId(provider)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = FindingSourceKind.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYSIS")
        @DisplayName("a key of every other kind has no provider")
        void otherKind(FindingSourceKind kind) {
            assertEquals(new FindingSourceKey(kind, null), FindingSourceKey.of(kind));
        }

        @Test
        @DisplayName("valid keys are equal by value")
        void equalByValue() {
            assertAll(
                    () -> assertEquals(FindingSourceKey.analysis("provider-a"),
                            new FindingSourceKey(FindingSourceKind.ANALYSIS, "provider-a")),
                    () -> assertEquals(FindingSourceKey.analysis("provider-a").hashCode(),
                            FindingSourceKey.analysis("provider-a").hashCode()),
                    () -> assertNotEquals(FindingSourceKey.analysis("provider-a"),
                            FindingSourceKey.analysis("provider-b")),
                    () -> assertEquals(FindingSourceKey.of(FindingSourceKind.QGATE),
                            FindingSourceKey.of(FindingSourceKind.QGATE)),
                    () -> assertNotEquals(FindingSourceKey.of(FindingSourceKind.QGATE),
                            FindingSourceKey.of(FindingSourceKind.CI)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @Test
        @DisplayName("an analysis key without a provider is refused")
        void analysisWithoutProvider() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> FindingSourceKey.of(FindingSourceKind.ANALYSIS)),
                    () -> assertThrows(IllegalArgumentException.class, () -> FindingSourceKey.analysis(null)));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = FindingSourceKind.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYSIS")
        @DisplayName("a key of another kind with a provider is refused")
        void otherKindWithProvider(FindingSourceKind kind) {
            assertThrows(IllegalArgumentException.class, () -> new FindingSourceKey(kind, "provider-a"));
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "ab", "Upper", "-leading", "trailing-", "with space", "under_score"})
        @DisplayName("a provider outside the pattern is refused")
        void providerPattern(String provider) {
            assertAll(
                    () -> assertFalse(FindingSourceKey.isProviderId(provider)),
                    () -> assertThrows(IllegalArgumentException.class, () -> FindingSourceKey.analysis(provider)));
        }

        @Test
        @DisplayName("a provider of 65 characters is refused, one of 64 accepted")
        void providerLength() {
            assertAll(
                    () -> assertTrue(FindingSourceKey.isProviderId("a".repeat(64))),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> FindingSourceKey.analysis("a".repeat(65))));
        }

        @Test
        @DisplayName("a key without a kind is refused")
        void missingKind() {
            assertThrows(NullPointerException.class, () -> new FindingSourceKey(null, null));
        }
    }

    @Nested
    @DisplayName("source kinds")
    class SourceKinds {

        @Test
        @DisplayName("the source vocabulary is the eleven wire names of the store, each found by its name")
        void wireNames() {
            var expected = new String[]{"analysis", "github_pr", "gitlab_mr", "ci", "self_review", "arch_gate",
                    "compiler", "qgate", "security_audit", "code_review", "dependency_delta"};

            var actual = Arrays.stream(FindingSourceKind.values()).map(FindingSourceKind::wireName)
                    .toArray(String[]::new);

            assertAll(
                    () -> assertEquals(Arrays.asList(expected), Arrays.asList(actual)),
                    () -> assertTrue(Arrays.stream(FindingSourceKind.values()).allMatch(
                            kind -> FindingSourceKind.fromWireName(kind.wireName()).equals(Optional.of(kind)))),
                    () -> assertEquals(Optional.empty(), FindingSourceKind.fromWireName("unknown")));
        }
    }
}
