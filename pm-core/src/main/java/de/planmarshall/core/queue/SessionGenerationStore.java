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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

import de.planmarshall.core.queue.SessionGeneration.Scope;
import de.planmarshall.core.queue.SessionGeneration.Workdir;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AtomicStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.ScopeSessionRow;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.StoreSnapshot;

/**
 * The session generation record {@code queue/session-generations.json} of a project (PM-WORK-5): the generations of
 * its interactive sessions, issued, fenced, given a scope and finally removed.
 * <p>
 * The record is one document, written under the queue lock {@code locks/queue.lock} by one atomic write-and-rename
 * ({@link AtomicStoreFile}); it is no part of a scope's transaction journal and has no digest sidecar. Every writing
 * method is one read-modify-write under that lock. Issuing a generation is the one that matters for correctness: the
 * new generation fences every earlier generation of its session key, and its drawn id is checked against the ids in
 * the record and drawn again on a collision. The fence and the issue are one write, so no reader ever sees two current
 * generations of one session key, nor a fenced generation without the generation that fenced it. A read takes no
 * lock ({@link StoreSnapshot}): it sees the record before or after a write and never a part of one, and since a
 * generation is never unfenced, a generation a read found fenced stays fenced.
 * <p>
 * The issue instants of one session key strictly increase: an issue whose clock reads an instant not after the latest
 * issue of its key takes the next nanosecond after it. A generation is fenced at the issue instant of the generation
 * that fenced it, so the record names that generation unambiguously, which is what tells a generation superseded by a
 * {@code pm-mcp enter} from a fenced one ({@link GenerationCheck.Superseded}).
 * <p>
 * The directory {@code queue/} of the project is created by the first write.
 *
 * @since 0.1
 */
public final class SessionGenerationStore {

    /** The directory of the task queue below the per-project machine store. */
    public static final String DIRECTORY = "queue";

    /** The name of the session generation record in the task queue directory. */
    public static final String FILE_NAME = "session-generations.json";

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;
    private final Clock clock;
    private final RandomGenerator random;

    /**
     * What the caller states about the session a generation is issued for.
     *
     * @param sessionKey      the SHA-256 of the session identity, 64 lower-case hexadecimal digits
     * @param client          the declared client
     * @param clientSessionId the host session identifier; {@code null} when the host provides none
     * @param sessionTasks    whether the client profile marks the host eligible for session tasks
     * @since 0.1
     */
    public record Request(String sessionKey, ScopeSessionRow.Client client, String clientSessionId,
    boolean sessionTasks) {

        /** @throws NullPointerException if the session key or the client is {@code null} */
        public Request {
            Objects.requireNonNull(sessionKey, "sessionKey");
            Objects.requireNonNull(client, "client");
        }
    }

    /**
     * @param base        the base directory of the runtime
     * @param projectId   the project
     * @param lockManager the lock manager of the runtime
     * @param clock       the clock of the issue instants
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public SessionGenerationStore(Path base, String projectId, LockManager lockManager, Clock clock) {
        this(base, projectId, lockManager, clock, null);
    }

    /**
     * @param random the source of the drawn generation ids; {@code null} for the secure default
     * @see #SessionGenerationStore(Path, String, LockManager, Clock)
     */
    SessionGenerationStore(Path base, String projectId, LockManager lockManager, Clock clock,
            RandomGenerator random) {
        var store = LockKey.projectStore(base, projectId).resolve(DIRECTORY).resolve(FILE_NAME);
        this.storeFile = new AtomicStoreFile(store, LockKey.queue(base, projectId));
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = random;
    }

    /**
     * Issues a new generation for the session identity and fences every earlier generation of its session key, in
     * one write under the queue lock.
     *
     * @param request the session the generation is issued for
     * @param cause   what issues it
     * @return the new generation, current
     * @throws IllegalArgumentException if a value of the request breaks its form; nothing was written then
     * @throws StoreRefusal             if the record is in another format version or unreadable
     * @throws UncheckedIOException     if the record cannot be read or written
     * @throws InternalFaultException   if the queue lock must not be acquired by the transaction
     */
    public SessionGeneration issue(Request request, GenerationCause cause) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(cause, "cause");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var generations = load(transaction);
            var issued = issueInto(generations, request, cause);
            save(transaction, generations);
            return issued;
        }
    }

    /**
     * The first binding of a session relay: the current generation of the session key when there is one, so a relay
     * its host restarted continues in its session's generation; otherwise a new generation issued by
     * {@link GenerationCause#RELAY_START}.
     *
     * @param request the session the relay binds
     * @return the current generation of the session key, found or issued
     * @throws IllegalArgumentException if a value of the request breaks its form; nothing was written then
     * @throws StoreRefusal             if the record is in another format version or unreadable
     * @throws UncheckedIOException     if the record cannot be read or written
     * @throws InternalFaultException   if the queue lock must not be acquired by the transaction
     */
    public SessionGeneration relayStart(Request request) {
        Objects.requireNonNull(request, "request");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var generations = load(transaction);
            var current = generations.values().stream()
                    .filter(generation -> !generation.isFenced()
                            && generation.sessionKey().equals(request.sessionKey()))
                    .findFirst();
            if (current.isPresent()) {
                return current.get();
            }
            var issued = issueInto(generations, request, GenerationCause.RELAY_START);
            save(transaction, generations);
            return issued;
        }
    }

    /**
     * The relay observed a new host session: a new generation is always issued.
     *
     * @param request the session
     * @return the new generation, current
     * @see #issue(Request, GenerationCause)
     */
    public SessionGeneration newSession(Request request) {
        return issue(request, GenerationCause.NEW_SESSION);
    }

    /**
     * {@code pm-mcp enter} of the session identity: a new generation is always issued, and the generations it fences
     * are superseded by it.
     *
     * @param request the session
     * @return the new generation, current
     * @see #issue(Request, GenerationCause)
     */
    public SessionGeneration enter(Request request) {
        return issue(request, GenerationCause.ENTER);
    }

    /**
     * Gives a current generation the scope its session entered and the working directory it entered from, as the
     * {@code start.create-scope} commit of a bootstrap does for the committing generation. The method takes the queue
     * lock in a transaction of its own.
     *
     * @param id      the generation
     * @param scope   the scope
     * @param workdir the working directory
     * @return {@link GenerationCheck.Current} with the changed entry if the generation is current; otherwise what the
     *         record says about it, and nothing was written then
     * @throws StoreRefusal           if the record is in another format version or unreadable
     * @throws UncheckedIOException   if the record cannot be read or written
     * @throws InternalFaultException if the queue lock must not be acquired by the transaction
     */
    public GenerationCheck assignScope(GenerationId id, Scope scope, Workdir workdir) {
        try (var transaction = lockManager.openTransaction()) {
            return assignScope(transaction, id, scope, workdir);
        }
    }

    /**
     * Gives a current generation its scope and working directory in the caller's transaction, which may hold the
     * lock of a scope already: the queue lock comes after every scope lock in the lock order, and it is held until the
     * caller closes the transaction.
     *
     * @param transaction the caller's transaction; the queue lock is acquired in it
     * @param id          the generation
     * @param scope       the scope
     * @param workdir     the working directory
     * @return {@link GenerationCheck.Current} with the changed entry if the generation is current; otherwise what the
     *         record says about it, and nothing was written then
     * @throws StoreRefusal           if the record is in another format version or unreadable
     * @throws UncheckedIOException   if the record cannot be read or written
     * @throws InternalFaultException if the queue lock must not be acquired after the locks the transaction holds
     */
    public GenerationCheck assignScope(LockTransaction transaction, GenerationId id, Scope scope, Workdir workdir) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(workdir, "workdir");
        transaction.acquire(storeFile.lockKey());
        var generations = load(transaction);
        var check = GenerationCheck.of(id, generations.get(id));
        if (!(check instanceof GenerationCheck.Current(var current))) {
            return check;
        }
        var assigned = current.withScope(scope, workdir);
        if (!assigned.equals(current)) {
            generations.put(id, assigned);
            save(transaction, generations);
        }
        return new GenerationCheck.Current(assigned);
    }

    /**
     * Checks the generation a call carries without a lock.
     *
     * @param id the generation
     * @return what the record says about it
     * @throws StoreRefusal         if the record is in another format version or unreadable
     * @throws UncheckedIOException if the record exists and cannot be read
     */
    public GenerationCheck check(GenerationId id) {
        Objects.requireNonNull(id, "id");
        var found = snapshot().stream().filter(generation -> generation.id().equals(id)).findFirst();
        return GenerationCheck.of(id, found.orElse(null));
    }

    /**
     * Removes every fenced generation fenced before the instant, in one write under the queue lock; a current
     * generation and one fenced at or after the instant are kept. The record is written only if an entry is removed.
     * A generation fenced by a removed one was fenced no later than it, so it is removed with it, and every fenced
     * entry that stays still has the generation that fenced it.
     *
     * @param instant the oldest fence that is kept
     * @return the removed generations, in the order of the record
     * @throws StoreRefusal           if the record is in another format version or unreadable
     * @throws UncheckedIOException   if the record cannot be read or written
     * @throws InternalFaultException if the queue lock must not be acquired by the transaction
     */
    public List<SessionGeneration> removeFencedOlderThan(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var generations = load(transaction);
            var removed = new ArrayList<SessionGeneration>();
            var remaining = generations.values().iterator();
            while (remaining.hasNext()) {
                var generation = remaining.next();
                if (generation.isFenced() && generation.fence().at().isBefore(instant)) {
                    removed.add(generation);
                    remaining.remove();
                }
            }
            if (!removed.isEmpty()) {
                save(transaction, generations);
            }
            return List.copyOf(removed);
        }
    }

    /**
     * Reads the record without a lock, as it is at the moment of the read. It may be out of date as soon as it is
     * returned; nothing a write depends on is decided on it.
     *
     * @return the generations of the record, in its order; empty if the record does not exist
     * @throws StoreRefusal         if the record is in another format version or unreadable
     * @throws UncheckedIOException if the record exists and cannot be read
     */
    public List<SessionGeneration> snapshot() {
        return StoreSnapshot.read(storeFile.store()).content().map(this::decode).orElseGet(List::of);
    }

    /** @return the record file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }

    private SessionGeneration issueInto(Map<GenerationId, SessionGeneration> generations, Request request,
            GenerationCause cause) {
        GenerationId id;
        do {
            id = random == null ? GenerationId.random() : GenerationId.random(random);
        } while (generations.containsKey(id));
        var issuedAt = issueInstant(generations, request.sessionKey());
        var issued = SessionGeneration.issued(id, request.sessionKey(), request.client(), request.clientSessionId(),
                cause, issuedAt, request.sessionTasks());
        var byEnter = cause == GenerationCause.ENTER;
        generations.replaceAll((key, generation) -> generation.sessionKey().equals(request.sessionKey())
                ? generation.fencedAt(issuedAt, byEnter)
                : generation);
        generations.put(id, issued);
        return issued;
    }

    /** The clock's instant, or the next nanosecond after the latest issue of the key if the clock is not past it. */
    private Instant issueInstant(Map<GenerationId, SessionGeneration> generations, String sessionKey) {
        var now = clock.instant();
        Optional<Instant> latest = generations.values().stream()
                .filter(generation -> generation.sessionKey().equals(sessionKey))
                .map(SessionGeneration::issuedAt).max(Instant::compareTo);
        return latest.filter(last -> !now.isAfter(last)).map(last -> last.plusNanos(1)).orElse(now);
    }

    private Map<GenerationId, SessionGeneration> load(LockTransaction transaction) {
        var generations = new LinkedHashMap<GenerationId, SessionGeneration>();
        storeFile.read(transaction).map(this::decode).orElseGet(List::of)
                .forEach(generation -> generations.put(generation.id(), generation));
        return generations;
    }

    private void save(LockTransaction transaction, Map<GenerationId, SessionGeneration> generations) {
        try {
            Files.createDirectories(storeFile.store().getParent());
        } catch (IOException e) {
            throw new UncheckedIOException("Task queue directory cannot be created: " + storeFile.store().getParent(),
                    e);
        }
        storeFile.write(transaction, SessionGenerationCodec.write(generations.values()));
    }

    private List<SessionGeneration> decode(byte[] document) {
        return SessionGenerationCodec.read(storeFile.store(), document);
    }
}
