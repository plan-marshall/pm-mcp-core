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


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleSkill;

/**
 * The skills delivered to a generation (Model Work § Skill Delivery Record &amp; Re-Delivery): a new generation starts
 * with an empty record, an unchanged digest is not delivered again, and a changed digest is.
 */
@DisplayName("Skill delivery")
class SkillDeliveryTest {

    private static final String DIGEST = "a".repeat(64);
    private static final String OTHER = "b".repeat(64);
    private static final RoleSkill SKILL = new RoleSkill("skill://pm/documents/doc-review/SKILL.md", DIGEST);

    @Test
    @DisplayName("a new generation receives every skill in-band")
    void newGeneration() {
        var delivery = new SkillDelivery();

        assertEquals(List.of(SKILL), delivery.deliver(TaskFixtures.GENERATION, List.of(SKILL)));
    }

    @Test
    @DisplayName("an unchanged skill is not delivered again")
    void unchanged() {
        var delivery = new SkillDelivery();
        delivery.deliver(TaskFixtures.GENERATION, List.of(SKILL));

        assertTrue(delivery.deliver(TaskFixtures.GENERATION, List.of(SKILL)).isEmpty());
    }

    @Test
    @DisplayName("a changed digest is delivered again, and a new generation starts empty")
    void changedDigestAndNewGeneration() {
        var delivery = new SkillDelivery();
        delivery.deliver(TaskFixtures.GENERATION, List.of(SKILL));
        var changed = new RoleSkill(SKILL.uri(), OTHER);

        assertEquals(List.of(changed), delivery.deliver(TaskFixtures.GENERATION, List.of(changed)));
        assertEquals(List.of(changed), delivery.deliver(TaskFixtures.SUCCESSOR, List.of(changed)));
        assertEquals(Map.of(SKILL.uri(), OTHER),
                delivery.recordOf(TaskFixtures.SUCCESSOR));
    }
}
