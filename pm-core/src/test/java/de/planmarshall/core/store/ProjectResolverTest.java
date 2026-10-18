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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.store.ConfinementOutcome.Reason;
import de.planmarshall.core.store.ProjectEnrolment.Build;
import de.planmarshall.core.store.ProjectResolution.EnrolmentRefused;
import de.planmarshall.core.store.ProjectResolution.MainCheckout;
import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.PathRefused;
import de.planmarshall.core.store.ProjectResolution.RootConflict;
import de.planmarshall.core.store.ProjectResolution.Worktree;

/**
 * The Project Resolution Rule (PM-SEC-2, PM-ARCH-1). The fixtures are written by the test: the enrolment files of the
 * runtime's base directory, a main checkout with a {@code .git} directory, and the plain pointer files git writes for a
 * linked worktree below {@code <project_root>/.marshall/local/worktrees/}. No git process runs. Every refusal stands
 * beside the positive control of its resolving counterpart (PM-TEST-5).
 */
@DisplayName("ProjectResolver")
class ProjectResolverTest {

    private static final String PROJECT = "pm-mcp-core";

    private static final String PLAN = "plan-14-stores";

    private static final String BASELINE = "baseline-" + PLAN + "-0123456789ab";

    @TempDir
    Path temp;

    /** The canonical directory that holds the runtime base and the checkouts. */
    private Path home;

    /** The base directory of the runtime, whose {@code enrolments/} holds the enrolment files. */
    private Path base;

    /** The canonical main checkout of the enrolled project. */
    private Path main;

    /** The canonical directory of the worktrees of the project. */
    private Path worktrees;

    private ProjectResolver resolver;

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

    private Path repository(String belowHome) throws IOException {
        var repository = Files.createDirectories(home.resolve(belowHome).resolve(".git/worktrees")).getParent()
                .getParent();
        Files.writeString(repository.resolve("README.md"), belowHome);
        return repository;
    }

    private void enrol(String projectId, Path projectRoot) throws IOException {
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
    private static Path linkedWorktree(Path directory, Path repository, String name) throws IOException {
        Files.createDirectories(directory.resolve("src"));
        Files.writeString(directory.resolve("src/Main.java"), name);
        var gitDirectory = Files.createDirectories(repository.resolve(".git/worktrees").resolve(name));
        Files.writeString(gitDirectory.resolve("commondir"), "../..\n");
        Files.writeString(gitDirectory.resolve("gitdir"), directory.resolve(".git") + "\n");
        Files.writeString(directory.resolve(".git"), "gitdir: " + gitDirectory + "\n");
        return directory;
    }

    private Path worktreeOfMain(String name) throws IOException {
        return linkedWorktree(worktrees.resolve(name), main, name);
    }

    private static NotEnrolled assertNotEnrolled(NotEnrolled.Reason reason, ProjectResolution resolution) {
        var notEnrolled = assertInstanceOf(NotEnrolled.class, resolution, "The path should resolve to no project");
        assertAll("not enrolled",
                () -> assertEquals(ProjectResolution.PROJECT_NOT_ENROLLED, notEnrolled.code(),
                        "Every reason should carry the one outcome code"),
                () -> assertEquals(reason, notEnrolled.reason(), "The diagnostic should name the cause"));
        return notEnrolled;
    }

    private static void assertMainCheckout(ProjectResolution resolution) {
        assertEquals(new MainCheckout(PROJECT), resolution, "The path should lie in the main checkout");
    }

    @Nested
    @DisplayName("a path in the main checkout")
    class MainCheckoutPaths {

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

    @Nested
    @DisplayName("a path in a linked worktree")
    class WorktreePaths {

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

    @Nested
    @DisplayName("a nested checkout")
    class NestedCheckouts {

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

    @Nested
    @DisplayName("a moved checkout")
    class MovedCheckouts {

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

    @Nested
    @DisplayName("a path outside an enrolled project")
    class Unenrolled {

        @Test
        @DisplayName("resolves a repository that no enrolment names to no project")
        void unenrolledRepository() throws Exception {
            var other = repository("git/other");

            assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(other.resolve("README.md")));
        }

        @Test
        @DisplayName("resolves a directory outside any work tree to no project")
        void outsideAnyWorkTree() throws Exception {
            assumeFalse(insideWorkTree(home), "The temporary directory lies inside a git work tree");
            var plain = Files.createDirectories(home.resolve("plain"));
            Files.writeString(plain.resolve("file.txt"), "plain");

            assertNotEnrolled(NotEnrolled.Reason.NO_WORK_TREE, resolver.resolve(plain.resolve("file.txt")));
        }

        private static boolean insideWorkTree(Path directory) {
            for (var candidate = directory; candidate != null; candidate = candidate.getParent()) {
                if (Files.exists(candidate.resolve(".git"), LinkOption.NOFOLLOW_LINKS)) {
                    return true;
                }
            }
            return false;
        }

        @Test
        @DisplayName("resolves every path to no project when the enrolment directory does not exist")
        void withoutEnrolmentDirectory() {
            var empty = new ProjectResolver(home.resolve("no-runtime"));

            assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, empty.resolve(main.resolve("README.md")));
        }
    }

    @Nested
    @DisplayName("a .git that is no main checkout and no linked worktree")
    class UnrecognisedGit {

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

    @Nested
    @DisplayName("the enrolment files")
    class Enrolments {

        @Test
        @DisplayName("refuses a main checkout that two enrolments name as their project root")
        void rootConflict() throws Exception {
            enrol("pm-mcp-core-copy", main);

            var conflict = assertInstanceOf(RootConflict.class, resolver.resolve(main.resolve("README.md")),
                    "Two enrolments of one root should be a conflict");

            assertAll(
                    () -> assertEquals(ProjectResolution.ENROLMENT_ROOT_CONFLICT, conflict.code(),
                            "The conflict should carry its outcome code"),
                    () -> assertEquals(new TreeSet<>(List.of(PROJECT, "pm-mcp-core-copy")), conflict.projectIds(),
                            "The conflict should name both projects"));
        }

        @Test
        @DisplayName("refuses a worktree of a main checkout that two enrolments name")
        void rootConflictOfWorktree() throws Exception {
            enrol("pm-mcp-core-copy", main);
            var worktree = worktreeOfMain(PLAN);

            assertInstanceOf(RootConflict.class, resolver.resolve(worktree.resolve("src/Main.java")));
        }

        @Test
        @DisplayName("refuses with the reason of an unreadable enrolment file when no readable one names the root")
        void unreadableEnrolmentWithoutMatch() throws Exception {
            var store = new EnrolmentStore(base);
            Files.delete(store.file(PROJECT));
            Files.writeString(store.file("broken"), "{not json");

            var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")),
                    "An unreadable enrolment should not count as no enrolment");

            assertAll(
                    () -> assertEquals(StoreRefusal.Outcome.UNREADABLE, refused.refusal().outcome(),
                            "The refusal should be that of the file"),
                    () -> assertEquals(store.file("broken"), refused.refusal().file(),
                            "The refusal should name the file"));
        }

        @Test
        @DisplayName("refuses an enrolment file whose project id is not the name of its file")
        void enrolmentOfAnotherName() throws Exception {
            var store = new EnrolmentStore(base);
            Files.move(store.file(PROJECT), store.file("renamed"));

            var refused = assertInstanceOf(EnrolmentRefused.class, resolver.resolve(main.resolve("README.md")));

            assertEquals(store.file("renamed"), refused.refusal().file(), "The refusal should name the renamed file");
        }

        @Test
        @DisplayName("resolves the main checkout beside an unreadable enrolment file of another project")
        void unreadableEnrolmentBesideMatch() throws Exception {
            Files.writeString(new EnrolmentStore(base).file("broken"), "{not json");

            assertMainCheckout(resolver.resolve(main.resolve("README.md")));
        }

        @Test
        @DisplayName("resolves the main checkout beside an empty enrolment file and a sidecar without its file")
        void emptyEnrolmentAndSidecar() throws Exception {
            var store = new EnrolmentStore(base);
            Files.write(store.file("empty"), new byte[0]);
            Files.writeString(store.sidecar("other"), "sidecar");

            assertMainCheckout(resolver.resolve(main.resolve("README.md")));
        }

        @Test
        @DisplayName("resolves to no project when the enrolment of the project was removed")
        void removedEnrolment() throws Exception {
            Files.delete(new EnrolmentStore(base).file(PROJECT));

            assertNotEnrolled(NotEnrolled.Reason.NO_ENROLMENT, resolver.resolve(main.resolve("README.md")));
        }
    }

    @Nested
    @DisplayName("as a contract")
    class Contract {

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
}
