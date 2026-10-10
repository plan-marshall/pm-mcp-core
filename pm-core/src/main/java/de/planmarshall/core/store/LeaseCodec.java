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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * Reads and writes the document of a lease store (PM-IMPL-7, PM-IMPL-2):
 * {@code {"format_version": 1, "leases": [<lease>]}}, where a lease is its {@code key}, its {@code owner},
 * {@code acquired_at} and {@code lease_expires_at}.
 * <p>
 * The reader accepts exactly one shape and never tolerates another: exactly the current {@code format_version}, only
 * the declared fields, each at most once, and every required field. A document in another format version is refused
 * as outdated or newer and is never rewritten; anything else that does not parse is unreadable, never an empty
 * store.
 * <p>
 * Every instant has one shape in a store: an RFC 3339 string in UTC with the suffix {@code Z}, written as
 * {@link Instant#toString()} writes it. The reader refuses an instant in any other spelling of the same moment (an
 * offset, a fraction padded differently), so what it reads is what the writer writes again: a lease written by one
 * component and read and written by another keeps its instants byte for byte.
 *
 * @since 0.1
 */
@UtilityClass
public class LeaseCodec {

    /** The format version of a lease store document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_FORMAT_VERSION = "format_version";
    private static final String FIELD_LEASES = "leases";
    private static final String FIELD_KEY = "key";
    private static final String FIELD_OWNER = "owner";
    private static final String FIELD_ACQUIRED_AT = "acquired_at";
    private static final String FIELD_LEASE_EXPIRES_AT = "lease_expires_at";
    private static final String FIELD_PROJECT_ID = "project_id";
    private static final String FIELD_SCOPE_TYPE = "scope_type";
    private static final String FIELD_SCOPE_ID = "scope_id";
    private static final String FIELD_HOLDER_INSTANCE = "holder_instance";
    private static final String FIELD_ORPHANED_AT = "orphaned_at";
    private static final String FIELD_RUNTIME_PID = "runtime_pid";
    private static final String FIELD_RUNTIME_STARTED_AT = "runtime_started_at";

    private static final JsonFactory JSON = new JsonFactory();

    /**
     * Why a lease store document was refused.
     *
     * @since 0.1
     */
    public enum Refusal {
        /** The document does not parse as a lease store document of the current format, or has no format version. */
        UNREADABLE("unreadable"),
        /** The document declares an older format version than the current one. */
        FORMAT_OUTDATED("format_outdated"),
        /** The document declares a newer format version than the current one. */
        FORMAT_NEWER("format_newer");

        private final String code;

        Refusal(String code) {
            this.code = code;
        }

        /** @return the name of the refusal in a representation */
        public String code() {
            return code;
        }
    }

    /**
     * A lease store document that was refused. The document is left as it is: a reader never repairs or rewrites
     * what it refuses.
     *
     * @since 0.1
     */
    public static final class FormatException extends RuntimeException {

        @Serial
        private static final long serialVersionUID = 1L;

        private static final int NO_VERSION = Integer.MIN_VALUE;

        /** The refusal. */
        private final Refusal refusal;

        /** The format version the document declares, or {@link #NO_VERSION}. */
        private final int found;

        private FormatException(Refusal refusal, int found, String message, Throwable cause) {
            super(message, cause);
            this.refusal = refusal;
            this.found = found;
        }

        private static FormatException unreadable(String detail, Throwable cause) {
            return new FormatException(Refusal.UNREADABLE, NO_VERSION, "Lease store document unreadable: " + detail,
                    cause);
        }

        private static FormatException unreadable(String detail) {
            return unreadable(detail, null);
        }

        private static FormatException ofVersion(int found) {
            var refusal = found < FORMAT_VERSION ? Refusal.FORMAT_OUTDATED : Refusal.FORMAT_NEWER;
            return new FormatException(refusal, found, "Lease store document has format_version %d, current is %d"
                    .formatted(found, FORMAT_VERSION), null);
        }

        /** @return why the document was refused */
        public Refusal refusal() {
            return refusal;
        }

        /** @return the format version the document declares; empty if the document is unreadable */
        public OptionalInt found() {
            return found == NO_VERSION ? OptionalInt.empty() : OptionalInt.of(found);
        }

        /**
         * @param store the store the document was read from
         * @return this refusal with a message that names the store
         */
        public FormatException at(Path store) {
            return new FormatException(refusal, found, store + ": " + getMessage(), this);
        }
    }

    /**
     * @param leases the leases of a store, in the order they are written
     * @return the document, UTF-8 encoded
     * @throws IllegalArgumentException if two leases have the same key
     */
    public static byte[] write(Collection<LeaseRecord> leases) {
        Objects.requireNonNull(leases, "leases");
        var keys = new HashSet<String>();
        var out = new ByteArrayOutputStream();
        try (var generator = JSON.createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeArrayFieldStart(FIELD_LEASES);
            for (var lease : leases) {
                if (!keys.add(lease.key())) {
                    throw new IllegalArgumentException("Two leases with the key '%s'".formatted(lease.key()));
                }
                writeLease(generator, lease);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Lease store document cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param document a lease store document, UTF-8 encoded
     * @return the leases of the document, in its order
     * @throws FormatException if the document declares another format version or is unreadable
     */
    public static List<LeaseRecord> read(byte[] document) {
        Objects.requireNonNull(document, "document");
        try {
            requireCurrentVersion(document);
            return readDocument(document);
        } catch (IOException e) {
            throw FormatException.unreadable(describe(e), e);
        }
    }

    private static void writeLease(JsonGenerator generator, LeaseRecord lease) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_KEY, lease.key());
        generator.writeFieldName(FIELD_OWNER);
        writeOwner(generator, lease.owner());
        writeInstant(generator, FIELD_ACQUIRED_AT, lease.acquiredAt());
        writeInstant(generator, FIELD_LEASE_EXPIRES_AT, lease.leaseExpiresAt());
        generator.writeEndObject();
    }

    private static void writeOwner(JsonGenerator generator, LeaseOwner owner) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_PROJECT_ID, owner.projectId());
        generator.writeStringField(FIELD_SCOPE_TYPE, owner.scopeType().wireName());
        if (owner.scopeId() == null) {
            generator.writeNullField(FIELD_SCOPE_ID);
        } else {
            generator.writeStringField(FIELD_SCOPE_ID, owner.scopeId());
        }
        generator.writeObjectFieldStart(FIELD_HOLDER_INSTANCE);
        generator.writeNumberField(FIELD_RUNTIME_PID, owner.holderInstance().runtimePid());
        writeInstant(generator, FIELD_RUNTIME_STARTED_AT, owner.holderInstance().runtimeStartedAt());
        generator.writeEndObject();
        writeInstant(generator, FIELD_ORPHANED_AT, owner.orphanedAt());
        generator.writeEndObject();
    }

    private static void writeInstant(JsonGenerator generator, String field, Instant instant) throws IOException {
        if (instant == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, instant.toString());
        }
    }

    /**
     * Finds the format version before anything else is read, so a document in another version is refused as such
     * and not as unreadable for the fields that version changed.
     */
    private static void requireCurrentVersion(byte[] document) throws IOException {
        Integer found = null;
        try (var parser = JSON.createParser(document)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw FormatException.unreadable("not a JSON object");
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = parser.currentName();
                var token = parser.nextToken();
                if (!FIELD_FORMAT_VERSION.equals(field)) {
                    parser.skipChildren();
                } else if (found != null || token != JsonToken.VALUE_NUMBER_INT) {
                    throw FormatException.unreadable("'format_version' must be one integer");
                } else {
                    found = parser.getIntValue();
                }
            }
        }
        if (found == null) {
            throw FormatException.unreadable("'format_version' is missing");
        }
        if (found != FORMAT_VERSION) {
            throw FormatException.ofVersion(found);
        }
    }

    private static List<LeaseRecord> readDocument(byte[] document) throws IOException {
        try (var parser = JSON.createParser(document)) {
            parser.nextToken();
            List<LeaseRecord> leases = null;
            var seen = new HashSet<String>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_FORMAT_VERSION -> parser.skipChildren();
                    case FIELD_LEASES -> leases = readLeases(parser);
                    default -> throw undeclared(field);
                }
            }
            requireFields(seen, "document", FIELD_LEASES);
            if (parser.nextToken() != null) {
                throw FormatException.unreadable("content after the document");
            }
            return List.copyOf(leases);
        }
    }

    private static List<LeaseRecord> readLeases(JsonParser parser) throws IOException {
        if (parser.currentToken() != JsonToken.START_ARRAY) {
            throw FormatException.unreadable("'leases' must be an array");
        }
        var leases = new ArrayList<LeaseRecord>();
        var keys = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            var lease = readLease(parser);
            if (!keys.add(lease.key())) {
                throw FormatException.unreadable("two leases with the key '%s'".formatted(lease.key()));
            }
            leases.add(lease);
        }
        return leases;
    }

    private static LeaseRecord readLease(JsonParser parser) throws IOException {
        requireObject(parser, "lease");
        String key = null;
        LeaseOwner owner = null;
        Instant acquiredAt = null;
        Instant leaseExpiresAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_KEY -> key = string(parser, field);
                case FIELD_OWNER -> owner = readOwner(parser);
                case FIELD_ACQUIRED_AT -> acquiredAt = instant(parser, field);
                case FIELD_LEASE_EXPIRES_AT -> leaseExpiresAt = nullableInstant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "lease", FIELD_KEY, FIELD_OWNER, FIELD_ACQUIRED_AT, FIELD_LEASE_EXPIRES_AT);
        try {
            return new LeaseRecord(key, owner, acquiredAt, leaseExpiresAt);
        } catch (IllegalArgumentException e) {
            throw FormatException.unreadable(e.getMessage(), e);
        }
    }

    private static LeaseOwner readOwner(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_OWNER);
        String projectId = null;
        ScopeType scopeType = null;
        String scopeId = null;
        HolderInstance holder = null;
        Instant orphanedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_PROJECT_ID -> projectId = string(parser, field);
                case FIELD_SCOPE_TYPE -> scopeType = scopeType(string(parser, field));
                case FIELD_SCOPE_ID -> scopeId = nullableString(parser, field);
                case FIELD_HOLDER_INSTANCE -> holder = readHolder(parser);
                case FIELD_ORPHANED_AT -> orphanedAt = nullableInstant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_OWNER, FIELD_PROJECT_ID, FIELD_SCOPE_TYPE, FIELD_SCOPE_ID, FIELD_HOLDER_INSTANCE);
        if (scopeType.hasScopeId() && scopeId == null) {
            throw FormatException.unreadable("'scope_id' must be set for the scope type '%s'"
                    .formatted(scopeType.wireName()));
        }
        try {
            return new LeaseOwner(projectId, scopeType, scopeId, holder, orphanedAt);
        } catch (IllegalArgumentException e) {
            throw FormatException.unreadable(e.getMessage(), e);
        }
    }

    private static HolderInstance readHolder(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_HOLDER_INSTANCE);
        long runtimePid = 0;
        Instant runtimeStartedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_RUNTIME_PID -> runtimePid = integer(parser, field);
                case FIELD_RUNTIME_STARTED_AT -> runtimeStartedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, FIELD_HOLDER_INSTANCE, FIELD_RUNTIME_PID, FIELD_RUNTIME_STARTED_AT);
        try {
            return new HolderInstance(runtimePid, runtimeStartedAt);
        } catch (IllegalArgumentException e) {
            throw FormatException.unreadable(e.getMessage(), e);
        }
    }

    /** Moves to the value of the current field and refuses a field that the object already had. */
    private static String nextField(JsonParser parser, Set<String> seen) throws IOException {
        var field = parser.currentName();
        if (!seen.add(field)) {
            throw FormatException.unreadable("field '%s' twice".formatted(field));
        }
        parser.nextToken();
        return field;
    }

    private static void requireObject(JsonParser parser, String name) {
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw FormatException.unreadable("'%s' must be an object".formatted(name));
        }
    }

    private static void requireFields(Set<String> seen, String object, String... required) {
        for (var field : required) {
            if (!seen.contains(field)) {
                throw FormatException.unreadable("field '%s' of the %s is missing".formatted(field, object));
            }
        }
    }

    private static String string(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_STRING) {
            throw FormatException.unreadable("'%s' must be a string".formatted(field));
        }
        return parser.getText();
    }

    private static String nullableString(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : string(parser, field);
    }

    private static long integer(JsonParser parser, String field) throws IOException {
        if (parser.currentToken() != JsonToken.VALUE_NUMBER_INT) {
            throw FormatException.unreadable("'%s' must be an integer".formatted(field));
        }
        return parser.getLongValue();
    }

    private static Instant nullableInstant(JsonParser parser, String field) throws IOException {
        return parser.currentToken() == JsonToken.VALUE_NULL ? null : instant(parser, field);
    }

    /** Accepts the one spelling the writer produces, so reading and writing an instant never changes its bytes. */
    private static Instant instant(JsonParser parser, String field) throws IOException {
        var text = string(parser, field);
        try {
            var instant = Instant.parse(text);
            if (instant.toString().equals(text)) {
                return instant;
            }
        } catch (DateTimeException e) {
            throw FormatException.unreadable("'%s' is not an instant: %s".formatted(field, text), e);
        }
        throw FormatException.unreadable(
                "'%s' is not in the one shape of an instant (RFC 3339, UTC, suffix Z): %s".formatted(field, text));
    }

    private static ScopeType scopeType(String wireName) {
        return ScopeType.fromWireName(wireName).orElseThrow(
                () -> FormatException.unreadable("'scope_type' has the undeclared value '%s'".formatted(wireName)));
    }

    private static FormatException undeclared(String field) {
        return FormatException.unreadable("undeclared field '%s'".formatted(field));
    }

    /** Names where the document stops being JSON, without the source excerpt the parser may append. */
    private static String describe(IOException e) {
        if (e instanceof JsonProcessingException malformed && malformed.getLocation() != null) {
            return "%s (line %d, column %d)".formatted(malformed.getOriginalMessage(),
                    malformed.getLocation().getLineNr(), malformed.getLocation().getColumnNr());
        }
        return String.valueOf(e.getMessage());
    }
}
