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

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The protected paths of the hook definitions (PM-WF-11, PM-SEC-5): the family of
 * {@code phase-workflows/03-q-gate-and-execute.adoc} § Protected Paths, matched at the repository root, ignoring case
 * and after Unicode NFC normalization. A path the plan declares lifts no protection: the matcher takes no declaration.
 * <p>
 * Every datum in this class is the specification's contract: the six patterns, the case and normalization rules, and
 * the near misses. The test is the display of the names the specification states once.
 */
@DisplayName("Protected paths: the hook definitions")
class ProtectedPathsTest {

    @Test
    @DisplayName("a protected path the plan declared is still reported")
    void declaredProtectedPathIsReported() {
        // the plan's declared_files would name exactly this path; the matcher reports it all the same
        var declared = ".husky/pre-commit";

        assertAll("A declared protected path",
                () -> assertEquals(List.of(declared), ProtectedPaths.of(List.of(declared)),
                        "the declared hook definition is reported"),
                () -> assertTrue(ProtectedPaths.isProtected(declared),
                        "the declared hook definition is protected"));
    }

    @Test
    @DisplayName("a differently cased declared protected path is reported, so a declaration lifts nothing")
    void differentlyCasedDeclaredPathIsReported() {
        var paths = List.of(".HUSKY/pre-commit", ".GitHooks/x");

        assertAll("A differently cased declared path",
                () -> assertEquals(paths, ProtectedPaths.of(paths), "both cased variants are reported"),
                () -> assertTrue(ProtectedPaths.isProtected(".GitHooks/x"),
                        "the single-path decision reports it too"));
    }

    @Test
    @DisplayName("the matcher's public surface takes no declared files")
    void noDeclaredFilesParameter() {
        var publicMethods = Arrays.stream(ProtectedPaths.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers())).toList();

        assertAll("The public surface of the matcher",
                () -> assertEquals(List.of("isProtected", "of"),
                        publicMethods.stream().map(Method::getName).sorted().toList(),
                        "the matcher exposes of and isProtected only"),
                () -> assertTrue(publicMethods.stream().allMatch(method -> method.getParameterCount() == 1),
                        "each public method takes one parameter"),
                () -> assertFalse(publicMethods.stream().flatMap(method -> Arrays.stream(
                                method.getParameterTypes())).anyMatch(type -> type == Collection.class),
                        "no public method takes a declared-files collection"));
    }

    @Test
    @DisplayName("every hook definition of the specification is protected at the root")
    void everyHookDefinitionIsProtected() {
        var paths = List.of(".husky/pre-commit", ".githooks/pre-push", "lefthook.yml", ".lefthook.yml",
                "lefthook-local.yml", ".pre-commit-config.yaml");

        var protectedPaths = ProtectedPaths.of(paths);

        assertEquals(paths, protectedPaths, "the six hook definitions are protected");
    }

    @Test
    @DisplayName("a nested file below a hook directory is protected")
    void nestedFileIsProtected() {
        var paths = List.of(".husky/nested/hook", ".githooks/sub/dir/hook");

        assertEquals(paths, ProtectedPaths.of(paths), "a nested file below .husky/ is protected");
    }

    @Test
    @DisplayName("case variants of the hook directories are protected")
    void caseVariantsAreProtected() {
        var paths = List.of(".HUSKY/pre-commit", ".GitHooks/x");

        assertEquals(paths, ProtectedPaths.of(paths), "case variants are protected");
    }

    @Test
    @DisplayName("a decomposed character in a path below .husky/ is protected")
    void decomposedCharacterIsProtected() {
        var decomposed = ".husky/" + Normalizer.normalize("é", Normalizer.Form.NFD) + "-hook";
        var paths = List.of(decomposed);

        assertEquals(paths, ProtectedPaths.of(paths), "NFC normalization matches the decomposed name");
    }

    @Test
    @DisplayName("the single-path decision agrees with the list decision")
    void singlePathDecisionAgrees() {
        assertAll("The single path decision",
                () -> assertTrue(ProtectedPaths.isProtected(".husky/pre-commit"), "a hook definition is protected"),
                () -> assertFalse(ProtectedPaths.isProtected(".pre-commit-config.yaml.bak"),
                        "a near miss is not protected"));
    }

    @Test
    @DisplayName("the near misses are not protected")
    void nearMissesAreNotProtected() {
        var paths = List.of("husky/x", "docs/lefthook.yml", ".pre-commit-config.yaml.bak", "README.md", "CLAUDE.md");

        assertTrue(ProtectedPaths.of(paths).isEmpty(), "a near miss or an unrelated file is not protected");
    }

    @Test
    @DisplayName("the matched control stays untracked by the matcher")
    void matchedControlIsNotProtected() {
        var protectedPath = ProtectedPaths.of(List.of(".husky/pre-commit"));
        var control = ProtectedPaths.of(List.of("README.md", "CLAUDE.md"));

        assertAll("The matched control",
                () -> assertEquals(List.of(".husky/pre-commit"), protectedPath, "the hook definition is protected"),
                () -> assertTrue(control.isEmpty(), "a changed README.md or CLAUDE.md is not protected"));
    }
}
