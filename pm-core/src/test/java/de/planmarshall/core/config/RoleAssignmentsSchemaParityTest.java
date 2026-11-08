/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.role.EffortValue;

/**
 * {@link RoleAssignmentsCodec} keeps parity with the schema of Store Schemas § Role Assignments: an empty store carries
 * exactly the schema's members, an assignment carries the schema's members in schema order, the effort is the one member
 * of the schema the levels differ in, and a written store is valid against the schema. The schema is the file
 * {@code role-assignments.schema.json} beside the codec in
 * {@code pm-core/src/main/resources/de/planmarshall/core/config/}, read from the class path through
 * {@link SchemaResource}. The member names, the role name and the effort value are contracts; they are literals here for
 * that reason.
 */
@EnableGeneratorController
@DisplayName("Role assignments schema parity")
class RoleAssignmentsSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/config/role-assignments.schema.json";

    @Test
    @DisplayName("an empty store has exactly the schema's members, every one required")
    void storeMembers() {
        var schema = SchemaResource.load(SCHEMA);
        var written = document(RoleAssignmentsCodec.write(Map.of(), 0));

        assertAll(
                () -> assertEquals(names(SchemaResource.object(schema.get("properties")).keySet()),
                        names(written.keySet()), "The store members are the schema's in order"),
                () -> assertEquals(names(SchemaResource.array(schema.get("required"))), names(written.keySet()),
                        "Every store member is required"));
    }

    @Test
    @DisplayName("an assignment names its harness, its model and its effort in schema order")
    void assignmentMembers() {
        var schema = SchemaResource.load(SCHEMA);
        var role = SchemaResource.object(
                SchemaResource.object(SchemaResource.object(schema.get("properties")).get("roles"))
                        .get("additionalProperties"));
        var written = assignment(RoleAssignmentValue.of("opencode", "deepseek-v4.1", "high"));

        assertAll(
                () -> assertEquals(names(SchemaResource.object(role.get("properties")).keySet()),
                        List.of("harness", "model", "effort", "escalation"), "The schema's assignment members"),
                () -> assertEquals(List.of("harness", "model", "effort"), names(written.keySet()),
                        "The written assignment members are in schema order"),
                () -> assertEquals(List.of("harness", "model"),
                        names(SchemaResource.array(role.get("required"))), "Only harness and model are required"));
    }

    @Test
    @DisplayName("an inherited effort is written as the absent member")
    void inheritedEffortIsAbsent() {
        var written = assignment(RoleAssignmentValue.inheritEffort("opencode", "deepseek-v4.1"));

        assertEquals(List.of("harness", "model"), names(written.keySet()),
                "An inherited effort leaves the member out");
    }

    @Test
    @DisplayName("a written store is valid against the schema")
    void writtenStoreIsValid() {
        var written = RoleAssignmentsCodec.write(Map.of(
                "review-code", RoleAssignmentValue.of("opencode", "deepseek-v4.1", "high"),
                "default-role", RoleAssignmentValue.of("opencode", "deepseek-v4.1", null),
                "inherited-role", RoleAssignmentValue.inheritEffort("opencode", "deepseek-v4.1"),
                "escalated-role", RoleAssignmentValue.of("opencode", "deepseek-v4.1", "high")
                        .withEscalation(new Escalation("opencode", "deepseek-v4.1",
                                Optional.of(new EffortValue("high"))))), 1);

        assertEquals(Set.of(), validator().validate(new String(written, UTF_8), InputFormat.JSON),
                () -> "The written store is valid: " + new String(written, UTF_8));
    }

    private static Map<?, ?> assignment(RoleAssignmentValue value) {
        var written = document(RoleAssignmentsCodec.write(Map.of("review-code", value), 1));
        return SchemaResource.object(SchemaResource.object(written.get("roles")).get("review-code"));
    }

    private static JsonSchema validator() {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(SchemaResource.text(SCHEMA));
    }

    private static Map<?, ?> document(byte[] written) {
        return SchemaResource.object(SchemaResource.parse(new String(written, UTF_8)));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
