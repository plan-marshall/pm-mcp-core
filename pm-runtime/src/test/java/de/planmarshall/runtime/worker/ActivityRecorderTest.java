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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * Every appended activity entry is the evaluation point of the off-track signals, and one cause takes its action once
 * (Model Work § A Worker Off Track, § Output Read While the Worker Runs).
 */
@DisplayName("Activity recorder")
class ActivityRecorderTest {

    private static final String TASK = "qt-0000000000000001";
    private static final String GENERATION = "l-0123456789abcdef";
    private static final Instant AT = Instant.parse("2026-10-26T10:00:00Z");

    private final RecordingProtocol protocol = new RecordingProtocol();

    private ActivityJournal journal(Path base) {
        return ActivityJournal.ofScope(base, "project-a",
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, "plan-a"),
                new FileLockManager(_ -> {
                }));
    }

    private ActivityRecorder recorder(ActivityJournal journal, int attempt) {
        var role = OffTrackDetectorTest.role(Map.of(RoleArtifact.OffTrackSignal.REPEATING,
                new RoleArtifact.OffTrackSetting(3.0, RoleArtifact.OffTrackAction.SHOW)));
        WorkerTaskActivity tasks = queueTaskId -> Optional.of(
                new WorkerTaskActivity.TaskActivity(attempt, role, Set.of(), null, -1));
        return new ActivityRecorder(journal, tasks, protocol, new OffTrackDetector(), InstantSource.fixed(AT));
    }

    private static ActivityEntry.ActivityEntryBuilder toolCall(String tool, String target) {
        return ActivityEntry.builder()
                .source(ActivityEntry.Source.HARNESS)
                .kind(ActivityEntry.Kind.TOOL_CALL)
                .tool(new ActivityEntry.Tool(tool, target));
    }

    @Test
    @DisplayName("the entry is written with the journal's sequence and the task's attempt")
    void appends(@TempDir Path base) {
        var journal = journal(base);
        var written = recorder(journal, 1).append(TASK, GENERATION, toolCall("read_file", "A.java"));

        assertAll(
                () -> assertEquals(1, written.seq()),
                () -> assertEquals(1, written.attempt()),
                () -> assertEquals("A.java", written.tool().target()),
                () -> assertEquals(1, journal.entries(TASK).size()));
    }

    @Test
    @DisplayName("a signal that holds is reported once, not again on the next entry")
    void reportsOnce(@TempDir Path base) {
        var journal = journal(base);
        var recorder = recorder(journal, 1);

        recorder.append(TASK, GENERATION, toolCall("read_file", "A.java"));
        recorder.append(TASK, GENERATION, toolCall("read_file", "A.java"));
        assertEquals(0, protocol.signals.size());

        recorder.append(TASK, GENERATION, toolCall("read_file", "A.java"));
        recorder.append(TASK, GENERATION, toolCall("read_file", "A.java"));

        assertEquals(1, protocol.signals.size());
        assertAll(
                () -> assertEquals("repeating", protocol.signals.getFirst().signal),
                () -> assertEquals("show", protocol.signals.getFirst().action),
                () -> assertEquals(3.0, protocol.signals.getFirst().measurement),
                () -> assertEquals(3.0, protocol.signals.getFirst().threshold));
    }

    @Test
    @DisplayName("a task whose facts cannot be read gets no entry and no signal")
    void unknownTask(@TempDir Path base) {
        var journal = journal(base);
        WorkerTaskActivity tasks = _ -> Optional.empty();
        var recorder = new ActivityRecorder(journal, tasks, protocol, new OffTrackDetector(),
                InstantSource.fixed(AT));

        var entry = toolCall("x", "y");
        assertThrows(IllegalStateException.class,
                () -> recorder.append(TASK, GENERATION, entry));
        assertTrue(journal.entries(TASK).isEmpty());
        assertTrue(protocol.signals.isEmpty());
    }

    /** Records what the recorder reports. */
    private static final class RecordingProtocol implements WorkerProtocol {

        private final List<Reported> signals = new ArrayList<>();

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
            signals.add(new Reported(report.signal(), report.action(), report.measurement(), report.threshold(),
                    report.attempt()));
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            throw new UnsupportedOperationException();
        }
    }

    private record Reported(String signal, String action, double measurement, double threshold, int attempt) {
    }
}
