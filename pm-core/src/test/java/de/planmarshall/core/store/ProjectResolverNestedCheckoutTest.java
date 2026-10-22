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

import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.Worktree;

/**
 * A repository nested inside the enrolled project (PM-SEC-2, PM-ARCH-1): the nested repository resolves to no project,
 * while the enrolled root and the worktree around it still resolve.
 */
@DisplayName("ProjectResolver: a nested checkout")
class ProjectResolverNestedCheckoutTest extends ProjectResolverFixture {

    @Test
    @DisplayName("resolves a repository inside the enrolled root to no project, while the root still resolves")
    void nestedRepository() throws Exception {
        var nested = Files.createDirectories(main.resolve("vendor/lib/.git")).getParent();
        Files.writeString(nested.resolve("lib.c"), "nested");
        Files.writeString(main.resolve("vendor/notes.txt"), "main");

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(nested.resolve("lib.c"))),
                () -> assertMainCheckout(resolver.resolve(main.resolve("vendor/notes.txt"))));
    }

    @Test
    @DisplayName("resolves a repository inside a worktree of the project to no project")
    void nestedRepositoryInWorktree() throws Exception {
        var worktree = worktreeOfMain(PLAN);
        var nested = Files.createDirectories(worktree.resolve("vendor/lib/.git")).getParent();

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(nested)),
                () -> assertEquals(new Worktree(PROJECT, PLAN), resolver.resolve(worktree.resolve("vendor"))));
    }
}
