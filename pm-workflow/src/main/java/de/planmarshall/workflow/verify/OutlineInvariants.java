/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.verify;

import java.util.Map;
import java.util.Set;

import de.planmarshall.workflow.ast.StateBehavior;
import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;

/**
 * The invariants of the outline phase of the plan unit, {@code INV-OUTLINE-1} and {@code INV-OUTLINE-2} (PM-IMPL-5).
 * <ul>
 * <li>{@link InvariantId#INV_OUTLINE_1}: {@code outline.gate} is a state of the unit, and every transition from a
 * state of another phase into a state of phase {@code plan} (Phase 4) leaves {@code outline.gate}, or is the
 * {@code approve} answer of a question state of phase {@code outline} (the optional review question). The verdicts the
 * gate passes on are compiled into its primitive, which Rule 1 binds.</li>
 * <li>{@link InvariantId#INV_OUTLINE_2}: {@code outline.qgate} is a state of the unit; the routes from the start state
 * into {@code outline.gate} that pass no {@code outline.qgate} are those of the default unit (the light lane, a
 * recipe, the surgical bypass), so no new route skips the Q-Gate; and its {@code blocking} verdict calls
 * {@code remediation.qgate-findings}. The outline validator with the derivation dry-run runs in the return evaluation
 * of the outline submission, which Rule 1 binds.</li>
 * </ul>
 *
 * @since 0.1
 */
final class OutlineInvariants {

    /** The gate of the outline phase towards Phase 4. */
    static final String GATE = "outline.gate";

    /** The Q-Gate of the deep lane of the outline phase. */
    static final String QGATE = "outline.qgate";

    /** The answer of the optional review question that enters Phase 4. */
    static final String APPROVE = "approve";

    /** The phase value of the outline phase. */
    private static final String PHASE_OUTLINE = "outline";

    private OutlineInvariants() {
    }

    static Map<InvariantId, Check> checks() {
        return Map.of(
                InvariantId.INV_OUTLINE_1, OutlineInvariants::gateIntoPhaseFour,
                InvariantId.INV_OUTLINE_2, OutlineInvariants::qualityGate);
    }

    private static void gateIntoPhaseFour(Context context) {
        var id = InvariantId.INV_OUTLINE_1;
        context.requirePresent(id, GATE);
        for (var edge : Context.edges(context.unit())) {
            var source = edge.source();
            var transition = edge.transition();
            var entersPhaseFour = !CompiledInvariants.PHASE_PLAN.equals(source.phase()) && context
                    .state(transition.targetStateId())
                    .filter(target -> CompiledInvariants.PHASE_PLAN.equals(target.phase())).isPresent();
            if (!entersPhaseFour || GATE.equals(source.id())) {
                continue;
            }
            var reviewApproval = APPROVE.equals(transition.outcomeName())
                    && source.behavior() instanceof StateBehavior.QuestionWait
                    && PHASE_OUTLINE.equals(source.phase());
            if (!reviewApproval) {
                context.reportTransition(id, source, transition,
                        "the transition '%s' -> '%s' enters Phase 4 from '%s', not from '%s' or a review approval"
                                .formatted(transition.outcomeName(), transition.targetStateId(), source.id(), GATE));
            }
        }
    }

    private static void qualityGate(Context context) {
        var id = InvariantId.INV_OUTLINE_2;
        context.requirePresent(id, QGATE);
        context.requireNoNewBypass(id, Set.of(QGATE), GATE);
        RefineInvariants.requireRemediationOnBlocking(context, id, QGATE);
    }
}
