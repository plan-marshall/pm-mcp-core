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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.queue.ActivityEventMapping;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.runtime.job.JobLog;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The off-track scenarios of Model Work § A Worker Off Track ("Verified by scenarios"): a worker's scripted output stream
 * is read by the real pump, recorder and detector, one scenario per signal and one per action. Each signal is raised at
 * its threshold and never one entry before it; each action is recorded with its signal, its measured value and its
 * threshold, and no action ends the plan or waits for the operator — the runtime only reports the signal to the task
 * protocol, which owns the action.
 * <p>
 * The stream is the fixture harness's: every line is printed by a started process and read back from its real output,
 * never handed to the mapper directly.
 */
@DisplayName("Off-track scenarios")
class OffTrackScenarioTest {

    private static final String TASK = "qt-0000000000000001";

    private static final String GENERATION = "l-0123456789abcdef";

    private static final Instant AT = Instant.parse("2026-10-26T10:00:00Z");

    private static final Duration AWAIT = Duration.ofSeconds(20);

    /** The lines the harness maps: a tool call, a tool result, a changed file, a turn and a text. */
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

    /** The usage pointers of the fixture profile: the two token figures a turn reports. */
    private static final Map<String, String> USAGE = Map.of("input_tokens", "/usage/input_tokens",
            "output_tokens", "/usage/output_tokens");

    private static final String LOOPING_READ = "{\"type\":\"tool_use\",\"name\":\"read_file\",\"input\":{\"file_path\":"
            + "\"A.java\"}}";

    private static final String TURN = "{\"type\":\"result\",\"stop_reason\":\"end_turn\"}";

    @TempDir
    Path base;

    /** One signal reported to the task protocol, with the facts it records. */
    private record Reported(String signal, String action, double measurement, double threshold) {
    }

    /** The observed run: the report count after every line of the stream, and every report in order. */
    private record Observed(List<Integer> reportsAfterEachLine, List<Reported> reports,
            List<String> fences, List<String> closedUnavailable) {
    }

    private static List<String> arguments(List<String> stream) {
        var arguments = new ArrayList<String>();
        stream.forEach(line -> {
            arguments.add("--output");
            arguments.add(line);
        });
        return arguments;
    }

    private static void append(Path log, String line) {
        try {
            Files.writeString(log, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("the fixture log cannot be written", e);
        }
    }

    /**
     * Replays a scripted output stream through the fixture harness, the pump and the recorder.
     *
     * @param stream          the lines the harness prints, in order
     * @param role            the role of the served task, with its off-track settings
     * @param predictedCost   the plan task's predicted cost, or {@code null} outside an envelope task
     * @param unplannedChange the unplanned changed files recorded for the plan task, or {@code -1} outside an envelope
     * @return the observed reports
     * @throws IOException if the harness cannot be started
     */
    private Observed replay(List<String> stream, RoleArtifact role, Double predictedCost, int unplannedChange)
            throws IOException {
        var directory = Files.createTempDirectory(base, "scenario");
        var journal = ActivityJournal.ofScope(directory, "project-a",
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, "plan-a"),
                new FileLockManager(_ -> {
                }));
        var protocol = new RecordingProtocol();
        WorkerTaskActivity tasks = _ -> Optional.of(
                new WorkerTaskActivity.TaskActivity(1, role, Set.of(), predictedCost, unplannedChange));
        var recorder = new ActivityRecorder(journal, tasks, protocol, new OffTrackDetector(), InstantSource.fixed(AT));
        var mapper = new ActivityEventMapper(ActivityEventMapping.of(
                JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events")), USAGE);
        var pump = new WorkerActivityPump(mapper, recorder);
        var log = directory.resolve("worker.log");
        var counts = new ArrayList<Integer>();
        try (var harness = FixtureHarness.start(directory, arguments(stream))) {
            for (var index = 0; index < stream.size(); index++) {
                append(log, awaitLine(harness));
                pump.pump(TASK, GENERATION, new JobLog(log));
                counts.add(protocol.reports.size());
            }
        }
        return new Observed(List.copyOf(counts), List.copyOf(protocol.reports), List.copyOf(protocol.fences),
                List.copyOf(protocol.closedUnavailable));
    }

    private static String awaitLine(FixtureHarness.Handle harness) {
        try {
            return harness.awaitLine(AWAIT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("The fixture harness stream was interrupted", e);
        }
    }

    private static RoleArtifact role(RoleArtifact.OffTrackSignal signal, RoleArtifact.OffTrackAction action) {
        return OffTrackDetectorTest.role(Map.of(signal, new RoleArtifact.OffTrackSetting(null, action)));
    }

    private static List<String> repeats(int count) {
        return Collections.nCopies(count, LOOPING_READ);
    }

    private static List<String> turns(int count, int inputTokens, int outputTokens) {
        return Collections.nCopies(count, "{\"type\":\"result\",\"stop_reason\":\"end_turn\",\"usage\":"
                + "{\"input_tokens\":%d,\"output_tokens\":%d}}".formatted(inputTokens, outputTokens));
    }

    private void assertNothingEndedThePlanOrWaitedForTheOperator(Observed observed) {
        assertAll(
                () -> assertTrue(observed.fences().isEmpty(), "an off-track signal never fences the generation"),
                () -> assertTrue(observed.closedUnavailable().isEmpty(),
                        "an off-track signal closes no task and raises no operator wait"));
    }

    @Test
    @DisplayName("repeating is raised at its threshold and never one call before it")
    void repeatingAtItsThreshold() throws IOException {
        var role = role(RoleArtifact.OffTrackSignal.REPEATING, RoleArtifact.OffTrackAction.SHOW);

        var observed = replay(repeats(5), role, null, -1);

        assertAll(
                () -> assertEquals(0, observed.reportsAfterEachLine().get(3), "four calls are below the threshold"),
                () -> assertEquals(1, observed.reportsAfterEachLine().get(4), "the fifth call raises the signal"),
                () -> assertEquals(1, observed.reports().size(), "one cause takes its action once"),
                () -> assertEquals("repeating", observed.reports().getFirst().signal()),
                () -> assertEquals(5.0, observed.reports().getFirst().measurement()),
                () -> assertEquals(5.0, observed.reports().getFirst().threshold()),
                () -> assertNothingEndedThePlanOrWaitedForTheOperator(observed));
    }

    @Test
    @DisplayName("no_progress is raised at its threshold and never one turn before it")
    void noProgressAtItsThreshold() throws IOException {
        var role = role(RoleArtifact.OffTrackSignal.NO_PROGRESS, RoleArtifact.OffTrackAction.SHOW);

        var observed = replay(turns(40, 1, 1), role, null, -1);

        assertAll(
                () -> assertEquals(0, observed.reportsAfterEachLine().get(38), "39 turns are below the threshold"),
                () -> assertEquals(1, observed.reportsAfterEachLine().get(39), "the 40th turn raises the signal"),
                () -> assertEquals("no_progress", observed.reports().getFirst().signal()),
                () -> assertEquals(40.0, observed.reports().getFirst().measurement()),
                () -> assertNothingEndedThePlanOrWaitedForTheOperator(observed));
    }

    @Test
    @DisplayName("over_size is raised at its threshold and never one turn before it")
    void overSizeAtItsThreshold() throws IOException {
        var role = role(RoleArtifact.OffTrackSignal.OVER_SIZE, RoleArtifact.OffTrackAction.SHOW);

        var observed = replay(turns(6, 40, 10), role, 100.0, -1);

        assertAll(
                () -> assertEquals(0, observed.reportsAfterEachLine().get(4), "250 tokens of 100 is below 3 times"),
                () -> assertEquals(1, observed.reportsAfterEachLine().get(5), "300 tokens of 100 is 3 times"),
                () -> assertEquals("over_size", observed.reports().getFirst().signal()),
                () -> assertEquals(3.0, observed.reports().getFirst().measurement()),
                () -> assertEquals(3.0, observed.reports().getFirst().threshold()),
                () -> assertNothingEndedThePlanOrWaitedForTheOperator(observed));
    }

    @Test
    @DisplayName("outside_footprint is raised at its threshold and never one file before it")
    void outsideFootprintAtItsThreshold() throws IOException {
        var role = role(RoleArtifact.OffTrackSignal.OUTSIDE_FOOTPRINT, RoleArtifact.OffTrackAction.SHOW);
        // outside_footprint is measured on the task's recorded unplanned changes, not on the journal: the stream is the
        // worker's output, and the recorded footprint is the other half of the task's facts.
        var stream = List.of(TURN, "{\"type\":\"file\",\"path\":\"outside/A.java\"}");

        var below = replay(stream, role, 100.0, 2);
        var at = replay(stream, role, 100.0, 3);

        assertAll(
                () -> assertTrue(below.reports().isEmpty(), "two unplanned files are below the threshold"),
                () -> assertEquals(1, at.reports().size(), "three unplanned files raise the signal"),
                () -> assertEquals("outside_footprint", at.reports().getFirst().signal()),
                () -> assertEquals(3.0, at.reports().getFirst().measurement()),
                () -> assertNothingEndedThePlanOrWaitedForTheOperator(at));
    }

    @Test
    @DisplayName("every action is recorded with its signal and takes no ending and no operator wait")
    void everyActionIsRecordedWithItsSignal() throws IOException {
        var byAction = new LinkedHashMap<RoleArtifact.OffTrackAction, Observed>();
        for (var action : RoleArtifact.OffTrackAction.values()) {
            var role = role(RoleArtifact.OffTrackSignal.REPEATING, action);
            byAction.put(action, replay(repeats(5), role, null, -1));
        }

        var assertions = new ArrayList<Executable>();
        for (var entry : byAction.entrySet()) {
            assertions.add(() -> {
                var observed = entry.getValue();
                var reports = observed.reports();
                assertEquals(1, reports.size(), "one report for " + entry.getKey());
                assertEquals(entry.getKey().wireName(), reports.getFirst().action());
                assertEquals("repeating", reports.getFirst().signal());
                assertEquals(5.0, reports.getFirst().measurement());
                assertEquals(5.0, reports.getFirst().threshold());
                assertTrue(observed.fences().isEmpty(), "no action fences the generation on the runtime's side");
                assertTrue(observed.closedUnavailable().isEmpty(), "no action closes a task or raises an operator wait");
            });
        }

        assertAll(assertions);
    }

    @Test
    @DisplayName("each scenario replayed twice reports the same signals and actions in the same order")
    void everyScenarioReplaysTwiceToTheSameReports() throws IOException {
        var scenarios = List.<Scenario>of(
                new Scenario(repeats(5), RoleArtifact.OffTrackSignal.REPEATING, RoleArtifact.OffTrackAction.SHOW,
                        null, -1),
                new Scenario(turns(40, 1, 1), RoleArtifact.OffTrackSignal.NO_PROGRESS,
                        RoleArtifact.OffTrackAction.NUDGE, null, -1),
                new Scenario(turns(6, 40, 10), RoleArtifact.OffTrackSignal.OVER_SIZE,
                        RoleArtifact.OffTrackAction.YIELD, 100.0, -1),
                new Scenario(List.of(TURN, "{\"type\":\"file\",\"path\":\"outside/A.java\"}"),
                        RoleArtifact.OffTrackSignal.OUTSIDE_FOOTPRINT, RoleArtifact.OffTrackAction.RESTART, 100.0,
                        3));

        var assertions = new ArrayList<Executable>();
        for (var scenario : scenarios) {
            var role = role(scenario.signal(), scenario.action());
            var once = replay(scenario.stream(), role, scenario.predictedCost(), scenario.unplannedChanges());
            var twice = replay(scenario.stream(), role, scenario.predictedCost(), scenario.unplannedChanges());
            assertions.add(() -> {
                assertEquals(once.reportsAfterEachLine(), twice.reportsAfterEachLine(),
                        "the same signal on the same entry for " + scenario.signal());
                assertEquals(once.reports(), twice.reports(), "the same action for " + scenario.signal());
            });
        }

        assertAll(assertions);
    }

    /** One off-track scenario: its scripted stream, its signal and action, and the task's recorded facts. */
    private record Scenario(List<String> stream, RoleArtifact.OffTrackSignal signal,
            RoleArtifact.OffTrackAction action, Double predictedCost, int unplannedChanges) {
    }

    @Test
    @DisplayName("a signal is reported once per attempt, however many entries follow it")
    void oneReportPerAttempt() throws IOException {
        var role = role(RoleArtifact.OffTrackSignal.REPEATING, RoleArtifact.OffTrackAction.NUDGE);

        var observed = replay(repeats(8), role, null, -1);

        assertAll(
                () -> assertEquals(1, observed.reports().size(), "one cause takes its action once"),
                () -> assertEquals("nudge", observed.reports().getFirst().action()),
                () -> assertEquals(1, observed.reportsAfterEachLine().getLast()));
    }

    /** The task protocol boundary, which records what the runtime reports and every call that ends or closes a task. */
    private static final class RecordingProtocol implements WorkerProtocol {

        private final List<Reported> reports = new ArrayList<>();

        private final List<String> fences = new ArrayList<>();

        private final List<String> closedUnavailable = new ArrayList<>();

        @Override
        public void fence(String generationId) {
            fences.add(generationId);
        }

        @Override
        public void agentStarted(String generationId, String queueTaskId, int attempt, String executable, Instant at) {
            throw new UnsupportedOperationException("an activity signal never reports an agent start");
        }

        @Override
        public void offTrack(OffTrackReport report) {
            reports.add(new Reported(report.signal(), report.action(), report.measurement(), report.threshold()));
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            closedUnavailable.add(role + "/" + harness);
        }
    }
}
