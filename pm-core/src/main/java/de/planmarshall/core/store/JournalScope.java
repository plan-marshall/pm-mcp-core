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
import java.util.List;
import java.util.Objects;

/**
 * A scope that writes its stores through a transaction journal (PM-IMPL-7): where its journal and its digest sidecar
 * lie, which directories its staged files may lie in, which key guards it, and the path base its store paths are
 * relative to.
 * <p>
 * Every scope lies in the per-project machine store {@code projects/<project_id>/} below the base directory of the
 * runtime:
 * <ul>
 * <li>a plan: path base {@code plans/<plan_id>/}, journal {@code mcp/transaction-journal.json}, sidecar
 * {@code mcp/digests.json}, guarded by the plan lock;</li>
 * <li>an epic: path base {@code epics/<epic_id>/}, journal {@code transaction-journal.json}, sidecar
 * {@code digests.json}, guarded by the epic lock;</li>
 * <li>the workspace: path base the project store root itself, journal {@code workspace/mcp/transaction-journal.json},
 * sidecar {@code workspace/mcp/digests.json}, staged files only below {@code workspace/} and {@code pending/},
 * guarded by the workspace lock;</li>
 * <li>the lesson store: path base {@code lessons/}, journal {@code .transaction-journal.json}, sidecar
 * {@code .digests.json}, guarded by the lesson store lock.</li>
 * </ul>
 * The owned directories of the workspace exclude {@code plans/} and {@code epics/}, although they lie below its path
 * base: a recovery of the workspace never touches a staged file of a plan or an epic.
 *
 * @param type             the type of the scope
 * @param scopeId          the plan or epic id; {@code null} for the workspace and the lesson store
 * @param pathBase         the directory the store paths of the scope are relative to; absolute and normalized
 * @param journalFile      the transaction journal of the scope
 * @param sidecarFile      the digest sidecar of the scope
 * @param ownedDirectories the directories, at any depth below which the staged files of the scope may lie
 * @param lockKey          the key that guards every store of the scope
 * @since 0.1
 */
public record JournalScope(JournalScopeType type, String scopeId, Path pathBase, Path journalFile, Path sidecarFile,
        List<Path> ownedDirectories, LockKey lockKey) {

    private static final String MCP = "mcp";
    private static final String JOURNAL = "transaction-journal.json";
    private static final String SIDECAR = "digests.json";

    /**
     * @throws NullPointerException     if a component other than the scope id is {@code null}
     * @throws IllegalArgumentException if the scope id is missing for a type that requires one or present for a type
     *                                  that has none, if a path is not absolute and normalized, if the journal, the
     *                                  sidecar or an owned directory lies outside the path base, or if no directory
     *                                  is owned
     */
    public JournalScope {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(lockKey, "lockKey");
        if (type.scopeIdRequired() != (scopeId != null)) {
            throw new IllegalArgumentException("A %s scope %s a scope id: %s".formatted(type.wireName(),
                    type.scopeIdRequired() ? "requires" : "has no", scopeId));
        }
        requireNormalized(pathBase, "pathBase");
        requireBelow(pathBase, journalFile, "journalFile");
        requireBelow(pathBase, sidecarFile, "sidecarFile");
        ownedDirectories = List.copyOf(ownedDirectories);
        if (ownedDirectories.isEmpty()) {
            throw new IllegalArgumentException("A scope owns at least one directory: " + pathBase);
        }
        for (var owned : ownedDirectories) {
            requireNormalized(owned, "ownedDirectories");
            if (!owned.startsWith(pathBase)) {
                throw new IllegalArgumentException("Owned directory %s lies outside %s".formatted(owned, pathBase));
            }
        }
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @param planId    the plan
     * @return the scope of the plan
     */
    public static JournalScope plan(Path base, String projectId, String planId) {
        var lockKey = LockKey.plan(base, projectId, planId);
        var pathBase = LockKey.projectStore(base, projectId).resolve("plans").resolve(planId);
        return new JournalScope(JournalScopeType.PLAN, planId, pathBase, pathBase.resolve(MCP).resolve(JOURNAL),
                pathBase.resolve(MCP).resolve(SIDECAR), List.of(pathBase), lockKey);
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @param epicId    the epic
     * @return the scope of the epic
     */
    public static JournalScope epic(Path base, String projectId, String epicId) {
        var lockKey = LockKey.epic(base, projectId, epicId);
        var pathBase = LockKey.projectStore(base, projectId).resolve("epics").resolve(epicId);
        return new JournalScope(JournalScopeType.EPIC, epicId, pathBase, pathBase.resolve(JOURNAL),
                pathBase.resolve(SIDECAR), List.of(pathBase), lockKey);
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the workspace scope of the project
     */
    public static JournalScope workspace(Path base, String projectId) {
        var pathBase = LockKey.projectStore(base, projectId);
        var workspace = pathBase.resolve("workspace");
        return new JournalScope(JournalScopeType.WORKSPACE, null, pathBase, workspace.resolve(MCP).resolve(JOURNAL),
                workspace.resolve(MCP).resolve(SIDECAR), List.of(workspace, pathBase.resolve("pending")),
                LockKey.workspace(base, projectId));
    }

    /**
     * @param base      the base directory of the runtime
     * @param projectId the project
     * @return the scope of the project's lesson store
     */
    public static JournalScope lessons(Path base, String projectId) {
        var pathBase = LockKey.projectStore(base, projectId).resolve("lessons");
        return new JournalScope(JournalScopeType.LESSONS, null, pathBase, pathBase.resolve("." + JOURNAL),
                pathBase.resolve("." + SIDECAR), List.of(pathBase), LockKey.lessonStore(base, projectId));
    }

    /**
     * Resolves a store path of the scope, as a journal and a sidecar name it.
     *
     * @param relativePath a path relative to the path base
     * @return the store file, absolute and normalized
     * @throws IllegalArgumentException if the path is absolute, empty, or does not lie below the path base
     */
    public Path resolve(String relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        var relative = Path.of(relativePath);
        var resolved = pathBase.resolve(relative).normalize();
        if (relative.isAbsolute() || resolved.equals(pathBase) || !resolved.startsWith(pathBase)) {
            throw new IllegalArgumentException(
                    "Store path '%s' does not lie below the path base %s".formatted(relativePath, pathBase));
        }
        return resolved;
    }

    private static void requireNormalized(Path path, String name) {
        Objects.requireNonNull(path, name);
        if (!path.isAbsolute() || !path.equals(path.normalize())) {
            throw new IllegalArgumentException("%s must be an absolute, normalized path: %s".formatted(name, path));
        }
    }

    private static void requireBelow(Path pathBase, Path file, String name) {
        requireNormalized(file, name);
        if (file.equals(pathBase) || !file.startsWith(pathBase)) {
            throw new IllegalArgumentException("%s %s lies outside %s".formatted(name, file, pathBase));
        }
    }
}
