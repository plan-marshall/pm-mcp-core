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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The journal masks what it writes, is archived with its plan and deleted with it (Store Schemas § Activity Journal).
 */
@EnableGeneratorController
@DisplayName("Activity journal masking and lifetime")
class ActivityJournalMaskingTest {

    private static final String TASK = "qt-" + "%016x".formatted(Generators.longs().next());

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

        assertEquals("api_key=" + SecretRedactor.REDACTED, journal.entries(TASK).getFirst().text(),
                "appended text is masked as a secret");
    }

    @Test
    @DisplayName("a secret at the text bound is redacted and refitted, not refused")
    void redactionAtTheBound(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a", QueueFixtures.planScope(), new TestLocks());
        var text = "password=abcd " + "x".repeat(ActivityEntry.TEXT_BOUND - 14);

        journal.append(TASK, ActivityEntry.builder()
                .seq(1)
                .attempt(1)
                .source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TEXT)
                .text(text)
                .at(QueueFixtures.AT)
                .build());

        var written = journal.entries(TASK).getFirst();
        assertAll(
                () -> assertEquals(ActivityEntry.TEXT_BOUND,
                        written.text().codePointCount(0, written.text().length()),
                        "redacted text is refitted to the bound"),
                () -> assertTrue(written.truncated(), "the refitted text is marked truncated"),
                () -> assertTrue(written.text().contains(SecretRedactor.REDACTED),
                        "the written text carries the redaction marker"));
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
                () -> assertTrue(archived.isPresent(), "archiving a task returns its target path"),
                () -> assertTrue(Files.exists(archived.get()),
                        "the archive file exists after archiving"),
                () -> assertTrue(Files.exists(journal.journalPath(TASK)),
                        "the live journal is kept after archiving"));

        journal.delete(TASK);

        assertAll(
                () -> assertFalse(Files.exists(journal.journalPath(TASK)),
                        "delete removes the task journal file"),
                () -> assertFalse(Files.exists(journal.directory().resolve(TASK + ".jsonl.lock")),
                        "delete removes the journal lock file"));
    }

    @Test
    @DisplayName("archiving a task without a journal writes nothing")
    void archiveAbsent(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a", QueueFixtures.planScope(), new TestLocks());

        assertTrue(journal.archiveInto(TASK, base.resolve("archive")).isEmpty(),
                "absent journal archives to no path");
    }
}
