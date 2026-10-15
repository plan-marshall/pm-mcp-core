/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The classification of a transport failure of {@code git.fetch-commit} (PM-WORK-6, PM-EXEC-5): the closed set of
 * wordings by which a remote that answered refuses a wanted commit is {@code UNKNOWN_REVISION}, so the import reports
 * the pin as missing; every other failure is {@code FAILED}, the remote did not answer.
 */
@DisplayName("Unserved want classification of JGitOperations")
class UnservedWantTest {

    private static final String SHA = "0123456789abcdef0123456789abcdef01234567";

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "upload-pack: not our ref " + SHA,
            "https://github.com/o/r: remote error: upload-pack: not our ref " + SHA,
            "ERR upload-pack: not our ref " + SHA,
            "fatal: remote error: upload-pack: not our ref " + SHA,
            "Not Our Ref " + SHA,
            "want " + SHA + " not valid",
            "file:///tmp/r: want " + SHA + " not valid",
            "want " + SHA + " not found"})
    @DisplayName("a remote's refusal of the wanted commit is recognised")
    void recognised(String message) {
        assertTrue(JGitOperations.isUnservedWant(message), message);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "https://github.com/o/r: Connection refused",
            "https://github.com/o/r: 503 Service Unavailable",
            "Read timed out",
            "https://github.com/o/r: not found",
            "https://github.com/o/r: authentication not supported",
            "the wanted pack is not valid",
            ""})
    @DisplayName("any other transport failure is not recognised")
    void notRecognised(String message) {
        assertFalse(JGitOperations.isUnservedWant(message), message);
    }

    @Test
    @DisplayName("a failure without message is not recognised")
    void noMessage() {
        assertFalse(JGitOperations.isUnservedWant((String) null));
    }
}
