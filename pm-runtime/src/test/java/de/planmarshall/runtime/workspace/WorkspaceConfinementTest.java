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

import static de.planmarshall.core.log.PmMcpLogMessages.WARN;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;


import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.store.ConfinementOutcome;
import de.planmarshall.core.store.ConfinementOutcome.Confined;
import de.planmarshall.core.store.ConfinementOutcome.Rejected;
import de.planmarshall.core.store.WorkspaceRoots;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * The runtime entry of the path confinement (PM-SEC-2): the outcome of {@code pm-core} is passed on unchanged,
 * a refusal writes the WARN record and an accepted path writes none. The linked worktree is the pair of plain
 * pointer files git writes for one; no git process runs.
 */
@EnableTestLogger
@DisplayName("WorkspaceConfinement")
class WorkspaceConfinementTest {

    private static final String OUTCOME_CODE = "path_traversal_rejected";

    @TempDir
    Path temp;

    /** The canonical enrolled root, a repository with a {@code .git} directory. */
    private Path root;

    /** A canonical live linked worktree of the enrolled root, beside it. */
    private Path worktree;

    /** A canonical directory beside the enrolled root, outside the workspace. */
    private Path beside;

    private WorkspaceConfinement confinement;

    @BeforeEach
    void workspace() throws IOException {
        var base = temp.toRealPath();
        root = Files.createDirectories(base.resolve("repo"));
        Files.writeString(root.resolve("README.md"), "root");
        var gitDirectory = Files.createDirectories(root.resolve(".git/worktrees/linked"));
        Files.writeString(gitDirectory.resolve("commondir"), "../..\n");

        worktree = Files.createDirectories(base.resolve("linked"));
        Files.writeString(worktree.resolve("Main.java"), "worktree");
        Files.writeString(worktree.resolve(".git"), "gitdir: " + gitDirectory + "\n");
        Files.writeString(gitDirectory.resolve("gitdir"), worktree.resolve(".git") + "\n");

        beside = Files.createDirectories(base.resolve("beside"));
        Files.writeString(beside.resolve("file.txt"), "beside");

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
            var outcome = confine("README.md");

            assertAcceptedWithoutRecord(root.resolve("README.md"), outcome);
        }

        @Test
        @DisplayName("returns a path inside a live linked worktree as Confined and writes no record")
        void acceptsPathInsideLiveWorktree() {
            var outcome = confine(worktree.resolve("Main.java").toString());

            assertAcceptedWithoutRecord(worktree.resolve("Main.java"), outcome);
        }
    }

    @Nested
    @DisplayName("a path outside the workspace")
    class Refused {

        @Test
        @DisplayName("returns a .. escape as Rejected and writes the WARN record")
        void refusesParentEscape() {
            var outcome = confine("../beside/file.txt");

            assertRefusedWithRecord(outcome);
        }

        @Test
        @DisplayName("returns an absolute path outside the root as Rejected and writes the WARN record")
        void refusesAbsolutePathOutside() {
            var outcome = confine(beside.resolve("file.txt").toString());

            assertRefusedWithRecord(outcome);
        }

        @Test
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege there")
        @DisplayName("returns a symbolic link out of the root as Rejected and writes the WARN record")
        void refusesSymbolicLinkOut() throws Exception {
            Files.createSymbolicLink(root.resolve("link-out"), beside);

            var outcome = confine("link-out/file.txt");

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
