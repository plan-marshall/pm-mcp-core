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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The classification of a transport failure of {@code git.fetch-commit} (PM-WORK-6, PM-EXEC-5): the closed set of
 * wordings by which a remote that answered refuses a wanted commit is {@code UNKNOWN_REVISION}, so the import reports
 * the pin as missing; every other failure is {@code FAILED}, the remote did not answer.
 * <p>
 * The wordings are literals by design: each is a message a remote or JGit sends, the closed set under test. The
 * commit id inside a refusal is generated.
 */
@EnableGeneratorController
@DisplayName("Unserved want classification of JGitOperations")
class UnservedWantTest {

    static Stream<String> refusals() {
        var sha = JGitCacheRepositoryTest.commitId();
        return Stream.of(
                "upload-pack: not our ref " + sha,
                "https://github.com/o/r: remote error: upload-pack: not our ref " + sha,
                "ERR upload-pack: not our ref " + sha,
                "fatal: remote error: upload-pack: not our ref " + sha,
                "Not Our Ref " + sha,
                "want " + sha + " not valid",
                "file:///tmp/r: want " + sha + " not valid",
                "want " + sha + " not found");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("refusals")
    @DisplayName("a remote's refusal of the wanted commit is recognised")
    void recognised(String message) {
        assertTrue(JGitOperations.isUnservedWant(message), "a refusal of the wanted commit is recognised");
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
    @DisplayName("any other transport failure is not recognised (the wordings are the contract)")
    void notRecognised(String message) {
        assertFalse(JGitOperations.isUnservedWant(message), "another transport failure is not recognised");
    }

    @Test
    @DisplayName("a failure without message is not recognised")
    void noMessage() {
        assertFalse(JGitOperations.isUnservedWant((String) null), "a failure without a message is not recognised");
    }
}
