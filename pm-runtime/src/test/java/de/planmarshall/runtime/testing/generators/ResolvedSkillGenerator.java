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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import de.planmarshall.runtime.launch.ResolvedSkill;

/**
 * Generates a valid {@link ResolvedSkill}: a skill URI in the {@code skill://pm/} namespace, a generated text and the
 * digest that {@link ResolvedSkill#of(String, String)} computes over exactly that text.
 *
 * @since 0.1
 */
public final class ResolvedSkillGenerator implements TypedGenerator<ResolvedSkill> {

    @Override
    public ResolvedSkill next() {
        var suffix = Generators.integers(1, 999).next();
        return ResolvedSkill.of("skill://pm/generated/skill-" + suffix + "/SKILL.md",
                "Generated skill text " + suffix + " " + Generators.letterStrings(8, 16).next());
    }

    @Override
    public Class<ResolvedSkill> getType() {
        return ResolvedSkill.class;
    }
}
