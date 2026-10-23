/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static de.planmarshall.core.store.StrictJson.FIELD_FORMAT_VERSION;
import static de.planmarshall.core.store.StrictJson.array;
import static de.planmarshall.core.store.StrictJson.instant;
import static de.planmarshall.core.store.StrictJson.integer;
import static de.planmarshall.core.store.StrictJson.nextField;
import static de.planmarshall.core.store.StrictJson.requireFields;
import static de.planmarshall.core.store.StrictJson.requireObject;
import static de.planmarshall.core.store.StrictJson.string;
import static de.planmarshall.core.store.StrictJson.undeclared;
import static de.planmarshall.core.store.StrictJson.unreadable;
import static de.planmarshall.core.store.StrictJson.writeInstant;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;
import de.planmarshall.core.store.StrictJson;

/**
 * The generations of a queue that were fenced (Model Work § Fencing): {@code queue/fences.json}, written under the
 * same queue lock as the records.
 * <p>
 * A generation is fenced when the supervisor ends its worker and, for every generation of a dead runtime instance, in
 * the project's recovery. The fence is persisted beside the queue so that a late call of a fenced generation is
 * refused after a restart, before the lease and every link check, and so that the fence and the release of the
 * generation's lease are written in the one transaction on the queue lock: the task protocol passes its transaction to
 * {@link #fence(LockTransaction, String, Instant)}.
 *
 * @since 0.1
 */
public final class FencedGenerations {

    /** The name of the fence document in the queue directory. */
    public static final String FILE_NAME = "fences.json";

    private static final String DOCUMENT = "generation fences";
    private static final String FIELD_FENCED = "fenced";
    private static final String FIELD_GENERATION_ID = "generation_id";
    private static final String FIELD_FENCED_AT = "fenced_at";

    private final Path file;
    private final LockKey lockKey;
    private final LockManager lockManager;

    /**
     * @param queueDirectory the queue directory
     * @param lockKey        the key of the queue lock
     * @param lockManager    the lock manager of the runtime
     */
    public FencedGenerations(Path queueDirectory, LockKey lockKey, LockManager lockManager) {
        this.file = Objects.requireNonNull(queueDirectory, "queueDirectory").toAbsolutePath().normalize()
                .resolve(FILE_NAME);
        this.lockKey = Objects.requireNonNull(lockKey, "lockKey");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
    }

    /** @return the fence document, absolute and normalized */
    public Path file() {
        return file;
    }

    /**
     * Reads the fences without a lock.
     *
     * @return the fenced generation ids in id order; empty if the document does not exist
     * @throws StoreRefusal         if the document is in another format version or unreadable
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public Set<String> fenced() {
        var fenced = new TreeSet<String>();
        StoreSnapshot.read(file).content().map(document -> decode(file, document)).orElseGet(List::of)
                .forEach(fence -> fenced.add(fence.generationId()));
        return fenced;
    }

    /**
     * Reads the fences without a lock, with the instant of each.
     *
     * @return the fences in id order; empty if the document does not exist
     * @throws StoreRefusal         if the document is in another format version or unreadable
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public List<Fence> entries() {
        return StoreSnapshot.read(file).content().map(document -> decode(file, document)).orElseGet(List::of);
    }

    /**
     * @param generationId a generation
     * @return whether the generation was fenced
     */
    public boolean isFenced(String generationId) {
        Objects.requireNonNull(generationId, "generationId");
        return fenced().contains(generationId);
    }

    /**
     * Fences a generation in one transaction on the queue lock. A generation that is fenced already keeps the instant
     * it was first fenced.
     *
     * @param generationId the generation
     * @param at           the instant the generation was fenced
     * @throws UncheckedIOException if the document cannot be read or written
     */
    public void fence(String generationId, Instant at) {
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(at, "at");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(lockKey);
            write(transaction, add(read(transaction), generationId, at));
        }
    }

    /**
     * Fences a generation inside the caller's transaction, which holds the queue lock: the transaction that releases
     * the generation's lease writes the fence in the same step.
     *
     * @param transaction  the caller's transaction
     * @param generationId the generation
     * @param at           the instant the generation was fenced
     * @throws de.planmarshall.core.service.InternalFaultException if the transaction does not hold the queue lock
     * @throws UncheckedIOException if the document cannot be read or written
     */
    public void fence(LockTransaction transaction, String generationId, Instant at) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(at, "at");
        write(transaction, add(read(transaction), generationId, at));
    }

    private List<Fence> read(LockTransaction transaction) {
        return new AtomicStoreFile(file, lockKey).read(transaction).map(document -> decode(file, document))
                .orElseGet(List::of);
    }

    private void write(LockTransaction transaction, List<Fence> fences) {
        try {
            Files.createDirectories(file.getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Queue directory cannot be created: " + file.getParent(), e);
        }
        new AtomicStoreFile(file, lockKey).write(transaction, encode(fences));
    }

    private static List<Fence> add(List<Fence> fences, String generationId, Instant at) {
        if (fences.stream().anyMatch(fence -> fence.generationId().equals(generationId))) {
            return fences;
        }
        var added = new ArrayList<>(fences);
        added.add(new Fence(generationId, at));
        added.sort((left, right) -> left.generationId().compareTo(right.generationId()));
        return added;
    }

    private static byte[] encode(List<Fence> fences) {
        var out = new ByteArrayOutputStream();
        try (var generator = StrictJson.factory().createGenerator(out, JsonEncoding.UTF8)) {
            generator.writeStartObject();
            generator.writeNumberField(FIELD_FORMAT_VERSION, QueueTaskRecord.FORMAT_VERSION);
            generator.writeArrayFieldStart(FIELD_FENCED);
            for (var fence : fences) {
                generator.writeStartObject();
                generator.writeStringField(FIELD_GENERATION_ID, fence.generationId());
                writeInstant(generator, FIELD_FENCED_AT, fence.fencedAt());
                generator.writeEndObject();
            }
            generator.writeEndArray();
            generator.writeEndObject();
        } catch (IOException e) {
            throw new UncheckedIOException("Generation fences cannot be written", e);
        }
        return out.toByteArray();
    }

    private static List<Fence> decode(Path file, byte[] document) {
        return StrictJson.read(file, document, QueueTaskRecord.FORMAT_VERSION, parser -> {
            requireObject(parser, DOCUMENT);
            List<Fence> fences = null;
            var seen = new HashSet<String>();
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                var field = nextField(parser, seen);
                switch (field) {
                    case FIELD_FORMAT_VERSION -> integer(parser, field);
                    case FIELD_FENCED -> fences = array(parser, field, (element, name) -> readFence(element, name,
                            file));
                    default -> throw undeclared(field);
                }
            }
            requireFields(seen, DOCUMENT, FIELD_FORMAT_VERSION, FIELD_FENCED);
            return List.copyOf(Objects.requireNonNull(fences, FIELD_FENCED));
        });
    }

    private static Fence readFence(JsonParser parser, String name, Path file) throws IOException {
        requireObject(parser, name);
        String generationId = null;
        Instant fencedAt = null;
        var seen = new HashSet<String>();
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            var field = nextField(parser, seen);
            switch (field) {
                case FIELD_GENERATION_ID -> generationId = string(parser, field);
                case FIELD_FENCED_AT -> fencedAt = instant(parser, field);
                default -> throw undeclared(field);
            }
        }
        requireFields(seen, name, FIELD_GENERATION_ID, FIELD_FENCED_AT);
        try {
            return new Fence(generationId, fencedAt);
        } catch (IllegalArgumentException e) {
            throw unreadable("%s: %s".formatted(name, e.getMessage()), e);
        }
    }

    /**
     * One fence.
     *
     * @param generationId the generation, {@code l-} and 16 hex digits
     * @param fencedAt     the instant it was fenced
     * @since 0.1
     */
    public record Fence(String generationId, Instant fencedAt) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the generation breaks its form
         */
        public Fence {
            Objects.requireNonNull(generationId, "generationId");
            Objects.requireNonNull(fencedAt, "fencedAt");
            if (!Pattern.matches("l-[0-9a-f]{16}", generationId)) {
                throw new IllegalArgumentException("Not a generation (l- and 16 hex digits): " + generationId);
            }
        }
    }
}
