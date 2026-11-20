/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.ast;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * The provenance of one bound call argument (PM-WF-16): where the value came from, and the reference of that source
 * where it has one.
 * <p>
 * The source is one of {@value #LITERAL} (a value written at the call site), {@value #CONFIG} (the value of the input's
 * {@code input_fallback} key), {@value #DEFAULT} (the input's compiled default), {@value #TASK_SUBMIT} (a task
 * submission that filled a slot) or {@value #OPERATOR_ANSWER} (the operator's answer that filled one). The store
 * validates the closed set; this tree value carries it as text because the AST never depends on the store.
 *
 * @param source    the provenance of the value
 * @param sourceRef the reference of the source, a queue task id or a link id; {@code null} when the source has none
 * @since 0.1
 */
public record ArgSource(String source, @Nullable String sourceRef) {

    /** The provenance of a value written at the call site. */
    public static final String LITERAL = "literal";

    /** The provenance of the value of the input's {@code input_fallback} configuration key. */
    public static final String CONFIG = "config";

    /** The provenance of the input's compiled default. */
    public static final String DEFAULT = "default";

    /** The provenance of a value a task submission filled a slot with. */
    public static final String TASK_SUBMIT = "task_submit";

    /** The provenance of a value the operator's answer filled a slot with. */
    public static final String OPERATOR_ANSWER = "operator_answer";

    /**
     * @throws NullPointerException if the source is {@code null}
     */
    public ArgSource {
        Objects.requireNonNull(source, "source");
    }
}
