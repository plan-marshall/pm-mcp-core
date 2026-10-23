/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.queue;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.QueueTaskRecord.SignalAction;
import de.planmarshall.core.queue.QueueTaskRecord.SignalName;

/**
 * The activity journal of a task lies in the store of the task's scope and appends one entry per line under its own
 * leaf lock (Store Schemas § Activity Journal).
 */
@DisplayName("Activity journal")
class ActivityJournalTest {

    private static final String TASK = "qt-0000000000000001";

    private static ActivityEntry signal(int seq) {
        return ActivityEntry.builder()
                .seq(seq)
                .attempt(1)
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.SIGNAL)
                .signal(new ActivityEntry.Signal(SignalName.AGENT_STARTED, SignalAction.RESTART))
                .at(QueueFixtures.AT)
                .build();
    }

    @Test
    @DisplayName("a plan's journal is plans/<plan_id>/tasks/activity/<queue_task_id>.jsonl")
    void planPath(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a", QueueFixtures.planScope(), new TestLocks());

        assertEquals(base.resolve("projects/project-a/plans/plan-a/tasks/activity/" + TASK + ".jsonl")
                .toAbsolutePath().normalize(), journal.journalPath(TASK));
    }

    @Test
    @DisplayName("the machine's journal is machine/tasks/activity/<queue_task_id>.jsonl")
    void machinePath(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, null,
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.MACHINE, null), new TestLocks());

        assertEquals(base.resolve("machine/tasks/activity/" + TASK + ".jsonl").toAbsolutePath().normalize(),
                journal.journalPath(TASK));
    }

    @Test
    @DisplayName("entries are appended and read in sequence order")
    void appendAndRead(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a", QueueFixtures.planScope(), new TestLocks());

        journal.append(TASK, signal(1));
        journal.append(TASK, signal(2));

        assertAll(
                () -> assertEquals(List.of(signal(1), signal(2)), journal.entries(TASK)),
                () -> assertEquals(2, journal.entries(TASK).size()));
    }

    @Test
    @DisplayName("an entry whose sequence is not the next one is refused")
    void sequence(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a", QueueFixtures.planScope(), new TestLocks());
        journal.append(TASK, signal(1));

        assertThrows(IllegalArgumentException.class, () -> journal.append(TASK, signal(3)));
    }
}
