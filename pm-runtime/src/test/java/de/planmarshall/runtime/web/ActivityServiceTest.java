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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
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
 * <p>
 * The form of the activity link is the specified URI; the task ids, scopes, entry texts, instants, entry counts, page
 * bounds and cursors are generated.
 */
@EnableGeneratorController
@DisplayName("Activity service")
class ActivityServiceTest {

    private final String task = taskId();
    private final Instant at = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 2_000_000_000L).next());

    /** @return a generated queue task id of the form {@code qt-} and 16 lower-case hex digits */
    private static String taskId() {
        return "qt-%016x".formatted(Generators.longs().next());
    }

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private ActivityJournal journal(Path base) {
        return ActivityJournal.ofScope(base, lower(),
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, lower()),
                new FileLockManager(_ -> {
                }));
    }

    private ActivityEntry text(int seq) {
        // Generated text with spaces and punctuation, as a harness writes it
        var text = lower() + " " + lower() + ", " + lower() + ".";
        return ActivityEntry.builder().seq(seq).attempt(1).source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TEXT).text(text).at(at).build();
    }

    private void append(ActivityJournal journal, int count) {
        var start = journal.entries(task).size();
        for (var index = 1; index <= count; index++) {
            journal.append(task, text(start + index));
        }
    }

    private static List<Integer> seqs(int fromInclusive, int toInclusive) {
        return IntStream.rangeClosed(fromInclusive, toInclusive).boxed().toList();
    }

    @Test
    @DisplayName("a page carries the entries after the cursor, its next cursor and whether more follow")
    void pages(@TempDir Path base) {
        var journal = journal(base);
        int count = Generators.integers(3, 30).next();
        int bound = Generators.integers(1, count - 1).next();
        append(journal, count);
        var service = new ActivityService(journal);

        var first = service.page(task, 0, bound);
        var second = service.page(task, bound, bound);
        var last = service.page(task, count - 1, bound);

        assertAll(
                () -> assertEquals(seqs(1, bound), first.entries().stream().map(ActivityEntry::seq).toList(),
                        "the first page must start after cursor 0"),
                () -> assertEquals(bound, first.nextCursor(), "the next cursor is the last sequence served"),
                () -> assertTrue(first.hasMore(), "more entries follow the first page"),
                () -> assertEquals(seqs(bound + 1, Math.min(2 * bound, count)),
                        second.entries().stream().map(ActivityEntry::seq).toList(),
                        "the second page must start after its cursor"),
                () -> assertEquals(2 * bound < count, second.hasMore(),
                        "the second page shows more only when more follow"),
                () -> assertEquals(List.of(count), last.entries().stream().map(ActivityEntry::seq).toList(),
                        "the last page must hold the last entry"),
                () -> assertFalse(last.hasMore(), "nothing follows the last page"));
    }

    @Test
    @DisplayName("an empty page keeps the cursor and shows no more")
    void emptyPage(@TempDir Path base) {
        var service = new ActivityService(journal(base));
        int cursor = Generators.integers(0, 1_000).next();
        int bound = Generators.integers(1, ActivityService.MAX_PAGE).next();

        var page = service.page(task, cursor, bound);

        assertAll(
                () -> assertTrue(page.entries().isEmpty(), "an empty journal serves no entry"),
                () -> assertEquals(cursor, page.nextCursor(), "an empty page must keep the cursor"),
                () -> assertFalse(page.hasMore(), "an empty page shows no more"));
    }

    @Test
    @DisplayName("a cursor and a page bound are checked (bound 0 is the specified lower limit)")
    void refusesBadRequest(@TempDir Path base) {
        var service = new ActivityService(journal(base));
        int negativeCursor = -Generators.integers(1, 1_000).next();
        int overBound = ActivityService.MAX_PAGE + Generators.integers(1, 1_000).next();

        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> service.page(task, negativeCursor, 1),
                        "a negative cursor must be refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> service.page(task, 0, 0),
                        "a page bound of zero must be refused"),
                () -> assertThrows(IllegalArgumentException.class, () -> service.page(task, 0, overBound),
                        "a bound above the maximum must be refused"));
    }

    @Test
    @DisplayName("a live stream answers what was written since the previous poll and nothing after its close")
    void liveStream(@TempDir Path base) {
        var journal = journal(base);
        int before = Generators.integers(1, 10).next();
        int since = Generators.integers(1, 10).next();
        append(journal, before);
        try (var stream = new ActivityService(journal).open(task, 0)) {
            var first = stream.poll();
            append(journal, since);
            var second = stream.poll();
            var third = stream.poll();

            assertAll(
                    () -> assertEquals(seqs(1, before), first.stream().map(ActivityEntry::seq).toList(),
                            "the first poll answers the entries before it"),
                    () -> assertEquals(seqs(before + 1, before + since),
                            second.stream().map(ActivityEntry::seq).toList(),
                            "the second poll answers the entries since"),
                    () -> assertEquals(before + since, stream.cursor(), "the cursor is the last entry answered"),
                    () -> assertTrue(third.isEmpty(), "a poll without new entries answers none"));
        }
    }

    @Test
    @DisplayName("a closed stream refuses a further poll")
    void closedStream(@TempDir Path base) {
        var stream = new ActivityService(journal(base)).open(task, 0);

        stream.close();

        assertAll(
                () -> assertTrue(stream.isClosed(), "the stream must report its close"),
                () -> assertThrows(IllegalStateException.class, stream::poll, "a closed stream refuses a poll"));
    }

    @Test
    @DisplayName("a leaf names each of its tasks with the same activity link the task resource spells")
    void leafLinks() {
        var other = taskId();

        var links = ActivityLinks.ofLeaf(List.of(task, other));

        assertAll(
                // Contract literal: the specified form of the activity link of a task
                () -> assertEquals("pm://task/" + task + "/activity", links.getFirst().link(),
                        "the link must name the task's activity"),
                () -> assertEquals(2, links.size(), "the leaf must name one link per task"),
                // Contract literal: a blank task id, which the link refuses
                () -> assertThrows(IllegalArgumentException.class, () -> ActivityLinks.of(" "),
                        "a blank task id must be refused"));
    }
}
