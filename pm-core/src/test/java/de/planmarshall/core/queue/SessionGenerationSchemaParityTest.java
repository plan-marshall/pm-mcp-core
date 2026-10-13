/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;
import de.planmarshall.core.queue.SessionGeneration.DeliveredSkill;
import de.planmarshall.core.queue.SessionGeneration.Scope;
import de.planmarshall.core.queue.SessionGeneration.Workdir;
import de.planmarshall.core.store.ScopeSessionRow.Client;

/**
 * The codec of the session generation record keeps parity with the schema of Store Schemas § Task Queue, Session
 * Generation Record (PM-WORK-5): the members the codec writes for an entry and its nested objects are the members of
 * the schema in schema order and every one required, the values of every enumeration are those of the schema, and the
 * format version and the key pattern match. A member added to or removed from the schema without the code fails here.
 * The schema is read from the documentation checkout named by {@value SpecificationDocument#ROOT_PROPERTY}; without
 * the checkout the tests are skipped with the missing path named.
 */
@DisplayName("Session generation record schema parity")
class SessionGenerationSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/16-task-queue.adoc";
    private static final String HEADING = "=== Session Generation Record";
    private static final GenerationId ID = new GenerationId("sg-0123456789abcdef");

    private static Map<?, ?> schema;
    private static Map<?, ?> entrySchema;
    private static Map<?, ?> entryProperties;
    private static Map<?, ?> written;

    @BeforeAll
    static void readSchema() {
        schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
        var generations = object(object(schema, "properties"), "generations");
        entrySchema = object(generations, "additionalProperties");
        entryProperties = object(entrySchema, "properties");
        var instant = Instant.parse("2026-10-13T08:00:00Z");
        var generation = SessionGeneration.issued(ID, "a".repeat(64), Client.CLAUDE, "session-1",
                GenerationCause.ENTER, instant, true).toBuilder()
                .scope(new Scope(Scope.Type.PLAN, "lock-manager"))
                .workdir(new Workdir(Workdir.Kind.PLAN_WORKTREE, "lock-manager"))
                .deliveredSkills(List.of(new DeliveredSkill("skill://pm/x", "c".repeat(64), instant)))
                .build();
        var document = SpecificationDocument.object(SpecificationDocument.parse(new String(
                SessionGenerationCodec.write(List.of(generation)), StandardCharsets.UTF_8)));
        written = object(object(document, "generations"), ID.value());
    }

    @Test
    @DisplayName("the members of the record are those of the schema, the format version is its constant")
    void recordMembers() {
        var properties = object(schema, "properties");

        assertAll(
                () -> assertEquals(List.of("format_version", "generations"), names(properties.keySet())),
                () -> assertEquals(List.of("format_version", "generations"), names(array(schema, "required"))),
                () -> assertEquals(SessionGenerationCodec.FORMAT_VERSION,
                        ((Number) object(properties, "format_version").get("const")).intValue()),
                () -> assertEquals("^sg-[0-9a-f]{16}$", object(object(properties, "generations"), "propertyNames")
                        .get("pattern")));
    }

    @Test
    @DisplayName("the members written for an entry are the members of the schema in schema order, every one required")
    void entryMembers() {
        var expected = names(entryProperties.keySet());

        assertAll(
                () -> assertFalse(expected.isEmpty(), "the schema declares members"),
                () -> assertEquals(expected, names(written.keySet())),
                () -> assertEquals(expected, names(array(entrySchema, "required"))));
    }

    @Test
    @DisplayName("the members written for the scope, the working directory and a delivered skill are the schema's")
    void nestedMembers() {
        var skillSchema = object(object(entryProperties, "delivered_skills"), "items");
        var writtenSkill = SpecificationDocument.object(SpecificationDocument.array(written.get("delivered_skills"))
                .getFirst());

        assertAll(
                () -> assertNested(object(entryProperties, "scope"), object(written, "scope")),
                () -> assertNested(object(entryProperties, "workdir"), object(written, "workdir")),
                () -> assertNested(skillSchema, writtenSkill));
    }

    @Test
    @DisplayName("the values of every enumeration are the values of the schema")
    void enumerations() {
        var scopeTypes = object(object(object(entryProperties, "scope"), "properties"), "type");
        var workdirKinds = object(object(object(entryProperties, "workdir"), "properties"), "kind");

        assertAll(
                () -> assertEquals(enumOf(object(entryProperties, "client")),
                        wireNames(Client.values(), Client::wireName)),
                () -> assertEquals(enumOf(object(entryProperties, "issued_by")),
                        wireNames(GenerationCause.values(), GenerationCause::wireName)),
                () -> assertEquals(enumOf(scopeTypes), wireNames(Scope.Type.values(), Scope.Type::wireName)),
                () -> assertEquals(enumOf(workdirKinds),
                        wireNames(Workdir.Kind.values(), Workdir.Kind::wireName)));
    }

    private static void assertNested(Map<?, ?> nestedSchema, Map<?, ?> nestedWritten) {
        var expected = names(object(nestedSchema, "properties").keySet());
        assertAll(
                () -> assertEquals(expected, names(nestedWritten.keySet())),
                () -> assertEquals(expected, names(array(nestedSchema, "required"))));
    }

    private static Set<String> enumOf(Map<?, ?> property) {
        return array(property, "enum").stream().filter(value -> value != null).map(String::valueOf)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static <E> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    private static Map<?, ?> object(Map<?, ?> parent, String member) {
        return SpecificationDocument.object(parent.get(member));
    }

    private static List<?> array(Map<?, ?> parent, String member) {
        return SpecificationDocument.array(parent.get(member));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
