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
 * A task's facts cannot be read: the task is unknown or already closed, so there is no attempt to append to. The
 * supervision pass treats it as the benign race it is — a task that closed between the read of its facts and the
 * append — and every other failure of a pump is not this one and must not be swallowed.
 *
 * @since 0.1
 */
public final class TaskNotOpenException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    /**
     * @param queueTaskId the task whose facts cannot be read
     */
    public TaskNotOpenException(String queueTaskId) {
        super("The activity names no open task: " + queueTaskId);
    }
}
