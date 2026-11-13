/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.id;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * The one definition keeps parity with the schemas that restate the two name grammars: the {@code name} pattern of the
 * role artifact schema ({@code id-role-name}) and the {@code harness} pattern of the qualification record schema
 * ({@code id-harness-id}).
 * <p>
 * The schemas are read from the class path through {@link SchemaResource}, so a grammar that changes in a schema
 * without {@link RoleName} or {@link HarnessId} fails here instead of drifting between the code and its contract. The
 * pattern texts are contracts; nothing is generated.
 */
@EnableGeneratorController
@DisplayName("Identifier specification parity")
class IdentifierSpecificationParityTest {

    private static final String ROLE_ARTIFACT_SCHEMA = "de/planmarshall/core/role/role-artifact-v1.schema.json";

    private static final String METRICS_SCHEMA = "de/planmarshall/core/store/metrics.schema.json";

    @Test
    @DisplayName("the name pattern of the role artifact schema is the role-name pattern")
    void roleNameEqualsSchema() {
        var expected = patternAt(ROLE_ARTIFACT_SCHEMA, "properties", "name");

        assertFalse(expected.isEmpty(), "The schema should name the role-name pattern");
        assertEquals(RoleName.PATTERN.pattern(), expected, "The role name should equal the schema pattern");
    }

    @Test
    @DisplayName("the harness pattern of the metrics schema is the harness-id pattern")
    void harnessIdEqualsSchema() {
        var expected = patternAt(METRICS_SCHEMA, "$defs", "harness");

        assertFalse(expected.isEmpty(), "The schema should name the harness-id pattern");
        assertEquals(HarnessId.PATTERN.pattern(), expected, "The harness id should equal the schema pattern");
    }

    private static String patternAt(String schemaResource, String... path) {
        Object current = SchemaResource.load(schemaResource);
        for (var member : path) {
            current = SchemaResource.object(current).get(member);
        }
        var declaration = SchemaResource.object(current);
        return String.valueOf(declaration.get("pattern"));
    }
}
