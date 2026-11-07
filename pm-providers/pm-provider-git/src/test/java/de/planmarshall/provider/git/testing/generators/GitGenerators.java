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

import de.cuioss.test.generator.TypedGenerator;
import lombok.experimental.UtilityClass;

import de.planmarshall.provider.git.GitOperations.GitCredential;

/**
 * Registers the generator artifact of {@code pm-provider-git} in one place: one factory method per
 * {@link TypedGenerator}. The factory is named for {@code @TypeGeneratorFactorySource(factoryClass =
 * GitGenerators.class, factoryMethod = "credentials")}.
 *
 * @since 0.1
 */
@UtilityClass
public class GitGenerators {

    /** @return a generator of transport credentials, each with a fresh secret array */
    public static TypedGenerator<GitCredential> credentials() {
        return new GitCredentialGenerator();
    }
}
