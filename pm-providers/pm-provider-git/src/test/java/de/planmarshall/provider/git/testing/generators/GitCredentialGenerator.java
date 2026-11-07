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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import de.planmarshall.provider.git.GitOperations.GitCredential;

/**
 * Generates a {@link GitCredential} for the HTTPS transport: a generated user name and a generated secret. Every call
 * draws fresh values and hands out a fresh secret array, so no two credentials share the array a caller may clear.
 *
 * @since 0.1
 */
public final class GitCredentialGenerator implements TypedGenerator<GitCredential> {

    @Override
    public GitCredential next() {
        var username = Generators.letterStrings(1, 20).next();
        var secret = Generators.letterStrings(8, 40).next().toCharArray();
        return new GitCredential(username, secret);
    }

    @Override
    public Class<GitCredential> getType() {
        return GitCredential.class;
    }
}
