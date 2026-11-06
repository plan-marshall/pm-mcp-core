/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SHA-256 digest generator")
@EnableGeneratorController
class Sha256DigestGeneratorTest {

    private final Sha256DigestGenerator generator = new Sha256DigestGenerator();

    @Test
    @DisplayName("Should yield distinct lower-case SHA-256 digests")
    void shouldYieldDistinctLowerCaseDigests() {
        var first = generator.next();
        var second = generator.next();

        // The pattern is the contract: the enrolment schema records a SHA-256 as 64 lower-case hex characters
        assertAll("generated digests",
                () -> assertTrue(first.matches("[0-9a-f]{64}"), "The digest is a lower-case SHA-256"),
                () -> assertTrue(second.matches("[0-9a-f]{64}"), "Every draw is a lower-case SHA-256"),
                () -> assertNotEquals(first, second, "Two calls yield distinct digests"));
    }

    @Test
    @DisplayName("Should be registered in the runtime generator factory")
    void shouldBeRegisteredInTheFactory() {
        assertTrue(RuntimeGenerators.sha256Digests().next().matches("[0-9a-f]{64}"),
                "The factory yields a lower-case SHA-256");
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(String.class, generator.getType(), "The generator names the string type");
    }
}
