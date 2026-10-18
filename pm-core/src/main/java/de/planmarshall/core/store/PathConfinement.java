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
 * of that root. A linked worktree counts as one of the root only when its git directory is registered directly
 * below the {@code .git/worktrees} directory of the root, names the {@code .git} directory of the root as its
 * common directory and names the worktree back in its {@code gitdir} record. Everything else is refused with
 * the outcome code {@link ConfinementOutcome#PATH_TRAVERSAL_REJECTED}.
 * <p>
 * The record that names the worktree back makes the rule stricter than the common directory alone: a worktree
 * directory moved by hand, with {@code mv} instead of {@code git worktree move}, leaves a stale {@code gitdir}
 * record and is refused until {@code git worktree repair} has rewritten it.
 * <p>
 * The recognition is one operation used in both directions: the confinement asks whether a live worktree belongs to
 * the enrolled root, and the project resolution ({@link ProjectResolver}) asks which main checkout a worktree belongs
 * to ({@link #linkedMainCheckout(Path)}); both read the same pointer files under the same checks.
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

    private static final String GITDIR = "gitdir";

    private static final String WORKTREES = "worktrees";

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
     * Follows the three pointer files of a linked worktree and accepts it only when all of them agree. Its
     * {@code .git} file names the worktree's git directory, whose {@code commondir} file names the common git
     * directory and whose {@code gitdir} file names the {@code .git} file of the worktree. The worktree
     * belongs to the root when
     * <ul>
     * <li>the common directory is the {@code .git} directory of the root,</li>
     * <li>the git directory is registered with the root, that is, it lies directly below the
     * {@code .git/worktrees} directory of the root, and</li>
     * <li>the {@code gitdir} record of the git directory names this worktree back.</li>
     * </ul>
     * A directory that merely carries a git directory with a fitting {@code commondir} file is therefore no
     * worktree of the root. This is stricter than the common directory alone: a worktree moved by hand, with
     * {@code mv} instead of {@code git worktree move}, keeps a stale {@code gitdir} record and is refused
     * until {@code git worktree repair} has rewritten it.
     *
     * @param worktreeRoot the canonical root of the candidate worktree
     * @param root         the canonical enrolled root
     * @return {@code true} when the candidate is a linked worktree registered with the root
     * @throws IOException when a pointer file or the path it names cannot be read; the caller treats it as
     *                     a refusal
     */
    private static boolean isLinkedTo(Path worktreeRoot, Path root) throws IOException {
        return linkedMainCheckout(worktreeRoot).filter(root::equals).isPresent();
    }

    /**
     * The recognition of a linked worktree read the other way round: from a candidate worktree to the main checkout
     * it belongs to, with the same three pointer files and the same checks as {@link #isLinkedTo(Path, Path)}. The
     * {@code .git} file of the candidate names its git directory, whose {@code commondir} file names the common git
     * directory and whose {@code gitdir} file names the {@code .git} file back. The candidate is a linked worktree of
     * a main checkout when
     * <ul>
     * <li>the common directory is a directory named {@code .git},</li>
     * <li>the git directory is registered with it, that is, it lies directly below its {@code worktrees} directory,
     * and</li>
     * <li>the {@code gitdir} record of the git directory names the candidate back.</li>
     * </ul>
     * The main checkout is then the parent of the common directory. Every pointer file is a regular file, never a
     * symbolic link, not empty and at most {@value #POINTER_FILE_LIMIT} bytes. The project resolution
     * ({@link ProjectResolver}) follows a {@code .git} file with this operation, so a worktree is recognised the same
     * way wherever it is.
     *
     * @param worktreeRoot the canonical root of the candidate worktree, which holds a {@code .git} file
     * @return the canonical main checkout of the candidate; empty when a pointer file fails a check
     * @throws IOException when a pointer file or the path it names cannot be read; a caller treats it as a refusal
     */
    static Optional<Path> linkedMainCheckout(Path worktreeRoot) throws IOException {
        var pointerFile = worktreeRoot.resolve(GIT);
        var gitDirectoryTarget = pointer(pointerFile, GITDIR_PREFIX);
        if (gitDirectoryTarget.isEmpty()) {
            return Optional.empty();
        }
        var gitDirectory = worktreeRoot.resolve(gitDirectoryTarget.get()).toRealPath();
        var commonDirectoryTarget = pointer(gitDirectory.resolve(COMMONDIR), "");
        if (commonDirectoryTarget.isEmpty()) {
            return Optional.empty();
        }
        var commonDirectory = gitDirectory.resolve(commonDirectoryTarget.get()).toRealPath();
        var mainCheckout = commonDirectory.getParent();
        if (mainCheckout == null || !GIT.equals(String.valueOf(commonDirectory.getFileName()))
                || !Files.isDirectory(commonDirectory, LinkOption.NOFOLLOW_LINKS)
                || !isRegistered(gitDirectory, commonDirectory) || !namesBack(gitDirectory, pointerFile)) {
            return Optional.empty();
        }
        return Optional.of(mainCheckout);
    }

    /**
     * The common directory is canonical and a directory that is no link, so the {@code worktrees} directory below it
     * is canonical as soon as it is no link either.
     *
     * @param gitDirectory    the canonical git directory of the candidate worktree
     * @param commonDirectory the canonical common git directory, the {@code .git} directory of a main checkout
     * @return {@code true} when the git directory is a direct child of the {@code worktrees} directory of the common
     *         directory, and that directory is not reached through a symbolic link
     */
    private static boolean isRegistered(Path gitDirectory, Path commonDirectory) {
        var registry = commonDirectory.resolve(WORKTREES);
        return Files.isDirectory(registry, LinkOption.NOFOLLOW_LINKS) && registry.equals(gitDirectory.getParent());
    }

    /**
     * @param gitDirectory the canonical git directory of the candidate worktree
     * @param pointerFile  the {@code .git} file of the candidate worktree
     * @return {@code true} when the {@code gitdir} record of the git directory, resolved against that
     *         directory when it is relative, names the {@code .git} file of the candidate
     * @throws IOException when the record cannot be read or names a path that does not exist
     */
    private static boolean namesBack(Path gitDirectory, Path pointerFile) throws IOException {
        var backPointer = pointer(gitDirectory.resolve(GITDIR), "");
        return backPointer.isPresent()
                && gitDirectory.resolve(backPointer.get()).toRealPath().equals(pointerFile.toRealPath());
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
