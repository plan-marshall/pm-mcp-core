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

import org.jspecify.annotations.Nullable;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockTransaction;

/**
 * The intent file beside a store that owes the audit records of its mutations (PM-IMPL-7): the document
 * {@code {"format_version": 1, "owed_events": [<owed event>]}}, named {@code <store>.audit-intent}, that exists only
 * while a record is owed.
 * <p>
 * A record is owed in the intent, written durably by one atomic rename under the lock of the store, before the
 * mutation that makes its cause disappear, and the intent is removed only after the sink accepted every record it
 * owes. A sink that refuses a record, or a stop before the hand-over, leaves the intent, and a later hand-over delivers
 * the records again with their ids: delivery is at least once. The file is read and written only by a caller that
 * holds the lock of the store; this type does not check it again.
 * <p>
 * The JSONL stores ({@link JsonlStoreFile}) and the enrolment store ({@link EnrolmentStore}) keep their owed records
 * here; an owed record is the object {@link OwedAuditEvents} reads and writes.
 *
 * @param file    the intent file; kept absolute and normalized
 * @param lockKey the key of the lock that guards the store and its intent
 * @since 0.1
 */
record AuditIntentFile(Path file, LockKey lockKey) {

    /** The suffix that names the intent file beside its store. */
    static final String SUFFIX = ".audit-intent";

    private static final int FORMAT_VERSION = 1;
    private static final String FIELD_OWED_EVENTS = "owed_events";

    /**
     * Makes the intent path absolute and normalized.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    AuditIntentFile {
        file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        Objects.requireNonNull(lockKey, "lockKey");
    }

    /**
     * @param store   the store whose mutations owe their records
     * @param lockKey the key of the lock that guards the store
     * @return the intent file beside the store, {@code <store>.audit-intent}
     */
    static AuditIntentFile beside(Path store, LockKey lockKey) {
        Objects.requireNonNull(store, "store");
        return new AuditIntentFile(store.resolveSibling(store.getFileName() + SUFFIX), lockKey);
    }

    /**
     * @return the owed records, in their order; empty if the intent file does not exist
     * @throws StoreRefusal         if the intent file is unreadable or of another format version, naming it
     * @throws UncheckedIOException if the intent file exists and cannot be read
     */
    List<AuditEvent> read() {
        return StoreSnapshot.read(file).content()
                .map(document -> StrictJson.read(file, document, FORMAT_VERSION, AuditIntentFile::readDocument))
                .orElseGet(List::of);
    }

    /**
     * Writes the owed records durably, replacing the intent by one atomic rename.
     *
     * @param transaction a transaction that holds the key of the store
     * @param owed        the owed records, in their order
     * @throws IllegalArgumentException if a detail value of a record is not a string, an integer or a list of strings
     * @throws UncheckedIOException     if the intent cannot be written
     */
    void write(LockTransaction transaction, List<AuditEvent> owed) {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(StrictJson.FIELD_FORMAT_VERSION, FORMAT_VERSION);
            generator.writeArrayFieldStart(FIELD_OWED_EVENTS);
            for (var event : owed) {
                OwedAuditEvents.write(generator, event);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Audit intent cannot be written: " + file, e);
        }
        new AtomicStoreFile(file, lockKey).write(transaction, out.toByteArray());
    }

    /**
     * Hands the owed records over in their order and stops at the first the sink refuses: once every record was
     * handed over the intent file is deleted and its directory forced to disk; after a part of them it is rewritten
     * with the rest; if none was, it is left as it is.
     *
     * @param transaction a transaction that holds the key of the store
     * @param owed        the owed records the intent file holds, in their order
     * @param sink        the audit sink
     * @return the records that were handed over, in their order; empty if none was or nothing was owed
     * @throws IOException          if the intent file cannot be deleted or its directory not forced to disk
     * @throws UncheckedIOException if the rest cannot be written
     */
    List<AuditEvent> handOver(LockTransaction transaction, List<AuditEvent> owed, AuditSink sink)
            throws IOException {
        if (owed.isEmpty()) {
            return List.of();
        }
        var remaining = OwedAuditEvents.deliver(owed, sink, file);
        if (remaining.isEmpty()) {
            Files.delete(file);
            SyncedFiles.forceDirectory(file.getParent());
        } else if (remaining.size() < owed.size()) {
            write(transaction, remaining);
        }
        return List.copyOf(owed.subList(0, owed.size() - remaining.size()));
    }

    private static List<AuditEvent> readDocument(JsonParser parser) throws IOException {
        @Nullable List<AuditEvent> owed = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = StrictJson.nextField(parser, seen);
            switch (field) {
                case StrictJson.FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_OWED_EVENTS -> owed = readOwedEvents(parser);
                default -> throw StrictJson.undeclared(field);
            }
        }
        StrictJson.requireFields(seen, "audit intent", FIELD_OWED_EVENTS);
        return Objects.requireNonNull(owed, FIELD_OWED_EVENTS);
    }

    private static List<AuditEvent> readOwedEvents(JsonParser parser) throws IOException {
        StrictJson.requireArray(parser, FIELD_OWED_EVENTS);
        var owed = new ArrayList<AuditEvent>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            owed.add(OwedAuditEvents.read(parser));
        }
        return List.copyOf(owed);
    }
}
