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

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.AmbiguousObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.jspecify.annotations.Nullable;

/**
 * The identity rules of the git contract (PM-WATCH-GEN-59): a pinned commit is an identity that is checked, never a
 * name that is looked up. A commit the server pins is passed and compared as its full 40-character object id, never
 * abbreviated and never as a ref name; a branch is addressed as {@code refs/heads/<name>}, a ref of the runtime as
 * {@code refs/pm-mcp/<...>} and a remote branch as {@code refs/remotes/<remote>/<name>}.
 * <p>
 * A name that resolves both as a ref and as an object id, or as two refs, is ambiguous and is refused
 * {@code git_identity_ambiguous}. An abbreviated commit id is refused rather than widened to the one object it may
 * currently match.
 * <p>
 * Specification: {@code doc/specification/job-runtime/01-pipeline-and-subprocesses.adoc} (Git Identities).
 *
 * @since 0.1
 */
public final class GitIdentity {

    /** A full commit id: 40 hexadecimal digits, in either case. */
    private static final Pattern FULL_COMMIT_ID = Pattern.compile("[0-9a-f]{40}", Pattern.CASE_INSENSITIVE);

    /** A prefix that may abbreviate a commit id: 4 to 39 hexadecimal digits, in either case. */
    private static final Pattern ABBREVIATED_COMMIT_ID =
            Pattern.compile("[0-9a-f]{4,39}", Pattern.CASE_INSENSITIVE);

    private GitIdentity() {
    }

    /**
     * How a revision string is addressed.
     *
     * @since 0.1
     */
    public enum Resolution {
        /** A full 40-character commit id that names an object of the repository. */
        FULL_COMMIT_ID,
        /** Exactly one ref whose short name is the string. */
        REF,
        /** A hexadecimal prefix shorter than 40 characters that the repository resolves. */
        ABBREVIATED_COMMIT_ID,
        /** The string names both a ref and an object of the repository. */
        REF_AND_COMMIT_ID,
        /** The string names two or more refs. */
        TWO_REFS,
        /** Neither a ref nor an object of the repository. */
        UNKNOWN
    }

    /**
     * @param value a revision string
     * @return whether it is a full 40-character commit id
     */
    public static boolean isFullCommitId(@Nullable String value) {
        return value != null && FULL_COMMIT_ID.matcher(value).matches();
    }

    /**
     * @param value a revision string
     * @return whether it is a hexadecimal prefix shorter than a full commit id
     */
    public static boolean isAbbreviatedCommitId(@Nullable String value) {
        return value != null && ABBREVIATED_COMMIT_ID.matcher(value).matches();
    }

    /**
     * @param branch the branch name
     * @return the full ref of the branch, {@code refs/heads/<branch>}
     */
    public static String branchRef(String branch) {
        Objects.requireNonNull(branch, "branch");
        return branch.startsWith(Constants.R_HEADS) ? branch : Constants.R_HEADS + branch;
    }

    /**
     * @param name the name of the runtime ref
     * @return the full ref of the runtime, {@code refs/pm-mcp/<name>}
     */
    public static String runtimeRef(String name) {
        Objects.requireNonNull(name, "name");
        return "refs/pm-mcp/" + name;
    }

    /**
     * @param remote the remote name
     * @param branch the branch name
     * @return the full remote-tracking ref, {@code refs/remotes/<remote>/<branch>}
     */
    public static String remoteBranchRef(String remote, String branch) {
        Objects.requireNonNull(remote, "remote");
        Objects.requireNonNull(branch, "branch");
        return Constants.R_REMOTES + remote + "/" + branch;
    }

    /**
     * Classifies a revision string against the repository.
     *
     * @param repository the repository
     * @param name       the revision string
     * @return how the string is addressed
     * @throws IOException if the ref database cannot be read
     */
    public static Resolution resolution(Repository repository, String name) throws IOException {
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(name, "name");
        List<String> refs = repository.getRefDatabase().getRefs().stream()
                .map(ref -> Repository.shortenRefName(ref.getName()))
                .filter(name::equals)
                .toList();
        // A full 40-character id is an identity by its syntax: a ref of the same name is ambiguous whether or not the
        // object is present in this clone (a shallow clone or a GC must not turn the pin back into a named ref).
        var fullCommitId = isFullCommitId(name);
        if (refs.size() > 1) {
            return Resolution.TWO_REFS;
        }
        if (refs.size() == 1 && fullCommitId) {
            return Resolution.REF_AND_COMMIT_ID;
        }
        if (refs.size() == 1) {
            return Resolution.REF;
        }
        if (fullCommitId) {
            return Resolution.FULL_COMMIT_ID;
        }
        if (isAbbreviatedCommitId(name) && resolvesAbbreviated(repository, name)) {
            return Resolution.ABBREVIATED_COMMIT_ID;
        }
        return Resolution.UNKNOWN;
    }

    /**
     * Whether the repository resolves the prefix. An ambiguous prefix is an abbreviated id too, not an I/O failure.
     */
    private static boolean resolvesAbbreviated(Repository repository, String name) throws IOException {
        try {
            return repository.resolve(name) != null;
        } catch (AmbiguousObjectException _) {
            return true;
        }
    }

    /**
     * @param resolution a resolution
     * @return whether the name is ambiguous and must be refused
     */
    public static boolean isAmbiguous(Resolution resolution) {
        return resolution == Resolution.REF_AND_COMMIT_ID || resolution == Resolution.TWO_REFS;
    }
}
