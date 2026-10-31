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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.PendingEntryRecord.Bootstrap;
import de.planmarshall.core.store.PendingEntryRecord.Kind;

/**
 * Reads and writes the pending entry record {@code pending/<entry_id>.json} (PM-CLIENT-1, PM-IMPL-2).
 * <p>
 * The reader checks the {@code format_version} first, then reads every member strictly: each at most once, only the
 * declared members, every required member, one JSON type per member, and the rules of the record. The call frames and
 * the condition overlay of the bootstrap are read by the readers of the status document. The raw members {@code facts},
 * {@code source} and {@code options} must be JSON objects ({@code source} also {@code null}) and are captured as the
 * exact text they were read with. Whatever does not parse is a {@link StoreRefusal} naming the file.
 * <p>
 * The writer writes every member in the order of the schema, the raw members as the text they were read with, so what
 * a source unit or a bootstrap state wrote is re-emitted byte for byte.
 *
 * @since 0.1
 */
@UtilityClass
public class PendingEntryCodec {

    private static final String ENTRY_ID = "entry_id";
    private static final String KIND = "kind";
    private static final String REQUESTED_ID = "requested_id";
    private static final String HINT = "hint";
    private static final String FROM_EPIC = "from_epic";
    private static final String CREATED_AT = "created_at";
    private static final String UPDATED_AT = "updated_at";
    private static final String BOOTSTRAP = "bootstrap";
    private static final String STATE = "state";
    private static final String CALL_STACK = "call_stack";
    private static final String FACTS = "facts";
    private static final String SOURCE = "source";
    private static final String OPTIONS = "options";
    private static final String PENDING_CONDITION = "pending_condition";
    private static final String LAST_REFUSAL = "last_refusal";

    /** The members of the record, in the order of the schema. */
    static final List<String> MEMBERS = List.of(FIELD_FORMAT_VERSION, ENTRY_ID, KIND, REQUESTED_ID, HINT, FROM_EPIC,
            CREATED_AT, UPDATED_AT, BOOTSTRAP);

    /** The members of the bootstrap state, in the order of the schema. */
    static final List<String> BOOTSTRAP_MEMBERS = List.of(STATE, CALL_STACK, FACTS, SOURCE, OPTIONS,
            PENDING_CONDITION, LAST_REFUSAL);

    /**
     * @param entry a pending entry record
     * @return the document, UTF-8 encoded
     */
    public static byte[] write(PendingEntryRecord entry) {
        Objects.requireNonNull(entry, "entry");
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, PendingEntryRecord.FORMAT_VERSION);
            generator.writeStringField(ENTRY_ID, entry.entryId().value());
            generator.writeStringField(KIND, entry.kind().wireName());
            writeNullable(generator, REQUESTED_ID, entry.requestedId());
            writeNullable(generator, HINT, entry.hint());
            writeNullable(generator, FROM_EPIC, entry.fromEpic());
            writeInstant(generator, CREATED_AT, entry.createdAt());
            writeInstant(generator, UPDATED_AT, entry.updatedAt());
            writeBootstrap(generator, entry.bootstrap());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Pending entry record cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the record file the document was read from, named by a refusal
     * @param document a pending entry record, UTF-8 encoded
     * @return the record
     * @throws StoreRefusal if the document declares another format version or does not have the one shape of a
     *                      pending entry record
     */
    public static PendingEntryRecord read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, PendingEntryRecord.FORMAT_VERSION, parser -> {
            try {
                return readDocument(parser, document);
            } catch (IllegalArgumentException e) {
                throw unreadable(e.getMessage(), e);
            }
        });
    }

    private static void writeBootstrap(JsonGenerator generator, Bootstrap bootstrap) throws IOException {
        generator.writeObjectFieldStart(BOOTSTRAP);
        generator.writeStringField(STATE, bootstrap.state());
        WorkflowStatusCodec.writeCallStack(generator, bootstrap.callStack());
        generator.writeFieldName(FACTS);
        generator.writeRawValue(bootstrap.facts());
        generator.writeFieldName(SOURCE);
        if (bootstrap.source() == null) {
            generator.writeNull();
        } else {
            generator.writeRawValue(bootstrap.source());
        }
        generator.writeFieldName(OPTIONS);
        generator.writeRawValue(bootstrap.options());
        WorkflowStatusCodec.writeCondition(generator, bootstrap.pendingCondition());
        writeNullable(generator, LAST_REFUSAL, bootstrap.lastRefusal());
        generator.writeEndObject();
    }

    private static PendingEntryRecord readDocument(JsonParser parser, byte[] document) throws IOException {
        var entry = PendingEntryRecord.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case ENTRY_ID -> entry.entryId(new EntryId(string(parser, field)));
                case KIND -> entry.kind(kind(string(parser, field)));
                case REQUESTED_ID -> entry.requestedId(nullableString(parser, field));
                case HINT -> entry.hint(nullableString(parser, field));
                case FROM_EPIC -> entry.fromEpic(nullableString(parser, field));
                case CREATED_AT -> entry.createdAt(instant(parser, field));
                case UPDATED_AT -> entry.updatedAt(instant(parser, field));
                case BOOTSTRAP -> entry.bootstrap(readBootstrap(parser, document));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "pending entry record", MEMBERS.toArray(String[]::new));
        return entry.build();
    }

    private static Bootstrap readBootstrap(JsonParser parser, byte[] document) throws IOException {
        requireObject(parser, BOOTSTRAP);
        var bootstrap = Bootstrap.builder();
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case STATE -> bootstrap.state(string(parser, field));
                case CALL_STACK -> bootstrap.callStack(
                        StrictJson.array(parser, field, WorkflowStatusCodec::readFrame));
                case FACTS -> bootstrap.facts(rawObject(parser, document, field));
                case SOURCE -> bootstrap.source(isNull(parser) ? null : rawObject(parser, document, field));
                case OPTIONS -> bootstrap.options(rawObject(parser, document, field));
                case PENDING_CONDITION -> bootstrap.pendingCondition(
                        isNull(parser) ? null : WorkflowStatusCodec.readCondition(parser, document));
                case LAST_REFUSAL -> bootstrap.lastRefusal(nullableString(parser, field));
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, BOOTSTRAP, BOOTSTRAP_MEMBERS.toArray(String[]::new));
        return bootstrap.build();
    }

    private static String rawObject(JsonParser parser, byte[] document, String field) throws IOException {
        requireObject(parser, field);
        return WorkflowStatusCodec.raw(parser, document);
    }

    private static Kind kind(String wireName) {
        return Kind.fromWireName(wireName)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(KIND, wireName)));
    }

    private static boolean isNull(JsonParser parser) {
        return parser.currentToken() == JsonToken.VALUE_NULL;
    }

    private static void writeNullable(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }
}
