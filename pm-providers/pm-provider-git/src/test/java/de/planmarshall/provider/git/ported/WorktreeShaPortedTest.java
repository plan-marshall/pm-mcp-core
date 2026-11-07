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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import org.junit.jupiter.api.BeforeEach;
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
 * <p>
 * The fixture and roster texts of the loader tests, the fixture names, the directive keywords, the step names of an
 * {@code unavailable} result and the refusal phrases are literals by design: they are the fixture grammar under test.
 * The digests and the failure texts of the comparison helper are generated.
 */
@EnableGeneratorController
@DisplayName("git.worktree-sha ported from plan-marshall")
class WorktreeShaPortedTest {

    /**
     * The number of fixtures the corpus holds at least: nine derived from pytest cases, three further cases of the
     * equality rule, and five deliberate changes. A directory that loses a fixture fails here instead of running a
     * smaller suite green.
     */
    static final int POPULATION_FLOOR = 17;

    /** The number of pytest cases of the original suite, the population the roster closes against. */
    private static final int ORIGINAL_CASES = 12;

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
        // a probe file outside the fixture, whose permissions are removed to learn whether the user can read past them
        Path probe = Files.writeString(temp.resolve("probe"), Generators.letterStrings(1, 10).next());
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
                        "the corpus holds no fewer fixtures than its floor"),
                () -> assertTrue(FIXTURES.stream().allMatch(f -> f.requires().isEmpty()
                                || f.requires().equals(Set.of(WorktreeShaScenario.POSIX_PERMISSIONS))),
                        "no fixture requires a capability no source runs"));
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
                        "an original fixture derives from the pinned suite"));
                header.cases().forEach(c -> derived.computeIfAbsent(c, k -> new TreeSet<>()).add(fixture.name()));
            } else {
                // the name prefix of a deliberate fixture is the contract of the fixture grammar
                checks.add(() -> assertTrue(fixture.name().startsWith("deliberate-"),
                        "a deliberate fixture is named deliberate-<change>"));
            }
        }
        for (var entry : roster.cases().entrySet()) {
            String pytestCase = entry.getKey();
            var rosterEntry = entry.getValue();
            if (rosterEntry.fixtures().isEmpty()) {
                checks.add(() -> assertFalse(rosterEntry.reason().isBlank(), "an excluded pytest case names a reason"));
                checks.add(() -> assertFalse(derived.containsKey(pytestCase),
                        "no fixture is derived from an excluded case"));
            } else {
                checks.add(() -> assertEquals(new TreeSet<>(rosterEntry.fixtures()),
                        derived.getOrDefault(pytestCase, Set.of()),
                        "the fixtures of a case are those of the roster"));
            }
        }
        for (String pytestCase : derived.keySet()) {
            checks.add(() -> assertTrue(roster.cases().containsKey(pytestCase),
                    "every case a fixture names is in the roster"));
        }
        checks.add(() -> assertEquals(ORIGINAL_CASES, roster.cases().size(),
                "the original suite has twelve pytest cases"));

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
                    () -> assertTrue(fixture.header().original(), "an original header is read as original"),
                    () -> assertEquals("t/test_a.py", fixture.header().pytestPath(),
                            "the header names the pytest file"),
                    () -> assertEquals(Set.of("test_one", "test_two"), fixture.header().cases(),
                            "the header names both pytest cases"),
                    () -> assertEquals(Set.of("posix-permissions"), fixture.requires(),
                            "the required capability is read"),
                    () -> assertEquals(List.of("repository", "write", "result"),
                            fixture.directives().stream().map(Directive::keyword).toList(),
                            "the directives are read in file order"),
                    () -> assertEquals(List.of("a.txt", "one two\\n"), fixture.directives().get(1).arguments(),
                            "the last argument keeps its spaces"));
        }

        @Test
        @DisplayName("reads a deliberate header named like its file")
        void readsDeliberate() {
            var fixture = parse("deliberate-x", "deliberate: deliberate-x\nrepository initial\nresult r\n");

            assertFalse(fixture.header().original(), "a deliberate header is not read as original");
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
                    () -> assertEquals("a\nb\tc\0d\\e", WorktreeShaScenario.unescape("a\\nb\\tc\\0d\\\\e"),
                            "each known escape resolves to its character"),
                    () -> assertThrows(IllegalArgumentException.class, () -> WorktreeShaScenario.unescape("a\\x"),
                            "an unknown escape is refused"),
                    () -> assertThrows(IllegalArgumentException.class, () -> WorktreeShaScenario.unescape("a\\"),
                            "an unfinished escape is refused"));
        }

        @Test
        @DisplayName("refuses a roster without its pinned suite, with an unmapped case or with a duplicate case")
        void refusesRoster() {
            String suite = "suite: " + PIN + " t/test_a.py\n";

            assertAll(
                    () -> assertEquals(Set.of("f1", "f2"),
                            PortedFixtures.roster(suite + "case test_a -> f1 f2\n").cases().get("test_a").fixtures(),
                            "a mapped case lists its fixtures"),
                    () -> assertThrows(IllegalArgumentException.class, () -> PortedFixtures.roster("case test_a -> f\n"),
                            "a roster without its suite is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> PortedFixtures.roster("suite: plan-marshall@b3aba30 t/test_a.py\n"),
                            "a roster of a foreign pin is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> PortedFixtures.roster(suite + "case test_a\n"),
                            "a case without mapping is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> PortedFixtures.roster(suite + "test_a -> f\n"),
                            "a line without the case keyword is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> PortedFixtures.roster(suite + "case test_a -> f\ncase test_a excluded: r\n"),
                            "a case listed twice is refused"),
                    () -> assertRefused("duplicate fixture",
                            () -> PortedFixtures.roster(suite + "case test_a -> f f\n")));
        }

        private void assertRefused(String reason, Executable parse) {
            var refusal = assertThrows(IllegalArgumentException.class, parse, "the fixture text is refused");
            assertTrue(refusal.getMessage().contains(reason), "the refusal message names its reason");
        }
    }

    @Nested
    @DisplayName("comparison helper")
    class Relations {

        private String digestA;
        private String digestB;

        @BeforeEach
        void digests() {
            var digests = Generators.uniqueValues(Generators.strings("0123456789abcdef", 64, 64));
            digestA = digests.next();
            digestB = digests.next();
        }

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

        /** @return an {@code unavailable} result of that step, its failure text generated */
        private GitResult<WorktreeSha> unavailable(String step) {
            return GitResult.of(GitOutcome.UNAVAILABLE, step + ": " + Generators.letterStrings(3, 30).next());
        }

        @Test
        @DisplayName("passes matching relations and fails each contradicted one")
        void relations() {
            Map<String, GitResult<WorktreeSha>> results = Map.of("a", ok(digestA), "a2", ok(digestA), "b", ok(digestB),
                    "u", unavailable("read"));

            DigestRelations.assertRelations(run(results, "same a a2", "different a b", "unavailable u read"));
            var contradicted = run(results, "different a a2", "same a b", "unavailable u read");
            var wrongReason = run(results, "same a a2", "different a b", "unavailable u head");
            var wrongLabel = run(results, "same a a2", "different a b", "unavailable a read");
            assertAll(
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(contradicted),
                            "contradicted relations fail"),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(wrongReason),
                            "an unavailable result of another step fails"),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(wrongLabel),
                            "an available result named unavailable fails"));
        }

        @Test
        @DisplayName("never lets an unavailable result enter an equality assertion")
        void unavailableNeverEqual() {
            var unavailable = unavailable("open");
            Map<String, GitResult<WorktreeSha>> results = Map.of("u", unavailable, "v", unavailable);
            var same = run(results, "same u v");
            var different = run(results, "different u v");

            assertAll(
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(same),
                            "unavailable results are never the same"),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(different),
                            "unavailable results are never different"));
        }

        @Test
        @DisplayName("fails a wrong hash version, a malformed digest, an unnamed result and an unknown label")
        void wellFormedness() {
            // a version other than the current one, and upper-case hex digits, which the digest format excludes
            var wrongVersion = run(Map.of("a", GitResult.ok(new WorktreeSha(digestA, DigestRelations.VERSION + 1)),
                    "b", ok(digestB)), "different a b");
            var malformedDigest = run(Map.of("a", ok(Generators.strings("ABCDEF", 64, 64).next()), "b", ok(digestB)),
                    "different a b");
            var unnamedResult = run(Map.of("a", ok(digestA), "b", ok(digestB), "c", ok(digestB)), "different a b");
            var unknownLabel = run(Map.of("a", ok(digestA)), "same a missing");
            var noRelation = run(Map.of("a", ok(digestA)));

            assertAll(
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(wrongVersion),
                            "a wrong hash version fails"),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(malformedDigest),
                            "a malformed digest fails"),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(unnamedResult),
                            "a result no relation names fails"),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(unknownLabel),
                            "a relation of an unknown label fails"),
                    () -> assertThrows(AssertionError.class, () -> DigestRelations.assertRelations(noRelation),
                            "a run without any relation fails"));
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

        // fixture paths: one fresh root per scenario
        var twoRepositoriesRoot = temp.resolve("2");
        var twiceRoot = temp.resolve("3");
        var unknownKindRoot = temp.resolve("4");

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> WorktreeShaScenario.run(noRepository, temp),
                        "a scenario without repository is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorktreeShaScenario.run(twoRepositories, twoRepositoriesRoot),
                        "a scenario with two repositories is refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> WorktreeShaScenario.run(twice, twiceRoot),
                        "a label recorded twice is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> WorktreeShaScenario.run(unknownKind, unknownKindRoot),
                        "an unknown repository kind is refused"));
    }
}
