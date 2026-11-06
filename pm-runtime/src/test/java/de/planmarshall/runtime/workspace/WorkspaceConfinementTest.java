/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.workspace;

import static de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.ConfinementOutcome;
import de.planmarshall.core.store.ConfinementOutcome.Confined;
import de.planmarshall.core.store.ConfinementOutcome.Rejected;
import de.planmarshall.core.store.WorkspaceRoots;

/**
 * The runtime entry of the path confinement (PM-SEC-2): the outcome of {@code pm-core} is passed on unchanged,
 * a refusal writes the WARN record and an accepted path writes none. The linked worktree is the pair of plain
 * pointer files git writes for one; no git process runs.
 * <p>
 * The names and the content of git's pointer files and the outcome code are the specified formats; the directory
 * and file names of the workspace and the content of its files are generated.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("WorkspaceConfinement")
class WorkspaceConfinementTest {

    /** Contract literal: the closed outcome code of a refused path. */
    private static final String OUTCOME_CODE = "path_traversal_rejected";

    @TempDir
    Path temp;

    /** The canonical enrolled root, a repository with a {@code .git} directory. */
    private Path root;

    /** A canonical live linked worktree of the enrolled root, beside it. */
    private Path worktree;

    /** A canonical directory beside the enrolled root, outside the workspace. */
    private Path beside;

    /** The generated name of the file in the enrolled root. */
    private String rootFile;

    /** The generated name of the file in the linked worktree. */
    private String worktreeFile;

    /** The generated name of the file beside the enrolled root. */
    private String besideFile;

    private WorkspaceConfinement confinement;

    /** @return a generated name; the prefix keeps the names of one workspace distinct */
    private static String name(String prefix) {
        return prefix + "-" + Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static String content() {
        return Generators.letterStrings(1, 40).next();
    }

    @BeforeEach
    void workspace() throws IOException {
        var base = temp.toRealPath();
        var worktreeName = name("linked");
        root = Files.createDirectories(base.resolve(name("root")));
        rootFile = name("in-root") + ".md";
        Files.writeString(root.resolve(rootFile), content());
        // Contract literals: the administrative directory and the pointer files git keeps for a linked worktree
        var gitDirectory = Files.createDirectories(root.resolve(".git/worktrees").resolve(worktreeName));
        Files.writeString(gitDirectory.resolve("commondir"), "../..\n");

        worktree = Files.createDirectories(base.resolve(worktreeName));
        worktreeFile = name("in-worktree") + ".java";
        Files.writeString(worktree.resolve(worktreeFile), content());
        Files.writeString(worktree.resolve(".git"), "gitdir: " + gitDirectory + "\n");
        Files.writeString(gitDirectory.resolve("gitdir"), worktree.resolve(".git") + "\n");

        beside = Files.createDirectories(base.resolve(name("beside")));
        besideFile = name("outside") + ".txt";
        Files.writeString(beside.resolve(besideFile), content());

        confinement = new WorkspaceConfinement(new WorkspaceRoots(root, Set.of(worktree)));
    }

    private ConfinementOutcome confine(String rawPath) {
        return assertDoesNotThrow(() -> confinement.confine(rawPath), "No exception should be thrown for a path");
    }

    private static void assertAcceptedWithoutRecord(Path expected, ConfinementOutcome outcome) {
        var confined = assertInstanceOf(Confined.class, outcome, "The path should be accepted");
        assertEquals(expected, confined.path(), "The outcome should carry the canonical path");
        LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, WorkspaceConfinement.class);
    }

    private static void assertRefusedWithRecord(ConfinementOutcome outcome) {
        var rejected = assertInstanceOf(Rejected.class, outcome, "The path should be refused");
        assertEquals(OUTCOME_CODE, rejected.code(), "A refusal should carry the closed outcome code");
        assertAll("WARN record",
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.PROJECT_PATH_REFUSED.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN, OUTCOME_CODE),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        rejected.reason().name()));
    }

    @Nested
    @DisplayName("a path inside the workspace")
    class Accepted {

        @Test
        @DisplayName("returns a path inside the enrolled root as Confined and writes no record")
        void acceptsPathInsideRoot() {
            var outcome = confine(rootFile);

            assertAcceptedWithoutRecord(root.resolve(rootFile), outcome);
        }

        @Test
        @DisplayName("returns a path inside a live linked worktree as Confined and writes no record")
        void acceptsPathInsideLiveWorktree() {
            var outcome = confine(worktree.resolve(worktreeFile).toString());

            assertAcceptedWithoutRecord(worktree.resolve(worktreeFile), outcome);
        }
    }

    @Nested
    @DisplayName("a path outside the workspace")
    class Refused {

        @Test
        @DisplayName("returns a .. escape as Rejected and writes the WARN record")
        void refusesParentEscape() {
            var outcome = confine("../" + beside.getFileName() + "/" + besideFile);

            assertRefusedWithRecord(outcome);
        }

        @Test
        @DisplayName("returns an absolute path outside the root as Rejected and writes the WARN record")
        void refusesAbsolutePathOutside() {
            var outcome = confine(beside.resolve(besideFile).toString());

            assertRefusedWithRecord(outcome);
        }

        @Test
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege there")
        @DisplayName("returns a symbolic link out of the root as Rejected and writes the WARN record")
        void refusesSymbolicLinkOut() throws Exception {
            var link = name("link-out");
            Files.createSymbolicLink(root.resolve(link), beside);

            var outcome = confine(link + "/" + besideFile);

            assertRefusedWithRecord(outcome);
        }
    }

    @Test
    @DisplayName("rejects construction without roots")
    void rejectsMissingRoots() {
        assertThrows(NullPointerException.class, () -> new WorkspaceConfinement(null),
                "The roots are required");
    }
}
