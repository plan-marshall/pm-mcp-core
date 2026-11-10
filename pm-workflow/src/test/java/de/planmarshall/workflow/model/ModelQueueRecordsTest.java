/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleForm;

/**
 * Proves that the builders of the records of {@link ModelQueue} build the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Model queue record builders")
class ModelQueueRecordsTest {

    @Test
    @DisplayName("Enqueue builder builds the canonical constructor's value")
    void enqueueBuilderEqualsConstructor() {
        var scopeId = "plan-" + Generators.letterStrings(3, 8).next();
        var stateId = Generators.letterStrings(1, 8).next();
        var entityKey = Generators.letterStrings(1, 8).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var form = Generators.enumValues(RoleForm.class).next();
        var configurationDigest = Generators.strings("0123456789abcdef", 64, 64).next();
        var harness = Generators.letterStrings(1, 8).next();
        var model = Generators.letterStrings(1, 8).next();
        var effort = new EffortValue(Generators.letterStrings(1, 8).next());
        var consultation = Generators.booleans().next();
        var constructed = new ModelQueue.Enqueue(scopeId, stateId, entityKey, role, form, configurationDigest,
                harness, model, effort, consultation);
        var built = ModelQueue.Enqueue.builder()
                .scopeId(scopeId)
                .stateId(stateId)
                .entityKey(entityKey)
                .role(role)
                .form(form)
                .configurationDigest(configurationDigest)
                .harness(harness)
                .model(model)
                .effort(effort)
                .consultation(consultation)
                .build();

        assertEquals(constructed, built, "the builder builds the enqueued task");
    }

    @Test
    @DisplayName("Enqueue builder refuses a missing state id")
    void enqueueBuilderRefusesMissingStateId() {
        var scopeId = "plan-" + Generators.letterStrings(3, 8).next();
        var entityKey = Generators.letterStrings(1, 8).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var form = Generators.enumValues(RoleForm.class).next();
        var configurationDigest = Generators.strings("0123456789abcdef", 64, 64).next();
        var harness = Generators.letterStrings(1, 8).next();
        var model = Generators.letterStrings(1, 8).next();
        var effort = new EffortValue(Generators.letterStrings(1, 8).next());
        var exception = assertThrows(NullPointerException.class, () -> ModelQueue.Enqueue.builder()
                .scopeId(scopeId)
                .entityKey(entityKey)
                .role(role)
                .form(form)
                .configurationDigest(configurationDigest)
                .harness(harness)
                .model(model)
                .effort(effort)
                .consultation(false)
                .build());

        assertEquals("stateId", exception.getMessage(), "the builder names the missing state id");
    }

    @Test
    @DisplayName("Close builder builds the canonical constructor's value")
    void closeBuilderEqualsConstructor() {
        var scopeId = "plan-" + Generators.letterStrings(3, 8).next();
        var stateId = Generators.letterStrings(1, 8).next();
        var entityKey = Generators.letterStrings(1, 8).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var reason = ModelCallResult.ROLE_UNAVAILABLE;
        var constructed = new ModelQueue.Close(scopeId, stateId, entityKey, role, reason);
        var built = ModelQueue.Close.builder()
                .scopeId(scopeId)
                .stateId(stateId)
                .entityKey(entityKey)
                .role(role)
                .reason(reason)
                .build();

        assertEquals(constructed, built, "the builder builds the closed task");
    }

    @Test
    @DisplayName("Close builder refuses a missing state id")
    void closeBuilderRefusesMissingStateId() {
        var scopeId = "plan-" + Generators.letterStrings(3, 8).next();
        var entityKey = Generators.letterStrings(1, 8).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var reason = ModelCallResult.ROLE_UNAVAILABLE;
        var exception = assertThrows(NullPointerException.class, () -> ModelQueue.Close.builder()
                .scopeId(scopeId)
                .entityKey(entityKey)
                .role(role)
                .reason(reason)
                .build());

        assertEquals("stateId", exception.getMessage(), "the builder names the missing state id");
    }
}
