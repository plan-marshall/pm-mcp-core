/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The baseline of a project's git metadata ({@link GitMetadataGuard}): recorded once, compared without changing it, and
 * moved only by the operator's act {@code rebaseline}. A project without git never compares.
 * <p>
 * Contract literals that stay: the git directory and config names and the part suffix of {@code unreadable}. The trees
 * and the config contents are generated.
 */
@EnableGeneratorController
@DisplayName("Git metadata guard")
class GitMetadataGuardTest {

    @TempDir
    Path base;

    @Test
    @DisplayName("Should answer unchanged for a tree it recorded and one that did not change")
    void shouldAnswerUnchangedForAnUnchangedTree() throws Exception {
        var root = repository();
        var guard = new GitMetadataGuard();

        guard.record(root);
        var verdict = guard.verify(root);

        assertEquals(new GitMetadataGuard.Unchanged(), verdict, "an unchanged tree stays unchanged");
    }

    @Test
    @DisplayName("Should answer changed with the parts that differ")
    void shouldAnswerChangedWithTheParts() throws Exception {
        var root = repository();
        var guard = new GitMetadataGuard();
        guard.record(root);
        Files.writeString(root.resolve(".git/config"), "[core]\n\tfsmonitor = attacker\n");

        var verdict = guard.verify(root);

        var changed = assertInstanceOf(GitMetadataGuard.Changed.class, verdict, "a changed tree is changed");
        assertEquals(Set.of(".git/config"), changed.parts(), "the changed part is named");
    }

    @Test
    @DisplayName("Should keep the baseline after a comparison and move it only on rebaseline")
    void shouldMoveTheBaselineOnlyOnRebaseline() throws Exception {
        var root = repository();
        var guard = new GitMetadataGuard();
        guard.record(root);
        Files.writeString(root.resolve(".git/config"), "[core]\n\tfsmonitor = attacker\n");
        var first = guard.verify(root);

        var second = guard.verify(root);

        assertAll("The baseline stays",
                () -> assertInstanceOf(GitMetadataGuard.Changed.class, first, "the first compare sees the change"),
                () -> assertInstanceOf(GitMetadataGuard.Changed.class, second,
                        "the baseline did not move with the comparison"),
                () -> {
                    guard.rebaseline(root);
                    assertEquals(new GitMetadataGuard.Unchanged(), guard.verify(root),
                            "the operator's act moves the baseline");
                });
    }

    @Test
    @DisplayName("Should never compare a project without git")
    void shouldNeverCompareAProjectWithoutGit() throws Exception {
        var root = Files.createDirectories(base.resolve(name()));
        var guard = new GitMetadataGuard();

        guard.record(root);
        var verdict = guard.verify(root);

        assertEquals(new GitMetadataGuard.Unchanged(), verdict, "a project without git never compares");
    }

    @Test
    @DisplayName("Should answer unchanged for a project it never recorded")
    void shouldAnswerUnchangedWithoutBaseline() throws Exception {
        var root = repository();

        var verdict = new GitMetadataGuard().verify(root);

        assertEquals(new GitMetadataGuard.Unchanged(), verdict, "a project without a baseline never compares");
    }

    private Path repository() throws IOException {
        var root = Files.createDirectories(base.resolve(name()));
        var git = Files.createDirectories(root.resolve(".git"));
        Files.writeString(git.resolve("config"), "[core]\n\trepositoryformatversion = 0\n");
        Files.createDirectories(git.resolve("hooks"));
        return root;
    }

    private static String name() {
        return Generators.letterStrings(4, 12).next();
    }
}
