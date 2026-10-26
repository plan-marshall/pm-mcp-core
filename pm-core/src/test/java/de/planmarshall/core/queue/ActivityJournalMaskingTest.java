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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The journal masks what it writes, is archived with its plan and deleted with it (Store Schemas § Activity Journal).
 */
@DisplayName("Activity journal masking and lifetime")
class ActivityJournalMaskingTest {

    private static final String TASK = "qt-0000000000000001";

    @Test
    @DisplayName("an appended entry is masked before it is written")
    void masksOnAppend(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a", QueueFixtures.planScope(), new TestLocks());
        journal.append(TASK, ActivityEntry.builder()
                .seq(1)
                .attempt(1)
                .source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TEXT)
                .text("api_key=abcd1234")
                .at(QueueFixtures.AT)
                .build());

        assertEquals("api_key=" + SecretMasker.REDACTED, journal.entries(TASK).getFirst().text());
    }

    @Test
    @DisplayName("a task's journal is archived with the plan and deleted with it")
    void archiveAndDelete(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a", QueueFixtures.planScope(), new TestLocks());
        journal.append(TASK, ActivityEntry.builder()
                .seq(1)
                .attempt(1)
                .source(ActivityEntry.Source.SERVER)
                .kind(ActivityEntry.Kind.SIGNAL)
                .signal(new ActivityEntry.Signal(QueueTaskRecord.SignalName.REPEATING,
                        QueueTaskRecord.SignalAction.SHOW))
                .at(QueueFixtures.AT)
                .build());

        var archived = journal.archiveInto(TASK, base.resolve("archive"));

        assertAll(
                () -> assertTrue(archived.isPresent()),
                () -> assertTrue(Files.exists(archived.get())),
                () -> assertTrue(Files.exists(journal.journalPath(TASK))));

        journal.delete(TASK);

        assertAll(
                () -> assertFalse(Files.exists(journal.journalPath(TASK))),
                () -> assertFalse(Files.exists(journal.directory().resolve(TASK + ".jsonl.lock"))));
    }

    @Test
    @DisplayName("archiving a task without a journal writes nothing")
    void archiveAbsent(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a", QueueFixtures.planScope(), new TestLocks());

        assertTrue(journal.archiveInto(TASK, base.resolve("archive")).isEmpty());
    }
}
