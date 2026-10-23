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

/**
 * Why a role artifact was refused (PM-WORK-1, Validation against Schema and Client Profile). The set is closed and is the
 * table of the specification row by row; an invalid role counts as undefined and never falls back to an artifact of a
 * lower set.
 * <p>
 * {@link #SESSION_FIRST_NOT_PERMITTED} is deliberately absent: the table has no such reason, and a validation reports
 * none.
 *
 * @since 0.1
 */
public enum RoleValidationReason {

    /** The artifact fails its schema, its name differs from its file name, or a member is invalid on the role's class. */
    SCHEMA_INVALID("schema_invalid"),
    /** No client profile has the {@code client_id} of the harness, or the harness is not enrolled on the machine. */
    HARNESS_UNKNOWN("harness_unknown"),
    /** The profile's {@code worker.eligible} is {@code false}. */
    HARNESS_NOT_ELIGIBLE("harness_not_eligible"),
    /** The profile's {@code headless.classes} entry for the role's class is {@code null}. */
    HARNESS_CLASS_NOT_RUNNABLE("harness_class_not_runnable"),
    /** No entry of {@code worker.restricted_models} matches the model: it refuses a restricted tool set. */
    MODEL_RESTRICTED("model_restricted"),
    /** The effort is below every value the harness accepts for the model, or non-null where the model takes none. */
    EFFORT_UNSUPPORTED("effort_unsupported"),
    /** The effective model catalogue does not allow the model on the role's harness; never for the steward. */
    MODEL_NOT_ALLOWED("model_not_allowed"),
    /** The tools name a tool outside the class's set, a forbidden harness tool, or omit a PM-MCP tool of the class. */
    TOOL_NOT_PERMITTED("tool_not_permitted"),
    /** A {@code zero_tool} role is {@code warm}. */
    FORM_NOT_PERMITTED("form_not_permitted"),
    /** A skill does not resolve in the project's skill catalogue, or the first skill is neither role nor ext skill. */
    SKILL_UNRESOLVED("skill_unresolved");

    private final String wireName;

    RoleValidationReason(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the reason in a representation */
    public String wireName() {
        return wireName;
    }
}
