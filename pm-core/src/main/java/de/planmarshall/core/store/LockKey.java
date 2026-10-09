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

import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The canonical key of one transaction lock (PM-IMPL-7): its level in the total lock order, the file the lock is
 * taken on, and the id that orders keys within a level. One factory exists per lock of the runtime; each takes the
 * instance directory of the runtime (its base directory) and the identifiers of the lock and returns the fixed lock
 * path.
 * <p>
 * A store that is replaced by atomic rename is never locked itself, because the rename replaces the inode and a lock
 * on the old file excludes nobody; its key names a sibling lock file. The locks of a project lie below
 * {@code projects/<project_id>/locks/} and those of the machine stores below {@code locks/}, outside the directories
 * they protect, so archiving a plan directory or working on an epic in a worktree never splits a lock. Only the two
 * leaf keys name the guarded file: an append-only file is never replaced by rename.
 * <p>
 * Keys are ordered by level, then by {@link #id()}, then by lock path. The id is the plan id of a plan lock and the
 * epic id of an epic lock, so several of them sort lexicographically by that id. The id of a machine store lock is
 * its lock name below {@code locks/} without the extension, which sorts the machine store locks alphabetically by
 * lock path and the enrolment locks lexicographically by project id; comparing the paths themselves would sort
 * {@code ab.lock} after {@code ab-c.lock}. The lock path as last criterion makes the order total across projects and
 * consistent with {@link #equals(Object)}.
 *
 * @param level    the level of the lock in the total order
 * @param lockFile the absolute, normalized path of the file the lock is taken on
 * @param id       what orders this key among the keys of its level
 * @since 0.1
 */
public record LockKey(LockLevel level, Path lockFile, String id) implements Comparable<LockKey> {

    /** The grammar of a project, plan and epic identifier. */
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z0-9][a-z0-9-]{1,62}[a-z0-9]");

    private static final Comparator<LockKey> TOTAL_ORDER = Comparator.comparing(LockKey::level)
            .thenComparing(LockKey::id)
            .thenComparing(key -> key.lockFile().toString());

    private static final String LOCK_DIRECTORY = "locks";
    private static final String LOCK_SUFFIX = ".lock";
    private static final String ENROLMENTS = "enrolments";

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the lock file is not an absolute, normalized path
     */
    public LockKey {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(lockFile, "lockFile");
        Objects.requireNonNull(id, "id");
        if (!lockFile.isAbsolute() || !lockFile.equals(lockFile.normalize())) {
            throw new IllegalArgumentException("Lock file must be an absolute, normalized path: " + lockFile);
        }
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the key of the lock of the project's workspace scope
     */
    public static LockKey workspace(Path base, String projectId) {
        return ofProject(LockLevel.WORKSPACE, base, projectId, "workspace");
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @param planId    the plan
     * @return the key of the lock of the plan
     */
    public static LockKey plan(Path base, String projectId, String planId) {
        return new LockKey(LockLevel.PLAN,
                projectLocks(base, projectId).resolve("plan-" + identifier(planId, "planId") + LOCK_SUFFIX), planId);
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @param epicId    the epic
     * @return the key of the lock of the epic
     */
    public static LockKey epic(Path base, String projectId, String epicId) {
        return new LockKey(LockLevel.EPIC,
                projectLocks(base, projectId).resolve("epic-" + identifier(epicId, "epicId") + LOCK_SUFFIX), epicId);
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the key of the lock of the git operations on the project's shared epic worktree
     */
    public static LockKey epicsWorktree(Path base, String projectId) {
        return ofProject(LockLevel.EPICS_WORKTREE, base, projectId, "epics-worktree");
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the key of the lock of the project's lesson store
     */
    public static LockKey lessonStore(Path base, String projectId) {
        return ofProject(LockLevel.LESSON_STORE, base, projectId, "lessons");
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the key of the lock of the project's local configuration
     */
    public static LockKey localConfiguration(Path base, String projectId) {
        return ofProject(LockLevel.LOCAL_CONFIGURATION, base, projectId, "local-config");
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the key of the lock of the project's derived facts
     */
    public static LockKey derivedFacts(Path base, String projectId) {
        return ofProject(LockLevel.DERIVED_FACTS, base, projectId, "derived-facts");
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the key of the lock of the project's build timings
     */
    public static LockKey buildTimings(Path base, String projectId) {
        return ofProject(LockLevel.BUILD_TIMINGS, base, projectId, "build-timings");
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the key of the lock of the project's task queue, its session generation record and its worker job
     *         records
     */
    public static LockKey queue(Path base, String projectId) {
        return ofProject(LockLevel.QUEUE, base, projectId, "queue");
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the build slot leases
     */
    public static LockKey buildSlots(Path base) {
        return ofMachineStore(base, "build-slots");
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the fallback credential files
     */
    public static LockKey credentials(Path base) {
        return ofMachineStore(base, "credentials");
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the key of the lock of the project's enrolment file and its sidecar
     */
    public static LockKey enrolment(Path base, String projectId) {
        var id = ENROLMENTS + "/" + identifier(projectId, "projectId");
        return new LockKey(LockLevel.MACHINE_STORE,
                machineLocks(base).resolve(ENROLMENTS).resolve(projectId + LOCK_SUFFIX), id);
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the harness enrolments and their sidecar
     */
    public static LockKey harnesses(Path base) {
        return ofMachineStore(base, "harnesses");
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the client install records
     */
    public static LockKey installRecords(Path base) {
        return ofMachineStore(base, "install-records");
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the machine configuration
     */
    public static LockKey machineConfiguration(Path base) {
        return ofMachineStore(base, "machine-config");
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the merge queue
     */
    public static LockKey mergeQueue(Path base) {
        return ofMachineStore(base, "merge");
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the model slot leases
     */
    public static LockKey modelSlots(Path base) {
        return ofMachineStore(base, "model-slots");
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the review-bot rate windows
     */
    public static LockKey reviewBotWindows(Path base) {
        return ofMachineStore(base, "review-bot");
    }

    /**
     * @param base the base directory of the runtime
     * @return the key of the lock of the paired web devices
     */
    public static LockKey webDevices(Path base) {
        return ofMachineStore(base, "web-devices");
    }

    /**
     * @param base the base directory of the runtime
     * @return the leaf key of the audit log, which is taken on the append-only log itself
     */
    public static LockKey auditLog(Path base) {
        return new LockKey(LockLevel.LEAF, canonical(base).resolve("logs").resolve("audit.jsonl"), "audit");
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @param planId    the plan
     * @return the leaf key of the plan's mailbox, which is taken on the append-only mailbox itself
     */
    public static LockKey mailbox(Path base, String projectId, String planId) {
        var mailbox = projectStore(base, projectId).resolve("plans").resolve(identifier(planId, "planId"))
                .resolve("mcp").resolve("mailbox.jsonl");
        return new LockKey(LockLevel.LEAF, mailbox, planId);
    }

    /**
     * Compares in the total lock order: the level first, then the id, then the lock path.
     */
    @Override
    public int compareTo(LockKey other) {
        return TOTAL_ORDER.compare(this, other);
    }

    private static LockKey ofProject(LockLevel level, Path base, String projectId, String name) {
        return new LockKey(level, projectLocks(base, projectId).resolve(name + LOCK_SUFFIX), projectId);
    }

    private static LockKey ofMachineStore(Path base, String name) {
        return new LockKey(LockLevel.MACHINE_STORE, machineLocks(base).resolve(name + LOCK_SUFFIX), name);
    }

    private static Path projectLocks(Path base, String projectId) {
        return projectStore(base, projectId).resolve(LOCK_DIRECTORY);
    }

    private static Path projectStore(Path base, String projectId) {
        return canonical(base).resolve("projects").resolve(identifier(projectId, "projectId"));
    }

    private static Path machineLocks(Path base) {
        return canonical(base).resolve(LOCK_DIRECTORY);
    }

    private static Path canonical(Path base) {
        return Objects.requireNonNull(base, "base").toAbsolutePath().normalize();
    }

    /** An identifier becomes a path segment, so one outside the grammar is refused before it reaches a path. */
    private static String identifier(String value, String name) {
        Objects.requireNonNull(value, name);
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a valid identifier for " + name + ": " + value);
        }
        return value;
    }
}
