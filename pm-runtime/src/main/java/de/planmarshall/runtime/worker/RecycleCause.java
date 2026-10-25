/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

/**
 * Why one warm worker generation is recycled instead of being kept (PM-WORK-3, Recycling Warm Workers): the closed set
 * of triggers the supervisor decides on, with the end cause the recycle runs through {@link WorkerEnding}.
 * <p>
 * The enum adds the reason dimension the one end path does not carry: every recycle ends the generation through
 * {@link EndCause#RECYCLE}, so the job record's worker state names the instructed end-of-a-warm-worker, while the log
 * line and the report name the trigger that ended it ({@link #wireName()}).
 *
 * @since 0.1
 */
public enum RecycleCause {

    /** The generation received its idle wake-ups in a row without a task. */
    IDLE("idle", EndCause.RECYCLE),

    /** The context of the generation's latest turn reached the role's token budget. */
    TOKEN_BUDGET("token_budget", EndCause.RECYCLE),

    /** The generation closed the role's tasks per generation. */
    TASKS_PER_GENERATION("tasks_per_generation", EndCause.RECYCLE),

    /** The role's configuration digest differs from the one the generation was started with. */
    CONFIGURATION_CHANGED("configuration_changed", EndCause.RECYCLE),

    /** The generation yielded its model slot while idle, so another role's queued task is admitted. */
    IDLE_YIELD("idle_yield", EndCause.RECYCLE);

    private final String wireName;
    private final EndCause endCause;

    RecycleCause(String wireName, EndCause endCause) {
        this.wireName = wireName;
        this.endCause = endCause;
    }

    /** @return the name of the cause in the log and in a report */
    public String wireName() {
        return wireName;
    }

    /** @return the end cause the recycle runs through {@link WorkerEnding} */
    public EndCause endCause() {
        return endCause;
    }
}
