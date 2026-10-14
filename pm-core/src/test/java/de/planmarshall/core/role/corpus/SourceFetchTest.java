/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The outcome of a fetch step (PM-WORK-6, PM-EXEC-5): its detail never carries the user information of a URI.
 */
@DisplayName("Source fetch outcome")
class SourceFetchTest {

    static Stream<Arguments> redactions() {
        return Stream.of(
                Arguments.of("a user", "https://user@host/x did not answer", "https://host/x did not answer"),
                Arguments.of("a user and a password", "https://user:secret@host/x", "https://host/x"),
                Arguments.of("a password with an unencoded @", "https://user:p@ss@host/x", "https://host/x"),
                Arguments.of("a password with two unencoded @", "fetch https://u:a@b@c@host:8443/o/r failed",
                        "fetch https://host:8443/o/r failed"),
                Arguments.of("an authority without a path", "https://user:p@ss@host", "https://host"),
                Arguments.of("two URIs", "https://a:p@q@one/x and ssh://b@two/y", "https://one/x and ssh://two/y"),
                Arguments.of("an @ in the path only", "https://host/o/r@v1", "https://host/o/r@v1"),
                Arguments.of("an @ in the query only", "https://host?to=a@b", "https://host?to=a@b"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("redactions")
    @DisplayName("the user information of every URI is removed up to the last @ of its authority")
    void redactsUserInformation(String name, String detail, String expected) {
        var fetch = SourceFetch.unreachable(detail);

        assertEquals(expected, fetch.detail());
    }

    @Test
    @DisplayName("no part of a password with an unencoded @ survives")
    void noPasswordTail() {
        var fetch = SourceFetch.pinMissing("https://user:p@ss@github.com/example/alpha has no commit");

        assertAll(
                () -> assertEquals("https://github.com/example/alpha has no commit", fetch.detail()),
                () -> assertFalse(fetch.detail().contains("ss@"), fetch::detail));
    }
}
