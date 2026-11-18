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

import de.planmarshall.core.role.RoleResolver;
import de.planmarshall.workflow.model.SkillCatalogue;
import org.jspecify.annotations.Nullable;

/**
 * The checks a composition runs beyond the tree (Workflow DSL § Static AST Verification Rules, Rules 13 and 14): the
 * effective role set with the plan's overrides applied, and the project's skill catalogue.
 * <p>
 * Both ports are optional so a composition without a project — a preview, or a caller that has no catalogue at hand —
 * still composes, exactly as the rules are scoped to a composition of a plan instance or an epic workflow. {@link #NONE}
 * is the answered absence; the lane and the assembly supply both for a plan.
 *
 * @param roles  the resolver of the plan's effective role set, {@code null} when the composition checks no role
 * @param skills the project's skill catalogue, {@code null} when the composition checks no skill
 * @since 0.1
 */
public record CompositionChecks(@Nullable RoleResolver roles, @Nullable SkillCatalogue skills) {

    /** The answered absence: a composition that checks neither roles nor skills. */
    public static final CompositionChecks NONE = new CompositionChecks(null, null);

    /**
     * @param roles  the role resolver
     * @param skills the skill catalogue
     * @return the checks
     */
    public static CompositionChecks of(@Nullable RoleResolver roles, @Nullable SkillCatalogue skills) {
        return new CompositionChecks(roles, skills);
    }
}
