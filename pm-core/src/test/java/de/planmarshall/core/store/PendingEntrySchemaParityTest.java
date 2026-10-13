/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;
import de.planmarshall.core.store.PendingEntryRecord.Kind;

/**
 * The codec of the pending entry record keeps parity with the schema of Store Schemas § Pending Entry Record
 * (PM-CLIENT-1): the members of the record and of its bootstrap state in schema order, the required members, the
 * values of {@code kind}, the format version and the length limits of {@code requested_id} and {@code hint}. A member
 * added to or removed from the schema without the code fails here. The schema is read from the documentation checkout
 * named by {@value SpecificationDocument#ROOT_PROPERTY}; without the checkout the tests are skipped with the missing
 * path named.
 */
@DisplayName("Pending entry record schema parity")
class PendingEntrySchemaParityTest {

    private static final String DOCUMENT = "store-schemas/01-format-and-session-log.adoc";
    private static final String HEADING = "=== Pending Entry Record";

    private static Map<?, ?> schema;
    private static Map<?, ?> properties;
    private static Map<?, ?> bootstrap;

    @BeforeAll
    static void readSchema() {
        schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
        properties = SpecificationDocument.object(schema.get("properties"));
        bootstrap = SpecificationDocument.object(properties.get("bootstrap"));
    }

    @Test
    @DisplayName("the members of the record are the members of the schema in schema order, every one required")
    void membersEqualSchema() {
        var expected = names(properties.keySet());

        assertAll(
                () -> assertFalse(expected.isEmpty(), "the schema declares members"),
                () -> assertEquals(PendingEntryCodec.MEMBERS, expected),
                () -> assertEquals(PendingEntryCodec.MEMBERS, names(SpecificationDocument.array(
                        schema.get("required")))));
    }

    @Test
    @DisplayName("the members of the bootstrap state are those of the schema in schema order, every one required")
    void bootstrapMembersEqualSchema() {
        var bootstrapProperties = SpecificationDocument.object(bootstrap.get("properties"));

        assertAll(
                () -> assertEquals(PendingEntryCodec.BOOTSTRAP_MEMBERS, names(bootstrapProperties.keySet())),
                () -> assertEquals(PendingEntryCodec.BOOTSTRAP_MEMBERS, names(SpecificationDocument.array(
                        bootstrap.get("required")))));
    }

    @Test
    @DisplayName("the kinds, the format version and the length limits are those of the schema")
    void valuesEqualSchema() {
        var kinds = SpecificationDocument.array(SpecificationDocument.object(properties.get("kind")).get("enum"))
                .stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());

        assertAll(
                () -> assertEquals(kinds, Arrays.stream(Kind.values()).map(Kind::wireName)
                        .collect(Collectors.toUnmodifiableSet())),
                () -> assertEquals(PendingEntryRecord.FORMAT_VERSION, number("format_version", "const")),
                () -> assertEquals(PendingEntryRecord.MAX_REQUESTED_ID, number("requested_id", "maxLength")),
                () -> assertEquals(PendingEntryRecord.MAX_HINT, number("hint", "maxLength")),
                () -> assertEquals("^ent-[0-9a-f]{8,32}$", SpecificationDocument.object(properties.get("entry_id"))
                        .get("pattern")));
    }

    private static int number(String member, String keyword) {
        return ((Number) SpecificationDocument.object(properties.get(member)).get(keyword)).intValue();
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
