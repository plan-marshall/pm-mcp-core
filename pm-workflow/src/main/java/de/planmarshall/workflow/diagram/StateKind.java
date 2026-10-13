/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.diagram;

import java.util.Locale;
import java.util.Objects;

import de.planmarshall.workflow.ast.StateBehavior;

/**
 * The kind of a state as the diagrams and tables name it (PM-IMPL-5): the <em>Kind</em> column of the state table and
 * the first word of a state's description in the Mermaid diagram.
 * <p>
 * The kind is derived from the sealed {@link StateBehavior} alone, by a switch over its variants that has no default
 * arm: a new variant fails to compile here until it is given a kind, so no second list of the variants exists. A task
 * state ({@link StateBehavior.ModelJob}) is of kind {@link #JOB}; the role of its tasks is named elsewhere in each
 * form.
 *
 * @since 0.1
 */
enum StateKind {
    /** {@code auto}: the server runs a primitive. */
    AUTO,
    /** {@code job} and {@code job llm}: the state waits for a job or for the tasks of a role. */
    JOB,
    /** {@code composite_job}: the state waits for a composite job. */
    COMPOSITE_JOB,
    /** {@code call}: the state calls a sub-workflow. */
    CALL,
    /** {@code link}: the state rests on its declared links. */
    LINK,
    /** {@code terminal}: the state ends the unit. */
    TERMINAL,
    /** {@code question}: the state waits for the resolution of a question record. */
    AWAITING_USER;

    /**
     * Derives the kind of a state from what it does.
     *
     * @param behavior what the state does
     * @return the kind of the state
     */
    static StateKind of(StateBehavior behavior) {
        Objects.requireNonNull(behavior, "behavior");
        return switch (behavior) {
            case StateBehavior.InteractiveLinks _ -> LINK;
            case StateBehavior.AutoStep _ -> AUTO;
            case StateBehavior.JobStep(_, var composite, _) -> composite ? COMPOSITE_JOB : JOB;
            case StateBehavior.ModelJob _ -> JOB;
            case StateBehavior.SubWorkflowCall _ -> CALL;
            case StateBehavior.QuestionWait _ -> AWAITING_USER;
            case StateBehavior.TerminalState _ -> TERMINAL;
        };
    }

    /**
     * @return the label of the kind, the lower-case name of the constant
     */
    String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
