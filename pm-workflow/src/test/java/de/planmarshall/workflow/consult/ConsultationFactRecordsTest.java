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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.queue.QueueTaskRecord;

/**
 * Proves that the builder of {@link ConsultationFact} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Consultation fact record builders")
class ConsultationFactRecordsTest {

    @Test
    @DisplayName("ConsultationFact builder builds the canonical constructor's value")
    void consultationFactBuilderEqualsConstructor() {
        var consultationId = "qt-" + Generators.strings("0123456789abcdef", 16, 16).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var status = Generators.enumValues(QueueTaskRecord.ConsultationStatus.class).next();
        var answer = Generators.letterStrings(1, 40).next();
        var constructed = new ConsultationFact(consultationId, role, status, answer);
        var built = ConsultationFact.builder()
                .consultationId(consultationId)
                .role(role)
                .status(status)
                .answer(answer)
                .build();

        assertEquals(constructed, built, "the builder builds the same consultation fact");
    }

    @Test
    @DisplayName("ConsultationFact builder refuses a missing status")
    void consultationFactBuilderRefusesMissingStatus() {
        var consultationId = "qt-" + Generators.strings("0123456789abcdef", 16, 16).next();
        var role = "role-" + Generators.strings("abcdefghijklmnopqrstuvwxyz0123456789", 2, 8).next();
        var exception = assertThrows(NullPointerException.class, () -> ConsultationFact.builder()
                .consultationId(consultationId)
                .role(role)
                .answer(Generators.letterStrings(1, 40).next())
                .build());

        assertEquals("status", exception.getMessage(), "the builder names the missing status");
    }
}
