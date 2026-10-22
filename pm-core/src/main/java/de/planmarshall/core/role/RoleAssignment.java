/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.util.Objects;
import java.util.Optional;

/**
 * What one level names of a role's harness, model and effort (PM-WORK-1, Role Assignment): the machine's or the project's
 * {@code role-assignments.json}, or the plan's {@code overrides.json}. Each member is optional; the level overrides only
 * what it names and keeps the value of the level below for everything else. An effort that a level names as
 * {@code null} clears the effort; an effort the level does not name keeps the lower value.
 *
 * @param harness the harness the level names, or empty
 * @param model   the model the level names, or empty
 * @param effort  the effort the level names, or empty
 * @since 0.1
 */
public record RoleAssignment(Optional<String> harness, Optional<String> model, Optional<EffortValue> effort) {

    /** Rejects {@code null} components. */
    public RoleAssignment {
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(effort, "effort");
    }

    /** @return the assignment that names nothing */
    public static RoleAssignment none() {
        return new RoleAssignment(Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * @param harness the harness
     * @param model   the model
     * @param effort  the effort value, {@code null} for no effort flag
     * @return the assignment that names all three
     */
    public static RoleAssignment of(String harness, String model, String effort) {
        return new RoleAssignment(Optional.of(harness), Optional.of(model), Optional.of(new EffortValue(effort)));
    }

    /**
     * This assignment laid over a lower one: every member this level names wins, every member it does not name keeps the
     * lower value.
     *
     * @param lower the assignment of the lower level
     * @return the overlaid assignment
     */
    public RoleAssignment over(RoleAssignment lower) {
        return new RoleAssignment(harness.or(lower::harness), model.or(lower::model), effort.or(lower::effort));
    }
}
