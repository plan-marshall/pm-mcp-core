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

import java.util.Arrays;
import java.util.Optional;

/**
 * The closed set of reasons why a composition left a template element out of a plan instance (PM-IMPL-5); every
 * pruned element of the header carries one. The set equals the {@code prune_reason} enumeration of the header schema,
 * in its order.
 *
 * @since 0.1
 */
public enum PruneReason {
    /** The posture of the plan leaves the step out, from version 1 on. */
    POSTURE("posture"),
    /** The provider the step needs is not configured. */
    PROVIDER_UNCONFIGURED("provider_unconfigured"),
    /** The step's footprint and the plan's footprint are disjoint. */
    FOOTPRINT_DISJOINT("footprint_disjoint"),
    /** The change type of the plan exempts the step. */
    CHANGE_TYPE_EXEMPT("change_type_exempt"),
    /** No push remote exists, so the steps that need one are left out. */
    LOCAL_ONLY("local_only"),
    /** The {@code land} lane allocates no tasks. */
    LAND_LANE("land_lane"),
    /** The {@code adopt} lane adopts an existing pull request. */
    ADOPT("adopt"),
    /**
     * A state reachable only from the entry of the lane the plan did not choose, pruned from the {@code outline}
     * event on.
     */
    OTHER_LANE("other_lane"),
    /** The bound asking variant leaves the merge to the operator, so the merge execution is left out. */
    MERGE_BY_OPERATOR("merge_by_operator"),
    /** A variant of a plan behaviour axis that is not bound, left out as a pruned call. */
    PLAN_DEFAULT("plan_default"),
    /** Post-run only: the plan takes the {@code returned} route. */
    RETURNED("returned"),
    /** Post-run only: the plan takes the {@code handed_back} route. */
    HANDED_BACK("handed_back"),
    /** Post-run only: a harvest yielded no targets. */
    EMPTY_HARVEST("empty_harvest");

    private final String wireName;

    PruneReason(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the value of {@code reason} in a pruned element of the instance header
     */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the value of {@code reason} as written in a header
     * @return the reason of that wire name, or empty if no reason has it
     */
    public static Optional<PruneReason> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(reason -> reason.wireName.equals(wireName)).findFirst();
    }
}
