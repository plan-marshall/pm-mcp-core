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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.InstantSource;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityEventMapping;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.runtime.job.JobLog;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The pump reads a running worker's stream into its journal as the lines arrive and counts a line that matches no
 * mapping (Model Work § Output Read While the Worker Runs).
 */
@DisplayName("Worker activity pump")
class WorkerActivityPumpTest {

    private static final String TASK = "qt-0000000000000001";
    private static final String GENERATION = "l-0123456789abcdef";
    private static final Instant AT = Instant.parse("2026-10-26T10:00:00Z");

    private static final String EVENTS = """
            {
              "kind_pointer": "/type",
              "kinds": {
                "text": { "match": ["assistant"], "text": "/message/content" },
                "tool_call": { "match": ["tool_use"], "tool": "/name", "target": ["/input/file_path"] },
                "tool_result": { "match": ["tool_result"], "size": "/size", "is_error": "/failed" },
                "file_changed": { "match": ["file"], "path": "/path" },
                "turn": { "match": ["result"], "stop_reason": "/stop_reason" }
              }
            }
            """;

    private static WorkerActivityPump pump(ActivityJournal journal) {
        var mapping = ActivityEventMapping.of(
                JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events"));
        var mapper = new ActivityEventMapper(mapping, Map.of());
        var role = OffTrackDetectorTest.role(Map.of());
        WorkerTaskActivity tasks = queueTaskId -> Optional.of(
                new WorkerTaskActivity.TaskActivity(1, role, Set.of(), null, -1));
        var recorder = new ActivityRecorder(journal, tasks, new NoSignals(), new OffTrackDetector(),
                InstantSource.fixed(AT));
        return new WorkerActivityPump(mapper, recorder);
    }

    /** A protocol that records nothing; the pump raises no signal in these fixtures. */
    private static final class NoSignals implements WorkerProtocol {

        @Override
        public void fence(String generationId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void agentStarted(String generationId, String queueTaskId, int attempt, String executable,
                Instant at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void offTrack(OffTrackReport report) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            throw new UnsupportedOperationException();
        }
    }

    private static void append(Path log, String line) {
        try {
            Files.writeString(log, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("the fixture log cannot be written", e);
        }
    }

    @Test
    @DisplayName("only the lines gained since the previous pass are appended, and an unmatched line is counted")
    void pumpsNewLines(@TempDir Path base) {
        var journal = ActivityJournal.ofScope(base, "project-a",
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, "plan-a"),
                new FileLockManager(_ -> {
                }));
        var log = base.resolve("worker.log");
        append(log, "{\"type\":\"assistant\",\"message\":{\"content\":\"hello\"}}");
        append(log, "{\"type\":\"unknown\"}");
        append(log, "{\"type\":\"tool_use\",\"name\":\"read_file\",\"input\":{\"file_path\":\"A.java\"}}");
        var pump = pump(journal);

        var first = pump.pump(TASK, GENERATION, new JobLog(log));
        append(log, "{\"type\":\"file\",\"path\":\"A.java\"}");
        var second = pump.pump(TASK, GENERATION, new JobLog(log));
        var unmapped = pump.unmappedLines();
        var end = pump.finish(TASK, GENERATION, QueueTaskRecord.AttemptEnd.OFF_TRACK);

        assertAll(
                () -> assertEquals(2, first),
                () -> assertEquals(1, second),
                () -> assertEquals(4, journal.entries(TASK).size()),
                () -> assertEquals(1, unmapped),
                () -> assertEquals(1, end.unmappedLines()),
                () -> assertEquals(ActivityEntry.Kind.ATTEMPT_END, end.kind()),
                () -> assertEquals("off_track", end.end()),
                () -> assertEquals(0, pump.unmappedLines()));
    }
}
