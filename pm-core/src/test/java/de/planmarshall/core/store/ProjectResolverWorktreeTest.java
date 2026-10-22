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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.Worktree;

/**
 * A path in a linked worktree of the project (PM-SEC-2, PM-ARCH-1): a plan worktree, the shared epic worktree, an
 * absolute common directory, and the worktree names that resolve to no project — a baseline scratch worktree, a name
 * outside the plan-id grammar, a worktree elsewhere, and the worktree of a repository that is not enrolled.
 */
@DisplayName("ProjectResolver: a path in a linked worktree")
class ProjectResolverWorktreeTest extends ProjectResolverFixture {

    @Test
    @DisplayName("resolves a worktree of a plan to the project and the plan")
    void resolvesPlanWorktree() throws Exception {
        var worktree = worktreeOfMain(PLAN);

        assertAll(
                () -> assertEquals(new Worktree(PROJECT, PLAN), resolver.resolve(worktree.resolve("src/Main.java")),
                        "A file of the worktree should resolve to it"),
                () -> assertEquals(new Worktree(PROJECT, PLAN), resolver.resolve(worktree),
                        "The worktree root should resolve to it"));
    }

    @Test
    @DisplayName("resolves the shared epic worktree to the project")
    void resolvesEpicsWorktree() throws Exception {
        var worktree = worktreeOfMain(ProjectResolution.EPICS_WORKTREE);

        var resolved = assertInstanceOf(Worktree.class, resolver.resolve(worktree.resolve("src/Main.java")),
                "The epic worktree should resolve to a worktree");

        assertAll(
                () -> assertEquals(PROJECT, resolved.projectId(), "The worktree should belong to the project"),
                () -> assertTrue(resolved.isEpicsWorktree(), "The worktree should be the shared epic worktree"));
    }

    @Test
    @DisplayName("resolves a worktree whose common directory is named by an absolute path")
    void resolvesAbsoluteCommonDirectory() throws Exception {
        var worktree = worktreeOfMain(PLAN);
        Files.writeString(main.resolve(".git/worktrees/" + PLAN + "/commondir"), main.resolve(".git") + "\n");

        assertEquals(new Worktree(PROJECT, PLAN), resolver.resolve(worktree.resolve("src/Main.java")));
    }

    @Test
    @DisplayName("resolves a baseline scratch worktree to no project, beside a plan worktree that resolves")
    void baselineWorktreeHasNoProject() throws Exception {
        var baseline = worktreeOfMain(BASELINE);
        var plan = worktreeOfMain(PLAN);

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.BASELINE_WORKTREE,
                        resolver.resolve(baseline.resolve("src/Main.java"))),
                () -> assertEquals(new Worktree(PROJECT, PLAN), resolver.resolve(plan.resolve("src/Main.java"))));
    }

    @ParameterizedTest(name = "baseline directory {0}")
    @ValueSource(strings = {"baseline-" + PLAN, "baseline-" + PLAN + "-0123", "baseline-ab-0123456789ab",
            "baseline-" + PLAN + "-0123456789AB", "baseline-Plan-Upper-0123456789ab", "baseline-"})
    @DisplayName("refuses every name with the prefix baseline- as a baseline worktree, beside a plan worktree that"
            + " resolves")
    void baselinePrefixHasNoProject(String name) throws Exception {
        var baseline = worktreeOfMain(name);
        var plan = worktreeOfMain(PLAN);

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.BASELINE_WORKTREE,
                        resolver.resolve(baseline.resolve("src/Main.java"))),
                () -> assertEquals(new Worktree(PROJECT, PLAN), resolver.resolve(plan.resolve("src/Main.java"))));
    }

    @Test
    @DisplayName("resolves a plan worktree whose name only begins like the baseline prefix to its plan")
    void nameWithoutBaselinePrefix() throws Exception {
        var worktree = worktreeOfMain("baselines-plan");

        assertEquals(new Worktree(PROJECT, "baselines-plan"), resolver.resolve(worktree.resolve("src/Main.java")));
    }

    @ParameterizedTest(name = "worktree directory {0}")
    @ValueSource(strings = {"Plan-Upper", "x", "_shared", "plan-"})
    @DisplayName("resolves a worktree below .marshall/local/worktrees whose name is no plan id to no project")
    void worktreeNamedOtherwise(String name) throws Exception {
        var worktree = worktreeOfMain(name);

        assertNotEnrolled(NotEnrolled.Reason.FOREIGN_TOPLEVEL, resolver.resolve(worktree.resolve("src/Main.java")));
    }

    @Test
    @DisplayName("resolves a worktree of the project that lies elsewhere to no project")
    void worktreeElsewhere() throws Exception {
        var beside = linkedWorktree(home.resolve("git/beside"), main, "beside");
        var below = linkedWorktree(main.resolve(".marshall/" + PLAN), main, "below");

        assertAll(
                () -> assertNotEnrolled(NotEnrolled.Reason.FOREIGN_TOPLEVEL,
                        resolver.resolve(beside.resolve("src/Main.java"))),
                () -> assertNotEnrolled(NotEnrolled.Reason.FOREIGN_TOPLEVEL,
                        resolver.resolve(below.resolve("src/Main.java"))));
    }

    @Test
    @DisplayName("resolves a worktree of a repository that is not enrolled to no project")
    void worktreeOfUnenrolledRepository() throws Exception {
        var other = repository("git/other");
        var worktree = linkedWorktree(other.resolve(".marshall/local/worktrees/" + PLAN), other, PLAN);

        assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(worktree.resolve("src/Main.java")));
    }
}
