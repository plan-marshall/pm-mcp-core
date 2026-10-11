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

import java.util.List;
import java.util.Optional;

/**
 * {@code <outcome> -> <target>}: the transition taken on an outcome, with its optional result guard and the steps it
 * completes (PM-IMPL-5). The completions are copied into an immutable list.
 *
 * @param outcomeName   the outcome the transition is taken on
 * @param targetStateId the identifier of the target state
 * @param resultGuard   the result guard of a call-site transition
 * @param completions   the {@code completes} clauses in source order
 * @param location      where the transition was read
 * @since 0.1
 */
public record OutcomeTransition(
String outcomeName,
String targetStateId,
Optional<ResultGuard> resultGuard,
List<StepCompletion> completions,
SourceLocation location) {

    /**
     * Copies the completions into an immutable list.
     *
     * @param outcomeName   the outcome the transition is taken on
     * @param targetStateId the identifier of the target state
     * @param resultGuard   the result guard of a call-site transition
     * @param completions   the {@code completes} clauses
     * @param location      where the transition was read
     */
    public OutcomeTransition {
        completions = List.copyOf(completions);
    }
}
