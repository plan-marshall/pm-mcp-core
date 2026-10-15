/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import java.util.List;

/**
 * What one evaluation of the composition rules produced (PM-IMPL-5): the prunes and the bindings of every rule that
 * held, in rule order. A prune still carries the selector of its rule; the composer resolves it against the template,
 * and a selector that matches no template element prunes nothing.
 *
 * @param prunes   the prunes, each with its reason and the fact that caused it
 * @param bindings the policy call arguments to bind
 * @since 0.1
 */
public record RuleEffects(List<PrunedElement> prunes, List<CompositionRule.Binding> bindings) {

    /**
     * Copies both lists into unmodifiable lists that keep their order.
     *
     * @param prunes   the prunes
     * @param bindings the bindings
     */
    public RuleEffects {
        prunes = List.copyOf(prunes);
        bindings = List.copyOf(bindings);
    }
}
