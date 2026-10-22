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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.ConfinementOutcome.Reason;
import de.planmarshall.core.store.ProjectResolution.PathRefused;

/**
 * A path in the main checkout of the enrolled project (PM-SEC-2, PM-ARCH-1): the checkout root, its files and
 * directories, its {@code .git} and its worktree directory, named through a symbolic link of the platform as well;
 * a path that cannot be canonicalised is the confinement's refusal.
 */
@DisplayName("ProjectResolver: a path in the main checkout")
class ProjectResolverMainCheckoutTest extends ProjectResolverFixture {

    @ParameterizedTest(name = "resolves <main>/{0}")
    @ValueSource(strings = {"", "README.md", "src", "src/Main.java", ".git", ".marshall/local/worktrees"})
    @DisplayName("resolves to the main checkout of the project, the checkout root and its .git included")
    void resolvesInside(String belowMain) {
        assertMainCheckout(resolver.resolve(main.resolve(belowMain)));
    }

    @Test
    @DisplayName("resolves a path named through a symbolic link of the platform by its canonical form")
    void resolvesThroughPlatformLink() {
        assertMainCheckout(resolver.resolve(temp.resolve("git/" + PROJECT + "/src/Main.java")));
    }

    @Test
    @DisplayName("refuses a path that does not exist, which cannot be canonicalised")
    void refusesMissingPath() {
        var resolution = resolver.resolve(main.resolve("src/Absent.java"));

        var refused = assertInstanceOf(PathRefused.class, resolution, "The path should be refused");
        assertAll(
                () -> assertEquals(Reason.CANONICALIZATION_FAILED, refused.rejected().reason(),
                        "The refusal should name the failed canonicalisation"),
                () -> assertEquals(ConfinementOutcome.PATH_TRAVERSAL_REJECTED, refused.rejected().code(),
                        "The refusal should carry the outcome code of the confinement"));
    }
}
