/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.model;

import java.util.Optional;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleValidationReason;

/**
 * What the one model caller did with a {@code job llm} state (PM-WORK-1, The Model Caller): it queued the task for the
 * role, or it closed the task without an attempt with the reason of the caller.
 *
 * @since 0.1
 */
public sealed interface ModelCallResult {

    /** The reasons a task the model caller closes without an attempt carries ({@code model-work/02#task-outcomes}). */
    String ROLE_UNDEFINED = "role_undefined";
    /** The role's class does not fit the use, or it became so after composition. */
    String CLASS_MISMATCH = "class_mismatch";
    /** No valid qualification record covers the role's qualification digest. */
    String ROLE_UNQUALIFIED = "role_unqualified";
    /** The start-failure guard of the role holds on its harness. */
    String ROLE_UNAVAILABLE = "role_unavailable";

    /**
     * A queued task.
     *
     * @param queueTaskId     the identifier of the created queue task
     * @param role            the role the task runs on
     * @param form            the form the task runs, which is the artifact's form or {@code fresh} after a downgrade
     * @param configurationDigest the configuration digest of the role at creation
     * @param formDowngraded  whether a warm artifact runs fresh because only the fresh form qualifies
     * @since 0.1
     */
    record Queued(String queueTaskId, String role, RoleForm form, String configurationDigest, boolean formDowngraded)
            implements ModelCallResult {
    }

    /**
     * A task closed without an attempt.
     *
     * @param role             the role the state names
     * @param reason           one of {@link #ROLE_UNDEFINED}, {@link #CLASS_MISMATCH}, {@link #ROLE_UNQUALIFIED} or
     *                         {@link #ROLE_UNAVAILABLE}
     * @param validationReason the validation reason of an undefined role, empty otherwise
     * @param detail           what is wrong, naming the value where one exists
     * @since 0.1
     */
    record Closed(String role, String reason, Optional<RoleValidationReason> validationReason, String detail)
            implements ModelCallResult {
    }
}
