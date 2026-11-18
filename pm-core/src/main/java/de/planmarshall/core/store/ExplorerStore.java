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
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockManager;

/**
 * The reader and writer of {@code <PM_MCP_BASE>/explorer.json}, the operator's projects (Store Schemas § The Operator's
 * Projects; Runtime &amp; Interaction Model § The Projects of the Machine): the favourite projects in the operator's
 * order, and per project when the operator last acted in it and how often per UTC day within the activity window.
 * <p>
 * The store serves operator data only: no model reads it, and it never reaches a representation. A write is one short
 * transaction on {@code locks/explorer.lock}: it reads the document again under the lock, refuses a caller whose
 * revision is not the one the document holds ({@code conflict}, 409), drops every entry of a project that is no longer
 * enrolled and every day older than the window, applies the change and increments the revision. A favourite is set,
 * moved or removed by writing the whole ordered list, so the operator's order survives.
 *
 * @since 0.1
 */
public final class ExplorerStore {

    /** The format version of the document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /** The compiled window of the activity: acts older than this many UTC days are dropped by the next write. */
    public static final int ACTIVITY_WINDOW_DAYS = 30;

    /** The revision a document that does not exist reads as. */
    private static final long NO_DOCUMENT = 0L;

    private static final String LOCK = "explorer";
    private static final String FILE = "explorer.json";
    private static final String PROJECT_ID = "projectId";

    private final AtomicStoreFile storeFile;
    private final LockManager lockManager;
    private final Clock clock;

    /**
     * The activity of one project: when the operator last acted in it and how often on each UTC day of the window.
     *
     * @param lastAt the last act
     * @param days   the acts per UTC day, the day as {@code yyyy-MM-dd}
     * @since 0.1
     */
    public record Activity(Instant lastAt, Map<String, Integer> days) {

        /**
         * @throws NullPointerException     if the instant is {@code null}
         * @throws IllegalArgumentException if a day is no date or a count is below one
         */
        public Activity {
            Objects.requireNonNull(lastAt, "lastAt");
            days = Map.copyOf(days);
            for (var day : days.entrySet()) {
                requireDay(day.getKey(), day.getValue());
            }
        }

        /** @return the number of acts the window holds */
        public int actsInWindow() {
            return days.values().stream().mapToInt(Integer::intValue).sum();
        }

        private static void requireDay(String day, int count) {
            try {
                LocalDate.parse(day);
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("A day of the activity is a UTC date: " + day, e);
            }
            if (count < 1) {
                throw new IllegalArgumentException("An act count is one or more, and not " + count);
            }
        }
    }

    /**
     * What a read of the store found.
     *
     * @param favourites the project ids in the operator's order
     * @param activity   the activity by project
     * @param revision   the {@code revision} of the document; {@value #NO_DOCUMENT} when it does not exist
     * @since 0.1
     */
    public record Snapshot(List<String> favourites, Map<String, Activity> activity, long revision) {

        /** Keeps the components unmodifiable. */
        public Snapshot {
            favourites = List.copyOf(favourites);
            activity = Map.copyOf(activity);
        }

        /**
         * @param limit how many projects to name
         * @return the projects the operator used most within the window, the most used first; a tie goes to the more
         *         recent act, then to the project id
         */
        public List<String> mostUsed(int limit) {
            Comparator<Map.Entry<String, Activity>> order = Comparator
                    .comparingInt((Map.Entry<String, Activity> entry) -> entry.getValue().actsInWindow())
                    .reversed()
                    .thenComparing((Map.Entry<String, Activity> entry) -> entry.getValue().lastAt(),
                            Comparator.reverseOrder())
                    .thenComparing(Map.Entry::getKey);
            return activity.entrySet().stream().sorted(order).limit(Math.max(0, limit)).map(Map.Entry::getKey)
                    .toList();
        }

        /**
         * @param limit how many projects to name
         * @return the projects of the most recent acts, the most recent first; a tie goes to the project id
         */
        public List<String> recent(int limit) {
            Comparator<Map.Entry<String, Activity>> order = Comparator
                    .comparing((Map.Entry<String, Activity> entry) -> entry.getValue().lastAt())
                    .reversed()
                    .thenComparing(Map.Entry::getKey);
            return activity.entrySet().stream().sorted(order).limit(Math.max(0, limit)).map(Map.Entry::getKey)
                    .toList();
        }
    }

    /**
     * @param storeFile   the {@code explorer.json} file with the key of the explorer lock
     * @param lockManager the lock manager of the runtime
     * @param clock       the clock of the UTC day and instant an act is recorded at
     * @throws IllegalArgumentException if the key of the store file is not the machine's explorer lock
     */
    public ExplorerStore(AtomicStoreFile storeFile, LockManager lockManager, Clock clock) {
        this.storeFile = Objects.requireNonNull(storeFile, "storeFile");
        this.lockManager = Objects.requireNonNull(lockManager, "lockManager");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (storeFile.lockKey().level() != LockLevel.MACHINE_STORE || !LOCK.equals(storeFile.lockKey().id())) {
            throw new IllegalArgumentException("The operator's projects are guarded by the machine's explorer lock: "
                    + storeFile.lockKey().lockFile());
        }
    }

    /**
     * @param storeFile   the {@code explorer.json} file with the key of the explorer lock
     * @param lockManager the lock manager of the runtime
     */
    public ExplorerStore(AtomicStoreFile storeFile, LockManager lockManager) {
        this(storeFile, lockManager, Clock.systemUTC());
    }

    /**
     * @param base the base directory of the runtime
     * @return the store file with the key of the explorer lock
     */
    public static AtomicStoreFile file(Path base) {
        var root = Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
        return new AtomicStoreFile(root.resolve(FILE), LockKey.explorer(base));
    }

    /**
     * Reads the favourites, the activity and the document's revision without a lock.
     *
     * @return the snapshot; empty lists and {@value #NO_DOCUMENT} if the document does not exist
     * @throws StoreRefusal         if the document is unreadable or in another format version
     * @throws UncheckedIOException if the document exists and cannot be read
     */
    public Snapshot read() {
        return StoreSnapshot.read(storeFile.store()).content()
                .map(document -> ExplorerCodec.read(storeFile.store(), document))
                .orElseGet(() -> new Snapshot(List.of(), Map.of(), NO_DOCUMENT));
    }

    /**
     * Writes the operator's favourites in their order, dropping a favourite of a project that is no longer enrolled.
     *
     * @param favourites the project ids in the operator's order
     * @param enrolled   the projects enrolled on the machine now
     * @param revision   the revision the caller read
     * @throws IllegalArgumentException if a favourite is not a project id or names a project twice
     * @throws StoreRefusal             as {@link StoreRefusal.Outcome#CONFLICT conflict} if the document holds another
     *                                  revision than {@code revision}; if the document is unreadable or has another
     *                                  format version
     * @throws UncheckedIOException     if the document cannot be read or written
     * @throws InternalFaultException   if the lock of the document must not be acquired by the transaction
     */
    public void setFavourites(List<String> favourites, Set<String> enrolled, long revision) {
        Objects.requireNonNull(favourites, "favourites");
        Objects.requireNonNull(enrolled, "enrolled");
        var unique = new LinkedHashSet<String>();
        for (var projectId : favourites) {
            requireProjectId(projectId);
            if (!unique.add(projectId)) {
                throw new IllegalArgumentException("A favourite names a project once: " + projectId);
            }
        }
        var kept = unique.stream().filter(enrolled::contains).toList();
        mutate(revision, current -> new Snapshot(kept, prune(current.activity(), enrolled, today()), current.revision()));
    }

    /**
     * Records one act of the operator in a project on the UTC day of the store's clock.
     *
     * @param projectId the project the act happened in
     * @param enrolled  the projects enrolled on the machine now
     * @param revision  the revision the caller read
     * @throws IllegalArgumentException if the project is no valid project id or is no longer enrolled
     * @throws StoreRefusal             as {@link StoreRefusal.Outcome#CONFLICT conflict} if the document holds another
     *                                  revision than {@code revision}
     * @throws UncheckedIOException     if the document cannot be read or written
     */
    public void recordActivity(String projectId, Set<String> enrolled, long revision) {
        requireProjectId(projectId);
        Objects.requireNonNull(enrolled, "enrolled");
        if (!enrolled.contains(projectId)) {
            throw new IllegalArgumentException("An act is recorded for an enrolled project: " + projectId);
        }
        var now = clock.instant();
        var day = LocalDate.ofInstant(now, ZoneOffset.UTC).toString();
        mutate(revision, current -> {
            var activity = new LinkedHashMap<>(prune(current.activity(), enrolled, today()));
            var previous = activity.get(projectId);
            var days = new LinkedHashMap<String, Integer>();
            if (previous != null) {
                days.putAll(previous.days());
            }
            days.merge(day, 1, Integer::sum);
            activity.put(projectId, new Activity(now, days));
            return new Snapshot(current.favourites(), activity, current.revision());
        });
    }

    /** @return the document file, absolute and normalized */
    public Path store() {
        return storeFile.store();
    }

    private void mutate(long revision, Change change) {
        try (var transaction = lockManager.openTransaction()) {
            transaction.acquire(storeFile.lockKey());
            var current = storeFile.read(transaction)
                    .map(document -> ExplorerCodec.read(storeFile.store(), document))
                    .orElseGet(() -> new Snapshot(List.of(), Map.of(), NO_DOCUMENT));
            if (current.revision() != revision) {
                throw StoreRefusal.conflict(storeFile.store(), revision, current.revision());
            }
            var next = change.apply(current);
            storeFile.write(transaction, ExplorerCodec.write(storeFile.store(),
                    new Snapshot(next.favourites(), next.activity(), revision + 1)));
        }
    }

    /** One change of the document; answers the document to write. */
    @FunctionalInterface
    private interface Change {

        /**
         * @param current the document as the lock reads it
         * @return the document to write
         */
        Snapshot apply(Snapshot current);
    }

    /**
     * Drops the entries of projects that are no longer enrolled and the days older than the window.
     */
    private static Map<String, Activity> prune(Map<String, Activity> activity, Set<String> enrolled,
            LocalDate today) {
        var oldest = today.minusDays(ACTIVITY_WINDOW_DAYS - 1L).toString();
        var kept = new LinkedHashMap<String, Activity>();
        for (var entry : activity.entrySet()) {
            if (!enrolled.contains(entry.getKey())) {
                continue;
            }
            var days = new LinkedHashMap<String, Integer>();
            for (var day : entry.getValue().days().entrySet()) {
                if (day.getKey().compareTo(oldest) >= 0) {
                    days.put(day.getKey(), day.getValue());
                }
            }
            if (!days.isEmpty()) {
                kept.put(entry.getKey(), new Activity(entry.getValue().lastAt(), days));
            }
        }
        return kept;
    }

    private LocalDate today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static void requireProjectId(String projectId) {
        Objects.requireNonNull(projectId, PROJECT_ID);
        if (!LockKey.isIdentifier(projectId)) {
            throw new IllegalArgumentException("Not a valid project id of the operator's projects: " + projectId);
        }
    }
}
