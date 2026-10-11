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

/**
 * The three-valued result of evaluating a guard (PM-IMPL-5): a link is offered, and its transition fires, only on
 * {@link Held}; {@link CouldNotEvaluate} is never folded into either of the other two.
 *
 * @since 0.1
 */
public sealed interface GuardResult permits GuardResult.Held, GuardResult.NotHeld, GuardResult.CouldNotEvaluate {

    /**
     * The guard holds.
     */
    record Held() implements GuardResult {
    }

    /**
     * The guard does not hold.
     */
    record NotHeld() implements GuardResult {
    }

    /**
     * The guard could not be evaluated.
     *
     * @param reason why the guard could not be evaluated
     */
    record CouldNotEvaluate(String reason) implements GuardResult {
    }
}
