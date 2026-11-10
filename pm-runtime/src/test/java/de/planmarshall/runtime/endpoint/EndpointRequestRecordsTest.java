/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonNode;

/**
 * Proves that the builder of {@link EndpointRequest} builds the value its canonical constructor builds, including its
 * null refusal for the model. Every value is generated; the request carries a result schema and the schema name the
 * endpoint enforces.
 */
@EnableGeneratorController
@DisplayName("EndpointRequest builder")
class EndpointRequestRecordsTest {

    @Test
    @DisplayName("the builder builds the endpoint request the constructor builds")
    void endpointRequestBuilderEqualsConstructor() {
        var model = Generators.letterStrings(1, 20).next();
        var system = Generators.letterStrings(1, 40).next();
        var user = Generators.letterStrings(1, 40).next();
        var resultSchema = JsonNode.object(Map.of("type", JsonNode.string("object")));
        var schemaName = "enforced";
        var maxAnswerTokens = Generators.integers(1, 100_000).next();

        var constructed = new EndpointRequest(model, system, user, resultSchema, schemaName, maxAnswerTokens);
        var built = EndpointRequest.builder()
                .model(model)
                .system(system)
                .user(user)
                .resultSchema(resultSchema)
                .schemaName(schemaName)
                .maxAnswerTokens(maxAnswerTokens)
                .build();

        assertEquals(constructed, built, "the builder builds the same endpoint request");
    }

    @Test
    @DisplayName("the builder refuses a missing model like the constructor")
    void endpointRequestBuilderRefusesMissingModel() {
        var refusal = assertThrows(NullPointerException.class, () -> EndpointRequest.builder().build());

        assertEquals("model", refusal.getMessage(), "the refusal names the model");
    }
}
