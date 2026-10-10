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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The scopes that write through a transaction journal (PM-IMPL-7): the documented layout of each below the
 * per-project machine store, its scope id and its lock key.
 */
@DisplayName("Journal scope")
class JournalScopeTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final String PLAN = "store-substrate";
    private static final String EPIC = "store-epic";

    @TempDir
    Path base;

    private Path projectStore() {
        return base.toAbsolutePath().normalize().resolve("projects").resolve(PROJECT);
    }

    @Nested
    @DisplayName("layout")
    class Layout {

        @Test
        @DisplayName("a plan lies at plans/<plan_id>/ with its journal and sidecar in mcp/, under the plan lock")
        void plan() {
            var pathBase = projectStore().resolve("plans").resolve(PLAN);

            var scope = JournalScope.plan(base, PROJECT, PLAN);

            assertAll(
                    () -> assertEquals(JournalScopeType.PLAN, scope.type()),
                    () -> assertEquals(PLAN, scope.scopeId()),
                    () -> assertEquals(pathBase, scope.pathBase()),
                    () -> assertEquals(pathBase.resolve("mcp/transaction-journal.json"), scope.journalFile()),
                    () -> assertEquals(pathBase.resolve("mcp/digests.json"), scope.sidecarFile()),
                    () -> assertEquals(List.of(pathBase), scope.ownedDirectories()),
                    () -> assertEquals(LockKey.plan(base, PROJECT, PLAN), scope.lockKey()));
        }

        @Test
        @DisplayName("an epic lies at epics/<epic_id>/ with its journal and sidecar at its top, under the epic lock")
        void epic() {
            var pathBase = projectStore().resolve("epics").resolve(EPIC);

            var scope = JournalScope.epic(base, PROJECT, EPIC);

            assertAll(
                    () -> assertEquals(JournalScopeType.EPIC, scope.type()),
                    () -> assertEquals(EPIC, scope.scopeId()),
                    () -> assertEquals(pathBase, scope.pathBase()),
                    () -> assertEquals(pathBase.resolve("transaction-journal.json"), scope.journalFile()),
                    () -> assertEquals(pathBase.resolve("digests.json"), scope.sidecarFile()),
                    () -> assertEquals(List.of(pathBase), scope.ownedDirectories()),
                    () -> assertEquals(LockKey.epic(base, PROJECT, EPIC), scope.lockKey()));
        }

        @Test
        @DisplayName("the workspace has the project store as path base and owns workspace/ and pending/ only")
        void workspace() {
            var pathBase = projectStore();

            var scope = JournalScope.workspace(base, PROJECT);

            assertAll(
                    () -> assertEquals(JournalScopeType.WORKSPACE, scope.type()),
                    () -> assertNull(scope.scopeId()),
                    () -> assertEquals(pathBase, scope.pathBase()),
                    () -> assertEquals(pathBase.resolve("workspace/mcp/transaction-journal.json"),
                            scope.journalFile()),
                    () -> assertEquals(pathBase.resolve("workspace/mcp/digests.json"), scope.sidecarFile()),
                    () -> assertEquals(List.of(pathBase.resolve("workspace"), pathBase.resolve("pending")),
                            scope.ownedDirectories()),
                    () -> assertFalse(scope.ownedDirectories().stream()
                            .anyMatch(owned -> pathBase.resolve("plans").startsWith(owned)),
                            "the workspace owns no directory of a plan"),
                    () -> assertEquals(LockKey.workspace(base, PROJECT), scope.lockKey()));
        }

        @Test
        @DisplayName("the lesson store lies at lessons/ with a hidden journal and sidecar, under the lesson store lock")
        void lessons() {
            var pathBase = projectStore().resolve("lessons");

            var scope = JournalScope.lessons(base, PROJECT);

            assertAll(
                    () -> assertEquals(JournalScopeType.LESSONS, scope.type()),
                    () -> assertNull(scope.scopeId()),
                    () -> assertEquals(pathBase, scope.pathBase()),
                    () -> assertEquals(pathBase.resolve(".transaction-journal.json"), scope.journalFile()),
                    () -> assertEquals(pathBase.resolve(".digests.json"), scope.sidecarFile()),
                    () -> assertEquals(List.of(pathBase), scope.ownedDirectories()),
                    () -> assertEquals(LockKey.lessonStore(base, PROJECT), scope.lockKey()));
        }
    }

    @Nested
    @DisplayName("scope id")
    class ScopeId {

        @ParameterizedTest
        @EnumSource(value = JournalScopeType.class, names = {"PLAN", "EPIC"})
        @DisplayName("a plan and an epic require a scope id")
        void required(JournalScopeType type) {
            var plan = JournalScope.plan(base, PROJECT, PLAN);
            var pathBase = plan.pathBase();
            var journal = plan.journalFile();
            var sidecar = plan.sidecarFile();
            var owned = plan.ownedDirectories();
            var lockKey = plan.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(type, null, pathBase, journal, sidecar, owned, lockKey));
        }

        @ParameterizedTest
        @EnumSource(value = JournalScopeType.class, names = {"WORKSPACE", "LESSONS"})
        @DisplayName("the workspace and the lesson store have no scope id")
        void forbidden(JournalScopeType type) {
            var workspace = JournalScope.workspace(base, PROJECT);
            var pathBase = workspace.pathBase();
            var journal = workspace.journalFile();
            var sidecar = workspace.sidecarFile();
            var owned = workspace.ownedDirectories();
            var lockKey = workspace.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(type, PLAN, pathBase, journal, sidecar, owned, lockKey));
        }

        @Test
        @DisplayName("a plan id outside the identifier grammar is refused before it becomes a path")
        void invalidPlanId() {
            assertThrows(IllegalArgumentException.class, () -> JournalScope.plan(base, PROJECT, "../other"));
        }

        @Test
        @DisplayName("each type names whether it requires a scope id and is found by its wire name")
        void types() {
            assertAll(
                    () -> assertTrue(JournalScopeType.PLAN.scopeIdRequired()),
                    () -> assertTrue(JournalScopeType.EPIC.scopeIdRequired()),
                    () -> assertFalse(JournalScopeType.WORKSPACE.scopeIdRequired()),
                    () -> assertFalse(JournalScopeType.LESSONS.scopeIdRequired()),
                    () -> assertEquals(Optional.of(JournalScopeType.LESSONS), JournalScopeType.ofWireName("lessons")),
                    () -> assertEquals(Optional.empty(), JournalScopeType.ofWireName("project")));
        }
    }

    @Nested
    @DisplayName("layout checks")
    class LayoutChecks {

        @Test
        @DisplayName("a journal outside the path base is refused")
        void journalOutsideBase() {
            var plan = JournalScope.plan(base, PROJECT, PLAN);
            var outside = plan.pathBase().resolveSibling("other").resolve("transaction-journal.json");
            var pathBase = plan.pathBase();
            var sidecar = plan.sidecarFile();
            var owned = plan.ownedDirectories();
            var lockKey = plan.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(JournalScopeType.PLAN, PLAN, pathBase, outside, sidecar, owned, lockKey));
        }

        @Test
        @DisplayName("an owned directory outside the path base is refused")
        void ownedOutsideBase() {
            var plan = JournalScope.plan(base, PROJECT, PLAN);
            var owned = List.of(plan.pathBase().getParent());
            var pathBase = plan.pathBase();
            var journal = plan.journalFile();
            var sidecar = plan.sidecarFile();
            var lockKey = plan.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(JournalScopeType.PLAN, PLAN, pathBase, journal, sidecar, owned, lockKey));
        }

        @Test
        @DisplayName("a scope without an owned directory is refused")
        void noOwnedDirectory() {
            var plan = JournalScope.plan(base, PROJECT, PLAN);
            var pathBase = plan.pathBase();
            var journal = plan.journalFile();
            var sidecar = plan.sidecarFile();
            var lockKey = plan.lockKey();
            List<Path> none = List.of();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(JournalScopeType.PLAN, PLAN, pathBase, journal, sidecar, none, lockKey));
        }

        @Test
        @DisplayName("a relative path base is refused")
        void relativeBase() {
            var plan = JournalScope.plan(base, PROJECT, PLAN);
            var relative = Path.of("plans", PLAN);
            var journal = plan.journalFile();
            var sidecar = plan.sidecarFile();
            var owned = plan.ownedDirectories();
            var lockKey = plan.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(JournalScopeType.PLAN, PLAN, relative, journal, sidecar, owned, lockKey));
        }
    }

    @Nested
    @DisplayName("resolve")
    class Resolving {

        @Test
        @DisplayName("a store path resolves below the path base")
        void below() {
            var scope = JournalScope.workspace(base, PROJECT);

            var store = scope.resolve("pending/entry-1.json");

            assertEquals(projectStore().resolve("pending").resolve("entry-1.json"), store);
        }

        @Test
        @DisplayName("a store path that stays below the base after normalization resolves")
        void normalizedBelow() {
            var scope = JournalScope.plan(base, PROJECT, PLAN);

            var store = scope.resolve("mcp/../references.json");

            assertEquals(scope.pathBase().resolve("references.json"), store);
        }

        @ParameterizedTest
        @ValueSource(strings = {"../other-plan/references.json", "mcp/../../escape.json", "..", ".", ""})
        @DisplayName("a store path that escapes the path base, or names the base itself, is refused")
        void escapes(String relativePath) {
            var scope = JournalScope.plan(base, PROJECT, PLAN);

            assertThrows(IllegalArgumentException.class, () -> scope.resolve(relativePath));
        }

        @Test
        @DisplayName("an absolute store path is refused, even one below the base")
        void absolute() {
            var scope = JournalScope.plan(base, PROJECT, PLAN);
            var absolute = scope.pathBase().resolve("references.json").toString();

            assertThrows(IllegalArgumentException.class, () -> scope.resolve(absolute));
        }
    }
}
