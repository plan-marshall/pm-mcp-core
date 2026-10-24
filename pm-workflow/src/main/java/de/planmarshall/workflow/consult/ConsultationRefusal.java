/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.consult;

/**
 * Why following a consultation link, or validating a submit that refers to a consultation, was refused. The names are
 * the ones the server reports (MCP Tools § Errors); the first five are the task protocol's own names, because a
 * consultation is a task of the consulted role and shares its call contract.
 *
 * @since 0.1
 */
public enum ConsultationRefusal {

    /** The task is not the caller's, or it no longer exists. */
    JOB_SCOPE_MISMATCH("job_scope_mismatch"),
    /** The link is not offered in the entity's current state. */
    LINK_NOT_AVAILABLE("link_not_available"),
    /** The entity changed while the caller held it: the consultation already closed. */
    STALE_ENTITY("stale_entity"),
    /** The arguments do not match the link's schema. */
    INVALID_LINK_ARGS("invalid_link_args"),
    /** The consulted role has no tool and no protocol to answer through. */
    CLASS_MISMATCH("class_mismatch"),
    /** The task already has an open consultation. */
    CONSULTATION_OPEN("consultation_open"),
    /** The task followed the compiled number of consultation links over all its attempts. */
    CONSULTATION_BOUND_SPENT("consultation_bound_spent"),
    /** A consultation task offers no consultation; a consulted task never consults. */
    CONSULTATION_DEPTH("consultation_depth");

    private final String wireName;

    ConsultationRefusal(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the refusal in the protocol */
    public String wireName() {
        return wireName;
    }
}
