/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.store.HolderInstance;
import de.planmarshall.core.store.LeaseOwner;
import de.planmarshall.core.store.ModelSlotStore;
import de.planmarshall.core.store.ModelSlotStore.Document;
import de.planmarshall.core.store.ModelSlotStore.Form;
import de.planmarshall.core.store.ModelSlotStore.Lease;
import de.planmarshall.core.store.ModelSlotStore.Revision;
import de.planmarshall.core.store.ModelSlotStore.Waiting;
import de.planmarshall.core.store.ModelSlotStore.WorkerClass;

/**
 * The admission of the machine model slots (PM-WF-14, Model Work § Model Slots & One Writer per Plan): every running
 * worker, warm or fresh, holds one of the {@code model_slots} slots of {@code model-slots.json}, and a worker that
 * finds none waits in FIFO order until one frees.
 * <p>
 * Admission decides in one transaction of the store, so two workers never see the same free slot: the slots, the queue,
 * the cap and the one-writer rule are read and written together. The rules are the specification's:
 * <ul>
 *   <li>a fresh worker holds its slot from spawn to exit, a warm worker for its whole generation, idle or not;</li>
 *   <li>at most one {@code writer} worker of a plan holds a slot or waits for one; the other classes run beside it
 *       within the machine cap;</li>
 *   <li>a slot whose runtime is no longer alive is reaped in the admission transaction, before the cap is applied, so
 *       a slot a killed daemon left never keeps a worker out; the recovery of the runtime settles the jobs of that
 *       instance before an admission runs, so a reaped slot's worker has ended;</li>
 *   <li>a paused plan holds no slot: {@link #releasePlan(String, String)} releases every slot of the plan of a project
 *       and drops its
 *       waiting workers at once.</li>
 * </ul>
 * The cap is {@code model_slots} of {@code machine-config.json}, its compiled default {@value #DEFAULT_SLOTS} and its
 * bounds {@value #MIN_SLOTS} to {@value #MAX_SLOTS} (Timeouts &amp; Budgets Specification, Model slot lease).
 * <p>
 * The probe that decides whether the runtime of a slot is alive is a seam, so a test decides it without starting a
 * process; {@link Liveness#processes()} is the probe of the runtime.
 *
 * @since 0.1
 */
public final class ModelSlotScheduler {

    /** The compiled default of {@code model_slots}. */
    public static final int DEFAULT_SLOTS = 2;

    /** The lowest cap {@code model_slots} may have. */
    public static final int MIN_SLOTS = 1;

    /** The highest cap {@code model_slots} may have. */
    public static final int MAX_SLOTS = 16;

    /** How far the process start instant of the operating system may lie from the recorded one. */
    private static final Duration START_TOLERANCE = Duration.ofSeconds(1);

    private final ModelSlotStore store;
    private final int slotCount;
    private final Liveness liveness;
    private final InstantSource clock;

    /**
     * Decides whether the runtime that serves a slot is still alive.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface Liveness {

        /**
         * @param holder the runtime instance a slot records
         * @return whether that runtime still runs; a runtime the probe cannot decide is never reapable
         */
        boolean alive(HolderInstance holder);

        /**
         * @return the probe of the runtime: the process id, together with the start instant that tells a reused id
         *         apart
         */
        static Liveness processes() {
            return holder -> {
                var handle = ProcessHandle.of(holder.runtimePid());
                if (handle.isEmpty() || !handle.get().isAlive()) {
                    return false;
                }
                var startedAt = handle.get().info().startInstant();
                return startedAt.isEmpty()
                        || Duration.between(startedAt.get(), holder.runtimeStartedAt()).abs().compareTo(START_TOLERANCE)
                        <= 0;
            };
        }
    }

    /**
     * One worker that asks for a slot.
     *
     * @param owner       the owning scope and the runtime that asks
     * @param jobId       the worker generation
     * @param role        the role the worker runs
     * @param workerClass the class of the worker
     * @param form        the form of the worker
     * @param planId      the plan the worker serves, for every class; {@code null} for a worker that serves no plan,
     *                    the project's workspace scope
     * @since 0.1
     */
    public record Request(LeaseOwner owner, String jobId, String role, WorkerClass workerClass, Form form,
    @Nullable String planId) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if a writer names no plan
         */
        public Request {
            Objects.requireNonNull(owner, "owner");
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(workerClass, "workerClass");
            Objects.requireNonNull(form, "form");
            if (workerClass == WorkerClass.WRITER && planId == null) {
                throw new IllegalArgumentException("A writer worker serves a plan: planId is set");
            }
        }
    }

    /**
     * The outcome of one admission.
     *
     * @since 0.1
     */
    public sealed interface Admission permits Granted, Queued, Deferred {
    }

    /**
     * The worker holds a slot.
     *
     * @param lease the slot it holds
     * @since 0.1
     */
    public record Granted(Lease lease) implements Admission {

        /** @throws NullPointerException if the lease is {@code null} */
        public Granted {
            Objects.requireNonNull(lease, "lease");
        }
    }

    /**
     * The worker waits for a slot, in the order of its request.
     *
     * @param waiting its entry in the FIFO
     * @since 0.1
     */
    public record Queued(Waiting waiting) implements Admission {

        /** @throws NullPointerException if the entry is {@code null} */
        public Queued {
            Objects.requireNonNull(waiting, "waiting");
        }
    }

    /**
     * The worker does not even wait yet: another writer worker of its plan holds the slot or waits for one, and the
     * worker's task stays queued until that one is settled.
     *
     * @param reason the reason of the deferral, {@value #WRITER_HOLDS_PLAN} today
     * @since 0.1
     */
    public record Deferred(String reason) implements Admission {

        /** @throws NullPointerException if the reason is {@code null} */
        public Deferred {
            Objects.requireNonNull(reason, "reason");
        }
    }

    /** The reason of a deferred writer whose plan already has a writer worker. */
    public static final String WRITER_HOLDS_PLAN = "writer_holds_plan";

    /**
     * @param store     the model-slot store
     * @param slotCount the cap {@code model_slots}; {@value #DEFAULT_SLOTS} when the machine configuration declares
     *                  none
     * @param liveness  the probe that decides whether the runtime of a slot is alive
     * @param clock     the clock of the store
     * @throws IllegalArgumentException if the cap lies outside {@value #MIN_SLOTS} to {@value #MAX_SLOTS}
     */
    public ModelSlotScheduler(ModelSlotStore store, int slotCount, Liveness liveness, InstantSource clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.liveness = Objects.requireNonNull(liveness, "liveness");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (slotCount < MIN_SLOTS || slotCount > MAX_SLOTS) {
            throw new IllegalArgumentException("'model_slots' lies outside %d to %d: %d"
                    .formatted(MIN_SLOTS, MAX_SLOTS, slotCount));
        }
        this.slotCount = slotCount;
    }

    /**
     * Asks for a slot for one worker. A worker that holds a slot or waits already is answered with its place, so a
     * repeated request is harmless.
     *
     * @param request the worker
     * @return whether the worker holds a slot, waits for one, or is deferred behind the writer of its plan
     */
    public Admission request(Request request) {
        Objects.requireNonNull(request, "request");
        var now = clock.instant();
        return store.update(document -> {
            var reaped = reap(document);
            var held = findSlot(reaped, request.jobId());
            if (held.isPresent()) {
                return new Revision<>(reaped, new Granted(held.get()));
            }
            var waiting = findWaiting(reaped, request.jobId());
            if (waiting.isPresent()) {
                return new Revision<>(reaped, new Queued(waiting.get()));
            }
            if (request.workerClass() == WorkerClass.WRITER
                    && writerOfPlan(reaped, request.owner().projectId(), request.planId())) {
                return new Revision<>(reaped, new Deferred(WRITER_HOLDS_PLAN));
            }
            var enqueued = new ArrayList<>(reaped.waiting());
            var entry = new Waiting(request.owner(), request.jobId(), request.role(), request.workerClass(),
                    request.form(), request.planId(), now);
            enqueued.add(entry);
            var admitted = admit(new Document(reaped.slots(), enqueued), now);
            return new Revision<>(admitted.document(), admitted.outcomeOf(request.jobId()));
        });
    }

    /**
     * Releases the slot of a settled worker and admits the waiting workers it makes room for.
     *
     * @param jobId the worker generation
     * @return the slots admitted in its place, in admission order; empty if it held none
     */
    public List<Lease> release(String jobId) {
        Objects.requireNonNull(jobId, "jobId");
        return store.update(document -> {
            var reaped = reap(document);
            var remaining = new ArrayList<Lease>();
            for (var lease : reaped.slots()) {
                if (!jobId.equals(lease.jobId())) {
                    remaining.add(lease);
                }
            }
            var waiting = without(reaped.waiting(), jobId);
            var admitted = admit(new Document(remaining, waiting), clock.instant());
            return new Revision<>(admitted.document(), admitted.admitted());
        });
    }

    /**
     * Releases every slot of a plan of one project and drops its waiting workers at once: a paused plan holds no model
     * slot. The plan is named by its project together with its plan id, because a plan id is unique only within its
     * project. The workers the freed slots make room for are admitted in the same transaction.
     *
     * @param projectId the project of the plan
     * @param planId    the plan
     * @return the slots admitted in the plan's place, in admission order
     */
    public List<Lease> releasePlan(String projectId, String planId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(planId, "planId");
        return store.update(document -> {
            var reaped = reap(document);
            var remaining = new ArrayList<Lease>();
            for (var lease : reaped.slots()) {
                if (!projectId.equals(lease.owner().projectId()) || !planId.equals(lease.planId())) {
                    remaining.add(lease);
                }
            }
            var waiting = new ArrayList<Waiting>();
            for (var entry : reaped.waiting()) {
                if (!projectId.equals(entry.owner().projectId()) || !planId.equals(entry.planId())) {
                    waiting.add(entry);
                }
            }
            var admitted = admit(new Document(remaining, waiting), clock.instant());
            return new Revision<>(admitted.document(), admitted.admitted());
        });
    }

    /** @return the document of the store as it is at the moment of the read */
    public Document snapshot() {
        return store.snapshot();
    }

    /** @return the cap this admission applies */
    public int slotCount() {
        return slotCount;
    }

    /** @return the slots the reaping admitted, and the document after the admission */
    private AdmissionResult admit(Document document, Instant now) {
        var slots = new ArrayList<>(document.slots());
        var waiting = new ArrayList<>(document.waiting());
        var admitted = new ArrayList<Lease>();
        while (slots.size() < slotCount && !waiting.isEmpty()) {
            var head = waiting.removeFirst();
            slots.add(new Lease(head.owner(), head.jobId(), head.role(), head.workerClass(), head.form(), head.planId(),
                    now, null));
            admitted.add(slots.getLast());
        }
        return new AdmissionResult(new Document(slots, waiting), admitted);
    }

    private Document reap(Document document) {
        var slots = new ArrayList<Lease>();
        for (var lease : document.slots()) {
            if (liveness.alive(lease.owner().holderInstance())) {
                slots.add(lease);
            }
        }
        var waiting = new ArrayList<Waiting>();
        for (var entry : document.waiting()) {
            if (liveness.alive(entry.owner().holderInstance())) {
                waiting.add(entry);
            }
        }
        return slots.size() == document.slots().size() && waiting.size() == document.waiting().size() ? document
                : new Document(slots, waiting);
    }

    private static Optional<Lease> findSlot(Document document, String jobId) {
        return document.slots().stream().filter(lease -> jobId.equals(lease.jobId())).findFirst();
    }

    private static Optional<Waiting> findWaiting(Document document, String jobId) {
        return document.waiting().stream().filter(entry -> jobId.equals(entry.jobId())).findFirst();
    }

    private static boolean writerOfPlan(Document document, String projectId, String planId) {
        return document.slots().stream()
                .anyMatch(lease -> lease.workerClass() == WorkerClass.WRITER && planId.equals(lease.planId())
                        && projectId.equals(lease.owner().projectId()))
                || document.waiting().stream()
                .anyMatch(entry -> entry.workerClass() == WorkerClass.WRITER && planId.equals(entry.planId())
                        && projectId.equals(entry.owner().projectId()));
    }

    private static List<Waiting> without(List<Waiting> waiting, String jobId) {
        return waiting.stream().filter(entry -> !jobId.equals(entry.jobId())).toList();
    }

    /**
     * The document after an admission and what it admitted.
     *
     * @param document the document
     * @param admitted the leases admitted in the transaction
     */
    private record AdmissionResult(Document document, List<Lease> admitted) {

        Admission outcomeOf(String jobId) {
            return document.slots().stream().filter(lease -> jobId.equals(lease.jobId())).findFirst()
                    .<Admission>map(Granted::new)
                    .or(() -> document.waiting().stream().filter(entry -> jobId.equals(entry.jobId())).findFirst()
                            .<Admission>map(Queued::new))
                    .orElseGet(() -> new Deferred(WRITER_HOLDS_PLAN));
        }
    }
}
