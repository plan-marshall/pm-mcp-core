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
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.ProjectResolution.MainCheckout;
import de.planmarshall.core.store.ProjectResolution.NotEnrolled;

/**
 * The fixtures of the Project Resolution tests (PM-SEC-2, PM-ARCH-1): the enrolment files of the runtime's base
 * directory, a main checkout with a {@code .git} directory, and the plain pointer files git writes for a linked
 * worktree below {@code <project_root>/.marshall/local/worktrees/}. No git process runs. Each test class of the
 * resolution holds one group of its cases and shares these fixtures.
 */
abstract class ProjectResolverFixture {

    static final String PROJECT = "pm-mcp-core";

    static final String PLAN = "plan-14-stores";

    static final String BASELINE = "baseline-" + PLAN + "-0123456789ab";

    @TempDir
    Path temp;

    /** The canonical directory that holds the runtime base and the checkouts. */
    Path home;

    /** The base directory of the runtime, whose {@code enrolments/} holds the enrolment files. */
    Path base;

    /** The canonical main checkout of the enrolled project. */
    Path main;

    /** The canonical directory of the worktrees of the project. */
    Path worktrees;

    ProjectResolver resolver;

    @BeforeEach
    void enrolledProject() throws IOException {
        home = temp.toRealPath();
        base = Files.createDirectories(home.resolve("runtime/" + EnrolmentStore.DIRECTORY_NAME)).getParent();
        main = repository("git/" + PROJECT);
        Files.createDirectories(main.resolve("src"));
        Files.writeString(main.resolve("src/Main.java"), "main");
        worktrees = Files.createDirectories(main.resolve(".marshall/local/worktrees"));
        enrol(PROJECT, main);
        resolver = new ProjectResolver(base);
    }

    Path repository(String belowHome) throws IOException {
        var repository = Files.createDirectories(home.resolve(belowHome).resolve(".git/worktrees")).getParent()
                .getParent();
        Files.writeString(repository.resolve("README.md"), belowHome);
        return repository;
    }

    void enrol(String projectId, Path projectRoot) throws IOException {
        var enrolment = new ProjectEnrolment(projectId, projectRoot.toString(), Instant.parse("2026-10-11T08:00:00Z"),
                false, null, null, List.of(), List.of(), Build.defaults(), Map.of(), new TreeMap<>(), new TreeMap<>(),
                new TreeMap<>(), List.of(), new TreeMap<>());
        Files.write(new EnrolmentStore(base).file(projectId), EnrolmentCodec.write(enrolment));
    }

    /**
     * Creates a directory with a file and writes the three pointer files that make it a linked worktree of the
     * repository: the {@code .git} file, and in its git directory {@code .git/worktrees/<name>} the {@code commondir}
     * file and the {@code gitdir} record that names the worktree back.
     */
    static Path linkedWorktree(Path directory, Path repository, String name) throws IOException {
        Files.createDirectories(directory.resolve("src"));
        Files.writeString(directory.resolve("src/Main.java"), name);
        var gitDirectory = Files.createDirectories(repository.resolve(".git/worktrees").resolve(name));
        Files.writeString(gitDirectory.resolve("commondir"), "../..\n");
        Files.writeString(gitDirectory.resolve("gitdir"), directory.resolve(".git") + "\n");
        Files.writeString(directory.resolve(".git"), "gitdir: " + gitDirectory + "\n");
        return directory;
    }

    Path worktreeOfMain(String name) throws IOException {
        return linkedWorktree(worktrees.resolve(name), main, name);
    }

    static NotEnrolled assertNotEnrolled(NotEnrolled.Reason reason, ProjectResolution resolution) {
        var notEnrolled = assertInstanceOf(NotEnrolled.class, resolution, "The path should resolve to no project");
        assertAll("not enrolled",
                () -> assertEquals(ProjectResolution.PROJECT_NOT_ENROLLED, notEnrolled.code(),
                        "Every reason should carry the one outcome code"),
                () -> assertEquals(reason, notEnrolled.reason(), "The diagnostic should name the cause"));
        return notEnrolled;
    }

    static void assertMainCheckout(ProjectResolution resolution) {
        assertEquals(new MainCheckout(PROJECT), resolution, "The path should lie in the main checkout");
    }

    static boolean insideWorkTree(Path directory) {
        for (var candidate = directory; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
        }
        return false;
    }

    /** The assumption of the tests that need a path outside any work tree. */
    static void requireOutsideWorkTree(Path directory) {
        assumeFalse(insideWorkTree(directory), "The temporary directory lies inside a git work tree");
    }
}
