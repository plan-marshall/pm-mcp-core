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

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.AuditRecord.Category;
import de.planmarshall.core.store.AuditRecord.Outcome;
import de.planmarshall.core.store.AuditRecord.Scope;

/**
 * Reads and writes the three files of the audit trail (PM-SEC-6, PM-IMPL-2): a record of the log
 * ({@link AuditRecord}), the head ({@link AuditHeadRecord}) and an entry of the index ({@link AuditIndexEntry}).
 * <p>
 * A reader checks the {@code format_version} first, then reads every field strictly: each at most once, only the
 * declared fields, every required field, one JSON type per field, the closed sets and the forms of ids and hashes.
 * Whatever does not parse is a {@link StoreRefusal} naming the file; a caller that reads a JSONL file line by line
 * names the line beside it.
 * <p>
 * A writer writes every field in the order of its schema and no whitespace; the details of a record are written in
 * the sorted order of their names. The canonical line of a record is its exact UTF-8 bytes in the log without the
 * newline: what the chain hashes ({@link #sha256Hex(byte[])}) is always the bytes as they stand in the file, never a
 * re-encoding of them.
 *
 * @since 0.1
 */
@UtilityClass
public class AuditCodec {

    /** The hash a chain starts from: 64 {@code 0} characters. */
    public static final String GENESIS_HASH = "0".repeat(64);

    private static final String EVENT_ID = "event_id";
    private static final String PREV_HASH = "prev_hash";
    private static final String TIMESTAMP = "timestamp";
    private static final String CATEGORY = "category";
    private static final String EVENT = "event";
    private static final String SCOPE_TYPE = "scope_type";
    private static final String SCOPE_ID = "scope_id";
    private static final String ACTOR = "actor";
    private static final String OUTCOME = "outcome";
    private static final String DETAILS = "details";
    private static final String FILE = "file";
    private static final String RECORD_COUNT = "record_count";
    private static final String LAST_EVENT_ID = "last_event_id";
    private static final String HEAD_HASH = "head_hash";
    private static final String INDEX_HEAD_HASH = "index_head_hash";
    private static final String HMAC = "hmac";
    private static final String FIRST_EVENT_ID = "first_event_id";
    private static final String CHECKPOINT_HASH = "checkpoint_hash";

    /** The fields of a record, in the order of the schema. */
    static final List<String> RECORD_FIELDS = List.of(FIELD_FORMAT_VERSION, EVENT_ID, PREV_HASH, TIMESTAMP, CATEGORY,
            EVENT, SCOPE_TYPE, SCOPE_ID, ACTOR, OUTCOME, DETAILS);

    /** The fields a record must have, in the order of the schema. */
    static final List<String> RECORD_REQUIRED = List.of(FIELD_FORMAT_VERSION, EVENT_ID, PREV_HASH, TIMESTAMP,
            CATEGORY, EVENT, ACTOR, OUTCOME);

    /** The fields of the head, in the order of the schema; every one is required. */
    static final List<String> HEAD_FIELDS = List.of(FIELD_FORMAT_VERSION, FILE, RECORD_COUNT, LAST_EVENT_ID,
            HEAD_HASH, INDEX_HEAD_HASH, HMAC);

    /** The fields of an index entry, in the order of the schema; every one is required. */
    static final List<String> INDEX_FIELDS = List.of(FIELD_FORMAT_VERSION, PREV_HASH, FILE, FIRST_EVENT_ID,
            LAST_EVENT_ID, RECORD_COUNT, CHECKPOINT_HASH);

    /**
     * @param line a canonical line, without its newline
     * @return the SHA-256 of the line, in lower-case hex
     */
    public static String sha256Hex(byte[] line) {
        return StoreSnapshot.sha256(Objects.requireNonNull(line, "line"));
    }

    /**
     * @param auditRecord a record
     * @return its canonical line, UTF-8 encoded, without the newline
     */
    public static byte[] writeRecord(AuditRecord auditRecord) {
        Objects.requireNonNull(auditRecord, "auditRecord");
        return write(generator -> {
            generator.writeNumberField(FIELD_FORMAT_VERSION, AuditRecord.FORMAT_VERSION);
            generator.writeStringField(EVENT_ID, auditRecord.eventId());
            generator.writeStringField(PREV_HASH, auditRecord.prevHash());
            writeInstant(generator, TIMESTAMP, auditRecord.timestamp());
            generator.writeStringField(CATEGORY, auditRecord.category().wireName());
            generator.writeStringField(EVENT, auditRecord.event());
            writeNullable(generator, SCOPE_TYPE,
                    auditRecord.scopeType() == null ? null : auditRecord.scopeType().wireName());
            writeNullable(generator, SCOPE_ID, auditRecord.scopeId());
            generator.writeStringField(ACTOR, auditRecord.actor());
            generator.writeStringField(OUTCOME, auditRecord.outcome().wireName());
            writeDetails(generator, auditRecord.details());
        });
    }

    /**
     * @param file the log the line was read from, named by a refusal
     * @param line a canonical line, UTF-8 encoded, without its newline
     * @return the record
     * @throws StoreRefusal if the line declares another format version or does not have the one shape of a record
     */
    public static AuditRecord readRecord(Path file, byte[] line) {
        return read(file, line, AuditRecord.FORMAT_VERSION, AuditCodec::readRecordObject);
    }

    /**
     * @param head a head
     * @return its document, UTF-8 encoded
     */
    public static byte[] writeHead(AuditHeadRecord head) {
        Objects.requireNonNull(head, "head");
        return write(generator -> {
            writeHeadFields(generator, head);
            generator.writeStringField(HMAC, head.hmac());
        });
    }

    /**
     * The bytes the HMAC of a head covers: the canonical JSON of every field but {@code hmac}, in the order of the
     * schema and without whitespace. The {@code hmac} of the given head is not read, so a head is signed by building
     * it with any well-formed HMAC first.
     *
     * @param head a head
     * @return the content the HMAC covers, UTF-8 encoded
     */
    public static byte[] signedContent(AuditHeadRecord head) {
        Objects.requireNonNull(head, "head");
        return write(generator -> writeHeadFields(generator, head));
    }

    /**
     * @param file     the head file the document was read from, named by a refusal
     * @param document a head, UTF-8 encoded
     * @return the head
     * @throws StoreRefusal if the document declares another format version or does not have the one shape of a head
     */
    public static AuditHeadRecord readHead(Path file, byte[] document) {
        return read(file, document, AuditHeadRecord.FORMAT_VERSION, AuditCodec::readHeadObject);
    }

    /**
     * @param entry an index entry
     * @return its canonical line, UTF-8 encoded, without the newline
     */
    public static byte[] writeIndexEntry(AuditIndexEntry entry) {
        Objects.requireNonNull(entry, "entry");
        return write(generator -> {
            generator.writeNumberField(FIELD_FORMAT_VERSION, AuditIndexEntry.FORMAT_VERSION);
            generator.writeStringField(PREV_HASH, entry.prevHash());
            generator.writeStringField(FILE, entry.file());
            generator.writeStringField(FIRST_EVENT_ID, entry.firstEventId());
            generator.writeStringField(LAST_EVENT_ID, entry.lastEventId());
            generator.writeNumberField(RECORD_COUNT, entry.recordCount());
            generator.writeStringField(CHECKPOINT_HASH, entry.checkpointHash());
        });
    }

    /**
     * @param file the index the line was read from, named by a refusal
     * @param line an index line, UTF-8 encoded, without its newline
     * @return the index entry
     * @throws StoreRefusal if the line declares another format version or does not have the one shape of an index
     *                      entry
     */
    public static AuditIndexEntry readIndexEntry(Path file, byte[] line) {
        return read(file, line, AuditIndexEntry.FORMAT_VERSION, AuditCodec::readIndexObject);
    }

    @FunctionalInterface
    private interface Fields {
        void write(JsonGenerator generator) throws IOException;
    }

    private static byte[] write(Fields fields) {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            fields.write(generator);
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Audit document cannot be written", e);
        }
        return out.toByteArray();
    }

    private static <T> T read(Path file, byte[] document, int formatVersion, StrictJson.DocumentReader<T> reader) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, formatVersion, parser -> {
            try {
                return reader.read(parser);
            } catch (IllegalArgumentException e) {
                throw unreadable(e.getMessage(), e);
            }
        });
    }

    private static void writeHeadFields(JsonGenerator generator, AuditHeadRecord head) throws IOException {
        generator.writeNumberField(FIELD_FORMAT_VERSION, AuditHeadRecord.FORMAT_VERSION);
        generator.writeStringField(FILE, head.file());
        generator.writeNumberField(RECORD_COUNT, head.recordCount());
        writeNullable(generator, LAST_EVENT_ID, head.lastEventId());
        generator.writeStringField(HEAD_HASH, head.headHash());
        generator.writeStringField(INDEX_HEAD_HASH, head.indexHeadHash());
    }

    private static void writeDetails(JsonGenerator generator, Map<String, Object> details) throws IOException {
        generator.writeObjectFieldStart(DETAILS);
        for (var detail : new TreeMap<>(details).entrySet()) {
            var name = detail.getKey();
            switch (detail.getValue()) {
                case String text -> generator.writeStringField(name, text);
                case Long number -> generator.writeNumberField(name, number);
                case Boolean flag -> generator.writeBooleanField(name, flag);
                case List<?> items -> {
                    generator.writeArrayFieldStart(name);
                    for (var item : items) {
                        generator.writeString((String) item);
                    }
                    generator.writeEndArray();
                }
                default -> throw new IllegalStateException("A record admits no detail of this type: " + name);
            }
        }
        generator.writeEndObject();
    }

    private static AuditRecord readRecordObject(JsonParser parser) throws IOException {
        var auditRecord = AuditRecord.builder().details(Map.of());
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case EVENT_ID -> auditRecord.eventId(string(parser, field));
                case PREV_HASH -> auditRecord.prevHash(string(parser, field));
                case TIMESTAMP -> auditRecord.timestamp(instant(parser, field));
                case CATEGORY -> auditRecord.category(Category.fromWireName(string(parser, field))
                        .orElseThrow(() -> undeclaredValue(parser, field)));
                case EVENT -> auditRecord.event(string(parser, field));
                case SCOPE_TYPE -> auditRecord.scopeType(scope(parser, field));
                case SCOPE_ID -> auditRecord.scopeId(nullableString(parser, field));
                case ACTOR -> auditRecord.actor(string(parser, field));
                case OUTCOME -> auditRecord.outcome(Outcome.fromWireName(string(parser, field))
                        .orElseThrow(() -> undeclaredValue(parser, field)));
                case DETAILS -> auditRecord.details(readDetails(parser));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "audit record", RECORD_REQUIRED.toArray(String[]::new));
        return auditRecord.build();
    }

    private static Scope scope(JsonParser parser, String field) throws IOException {
        var wireName = nullableString(parser, field);
        return wireName == null ? null
                : Scope.fromWireName(wireName).orElseThrow(() -> undeclaredValue(parser, field));
    }

    private static StrictJson.Malformed undeclaredValue(JsonParser parser, String field) {
        String value;
        try {
            value = parser.getText();
        } catch (IOException e) {
            return unreadable("'%s' has an undeclared value".formatted(field), e);
        }
        return unreadable("'%s' has the undeclared value '%s'".formatted(field, value));
    }

    private static Map<String, Object> readDetails(JsonParser parser) throws IOException {
        requireObject(parser, DETAILS);
        var details = new LinkedHashMap<String, Object>();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var name = nextField(parser, seen);
            details.put(name, switch (parser.currentToken()) {
                case VALUE_STRING -> parser.getText();
                case VALUE_NUMBER_INT -> integer(parser, name);
                case VALUE_TRUE -> Boolean.TRUE;
                case VALUE_FALSE -> Boolean.FALSE;
                case START_ARRAY -> readStrings(parser, name);
                case null, default -> throw unreadable(
                        "detail '%s' is not a string, an integer, a boolean or an array of strings".formatted(name));
            });
        }
        return details;
    }

    private static List<String> readStrings(JsonParser parser, String name) throws IOException {
        var items = new ArrayList<String>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            items.add(string(parser, name));
        }
        return List.copyOf(items);
    }

    private static AuditHeadRecord readHeadObject(JsonParser parser) throws IOException {
        @Nullable String file = null;
        long recordCount = 0;
        @Nullable String lastEventId = null;
        @Nullable String headHash = null;
        @Nullable String indexHeadHash = null;
        @Nullable String hmac = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FILE -> file = string(parser, field);
                case RECORD_COUNT -> recordCount = integer(parser, field);
                case LAST_EVENT_ID -> lastEventId = nullableString(parser, field);
                case HEAD_HASH -> headHash = string(parser, field);
                case INDEX_HEAD_HASH -> indexHeadHash = string(parser, field);
                case HMAC -> hmac = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "audit head", HEAD_FIELDS.toArray(String[]::new));
        return new AuditHeadRecord(file, recordCount, lastEventId, headHash, indexHeadHash, hmac);
    }

    private static AuditIndexEntry readIndexObject(JsonParser parser) throws IOException {
        @Nullable String prevHash = null;
        @Nullable String file = null;
        @Nullable String firstEventId = null;
        @Nullable String lastEventId = null;
        long recordCount = 0;
        @Nullable String checkpointHash = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case PREV_HASH -> prevHash = string(parser, field);
                case FILE -> file = string(parser, field);
                case FIRST_EVENT_ID -> firstEventId = string(parser, field);
                case LAST_EVENT_ID -> lastEventId = string(parser, field);
                case RECORD_COUNT -> recordCount = integer(parser, field);
                case CHECKPOINT_HASH -> checkpointHash = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "audit index entry", INDEX_FIELDS.toArray(String[]::new));
        return new AuditIndexEntry(prevHash, file, firstEventId, lastEventId, recordCount, checkpointHash);
    }

    private static void writeNullable(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }
}
