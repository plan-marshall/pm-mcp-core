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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import de.planmarshall.core.store.ConfinementOutcome.Confined;
import de.planmarshall.core.store.ConfinementOutcome.Reason;
import de.planmarshall.core.store.ConfinementOutcome.Rejected;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The adversarial fixtures of the path confinement (PM-SEC-2). Every refusal stands beside the positive control
 * of its permitted counterpart (PM-TEST-5), so that a check that refuses everything fails here as well.
 */
@DisplayName("PathConfinement")
class PathConfinementTest {

    private static final String OUTCOME_CODE = "path_traversal_rejected";

    @TempDir
    Path temp;

    /** The canonical directory that holds the repository and its neighbours. */
    private Path base;

    /** The canonical enrolled root. */
    private Path root;

    /** A canonical directory beside the root. */
    private Path outside;

    private WorkspaceRoots roots;

    @BeforeEach
    void workspace() throws IOException {
        base = temp.toRealPath();
        root = Files.createDirectories(base.resolve("repo/src")).getParent();
        Files.writeString(root.resolve("README.md"), "inside");
        Files.writeString(root.resolve("src/Main.java"), "inside");
        outside = Files.createDirectories(base.resolve("outside/nested")).getParent();
        Files.writeString(outside.resolve("secret.txt"), "outside");
        Files.writeString(outside.resolve("nested/secret.txt"), "outside");
        roots = new WorkspaceRoots(temp.resolve("repo"), Set.of());
    }

    private ConfinementOutcome confine(String rawPath) {
        return PathConfinement.confine(roots, rawPath);
    }

    private static void assertRejected(Reason reason, ConfinementOutcome outcome) {
        var rejected = assertInstanceOf(Rejected.class, outcome, "The path should be refused");
        assertAll("refusal",
                () -> assertEquals(OUTCOME_CODE, rejected.code(), "A refusal should carry the closed outcome code"),
                () -> assertEquals(reason, rejected.reason(), "The reason should name the cause"));
    }

    private static Path assertConfined(ConfinementOutcome outcome) {
        return assertInstanceOf(Confined.class, outcome, "The path should be accepted").path();
    }

    private void assertConfinedTo(String expectedBelowRoot, ConfinementOutcome outcome) {
        assertEquals(root.resolve(expectedBelowRoot), assertConfined(outcome),
                "The outcome should carry the canonical path");
    }

    @Nested
    @DisplayName("with parent names")
    class ParentNames {

        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(strings = {
                "..",
                "../outside/secret.txt",
                "src/../../outside/secret.txt",
                "./src/./../../outside/nested/../secret.txt",
                "../../../../../../../../../../../../etc/passwd"})
        @DisplayName("refuses a path that climbs out of the root")
        void refusesClimbingOut(String rawPath) {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine(rawPath));
        }

        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(strings = {
                "missing/../../outside/secret.txt",
                "src/missing/..",
                "missing/deeper/../../file.txt"})
        @DisplayName("refuses a parent name below a part that does not exist")
        void refusesParentNameAfterMissingPart(String rawPath) {
            assertRejected(Reason.PARENT_NAME_AFTER_MISSING_PART, confine(rawPath));
        }

        @ParameterizedTest(name = "accepts {0} as {1}")
        @CsvSource({
                "src/../README.md, README.md",
                "src/../src/Main.java, src/Main.java",
                "./src/./Main.java, src/Main.java",
                "../repo/README.md, README.md"})
        @DisplayName("accepts a path whose parent names stay inside the root")
        void acceptsStayingInside(String rawPath, String expected) {
            assertConfinedTo(expected, confine(rawPath));
        }
    }

    @Nested
    @DisplayName("with absolute paths")
    class AbsolutePaths {

        @ParameterizedTest(name = "refuses <base>/{0}")
        @ValueSource(strings = {"outside/secret.txt", "outside", "", "repository/README.md", "repo.bak"})
        @DisplayName("refuses an absolute path beside the root, a name that only begins like the root included")
        void refusesOutside(String belowBase) throws Exception {
            Files.createDirectories(base.resolve("repository"));
            Files.writeString(base.resolve("repository/README.md"), "outside");

            var outcome = confine(base.resolve(belowBase).toString());

            assertRejected(Reason.OUTSIDE_WORKSPACE, outcome);
        }

        @Test
        @DisplayName("refuses the root of the filesystem")
        void refusesFilesystemRoot() {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine(root.getRoot().toString()));
        }

        @ParameterizedTest(name = "accepts <root>/{0}")
        @ValueSource(strings = {"README.md", "src/Main.java", "src", ""})
        @DisplayName("accepts an absolute path inside the root, the root itself included")
        void acceptsInside(String belowRoot) {
            var outcome = confine(root.resolve(belowRoot).toString());

            assertConfinedTo(belowRoot, outcome);
        }

        @Test
        @DisplayName("accepts the root when it is named through a symbolic link of the platform")
        void acceptsRootNamedAsEnrolled() {
            var outcome = confine(temp.resolve("repo/README.md").toString());

            assertConfinedTo("README.md", outcome);
        }
    }

    @Nested
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege there")
    @DisplayName("with symbolic links")
    class SymbolicLinks {

        @BeforeEach
        void links() throws IOException {
            Files.createSymbolicLink(root.resolve("file-out"), outside.resolve("secret.txt"));
            Files.createSymbolicLink(root.resolve("dir-out"), outside);
            Files.createSymbolicLink(root.resolve("relative-out"), Path.of("../outside/secret.txt"));
            Files.createSymbolicLink(root.resolve("chain-out-end"), outside.resolve("secret.txt"));
            Files.createSymbolicLink(root.resolve("chain-out-mid"), root.resolve("chain-out-end"));
            Files.createSymbolicLink(root.resolve("chain-out"), root.resolve("chain-out-mid"));
            Files.createSymbolicLink(root.resolve("src/mid-out"), outside);

            Files.createSymbolicLink(root.resolve("file-in"), root.resolve("README.md"));
            Files.createSymbolicLink(root.resolve("dir-in"), root.resolve("src"));
            Files.createSymbolicLink(root.resolve("chain-in-end"), root.resolve("README.md"));
            Files.createSymbolicLink(root.resolve("chain-in-mid"), root.resolve("chain-in-end"));
            Files.createSymbolicLink(root.resolve("chain-in"), root.resolve("chain-in-mid"));
            Files.createSymbolicLink(root.resolve("src/mid-in"), root);

            Files.createSymbolicLink(root.resolve("broken"), root.resolve("nowhere"));
            Files.createSymbolicLink(root.resolve("loop-a"), root.resolve("loop-b"));
            Files.createSymbolicLink(root.resolve("loop-b"), root.resolve("loop-a"));
        }

        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(strings = {
                "file-out",
                "dir-out",
                "dir-out/secret.txt",
                "relative-out",
                "chain-out",
                "src/mid-out/nested/secret.txt",
                "dir-out/not-yet.txt"})
        @DisplayName("refuses a link to a file, to a directory, a chain of links and a link in the middle that lead out")
        void refusesLinksOut(String rawPath) {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine(rawPath));
        }

        @ParameterizedTest(name = "accepts {0} as {1}")
        @CsvSource({
                "file-in, README.md",
                "dir-in, src",
                "dir-in/Main.java, src/Main.java",
                "chain-in, README.md",
                "src/mid-in/src/Main.java, src/Main.java",
                "dir-in/not-yet.txt, src/not-yet.txt"})
        @DisplayName("accepts the same four shapes when they stay inside, with the links resolved")
        void acceptsLinksInside(String rawPath, String expected) {
            assertConfinedTo(expected, confine(rawPath));
        }

        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(strings = {"broken", "broken/below", "loop-a", "loop-a/below"})
        @DisplayName("refuses a link without a target and a loop of links")
        void refusesLinksThatDoNotResolve(String rawPath) {
            assertRejected(Reason.CANONICALIZATION_FAILED, confine(rawPath));
        }
    }

    @Nested
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "creating a symbolic link needs a privilege there")
    @DisplayName("with case variants, judged on the path the filesystem resolves")
    class CaseVariants {

        @BeforeEach
        void link() throws IOException {
            Files.createSymbolicLink(root.resolve("link-out"), outside);
        }

        @Test
        @DisplayName("refuses the link out and accepts the file inside in their own case")
        void exactCase() {
            assertAll(
                    () -> assertRejected(Reason.OUTSIDE_WORKSPACE, confine("link-out/secret.txt")),
                    () -> assertConfinedTo("README.md", confine("README.md")));
        }

        @Test
        @EnabledOnOs(OS.MAC)
        @DisplayName("case-insensitive: refuses the variant that resolves to the link out")
        void insensitiveRefusesVariantOfLinkOut() {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine("LINK-OUT/secret.txt"));
        }

        @ParameterizedTest(name = "accepts {0}")
        @EnabledOnOs(OS.MAC)
        @ValueSource(strings = {"README.MD", "readme.md", "SRC/main.JAVA"})
        @DisplayName("case-insensitive: accepts the variant that resolves to a file inside the root")
        void insensitiveAcceptsVariantInside(String rawPath) {
            var path = assertConfined(confine(rawPath));

            assertAll(
                    () -> assertTrue(path.startsWith(root), "The canonical path should lie inside the root"),
                    () -> assertTrue(Files.exists(path), "The variant should resolve to the existing file"));
        }

        @Test
        @EnabledOnOs(OS.MAC)
        @DisplayName("case-insensitive: accepts a variant of the name of the root, which resolves to the root")
        void insensitiveAcceptsVariantOfRoot() {
            var path = assertConfined(confine(base.resolve("REPO/README.md").toString()));

            assertEquals(root.resolve("README.md"), path, "The outcome should carry the path in its true case");
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("case-sensitive: accepts the variant of the link out as a new name inside the root")
        void sensitiveAcceptsVariantOfLinkOut() {
            assertConfinedTo("LINK-OUT/secret.txt", confine("LINK-OUT/secret.txt"));
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("case-sensitive: accepts the variant of a file as a new name inside the root")
        void sensitiveAcceptsVariantInside() {
            var path = assertConfined(confine("README.MD"));

            assertAll(
                    () -> assertEquals(root.resolve("README.MD"), path, "The name should be kept as it is given"),
                    () -> assertFalse(Files.exists(path), "The variant should name no existing file"));
        }

        @Test
        @EnabledOnOs(OS.LINUX)
        @DisplayName("case-sensitive: refuses a variant of the name of the root, which is another directory")
        void sensitiveRefusesVariantOfRoot() {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine(base.resolve("REPO/README.md").toString()));
        }
    }

    /**
     * The characters that matter here are built from their code points and normalization forms, never written
     * into the source: a look-alike cannot be told from the original by reading, and a tool that rewrites the
     * file may change the form of a literal.
     */
    @Nested
    @DisplayName("with Unicode variants")
    class UnicodeVariants {

        private static final int LATIN_SMALL_E_WITH_ACUTE = 0x00E9;

        private static final int FULLWIDTH_FULL_STOP = 0xFF0E;

        private static final int ONE_DOT_LEADER = 0x2024;

        private static final int TWO_DOT_LEADER = 0x2025;

        private static final int DIVISION_SLASH = 0x2215;

        private static final int CYRILLIC_SMALL_IE = 0x0435;

        private static final int GREEK_SMALL_OMICRON = 0x03BF;

        /** The fullwidth forms of the letters r, e, p and o. */
        private static final int[] FULLWIDTH_REPO = {0xFF52, 0xFF45, 0xFF50, 0xFF4F};

        private static String text(int... codePoints) {
            return new String(codePoints, 0, codePoints.length);
        }

        private static String accented(Normalizer.Form form) {
            return Normalizer.normalize("caf" + text(LATIN_SMALL_E_WITH_ACUTE), form);
        }

        static Stream<String> lookAlikesOfParentName() {
            return Stream.of(
                    text(FULLWIDTH_FULL_STOP, FULLWIDTH_FULL_STOP) + "/outside/secret.txt",
                    text(ONE_DOT_LEADER, ONE_DOT_LEADER) + "/outside/secret.txt",
                    text(TWO_DOT_LEADER) + "/outside/secret.txt",
                    ".." + text(DIVISION_SLASH) + "outside" + text(DIVISION_SLASH) + "secret.txt");
        }

        static Stream<String> lookAlikesOfRoot() {
            return Stream.of(
                    "r" + text(CYRILLIC_SMALL_IE) + "po",
                    "rep" + text(GREEK_SMALL_OMICRON),
                    text(FULLWIDTH_REPO));
        }

        @BeforeEach
        void names() throws IOException {
            var composed = accented(Normalizer.Form.NFC);
            Files.writeString(root.resolve(composed + ".txt"), "inside");
            Files.createDirectories(base.resolve(composed));
            Files.writeString(base.resolve(composed).resolve("secret.txt"), "outside");
        }

        @ParameterizedTest(name = "form {0}")
        @EnumSource(names = {"NFC", "NFD"})
        @DisplayName("refuses the composed and the decomposed form of a name beside the root")
        void refusesBothFormsOutside(Normalizer.Form form) {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine("../" + accented(form) + "/secret.txt"));
        }

        @ParameterizedTest(name = "form {0}")
        @EnumSource(names = {"NFC", "NFD"})
        @DisplayName("accepts the composed and the decomposed form of a name inside the root")
        void acceptsBothFormsInside(Normalizer.Form form) {
            var path = assertConfined(confine(accented(form) + ".txt"));

            assertTrue(path.startsWith(root), "Either form should resolve to a place inside the root");
        }

        @Test
        @DisplayName("tells the two forms apart, so the fixture does test a variant")
        void formsDiffer() {
            assertNotEquals(accented(Normalizer.Form.NFC), accented(Normalizer.Form.NFD),
                    "The composed and the decomposed form should differ as text");
        }

        @ParameterizedTest(name = "look-alike {index}")
        @MethodSource("lookAlikesOfParentName")
        @DisplayName("accepts a look-alike of the parent name, which is an ordinary name inside the root")
        void acceptsLookAlikeOfParentName(String rawPath) {
            assertConfinedTo(rawPath, confine(rawPath));
        }

        @ParameterizedTest(name = "look-alike {index}")
        @MethodSource("lookAlikesOfRoot")
        @DisplayName("refuses a look-alike of the name of the root, which is another directory")
        void refusesLookAlikeOfRoot(String name) {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine(base.resolve(name).resolve("README.md").toString()));
        }

        @Test
        @DisplayName("refuses a real parent name behind a look-alike")
        void refusesParentNameBehindLookAlike() {
            var lookAlike = text(FULLWIDTH_FULL_STOP, FULLWIDTH_FULL_STOP);

            assertRejected(Reason.PARENT_NAME_AFTER_MISSING_PART, confine(lookAlike + "/../../outside/secret.txt"));
        }

        @ParameterizedTest(name = "control character {index}")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a name holds no control character there")
        @ValueSource(strings = {"new\nline.txt", "src/tab\there.txt", "bell\u0007.txt", "\u007fdelete"})
        @DisplayName("accepts a control character in a name inside the root")
        void acceptsControlCharacterInside(String rawPath) {
            assertConfinedTo(rawPath, confine(rawPath));
        }

        @ParameterizedTest(name = "control character {index}")
        @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a name holds no control character there")
        @ValueSource(strings = {"../outside/new\nline.txt", "../out\tside/secret.txt", "../outside/bell\u0007.txt"})
        @DisplayName("refuses a path with a control character that leaves the root")
        void refusesControlCharacterOutside(String rawPath) {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine(rawPath));
        }

        @ParameterizedTest(name = "NUL {index}")
        @ValueSource(strings = {"\0", "README.md\0", "README.md\0.txt", "src/\0/Main.java", "../outside/secret.txt\0"})
        @DisplayName("refuses an embedded NUL as a value, not with an exception")
        void refusesEmbeddedNul(String rawPath) {
            assertRejected(Reason.UNPARSEABLE_PATH, confine(rawPath));
        }

        @Test
        @DisplayName("accepts the same name without the NUL")
        void acceptsNameWithoutNul() {
            assertConfinedTo("README.md", confine("README.md"));
        }
    }

    @Nested
    @DisplayName("with a target that does not exist yet")
    class MissingTargets {

        @ParameterizedTest(name = "accepts {0} as {1}")
        @CsvSource({
                "new.txt, new.txt",
                "src/new/deep/File.java, src/new/deep/File.java",
                "new/./File.java, new/File.java",
                "src/../new/File.java, new/File.java"})
        @DisplayName("accepts new names below an existing part inside the root")
        void acceptsNewNamesInside(String rawPath, String expected) {
            var outcome = confine(rawPath);

            assertConfinedTo(expected, outcome);
            assertFalse(Files.exists(assertConfined(outcome)), "The check should create nothing");
        }

        @ParameterizedTest(name = "refuses {0}")
        @ValueSource(strings = {"../outside/new.txt", "../not-there/new.txt", "../outside/nested/new/deep.txt"})
        @DisplayName("refuses new names below an existing part outside the root")
        void refusesNewNamesOutside(String rawPath) {
            assertRejected(Reason.OUTSIDE_WORKSPACE, confine(rawPath));
        }

        @Test
        @DisplayName("refuses every path when the enrolled root does not exist")
        void refusesWithoutRoot() {
            var missingRoot = new WorkspaceRoots(base.resolve("not-enrolled"), Set.of());

            var outcome = PathConfinement.confine(missingRoot, "README.md");

            assertRejected(Reason.CANONICALIZATION_FAILED, outcome);
        }
    }

    @Nested
    @DisplayName("as a contract")
    class Contract {

        @Test
        @DisplayName("declares the closed outcome code")
        void outcomeCode() {
            assertEquals(OUTCOME_CODE, ConfinementOutcome.PATH_TRAVERSAL_REJECTED,
                    "The outcome code is the string of the specification");
        }

        @Test
        @DisplayName("accepts the empty path as the root, against which a relative path is resolved")
        void emptyPathIsTheRoot() {
            assertEquals(root, assertConfined(confine("")), "The empty path should be the enrolled root");
        }

        @Test
        @DisplayName("rejects a missing argument and a missing component as a programming error")
        void nullArguments() {
            assertAll(
                    () -> assertThrows(NullPointerException.class, () -> PathConfinement.confine(null, "README.md"),
                            "Missing roots should be a programming error"),
                    () -> assertThrows(NullPointerException.class, () -> PathConfinement.confine(roots, null),
                            "A missing path should be a programming error"),
                    () -> assertThrows(NullPointerException.class, () -> new WorkspaceRoots(null, Set.of()),
                            "A missing enrolled root should be a programming error"),
                    () -> assertThrows(NullPointerException.class, () -> new WorkspaceRoots(root, null),
                            "A missing worktree set should be a programming error"),
                    () -> assertThrows(NullPointerException.class, () -> new Confined(null),
                            "A missing canonical path should be a programming error"),
                    () -> assertThrows(NullPointerException.class, () -> new Rejected(null),
                            "A missing reason should be a programming error"));
        }

        @Test
        @DisplayName("copies the live worktrees, so a later change of the caller's set is not seen")
        void worktreesAreCopied() {
            var worktrees = new HashSet<Path>();
            var copied = new WorkspaceRoots(root, worktrees);

            worktrees.add(outside);

            assertTrue(copied.liveWorktrees().isEmpty(), "The record should hold its own copy of the set");
        }
    }
}
