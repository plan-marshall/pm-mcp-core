/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link ContractEnvelope} builds the value its canonical constructor builds, including its
 * null refusal for the scope kind. Every value is generated; the link names one generated text parameter without a
 * schema, and the result schema is an empty object node.
 */
@EnableGeneratorController
@DisplayName("ContractEnvelope builder")
class ContractEnvelopeRecordsTest {

    @Test
    @DisplayName("the builder builds the contract envelope the constructor builds")
    void contractEnvelopeBuilderEqualsConstructor() {
        var scopeKind = "plan";
        var scopeId = RuntimeGenerators.planIds().next();
        var entity = Generators.letterStrings(1, 20).next();
        var firstCall = ContractEnvelope.PM_STATE;
        var links = List.of(new LinkSchema(Generators.letterStrings(1, 20).next(),
                List.of(new LinkParameter(Generators.letterStrings(1, 20).next(), "text", null))));
        var resultSchema = JsonNode.object(Map.of("type", JsonNode.string("object")));

        var constructed = new ContractEnvelope(scopeKind, scopeId, entity, firstCall, links, resultSchema);
        var built = ContractEnvelope.builder()
                .scopeKind(scopeKind)
                .scopeId(scopeId)
                .entity(entity)
                .firstCall(firstCall)
                .links(links)
                .resultSchema(resultSchema)
                .build();

        assertEquals(constructed, built, "the builder builds the same contract envelope");
    }

    @Test
    @DisplayName("the builder refuses a missing scope kind like the constructor")
    void contractEnvelopeBuilderRefusesMissingScopeKind() {
        var refusal = assertThrows(NullPointerException.class, () -> ContractEnvelope.builder().build());

        assertEquals("scopeKind", refusal.getMessage(), "the refusal names the scope kind");
    }
}
