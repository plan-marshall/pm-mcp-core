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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.GitOperations.GitCredential;

@DisplayName("GitCredential generator")
@EnableGeneratorController
class GitCredentialGeneratorTest {

    private final GitCredentialGenerator generator = new GitCredentialGenerator();

    @RepeatedTest(20)
    @DisplayName("Should yield credentials with a user name and a secret")
    void shouldYieldValidCredentials() {
        var generated = generator.next();

        assertAll("generated credential",
                () -> assertNotNull(generated, "The generator yields a credential"),
                () -> assertFalse(generated.username().isBlank(), "The user name of the credential is set"),
                () -> assertTrue(generated.secret().length >= 8, "The secret holds at least eight characters"),
                () -> assertFalse(generated.toString().contains(new String(generated.secret())),
                        "The string form of the credential masks the secret"));
    }

    @RepeatedTest(20)
    @DisplayName("Should hand out a fresh secret array per credential")
    void shouldHandOutAFreshSecretArrayPerCredential() {
        var first = generator.next();
        var second = generator.next();
        var secondBefore = second.secret().clone();

        Arrays.fill(first.secret(), '\0');

        assertAll("secret arrays",
                () -> assertNotSame(first.secret(), second.secret(), "Two credentials share no secret array"),
                () -> assertTrue(Arrays.equals(secondBefore, second.secret()),
                        "Clearing one secret leaves the other intact"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(GitCredential.class, generator.getType(), "The generator names the credential type");
    }

    @Test
    @DisplayName("Should be registered by the module factory")
    void shouldBeRegisteredByTheModuleFactory() {
        assertEquals(GitCredential.class, GitGenerators.credentials().getType(),
                "The factory yields the credential generator");
    }
}
