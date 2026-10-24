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

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;

/**
 * The machine model-slot store {@code state/model-slots.json} (PM-IMPL-7, PM-WF-14): one lease per running worker, warm
 * or fresh, and the FIFO of the workers that wait for a slot.
 * <p>
 * The store is the substrate of the model-slot admission. It holds what the specification of the store holds and
 * decides nothing: the cap {@code model_slots} of {@code machine-config.json} and the one-writer-per-plan rule belong
 * to the admission, which reads and rewrites the document in one transaction through {@link #update(Function)}. A
 * transaction takes the lock of the machine store, reads the document, applies the change and writes it back, so two
 * admissions never interleave and the slot count of the written document is the count that was admitted.
 * <p>
 * Every slot is a lease of the store and is owned by a scope, never by the runtime that serves it (as every machine
 * lease of {@link LeaseStore}); the runtime instance stays the record of who serves it at the moment, so a slot whose
 * runtime died is recognisable and reapable. Unlike a build slot, a model slot has no time bound:
 * {@code lease_expires_at} is {@code null} on every lease, because a worker holds its slot from spawn to exit and the
 * supervisor's bounds, never a lease expiry, end it.
 * <p>
 * The directory of the store exists before the first write; this type does not create it.
 *
 * @since 0.1
 */
public final class ModelSlotStore {

    /** The format version of a model-slot store document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    private static final Pattern JOB_ID = Pattern.compile("l-[0-9a-f]{16}");
    private static final Pattern ROLE = Pattern.compile("[a-z][a-z0-9-]{1,31}");

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;

    /**
     * The class of a worker, which the slot rules bound differently: at most one {@link #WRITER} worker of a plan runs
     * or waits for a slot, while the other classes run beside it within the machine cap.
     *
     * @since 0.1
     */
    public enum WorkerClass {
        /** A worker that changes the plan's worktree. */
        WRITER("writer"),
        /** A worker that only reads; several of one plan run beside its writer. */
        READ_ONLY("read_only"),
        /** A worker of a role without tools and without a connection. */
        ZERO_TOOL("zero_tool");

        private final String wireName;

        WorkerClass(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<WorkerClass> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * The form of a worker: a fresh worker serves one task and ends, a warm worker holds its slot for its whole
     * generation, idle or not.
     *
     * @since 0.1
     */
    public enum Form {
        /** One worker per task. */
        FRESH("fresh"),
        /** A supervised worker that takes task after task. */
        WARM("warm");

        private final String wireName;

        Form(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store */
        public String wireName() {
            return wireName;
        }

        /**
         * @param wireName the name of a value in a store
         * @return the value of that name; empty if no value has it
         */
        public static Optional<Form> fromWireName(String wireName) {
            return Arrays.stream(values()).filter(value -> value.wireName.equals(wireName)).findFirst();
        }
    }

    /**
     * One held model slot.
     *
     * @param owner          the owning scope and the runtime that serves the slot
     * @param jobId          the worker generation that holds the slot
     * @param role           the role the worker runs
     * @param workerClass    the class of the worker
     * @param form           the form of the worker
     * @param planId         the plan of a writer worker; {@code null} for the other classes
     * @param acquiredAt     the instant the slot was acquired
     * @param leaseExpiresAt always {@code null}: a model slot has no time bound
     * @since 0.1
     */
    public record Lease(LeaseOwner owner, String jobId, String role, WorkerClass workerClass, Form form, String planId,
    Instant acquiredAt, Instant leaseExpiresAt) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if an identifier or a role breaks its grammar, the instant is null, a
         *                                  writer has no plan, another class names one, or the lease names an expiry
         */
        public Lease {
            Objects.requireNonNull(owner, "owner");
            requireIdentifier(jobId, JOB_ID, "job_id");
            requireIdentifier(role, ROLE, "role");
            Objects.requireNonNull(workerClass, "worker_class");
            Objects.requireNonNull(form, "form");
            Objects.requireNonNull(acquiredAt, "acquired_at");
            if (workerClass == WorkerClass.WRITER) {
                requireIdentifier(planId, null, "plan_id");
            } else if (planId != null) {
                requireIdentifier(planId, null, "plan_id");
            }
            if (leaseExpiresAt != null) {
                throw new IllegalArgumentException("A model slot has no time bound: lease_expires_at must be null");
            }
        }
    }

    /**
     * One worker waiting for a model slot, in request order.
     *
     * @param owner       the owning scope and the runtime that waits
     * @param jobId       the worker generation that waits
     * @param role        the role the worker runs
     * @param workerClass the class of the worker
     * @param form        the form of the worker, which the admission carries into the slot it grants
     * @param planId      the plan of a writer worker; {@code null} for the other classes
     * @param enqueuedAt  the instant the worker was enqueued
     * @since 0.1
     */
    public record Waiting(LeaseOwner owner, String jobId, String role, WorkerClass workerClass, Form form,
    String planId, Instant enqueuedAt) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if an identifier or a role breaks its grammar, or a writer has no plan
         */
        public Waiting {
            Objects.requireNonNull(owner, "owner");
            requireIdentifier(jobId, JOB_ID, "job_id");
            requireIdentifier(role, ROLE, "role");
            Objects.requireNonNull(workerClass, "worker_class");
            Objects.requireNonNull(form, "form");
            Objects.requireNonNull(enqueuedAt, "enqueued_at");
            if (workerClass == WorkerClass.WRITER) {
                requireIdentifier(planId, null, "plan_id");
            } else if (planId != null) {
                requireIdentifier(planId, null, "plan_id");
            }
        }
    }

    /**
     * The document of the store: the held slots and the waiting workers, each in the order of the store.
     *
     * @param slots   the held slots, in the order they were acquired
     * @param waiting the waiting workers, in request order
     * @since 0.1
     */
    public record Document(List<Lease> slots, List<Waiting> waiting) {

        /** @throws NullPointerException if a component is {@code null} */
        public Document {
            slots = List.copyOf(Objects.requireNonNull(slots, "slots"));
            waiting = List.copyOf(Objects.requireNonNull(waiting, "waiting"));
        }

        /** @return the empty document */
        public static Document empty() {
            return new Document(List.of(), List.of());
        }
    }

    /**
     * The change of one transaction: the document to write and the value the caller asked for.
     *
     * @param document the document after the change; equal to the one before it, nothing is written
     * @param outcome  what the change decided
     * @param <T>      what the change decided
     * @since 0.1
     */
    public record Revision<T>(Document document, T outcome) {

        /** @throws NullPointerException if a component is {@code null} */
        public Revision {
            Objects.requireNonNull(document, "document");
            Objects.requireNonNull(outcome, "outcome");
        }
    }

    /**
     * @param storeFile   the store file with the key of its machine store lock
     * @param lockManager the lock manager of the runtime
     * @throws IllegalArgumentException if the key of the store file is not a machine store lock
     */
    public ModelSlotStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        if (storeFile.lockKey().level() != LockLevel.MACHINE_STORE) {
            throw new IllegalArgumentException(
                    "A model-slot store is guarded by a machine store lock: " + storeFile.lockKey().lockFile());
        }
    }

    /**
     * Reads the document, applies the change and writes it back in one transaction on the lock of the store. The store
     * is written only when the change returns a document that differs from the one it was given.
     *
     * @param change what becomes of the document, and what it decided
     * @param <T>    what the change decided
     * @return what the change decided
     * @throws StoreRefusal           if the store is in another format version or unreadable
     * @throws UncheckedIOException   if the store cannot be read or written
     * @throws InternalFaultException if the lock of the store must not be acquired by the transaction
     */
    public <T> T update(Function<Document, Revision<T>> change) {
        Objects.requireNonNull(change, "change");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var before = storeFile.read(transaction).map(this::decode).orElseGet(Document::empty);
            var revision = change.apply(before);
            if (!revision.document().equals(before)) {
                storeFile.write(transaction, ModelSlotCodec.write(revision.document()));
            }
            return revision.outcome();
        }
    }

    /**
     * Reads the document without a lock, as it is at the moment of the read. A write replaces the store in one step, so
     * the result is the document before or after a write and never a part of one; it may be out of date as soon as it
     * is returned.
     *
     * @return the document of the store; the empty document if the store does not exist
     * @throws StoreRefusal         if the store is in another format version or unreadable
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Document snapshot() {
        return StoreSnapshot.read(storeFile.store()).content().map(this::decode).orElseGet(Document::empty);
    }

    /** @return the store file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }

    private Document decode(byte[] document) {
        return ModelSlotCodec.read(storeFile.store(), document);
    }

    private static void requireIdentifier(String value, Pattern pattern, String name) {
        if (pattern == null) {
            LockKey.identifier(value, name);
            return;
        }
        Objects.requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("'%s' is not an identifier: %s".formatted(name, value));
        }
    }
}
