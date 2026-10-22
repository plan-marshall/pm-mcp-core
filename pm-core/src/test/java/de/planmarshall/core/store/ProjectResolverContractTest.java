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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectResolution.EnrolmentRefused;
import de.planmarshall.core.store.ProjectResolution.MainCheckout;
import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.PathRefused;
import de.planmarshall.core.store.ProjectResolution.RootConflict;
import de.planmarshall.core.store.ProjectResolution.Worktree;

/**
 * The contract of the resolution values (PM-SEC-2, PM-ARCH-1): a relative path and a missing argument are programming
 * errors, every value validates its own members, a conflict holds its own sorted unmodifiable set, and the shared epic
 * worktree is told from a plan worktree.
 */
@DisplayName("ProjectResolver: as a contract")
class ProjectResolverContractTest extends ProjectResolverFixture {

    @Test
    @DisplayName("rejects a relative path and a missing argument as a programming error")
    void invalidArguments() {
        var relative = Path.of("src/Main.java");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> resolver.resolve(relative),
                        "A relative path should be a programming error"),
                () -> assertThrows(NullPointerException.class, () -> resolver.resolve(null),
                        "A missing path should be a programming error"),
                () -> assertThrows(NullPointerException.class, () -> new ProjectResolver(null),
                        "A missing base should be a programming error"));
    }

    @Test
    @DisplayName("validates the values of a resolution")
    void resolutionValues() {
        var oneProject = new TreeSet<>(List.of(PROJECT));
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> new MainCheckout("Not An Id"),
                        "A project id outside the grammar should be refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new Worktree(PROJECT, "_other"),
                        "A worktree that is neither a plan id nor _epics should be refused"),
                () -> assertThrows(NullPointerException.class, () -> new Worktree(PROJECT, null),
                        "A missing worktree should be refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> new RootConflict(oneProject),
                        "A conflict of one project should be refused"),
                () -> assertThrows(NullPointerException.class, () -> new NotEnrolled(null),
                        "A missing reason should be refused"),
                () -> assertThrows(NullPointerException.class, () -> new PathRefused(null),
                        "A missing refusal should be refused"),
                () -> assertThrows(NullPointerException.class, () -> new EnrolmentRefused(null),
                        "A missing refusal should be refused"));
    }

    @Test
    @DisplayName("keeps the projects of a conflict sorted and unmodifiable")
    void conflictCopiesItsProjects() {
        var projects = new TreeSet<>(List.of("zeta-project", "alpha-project"));
        var conflict = new RootConflict(projects);

        projects.add("beta-project");
        var projectIds = conflict.projectIds();

        assertAll(
                () -> assertEquals(List.of("alpha-project", "zeta-project"), List.copyOf(projectIds),
                        "The conflict should hold its own sorted copy"),
                () -> assertThrows(UnsupportedOperationException.class,
                        () -> projectIds.add("gamma-project"),
                        "The projects of a conflict should not be modifiable"));
    }

    @Test
    @DisplayName("tells a plan worktree from the shared epic worktree")
    void epicsWorktree() {
        assertAll(
                () -> assertTrue(new Worktree(PROJECT, ProjectResolution.EPICS_WORKTREE).isEpicsWorktree()),
                () -> assertFalse(new Worktree(PROJECT, PLAN).isEpicsWorktree()));
    }
}
