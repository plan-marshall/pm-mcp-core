/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.primitive.CommitTrailer;

@DisplayName("CommitTrailer generator")
@EnableGeneratorController
class CommitTrailerGeneratorTest {

    private final CommitTrailerGenerator generator = new CommitTrailerGenerator();

    @RepeatedTest(50)
    @DisplayName("Should yield trailers within the constraints of the key")
    void shouldYieldTrailersWithinTheConstraints() {
        var generated = generator.next();

        assertAll("generated trailer",
                () -> assertTrue(CommitTrailer.isValidName(generated.name()),
                        "The generated name meets the name rule"),
                () -> assertTrue(CommitTrailer.isValidEmail(generated.email()),
                        "The generated email meets the email rule"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(CommitTrailer.class, generator.getType(), "The generator names the trailer type");
    }

    @Test
    @DisplayName("Should be registered by the module factory")
    void shouldBeRegisteredByTheModuleFactory() {
        assertEquals(CommitTrailer.class, GitGenerators.trailers().getType(),
                "The factory yields the trailer generator");
    }
}
