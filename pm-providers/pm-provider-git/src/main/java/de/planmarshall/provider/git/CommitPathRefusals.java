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
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;
import lombok.experimental.UtilityClass;
import org.eclipse.jgit.api.errors.JGitInternalException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.util.FS;
import org.jspecify.annotations.Nullable;

/**
 * The one rule of a path a commit cannot stage, shared by the commit that applies it and the staging set of a scope
 * that partitions its dirty paths by it (PM-WF-16, Stages 3 and 4). It has three parts, applied in this order:
 * <ul>
 * <li>the textual rule of {@link CommitPaths#isRefusedText(String)};</li>
 * <li>the file-system rule: a path that is absolute on the platform, outside the root lexically, a directory, or whose
 * parent directory a symbolic link carries outside the real root, or that is no legal path of the file system;</li>
 * <li>the tracked-tree rule: a path that names a directory of the index or of the head tree although the worktree no
 * longer holds it as one, because a Git pathspec is prefix-based and would stage every path below it.</li>
 * </ul>
 * <p>
 * Neither the file-system rule nor the tracked-tree rule follows a final symbolic link: git records a link as a link, so
 * a link to a directory, a link to a target outside the root and a dangling link are each a legal commit path. The two
 * readers therefore answer the same for a name the commit would refuse: {@link JGitOperations#commit} refuses it with
 * {@code PATH_REFUSED} before anything is staged, and {@code StagingSets} puts it in the {@code outside} set where the
 * footprint check names it.
 *
 * @since 0.1
 */
@UtilityClass
public final class CommitPathRefusals {

    /**
     * The textual rule, the one the commit shares with the staging set.
     *
     * @param path a repository-relative path
     * @return whether the path is no literal commit path of one file by its text alone
     */
    public static boolean isRefusedText(String path) {
        return CommitPaths.isRefusedText(path);
    }

    /**
     * The file-system rule: no legal path, absolute on this platform, outside the root lexically, a directory, or a
     * parent directory that a symbolic link carries outside the real root. The final entry is not followed, so a final
     * link, dangling or pointing anywhere, stays a legal commit path, and a real directory is refused because JGit's
     * pathspec would expand it to every path below it.
     *
     * @param root the worktree root
     * @param path a repository-relative path
     * @return whether the path is no legal file location inside the root
     */
    public static boolean isRefusedLocation(Path root, String path) {
        try {
            var relative = root.getFileSystem().getPath(path);
            if (relative.isAbsolute()) {
                return true;
            }
            var normalRoot = root.toAbsolutePath().normalize();
            var resolved = normalRoot.resolve(relative).normalize();
            if (!resolved.startsWith(normalRoot) || resolved.equals(normalRoot)
                    || Files.isDirectory(resolved, LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
            return !isParentConfined(normalRoot, Objects.requireNonNull(resolved.getParent(), "parent"));
        } catch (InvalidPathException _) {
            return true;
        }
    }

    /**
     * The tracked-tree rule over the checkout's own repository: the names that are a directory of the index or of the
     * head tree although the worktree no longer holds them as one. The worktree root is opened through
     * {@link RefusingFS}, so no operation starts a process. The rule stays silent when it cannot read the repository:
     * a checkout whose repository or index cannot be read yields no name, so the staging set decides by the textual and
     * file-system rules alone and the commit reports the condition — the tracked-tree answer is not invented for a
     * state it cannot read (see {@link de.planmarshall.provider.git.primitive.CommitSite#dirtyPaths}).
     *
     * @param worktree the worktree root
     * @param names    the repository-relative candidates
     * @return the names the tracked-tree rule refuses, sorted; none when the repository cannot be read
     */
    public static SortedSet<String> refusedTrackedTrees(Path worktree, Collection<String> names) {
        if (names.isEmpty()) {
            return Collections.emptySortedSet();
        }
        try (Repository repository = open(worktree, new RefusingFS())) {
            return refusedTrackedTrees(repository, names);
        } catch (IOException | JGitInternalException _) {
            return Collections.emptySortedSet();
        }
    }

    /**
     * The tracked-tree rule over an opened repository: a name is refused when the index holds an entry below it or the
     * head tree holds a subtree there. A name that is itself a symbolic link in the worktree is not refused by this
     * rule: git records a link as a link, so the link is a legal commit entry and the entries below the replaced
     * directory are simply no longer tracked. The index is checked whether or not {@code HEAD} resolves; only the
     * head-tree check needs {@code HEAD}.
     *
     * @param repository the repository
     * @param names      the repository-relative candidates
     * @return the names the tracked-tree rule refuses, sorted
     * @throws IOException when the head tree or the index cannot be read
     */
    static SortedSet<String> refusedTrackedTrees(Repository repository, Collection<String> names) throws IOException {
        var refused = new TreeSet<String>();
        if (names.isEmpty()) {
            return refused;
        }
        var workTree = repository.getWorkTree().toPath();
        var index = repository.readDirCache();
        var headId = repository.resolve(Constants.HEAD + "^{commit}");
        try (RevWalk walk = new RevWalk(repository)) {
            var headTree = headId == null ? null : walk.parseCommit(headId).getTree();
            for (var name : names) {
                if (Files.isSymbolicLink(workTree.resolve(name))) {
                    continue;
                }
                if (index.getEntriesWithin(name + "/").length > 0 || isHeadSubtree(repository, headTree, name)) {
                    refused.add(name);
                }
            }
        }
        return refused;
    }

    /**
     * Opens a worktree root as a repository without ever starting a process.
     *
     * @param worktree the root that holds the checkout
     * @param fs       the file system the repository is read through
     * @return the opened repository
     * @throws IOException when the root holds no repository
     */
    static Repository open(Path worktree, FS fs) throws IOException {
        var builder = new FileRepositoryBuilder().setFS(fs).setMustExist(true);
        Path dotGit = worktree.resolve(Constants.DOT_GIT);
        if (Files.exists(dotGit)) {
            builder.setWorkTree(worktree.toFile());
        } else {
            builder.findGitDir(worktree.toFile());
            if (builder.getGitDir() == null) {
                throw new RepositoryNotFoundException(worktree.toFile());
            }
        }
        return builder.build();
    }

    private static boolean isHeadSubtree(Repository repository, @Nullable RevTree headTree, String path)
            throws IOException {
        if (headTree == null) {
            return false;
        }
        try (TreeWalk treeWalk = TreeWalk.forPath(repository, path, headTree)) {
            return treeWalk != null && treeWalk.isSubtree();
        }
    }

    /**
     * Whether the parent directory of a commit path lies inside the real repository root once every symbolic link on the
     * way is followed. A parent that does not exist is judged by its nearest existing ancestor; an ancestor that is a
     * dangling link resolves to nothing and is not confined. A root that cannot be resolved is left to the opening of the
     * repository, which reports it.
     *
     * @param normalRoot the absolute, normalised worktree root
     * @param parent     the lexically confined parent of the path
     * @return {@code true} if the parent stays inside the real root
     */
    private static boolean isParentConfined(Path normalRoot, Path parent) {
        Path realRoot;
        try {
            realRoot = normalRoot.toRealPath();
        } catch (IOException _) {
            return true;
        }
        Path existing = parent;
        while (!existing.equals(normalRoot) && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            existing = Objects.requireNonNull(existing.getParent(), "ancestor");
        }
        try {
            return existing.toRealPath().startsWith(realRoot);
        } catch (IOException _) {
            return false;
        }
    }
}
