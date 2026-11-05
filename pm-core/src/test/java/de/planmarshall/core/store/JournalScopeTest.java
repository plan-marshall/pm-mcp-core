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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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
@EnableGeneratorController
@DisplayName("Journal scope")
class JournalScopeTest {

    private String project;
    private String plan;
    private String epic;

    @TempDir
    Path base;

    @BeforeEach
    void createIdentifiers() {
        project = "project-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        plan = "plan-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        epic = "epic-" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
    }

    private Path projectStore() {
        return base.toAbsolutePath().normalize().resolve("projects").resolve(project);
    }

    @Nested
    @DisplayName("layout")
    class Layout {

        @Test
        @DisplayName("a plan lies at plans/<plan_id>/ with its journal and sidecar in mcp/, under the plan lock")
        void plan() {
            var pathBase = projectStore().resolve("plans").resolve(plan);

            var scope = JournalScope.plan(base, project, plan);

            assertAll("the plan scope of the documented layout",
                    () -> assertEquals(JournalScopeType.PLAN, scope.type(), "the type is the plan scope"),
                    () -> assertEquals(plan, scope.scopeId(), "the scope id is the plan id"),
                    () -> assertEquals(pathBase, scope.pathBase(), "the path base lies below the plans"),
                    () -> assertEquals(pathBase.resolve("mcp/transaction-journal.json"), scope.journalFile(),
                            "the journal lies in mcp of the plan"),
                    () -> assertEquals(pathBase.resolve("mcp/digests.json"), scope.sidecarFile(),
                            "the sidecar lies in mcp of the plan"),
                    () -> assertEquals(List.of(pathBase), scope.ownedDirectories(),
                            "the plan owns its own directory"),
                    () -> assertEquals(LockKey.plan(base, project, plan), scope.lockKey(),
                            "the lock key is the plan lock key"));
        }

        @Test
        @DisplayName("an epic lies at epics/<epic_id>/ with its journal and sidecar at its top, under the epic lock")
        void epic() {
            var pathBase = projectStore().resolve("epics").resolve(epic);

            var scope = JournalScope.epic(base, project, epic);

            assertAll("the epic scope of the documented layout",
                    () -> assertEquals(JournalScopeType.EPIC, scope.type(), "the type is the epic scope"),
                    () -> assertEquals(epic, scope.scopeId(), "the scope id is the epic id"),
                    () -> assertEquals(pathBase, scope.pathBase(), "the path base lies below the epics"),
                    () -> assertEquals(pathBase.resolve("transaction-journal.json"), scope.journalFile(),
                            "the journal lies at the epic top"),
                    () -> assertEquals(pathBase.resolve("digests.json"), scope.sidecarFile(),
                            "the sidecar lies at the epic top"),
                    () -> assertEquals(List.of(pathBase), scope.ownedDirectories(),
                            "the epic owns its own directory"),
                    () -> assertEquals(LockKey.epic(base, project, epic), scope.lockKey(),
                            "the lock key is the epic lock key"));
        }

        @Test
        @DisplayName("the workspace has the project store as path base and owns workspace/ and pending/ only")
        void workspace() {
            var pathBase = projectStore();

            var scope = JournalScope.workspace(base, project);

            assertAll("the workspace scope of the documented layout",
                    () -> assertEquals(JournalScopeType.WORKSPACE, scope.type(), "the type is the workspace scope"),
                    () -> assertNull(scope.scopeId(), "the workspace has no scope id"),
                    () -> assertEquals(pathBase, scope.pathBase(), "the path base is the project store"),
                    () -> assertEquals(pathBase.resolve("workspace/mcp/transaction-journal.json"),
                            scope.journalFile(), "the journal lies in workspace/mcp"),
                    () -> assertEquals(pathBase.resolve("workspace/mcp/digests.json"), scope.sidecarFile(),
                            "the sidecar lies in workspace/mcp"),
                    () -> assertEquals(List.of(pathBase.resolve("workspace"), pathBase.resolve("pending")),
                            scope.ownedDirectories(), "the workspace owns workspace and pending"),
                    () -> assertFalse(scope.ownedDirectories().stream()
                                    .anyMatch(owned -> pathBase.resolve("plans").startsWith(owned)),
                            "the workspace owns no directory of a plan"),
                    () -> assertEquals(LockKey.workspace(base, project), scope.lockKey(),
                            "the lock key is the workspace lock key"));
        }

        @Test
        @DisplayName("the lesson store lies at lessons/ with a hidden journal and sidecar, under the lesson store lock")
        void lessons() {
            var pathBase = projectStore().resolve("lessons");

            var scope = JournalScope.lessons(base, project);

            assertAll("the lesson store scope of the documented layout",
                    () -> assertEquals(JournalScopeType.LESSONS, scope.type(), "the type is the lesson store scope"),
                    () -> assertNull(scope.scopeId(), "the lesson store has no scope id"),
                    () -> assertEquals(pathBase, scope.pathBase(), "the path base lies below lessons"),
                    () -> assertEquals(pathBase.resolve(".transaction-journal.json"), scope.journalFile(),
                            "the journal is hidden at the lessons top"),
                    () -> assertEquals(pathBase.resolve(".digests.json"), scope.sidecarFile(),
                            "the sidecar is hidden at the lessons top"),
                    () -> assertEquals(List.of(pathBase), scope.ownedDirectories(),
                            "the lesson store owns its directory"),
                    () -> assertEquals(LockKey.lessonStore(base, project), scope.lockKey(),
                            "the lock key is the lesson store lock"));
        }
    }

    @Nested
    @DisplayName("scope id")
    class ScopeId {

        @ParameterizedTest
        @EnumSource(value = JournalScopeType.class, names = {"PLAN", "EPIC"})
        @DisplayName("a plan and an epic require a scope id")
        void required(JournalScopeType type) {
            var saved = JournalScope.plan(base, project, plan);
            var pathBase = saved.pathBase();
            var journal = saved.journalFile();
            var sidecar = saved.sidecarFile();
            var owned = saved.ownedDirectories();
            var lockKey = saved.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(type, null, pathBase, journal, sidecar, owned, lockKey),
                    "a plan and an epic require a scope id");
        }

        @ParameterizedTest
        @EnumSource(value = JournalScopeType.class, names = {"WORKSPACE", "LESSONS"})
        @DisplayName("the workspace and the lesson store have no scope id")
        void forbidden(JournalScopeType type) {
            var workspace = JournalScope.workspace(base, project);
            var pathBase = workspace.pathBase();
            var journal = workspace.journalFile();
            var sidecar = workspace.sidecarFile();
            var owned = workspace.ownedDirectories();
            var lockKey = workspace.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(type, plan, pathBase, journal, sidecar, owned, lockKey),
                    "the workspace and lessons forbid a scope id");
        }

        @Test
        @DisplayName("a plan id outside the identifier grammar is refused before it becomes a path")
        void invalidPlanId() {
            assertThrows(IllegalArgumentException.class, () -> JournalScope.plan(base, project, "../other"),
                    "an id outside the identifier grammar is refused");
        }

        @Test
        @DisplayName("each type names whether it requires a scope id and is found by its wire name")
        void types() {
            assertAll("the scope id rule and wire name of each type",
                    () -> assertTrue(JournalScopeType.PLAN.scopeIdRequired(), "a plan requires a scope id"),
                    () -> assertTrue(JournalScopeType.EPIC.scopeIdRequired(), "an epic requires a scope id"),
                    () -> assertFalse(JournalScopeType.WORKSPACE.scopeIdRequired(),
                            "the workspace needs no scope id"),
                    () -> assertFalse(JournalScopeType.LESSONS.scopeIdRequired(),
                            "the lesson store needs no scope id"),
                    () -> assertEquals(Optional.of(JournalScopeType.LESSONS),
                            JournalScopeType.ofWireName("lessons"),
                            "the lesson store is found by its wire name"),
                    () -> assertEquals(Optional.empty(), JournalScopeType.ofWireName("project"),
                            "an unknown wire name is not found"));
        }
    }

    @Nested
    @DisplayName("layout checks")
    class LayoutChecks {

        @Test
        @DisplayName("a journal outside the path base is refused")
        void journalOutsideBase() {
            var saved = JournalScope.plan(base, project, plan);
            var outside = saved.pathBase().resolveSibling("other").resolve("transaction-journal.json");
            var pathBase = saved.pathBase();
            var sidecar = saved.sidecarFile();
            var owned = saved.ownedDirectories();
            var lockKey = saved.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(JournalScopeType.PLAN, plan, pathBase, outside, sidecar, owned, lockKey),
                    "a journal outside the base is refused");
        }

        @Test
        @DisplayName("an owned directory outside the path base is refused")
        void ownedOutsideBase() {
            var saved = JournalScope.plan(base, project, plan);
            var owned = List.of(saved.pathBase().getParent());
            var pathBase = saved.pathBase();
            var journal = saved.journalFile();
            var sidecar = saved.sidecarFile();
            var lockKey = saved.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(JournalScopeType.PLAN, plan, pathBase, journal, sidecar, owned, lockKey),
                    "an owned directory outside the base is refused");
        }

        @Test
        @DisplayName("a scope without an owned directory is refused")
        void noOwnedDirectory() {
            var saved = JournalScope.plan(base, project, plan);
            var pathBase = saved.pathBase();
            var journal = saved.journalFile();
            var sidecar = saved.sidecarFile();
            var lockKey = saved.lockKey();
            List<Path> none = List.of();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(JournalScopeType.PLAN, plan, pathBase, journal, sidecar, none, lockKey),
                    "a scope without owned directory is refused");
        }

        @Test
        @DisplayName("a relative path base is refused")
        void relativeBase() {
            var saved = JournalScope.plan(base, project, plan);
            var relative = Path.of("plans", plan);
            var journal = saved.journalFile();
            var sidecar = saved.sidecarFile();
            var owned = saved.ownedDirectories();
            var lockKey = saved.lockKey();

            assertThrows(IllegalArgumentException.class,
                    () -> new JournalScope(JournalScopeType.PLAN, plan, relative, journal, sidecar, owned, lockKey),
                    "a relative path base is refused");
        }
    }

    @Nested
    @DisplayName("resolve")
    class Resolving {

        @Test
        @DisplayName("a store path resolves below the path base")
        void below() {
            var scope = JournalScope.workspace(base, project);

            var store = scope.resolve("pending/entry-1.json");

            assertEquals(projectStore().resolve("pending").resolve("entry-1.json"), store,
                    "the store resolves below the path base");
        }

        @Test
        @DisplayName("a store path that stays below the base after normalization resolves")
        void normalizedBelow() {
            var scope = JournalScope.plan(base, project, plan);

            var store = scope.resolve("mcp/../references.json");

            assertEquals(scope.pathBase().resolve("references.json"), store,
                    "a normalized path resolves below the base");
        }

        @ParameterizedTest
        @ValueSource(strings = {"../other-plan/references.json", "mcp/../../escape.json", "..", ".", ""})
        @DisplayName("a store path that escapes the path base, or names the base itself, is refused")
        void escapes(String relativePath) {
            var scope = JournalScope.plan(base, project, plan);

            assertThrows(IllegalArgumentException.class, () -> scope.resolve(relativePath),
                    "a path escaping the base is refused");
        }

        @Test
        @DisplayName("an absolute store path is refused, even one below the base")
        void absolute() {
            var scope = JournalScope.plan(base, project, plan);
            var absolute = scope.pathBase().resolve("references.json").toString();

            assertThrows(IllegalArgumentException.class, () -> scope.resolve(absolute),
                    "an absolute store path is refused");
        }
    }

    @Nested
    @DisplayName("symbolic links below the path base")
    class Links {

        @Test
        @DisplayName("a store reached without a link resolves, also when it does not exist yet")
        void withoutLink() throws Exception {
            var scope = JournalScope.plan(base, project, plan);
            Files.createDirectories(scope.pathBase().resolve("logs"));

            var store = scope.resolveWithoutLinks("logs/work.jsonl");

            assertAll("a store reached without a symbolic link",
                    () -> assertEquals(scope.pathBase().resolve("logs/work.jsonl"), store,
                            "the store resolves without a link"),
                    () -> assertEquals(Optional.empty(), scope.linkBelowPathBase(store),
                            "no link is found below the base"));
        }

        @Test
        @DisplayName("a store that is a symbolic link to a file outside the scope is refused")
        void linkedStore() throws Exception {
            var scope = JournalScope.plan(base, project, plan);
            var outside = Files.writeString(base.resolve("outside.json"), "{}");
            var store = scope.pathBase().resolve("status.json");
            SymbolicLinks.createOrSkip(store, outside);

            assertAll("a store that is itself a symbolic link",
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> scope.resolveWithoutLinks("status.json"),
                            "a store linked outside is refused"),
                    () -> assertEquals(Optional.of(store), scope.linkBelowPathBase(store),
                            "the link below the base is reported"));
        }

        @Test
        @DisplayName("a store below a parent directory that is a symbolic link to a directory outside is refused")
        void linkedParent() throws Exception {
            var scope = JournalScope.plan(base, project, plan);
            var outside = Files.createDirectories(base.resolve("outside"));
            var parent = scope.pathBase().resolve("logs");
            SymbolicLinks.createOrSkip(parent, outside);

            assertAll("a store below a linked parent directory",
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> scope.resolveWithoutLinks("logs/work.jsonl"),
                            "a parent linked outside is refused"),
                    () -> assertEquals(Optional.of(parent),
                            scope.linkBelowPathBase(scope.resolve("logs/work.jsonl")),
                            "the linked parent is reported"));
        }

        @Test
        @DisplayName("a dangling symbolic link in place of a store is refused")
        void danglingLink() {
            var scope = JournalScope.plan(base, project, plan);
            var store = scope.pathBase().resolve("status.json");
            SymbolicLinks.createOrSkip(store, base.resolve("missing.json"));

            assertThrows(IllegalArgumentException.class, () -> scope.resolveWithoutLinks("status.json"),
                    "a dangling link in place of a store is refused");
        }

        @ParameterizedTest
        @ValueSource(strings = {"status.json", "mcp/status.json", "a/b/c.jsonl"})
        @DisplayName("a relative, normalized path is a store path")
        void storePath(String path) {
            assertTrue(JournalScope.isStorePath(path), "a relative normalized path is a store path");
        }

        @ParameterizedTest
        @ValueSource(strings = {"", "/status.json", "../status.json", "a/../status.json", "./status.json",
                "a//status.json", "a/", "a/."})
        @DisplayName("an empty, absolute or not normalized path is no store path")
        void noStorePath(String path) {
            assertFalse(JournalScope.isStorePath(path), "an empty or unnormalized path is no store path");
        }
    }
}
