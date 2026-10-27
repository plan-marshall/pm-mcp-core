/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.web;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * A task's activity is served from a cursor and as a live stream, and a plan's process names a link to it
 * (Store Schemas § Activity Journal).
 */
@DisplayName("Activity service")
class ActivityServiceTest {

    private static final String TASK = "qt-0000000000000001";
    private static final Instant AT = Instant.parse("2026-10-26T10:00:00Z");

    private ActivityJournal journal(Path base) {
        return ActivityJournal.ofScope(base, "project-a",
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, "plan-a"),
                new FileLockManager(_ -> {
                }));
    }

    private static ActivityEntry text(int seq, String text) {
        return ActivityEntry.builder().seq(seq).attempt(1).source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TEXT).text(text).at(AT).build();
    }

    private static void append(ActivityJournal journal, int count) {
        var start = journal.entries(TASK).size();
        for (var index = 1; index <= count; index++) {
            journal.append(TASK, text(start + index, "line " + (start + index)));
        }
    }

    @Test
    @DisplayName("a page carries the entries after the cursor, its next cursor and whether more follow")
    void pages(@TempDir Path base) {
        var journal = journal(base);
        append(journal, 5);
        var service = new ActivityService(journal);

        assertAll(
                () -> assertEquals(List.of(1, 2), service.page(TASK, 0, 2).entries().stream()
                        .map(ActivityEntry::seq).toList()),
                () -> assertEquals(2, service.page(TASK, 0, 2).nextCursor()),
                () -> assertTrue(service.page(TASK, 0, 2).hasMore()),
                () -> assertEquals(List.of(3, 4), service.page(TASK, 2, 2).entries().stream()
                        .map(ActivityEntry::seq).toList()),
                () -> assertEquals(List.of(5), service.page(TASK, 4, 2).entries().stream()
                        .map(ActivityEntry::seq).toList()),
                () -> assertFalse(service.page(TASK, 4, 2).hasMore()));
    }

    @Test
    @DisplayName("an empty page keeps the cursor and shows no more")
    void emptyPage(@TempDir Path base) {
        var service = new ActivityService(journal(base));

        assertAll(
                () -> assertTrue(service.page(TASK, 7, 10).entries().isEmpty()),
                () -> assertEquals(7, service.page(TASK, 7, 10).nextCursor()),
                () -> assertFalse(service.page(TASK, 7, 10).hasMore()));
    }

    @Test
    @DisplayName("a cursor and a page bound are checked")
    void refusesBadRequest(@TempDir Path base) {
        var service = new ActivityService(journal(base));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> service.page(TASK, -1, 10)),
                () -> assertThrows(IllegalArgumentException.class, () -> service.page(TASK, 0, 0)),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> service.page(TASK, 0, ActivityService.MAX_PAGE + 1)));
    }

    @Test
    @DisplayName("a live stream answers what was written since the previous poll and nothing after its close")
    void liveStream(@TempDir Path base) {
        var journal = journal(base);
        append(journal, 1);
        try (var stream = new ActivityService(journal).open(TASK, 0)) {
            var first = stream.poll();
            append(journal, 3);
            var second = stream.poll();
            var third = stream.poll();

            assertAll(
                    () -> assertEquals(List.of(1), first.stream().map(ActivityEntry::seq).toList()),
                    () -> assertEquals(List.of(2, 3, 4), second.stream().map(ActivityEntry::seq).toList()),
                    () -> assertEquals(4, stream.cursor()),
                    () -> assertTrue(third.isEmpty()));
        }
    }

    @Test
    @DisplayName("a closed stream refuses a further poll")
    void closedStream(@TempDir Path base) {
        var stream = new ActivityService(journal(base)).open(TASK, 0);

        stream.close();

        assertAll(
                () -> assertTrue(stream.isClosed()),
                () -> assertThrows(IllegalStateException.class, stream::poll));
    }

    @Test
    @DisplayName("a leaf names each of its tasks with the same activity link the task resource spells")
    void leafLinks() {
        var links = ActivityLinks.ofLeaf(List.of("qt-0000000000000001", "qt-0000000000000002"));

        assertAll(
                () -> assertEquals("pm://task/qt-0000000000000001/activity", links.getFirst().link()),
                () -> assertEquals(2, links.size()),
                () -> assertThrows(IllegalArgumentException.class, () -> ActivityLinks.of(" ")));
    }
}
