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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.ModelSlotStore.Document;
import de.planmarshall.core.store.ModelSlotStore.Form;
import de.planmarshall.core.store.ModelSlotStore.Lease;
import de.planmarshall.core.store.ModelSlotStore.Waiting;
import de.planmarshall.core.store.ModelSlotStore.WorkerClass;

/**
 * Reads and writes the document of the model-slot store (PM-IMPL-7, PM-IMPL-2):
 * {@code {"format_version": 1, "slots": [<slot>], "waiting": [<waiting>]}}.
 * <p>
 * A slot is its {@code owner}, {@code job_id}, {@code role}, {@code class}, {@code form}, {@code plan_id},
 * {@code acquired_at} and {@code lease_expires_at}; a waiting worker is its {@code owner}, {@code job_id},
 * {@code role}, {@code class}, {@code form}, {@code plan_id} and {@code enqueued_at}. The reader accepts exactly one shape and never
 * tolerates another: exactly the current {@code format_version}, only the declared fields, each at most once, and every
 * required field. Anything else that does not parse is unreadable, never an empty store, and every refusal is a
 * {@link StoreRefusal} that names the store file.
 * <p>
 * The order of both arrays is the order of the store and is kept: it is the FIFO of the waiting workers.
 *
 * @since 0.1
 */
@UtilityClass
public class ModelSlotCodec {

    private static final String FIELD_SLOTS = "slots";
    private static final String PARAMETER_DOCUMENT = "document";
    private static final String FIELD_WAITING = "waiting";
    private static final String FIELD_OWNER = "owner";
    private static final String FIELD_JOB_ID = "job_id";
    private static final String FIELD_ROLE = "role";
    private static final String FIELD_CLASS = "class";
    private static final String FIELD_FORM = "form";
    private static final String FIELD_PLAN_ID = "plan_id";
    private static final String FIELD_ACQUIRED_AT = "acquired_at";
    private static final String FIELD_LEASE_EXPIRES_AT = "lease_expires_at";
    private static final String FIELD_ENQUEUED_AT = "enqueued_at";
    private static final String FIELD_PROJECT_ID = "project_id";
    private static final String FIELD_SCOPE_TYPE = "scope_type";
    private static final String FIELD_SCOPE_ID = "scope_id";
    private static final String FIELD_HOLDER_INSTANCE = "holder_instance";
    private static final String FIELD_ORPHANED_AT = "orphaned_at";
    private static final String FIELD_RUNTIME_PID = "runtime_pid";
    private static final String FIELD_RUNTIME_STARTED_AT = "runtime_started_at";

    /**
     * @param document the document to write
     * @return the document, UTF-8 encoded
     * @throws IllegalArgumentException if a job holds a slot or waits twice, or a plan holds or awaits two writers
     */
    public static byte[] write(Document document) {
        Objects.requireNonNull(document, PARAMETER_DOCUMENT);
        requireNoDuplicateWorker(document);
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, ModelSlotStore.FORMAT_VERSION);
            generator.writeArrayFieldStart(FIELD_SLOTS);
            for (var lease : document.slots()) {
                writeSlot(generator, lease);
            }
            generator.writeEndArray();
            generator.writeArrayFieldStart(FIELD_WAITING);
            for (var waiting : document.waiting()) {
                writeWaiting(generator, waiting);
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Model-slot store document cannot be written", e);
        }
        return out.toByteArray();
    }

    /**
     * @param file     the store file the document was read from, named by a refusal
     * @param document a model-slot store document, UTF-8 encoded
     * @return the document
     * @throws StoreRefusal if the document declares another format version or is unreadable
     */
    public static Document read(Path file, byte[] document) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(document, PARAMETER_DOCUMENT);
        return StrictJson.read(file, document, ModelSlotStore.FORMAT_VERSION, ModelSlotCodec::readDocument);
    }

    private static void writeSlot(JsonGenerator generator, Lease lease) throws IOException {
        generator.writeStartObject();
        writeOwner(generator, lease.owner());
        generator.writeStringField(FIELD_JOB_ID, lease.jobId());
        generator.writeStringField(FIELD_ROLE, lease.role());
        generator.writeStringField(FIELD_CLASS, lease.workerClass().wireName());
        generator.writeStringField(FIELD_FORM, lease.form().wireName());
        writeNullableString(generator, FIELD_PLAN_ID, lease.planId());
        writeInstant(generator, FIELD_ACQUIRED_AT, lease.acquiredAt());
        writeInstant(generator, FIELD_LEASE_EXPIRES_AT, lease.leaseExpiresAt());
        generator.writeEndObject();
    }

    private static void writeWaiting(JsonGenerator generator, Waiting waiting) throws IOException {
        generator.writeStartObject();
        writeOwner(generator, waiting.owner());
        generator.writeStringField(FIELD_JOB_ID, waiting.jobId());
        generator.writeStringField(FIELD_ROLE, waiting.role());
        generator.writeStringField(FIELD_CLASS, waiting.workerClass().wireName());
        generator.writeStringField(FIELD_FORM, waiting.form().wireName());
        writeNullableString(generator, FIELD_PLAN_ID, waiting.planId());
        writeInstant(generator, FIELD_ENQUEUED_AT, waiting.enqueuedAt());
        generator.writeEndObject();
    }

    private static void writeOwner(JsonGenerator generator, LeaseOwner owner) throws IOException {
        generator.writeObjectFieldStart(FIELD_OWNER);
        generator.writeStringField(FIELD_PROJECT_ID, owner.projectId());
        generator.writeStringField(FIELD_SCOPE_TYPE, owner.scopeType().wireName());
        writeNullableString(generator, FIELD_SCOPE_ID, owner.scopeId());
        generator.writeObjectFieldStart(FIELD_HOLDER_INSTANCE);
        generator.writeNumberField(FIELD_RUNTIME_PID, owner.holderInstance().runtimePid());
        writeInstant(generator, FIELD_RUNTIME_STARTED_AT, owner.holderInstance().runtimeStartedAt());
        generator.writeEndObject();
        writeInstant(generator, FIELD_ORPHANED_AT, owner.orphanedAt());
        generator.writeEndObject();
    }

    private static void writeNullableString(JsonGenerator generator, String field, @Nullable String value) throws IOException {
        if (value == null) {
            generator.writeNullField(field);
        } else {
            generator.writeStringField(field, value);
        }
    }

    private static Document readDocument(JsonParser parser) throws IOException {
        @Nullable List<Lease> slots = null;
        @Nullable List<Waiting> waiting = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_FORMAT_VERSION -> parser.skipChildren();
                case FIELD_SLOTS -> slots = readSlots(parser);
                case FIELD_WAITING -> waiting = readWaiting(parser);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, PARAMETER_DOCUMENT, FIELD_SLOTS, FIELD_WAITING);
        try {
            var document = new Document(slots, waiting);
            requireNoDuplicateWorker(document);
            return document;
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static List<Lease> readSlots(JsonParser parser) throws IOException {
        requireArray(parser, FIELD_SLOTS);
        var slots = new ArrayList<Lease>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            slots.add(readSlot(parser));
        }
        return slots;
    }

    private static List<Waiting> readWaiting(JsonParser parser) throws IOException {
        requireArray(parser, FIELD_WAITING);
        var waiting = new ArrayList<Waiting>();
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            waiting.add(readWaitingEntry(parser));
        }
        return waiting;
    }

    private static Lease readSlot(JsonParser parser) throws IOException {
        requireObject(parser, "slot");
        @Nullable LeaseOwner owner = null;
        @Nullable String jobId = null;
        @Nullable String role = null;
        @Nullable WorkerClass workerClass = null;
        @Nullable Form form = null;
        @Nullable String planId = null;
        @Nullable Instant acquiredAt = null;
        @Nullable Instant leaseExpiresAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_OWNER -> owner = LeaseCodec.readOwner(parser, true);
                case FIELD_JOB_ID -> jobId = string(parser, field);
                case FIELD_ROLE -> role = string(parser, field);
                case FIELD_CLASS -> workerClass = workerClass(parser, field);
                case FIELD_FORM -> form = form(parser, field);
                case FIELD_PLAN_ID -> planId = nullableString(parser, field);
                case FIELD_ACQUIRED_AT -> acquiredAt = instant(parser, field);
                case FIELD_LEASE_EXPIRES_AT -> leaseExpiresAt = nullableInstant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "slot", FIELD_OWNER, FIELD_JOB_ID, FIELD_ROLE, FIELD_CLASS, FIELD_FORM, FIELD_PLAN_ID,
                FIELD_ACQUIRED_AT, FIELD_LEASE_EXPIRES_AT);
        requireWriterPlan(workerClass, planId);
        try {
            return new Lease(owner, jobId, role, workerClass, form, planId, acquiredAt, leaseExpiresAt);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    private static Waiting readWaitingEntry(JsonParser parser) throws IOException {
        requireObject(parser, "waiting worker");
        @Nullable LeaseOwner owner = null;
        @Nullable String jobId = null;
        @Nullable String role = null;
        @Nullable WorkerClass workerClass = null;
        @Nullable Form form = null;
        @Nullable String planId = null;
        @Nullable Instant enqueuedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_OWNER -> owner = LeaseCodec.readOwner(parser, true);
                case FIELD_JOB_ID -> jobId = string(parser, field);
                case FIELD_ROLE -> role = string(parser, field);
                case FIELD_CLASS -> workerClass = workerClass(parser, field);
                case FIELD_FORM -> form = form(parser, field);
                case FIELD_PLAN_ID -> planId = nullableString(parser, field);
                case FIELD_ENQUEUED_AT -> enqueuedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, "waiting worker", FIELD_OWNER, FIELD_JOB_ID, FIELD_ROLE, FIELD_CLASS, FIELD_FORM,
                FIELD_PLAN_ID, FIELD_ENQUEUED_AT);
        requireWriterPlan(workerClass, planId);
        try {
            return new Waiting(owner, jobId, role, workerClass, form, planId, enqueuedAt);
        } catch (IllegalArgumentException e) {
            throw unreadable(e.getMessage(), e);
        }
    }

    /** A writer serves a plan; a stored writer without one is refused as unreadable rather than as an NPE. */
    private static void requireWriterPlan(WorkerClass workerClass, String planId) {
        if (workerClass == WorkerClass.WRITER && planId == null) {
            throw unreadable("'plan_id' must be set for a writer worker");
        }
    }

    private static WorkerClass workerClass(JsonParser parser, String field) throws IOException {
        var name = string(parser, field);
        return WorkerClass.fromWireName(name).orElseThrow(
                () -> unreadable("'%s' has the undeclared value '%s'".formatted(field, name)));
    }

    private static Form form(JsonParser parser, String field) throws IOException {
        var name = string(parser, field);
        return Form.fromWireName(name)
                .orElseThrow(() -> unreadable("'%s' has the undeclared value '%s'".formatted(field, name)));
    }

    private static ScopeType scopeType(String wireName) {
        return ScopeType.fromWireName(wireName).orElseThrow(
                () -> unreadable("'scope_type' has the undeclared value '%s'".formatted(wireName)));
    }

    private static void requireNoDuplicateWorker(Document document) {
        var jobs = new HashSet<String>();
        for (var slot : document.slots()) {
            if (!jobs.add(slot.jobId())) {
                throw new IllegalArgumentException("The worker '%s' holds two slots".formatted(slot.jobId()));
            }
        }
        for (var waiting : document.waiting()) {
            if (!jobs.add(waiting.jobId())) {
                throw new IllegalArgumentException(
                        "The worker '%s' holds a slot or waits twice".formatted(waiting.jobId()));
            }
        }
        requireOneWriterPerPlan(document);
    }

    private static void requireOneWriterPerPlan(Document document) {
        var writers = new HashSet<String>();
        for (var slot : document.slots()) {
            addWriter(writers, slot.workerClass(), slot.owner().projectId(), slot.planId());
        }
        for (var waiting : document.waiting()) {
            addWriter(writers, waiting.workerClass(), waiting.owner().projectId(), waiting.planId());
        }
    }

    private static void addWriter(Set<String> writers, WorkerClass workerClass, String projectId, String planId) {
        if (workerClass == WorkerClass.WRITER && !writers.add(projectId + '\u0000' + planId)) {
            throw new IllegalArgumentException(
                    "The plan '%s' of the project '%s' holds or awaits more than one writer worker"
                            .formatted(planId, projectId));
        }
    }
}
