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
import java.util.Objects;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;

/**
 * The records and the codec keep parity with the schema block of Store Schemas § Derived Facts Store (PM-EXT-6,
 * PM-IMPL-2, R10): the members of the document, of an entry, of {@code source} and of {@code failure} in schema order,
 * their required sets, the values of every enumeration, the patterns, the failure-reason bound and the format version.
 * A member, an enumeration value or a bound changed in the schema without the code fails here. The schema block is
 * read from the specification of this repository below {@value SpecificationDocument#ROOT_PROPERTY} and is test input:
 * it stays byte-stable (R10).
 */
@DisplayName("Derived facts schema parity")
class DerivedFactsSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/09-derived-facts-and-build-timings.adoc";
    private static final String HEADING = "== Derived Facts Store";

    private static Map<?, ?> schema;
    private static Map<?, ?> properties;
    private static Map<?, ?> entry;
    private static Map<?, ?> entryProperties;
    private static Map<?, ?> sourceProperties;

    @BeforeAll
    static void readSchema() {
        schema = SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, HEADING)));
        properties = SpecificationDocument.object(schema.get("properties"));
        entry = SpecificationDocument.object(SpecificationDocument.object(schema.get("$defs")).get("entry"));
        entryProperties = SpecificationDocument.object(entry.get("properties"));
        sourceProperties = SpecificationDocument.object(
                SpecificationDocument.object(entryProperties.get("source")).get("properties"));
    }

    @Test
    @DisplayName("the members of the document are those of the schema in schema order, every one required")
    void rootMembersEqualSchema() {
        assertAll(
                () -> assertEquals("DerivedFactsStore", schema.get("title"), "The schema titles the store"),
                () -> assertFalse(properties.isEmpty(), "The schema declares members"),
                () -> assertEquals(DerivedFactsCodec.MEMBERS, names(properties.keySet()),
                        "The document members are the schema's"),
                () -> assertEquals(DerivedFactsCodec.MEMBERS_REQUIRED, required(schema),
                        "The required document members are the schema's"));
    }

    @Test
    @DisplayName("the members of an entry, of source and of failure are those of the schema in schema order")
    void entryMembersEqualSchema() {
        var source = SpecificationDocument.object(entryProperties.get("source"));
        var failure = SpecificationDocument.object(entryProperties.get("failure"));

        assertAll(
                () -> assertEquals(DerivedFactsCodec.ENTRY_MEMBERS, names(entryProperties.keySet()),
                        "The entry members are the schema's"),
                () -> assertEquals(DerivedFactsCodec.ENTRY_REQUIRED, required(entry),
                        "The required entry members are the schema's"),
                () -> assertEquals(DerivedFactsCodec.SOURCE_MEMBERS, names(sourceProperties.keySet()),
                        "The source members are the schema's"),
                () -> assertEquals(DerivedFactsCodec.SOURCE_REQUIRED, required(source),
                        "The required source members are the schema's"),
                () -> assertEquals(DerivedFactsCodec.FAILURE_MEMBERS, keys(failure),
                        "The failure members are the schema's"),
                () -> assertEquals(DerivedFactsCodec.FAILURE_REQUIRED, required(failure),
                        "The required failure members are the schema's"));
    }

    @Test
    @DisplayName("the values of every enumeration are those of the schema in schema order")
    void enumerationsEqualSchema() {
        assertAll(
                () -> assertEquals(enumOf(entryProperties, "kind"),
                        wireNames(DerivedFactKind.values(), DerivedFactKind::wireName),
                        "The kind enumeration is the schema's"),
                () -> assertEquals(enumOf(sourceProperties, "class"),
                        wireNames(DerivedFact.DerivationClass.values(), DerivedFact.DerivationClass::wireName),
                        "The source class enumeration is the schema's"),
                () -> assertEquals(enumOf(entryProperties, "state"),
                        wireNames(DerivedFact.State.values(), DerivedFact.State::wireName),
                        "The state enumeration is the schema's"));
    }

    @Test
    @DisplayName("the patterns, the reason bound and the format version are the schema's")
    void patternsAndBoundsEqualSchema() {
        var facts = SpecificationDocument.object(properties.get("facts"));
        var propertyNames = SpecificationDocument.object(facts.get("propertyNames"));
        var failureReason = SpecificationDocument.object(
                SpecificationDocument.object(entryProperties.get("failure")).get("properties")).get("reason");
        var formatVersion = SpecificationDocument.object(properties.get("format_version"));

        assertAll(
                () -> assertEquals(DerivedFactKey.FORM_TEXT, propertyNames.get("pattern"),
                        "The key pattern is the schema's"),
                () -> assertEquals(DerivedFact.INPUT_DIGEST_TEXT,
                        SpecificationDocument.object(entryProperties.get("input_digest")).get("pattern"),
                        "The input digest pattern is the schema's"),
                () -> assertEquals(DerivedFact.Source.DERIVATION_TEXT,
                        SpecificationDocument.object(sourceProperties.get("derivation")).get("pattern"),
                        "The derivation pattern is the schema's"),
                () -> assertEquals(DerivedFact.Failure.MAX_REASON,
                        number(SpecificationDocument.object(failureReason).get("maxLength")),
                        "The failure reason bound is the schema's"),
                () -> assertEquals(DerivedFacts.FORMAT_VERSION, number(formatVersion.get("const")),
                        "The format version is the schema's"));
    }

    @Test
    @DisplayName("the document, an entry, source and failure are closed at every level")
    void everyObjectIsClosed() {
        assertAll(
                () -> assertEquals(Boolean.FALSE, schema.get("additionalProperties"),
                        "The document is closed"),
                () -> assertEquals(Boolean.FALSE, entry.get("additionalProperties"),
                        "An entry is closed"),
                () -> assertEquals(Boolean.FALSE,
                        SpecificationDocument.object(entryProperties.get("source")).get("additionalProperties"),
                        "The source is closed"),
                () -> assertEquals(Boolean.FALSE,
                        SpecificationDocument.object(entryProperties.get("failure")).get("additionalProperties"),
                        "The failure is closed"));
    }

    private static List<String> keys(Map<?, ?> object) {
        return names(SpecificationDocument.object(object.get("properties")).keySet());
    }

    private static List<String> required(Map<?, ?> object) {
        return names(SpecificationDocument.array(object.get("required")));
    }

    private static List<String> enumOf(Map<?, ?> owner, String name) {
        return SpecificationDocument.array(SpecificationDocument.object(owner.get(name)).get("enum")).stream()
                .filter(Objects::nonNull).map(String::valueOf).toList();
    }

    private static <E> List<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).toList();
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
