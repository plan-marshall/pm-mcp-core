/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.ci.testing.generators;

import de.cuioss.test.generator.TypedGenerator;
import lombok.experimental.UtilityClass;

import de.planmarshall.provider.ci.RepositoryRef;

/**
 * Registers the generator artifact of {@code pm-provider-ci} in one place: one factory method per
 * {@link TypedGenerator}. The factory is named for {@code @TypeGeneratorFactorySource(factoryClass =
 * CiGenerators.class, factoryMethod = "repositoryRefs")}.
 *
 * @since 0.1
 */
@UtilityClass
public class CiGenerators {

    /** @return a generator of valid repository references */
    public static TypedGenerator<RepositoryRef> repositoryRefs() {
        return new RepositoryRefGenerator();
    }
}
