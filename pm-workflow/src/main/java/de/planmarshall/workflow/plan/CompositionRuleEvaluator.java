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

import java.util.ArrayList;
import java.util.Objects;

/**
 * Evaluates the composition rules against the facts of a plan (PM-IMPL-5, PM-EXT-7): every rule whose condition holds
 * contributes its prunes, each with the rule's reason and the fact the rule read as the source fact, and its bindings;
 * the effects come in rule order and, within a rule, in the order the rule declares them. The evaluation reads nothing
 * but its two arguments.
 *
 * @since 0.1
 */
public final class CompositionRuleEvaluator {

    private CompositionRuleEvaluator() {
    }

    /**
     * @param rules the composition rules
     * @param facts the facts of the plan, the effective configuration values included
     * @return the prunes and bindings of every rule that holds
     */
    public static RuleEffects evaluate(CompositionRules rules, PlanFacts facts) {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(facts, "facts");
        var prunes = new ArrayList<PrunedElement>();
        var bindings = new ArrayList<CompositionRule.Binding>();
        for (var rule : rules.rules()) {
            if (rule.when().holds(facts)) {
                var fact = rule.when().fact();
                rule.prunes().forEach(prune -> prunes.add(new PrunedElement(prune.element(), prune.id(),
                        prune.reason(), fact)));
                bindings.addAll(rule.bindings());
            }
        }
        return new RuleEffects(prunes, bindings);
    }
}
