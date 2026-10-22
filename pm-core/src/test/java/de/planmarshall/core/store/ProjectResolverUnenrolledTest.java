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

import java.nio.file.Files;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectResolution.NotEnrolled;

/**
 * A path outside an enrolled project (PM-SEC-2, PM-ARCH-1): a repository no enrolment names, a plain directory outside
 * any work tree, and a runtime whose enrolment directory does not exist. Each resolves to no project, never to a
 * project the store does not hold.
 */
@DisplayName("ProjectResolver: a path outside an enrolled project")
class ProjectResolverUnenrolledTest extends ProjectResolverFixture {

    @Test
    @DisplayName("resolves a repository that no enrolment names to no project")
    void unenrolledRepository() throws Exception {
        var other = repository("git/other");

        assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(other.resolve("README.md")));
    }

    @Test
    @DisplayName("resolves a directory outside any work tree to no project")
    void outsideAnyWorkTree() throws Exception {
        requireOutsideWorkTree(home);
        var plain = Files.createDirectories(home.resolve("plain"));
        Files.writeString(plain.resolve("file.txt"), "plain");

        assertNotEnrolled(NotEnrolled.Reason.NO_WORK_TREE, resolver.resolve(plain.resolve("file.txt")));
    }

    @Test
    @DisplayName("resolves every path to no project when the enrolment directory does not exist")
    void withoutEnrolmentDirectory() {
        var empty = new ProjectResolver(home.resolve("no-runtime"));

        assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, empty.resolve(main.resolve("README.md")));
    }
}
