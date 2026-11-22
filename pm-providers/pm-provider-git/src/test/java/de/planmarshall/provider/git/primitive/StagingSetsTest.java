/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.ScopeType;
import de.planmarshall.provider.git.primitive.StagingSets.StagingSet;

/**
 * The staging set derived from the scope: the directory of each dirty path is confined to the checkout, a symbolic
 * link is staged as a link, and a path is staged only when it belongs to its scope's set. The scope sets
 * ({@code .marshall/epics/}, {@code .gitignore}, ...) are the specified literals.
 */
@EnableGeneratorController
@DisplayName("StagingSets")
class StagingSetsTest {

    @TempDir
    Path worktree;

    private final TypedGenerator<String> names = Generators.uniqueValues(Generators.letterStrings(1, 12));

    @Nested
    @DisplayName("plan scope")
    class PlanScope {

        @RepeatedTest(10)
        @DisplayName("every relative path inside the worktree is staged")
        void insidePathsAreStaged() {
            var dirty = Set.of(names.next() + "/" + names.next() + ".java", names.next() + ".md");

            var set = StagingSets.derive(ScopeType.PLAN, worktree, dirty, false);

            assertAll("plan staging set",
                    () -> assertEquals(new TreeSet<>(dirty), set.staged(), "Every inside path is staged"),
                    () -> assertTrue(set.outside().isEmpty(), "No inside path is reported outside"));
        }

        // a parent escape, an absolute path, a non-normal name and the empty name: each refused as a value
        @ParameterizedTest
        @ValueSource(strings = {"../outside.txt", "a/../../outside.txt", "/etc/passwd", "a/./b", "./a", "a/../b", ""})
        @DisplayName("a path that is no confined name of the worktree is outside")
        void refusedPathsAreOutside(String path) {
            var inside = names.next() + ".txt";

            var set = StagingSets.derive(ScopeType.PLAN, worktree, List.of(path, inside), false);

            assertAll("refused path",
                    () -> assertEquals(Set.of(path), set.outside(), "The refused path is reported outside"),
                    () -> assertEquals(Set.of(inside), set.staged(), "The inside path is still staged"));
        }

        @Test
        @DisplayName("an absolute path inside the worktree is outside")
        void absoluteInsidePathIsOutside() {
            var absolute = worktree.resolve(names.next()).toString();

            var set = StagingSets.derive(ScopeType.PLAN, worktree, List.of(absolute), false);

            assertEquals(Set.of(absolute), set.outside(), "An absolute name is never staged");
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("a path through a symbolic link that leads out of the worktree is outside")
        void pathThroughOutsideLinkIsOutside(@TempDir Path elsewhere) throws Exception {
            var link = names.next();
            Files.createSymbolicLink(worktree.resolve(link), elsewhere);
            var through = link + "/" + names.next();
            var inside = names.next() + ".txt";

            var set = StagingSets.derive(ScopeType.PLAN, worktree, List.of(through, inside), false);

            assertAll("path through an outside link",
                    () -> assertEquals(Set.of(through), set.outside(), "A path through the outside link is outside"),
                    () -> assertEquals(Set.of(inside), set.staged(), "The inside path is still staged"));
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("a dirty symbolic link to outside the worktree is staged as a link")
        void linkToOutsideIsStaged(@TempDir Path elsewhere) throws Exception {
            var nested = names.next();
            Files.createDirectories(worktree.resolve(nested));
            var topLink = names.next();
            var nestedLink = nested + "/" + names.next();
            Files.createSymbolicLink(worktree.resolve(topLink), elsewhere);
            Files.createSymbolicLink(worktree.resolve(nestedLink), elsewhere.resolve(names.next()));
            var inside = names.next() + ".txt";

            var set = StagingSets.derive(ScopeType.PLAN, worktree, List.of(topLink, nestedLink, inside), false);

            assertAll("links to outside",
                    () -> assertEquals(Set.of(topLink, nestedLink, inside), set.staged(),
                            "The links are staged with the rest of the scope"),
                    () -> assertTrue(set.outside().isEmpty(), "No link to outside blocks the commit"));
        }

        @Test
        @EnabledOnOs({OS.LINUX, OS.MAC})
        @DisplayName("a dangling symbolic link is staged as a link")
        void danglingLinkIsStaged() throws Exception {
            var link = names.next();
            Files.createSymbolicLink(worktree.resolve(link), worktree.resolve(names.next() + ".missing"));
            var inside = names.next() + ".txt";

            var set = StagingSets.derive(ScopeType.PLAN, worktree, List.of(link, inside), false);

            assertAll("dangling link",
                    () -> assertEquals(Set.of(link, inside), set.staged(), "The dangling link is staged"),
                    () -> assertTrue(set.outside().isEmpty(), "A dangling link blocks no commit"));
        }

        @Test
        @DisplayName("a name shaped like a link target is staged as a plain name")
        void linkShapedNameIsAPlainName() {
            var name = names.next() + " -> " + names.next();

            var set = StagingSets.derive(ScopeType.PLAN, worktree, List.of(name), false);

            assertEquals(Set.of(name), set.staged(), "The link-shaped name is staged as named");
        }
    }

    @Nested
    @DisplayName("epic scope")
    class EpicScope {

        @Test
        @DisplayName("stages the epic ledger and leaves every other path outside")
        void stagesTheLedger() {
            var ledger = ".marshall/epics/" + names.next() + "/ledger.json";
            var source = "src/" + names.next() + ".java";

            var set = StagingSets.derive(ScopeType.EPIC, worktree, List.of(ledger, source), false);

            assertAll("epic staging set",
                    () -> assertEquals(Set.of(ledger), set.staged(), "Only the epic ledger is staged"),
                    () -> assertEquals(Set.of(source), set.outside(), "A source file stays outside the epic"));
        }

        @Test
        @DisplayName("stages .marshall/archived-epics/x only while the epic archives")
        void archivedEpicsOnlyWhileArchiving() {
            var archived = List.of(".marshall/archived-epics/x");

            var notArchiving = StagingSets.derive(ScopeType.EPIC, worktree, archived, false);
            var archiving = StagingSets.derive(ScopeType.EPIC, worktree, archived, true);

            assertAll("archive flag",
                    () -> assertEquals(Set.copyOf(archived), notArchiving.outside(),
                            "The archive is outside without the flag"),
                    () -> assertEquals(Set.copyOf(archived), archiving.staged(),
                            "The archive is staged while archiving"));
        }

        @Test
        @DisplayName("a name that leaves the epic set through .. is outside")
        void parentEscapeFromTheEpicSet() {
            var escape = ".marshall/epics/../config/" + names.next();

            var set = StagingSets.derive(ScopeType.EPIC, worktree, List.of(escape), false);

            assertEquals(Set.of(escape), set.outside(), "A name escaping the epic set is outside");
        }
    }

    @Nested
    @DisplayName("workspace scope")
    class WorkspaceScope {

        @Test
        @DisplayName("stages the setup's set and leaves every other path outside")
        void stagesTheSetupSet() {
            var config = ".marshall/config/" + names.next() + ".json";
            var architecture = ".marshall/project-architecture/" + names.next() + ".json";
            var epic = ".marshall/epics/" + names.next();
            var source = names.next() + "/.gitignore";

            var set = StagingSets.derive(ScopeType.WORKSPACE, worktree,
                    List.of(config, ".gitignore", architecture, epic, source), false);

            assertAll("workspace staging set",
                    () -> assertEquals(Set.of(config, ".gitignore", architecture), set.staged(),
                            "The setup's set is staged"),
                    () -> assertEquals(Set.of(epic, source), set.outside(),
                            "Paths beyond the setup's set are outside"));
        }
    }

    @Nested
    @DisplayName("every scope")
    class EveryScope {

        @Test
        @DisplayName("the project scope has no staging set")
        void projectHasNoStagingSet() {
            var dirty = Set.of(names.next(), ".marshall/config/" + names.next(), ".gitignore");

            var set = StagingSets.derive(ScopeType.PROJECT, worktree, dirty, true);

            assertAll("project staging set",
                    () -> assertTrue(set.staged().isEmpty(), "The project scope stages nothing"),
                    () -> assertEquals(new TreeSet<>(dirty), set.outside(), "Every dirty path is outside"));
        }

        @ParameterizedTest
        @EnumSource(ScopeType.class)
        @DisplayName("each dirty path lands in exactly one of staged and outside")
        void partition(ScopeType scope) {
            var dirty = new ArrayList<>(List.of(".gitignore", "../x", ".marshall/epics/" + names.next(),
                    ".marshall/config/" + names.next(), names.next()));

            var set = StagingSets.derive(scope, worktree, dirty, false);

            var union = new TreeSet<>(set.staged());
            union.addAll(set.outside());
            assertAll("partition",
                    () -> assertEquals(new TreeSet<>(dirty), union, "Staged and outside cover every path"),
                    () -> assertEquals(dirty.size(), set.staged().size() + set.outside().size(),
                            "No path is in both staged and outside"));
        }

        @Test
        @DisplayName("an empty change set derives an empty staging set")
        void emptyChangeSet() {
            var set = StagingSets.derive(ScopeType.PLAN, worktree, List.of(), false);

            assertEquals(new StagingSet(new TreeSet<>(), new TreeSet<>()), set, "Nothing dirty stages nothing");
        }

        @Test
        @DisplayName("the staging set cannot be changed by its holder")
        void unmodifiable() {
            var set = StagingSets.derive(ScopeType.PLAN, worktree, List.of(names.next()), false);
            var staged = set.staged();
            var another = names.next();

            assertThrows(UnsupportedOperationException.class, () -> staged.add(another),
                    "The staged paths are unmodifiable");
        }
    }
}
