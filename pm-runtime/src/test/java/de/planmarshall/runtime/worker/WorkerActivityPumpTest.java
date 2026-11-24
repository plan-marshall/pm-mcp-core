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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        return pump(journal, Map.of(), ServedModelWatch.recordingNothing());
    }

    private static WorkerActivityPump pump(ActivityJournal journal, Map<String, String> usagePointers,
            ServedModelWatch watch) {
        var mapping = ActivityEventMapping.of(
                JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events")).orElseThrow();
        var mapper = new ActivityEventMapper(mapping, usagePointers);
        var role = OffTrackDetectorTest.role(Map.of());
        WorkerTaskActivity tasks = queueTaskId -> Optional.of(
                new WorkerTaskActivity.TaskActivity(1, role, Set.of(), null, -1));
        var recorder = new ActivityRecorder(journal, tasks, noSignals(), new OffTrackDetector(),
                InstantSource.fixed(AT));
        return new WorkerActivityPump(mapper, recorder, watch);
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

        var first = pump.pump(task, generation, 1, new JobLog(log));
        append(log, "{\"type\":\"file\",\"path\":\"" + file + "\"}");
        var second = pump.pump(task, generation, 1, new JobLog(log));
        var unmapped = pump.unmappedLines();
        var end = pump.finish(task, generation, QueueTaskRecord.AttemptEnd.OFF_TRACK);

        assertAll(
                () -> assertEquals(2, first, "first pass appends the two mapped lines"),
                () -> assertEquals(1, second, "second pass appends only the file line"),
                () -> assertEquals(4, journal.entries(task).size(),
                        "the journal holds the mapped entries and the end"),
                () -> assertEquals(1, unmapped, "one line matches no event mapping"),
                () -> assertEquals(1, end.unmappedLines(), "the attempt end reports the same unmatched count"),
                () -> assertEquals(ActivityEntry.Kind.ATTEMPT_END, end.kind(),
                        "the end entry carries the attempt end kind"),
                () -> assertEquals("off_track", end.end(), "the end marker is the off-track reason"),
                () -> assertEquals(0, pump.unmappedLines(), "finishing clears the unmapped line counter"));
    }

    @Test
    @DisplayName("each model change writes its model_differs entry before the line's own entry")
    void servedModelChanges(@TempDir Path base) {
        var launch = "launch-" + Generators.letterStrings(3, 12).next();
        var first = "first-" + Generators.letterStrings(3, 12).next();
        var second = "second-" + Generators.letterStrings(3, 12).next();
        var journal = ActivityJournal.ofScope(base, project,
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, plan),
                new FileLockManager(_ -> {
                }));
        var log = base.resolve("worker.log");
        append(log, line(launch));
        append(log, line(first));
        append(log, line(first));
        append(log, line(second));
        var pump = pump(journal, Map.of("model", "/message/model"), new ServedModelWatch(launch));

        pump.pump(task, generation, 1, new JobLog(log));

        var entries = journal.entries(task);
        var models = entries.stream().filter(entry -> entry.kind() == ActivityEntry.Kind.MODEL_DIFFERS).toList();
        assertAll(
                () -> assertEquals(2, models.size(), "the two changes write two entries"),
                () -> assertEquals(launch, models.getFirst().launchModel(), "the first entry names the launch model"),
                () -> assertEquals(first, models.getFirst().servedModel(), "the first entry names the first fallback"),
                () -> assertEquals(second, models.get(1).servedModel(), "the second entry names the second fallback"),
                () -> assertEquals(ActivityEntry.Kind.MODEL_DIFFERS, entries.get(1).kind(),
                        "the change is written before the line's own entry"),
                () -> assertEquals(ActivityEntry.Kind.MODEL_DIFFERS, entries.get(4).kind(),
                        "the second change precedes its line too"),
                () -> assertEquals(6, entries.size(), "the four lines and the two changes are appended"));
    }

    @Test
    @DisplayName("a mapper without the model pointer writes no model_differs entry")
    void noModelPointer(@TempDir Path base) {
        var model = Generators.letterStrings(3, 12).next();
        var journal = ActivityJournal.ofScope(base, project,
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, plan),
                new FileLockManager(_ -> {
                }));
        var log = base.resolve("worker.log");
        append(log, line(model));
        var pump = pump(journal, Map.of(), new ServedModelWatch(model));

        pump.pump(task, generation, 1, new JobLog(log));

        assertEquals(1, journal.entries(task).size(), "only the line's own entry is written");
    }

    private static String line(String model) {
        return "{\"type\":\"assistant\",\"message\":{\"content\":\""
                + Generators.letterStrings(1, 30).next() + "\",\"model\":\"" + model + "\"}}";
    }

    @Test
    @DisplayName("an attempt whose tail cannot be read stays open instead of moving to the next task")
    void anUnreadableTailKeepsTheAttemptOpen(@TempDir Path base) throws Exception {
        var journal = ActivityJournal.ofScope(base, project,
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, plan),
                new FileLockManager(_ -> {
                }));
        var log = base.resolve("worker.log");
        // A directory at the log's place: a read of it fails for a reason that may heal, unlike a missing log.
        Files.createDirectories(log);
        var pump = pump(journal);
        var next = RuntimeGenerators.queueTaskIds().next();

        assertAll(
                () -> assertEquals(0, pump.pump(task, generation, 1, new JobLog(log)),
                        "an unreadable log appends nothing"),
                () -> assertFalse(pump.close(generation, new JobLog(log)).isPresent(),
                        "an unread tail writes no attempt end"),
                () -> assertEquals(0, pump.pump(next, generation, 1, new JobLog(log)),
                        "the pump does not switch to the next task"),
                () -> assertTrue(journal.entries(task).isEmpty(), "no line is attributed to the task"));

        Files.delete(log);
        append(log, "{\"type\":\"assistant\",\"message\":{\"content\":\"" + text + "\"}}");
        var healed = pump.close(generation, new JobLog(log));

        assertAll(
                () -> assertTrue(healed.isPresent(), "the healed tail closes the attempt"),
                () -> assertEquals(2, journal.entries(task).size(), "the healed attempt carries its line and end"));
    }
}
