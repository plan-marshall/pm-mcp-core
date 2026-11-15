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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import java.util.Locale;
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
@EnableGeneratorController
@DisplayName("Finding source key")
class FindingSourceKeyTest {

    private static final String PROVIDER = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    private static final String OTHER_PROVIDER = "q" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"abc", "analysis-provider", "a1-b2", "0x9"})
        @DisplayName("an analysis key carries a provider id of the pattern")
        void analysisKey(String provider) {
            var key = FindingSourceKey.analysis(provider);

            assertAll(
                    () -> assertEquals(FindingSourceKind.ANALYSIS, key.kind(),
                            "analysis key carries the analysis kind"),
                    () -> assertEquals(provider, key.provider(), "analysis key keeps its provider id"),
                    () -> assertTrue(FindingSourceKey.isProviderId(provider),
                            "provider id matches the pattern"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = FindingSourceKind.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYSIS")
        @DisplayName("a key of every other kind has no provider")
        void otherKind(FindingSourceKind kind) {
            assertEquals(new FindingSourceKey(kind, null), FindingSourceKey.of(kind),
                    "key of another kind has no provider");
        }

        @Test
        @DisplayName("valid keys are equal by value")
        void equalByValue() {
            assertAll(
                    () -> assertEquals(FindingSourceKey.analysis(PROVIDER),
                            new FindingSourceKey(FindingSourceKind.ANALYSIS, PROVIDER),
                            "keys with the same content are equal"),
                    () -> assertEquals(FindingSourceKey.analysis(PROVIDER).hashCode(),
                            FindingSourceKey.analysis(PROVIDER).hashCode(), "equal keys share their hash code"),
                    () -> assertNotEquals(FindingSourceKey.analysis(PROVIDER),
                            FindingSourceKey.analysis(OTHER_PROVIDER), "keys of different providers differ"),
                    () -> assertEquals(FindingSourceKey.of(FindingSourceKind.QGATE),
                            FindingSourceKey.of(FindingSourceKind.QGATE), "qgate keys of the same kind are equal"),
                    () -> assertNotEquals(FindingSourceKey.of(FindingSourceKind.QGATE),
                            FindingSourceKey.of(FindingSourceKind.CI), "qgate and ci keys differ"));
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
                            () -> FindingSourceKey.of(FindingSourceKind.ANALYSIS),
                            "analysis key without a provider is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> FindingSourceKey.analysis(null),
                            "analysis key with a null provider is refused"));
        }

        @ParameterizedTest(name = "{0}")
        @EnumSource(value = FindingSourceKind.class, mode = EnumSource.Mode.EXCLUDE, names = "ANALYSIS")
        @DisplayName("a key of another kind with a provider is refused")
        void otherKindWithProvider(FindingSourceKind kind) {
            assertThrows(IllegalArgumentException.class, () -> new FindingSourceKey(kind, PROVIDER),
                    "other kind with a provider is refused");
        }

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"", "ab", "Upper", "-leading", "trailing-", "with space", "under_score"})
        @DisplayName("a provider outside the pattern is refused")
        void providerPattern(String provider) {
            assertAll(
                    () -> assertFalse(FindingSourceKey.isProviderId(provider),
                            "provider outside the id pattern is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> FindingSourceKey.analysis(provider),
                            "analysis key of an invalid provider is refused"));
        }

        @Test
        @DisplayName("a provider of 65 characters is refused, one of 64 accepted")
        void providerLength() {
            var longest = "a".repeat(64);
            var tooLong = "a".repeat(65);

            assertAll(
                    () -> assertTrue(FindingSourceKey.isProviderId(longest),
                            "64-character provider id is accepted"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> FindingSourceKey.analysis(tooLong),
                            "65-character provider id is refused"));
        }

        @Test
        @DisplayName("a key without a kind is refused")
        void missingKind() {
            assertThrows(NullPointerException.class, () -> new FindingSourceKey(null, null),
                    "key without a kind is refused");
        }
    }

    @Nested
    @DisplayName("source kinds")
    class SourceKinds {

        @Test
        @DisplayName("the source vocabulary is the thirteen wire names of the store, each found by its name")
        void wireNames() {
            var expected = new String[]{"analysis", "github_pr", "gitlab_mr", "ci", "self_review", "arch_gate",
                    "compiler", "qgate", "security_audit", "code_review", "dependency_delta", "fix_proof",
                    "added_lines"};

            var actual = Arrays.stream(FindingSourceKind.values()).map(FindingSourceKind::wireName)
                    .toArray(String[]::new);

            assertAll(
                    () -> assertEquals(Arrays.asList(expected), Arrays.asList(actual),
                            "wire names are the thirteen source kinds"),
                    () -> assertTrue(Arrays.stream(FindingSourceKind.values()).allMatch(
                            kind -> FindingSourceKind.fromWireName(kind.wireName()).equals(Optional.of(kind))),
                            "every source kind round-trips its wire name"),
                    () -> assertEquals(Optional.empty(), FindingSourceKind.fromWireName("unknown"),
                            "unknown wire name answers empty"));
        }
    }
}
