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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * The protected paths of a project (PM-WF-11, PM-SEC-5): the tracked hook definitions a change may not touch without
 * the plan's declaration, as {@code phase-workflows/03-q-gate-and-execute.adoc} § Protected Paths states them.
 * <p>
 * The family is matched at the repository root, ignoring case and after Unicode NFC normalization on every platform, so
 * a decomposed name and a differently cased directory are the same protected path. A path the plan's
 * {@code declared_files} names is not reported; the declaration lifts exactly that path, never its family.
 * <p>
 * The class is pure: it decides over the paths it is given and reads no file. The other families of the same paragraph
 * ({@code .marshall/**}, harness configuration, forge CI, editor files) are added by the plan that implements
 * {@code PM-WATCH-PHASE-130} and {@code PM-WATCH-PHASE-131}.
 *
 * @since 0.1
 */
@UtilityClass
public final class ProtectedPaths {

    /** The hook directories of the specification; every path below one of them is protected. */
    private static final List<String> HOOK_DIRECTORIES = List.of(".husky", ".githooks");

    /** The hook definition files of the specification, matched at the repository root. */
    private static final Set<String> HOOK_FILES = Set.of("lefthook.yml", ".lefthook.yml", "lefthook-local.yml",
            ".pre-commit-config.yaml");

    private static final String SEPARATOR = "/";

    /**
     * Decides which of the given repository-relative paths are protected.
     *
     * @param repositoryRelativePaths the paths to decide over, relative to the repository root and separated by
     *                                {@code /}
     * @param declaredFiles           the paths the plan declared; a declared path is not reported
     * @return the protected paths, in the order they were given
     */
    public static List<String> of(List<String> repositoryRelativePaths, Collection<String> declaredFiles) {
        Objects.requireNonNull(repositoryRelativePaths, "repositoryRelativePaths");
        Objects.requireNonNull(declaredFiles, "declaredFiles");
        var declared = declaredFiles.stream().filter(Objects::nonNull).map(ProtectedPaths::declarationKey)
                .collect(Collectors.toUnmodifiableSet());
        var protectedPaths = new ArrayList<String>();
        for (var path : repositoryRelativePaths) {
            Objects.requireNonNull(path, "path");
            if (!declared.contains(declarationKey(path)) && matches(normalize(path))) {
                protectedPaths.add(path);
            }
        }
        return List.copyOf(protectedPaths);
    }

    /**
     * @param repositoryRelativePath a repository-relative path
     * @param declaredFiles          the paths the plan declared
     * @return whether the path is protected and not declared
     */
    public static boolean isProtected(String repositoryRelativePath, Collection<String> declaredFiles) {
        Objects.requireNonNull(repositoryRelativePath, "repositoryRelativePath");
        return !of(List.of(repositoryRelativePath), declaredFiles).isEmpty();
    }

    private static boolean matches(String normalizedPath) {
        var lowerCase = normalizedPath.toLowerCase(Locale.ROOT);
        if (HOOK_FILES.contains(lowerCase)) {
            return true;
        }
        for (var directory : HOOK_DIRECTORIES) {
            if (lowerCase.startsWith(directory + SEPARATOR)) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(String path) {
        return Normalizer.normalize(path, Normalizer.Form.NFC);
    }

    /**
     * @return the key a declaration and a candidate path are compared by: NFC normalised and lower-cased, so a
     *         differently cased declaration lifts the path, as the matcher treats the two as one path
     */
    private static String declarationKey(String path) {
        return normalize(path).toLowerCase(Locale.ROOT);
    }
}
