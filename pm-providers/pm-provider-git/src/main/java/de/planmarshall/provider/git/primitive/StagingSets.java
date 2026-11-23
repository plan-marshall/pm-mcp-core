/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.provider.git.primitive;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.store.ConfinementOutcome;
import de.planmarshall.core.store.PathConfinement;
import de.planmarshall.core.store.ScopeType;
import de.planmarshall.core.store.WorkspaceRoots;
import de.planmarshall.provider.git.CommitPaths;

/**
 * Derives the staging set of a commit from its scope, never from whatever the checkout holds (PM-WF-16, Stage 4).
 * <p>
 * Each dirty path is a repository-relative name of the target checkout. It is staged only when it satisfies the
 * textual rule of a commit path ({@link CommitPaths#isRefusedText(String)}: no pathspec magic, no backslash, no control
 * character, no empty segment, which a directory entry such as an embedded repository or a submodule fails), is a
 * relative name in normal form (no {@code .} or {@code ..} segment), the directory that holds it lies inside the
 * checkout ({@link PathConfinement} with the checkout as the one root) and it belongs to the set of its scope. Its last
 * name is not followed: a symbolic link is committed as a link, so a link to outside or a dangling link is staged,
 * while a path through a link to outside is not. The sets of the scopes:
 * <ul>
 * <li>{@link ScopeType#PLAN}: every such path of the plan's worktree;</li>
 * <li>{@link ScopeType#EPIC}: {@code .marshall/epics/**}, and {@code .marshall/archived-epics/**} only while the epic
 * archives;</li>
 * <li>{@link ScopeType#WORKSPACE}: {@code .marshall/config/**}, {@code .gitignore} and
 * {@code .marshall/project-architecture/**};</li>
 * <li>{@link ScopeType#PROJECT}: no staging set, every dirty path is outside.</li>
 * </ul>
 * Every other dirty path is {@code outside}; a refused path is a value, never an exception.
 *
 * @since 0.1
 */
@UtilityClass
public final class StagingSets {

    private static final String EPICS = ".marshall/epics/";

    private static final String ARCHIVED_EPICS = ".marshall/archived-epics/";

    private static final List<String> WORKSPACE_DIRECTORIES =
            List.of(".marshall/config/", ".marshall/project-architecture/");

    private static final String GITIGNORE = ".gitignore";

    private static final String PARENT = "..";

    /**
     * Derives the staging set.
     *
     * @param scope      the type of the scope that commits
     * @param worktree   the root of the target checkout
     * @param dirtyPaths the dirty paths of the target checkout, repository-relative
     * @param archiving  whether the epic archives (its archive flag); read only for {@link ScopeType#EPIC}
     * @return the staged and the outside paths, each dirty path in exactly one of them
     */
    public static StagingSet derive(ScopeType scope, Path worktree, Collection<String> dirtyPaths, boolean archiving) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(worktree, "worktree");
        Objects.requireNonNull(dirtyPaths, "dirtyPaths");
        var member = member(scope, archiving);
        var roots = new WorkspaceRoots(worktree, Set.of());
        var staged = new TreeSet<String>();
        var outside = new TreeSet<String>();
        for (var path : dirtyPaths) {
            if (isConfinedName(roots, path) && member.test(path)) {
                staged.add(path);
            } else {
                outside.add(path);
            }
        }
        return new StagingSet(staged, outside);
    }

    private static Predicate<String> member(ScopeType scope, boolean archiving) {
        return switch (scope) {
            case PLAN -> _ -> true;
            case EPIC -> path -> path.startsWith(EPICS) || (archiving && path.startsWith(ARCHIVED_EPICS));
            case WORKSPACE -> path -> GITIGNORE.equals(path)
                    || WORKSPACE_DIRECTORIES.stream().anyMatch(path::startsWith);
            case PROJECT -> _ -> false;
        };
    }

    /**
     * The textual rule of the commit comes first ({@link CommitPaths#isRefusedText(String)}), so a name the commit
     * would refuse is never staged: a colon-led name, a backslash, a control character, a newline and a directory entry
     * such as an embedded repository or a submodule ({@code newdir/}) go to {@code outside}, where the footprint check
     * names them. The last name of a dirty path is the entry git records, and a symbolic link is recorded as a link,
     * never followed: only the directory that holds it is confined. A link to outside or a dangling link is therefore a
     * name of the checkout like any other, while a path that runs through a link to outside stays outside.
     */
    private static boolean isConfinedName(WorkspaceRoots roots, String path) {
        if (CommitPaths.isRefusedText(path)) {
            return false;
        }
        Path name;
        try {
            name = Path.of(path);
        } catch (InvalidPathException _) {
            return false;
        }
        if (name.isAbsolute() || !name.normalize().equals(name)) {
            return false;
        }
        for (var segment : name) {
            if (PARENT.equals(segment.toString())) {
                return false;
            }
        }
        var directory = name.getParent();
        return directory == null
                || PathConfinement.confine(roots, directory.toString()) instanceof ConfinementOutcome.Confined;
    }

    /**
     * The staging set of a commit and the dirty paths outside it.
     *
     * @param staged  the paths the scope may stage, sorted
     * @param outside the dirty paths the scope must not stage, sorted
     * @since 0.1
     */
    public record StagingSet(SortedSet<String> staged, SortedSet<String> outside) {

        /**
         * @param staged  the staged paths, copied
         * @param outside the outside paths, copied
         */
        public StagingSet {
            staged = Collections.unmodifiableSortedSet(new TreeSet<>(staged));
            outside = Collections.unmodifiableSortedSet(new TreeSet<>(outside));
        }
    }
}
