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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.enrolment.EnrolmentKey;

@DisplayName("HMAC key generator")
@EnableGeneratorController
class HmacKeyGeneratorTest {

    @Test
    @DisplayName("Should yield distinct 32-byte keys of the enrolment integrity algorithm")
    void shouldYieldDistinctKeysOfTheAlgorithm() {
        var first = RuntimeGenerators.hmacKeys().next();
        var second = RuntimeGenerators.hmacKeys().next();

        assertAll("generated keys",
                () -> assertEquals(EnrolmentKey.ALGORITHM, first.getAlgorithm(), "The key has the integrity algorithm"),
                () -> assertEquals(32, first.getEncoded().length, "The key is as long as the integrity key"),
                () -> assertNotEquals(first, second, "Two calls yield distinct keys"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(SecretKey.class, new HmacKeyGenerator().getType(), "The generator names the secret key type");
    }
}
