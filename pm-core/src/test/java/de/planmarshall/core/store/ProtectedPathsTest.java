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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SpecificationDocument;

/**
 * The protected paths of the hook definitions (PM-WF-11, PM-SEC-5): the family of
 * {@code phase-workflows/03-q-gate-and-execute.adoc} § Protected Paths, matched at the repository root, ignoring case
 * and after Unicode NFC normalization, and lifted only for the paths a plan declares.
 * <p>
 * Every datum in this class is the specification's contract: the six patterns, the case and normalization rules, and
 * the near misses. The test is the display of the names the specification states once.
 */
@DisplayName("Protected paths: the hook definitions")
class ProtectedPathsTest {

    @Test
    @DisplayName("every hook definition of the specification is protected at the root")
    void everyHookDefinitionIsProtected() {
        var paths = List.of(".husky/pre-commit", ".githooks/pre-push", "lefthook.yml", ".lefthook.yml",
                "lefthook-local.yml", ".pre-commit-config.yaml");

        var protectedPaths = ProtectedPaths.of(paths, Set.of());

        assertEquals(paths, protectedPaths, "the six hook definitions are protected");
    }

    @Test
    @DisplayName("a nested file below a hook directory is protected")
    void nestedFileIsProtected() {
        var paths = List.of(".husky/nested/hook", ".githooks/sub/dir/hook");

        assertEquals(paths, ProtectedPaths.of(paths, Set.of()), "a nested file below .husky/ is protected");
    }

    @Test
    @DisplayName("case variants of the hook directories are protected")
    void caseVariantsAreProtected() {
        var paths = List.of(".HUSKY/pre-commit", ".GitHooks/x");

        assertEquals(paths, ProtectedPaths.of(paths, Set.of()), "case variants are protected");
    }

    @Test
    @DisplayName("a decomposed character in a path below .husky/ is protected")
    void decomposedCharacterIsProtected() {
        var decomposed = ".husky/" + Normalizer.normalize("é", Normalizer.Form.NFD) + "-hook";
        var paths = List.of(decomposed);

        assertEquals(paths, ProtectedPaths.of(paths, Set.of()), "NFC normalization matches the decomposed name");
    }

    @Test
    @DisplayName("a decomposed declared path lifts the composed path")
    void decomposedDeclaredPathLiftsComposedPath() {
        var composed = ".husky/" + Normalizer.normalize("é", Normalizer.Form.NFC) + "-hook";
        var decomposed = Normalizer.normalize(composed, Normalizer.Form.NFD);

        var protectedPaths = ProtectedPaths.of(List.of(composed), Set.of(decomposed));

        assertTrue(protectedPaths.isEmpty(), "normalization lifts the declared path on both sides");
    }

    @Test
    @DisplayName("the single-path decision agrees with the list decision")
    void singlePathDecisionAgrees() {
        assertAll("The single path decision",
                () -> assertTrue(ProtectedPaths.isProtected(".husky/pre-commit", Set.of()),
                        "a hook definition is protected"),
                () -> assertFalse(ProtectedPaths.isProtected(".pre-commit-config.yaml.bak", Set.of()),
                        "a near miss is not protected"),
                () -> assertFalse(ProtectedPaths.isProtected(".husky/pre-commit", Set.of(".husky/pre-commit")),
                        "a declared path is lifted"));
    }

    @Test
    @DisplayName("the hook family is the specification's list")
    void hookFamilyIsTheSpecificationList() throws Exception {
        var document = SpecificationDocument.root()
                .resolve("doc/specification/phase-workflows/03-q-gate-and-execute.adoc");
        var bullet = Files.readAllLines(document).stream()
                .filter(line -> line.contains("[[protected-paths]]")).findFirst()
                .orElseThrow(() -> new AssertionError("the Protected paths bullet is absent"));
        var start = bullet.indexOf("the hook definitions git runs from tracked files (");
        var end = bullet.indexOf(")", start);
        var declared = Arrays.stream(bullet.substring(start + "the hook definitions git runs from tracked files ("
                .length(), end).split(",")).map(String::strip).map(name -> name.replace("`", ""))
                .toList();

        assertEquals(List.of(".husky/**", ".githooks/**", "lefthook.yml", ".lefthook.yml", "lefthook-local.yml",
                ".pre-commit-config.yaml"), declared, "the matcher's hook family is the specification's list");
    }

    @Test
    @DisplayName("a differently cased declaration lifts the path")
    void differentlyCasedDeclarationLifts() {
        assertAll("A declaration is compared as the matcher compares paths",
                () -> assertTrue(ProtectedPaths.of(List.of(".HUSKY/pre-commit"), Set.of(".husky/pre-commit")).isEmpty(),
                        "an upper-cased path is lifted by a lower-cased declaration"),
                () -> assertFalse(ProtectedPaths.isProtected(".GitHooks/x", Set.of(".githooks/x")),
                        "the single-path decision agrees"),
                () -> assertEquals(List.of(".GitHooks/y"),
                        ProtectedPaths.of(List.of(".GitHooks/x", ".GitHooks/y"), Set.of(".githooks/x")),
                        "the declaration lifts its path only"));
    }

    @Test
    @DisplayName("a declared path lifts that path only")
    void declaredPathLiftsThatPathOnly() {
        var declared = ".husky/pre-commit";
        var other = ".husky/pre-push";

        var protectedPaths = ProtectedPaths.of(List.of(declared, other), Set.of(declared));

        assertEquals(List.of(other), protectedPaths, "the declaration lifts one path and not its family");
    }

    @Test
    @DisplayName("the near misses are not protected")
    void nearMissesAreNotProtected() {
        var paths = List.of("husky/x", "docs/lefthook.yml", ".pre-commit-config.yaml.bak", "README.md", "CLAUDE.md");

        assertTrue(ProtectedPaths.of(paths, Set.of()).isEmpty(), "a near miss or an unrelated file is not protected");
    }

    @Test
    @DisplayName("the matched control stays untracked by the matcher")
    void matchedControlIsNotProtected() {
        var protectedPath = ProtectedPaths.of(List.of(".husky/pre-commit"), Set.of());
        var control = ProtectedPaths.of(List.of("README.md", "CLAUDE.md"), Set.of());

        assertAll("The matched control",
                () -> assertEquals(List.of(".husky/pre-commit"), protectedPath, "the hook definition is protected"),
                () -> assertTrue(control.isEmpty(), "a changed README.md or CLAUDE.md is not protected"));
    }
}
