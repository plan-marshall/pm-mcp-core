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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.store.JobId;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Every appended activity entry is the evaluation point of the off-track signals, and one cause takes its action once
 * (Model Work § A Worker Off Track, § Output Read While the Worker Runs).
 * <p>
 * The task id, the generation, the project and plan ids, the tool name and its target are generated in the
 * {@code @BeforeEach}; the fixed instant, the signal names {@code repeating} and {@code show}, and the threshold of
 * three are the contracts of the detector.
 */
@EnableGeneratorController
@DisplayName("Activity recorder")
class ActivityRecorderTest {

    private static final Instant AT = Instant.parse("2026-10-26T10:00:00Z");

    /** The off-track reports the recorder reported, projected to the values a test asserts. */
    private final List<Reported> signals = new ArrayList<>();

    private final WorkerProtocol protocol = protocol();

    private String task;

    private String generation;

    private String project;

    private String plan;

    private String tool;

    private String target;

    @BeforeEach
    void draw() {
        task = RuntimeGenerators.queueTaskIds().next();
        generation = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        project = RuntimeGenerators.projectIds().next();
        plan = RuntimeGenerators.planIds().next();
        tool = RuntimeGenerators.words().next();
        target = Generators.letterStrings(1, 12).next() + ".java";
    }

    private ActivityJournal journal(Path base) {
        return ActivityJournal.ofScope(base, project,
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, plan),
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
        var written = recorder(journal, 1).append(task, generation, toolCall(tool, target));

        assertAll(
                () -> assertEquals(1, written.seq()),
                () -> assertEquals(1, written.attempt()),
                () -> assertEquals(target, written.tool().target()),
                () -> assertEquals(1, journal.entries(task).size()));
    }

    @Test
    @DisplayName("a signal that holds is reported once, not again on the next entry")
    void reportsOnce(@TempDir Path base) {
        var journal = journal(base);
        var recorder = recorder(journal, 1);

        recorder.append(task, generation, toolCall(tool, target));
        recorder.append(task, generation, toolCall(tool, target));
        assertEquals(0, signals.size());

        recorder.append(task, generation, toolCall(tool, target));
        recorder.append(task, generation, toolCall(tool, target));

        assertEquals(1, signals.size());
        assertAll(
                () -> assertEquals("repeating", signals.getFirst().signal),
                () -> assertEquals("show", signals.getFirst().action),
                () -> assertEquals(3.0, signals.getFirst().measurement),
                () -> assertEquals(3.0, signals.getFirst().threshold));
    }

    @Test
    @DisplayName("a task whose facts cannot be read gets no entry and no signal")
    void unknownTask(@TempDir Path base) {
        var journal = journal(base);
        WorkerTaskActivity tasks = _ -> Optional.empty();
        var recorder = new ActivityRecorder(journal, tasks, protocol, new OffTrackDetector(),
                InstantSource.fixed(AT));

        var entry = toolCall(tool, target);
        assertThrows(IllegalStateException.class,
                () -> recorder.append(task, generation, entry));
        assertTrue(journal.entries(task).isEmpty());
        assertTrue(signals.isEmpty());
    }

    private WorkerProtocol protocol() {
        WorkerProtocol mock = createMock(WorkerProtocol.class);
        mock.offTrack(anyObject(WorkerProtocol.OffTrackReport.class));
        expectLastCall().andAnswer(() -> {
            var report = (WorkerProtocol.OffTrackReport) getCurrentArguments()[0];
            signals.add(new Reported(report.signal(), report.action(), report.measurement(), report.threshold(),
                    report.attempt()));
            return null;
        }).anyTimes();
        replay(mock);
        return mock;
    }

    private record Reported(String signal, String action, double measurement, double threshold, int attempt) {
    }
}
