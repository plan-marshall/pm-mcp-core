/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The canonical object methods of the profile tree (PM-CLIENT-5): two trees are equal exactly when they carry the same
 * members, elements and values, so a tree can be compared and used as a map key.
 */
@EnableGeneratorController
@DisplayName("Contract of the profile JSON tree")
class JsonNodeContractTest implements ShouldHandleObjectContracts<JsonNode> {

    @Override
    public JsonNode getUnderTest() {
        return JsonNode.object(Map.of(Generators.letterStrings(1, 8).next(),
                JsonNode.string(Generators.letterStrings(1, 20).next())));
    }

    /**
     * The tree is never written with Java serialization: canonical JSON is its wire form, and a profile is hashed and
     * compared as those bytes.
     */
    @Override
    @Test
    @DisplayName("the tree is deliberately not Serializable")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(JsonNode.class),
                "The profile tree is not Java-serialized; canonical JSON is its wire form");
    }
}
