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
import static de.planmarshall.core.store.StrictJson.requireArray;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * The transaction journal of a scope (PM-IMPL-7, PM-IMPL-2): the document that a transaction writes and syncs before
 * it touches a store, and from which a recovery rolls an interrupted transaction forward.
 * <p>
 * The document is
 * {@code {"format_version": 1, "transaction_id", "scope_type", "scope_id", "created_at", "targets": [<target>]}}, a
 * target {@code {"path", "mode", "staged_path", "size_before", "new_size", "new_sha256"}}. The paths are relative to
 * the path base of the scope ({@link JournalScope}). A journal lists at least one target, and an {@code append}
 * target always carries the size of its store before the append: it is the point a recovery cuts the store back to
 * before it appends the staged block again, so a torn append is never applied twice.
 * <p>
 * The reader accepts exactly one shape through {@link StrictJson}; every refusal is a {@link StoreRefusal} naming the
 * journal file.
 *
 * @param transactionId the transaction the journal records
 * @param scopeType     the type of the scope
 * @param scopeId       the plan or epic id; {@code null} for the workspace and the lesson store
 * @param createdAt     the instant the journal was written
 * @param targets       the stores the transaction writes, in the order it applies them
 * @since 0.1
 */
public record TransactionJournal(TransactionId transactionId, JournalScopeType scopeType, String scopeId,
        Instant createdAt, List<Target> targets) {

    /** The format version of a transaction journal that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final String FIELD_TRANSACTION_ID = "transaction_id";
    private static final String FIELD_SCOPE_TYPE = "scope_type";
    private static final String FIELD_SCOPE_ID = "scope_id";
    private static final String FIELD_CREATED_AT = "created_at";
    private static final String FIELD_TARGETS = "targets";
    private static final String FIELD_PATH = "path";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_STAGED_PATH = "staged_path";
    private static final String FIELD_SIZE_BEFORE = "size_before";
    private static final String FIELD_NEW_SIZE = "new_size";
    private static final String FIELD_NEW_SHA256 = "new_sha256";

    /**
     * How a target store is written.
     *
     * @since 0.1
     */
    public enum Mode {
        /** The store is replaced by its staged file in one atomic rename. */
        REPLACE("replace"),
        /** The staged block is appended to the JSONL store. */
        APPEND("append");

        private final String wireName;

        Mode(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the mode in a journal */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * One store a transaction writes.
     *
     * @param path       the store, relative to the path base of the scope
     * @param mode       how the store is written
     * @param stagedPath the staged file, relative to the path base of the scope
     * @param sizeBefore the size of the store before the append; present for an append, optional for a replace
     * @param newDigest  the size and SHA-256 of the store once the target is applied
     * @since 0.1
     */
    public record Target(String path, Mode mode, String stagedPath, OptionalLong sizeBefore, StoreDigest newDigest) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if a path is empty, if an append target has no size before, or if a size
         *                                  before is negative
         */
        public Target {
            requirePath(path, FIELD_PATH);
            Objects.requireNonNull(mode, FIELD_MODE);
            requirePath(stagedPath, FIELD_STAGED_PATH);
            Objects.requireNonNull(sizeBefore, "sizeBefore");
            Objects.requireNonNull(newDigest, "newDigest");
            if (mode == Mode.APPEND && sizeBefore.isEmpty()) {
                throw new IllegalArgumentException("The append target '%s' has no size before".formatted(path));
            }
            if (sizeBefore.isPresent() && sizeBefore.getAsLong() < 0) {
                throw new IllegalArgumentException(
                        "The size before of the target '%s' is negative: %d".formatted(path, sizeBefore.getAsLong()));
            }
        }

        private static void requirePath(String path, String name) {
            Objects.requireNonNull(path, name);
            if (path.isEmpty()) {
                throw new IllegalArgumentException("'%s' of a target is empty".formatted(name));
            }
        }
    }

    /**
     * @throws NullPointerException     if a component other than the scope id is {@code null}
     * @throws IllegalArgumentException if the scope id is missing for a type that requires one or present for a type
     *                                  that has none, or if there is no target
     */
    public TransactionJournal {
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(scopeType, "scopeType");
        Objects.requireNonNull(createdAt, "createdAt");
        targets = List.copyOf(targets);
        if (scopeType.scopeIdRequired() != (scopeId != null)) {
            throw new IllegalArgumentException("A %s journal %s a scope id: %s".formatted(scopeType.wireName(),
                    scopeType.scopeIdRequired() ? "requires" : "has no", scopeId));
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("A journal lists at least one target");
        }
    }

    /** @return the document, UTF-8 encoded */
    public byte[] write() {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeStringField(FIELD_TRANSACTION_ID, transactionId.value());
            generator.writeStringField(FIELD_SCOPE_TYPE, scopeType.wireName());
            if (scopeId == null) {
                generator.writeNullField(FIELD_SCOPE_ID);
            } else {
                generator.writeStringField(FIELD_SCOPE_ID, scopeId);
            }
            writeInstant(generator, FIELD_CREATED_AT, createdAt);
            generator.writeArrayFieldStart(FIELD_TARGETS);
            for (var target : targets) {
                writeTarget(generator, target);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Transaction journal cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the journal file the document was read from, named by a refusal
     * @param document a journal document, UTF-8 encoded
     * @return the journal
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static TransactionJournal read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, "document");
        return StrictJson.read(file, document, FORMAT_VERSION, TransactionJournal::readDocument);
    }

    private static void writeTarget(JsonGenerator generator, Target target) throws IOException {
        generator.writeStartObject();
        generator.writeStringField(FIELD_PATH, target.path());
        generator.writeStringField(FIELD_MODE, target.mode().wireName());
        generator.writeStringField(FIELD_STAGED_PATH, target.stagedPath());
        if (target.sizeBefore().isPresent()) {
            generator.writeNumberField(FIELD_SIZE_BEFORE, target.sizeBefore().getAsLong());
        } else {
            generator.writeNullField(FIELD_SIZE_BEFORE);
        }
        generator.writeNumberField(FIELD_NEW_SIZE, target.newDigest().size());
        generator.writeStringField(FIELD_NEW_SHA256, target.newDigest().sha256());
        generator.writeEndObject();
    }

    private static TransactionJournal readDocument(JsonParser parser) throws IOException {
        TransactionId transactionId = null;
        JournalScopeType scopeType = null;
        String scopeId = null;
        Instant createdAt = null;
        List<Target> targets = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_TRANSACTION_ID -> transactionId = transactionId(string(parser, field));
                case FIELD_SCOPE_TYPE -> scopeType = scopeType(string(parser, field));
                case FIELD_SCOPE_ID -> scopeId = nullableString(parser, field);
                case FIELD_CREATED_AT -> createdAt = instant(parser, field);
                case FIELD_TARGETS -> targets = readTargets(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "journal", FIELD_TRANSACTION_ID, FIELD_SCOPE_TYPE, FIELD_SCOPE_ID, FIELD_CREATED_AT,
                FIELD_TARGETS);
        try {
            return new TransactionJournal(transactionId, scopeType, scopeId, createdAt, targets);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static List<Target> readTargets(JsonParser parser) throws IOException {
        requireArray(parser, FIELD_TARGETS);
        var targets = new ArrayList<Target>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            targets.add(readTarget(parser));
        }
        return targets;
    }

    private static Target readTarget(JsonParser parser) throws IOException {
        requireObject(parser, "target");
        String path = null;
        Mode mode = null;
        String stagedPath = null;
        var sizeBefore = OptionalLong.empty();
        long newSize = 0;
        String newSha256 = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_PATH -> path = string(parser, field);
                case FIELD_MODE -> mode = mode(string(parser, field));
                case FIELD_STAGED_PATH -> stagedPath = string(parser, field);
                case FIELD_SIZE_BEFORE -> sizeBefore = parser.currentToken() == JsonToken.VALUE_NULL
                        ? OptionalLong.empty()
                        : OptionalLong.of(integer(parser, field));
                case FIELD_NEW_SIZE -> newSize = integer(parser, field);
                case FIELD_NEW_SHA256 -> newSha256 = string(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "target", FIELD_PATH, FIELD_MODE, FIELD_STAGED_PATH, FIELD_NEW_SIZE, FIELD_NEW_SHA256);
        try {
            return new Target(path, mode, stagedPath, sizeBefore, new StoreDigest(newSize, newSha256));
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static TransactionId transactionId(String value) {
        try {
            return new TransactionId(value);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static JournalScopeType scopeType(String wireName) {
        return JournalScopeType.ofWireName(wireName).orElseThrow(
                () -> unreadable("'scope_type' has the undeclared value '%s'".formatted(wireName)));
    }

    private static Mode mode(String wireName) {
        return Arrays.stream(Mode.values()).filter(mode -> mode.wireName().equals(wireName)).findFirst()
                .orElseThrow(() -> unreadable("'mode' has the undeclared value '%s'".formatted(wireName)));
    }
}
