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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.random.RandomGenerator;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.JobRecord.Execution;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;

/**
 * The job records of one store (PM-EXEC-4, PM-ARCH-7): the {@code jobs/} of a project's machine store
 * ({@code projects/<project_id>/jobs/}), of the machine scope ({@code machine/jobs/}) or of an onboarding entry
 * ({@code onboardings/<entry_id>/jobs/}), one record {@code <job_id>.json} per job with its raw log
 * {@code <job_id>.log} beside it.
 * <p>
 * A record is written at fixed points, each one atomic replace ({@link AtomicStoreFile}) under the lock of the scope
 * that owns the job, which the method acquires in the caller's transaction: the plan or epic lock, the workspace lock
 * for a job without a plan or epic, the project's queue lock for a worker, the machine queue lock for a job of the
 * machine scope, and the entry lock for a probe of an onboarding entry. The points are
 * <ol>
 * <li>{@link #writeQueued queued}: a job that waits for a slot, not spawned;</li>
 * <li>{@link #writeAhead write-ahead}: {@code running} with neither PID nor process start, before the process is
 * started, or before an in-process operation starts;</li>
 * <li>{@link #writePostSpawn post-spawn}: the PID and the process start of a process job, before the job counts as
 * running (PM-ARCH-7, bullet 8);</li>
 * <li>{@link #writeFate fate}: the settled record.</li>
 * </ol>
 * Each point checks the record it replaces, so a record moves only forward through them.
 * <p>
 * Allocating an id is a check-then-act on {@code jobs/}: the id is drawn at random, checked against the records and
 * logs present and drawn again on a collision, under the lock of the owning scope. The caller writes the first record
 * of the job in the same transaction, so no second writer of that scope comes between the check and the write.
 * <p>
 * A read takes no lock; a caller that decides on what it read holds the lock of the owning scope. The listing names
 * only the files whose name parses as {@code <job_id>.json}. The directory {@code jobs/} is created by the first write.
 *
 * @since 0.1
 */
public final class JobRecordStore {

    /** The directory of the job records below the home of their store. */
    public static final String DIRECTORY = "jobs";

    private static final Set<Scope.Type> PROJECT_SCOPES =
            Set.of(Scope.Type.PLAN, Scope.Type.EPIC, Scope.Type.WORKSPACE, Scope.Type.PROJECT);

    private final Path base;
    private final Home home;
    private final String homeId;
    private final Path directory;

    /** The home a store of job records lies in. */
    private enum Home {
        PROJECT, MACHINE, ENTRY
    }

    private JobRecordStore(Path base, Home home, String homeId, Path directory) {
        this.base = base;
        this.home = home;
        this.homeId = homeId;
        this.directory = directory;
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the job records of the project, {@code projects/<project_id>/jobs/}
     * @throws IllegalArgumentException if the project id is not a valid identifier
     */
    public static JobRecordStore ofProject(Path base, String projectId) {
        return new JobRecordStore(canonical(base), Home.PROJECT, projectId,
                LockKey.projectStore(base, projectId).resolve(DIRECTORY));
    }

    /**
     * @param base the base directory of the runtime
     * @return the job records of the machine scope, {@code machine/jobs/}
     */
    public static JobRecordStore ofMachine(Path base) {
        var canonical = canonical(base);
        return new JobRecordStore(canonical, Home.MACHINE, null, canonical.resolve("machine").resolve(DIRECTORY));
    }

    /**
     * @param base    the base directory of the runtime
     * @param entryId the onboarding entry
     * @return the job records of the entry, {@code onboardings/<entry_id>/jobs/}
     * @throws IllegalArgumentException if the entry id is not a valid identifier
     */
    public static JobRecordStore ofEntry(Path base, String entryId) {
        var canonical = canonical(base);
        return new JobRecordStore(canonical, Home.ENTRY, entryId, canonical.resolve("onboardings")
                .resolve(LockKey.identifier(entryId, "entryId")).resolve(DIRECTORY));
    }

    /** @return the directory {@code jobs/}, absolute and normalized */
    public Path directoryPath() {
        return directory;
    }

    /**
     * @param jobId a job
     * @return the record file of the job, absolute and normalized
     */
    public Path recordPath(JobId jobId) {
        return directory.resolve(Objects.requireNonNull(jobId, "jobId").recordFileName());
    }

    /**
     * @param jobId a job
     * @return the raw log of the job beside its record, absolute and normalized
     */
    public Path logPath(JobId jobId) {
        return directory.resolve(Objects.requireNonNull(jobId, "jobId").logFileName());
    }

    /**
     * @param scope the scope that owns a job of this store
     * @return the key of the lock every write of the job's record is made under
     * @throws IllegalArgumentException if a job of the scope does not belong to this store
     */
    public LockKey lockKey(Scope scope) {
        requireOwnScope(scope);
        return switch (scope.type()) {
            case PLAN -> LockKey.plan(base, homeId, scope.id());
            case EPIC -> LockKey.epic(base, homeId, scope.id());
            case WORKSPACE -> LockKey.workspace(base, homeId);
            case PROJECT -> LockKey.queue(base, homeId);
            case MACHINE -> LockKey.machineQueue(base);
            case ENTRY -> LockKey.onboardingEntry(base, homeId);
        };
    }

    /**
     * Draws a fresh job id: one that names no record and no log present. The draw is repeated on a collision. The
     * lock of the owning scope is acquired in the caller's transaction and stays held, so the caller writes the job's
     * first record under the same lock.
     *
     * @param transaction the caller's transaction
     * @param kind        the kind of the job
     * @param scope       the scope that owns the job
     * @return a free job id of the kind
     * @throws IllegalArgumentException if a job of the scope does not belong to this store
     * @throws InternalFaultException   if the lock must not be acquired after the locks the transaction holds
     */
    public JobId allocate(LockTransaction transaction, JobId.Kind kind, Scope scope) {
        return allocate(transaction, kind, scope, null);
    }

    /**
     * @param random the source of the drawn digits; {@code null} for the secure default
     * @see #allocate(LockTransaction, JobId.Kind, Scope)
     */
    JobId allocate(LockTransaction transaction, JobId.Kind kind, Scope scope, RandomGenerator random) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(kind, "kind");
        transaction.acquire(lockKey(scope));
        JobId candidate;
        do {
            candidate = random == null ? JobId.random(kind) : JobId.random(kind, random);
        } while (exists(recordPath(candidate)) || exists(logPath(candidate)));
        return candidate;
    }

    /**
     * Writes the first record of a job that waits for a slot.
     *
     * @param transaction the caller's transaction; the lock of the owning scope is acquired in it
     * @param job         the record, {@code queued}
     * @throws IllegalArgumentException if the record is not queued, a record of the job exists, or a job of its scope
     *                                  does not belong to this store
     * @throws StoreRefusal             if a record of the job exists and is unreadable
     * @throws UncheckedIOException     if the record cannot be written
     * @throws InternalFaultException   if the lock must not be acquired after the locks the transaction holds
     */
    public void writeQueued(LockTransaction transaction, JobRecord job) {
        requireStatus(job, Status.QUEUED);
        var current = currentUnderLock(transaction, job);
        if (current.isPresent()) {
            throw new IllegalArgumentException("The job record %s exists already".formatted(job.jobId()));
        }
        write(transaction, job);
    }

    /**
     * Writes the write-ahead record before the job's process is started, or before its in-process operation starts:
     * {@code running} with neither PID nor process start. It is the first record of the job, or it replaces the
     * queued record of the job that acquired its slot.
     *
     * @param transaction the caller's transaction; the lock of the owning scope is acquired in it
     * @param job         the record, {@code running} without a PID
     * @throws IllegalArgumentException if the record is not running or carries a PID, the record it replaces is not
     *                                  the queued record of the job, or a job of its scope does not belong to this
     *                                  store
     * @throws StoreRefusal             if a record of the job exists and is unreadable
     * @throws UncheckedIOException     if the record cannot be written
     * @throws InternalFaultException   if the lock must not be acquired after the locks the transaction holds
     */
    public void writeAhead(LockTransaction transaction, JobRecord job) {
        requireStatus(job, Status.RUNNING);
        if (job.isSpawnRecorded()) {
            throw new IllegalArgumentException("A write-ahead record carries no PID: " + job.jobId());
        }
        currentUnderLock(transaction, job).ifPresent(current -> requireReplaces(job, current, Status.QUEUED));
        write(transaction, job);
    }

    /**
     * Writes the post-spawn record of a process job: the PID and the process start the launcher reported, and the
     * spawn instant. Only from this record on does the job count as running.
     *
     * @param transaction the caller's transaction; the lock of the owning scope is acquired in it
     * @param job         the record, {@code running} with PID, process start and spawn instant
     * @throws IllegalArgumentException if the record is not that of a running process job with its PID, process start
     *                                  and spawn instant, the record it replaces is not the write-ahead record of the
     *                                  job, or a job of its scope does not belong to this store
     * @throws StoreRefusal             if the record of the job is unreadable
     * @throws UncheckedIOException     if the record cannot be written
     * @throws InternalFaultException   if the lock must not be acquired after the locks the transaction holds
     */
    public void writePostSpawn(LockTransaction transaction, JobRecord job) {
        requireStatus(job, Status.RUNNING);
        if (job.execution() != Execution.PROCESS || !job.isSpawnRecorded() || job.spawnedAt() == null) {
            throw new IllegalArgumentException(
                    "A post-spawn record is that of a process job with PID, process start and spawn instant: "
                            + job.jobId());
        }
        var current = currentUnderLock(transaction, job).orElseThrow(() -> new IllegalArgumentException(
                "The job %s has no write-ahead record".formatted(job.jobId())));
        requireReplaces(job, current, Status.RUNNING);
        if (current.isSpawnRecorded()) {
            throw new IllegalArgumentException("The job %s has its post-spawn record already".formatted(job.jobId()));
        }
        write(transaction, job);
    }

    /**
     * Writes the settled record of a job. It replaces the record of a job that is queued or running, or it is the
     * only record of a job refused before anything was written.
     *
     * @param transaction the caller's transaction; the lock of the owning scope is acquired in it
     * @param job         the record, {@code refused} or {@code ended}
     * @throws IllegalArgumentException if the record is not settled, the record it replaces is settled already or not
     *                                  of the job, or a job of its scope does not belong to this store
     * @throws StoreRefusal             if a record of the job exists and is unreadable
     * @throws UncheckedIOException     if the record cannot be written
     * @throws InternalFaultException   if the lock must not be acquired after the locks the transaction holds
     */
    public void writeFate(LockTransaction transaction, JobRecord job) {
        Objects.requireNonNull(job, "job");
        if (!job.status().isSettled()) {
            throw new IllegalArgumentException("A fate record is settled, not %s: %s".formatted(
                    job.status().wireName(), job.jobId()));
        }
        currentUnderLock(transaction, job).ifPresent(current -> requireReplaces(job, current, Status.QUEUED,
                Status.RUNNING));
        write(transaction, job);
    }

    /**
     * Reads a record without a lock. A record is readable only if the {@code job_id} it states is the id its file name
     * carries.
     *
     * @param jobId the job
     * @return the record; empty if it does not exist
     * @throws StoreRefusal         if the record is in another format version, unreadable, or states another
     *                              {@code job_id} than the one of its file name
     * @throws UncheckedIOException if the record exists and cannot be read
     */
    public Optional<JobRecord> read(JobId jobId) {
        var file = recordPath(jobId);
        return StoreSnapshot.read(file).content().map(document -> {
            var job = JobRecordCodec.read(file, document);
            if (!job.jobId().equals(jobId)) {
                throw StoreRefusal.unreadable(file, JobRecord.FORMAT_VERSION,
                        "'job_id' is '%s', not the '%s' of the file name".formatted(job.jobId(), jobId), null);
            }
            return job;
        });
    }

    /**
     * Lists the jobs without a lock: every file {@code <job_id>.json} of {@code jobs/} whose name parses as a job id.
     * A log, a staged file of a write and every other name is not a job.
     *
     * @return the jobs, sorted by id; empty if the directory does not exist
     * @throws UncheckedIOException if the directory exists and cannot be listed
     */
    public SortedSet<JobId> jobIds() {
        var ids = new TreeSet<>(Comparator.comparing(JobId::value));
        try (var entries = Files.list(directory)) {
            entries.filter(entry -> Files.isRegularFile(entry, LinkOption.NOFOLLOW_LINKS))
                    .map(entry -> JobId.ofRecordFileName(entry.getFileName().toString()))
                    .flatMap(Optional::stream).forEach(ids::add);
        } catch (NoSuchFileException _) {
            return Collections.emptySortedSet();
        } catch (IOException e) {
            throw new UncheckedIOException("Job records cannot be listed: " + directory, e);
        }
        return Collections.unmodifiableSortedSet(ids);
    }

    /**
     * Reads every record of the store without a lock, as the recovery after a runtime start does.
     *
     * @return the records, sorted by job id
     * @throws StoreRefusal         if a record is in another format version or unreadable
     * @throws UncheckedIOException if the directory or a record cannot be read
     */
    public List<JobRecord> records() {
        return jobIds().stream().map(this::read).flatMap(Optional::stream).toList();
    }

    private Optional<JobRecord> currentUnderLock(LockTransaction transaction, JobRecord job) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(job, "job");
        transaction.acquire(lockKey(job.scope()));
        return read(job.jobId());
    }

    private void write(LockTransaction transaction, JobRecord job) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Job record directory cannot be created: " + directory, e);
        }
        new AtomicStoreFile(recordPath(job.jobId()), lockKey(job.scope())).write(transaction,
                JobRecordCodec.write(job));
    }

    private void requireOwnScope(Scope scope) {
        Objects.requireNonNull(scope, "scope");
        var own = switch (home) {
            case PROJECT -> PROJECT_SCOPES.contains(scope.type());
            case MACHINE -> scope.type() == Scope.Type.MACHINE;
            case ENTRY -> scope.type() == Scope.Type.ENTRY && homeId.equals(scope.id());
        };
        if (!own) {
            throw new IllegalArgumentException("A job of the scope %s %s does not belong to the store %s".formatted(
                    scope.type().wireName(), Objects.toString(scope.id(), ""), directory));
        }
    }

    private static void requireStatus(JobRecord job, Status status) {
        Objects.requireNonNull(job, "job");
        if (job.status() != status) {
            throw new IllegalArgumentException("The record of job %s is %s, not %s".formatted(job.jobId(),
                    job.status().wireName(), status.wireName()));
        }
    }

    private static void requireReplaces(JobRecord job, JobRecord current, Status... replaceable) {
        if (!current.scope().equals(job.scope()) || current.execution() != job.execution()) {
            throw new IllegalArgumentException(
                    "The record of job %s changes its scope or execution".formatted(job.jobId()));
        }
        if (!Set.of(replaceable).contains(current.status())) {
            throw new IllegalArgumentException("The record of job %s is %s and is not replaced by a %s record"
                    .formatted(job.jobId(), current.status().wireName(), job.status().wireName()));
        }
    }

    private static boolean exists(Path file) {
        return Files.exists(file, LinkOption.NOFOLLOW_LINKS);
    }

    private static Path canonical(Path base) {
        return Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
    }
}
