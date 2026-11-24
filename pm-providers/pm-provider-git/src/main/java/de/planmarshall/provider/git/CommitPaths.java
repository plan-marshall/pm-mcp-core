/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git;

import java.util.Arrays;
import lombok.experimental.UtilityClass;

/**
 * The textual rule a commit path must satisfy, shared by the commit and the staging set of a scope (PM-WF-16, Stage 4).
 * <p>
 * A commit path is a literal repository path of one file, never a pathspec: it is not empty, does not begin with
 * {@code :} (git's pathspec magic) or with {@code /}, holds no backslash and no control character, and has no empty,
 * {@code .} or {@code ..} segment. The rule is textual, so it is decided without the file system and identically in
 * both variants of the contract; an entry git lists by its directory ({@code newdir/}) and a name carrying a newline
 * fail it through the empty segment and the control-character clause. {@code JGitOperations} refuses a path that fails
 * it with {@link GitOutcome#PATH_REFUSED}, and {@code StagingSets} puts such a dirty name in the {@code outside} set, so
 * the footprint check names it instead of a commit failing on it.
 * <p>
 * The rule is read through {@code CommitPathRefusals.isRefusedText}, the façade both readers name for all three parts
 * of the commit's path rule; this class keeps the text grammar itself, which {@code CommitPathsTest} pins.
 *
 * @since 0.1
 */
@UtilityClass
public final class CommitPaths {

    /**
     * @param path a repository-relative path
     * @return whether the path breaks the textual rule of a commit path, so the commit refuses it
     */
    public static boolean isRefusedText(String path) {
        if (path.isEmpty() || path.startsWith(":") || path.startsWith("/") || path.indexOf('\\') >= 0
                || path.chars().anyMatch(Character::isISOControl)) {
            return true;
        }
        return Arrays.stream(path.split("/", -1))
                .anyMatch(segment -> segment.isEmpty() || ".".equals(segment) || "..".equals(segment));
    }
}
