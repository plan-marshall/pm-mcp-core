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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    /** The identifier registry, read from the module's working directory beside the specification. */
    private static final String REGISTRY = "../doc/specification/identifiers/01-general-rules-and-registry.adoc";

    @Test
    @DisplayName("the name pattern of the role artifact schema is the role-name pattern")
    void roleNameEqualsSchema() {
        var expected = patternAt(ROLE_ARTIFACT_SCHEMA, "properties", "name");

        assertFalse(expected.isBlank(), "The schema should name the role-name pattern");
        assertEquals(RoleName.PATTERN.pattern(), expected, "The role name should equal the schema pattern");
    }

    @Test
    @DisplayName("the harness pattern of the metrics schema is the harness-id pattern")
    void harnessIdEqualsSchema() {
        var expected = patternAt(METRICS_SCHEMA, "$defs", "harness");

        assertFalse(expected.isBlank(), "The schema should name the harness-id pattern");
        assertEquals(HarnessId.PATTERN.pattern(), expected, "The harness id should equal the schema pattern");
    }

    @Test
    @DisplayName("the registry rows of the command and correlation ids name the code's patterns")
    void commandAndCorrelationIdsEqualRegistry() {
        var registry = readRegistry();

        assertAll(
                () -> assertTrue(registry.contains("[[id-command-id]]"),
                        "the registry should register the command id"),
                () -> assertTrue(registry.contains(CommandId.PATTERN.pattern()),
                        "the registry should name the command-id pattern"),
                () -> assertTrue(registry.contains("[[id-correlation-id]]"),
                        "the registry should register the correlation id"),
                () -> assertTrue(registry.contains(CorrelationId.PATTERN.pattern()),
                        "the registry should name the correlation-id pattern"),
                () -> assertTrue(registry.contains("call_stack"),
                        "the registry should name the call frame"));
    }

    private static String readRegistry() {
        try {
            return Files.readString(Path.of(REGISTRY));
        } catch (IOException e) {
            throw new UncheckedIOException("the identifier registry cannot be read", e);
        }
    }

    private static String patternAt(String schemaResource, String... path) {
        Object current = SchemaResource.load(schemaResource);
        for (var member : path) {
            current = SchemaResource.object(current).get(member);
        }
        var pattern = SchemaResource.object(current).get("pattern");
        if (pattern == null) {
            return fail("the schema declares no pattern at " + String.join(".", path));
        }
        return pattern.toString();
    }
}
