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
 * Whether the job of a worker generation ended (Model Work § Execute &amp; Submit, first check). A call after the
 * worker's job ended is refused {@code job_ended}; the job record is the runtime's store, so the protocol asks this
 * port.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface JobState {

    /** A job state in which no job ended. */
    JobState NEVER = generationId -> false;

    /**
     * @param generationId the generation
     * @return whether the job of the generation ended
     */
    boolean hasEnded(String generationId);
}
