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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;

/**
 * The protected paths of a project (PM-WF-11, PM-SEC-5): the tracked hook definitions whose change waits for the
 * operator's decision on its diff, as {@code phase-workflows/03-q-gate-and-execute.adoc} § Protected Paths states them.
 * <p>
 * The family is matched at the repository root, ignoring case and after Unicode NFC normalization on every platform, so
 * a decomposed name and a differently cased directory are the same protected path. A declaration lifts no protection:
 * the matcher takes no declared files, so a path the plan's {@code declared_files} names is protected all the same.
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
     * @return the protected paths, in the order they were given
     */
    public static List<String> of(List<String> repositoryRelativePaths) {
        Objects.requireNonNull(repositoryRelativePaths, "repositoryRelativePaths");
        var protectedPaths = new ArrayList<String>();
        for (var path : repositoryRelativePaths) {
            Objects.requireNonNull(path, "path");
            if (matches(Normalizer.normalize(path, Normalizer.Form.NFC))) {
                protectedPaths.add(path);
            }
        }
        return List.copyOf(protectedPaths);
    }

    /**
     * @param repositoryRelativePath a repository-relative path
     * @return whether the path is protected
     */
    public static boolean isProtected(String repositoryRelativePath) {
        Objects.requireNonNull(repositoryRelativePath, "repositoryRelativePath");
        return !of(List.of(repositoryRelativePath)).isEmpty();
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
}
