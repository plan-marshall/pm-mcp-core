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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The scope of a finding source and the target of code analysis (PM-IMPL-1).
 */
@DisplayName("Finding scope")
class FindingScopeTest {

    private static final String SHA_1 = "0123456789abcdef0123456789abcdef01234567";
    private static final String SHA_256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    @Nested
    @DisplayName("accepted")
    class Accepted {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {SHA_1, SHA_256})
        @DisplayName("a commit scope of a 40- or 64-digit commit has no pull-request number")
        void commit(String sha) {
            var scope = FindingScope.commit(sha);

            assertAll(
                    () -> assertEquals(FindingScope.Kind.COMMIT, scope.kind()),
                    () -> assertEquals(sha, scope.commitSha()),
                    () -> assertNull(scope.prNumber()));
        }

        @Test
        @DisplayName("a pull-request scope carries its number")
        void pullRequest() {
            var scope = FindingScope.pullRequest(SHA_1, 1);

            assertAll(
                    () -> assertEquals(FindingScope.Kind.PULL_REQUEST, scope.kind()),
                    () -> assertEquals(1, scope.prNumber()));
        }

        @ParameterizedTest(name = "{0} is {1} on the wire")
        @CsvSource({"COMMIT, commit", "PULL_REQUEST, pull_request"})
        @DisplayName("the kinds carry their wire names and are found by them")
        void wireNames(FindingScope.Kind kind, String wireName) {
            assertAll(
                    () -> assertEquals(wireName, kind.wireName()),
                    () -> assertEquals(Optional.of(kind), FindingScope.Kind.fromWireName(wireName)));
        }
    }

    @Nested
    @DisplayName("refusals")
    class Refusals {

        @ParameterizedTest(name = "\"{0}\"")
        @ValueSource(strings = {"0123456789abcdef", "0123456789abcdef0123456789abcdef012345678",
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde"})
        @DisplayName("a commit of a wrong digest length is refused")
        void wrongLength(String sha) {
            assertThrows(IllegalArgumentException.class, () -> FindingScope.commit(sha));
        }

        @Test
        @DisplayName("a commit in upper-case hexadecimal digits is refused")
        void upperCase() {
            var upper = SHA_1.toUpperCase();

            assertThrows(IllegalArgumentException.class, () -> FindingScope.commit(upper));
        }

        @Test
        @DisplayName("a pull-request scope without its number is refused")
        void missingPrNumber() {
            assertThrows(IllegalArgumentException.class,
                    () -> new FindingScope(FindingScope.Kind.PULL_REQUEST, SHA_1, null));
        }

        @Test
        @DisplayName("a commit scope with a pull-request number is refused")
        void extraPrNumber() {
            assertThrows(IllegalArgumentException.class, () -> new FindingScope(FindingScope.Kind.COMMIT, SHA_1, 3));
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(ints = {0, -1})
        @DisplayName("a pull-request number below 1 is refused")
        void prNumberBelowOne(int prNumber) {
            assertThrows(IllegalArgumentException.class, () -> FindingScope.pullRequest(SHA_1, prNumber));
        }

        @Test
        @DisplayName("a missing kind or commit is refused")
        void missingMembers() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> new FindingScope(null, SHA_1, null)),
                    () -> assertThrows(NullPointerException.class, () -> FindingScope.commit(null)));
        }
    }
}
