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

import de.cuioss.test.generator.TypedGenerator;
import lombok.experimental.UtilityClass;

import de.planmarshall.runtime.launch.ResolvedSkill;

/**
 * Registers the generator artifact of {@code pm-runtime} in one place: one factory method per
 * {@link TypedGenerator}, for {@code @TypeGeneratorFactorySource(factoryClass = RuntimeGenerators.class,
 * factoryMethod = "resolvedSkills")}.
 *
 * @since 0.1
 */
@UtilityClass
public class RuntimeGenerators {

    /** @return a generator of valid resolved skills */
    public static TypedGenerator<ResolvedSkill> resolvedSkills() {
        return new ResolvedSkillGenerator();
    }
}
