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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * Proves that the builders of the records of {@link ModelCallResult} build the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Model call result record builders")
class ModelCallResultRecordsTest {

    @Test
    @DisplayName("Queued builder builds the canonical constructor's value")
    void queuedBuilderEqualsConstructor() {
        var queueTaskId = "qt-" + Generators.strings("0123456789abcdef", 16, 16).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var form = Generators.enumValues(RoleForm.class).next();
        var configurationDigest = Generators.strings("0123456789abcdef", 64, 64).next();
        var formDowngraded = Generators.booleans().next();
        var constructed = new ModelCallResult.Queued(queueTaskId, role, form, configurationDigest, formDowngraded);
        var built = ModelCallResult.Queued.builder()
                .queueTaskId(queueTaskId)
                .role(role)
                .form(form)
                .configurationDigest(configurationDigest)
                .formDowngraded(formDowngraded)
                .build();

        assertEquals(constructed, built, "the builder builds the queued result");
    }

    @Test
    @DisplayName("Closed builder builds the canonical constructor's value")
    void closedBuilderEqualsConstructor() {
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var reason = ModelCallResult.ROLE_UNAVAILABLE;
        var validationReason = Generators.enumValues(RoleValidationReason.class).next();
        var detail = Generators.letterStrings(5, 20).next();
        var constructed = new ModelCallResult.Closed(role, reason, validationReason, detail);
        var built = ModelCallResult.Closed.builder()
                .role(role)
                .reason(reason)
                .validationReason(validationReason)
                .detail(detail)
                .build();

        assertEquals(constructed, built, "the builder builds the closed result");
    }
}
