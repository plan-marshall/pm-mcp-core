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

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityEventMapping;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.store.JobId;
import de.planmarshall.runtime.job.JobLog;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The pump reads a running worker's stream into its journal as the lines arrive and counts a line that matches no
 * mapping (Model Work § Output Read While the Worker Runs).
 * <p>
 * The task id, the generation, the project and plan ids, the two file paths and the text are generated; the pointer
 * document, the event kinds and the {@code off_track} end marker are the wire contract.
 */
@EnableGeneratorController
@DisplayName("Worker activity pump")
class WorkerActivityPumpTest {

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

    private String task;

    private String generation;

    private String project;

    private String plan;

    private String file;

    private String text;

    @BeforeEach
    void draw() {
        task = RuntimeGenerators.queueTaskIds().next();
        generation = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        project = RuntimeGenerators.projectIds().next();
        plan = RuntimeGenerators.planIds().next();
        file = Generators.letterStrings(1, 12).next() + ".java";
        text = Generators.letterStrings(1, 30).next();
    }

    private static WorkerActivityPump pump(ActivityJournal journal) {
        var mapping = ActivityEventMapping.of(
                JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events")).orElseThrow();
        var mapper = new ActivityEventMapper(mapping, Map.of());
        var role = OffTrackDetectorTest.role(Map.of());
        WorkerTaskActivity tasks = queueTaskId -> Optional.of(
                new WorkerTaskActivity.TaskActivity(1, role, Set.of(), null, -1));
        var recorder = new ActivityRecorder(journal, tasks, noSignals(), new OffTrackDetector(),
                InstantSource.fixed(AT));
        return new WorkerActivityPump(mapper, recorder);
    }

    /** @return an EasyMock protocol with no expectation: the pump raises no signal in these fixtures */
    private static WorkerProtocol noSignals() {
        WorkerProtocol mock = createMock(WorkerProtocol.class);
        replay(mock);
        return mock;
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
        var journal = ActivityJournal.ofScope(base, project,
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, plan),
                new FileLockManager(_ -> {
                }));
        var log = base.resolve("worker.log");
        append(log, "{\"type\":\"assistant\",\"message\":{\"content\":\"" + text + "\"}}");
        append(log, "{\"type\":\"unknown\"}");
        append(log, "{\"type\":\"tool_use\",\"name\":\"read_file\",\"input\":{\"file_path\":\"" + file + "\"}}");
        var pump = pump(journal);

        var first = pump.pump(task, generation, new JobLog(log));
        append(log, "{\"type\":\"file\",\"path\":\"" + file + "\"}");
        var second = pump.pump(task, generation, new JobLog(log));
        var unmapped = pump.unmappedLines();
        var end = pump.finish(task, generation, QueueTaskRecord.AttemptEnd.OFF_TRACK);

        assertAll(
                () -> assertEquals(2, first),
                () -> assertEquals(1, second),
                () -> assertEquals(4, journal.entries(task).size()),
                () -> assertEquals(1, unmapped),
                () -> assertEquals(1, end.unmappedLines()),
                () -> assertEquals(ActivityEntry.Kind.ATTEMPT_END, end.kind()),
                () -> assertEquals("off_track", end.end()),
                () -> assertEquals(0, pump.unmappedLines()));
    }
}
