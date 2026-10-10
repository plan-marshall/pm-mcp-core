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

import java.nio.file.Path;
import java.util.Objects;

/**
 * The result of confining a project path to the workspace (PM-SEC-2): the path is either {@link Confined} or
 * {@link Rejected}. A refusal is a value, never an exception.
 *
 * @since 0.1
 */
public sealed interface ConfinementOutcome permits ConfinementOutcome.Confined, ConfinementOutcome.Rejected {

    /** The closed outcome code of every refused path (PM-SEC-2). */
    String PATH_TRAVERSAL_REJECTED = "path_traversal_rejected";

    /**
     * The path lies inside the enrolled repository root or inside a live linked worktree of it.
     *
     * @param path the canonical path with all symbolic links resolved, never {@code null}; a caller operates
     *             on this path, not on the text it passed in
     */
    record Confined(Path path) implements ConfinementOutcome {

        /**
         * @param path the canonical path
         */
        public Confined {
            Objects.requireNonNull(path, "path");
        }
    }

    /**
     * The path is refused with the outcome code {@link #PATH_TRAVERSAL_REJECTED}.
     *
     * @param reason why the path is refused, never {@code null}; a diagnostic that carries no path
     */
    record Rejected(Reason reason) implements ConfinementOutcome {

        /**
         * @param reason why the path is refused
         */
        public Rejected {
            Objects.requireNonNull(reason, "reason");
        }

        /**
         * @return the outcome code, {@link #PATH_TRAVERSAL_REJECTED} for every reason
         */
        public String code() {
            return PATH_TRAVERSAL_REJECTED;
        }
    }

    /** Why a path is refused. The reasons are diagnostics; the outcome code is the same for all of them. */
    enum Reason {

        /** The text is no path of the platform, for example because it contains a NUL character. */
        UNPARSEABLE_PATH,

        /** A {@code ..} name follows a part of the path that does not exist, so it cannot be resolved. */
        PARENT_NAME_AFTER_MISSING_PART,

        /** The path or a root could not be canonicalized, for example because a symbolic link is broken. */
        CANONICALIZATION_FAILED,

        /** The canonical path lies outside the enrolled repository root and every live linked worktree of it. */
        OUTSIDE_WORKSPACE
    }
}
