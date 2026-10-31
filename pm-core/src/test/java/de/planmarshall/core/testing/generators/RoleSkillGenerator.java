/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import de.planmarshall.core.role.RoleSkill;

/**
 * Generates valid {@link RoleSkill} values: a skill URI in the {@code skill://pm/} namespace and a 64-digit
 * lower-case hexadecimal digest, the two components the record validates. The record checks the shape of the digest,
 * not that it hashes a particular text.
 *
 * @since 0.1
 */
public final class RoleSkillGenerator implements TypedGenerator<RoleSkill> {

    private static final String URI_PREFIX = "skill://pm/generated/";

    @Override
    public RoleSkill next() {
        return new RoleSkill(URI_PREFIX + "%016x".formatted(Generators.longs().next()) + "/SKILL.md", digest());
    }

    @Override
    public Class<RoleSkill> getType() {
        return RoleSkill.class;
    }

    private static String digest() {
        return "%064x".formatted(Generators.longs().next());
    }
}
