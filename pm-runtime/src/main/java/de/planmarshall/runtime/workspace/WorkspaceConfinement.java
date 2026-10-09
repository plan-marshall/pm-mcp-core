/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.workspace;

import static de.planmarshall.core.log.PmMcpLogMessages.WARN;

import java.util.Objects;


import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.store.ConfinementOutcome;
import de.planmarshall.core.store.ConfinementOutcome.Rejected;
import de.planmarshall.core.store.PathConfinement;
import de.planmarshall.core.store.WorkspaceRoots;

/**
 * Confines the paths of one project to its workspace (PM-SEC-2): the enrolled repository root and the live
 * linked worktrees of it. An operation of the daemon on a project file passes the path it was given through
 * {@link #confine(String)} and works on the canonical path of the outcome, never on the text it passed in.
 * <p>
 * A refused path is a return value, not an exception: the outcome is
 * {@link ConfinementOutcome.Rejected} with the code {@link ConfinementOutcome#PATH_TRAVERSAL_REJECTED}, and
 * the refusal is logged at WARN with the code and the reason, never with the path.
 *
 * @since 0.1
 */
public final class WorkspaceConfinement {

    private static final CuiLogger LOGGER = new CuiLogger(WorkspaceConfinement.class);

    private final WorkspaceRoots roots;

    /**
     * @param roots the enrolled root and the live linked worktrees of the project, must not be {@code null}
     */
    public WorkspaceConfinement(WorkspaceRoots roots) {
        this.roots = Objects.requireNonNull(roots, "roots");
    }

    /**
     * Decides whether a path lies inside the workspace of the project.
     *
     * @param rawPath the path as text, absolute or relative to the enrolled root, must not be {@code null}
     * @return {@link ConfinementOutcome.Confined} with the canonical path, or
     *         {@link ConfinementOutcome.Rejected} with the reason
     */
    public ConfinementOutcome confine(String rawPath) {
        var outcome = PathConfinement.confine(roots, rawPath);
        if (outcome instanceof Rejected rejected) {
            LOGGER.warn(WARN.PROJECT_PATH_REFUSED, rejected.code(), rejected.reason());
        }
        return outcome;
    }
}
