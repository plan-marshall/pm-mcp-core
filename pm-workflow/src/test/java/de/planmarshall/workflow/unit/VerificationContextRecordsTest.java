/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.spi.PrimitiveRegistry;
import de.planmarshall.workflow.ast.WorkflowDefinition;
import de.planmarshall.workflow.verify.EntityKindRegistry;
import de.planmarshall.workflow.verify.QuestionClassRegistry;
import de.planmarshall.workflow.verify.SchemaRegistry;

/**
 * Proves that the builder of {@link VerificationContext} builds the value of the canonical constructor.
 */
@EnableGeneratorController
@DisplayName("Verification context record builder")
class VerificationContextRecordsTest {

    @Test
    @DisplayName("VerificationContext builder builds the canonical constructor's value")
    void verificationContextBuilderEqualsConstructor() {
        var registry = new PrimitiveRegistry(List.of());
        var entityKinds = EntityKindRegistry.defaults();
        var questionClasses = QuestionClassRegistry.defaults();
        var schemas = SchemaRegistry.defaults();
        var compiledUnits = List.<WorkflowDefinition>of();
        var constructed = new VerificationContext(registry, entityKinds, questionClasses, schemas, compiledUnits);
        var built = VerificationContext.builder()
                .registry(registry)
                .entityKinds(entityKinds)
                .questionClasses(questionClasses)
                .schemas(schemas)
                .compiledUnits(compiledUnits)
                .build();

        assertEquals(constructed, built, "the builder builds the same verification context");
    }

    @Test
    @DisplayName("VerificationContext builder refuses a missing registry")
    void verificationContextBuilderRefusesMissingRegistry() {
        var builder = VerificationContext.builder()
                .entityKinds(EntityKindRegistry.defaults())
                .questionClasses(QuestionClassRegistry.defaults())
                .schemas(SchemaRegistry.defaults())
                .compiledUnits(List.<WorkflowDefinition>of());
        var exception = assertThrows(NullPointerException.class, builder::build);

        assertEquals("registry", exception.getMessage(), "the builder names the missing registry");
    }
}
