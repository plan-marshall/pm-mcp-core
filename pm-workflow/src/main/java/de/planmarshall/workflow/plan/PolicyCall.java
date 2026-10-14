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

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One call of a remediation policy unit in a plan instance with its effective bound arguments (PM-IMPL-5): one entry
 * of {@code policy_calls[]} in the instance header. One sub-unit serves every call site; the call site carries what it
 * binds, never an inline expansion.
 *
 * @param callSite the state id of the calling state
 * @param unit     the remediation unit called, for example {@code remediation.ci-failure}
 * @param args     the effective arguments by name ({@code retry_budget}, {@code fix_budget},
 *                 {@code waiver_wait_seconds}, {@code actions}), each with its provenance, ordered by name
 * @since 0.1
 */
public record PolicyCall(String callSite, String unit, SortedMap<String, BoundArgument> args) {

    /**
     * Refuses a missing component and copies the arguments into an unmodifiable map ordered by name.
     *
     * @param callSite the state id of the calling state
     * @param unit     the remediation unit called
     * @param args     the effective arguments by name
     */
    public PolicyCall {
        Objects.requireNonNull(callSite, "callSite");
        Objects.requireNonNull(unit, "unit");
        args = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(args, "args")));
    }

    /**
     * @param callSite the state id of the calling state
     * @param unit     the remediation unit called
     * @param args     the effective arguments by name, in any order
     * @return the policy call with its arguments ordered by name
     */
    public static PolicyCall of(String callSite, String unit, Map<String, BoundArgument> args) {
        return new PolicyCall(callSite, unit, new TreeMap<>(args));
    }
}
