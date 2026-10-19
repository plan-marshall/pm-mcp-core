/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.ported;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import de.planmarshall.provider.git.GitOperations.WorktreeSha;
import de.planmarshall.provider.git.GitOutcome;
import de.planmarshall.provider.git.GitResult;
import de.planmarshall.provider.git.ported.PortedFixtures.Directive;
import de.planmarshall.provider.git.ported.PortedFixtures.Fixture;
import de.planmarshall.provider.git.ported.PortedFixtures.Header;

/**
 * Ported-primitive tests of {@code git.worktree-sha} (PM-TEST-2, PM-MIG-4), derived from
 * {@code script-shared/scripts/worktree_sha.py} and {@code test_worktree_sha.py} of {@code plan-marshall} at the
 * pinned commit, over the plain-file fixtures of {@code ported/worktree-sha}.
 * <p>
 * Each fixture builds a repository, records labelled digests through the public contract and states their equality
 * behaviour: the same tree yields the same digest, and a change of tracked content, an untracked path or its
 * content, a deletion or {@code HEAD} yields a different one; a failure is {@code unavailable} with its failing step.
 * The deliberate-change fixtures assert the corrected behaviour where the original hashes git's textual output, folds
 * an unreadable file in as empty content, or frames untracked entries ambiguously. The roster test closes the corpus
 * in both directions against the original's twelve pytest cases.
 */
@DisplayName("git.worktree-sha ported from plan-marshall")
class WorktreeShaPortedTest {

    /**
     * The number of fixtures the corpus holds at least: nine derived from pytest cases, three further cases of the
     * equality rule, and five deliberate changes. A directory that loses a fixture fails here instead of running a
     * smaller suite green.
     */
    static final int POPULATION_FLOOR = 17;

    private static final List<Fixture> FIXTURES = WorktreeShaScenario.fixtures();

    static Stream<Fixture> portableFixtures() {
        return populated(FIXTURES.stream().filter(f -> f.requires().isEmpty()).toList());
    }

    static Stream<Fixture> posixFixtures() {
        return populated(FIXTURES.stream()
                .filter(f -> f.requires().equals(Set.of(WorktreeShaScenario.POSIX_PERMISSIONS))).toList());
    }

    private static Stream<Fixture> populated(List<Fixture> fixtures) {
        if (fixtures.isEmpty()) {
            throw new IllegalStateException("no fixture of " + WorktreeShaScenario.DIRECTORY + " for this source");
        }
        return fixtures.stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("portableFixtures")
    @DisplayName("holds the equality behaviour of each fixture")
    void equalityBehaviour(Fixture fixture, @TempDir Path temp) throws Exception {
        var run = WorktreeShaScenario.run(fixture, temp);

        DigestRelations.assertRelations(run);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("posixFixtures")
    @EnabledOnOs(value = {OS.LINUX, OS.MAC}, disabledReason = "the fixture removes POSIX file permissions")
    @DisplayName("holds the equality behaviour of each fixture that needs POSIX permissions")
    void posixEqualityBehaviour(Fixture fixture, @TempDir Path temp) throws Exception {
        Path probe = Files.writeString(temp.resolve("probe"), "p");
        Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("---------"));
        Assumptions.assumeFalse(Files.isReadable(probe),
                "the running user reads a file without read permission (root), so no file is unreadable to it");
        Files.delete(probe);

        var run = WorktreeShaScenario.run(fixture, temp);

        DigestRelations.assertRelations(run);
    }

    @Test
    @DisplayName("runs no smaller corpus than its population floor, every fixture requiring only known capabilities")
    void populationFloor() {
        assertAll(
                () -> assertTrue(FIXTURES.size() >= POPULATION_FLOOR,
                        "the corpus holds " + FIXTURES.size() + " fixtures, fewer than " + POPULATION_FLOOR),
                () -> assertTrue(FIXTURES.stream().allMatch(f -> f.requires().isEmpty()
                        || f.requires().equals(Set.of(WorktreeShaScenario.POSIX_PERMISSIONS))),
                        "a fixture requires a capability no test source runs"));
    }

    @Test
    @DisplayName("maps every pytest case of the original to a fixture or a reasoned exclusion, and back")
    void roster() {
        var roster = PortedFixtures.roster(PortedFixtures.read(WorktreeShaScenario.DIRECTORY, PortedFixtures.ROSTER));
        Map<String, Set<String>> derived = new TreeMap<>();
        var checks = new ArrayList<Executable>();
        for (Fixture fixture : FIXTURES) {
            Header header = fixture.header();
            if (header.original()) {
                checks.add(() -> assertEquals(roster.pytestPath(), header.pytestPath(),
                        fixture + " is derived from another suite"));
                header.cases().forEach(c -> derived.computeIfAbsent(c, k -> new TreeSet<>()).add(fixture.name()));
            } else {
                checks.add(() -> assertTrue(fixture.name().startsWith("deliberate-"),
                        fixture + " is headed deliberate but not named deliberate-<change>"));
            }
        }
        for (var entry : roster.cases().entrySet()) {
            String pytestCase = entry.getKey();
            var rosterEntry = entry.getValue();
            if (rosterEntry.fixtures().isEmpty()) {
                checks.add(() -> assertFalse(rosterEntry.reason().isBlank(), pytestCase + " is excluded without a reason"));
                checks.add(() -> assertFalse(derived.containsKey(pytestCase),
                        pytestCase + " is excluded, yet " + derived.get(pytestCase) + " name it"));
            } else {
                checks.add(() -> assertEquals(new TreeSet<>(rosterEntry.fixtures()),
                        derived.getOrDefault(pytestCase, Set.of()),
                        "the fixtures derived from " + pytestCase + " differ from the roster"));
            }
        }
        for (String pytestCase : derived.keySet()) {
            checks.add(() -> assertTrue(roster.cases().containsKey(pytestCase),
                    derived.get(pytestCase) + " name " + pytestCase + ", which the roster does not list"));
        }
        checks.add(() -> assertEquals(12, roster.cases().size(), "the original suite has twelve pytest cases"));

        assertAll("roster", checks);
    }

    @Nested
    @DisplayName("fixture loader")
    class Loader {

        private static final String PIN = "plan-marshall@" + PortedFixtures.INVENTORY_PIN;

        private Fixture parse(String name, String text) {
            return PortedFixtures.parse(name, text, WorktreeShaScenario.GRAMMAR);
        }

        @Test
        @DisplayName("reads an original header with its cases and the directives in file order")
        void readsOriginal() {
            var fixture = parse("x", "# comment\noriginal: " + PIN + " t/test_a.py::test_one t/test_a.py::test_two\n"
                    + "requires: posix-permissions\nrepository initial\nwrite a.txt one two\\n\nresult r\n");

            assertAll(
                    () -> assertTrue(fixture.header().original()),
                    () -> assertEquals("t/test_a.py", fixture.header().pytestPath()),
                    () -> assertEquals(Set.of("test_one", "test_two"), fixture.header().cases()),
                    () -> assertEquals(Set.of("posix-permissions"), fixture.requires()),
                    () -> assertEquals(List.of("repository", "write", "result"),
                            fixture.directives().stream().map(Directive::keyword).toList()),
                    () -> assertEquals(List.of("a.txt", "one two\\n"), fixture.directives().get(1).arguments()));
        }

        @Test
        @DisplayName("reads a deliberate header named like its file")
        void readsDeliberate() {
            var fixture = parse("deliberate-x", "deliberate: deliberate-x\nrepository initial\nresult r\n");

            assertFalse(fixture.header().original());
        }

        @Test
        @DisplayName("refuses an unknown header, a foreign pin, a mismatched deliberate name and a missing case")
        void refusesHeaders() {
            assertAll(
                    () -> assertRefused("unknown header", () -> parse("x", "derived: x\nresult r\n")),
                    () -> assertRefused("not the inventory pin",
                            () -> parse("x", "original: plan-marshall@b3aba30 t/test_a.py::test_one\nresult r\n")),
                    () -> assertRefused("not its file name", () -> parse("x", "deliberate: y\nresult r\n")),
                    () -> assertRefused("names no pytest case", () -> parse("x", "original: " + PIN + "\nresult r\n")),
                    () -> assertRefused("malformed pytest case",
                            () -> parse("x", "original: " + PIN + " t/a.py\nresult r\n")),
                    () -> assertRefused("two pytest suites",
                            () -> parse("x", "original: " + PIN + " t/a.py::test_a t/b.py::test_b\nresult r\n")),
                    () -> assertRefused("has no header", () -> parse("x", "# only a comment\n")),
                    () -> assertRefused("has no directive", () -> parse("x", "deliberate: x\n")));
        }

        @Test
        @DisplayName("refuses a directive the grammar does not declare or with the wrong number of arguments")
        void refusesDirectives() {
            String header = "deliberate: x\n";

            assertAll(
                    () -> assertRefused("does not declare", () -> parse("x", header + "rename a b\n")),
                    () -> assertRefused("takes 2 argument", () -> parse("x", header + "same a\n")),
                    () -> assertRefused("takes 0 argument", () -> parse("x", header + "detach now\n")),
                    () -> assertRefused("takes 2 argument", () -> parse("x", header + "write a.txt\n")),
                    () -> assertRefused("takes 1 argument", () -> parse("x", header + "commit\n")),
                    () -> assertRefused("takes 1 argument", () -> parse("x", header + "result a  \n")));
        }

        @Test
        @DisplayName("resolves the content escapes and refuses an unknown or unfinished one")
        void unescapes() {
            assertAll(
                    () -> assertEquals("a\nb\tc\0d\\e", WorktreeShaScenario.unescape("a\\nb\\tc\\0d\\\\e")),
                    () -> assertThrows(IllegalArgumentException.class, () -> WorktreeShaScenario.unescape("a\\x")),
                    () -> assertThrows(IllegalArgumentException.class, () -> WorktreeShaScenario.unescape("a\\")));
        }

        @Test
        @DisplayName("refuses a roster without its pinned suite, with an unmapped case or with a duplicate case")
        void refusesRoster() {
            String suite = "suite: " + PIN + " t/test_a.py\n";

            assertAll(
                    () -> assertEquals(Set.of("f1", "f2"),
                            PortedFixtures.roster(suite + "case test_a -> f1 f2\n").cases().get("test_a").fixtures()),
                    () -> assertThrows(IllegalArgumentException.class, () -> PortedFixtures.roster("case test_a -> f\n")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> PortedFixtures.roster("suite: plan-marshall@b3aba30 t/test_a.py\n")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> PortedFixtures.roster(suite + "case test_a\n")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> PortedFixtures.roster(suite + "test_a -> f\n")),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> PortedFixtures.roster(suite + "case test_a -> f\ncase test_a excluded: r\n")));
        }

        private void assertRefused(String reason, Executable parse) {
            var refusal = assertThrows(IllegalArgumentException.class, parse);
            assertTrue(refusal.getMessage().contains(reason), refusal.getMessage());
        }
    }

    @Nested
    @DisplayName("comparison helper")
    class Relations {

        private static final String DIGEST_A = "a".repeat(64);
        private static final String DIGEST_B = "b".repeat(64);

        private WorktreeShaScenario.Run run(Map<String, GitResult<WorktreeSha>> results, String... expectations) {
            var directives = new ArrayList<Directive>();
            for (String expectation : expectations) {
                String[] parts = expectation.split(" ");
                directives.add(new Directive(directives.size() + 1, parts[0], List.of(parts).subList(1, parts.length)));
            }
            var fixture = new Fixture("relations", new Header(false, "", Set.of()), Set.of(), List.copyOf(directives));
            return new WorktreeShaScenario.Run(fixture, results, List.copyOf(directives));
        }

        private GitResult<WorktreeSha> ok(String digest) {
            return GitResult.ok(new WorktreeSha(digest, DigestRelations.VERSION));
        }

        @Test
        @DisplayName("passes matching relations and fails each contradicted one")
        void relations() {
            Map<String, GitResult<WorktreeSha>> results = Map.of("a", ok(DIGEST_A), "a2", ok(DIGEST_A), "b", ok(DIGEST_B),
                    "u", GitResult.of(GitOutcome.UNAVAILABLE, "read: java.nio.file.AccessDeniedException"));

            DigestRelations.assertRelations(run(results, "same a a2", "different a b", "unavailable u read"));
            assertAll(
                    () -> assertThrows(AssertionError.class,
                            () -> DigestRelations.assertRelations(run(results, "different a a2", "same a b",
                                    "unavailable u read"))),
                    () -> assertThrows(AssertionError.class,
                            () -> DigestRelations.assertRelations(run(results, "same a a2", "different a b",
                                    "unavailable u head"))),
                    () -> assertThrows(AssertionError.class,
                            () -> DigestRelations.assertRelations(run(results, "same a a2", "different a b",
                                    "unavailable a read"))));
        }

        @Test
        @DisplayName("never lets an unavailable result enter an equality assertion")
        void unavailableNeverEqual() {
            var unavailable = GitResult.<WorktreeSha>of(GitOutcome.UNAVAILABLE, "open: x");
            Map<String, GitResult<WorktreeSha>> results = Map.of("u", unavailable, "v", unavailable);

            assertAll(
                    () -> assertThrows(AssertionError.class,
                            () -> DigestRelations.assertRelations(run(results, "same u v"))),
                    () -> assertThrows(AssertionError.class,
                            () -> DigestRelations.assertRelations(run(results, "different u v"))));
        }

        @Test
        @DisplayName("fails a wrong hash version, a malformed digest, an unnamed result and an unknown label")
        void wellFormedness() {
            assertAll(
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(
                            run(Map.of("a", GitResult.ok(new WorktreeSha(DIGEST_A, 2)), "b", ok(DIGEST_B)),
                                    "different a b"))),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(
                            run(Map.of("a", ok("A".repeat(64)), "b", ok(DIGEST_B)), "different a b"))),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(
                            run(Map.of("a", ok(DIGEST_A), "b", ok(DIGEST_B), "c", ok(DIGEST_B)), "different a b"))),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(
                            run(Map.of("a", ok(DIGEST_A)), "same a missing"))),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(
                            run(Map.of("a", ok(DIGEST_A))))));
        }
    }

    @Test
    @DisplayName("refuses a scenario without a repository, with two repositories or a label recorded twice")
    void refusesScenario(@TempDir Path temp) {
        var noRepository = new Fixture("x", new Header(false, "", Set.of()), Set.of(),
                List.of(new Directive(1, "result", List.of("r"))));
        var twoRepositories = new Fixture("y", new Header(false, "", Set.of()), Set.of(), List.of(
                new Directive(1, "repository", List.of("none")), new Directive(2, "repository", List.of("none"))));
        var twice = new Fixture("z", new Header(false, "", Set.of()), Set.of(), List.of(
                new Directive(1, "repository", List.of("none")), new Directive(2, "result", List.of("r")),
                new Directive(3, "result", List.of("r"))));
        var unknownKind = new Fixture("w", new Header(false, "", Set.of()), Set.of(),
                List.of(new Directive(1, "repository", List.of("bare"))));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> WorktreeShaScenario.run(noRepository, temp)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorktreeShaScenario.run(twoRepositories, temp.resolve("2"))),
                () -> assertThrows(IllegalArgumentException.class, () -> WorktreeShaScenario.run(twice, temp.resolve("3"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorktreeShaScenario.run(unknownKind, temp.resolve("4"))));
    }
}
