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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;

import de.planmarshall.core.store.ConfinementOutcome.Reason;
import de.planmarshall.core.store.ConfinementOutcome.Rejected;
import de.planmarshall.core.store.ProjectResolution.EnrolmentRefused;
import de.planmarshall.core.store.ProjectResolution.MainCheckout;
import de.planmarshall.core.store.ProjectResolution.NotEnrolled;
import de.planmarshall.core.store.ProjectResolution.PathRefused;
import de.planmarshall.core.store.ProjectResolution.RootConflict;
import de.planmarshall.core.store.ProjectResolution.Worktree;

/**
 * The one resolver of the Project Resolution Rule (PM-SEC-2, PM-ARCH-1): it maps a checkout path an operator's client
 * names to the enrolled project it belongs to, and to the main checkout or the worktree of that project it lies in.
 * <p>
 * The rule reads the plain pointer files git writes and runs no git process:
 * <ol>
 * <li>The path is canonicalised ({@link Path#toRealPath}); its toplevel is the nearest ancestor, the path itself
 * included, that holds a {@code .git}. A {@code .git} directory makes the toplevel a main checkout; a {@code .git} file
 * is followed through {@code gitdir:}, {@code commondir} and the back-pointer {@code gitdir} by the recognition of a
 * linked worktree that the confinement uses as well ({@link PathConfinement#linkedMainCheckout(Path)}).</li>
 * <li>The main checkout must be the {@code project_root} of exactly one enrolment file of the enrolment store
 * ({@link EnrolmentStore}, {@link EnrolmentCodec}); that file names the project.</li>
 * <li>The toplevel is the main checkout, or a worktree {@code <project_root>/.marshall/local/worktrees/<x>/} of a plan
 * id or of the shared epic worktree {@code _epics}; a baseline scratch worktree, written
 * {@code baseline-<plan_id>-<sha12>} and recognised by its prefix {@code baseline-} alone, so that no such directory is
 * ever taken for a plan worktree whatever follows the prefix, and any other toplevel resolve to no project.</li>
 * </ol>
 * Every refusal is a {@link ProjectResolution} value. The enrolment files are read without a lock, as they are at the
 * moment of the resolution; their integrity against the sidecar is the runtime's per-use check and not decided here.
 * An enrolment file that cannot be read is no evidence that the main checkout is unenrolled, and no evidence that a
 * readable match is the only one: an unreadable file may name the same project root as the readable match, so the
 * resolution is refused with that file's reason rather than naming one project. Only when every enrolment file was
 * read, and exactly one of them names the main checkout, does the path resolve to that project.
 *
 * @since 0.1
 */
public final class ProjectResolver {

    private static final String GIT = ".git";

    /**
     * The prefix of a baseline scratch worktree. A name is refused by the prefix alone, not by a copy of the plan id
     * grammar, so a name the grammar would reject is never taken for a plan worktree either.
     */
    private static final String BASELINE_PREFIX = "baseline-";

    private final EnrolmentStore enrolments;

    /** @param base the base directory of the runtime, whose {@code enrolments/} the main checkout is matched in */
    public ProjectResolver(Path base) {
        this.enrolments = new EnrolmentStore(Objects.requireNonNull(base, "base"));
    }

    /**
     * Resolves a checkout path by the rule.
     *
     * @param path an absolute path in a checkout, a directory or a file
     * @return where the path lies, or why it is refused
     * @throws IllegalArgumentException if the path is not absolute
     * @throws UncheckedIOException     if the enrolment directory exists and cannot be listed; a file it lists that
     *                                  cannot be read is refused with its own `StoreRefusal` instead
     */
    public ProjectResolution resolve(Path path) {
        Objects.requireNonNull(path, "path");
        if (!path.isAbsolute()) {
            throw new IllegalArgumentException("A checkout path is resolved only as an absolute path: " + path);
        }
        Path canonical;
        try {
            canonical = path.toRealPath();
        } catch (IOException _) {
            return new PathRefused(new Rejected(Reason.CANONICALIZATION_FAILED));
        }
        var toplevel = toplevelOf(canonical);
        if (toplevel.isEmpty()) {
            return new NotEnrolled(NotEnrolled.Reason.NO_WORK_TREE);
        }
        var mainCheckout = mainCheckoutOf(toplevel.get());
        if (mainCheckout.isEmpty()) {
            return new NotEnrolled(NotEnrolled.Reason.UNRECOGNISED_GIT);
        }
        var match = enrolledProject(mainCheckout.get());
        if (!(match instanceof MainCheckout(var projectId))) {
            return match;
        }
        return classify(toplevel.get(), mainCheckout.get(), projectId);
    }

    /** The nearest ancestor of the canonical path, the path itself included, that holds a {@code .git}. */
    private static Optional<Path> toplevelOf(Path canonical) {
        for (var candidate = canonical; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve(GIT), LinkOption.NOFOLLOW_LINKS)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /**
     * @return the toplevel itself for a {@code .git} directory, the main checkout of a {@code .git} file that passes the
     *         checks of a linked worktree; empty for anything else, a symbolic link among it
     */
    private static Optional<Path> mainCheckoutOf(Path toplevel) {
        try {
            var git = Files.readAttributes(toplevel.resolve(GIT), BasicFileAttributes.class,
                    LinkOption.NOFOLLOW_LINKS);
            if (git.isDirectory()) {
                return Optional.of(toplevel);
            }
            return git.isRegularFile() ? PathConfinement.linkedMainCheckout(toplevel) : Optional.empty();
        } catch (IOException | InvalidPathException _) {
            // A pointer file that cannot be read or names no path makes the toplevel no worktree of a main checkout.
            return Optional.empty();
        }
    }

    /**
     * Matches the main checkout against the project root of every enrolment file.
     *
     * @return {@link MainCheckout} of the project when exactly one readable enrolment names the main checkout and every
     *         enrolment file was read, which the caller classifies further; otherwise the refusal, {@link RootConflict}
     *         when more than one readable enrolment names it and {@link EnrolmentRefused} with the first refused file
     *         when any enrolment file could not be read
     */
    private ProjectResolution enrolledProject(Path mainCheckout) {
        var matches = new TreeSet<String>();
        StoreRefusal refused = null;
        for (var projectId : enrolments.projectIds()) {
            var file = enrolments.file(projectId);
            Optional<byte[]> content;
            try {
                content = StoreSnapshot.read(file).content();
            } catch (UncheckedIOException e) {
                // A file that exists and cannot be read is refused like an undecodable one: it may name any root.
                if (refused == null) {
                    refused = StoreRefusal.unreadable(file, ProjectEnrolment.FORMAT_VERSION,
                            "the file cannot be read: " + e.getMessage(), e);
                }
                continue;
            }
            if (content.isEmpty()) {
                continue;
            }
            try {
                var enrolment = enrolments.decode(projectId, content.get());
                if (Path.of(enrolment.projectRoot()).equals(mainCheckout)) {
                    matches.add(projectId);
                }
            } catch (StoreRefusal refusal) {
                if (refused == null) {
                    refused = refusal;
                }
            }
        }
        if (matches.size() > 1) {
            return new RootConflict(matches);
        }
        if (refused != null) {
            return new EnrolmentRefused(refused);
        }
        if (matches.size() == 1) {
            return new MainCheckout(matches.first());
        }
        return new NotEnrolled(NotEnrolled.Reason.NO_ENROLMENT);
    }

    private static ProjectResolution classify(Path toplevel, Path mainCheckout, String projectId) {
        if (toplevel.equals(mainCheckout)) {
            return new MainCheckout(projectId);
        }
        var worktrees = mainCheckout.resolve(".marshall").resolve("local").resolve("worktrees");
        if (!worktrees.equals(toplevel.getParent())) {
            return new NotEnrolled(NotEnrolled.Reason.FOREIGN_TOPLEVEL);
        }
        var name = toplevel.getFileName().toString();
        if (name.startsWith(BASELINE_PREFIX)) {
            return new NotEnrolled(NotEnrolled.Reason.BASELINE_WORKTREE);
        }
        try {
            return new Worktree(projectId, name);
        } catch (IllegalArgumentException _) {
            // A directory below the worktrees whose name is neither a plan id nor _epics holds no worktree of ours.
            return new NotEnrolled(NotEnrolled.Reason.FOREIGN_TOPLEVEL);
        }
    }
}
