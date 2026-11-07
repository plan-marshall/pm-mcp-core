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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.provider.git.GitOperations.GitCredential;
import de.planmarshall.provider.git.testing.generators.GitGenerators;

/**
 * The canonical object methods of the transport credential {@link GitCredential}: a record whose {@code equals} and
 * {@code hashCode} are written by hand, because its secret is a {@code char[]} that is compared by content rather than
 * by identity.
 */
@EnableGeneratorController
@DisplayName("Contract of a git transport credential")
class GitOperationsContractTest implements ShouldHandleObjectContracts<GitCredential> {

    @Override
    public GitCredential getUnderTest() {
        return GitGenerators.credentials().next();
    }

    /** A credential is handed over in-process and never written out, so it is no Java-serialized value. */
    @Override
    @Test
    @DisplayName("a credential is deliberately not Serializable")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(GitCredential.class),
                "a credential is never Java-serialized");
    }
}
