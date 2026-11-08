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
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.AuditRecord.Category;
import de.planmarshall.core.store.AuditRecord.Outcome;
import de.planmarshall.core.store.AuditRecord.Scope;

/**
 * The codec of the audit trail keeps parity with the three schemas of Store Schemas § Audit Log, § Audit Head and
 * § Audit Index (PM-SEC-6): the fields it reads and writes in schema order, the fields it requires, the values of
 * every enum, the forms of ids and hashes, the format version 1 of each, and every document it writes is valid against
 * its schema. Each schema is the file beside the codec in
 * {@code pm-core/src/main/resources/de/planmarshall/core/store/}, read from the class path through {@link SchemaResource}.
 */
@EnableGeneratorController
@DisplayName("Audit schema parity")
class AuditSchemaParityTest {

    private static final String RECORD_SCHEMA = "de/planmarshall/core/store/audit-record.schema.json";
    private static final String EVENT_ID = "01890a5d-ac96-774b-bcce-b302099a8057";
    private static final String OTHER_EVENT_ID = "01890a5d-ac97-7c4b-9cce-b302099a8058";
    private static final String HASH = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

    private static Map<?, ?> schema(String resource) {
        return SchemaResource.load(resource);
    }

    private static List<String> members(Map<?, ?> schema) {
        return SchemaResource.object(schema.get("properties")).keySet().stream().map(String::valueOf).toList();
    }

    private static List<String> required(Map<?, ?> schema) {
        return SchemaResource.array(schema.get("required")).stream().map(String::valueOf).toList();
    }

    private static Map<?, ?> member(Map<?, ?> schema, String name) {
        return SchemaResource.object(SchemaResource.object(schema.get("properties")).get(name));
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
        return SchemaResource.array(member(schema, name).get("enum")).stream().filter(Objects::nonNull)
                .map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static <E extends Enum<E>> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    /** Reads the schema file, so an absent resource fails the test before any assertion runs. */
    private static JsonSchema validator(String resource) {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                .getSchema(SchemaResource.text(resource));
    }

    private static void assertValid(JsonSchema validator, byte[] document) {
        assertValid(validator, document, "the written document is valid");
    }

    private static void assertValid(JsonSchema validator, byte[] document, String message) {
        var written = new String(document, UTF_8);

        assertEquals(Set.of(), validator.validate(written, InputFormat.JSON),
                () -> message + ": violations of " + written);
    }

    @Nested
    @DisplayName("audit record")
    class Records {

        private static final String SCHEMA = RECORD_SCHEMA;

        @Test
        @DisplayName("the fields of the codec are the members of the schema in schema order, the required ones too")
        void members() {
            var schema = schema(SCHEMA);

            assertAll("codec fields match the schema members",
                    () -> assertFalse(AuditSchemaParityTest.members(schema).isEmpty(),
                            "the schema has at least one member"),
                    () -> assertEquals(AuditCodec.RECORD_FIELDS, AuditSchemaParityTest.members(schema),
                            "the record fields are the schema members in order"),
                    () -> assertEquals(AuditCodec.RECORD_REQUIRED, required(schema),
                            "the required record fields match the schema"));
        }

        @Test
        @DisplayName("category, outcome and scope type have the values of the schema's enums")
        void enums() {
            var schema = schema(SCHEMA);

            assertAll("enums have the schema's values",
                    () -> assertEquals(enumOf(schema, "category"), wireNames(Category.values(), Category::wireName),
                            "the category values are the schema's"),
                    () -> assertEquals(enumOf(schema, "outcome"), wireNames(Outcome.values(), Outcome::wireName),
                            "the outcome values are the schema's"),
                    () -> assertEquals(enumOf(schema, "scope_type"), wireNames(Scope.values(), Scope::wireName),
                            "the scope type values are the schema's"));
        }

        @Test
        @DisplayName("the forms of event id and prev_hash and the format version are those of the schema")
        void forms() {
            var schema = schema(SCHEMA);

            assertAll("id, hash and format version forms match",
                    () -> assertEquals(pattern(schema, "event_id"), anchored(AuditRecord.EVENT_ID),
                            "the event id pattern is the schema's"),
                    () -> assertEquals(pattern(schema, "prev_hash"), anchored(AuditRecord.HASH),
                            "the prev_hash pattern is the schema's"),
                    () -> assertEquals(AuditRecord.FORMAT_VERSION, formatVersion(schema),
                            "the format version is the schema's"));
        }

        @Test
        @DisplayName("a written record is valid against the schema, with and without a scope id")
        void valid() {
            var scoped = AuditRecord.builder().eventId(EVENT_ID).prevHash(AuditCodec.GENESIS_HASH)
                    .timestamp(Instant.ofEpochSecond(
                            Generators.longs(1_600_000_000L, 1_900_000_000L).next()))
                    .category(Category.STORE).event("transaction_rolled_forward").scopeType(Scope.PLAN)
                    .scopeId("plan-" + Generators.letterStrings(3, 20).next().toLowerCase(Locale.ROOT))
                    .actor(Generators.letterStrings(3, 20).next()).outcome(Outcome.SUCCEEDED)
                    .details(Map.of("targets", List.of("status.json"), "count",
                            Generators.integers(0, 100).next(), "flag", true))
                    .build();
            var machine = scoped.withScopeType(Scope.MACHINE).withScopeId(null);
            var entry = scoped.withScopeType(Scope.ENTRY)
                    .withScopeId("ent-"
                            + "%08x".formatted(Generators.integers(0, Integer.MAX_VALUE - 1).next()));
            var validator = validator(SCHEMA);

            assertAll("written records are valid against the schema",
                    () -> assertValid(validator, AuditCodec.writeRecord(scoped)),
                    () -> assertValid(validator, AuditCodec.writeRecord(machine)),
                    () -> assertValid(validator, AuditCodec.writeRecord(entry)));
        }
    }

    @Nested
    @DisplayName("audit head")
    class Heads {

        private static final String SCHEMA = "de/planmarshall/core/store/audit-head.schema.json";

        @Test
        @DisplayName("the fields of the codec are the members of the schema in schema order, every one required")
        void members() {
            var schema = schema(SCHEMA);

            assertAll("head fields match the schema members",
                    () -> assertEquals(AuditCodec.HEAD_FIELDS, AuditSchemaParityTest.members(schema),
                            "the head fields are the schema members in order"),
                    () -> assertEquals(AuditCodec.HEAD_FIELDS, required(schema),
                            "every head field is required by the schema"));
        }

        @Test
        @DisplayName("the file, the forms of the hashes and the format version are those of the schema")
        void forms() {
            var schema = schema(SCHEMA);

            assertAll("head forms and file match the schema",
                    () -> assertEquals(AuditHeadRecord.CURRENT_LOG_FILE, member(schema, "file").get("const"),
                            "the file is the current log file"),
                    () -> assertEquals(pattern(schema, "head_hash"), anchored(AuditRecord.HASH),
                            "the head_hash pattern is the schema's"),
                    () -> assertEquals(pattern(schema, "index_head_hash"), anchored(AuditRecord.HASH),
                            "the index head hash pattern is the schema's"),
                    () -> assertEquals(pattern(schema, "hmac"), anchored(AuditRecord.HASH),
                            "the hmac pattern is the schema's"),
                    () -> assertEquals(AuditHeadRecord.FORMAT_VERSION, formatVersion(schema),
                            "the format version is the schema's"));
        }

        @Test
        @DisplayName("a written head is valid against the schema, for a log with records and an empty one")
        void valid() {
            var validator = validator(SCHEMA);

            assertAll("written heads are valid against the schema",
                    () -> assertValid(validator,
                            AuditCodec.writeHead(new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE,
                                    Generators.longs(1, 1_000).next(), EVENT_ID, HASH, AuditCodec.GENESIS_HASH,
                                    HASH)),
                            "the head of a log with records is valid"),
                    () -> assertValid(validator, AuditCodec.writeHead(new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 0,
                            null, AuditCodec.GENESIS_HASH, HASH, HASH))));
        }
    }

    @Nested
    @DisplayName("audit index entry")
    class IndexEntries {

        private static final String SCHEMA = "de/planmarshall/core/store/audit-index-entry.schema.json";

        @Test
        @DisplayName("the fields of the codec are the members of the schema in schema order, every one required")
        void members() {
            var schema = schema(SCHEMA);

            assertAll("index fields match the schema members",
                    () -> assertEquals(AuditCodec.INDEX_FIELDS, AuditSchemaParityTest.members(schema),
                            "the index fields are the schema members in order"),
                    () -> assertEquals(AuditCodec.INDEX_FIELDS, required(schema),
                            "every index field is required by the schema"));
        }

        @Test
        @DisplayName("the forms of the file and the hashes and the format version are those of the schema")
        void forms() {
            var schema = schema(SCHEMA);

            assertAll("index forms match the schema's patterns",
                    () -> assertEquals(pattern(schema, "file"), anchored(AuditIndexEntry.ROTATED_FILE_FORM),
                            "the rotated file pattern is the schema's"),
                    () -> assertEquals(pattern(schema, "prev_hash"), anchored(AuditRecord.HASH),
                            "the prev_hash pattern is the schema's"),
                    () -> assertEquals(pattern(schema, "checkpoint_hash"), anchored(AuditRecord.HASH),
                            "the checkpoint hash pattern is the schema's"),
                    () -> assertEquals(1, ((Number) member(schema, "record_count").get("minimum")).intValue(),
                            "the record count minimum is one"),
                    () -> assertEquals(AuditIndexEntry.FORMAT_VERSION, formatVersion(schema),
                            "the format version is the schema's"));
        }

        @Test
        @DisplayName("a written entry is valid against the schema")
        void valid() {
            assertValid(validator(SCHEMA),
                    AuditCodec.writeIndexEntry(new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                            AuditIndexEntry.rotatedFileName(EVENT_ID), EVENT_ID, OTHER_EVENT_ID,
                            Generators.longs(1, 1_000).next(), HASH)),
                    "the written entry is valid");
        }
    }
}
