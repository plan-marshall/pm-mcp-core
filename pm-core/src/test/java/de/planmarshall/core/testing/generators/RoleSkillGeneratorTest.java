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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleSkill;

@DisplayName("Role skill generator")
@EnableGeneratorController
class RoleSkillGeneratorTest {

    private final RoleSkillGenerator generator = new RoleSkillGenerator();

    @Test
    @DisplayName("Should yield valid and distinct role skills")
    void shouldYieldValidAndDistinctRoleSkills() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated role skills",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertTrue(first.uri().startsWith("skill://pm/"), "The URI names the skill namespace"),
                () -> assertTrue(first.digest().matches("[0-9a-f]{64}"), "The digest is a lower-case SHA-256"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(RoleSkill.class, generator.getType(), "The generator names the role skill type");
    }
}
