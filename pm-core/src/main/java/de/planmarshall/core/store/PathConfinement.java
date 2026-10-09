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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.Objects;
import java.util.Optional;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.ConfinementOutcome.Confined;
import de.planmarshall.core.store.ConfinementOutcome.Reason;
import de.planmarshall.core.store.ConfinementOutcome.Rejected;

/**
 * Confines a project path to the workspace (PM-SEC-2): a path is accepted only when its canonical form, with
 * all symbolic links resolved, lies inside the enrolled repository root or inside a live linked git worktree
 * whose git common directory resolves to that root. Everything else is refused with the outcome code
 * {@link ConfinementOutcome#PATH_TRAVERSAL_REJECTED}.
 * <p>
 * The check takes the path as text, so that a text that is no path is a refusal like any other, and it never
 * throws for a path it refuses: an I/O failure during canonicalization is a refusal too.
 * <p>
 * The result holds for the moment of the check. A caller works on the canonical path of the outcome, never
 * on the text it passed in; a link that is replaced between the check and the file operation is not seen
 * here.
 *
 * @since 0.1
 */
@UtilityClass
public final class PathConfinement {

    private static final String GIT = ".git";

    private static final String GITDIR_PREFIX = "gitdir:";

    private static final String COMMONDIR = "commondir";

    private static final String PARENT = "..";

    private static final String SELF = ".";

    /** A pointer file holds one path; a larger file is none. */
    private static final long POINTER_FILE_LIMIT = 8192;

    /**
     * Decides whether a path lies inside the workspace.
     * <p>
     * A relative path is resolved against the enrolled root. A target that does not exist yet is judged by
     * its deepest existing ancestor, which is canonicalized; the names below it are appended as they are and
     * must not contain {@code ..}.
     *
     * @param roots   the enrolled root and the live linked worktrees, must not be {@code null}
     * @param rawPath the path as text, absolute or relative to the enrolled root, must not be {@code null}
     * @return {@link Confined} with the canonical path, or {@link Rejected} with the reason
     */
    public static ConfinementOutcome confine(WorkspaceRoots roots, String rawPath) {
        Objects.requireNonNull(roots, "roots");
        Objects.requireNonNull(rawPath, "rawPath");
        if (rawPath.indexOf('\0') >= 0) {
            return new Rejected(Reason.UNPARSEABLE_PATH);
        }
        try {
            var root = roots.enrolledRoot().toRealPath();
            var canonical = canonicalize(root.resolve(rawPath));
            if (canonical.isEmpty()) {
                return new Rejected(Reason.PARENT_NAME_AFTER_MISSING_PART);
            }
            if (canonical.get().startsWith(root) || insideLinkedWorktree(canonical.get(), roots, root)) {
                return new Confined(canonical.get());
            }
            return new Rejected(Reason.OUTSIDE_WORKSPACE);
        } catch (InvalidPathException _) {
            return new Rejected(Reason.UNPARSEABLE_PATH);
        } catch (IOException _) {
            return new Rejected(Reason.CANONICALIZATION_FAILED);
        }
    }

    /**
     * Canonicalizes the deepest existing part of the path and appends the names that do not exist yet.
     * The last name of each candidate is not followed when its existence is tested: a symbolic link without
     * a target exists, and its canonicalization fails instead of the link being taken for a new name.
     *
     * @return the canonical path, or empty when a {@code ..} name follows the part that does not exist
     */
    private static Optional<Path> canonicalize(Path path) throws IOException {
        var missing = new ArrayDeque<String>();
        var existing = path;
        while (!exists(existing)) {
            var parent = existing.getParent();
            if (parent == null) {
                throw new NoSuchFileException(existing.toString());
            }
            missing.push(existing.getFileName().toString());
            existing = parent;
        }
        var canonical = existing.toRealPath();
        for (String name : missing) {
            if (PARENT.equals(name)) {
                return Optional.empty();
            }
            if (!SELF.equals(name)) {
                canonical = canonical.resolve(name);
            }
        }
        return Optional.of(canonical);
    }

    /**
     * @return {@code false} only when nothing has this name; any other failure to look is passed on
     */
    private static boolean exists(Path path) throws IOException {
        try {
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return true;
        } catch (NoSuchFileException _) {
            return false;
        }
    }

    private static boolean insideLinkedWorktree(Path canonical, WorkspaceRoots roots, Path root) {
        for (Path worktree : roots.liveWorktrees()) {
            try {
                var worktreeRoot = worktree.toRealPath();
                if (canonical.startsWith(worktreeRoot) && isLinkedTo(worktreeRoot, root)) {
                    return true;
                }
            } catch (IOException | InvalidPathException _) {
                // A worktree that cannot be read or whose pointers name no path is not a worktree of the root.
            }
        }
        return false;
    }

    /**
     * Follows the two pointer files of a linked worktree: its {@code .git} file names the worktree's git
     * directory, whose {@code commondir} file names the common git directory. The worktree belongs to the
     * root when that directory is the {@code .git} directory of the root.
     */
    private static boolean isLinkedTo(Path worktreeRoot, Path root) throws IOException {
        var gitDirectoryTarget = pointer(worktreeRoot.resolve(GIT), GITDIR_PREFIX);
        if (gitDirectoryTarget.isEmpty()) {
            return false;
        }
        var gitDirectory = worktreeRoot.resolve(gitDirectoryTarget.get()).toRealPath();
        var commonDirectoryTarget = pointer(gitDirectory.resolve(COMMONDIR), "");
        if (commonDirectoryTarget.isEmpty()) {
            return false;
        }
        var commonDirectory = gitDirectory.resolve(commonDirectoryTarget.get()).toRealPath();
        var rootGitDirectory = root.resolve(GIT);
        return Files.isDirectory(rootGitDirectory, LinkOption.NOFOLLOW_LINKS)
                && commonDirectory.equals(rootGitDirectory);
    }

    /**
     * @return the path a pointer file names after its prefix, or empty when the file is no regular file, is
     *         too large for a pointer, lacks the prefix or names nothing
     */
    private static Optional<String> pointer(Path file, String prefix) throws IOException {
        var attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.size() > POINTER_FILE_LIMIT) {
            return Optional.empty();
        }
        var content = Files.readString(file).strip();
        if (!content.startsWith(prefix)) {
            return Optional.empty();
        }
        var target = content.substring(prefix.length()).strip();
        return target.isEmpty() ? Optional.empty() : Optional.of(target);
    }
}
