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

import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.QueueTaskRecord;

/**
 * Where the activity journal of a task lies (Store Schemas § Activity Journal): a journal is per scope, and one queue
 * holds the tasks of plans, epics, the workspace and the machine, so the protocol resolves the journal from the scope
 * of the task it records.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface JournalProvider {

    /**
     * @param scope the scope of the task
     * @return the journal of the scope, or {@code null} for a scope without one
     */
    ActivityJournal journalFor(QueueTaskRecord.Scope scope);
}
