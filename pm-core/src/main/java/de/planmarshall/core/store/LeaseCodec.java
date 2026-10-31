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
import static de.planmarshall.core.store.StrictJson.nullableInstant;
import static de.planmarshall.core.store.StrictJson.nullableString;
import static de.planmarshall.core.store.StrictJson.requireArray;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import lombok.experimental.UtilityClass;

/**
 * Reads and writes the document of a lease store (PM-IMPL-7, PM-IMPL-2):
 * {@code {"format_version": 1, "leases": [<lease>]}}, where a lease is its {@code key}, its {@code owner},
 * {@code acquired_at} and {@code lease_expires_at}.
 * <p>
 * The reader accepts exactly one shape and never tolerates another: exactly the current {@code format_version}, only
 * the declared fields, each at most once, and every required field. A document in another format version is refused
 * as outdated or newer and is never rewritten; anything else that does not parse is unreadable, never an empty
 * store. Every refusal is a {@link StoreRefusal} that names the store file.
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

    /**
     * @param leases the leases of a store, in the order they are written
     * @return the document, UTF-8 encoded
     * @throws IllegalArgumentException if two leases have the same key
     */
    public static byte[] write(Collection<LeaseRecord> leases) {
        Objects.requireNonNull(leases, FIELD_LEASES);
        var keys = new HashSet<String>();
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
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
     * @param file     the store file the document was read from, named by a refusal
     * @param document a lease store document, UTF-8 encoded
     * @return the leases of the document, in its order
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static List<LeaseRecord> read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, LeaseCodec::readDocument);
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

    private static List<LeaseRecord> readDocument(JsonParser parser) throws IOException {
        @Nullable List<LeaseRecord> leases = null;
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
        return List.copyOf(leases);
    }

    private static List<LeaseRecord> readLeases(JsonParser parser) throws IOException {
        requireArray(parser, FIELD_LEASES);
        var leases = new ArrayList<LeaseRecord>();
        var keys = new HashSet<String>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            var lease = readLease(parser);
            if (!keys.add(lease.key())) {
                throw unreadable("two leases with the key '%s'".formatted(lease.key()));
            }
            leases.add(lease);
        }
        return leases;
    }

    private static LeaseRecord readLease(JsonParser parser) throws IOException {
        requireObject(parser, "lease");
        @Nullable String key = null;
        @Nullable LeaseOwner owner = null;
        @Nullable Instant acquiredAt = null;
        @Nullable Instant leaseExpiresAt = null;
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
            throw unreadable(e.getMessage(), e);
        }
    }

    static LeaseOwner readOwner(JsonParser parser) throws IOException {
        return readOwner(parser, false);
    }

    static LeaseOwner readOwner(JsonParser parser, boolean requireOrphanedAt) throws IOException {
        requireObject(parser, FIELD_OWNER);
        @Nullable String projectId = null;
        @Nullable ScopeType scopeType = null;
        @Nullable String scopeId = null;
        @Nullable HolderInstance holder = null;
        @Nullable Instant orphanedAt = null;
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
        if (requireOrphanedAt) {
            requireFields(seen, FIELD_OWNER, FIELD_PROJECT_ID, FIELD_SCOPE_TYPE, FIELD_SCOPE_ID, FIELD_HOLDER_INSTANCE,
                    FIELD_ORPHANED_AT);
        } else {
            requireFields(seen, FIELD_OWNER, FIELD_PROJECT_ID, FIELD_SCOPE_TYPE, FIELD_SCOPE_ID,
                    FIELD_HOLDER_INSTANCE);
        }
        if (scopeType.hasScopeId() && scopeId == null) {
            throw unreadable("'scope_id' must be set for the scope type '%s'".formatted(scopeType.wireName()));
        }
        try {
            return new LeaseOwner(projectId, scopeType, scopeId, holder, orphanedAt);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    static HolderInstance readHolder(JsonParser parser) throws IOException {
        requireObject(parser, FIELD_HOLDER_INSTANCE);
        long runtimePid = 0;
        @Nullable Instant runtimeStartedAt = null;
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
            throw unreadable(e.getMessage(), e);
        }
    }

    private static ScopeType scopeType(String wireName) {
        return ScopeType.fromWireName(wireName).orElseThrow(
                () -> unreadable("'scope_type' has the undeclared value '%s'".formatted(wireName)));
    }
}
