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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;

/**
 * Proves that the builder of {@link SecurityAudit.Event} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Security audit record builders")
class SecurityAuditRecordsTest {

    @Test
    @DisplayName("Event builder builds the canonical constructor's value")
    void eventBuilderEqualsConstructor() {
        var executable = Generators.letterStrings(3, 10).next();
        var generationId = Generators.letterStrings(3, 10).next();
        var queueTaskId = Generators.letterStrings(3, 10).next();
        var attempt = Generators.integers(1, 5).next();
        var at = Instant.EPOCH.plusSeconds(Generators.integers(1, 1_000).next());
        var constructed = new SecurityAudit.Event(executable, generationId, queueTaskId, attempt, at);
        var built = SecurityAudit.Event.builder()
                .executable(executable)
                .generationId(generationId)
                .queueTaskId(queueTaskId)
                .attempt(attempt)
                .at(at)
                .build();

        assertEquals(constructed, built, "the builder builds the same audit event");
    }

    @Test
    @DisplayName("Event builder refuses a missing executable")
    void eventBuilderRefusesMissingExecutable() {
        var generationId = Generators.letterStrings(3, 10).next();
        var queueTaskId = Generators.letterStrings(3, 10).next();
        var at = Instant.EPOCH.plusSeconds(Generators.integers(1, 1_000).next());
        var exception = assertThrows(NullPointerException.class, () -> SecurityAudit.Event.builder()
                .generationId(generationId)
                .queueTaskId(queueTaskId)
                .attempt(1)
                .at(at)
                .build());

        assertEquals("executable", exception.getMessage(), "the builder names the missing executable");
    }
}
