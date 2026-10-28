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

import de.planmarshall.workflow.verify.CompiledInvariants.Check;
import de.planmarshall.workflow.verify.CompiledInvariants.Context;

/**
 * The invariant of the plan phase (Phase 4) of the plan unit, {@code INV-PLAN-1} (PM-IMPL-5).
 * <p>
 * {@code plan.qgate} is a state of the unit and its {@code blocking} verdict calls {@code remediation.qgate-findings}.
 * Every state of phase {@code plan} that lies on every path of the default unit into the states of Phase 5 (the task
 * derivation with its DAG check, {@code plan.qgate}, the composition of the instance version of event {@code plan})
 * lies on every path of the unit into them. The DAG check and the composition are compiled into the primitives of
 * those states, which Rule 1 binds.
 *
 * @since 0.1
 */
final class PlanInvariants {

    /** The Q-Gate of the plan phase. */
    static final String QGATE = "plan.qgate";

    private PlanInvariants() {
    }

    static Map<InvariantId, Check> checks() {
        return Map.of(InvariantId.INV_PLAN_1, PlanInvariants::pathIntoExecution);
    }

    private static void pathIntoExecution(Context context) {
        var id = InvariantId.INV_PLAN_1;
        context.requirePresent(id, QGATE);
        RefineInvariants.requireRemediationOnBlocking(context, id, QGATE);
        context.requireDominatorsOf(id, state -> context.inPhase(state, CompiledInvariants.PHASE_EXECUTE),
                state -> context.inPhase(state, CompiledInvariants.PHASE_PLAN));
    }
}
