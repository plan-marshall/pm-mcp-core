/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * The skills delivered to a generation (Model Work § Skill Delivery Record &amp; Re-Delivery): a new generation starts
 * with an empty record, an unchanged digest is not delivered again, and a changed digest is.
 */
@EnableGeneratorController
@SuppressWarnings("java:S5778")
// one delivery case per scenario, by design
@DisplayName("Skill delivery")
class SkillDeliveryTest {

    private RoleSkill skill;
    private String otherDigest;

    @BeforeEach
    void createSkill() {
        skill = CoreGenerators.roleSkills().next();
        var digest = skill.digest();
        otherDigest = (digest.startsWith("0") ? "1" : "0") + digest.substring(1);
    }

    @Test
    @DisplayName("a new generation receives every skill in-band")
    void newGeneration() {
        var delivery = new SkillDelivery();

        assertEquals(List.of(skill), delivery.deliver(TaskFixtures.GENERATION, List.of(skill)));
    }

    @Test
    @DisplayName("an unchanged skill is not delivered again")
    void unchanged() {
        var delivery = new SkillDelivery();
        delivery.deliver(TaskFixtures.GENERATION, List.of(skill));

        assertTrue(delivery.deliver(TaskFixtures.GENERATION, List.of(skill)).isEmpty());
    }

    @Test
    @DisplayName("a changed digest is delivered again, and a new generation starts empty")
    void changedDigestAndNewGeneration() {
        var delivery = new SkillDelivery();
        delivery.deliver(TaskFixtures.GENERATION, List.of(skill));
        var changed = new RoleSkill(skill.uri(), otherDigest);

        assertEquals(List.of(changed), delivery.deliver(TaskFixtures.GENERATION, List.of(changed)));
        assertEquals(List.of(changed), delivery.deliver(TaskFixtures.SUCCESSOR, List.of(changed)));
        assertEquals(Map.of(skill.uri(), otherDigest),
                delivery.recordOf(TaskFixtures.SUCCESSOR));
    }
}
