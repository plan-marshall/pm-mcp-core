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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.Worktree;

/**
 * A repository nested inside the enrolled project (PM-SEC-2, PM-ARCH-1): the nested repository resolves to no project,
 * while the enrolled root and the worktree around it still resolve. The {@code .git} segment is the git layout; the
 * directory and file names are generated.
 */
@EnableGeneratorController
@DisplayName("ProjectResolver: a nested checkout")
class ProjectResolverNestedCheckoutTest extends ProjectResolverFixture {

    private static String name() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("resolves a repository inside the enrolled root to no project, while the root still resolves")
    void nestedRepository() throws Exception {
        var vendor = main.resolve(name());
        var nested = Files.createDirectories(vendor.resolve(name()).resolve(".git")).getParent();
        var nestedFile = Files.writeString(nested.resolve(name() + ".c"), Generators.letterStrings(1, 40).next());
        var mainFile = Files.writeString(vendor.resolve(name() + ".txt"), Generators.letterStrings(1, 40).next());

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(nestedFile)),
                () -> assertMainCheckout(resolver.resolve(mainFile)));
    }

    @Test
    @DisplayName("resolves a repository inside a worktree of the project to no project")
    void nestedRepositoryInWorktree() throws Exception {
        var worktree = worktreeOfMain(PLAN);
        var vendor = worktree.resolve(name());
        var nested = Files.createDirectories(vendor.resolve(name()).resolve(".git")).getParent();

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(nested)),
                () -> assertEquals(new Worktree(PROJECT, PLAN), resolver.resolve(vendor),
                        "The directory around it should be in the worktree"));
    }
}
