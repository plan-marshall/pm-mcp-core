/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.consult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.role.RoleClass;

/**
 * Proves that the builder of {@link ConsultationRequest} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Consultation request record builders")
class ConsultationRequestRecordsTest {

    @Test
    @DisplayName("ConsultationRequest builder builds the canonical constructor's value")
    void consultationRequestBuilderEqualsConstructor() {
        var askingTaskId = "qt-" + Generators.strings("0123456789abcdef", 16, 16).next();
        var generationId = "l-" + Generators.strings("0123456789abcdef", 16, 16).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var roleClass = Generators.enumValues(RoleClass.class).next();
        var formDowngraded = Generators.booleans().next();
        var declaredRoles = List.of(role);
        var question = Generators.letterStrings(20, 80).next();
        var at = Instant.EPOCH.plusSeconds(Generators.integers(1, 1_000).next());
        var constructed = new ConsultationRequest(askingTaskId, generationId, role, roleClass, formDowngraded,
                declaredRoles, question, at);
        var built = ConsultationRequest.builder()
                .askingTaskId(askingTaskId)
                .generationId(generationId)
                .role(role)
                .roleClass(roleClass)
                .formDowngraded(formDowngraded)
                .declaredRoles(declaredRoles)
                .question(question)
                .at(at)
                .build();

        assertEquals(constructed, built, "the builder builds the same consultation ask");
    }

    @Test
    @DisplayName("ConsultationRequest builder refuses a missing question")
    void consultationRequestBuilderRefusesMissingQuestion() {
        var askingTaskId = "qt-" + Generators.strings("0123456789abcdef", 16, 16).next();
        var generationId = "l-" + Generators.strings("0123456789abcdef", 16, 16).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var at = Instant.EPOCH.plusSeconds(Generators.integers(1, 1_000).next());
        var exception = assertThrows(NullPointerException.class, () -> ConsultationRequest.builder()
                .askingTaskId(askingTaskId)
                .generationId(generationId)
                .role(role)
                .roleClass(RoleClass.READ_ONLY)
                .formDowngraded(false)
                .declaredRoles(List.of(role))
                .at(at)
                .build());

        assertEquals("question", exception.getMessage(), "the builder names the missing question");
    }
}
