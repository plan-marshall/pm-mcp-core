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

import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.Worktree;

/**
 * A checkout that was moved by hand (PM-SEC-2, PM-ARCH-1): the pointer files name the old place until the worktree is
 * repaired or the project is enrolled again, and no recorded path is followed to a checkout that is not there.
 */
@DisplayName("ProjectResolver: a moved checkout")
class ProjectResolverMovedCheckoutTest extends ProjectResolverFixture {

    @Test
    @DisplayName("refuses a worktree moved by hand, whose gitdir record is stale, until it is repaired")
    void worktreeMovedByHand() throws Exception {
        var worktree = worktreeOfMain(PLAN);
        var moved = Files.move(worktree, worktrees.resolve("plan-15-moved"));
        var stale = resolver.resolve(moved.resolve("src/Main.java"));
        Files.writeString(main.resolve(".git/worktrees/" + PLAN + "/gitdir"), moved.resolve(".git") + "\n");

        var repaired = resolver.resolve(moved.resolve("src/Main.java"));

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.UNRECOGNISED_GIT, stale),
                () -> assertEquals(new Worktree(PROJECT, "plan-15-moved"), repaired,
                        "The repaired worktree should resolve under its new name"));
    }

    @Test
    @DisplayName("refuses a main checkout whose path differs from the project root of its enrolment")
    void mainCheckoutMoved() throws Exception {
        var moved = Files.move(main, home.resolve("git/moved"));

        var resolution = resolver.resolve(moved.resolve("README.md"));

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolution),
                () -> assertFalse(Files.exists(main), "The enrolled project root should no longer exist"));
    }

    @Test
    @DisplayName("refuses a worktree of a main checkout that was moved, whose pointers name the old place")
    void worktreeOfMovedMainCheckout() throws Exception {
        worktreeOfMain(PLAN);
        var moved = Files.move(main, home.resolve("git/moved"));

        var resolution = resolver.resolve(moved.resolve(".marshall/local/worktrees/" + PLAN + "/src/Main.java"));

        assertNotEnrolled(NotEnrolled.Reason.UNRECOGNISED_GIT, resolution);
    }
}
