/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.task;

/**
 * Why a call on a task entity was refused (MCP Tools § Errors). The checks run in the order of the constants.
 *
 * @since 0.1
 */
public enum TaskRefusal {

    /** The worker's job ended before the call. */
    JOB_ENDED("job_ended"),
    /** The caller's generation was fenced; the refusal comes before every other check. */
    GENERATION_FENCED("generation_fenced"),
    /** The task closed while the caller's generation held it; the answer carries the entity's representation. */
    STALE_ENTITY("stale_entity"),
    /** The entity is not leased to the caller's generation. */
    JOB_SCOPE_MISMATCH("job_scope_mismatch"),
    /** The link is not offered in the entity's current state. */
    LINK_NOT_AVAILABLE("link_not_available"),
    /** The arguments do not match the link's schema. */
    INVALID_LINK_ARGS("invalid_link_args"),
    /** A submit came while a skill the task requires is missing from the generation's delivery record. */
    SKILLS_NOT_LOADED("skills_not_loaded");

    private final String wireName;

    TaskRefusal(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the refusal in the protocol */
    public String wireName() {
        return wireName;
    }
}
