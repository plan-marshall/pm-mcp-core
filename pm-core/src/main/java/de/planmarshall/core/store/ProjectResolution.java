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

import java.util.Collections;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeSet;

import de.planmarshall.core.store.ConfinementOutcome.Rejected;

/**
 * What the Project Resolution Rule found for a checkout path ({@link ProjectResolver}, PM-SEC-2): the path lies in the
 * main checkout of an enrolled project, in one of its worktrees, or it is refused. A refusal is a value, never an
 * exception, and no result holds the path: what a request records is the project id.
 *
 * @since 0.1
 */
public sealed interface ProjectResolution permits ProjectResolution.MainCheckout, ProjectResolution.Worktree,
        ProjectResolution.NotEnrolled, ProjectResolution.PathRefused, ProjectResolution.EnrolmentRefused,
        ProjectResolution.RootConflict {

    /** The outcome code of a path that resolves to no enrolled project. */
    String PROJECT_NOT_ENROLLED = "project_not_enrolled";

    /** The outcome code of a main checkout that is the project root of more than one enrolment. */
    String ENROLMENT_ROOT_CONFLICT = "enrolment_root_conflict";

    /** The name of the shared epic worktree below {@code .marshall/local/worktrees/}. */
    String EPICS_WORKTREE = "_epics";

    /**
     * The path lies in the main checkout of the project.
     *
     * @param projectId the project
     * @since 0.1
     */
    record MainCheckout(String projectId) implements ProjectResolution {

        /** @throws IllegalArgumentException if the project id is not a valid identifier */
        public MainCheckout {
            LockKey.identifier(projectId, "projectId");
        }
    }

    /**
     * The path lies in a worktree of the project below {@code <project_root>/.marshall/local/worktrees/<worktree>/}.
     *
     * @param projectId the project
     * @param worktree  the worktree: a plan id, or {@value #EPICS_WORKTREE} for the shared epic worktree
     * @since 0.1
     */
    record Worktree(String projectId, String worktree) implements ProjectResolution {

        /**
         * @throws IllegalArgumentException if the project id is not a valid identifier, or the worktree is neither a
         *                                  plan id nor the shared epic worktree
         */
        public Worktree {
            LockKey.identifier(projectId, "projectId");
            if (!EPICS_WORKTREE.equals(Objects.requireNonNull(worktree, "worktree"))) {
                LockKey.identifier(worktree, "worktree");
            }
        }

        /** @return whether the worktree is the shared epic worktree */
        public boolean isEpicsWorktree() {
            return EPICS_WORKTREE.equals(worktree);
        }
    }

    /**
     * The path resolves to no enrolled project: the outcome code {@value #PROJECT_NOT_ENROLLED}.
     *
     * @param reason why, a diagnostic that carries no path
     * @since 0.1
     */
    record NotEnrolled(Reason reason) implements ProjectResolution {

        /** @throws NullPointerException if the reason is {@code null} */
        public NotEnrolled {
            Objects.requireNonNull(reason, "reason");
        }

        /** @return the outcome code, {@value #PROJECT_NOT_ENROLLED} for every reason */
        public String code() {
            return PROJECT_NOT_ENROLLED;
        }

        /**
         * Why a path resolves to no enrolled project. The reasons are diagnostics; the outcome code is the same for
         * all of them.
         *
         * @since 0.1
         */
        public enum Reason {
            /** No ancestor of the path holds a {@code .git}: the path lies outside any git work tree. */
            NO_WORK_TREE,
            /**
             * The {@code .git} of the toplevel is neither a directory nor a {@code .git} file that passes the checks
             * of a linked worktree.
             */
            UNRECOGNISED_GIT,
            /** The main checkout is the project root of no enrolment. */
            NO_ENROLMENT,
            /** The toplevel is a baseline scratch worktree {@code baseline-<plan_id>-<sha12>}, which has no project. */
            BASELINE_WORKTREE,
            /**
             * The toplevel is neither the main checkout nor a worktree below its {@code .marshall/local/worktrees/}:
             * a worktree elsewhere or a nested repository.
             */
            FOREIGN_TOPLEVEL
        }
    }

    /**
     * The path could not be canonicalised; the outcome code of the confinement.
     *
     * @param rejected the refusal, {@link ConfinementOutcome#PATH_TRAVERSAL_REJECTED}
     * @since 0.1
     */
    record PathRefused(Rejected rejected) implements ProjectResolution {

        /** @throws NullPointerException if the refusal is {@code null} */
        public PathRefused {
            Objects.requireNonNull(rejected, "rejected");
        }
    }

    /**
     * An enrolment file could not be read or was refused, and no readable enrolment names the main checkout, so the
     * path cannot be told apart from a project of that file: it is refused with that file's reason.
     *
     * @param refusal the refusal of the enrolment file, naming the file and why
     * @since 0.1
     */
    record EnrolmentRefused(StoreRefusal refusal) implements ProjectResolution {

        /** @throws NullPointerException if the refusal is {@code null} */
        public EnrolmentRefused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }

    /**
     * The main checkout is the project root of more than one enrolment: the outcome code
     * {@value #ENROLMENT_ROOT_CONFLICT}, which blocks each of the projects until the operator resolves it.
     *
     * @param projectIds the projects whose enrolment names the main checkout, at least two, sorted
     * @since 0.1
     */
    record RootConflict(SortedSet<String> projectIds) implements ProjectResolution {

        /**
         * @throws NullPointerException     if the set or an id is {@code null}
         * @throws IllegalArgumentException if fewer than two projects are named
         */
        public RootConflict {
            projectIds = Collections.unmodifiableSortedSet(new TreeSet<>(Objects.requireNonNull(projectIds,
                    "projectIds")));
            if (projectIds.size() < 2) {
                throw new IllegalArgumentException("A root conflict names at least two projects: " + projectIds);
            }
        }

        /** @return the outcome code, {@value #ENROLMENT_ROOT_CONFLICT} */
        public String code() {
            return ENROLMENT_ROOT_CONFLICT;
        }
    }
}
