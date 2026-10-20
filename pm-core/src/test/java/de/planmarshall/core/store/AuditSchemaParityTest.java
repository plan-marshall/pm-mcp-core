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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;
import de.planmarshall.core.store.AuditRecord.Category;
import de.planmarshall.core.store.AuditRecord.Outcome;
import de.planmarshall.core.store.AuditRecord.Scope;

/**
 * The codec of the audit trail keeps parity with the three schemas of Store Schemas § Audit Log, § Audit Head and
 * § Audit Index (PM-SEC-6): the fields it reads and writes in schema order, the fields it requires, the values of
 * every enum, the forms of ids and hashes, the format version 1 of each, and every document it writes is valid against
 * its schema. The schemas are read from the specification of this repository below {@value SpecificationDocument#ROOT_PROPERTY}.
 */
@DisplayName("Audit schema parity")
class AuditSchemaParityTest {

    private static final String DOCUMENT = "store-schemas/12-machine-stores.adoc";
    private static final String EVENT_ID = "01890a5d-ac96-774b-bcce-b302099a8057";
    private static final String OTHER_EVENT_ID = "01890a5d-ac97-7c4b-9cce-b302099a8058";
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static Map<?, ?> schema(String anchor) {
        return SpecificationDocument.object(
                SpecificationDocument.parse(SpecificationDocument.jsonBlockAfter(DOCUMENT, anchor)));
    }

    private static List<String> members(Map<?, ?> schema) {
        return SpecificationDocument.object(schema.get("properties")).keySet().stream().map(String::valueOf).toList();
    }

    private static List<String> required(Map<?, ?> schema) {
        return SpecificationDocument.array(schema.get("required")).stream().map(String::valueOf).toList();
    }

    private static Map<?, ?> member(Map<?, ?> schema, String name) {
        return SpecificationDocument.object(SpecificationDocument.object(schema.get("properties")).get(name));
    }

    private static int formatVersion(Map<?, ?> schema) {
        return ((Number) member(schema, "format_version").get("const")).intValue();
    }

    private static String pattern(Map<?, ?> schema, String name) {
        return String.valueOf(member(schema, name).get("pattern"));
    }

    private static String anchored(Pattern pattern) {
        return "^" + pattern.pattern() + "$";
    }

    private static Set<String> enumOf(Map<?, ?> schema, String name) {
        return SpecificationDocument.array(member(schema, name).get("enum")).stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static <E extends Enum<E>> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    /** Reads the schema first, so a missing checkout skips the test before any assertion runs. */
    private static JsonSchema validator(String anchor) {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(SpecificationDocument.jsonBlockAfter(DOCUMENT, anchor));
    }

    private static void assertValid(JsonSchema validator, byte[] document) {
        var written = new String(document, UTF_8);

        assertEquals(Set.of(), validator.validate(written, InputFormat.JSON), () -> "violations of " + written);
    }

    @Nested
    @DisplayName("audit record")
    class Records {

        private static final String ANCHOR = "[#audit-log]";

        @Test
        @DisplayName("the fields of the codec are the members of the schema in schema order, the required ones too")
        void members() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertFalse(AuditSchemaParityTest.members(schema).isEmpty()),
                    () -> assertEquals(AuditCodec.RECORD_FIELDS, AuditSchemaParityTest.members(schema)),
                    () -> assertEquals(AuditCodec.RECORD_REQUIRED, required(schema)));
        }

        @Test
        @DisplayName("category, outcome and scope type have the values of the schema's enums")
        void enums() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(enumOf(schema, "category"), wireNames(Category.values(), Category::wireName)),
                    () -> assertEquals(enumOf(schema, "outcome"), wireNames(Outcome.values(), Outcome::wireName)),
                    () -> assertEquals(enumOf(schema, "scope_type"), wireNames(Scope.values(), Scope::wireName)));
        }

        @Test
        @DisplayName("the forms of event id and prev_hash and the format version are those of the schema")
        void forms() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(pattern(schema, "event_id"), anchored(AuditRecord.EVENT_ID)),
                    () -> assertEquals(pattern(schema, "prev_hash"), anchored(AuditRecord.HASH)),
                    () -> assertEquals(AuditRecord.FORMAT_VERSION, formatVersion(schema)));
        }

        @Test
        @DisplayName("a written record is valid against the schema, with and without a scope id")
        void valid() {
            var scoped = AuditRecord.builder().eventId(EVENT_ID).prevHash(AuditCodec.GENESIS_HASH)
                    .timestamp(Instant.parse("2026-10-14T09:30:15.250Z")).category(Category.STORE)
                    .event("transaction_rolled_forward").scopeType(Scope.PLAN).scopeId("audit-trail")
                    .actor("oliver").outcome(Outcome.SUCCEEDED)
                    .details(Map.of("targets", List.of("status.json"), "count", 1, "flag", true)).build();
            var machine = scoped.withScopeType(Scope.MACHINE).withScopeId(null);
            var entry = scoped.withScopeType(Scope.ENTRY).withScopeId("ent-0123456789abcdef");
            var validator = validator(ANCHOR);

            assertAll(
                    () -> assertValid(validator, AuditCodec.writeRecord(scoped)),
                    () -> assertValid(validator, AuditCodec.writeRecord(machine)),
                    () -> assertValid(validator, AuditCodec.writeRecord(entry)));
        }
    }

    @Nested
    @DisplayName("audit head")
    class Heads {

        private static final String ANCHOR = "[#audit-head]";

        @Test
        @DisplayName("the fields of the codec are the members of the schema in schema order, every one required")
        void members() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(AuditCodec.HEAD_FIELDS, AuditSchemaParityTest.members(schema)),
                    () -> assertEquals(AuditCodec.HEAD_FIELDS, required(schema)));
        }

        @Test
        @DisplayName("the file, the forms of the hashes and the format version are those of the schema")
        void forms() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(AuditHeadRecord.CURRENT_LOG_FILE, member(schema, "file").get("const")),
                    () -> assertEquals(pattern(schema, "head_hash"), anchored(AuditRecord.HASH)),
                    () -> assertEquals(pattern(schema, "index_head_hash"), anchored(AuditRecord.HASH)),
                    () -> assertEquals(pattern(schema, "hmac"), anchored(AuditRecord.HASH)),
                    () -> assertEquals(AuditHeadRecord.FORMAT_VERSION, formatVersion(schema)));
        }

        @Test
        @DisplayName("a written head is valid against the schema, for a log with records and an empty one")
        void valid() {
            var validator = validator(ANCHOR);

            assertAll(
                    () -> assertValid(validator, AuditCodec.writeHead(new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 2,
                            EVENT_ID, HASH, AuditCodec.GENESIS_HASH, HASH))),
                    () -> assertValid(validator, AuditCodec.writeHead(new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 0,
                            null, AuditCodec.GENESIS_HASH, HASH, HASH))));
        }
    }

    @Nested
    @DisplayName("audit index entry")
    class IndexEntries {

        private static final String ANCHOR = "[#audit-index]";

        @Test
        @DisplayName("the fields of the codec are the members of the schema in schema order, every one required")
        void members() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(AuditCodec.INDEX_FIELDS, AuditSchemaParityTest.members(schema)),
                    () -> assertEquals(AuditCodec.INDEX_FIELDS, required(schema)));
        }

        @Test
        @DisplayName("the forms of the file and the hashes and the format version are those of the schema")
        void forms() {
            var schema = schema(ANCHOR);

            assertAll(
                    () -> assertEquals(pattern(schema, "file"), anchored(AuditIndexEntry.ROTATED_FILE_FORM)),
                    () -> assertEquals(pattern(schema, "prev_hash"), anchored(AuditRecord.HASH)),
                    () -> assertEquals(pattern(schema, "checkpoint_hash"), anchored(AuditRecord.HASH)),
                    () -> assertEquals(1, ((Number) member(schema, "record_count").get("minimum")).intValue()),
                    () -> assertEquals(AuditIndexEntry.FORMAT_VERSION, formatVersion(schema)));
        }

        @Test
        @DisplayName("a written entry is valid against the schema")
        void valid() {
            assertValid(validator(ANCHOR), AuditCodec.writeIndexEntry(new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                    AuditIndexEntry.rotatedFileName(EVENT_ID), EVENT_ID, OTHER_EVENT_ID, 3, HASH)));
        }
    }
}
