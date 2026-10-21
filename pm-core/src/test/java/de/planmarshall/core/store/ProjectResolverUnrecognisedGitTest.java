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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.ProjectResolution.NotEnrolled;

/**
 * A {@code .git} that is neither a main checkout nor a linked worktree (PM-SEC-2, PM-ARCH-1): an empty pointer, a
 * missing {@code gitdir}, a target that does not exist, another namespace, a common directory that names no
 * {@code .git}, and a symbolic link. Each resolves to no project.
 */
@DisplayName("ProjectResolver: a .git that is no main checkout and no linked worktree")
class ProjectResolverUnrecognisedGitTest extends ProjectResolverFixture {

    @ParameterizedTest(name = "pointer {index}")
    @ValueSource(strings = {"", "gitdir:", "worktree: %s", "gitdir: %s/absent", "gitdir: bad\0path"})
    @DisplayName("resolves a .git file that is no pointer to a git directory to no project")
    void refusesPointerFileWithoutTarget(String content) throws Exception {
        var worktree = worktreeOfMain(PLAN);
        Files.writeString(worktree.resolve(".git"), content.formatted(main.resolve(".git/worktrees/" + PLAN)),
                StandardCharsets.UTF_8);

        assertNotEnrolled(NotEnrolled.Reason.UNRECOGNISED_GIT, resolver.resolve(worktree.resolve("src/Main.java")));
    }

    @Test
    @DisplayName("resolves a worktree whose commondir file names no .git directory to no project")
    void refusesCommonDirectoryElsewhere() throws Exception {
        var worktree = worktreeOfMain(PLAN);
        Files.writeString(main.resolve(".git/worktrees/" + PLAN + "/commondir"), main + "\n");

        assertNotEnrolled(NotEnrolled.Reason.UNRECOGNISED_GIT, resolver.resolve(worktree.resolve("src/Main.java")));
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege there")
    @DisplayName("resolves a toplevel whose .git is a symbolic link to no project")
    void refusesLinkedGit() throws Exception {
        var linked = Files.createDirectories(home.resolve("git/linked"));
        Files.createSymbolicLink(linked.resolve(".git"), main.resolve(".git"));

        assertNotEnrolled(NotEnrolled.Reason.UNRECOGNISED_GIT, resolver.resolve(linked));
    }
}
