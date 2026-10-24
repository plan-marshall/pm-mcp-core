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
 * Whether the plan of a scope is paused (Runtime Lifecycle § Pausing and Starting a Plan).
 * <p>
 * A task of a paused plan stays {@code queued} and is offered to no worker until the operator starts the plan again.
 * The pause itself is the lifecycle's; the task protocol asks this port inside the queue transaction, so an
 * implementation must read a snapshot without taking another lock: a scope lock taken there would invert the lock order
 * (the scope lock is taken before the queue lock, never after it).
 *
 * @since 0.1
 */
@FunctionalInterface
public interface PlanPause {

    /** A pause state in which no plan is paused. */
    PlanPause NEVER = planId -> false;

    /**
     * @param planId the plan
     * @return whether the plan is paused
     */
    boolean isPaused(String planId);
}
