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

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityEventMapping;
import de.planmarshall.core.queue.ActivityJournal;
import de.planmarshall.core.queue.QueueTaskRecord;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * One step of the supervision (PM-WORK-3, PM-WORK-7): a pass over the supervisor's registry, the job records and the job
 * logs.
 * <p>
 * The clock is the fixture's virtual clock and the journal is the landed one, so a scenario asserts what the pass wrote
 * and never waits: the log is appended between two passes and the entries are read back. The process tree of the
 * {@code agent_started} scenario is the real one — the test starts a child and the detector reads what the operating
 * system answers for it.
 * <p>
 * The generation, the task and the project are generated; the event mapping is the wire contract of a harness profile,
 * and the enrolment's executable is the operating system's own {@code sleep}.
 */
@EnableGeneratorController
@DisplayName("Supervision pass")
class SupervisionPassTest {

    private static final Instant AT = Instant.parse("2026-10-26T10:00:00Z");

    /** An executable every Linux and macOS installation carries, so the enrolled match reads a real file. */
    private static final Path SLEEP = Path.of("/bin/sleep").toAbsolutePath();

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

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private FileLockManager locks;

    private SupervisorEnvironment environment;

    private WorkerSupervisor supervisor;

    private WorkerEnding ending;

    private WorkerRecycling recycling;

    private SupervisorCalls calls;

    private WorkerParameters parameters;

    private EffectiveRole role;

    private WorkerGeneration generation;

    private String task;

    private WorkerTaskActivity tasks;

    private final Set<String> openTasks = new HashSet<>();

    private JobRecordStore jobs;

    private Process child;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        locks = new FileLockManager(_ -> {
        });
        environment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        ending = new WorkerEnding(environment, () -> false, () -> {
        });
        supervisor = new WorkerSupervisor(environment);
        parameters = new WorkerParameters(true, OptionalInt.of(270), false, 600, 3, 300, false);
        recycling = new WorkerRecycling(environment, ending, supervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), parameters);
        calls = new SupervisorCalls(supervisor, ending, recycling, parameters, _ -> RoleClass.WRITER);
        role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        generation = started(supervisor);
        task = RuntimeGenerators.queueTaskIds().next();
        openTasks.add(task);
        tasks = tasks();
        jobs = JobRecordStore.ofProject(fixture.base, fixture.project);
    }

    @AfterEach
    void tearDown() {
        if (child != null) {
            child.destroyForcibly();
        }
    }

    private WorkerGeneration started(WorkerSupervisor over) {
        var outcomes = over.start(List.of(fixture.warmRequest(role, fixture.workdir)));
        return ((WorkerSupervisor.StartOutcome.Started) outcomes.getFirst()).generation();
    }

    /** @return the {@code WorkerTaskActivity} double: the facts of the one task while the test leaves it open */
    private WorkerTaskActivity tasks() {
        WorkerTaskActivity mock = createMock(WorkerTaskActivity.class);
        expect(mock.facts(anyString())).andAnswer(() -> {
            var asked = (String) getCurrentArguments()[0];
            return openTasks.contains(asked)
                    ? Optional.of(new WorkerTaskActivity.TaskActivity(1, role.artifact(), Set.of(), null, -1))
                    : Optional.empty();
        }).anyTimes();
        replay(mock);
        return mock;
    }

    private ActivityJournal journal(String name) throws IOException {
        return ActivityJournal.ofScope(Files.createDirectories(base.resolve(name)), fixture.project,
                new QueueTaskRecord.Scope(QueueTaskRecord.ScopeType.PLAN, fixture.plan), locks);
    }

    private Function<WorkerGeneration, WorkerActivityPump> pumps(ActivityJournal journal) {
        var mapping = ActivityEventMapping.of(JsonDocument.parse(EVENTS.getBytes(StandardCharsets.UTF_8), "events"))
                .orElseThrow();
        var mapper = new ActivityEventMapper(mapping, Map.of());
        var recorder = new ActivityRecorder(journal, tasks, fixture.protocol, new OffTrackDetector(),
                InstantSource.fixed(AT));
        return _ -> new WorkerActivityPump(mapper, recorder);
    }

    private AgentStartedDetector silentDetector() {
        return new AgentStartedDetector(environment, _ -> new ProcessExecutables.Absent(), List::of, ending);
    }

    private SupervisionPass pass(ActivityJournal journal) {
        return new SupervisionPass(supervisor, calls, ending, recycling, tasks, silentDetector(), pumps(journal));
    }

    private JobRecord running(@Nullable Long pid) {
        var builder = JobRecord.builder().jobId(generation.jobId()).execution(JobRecord.Execution.PROCESS)
                .scope(new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null)).worker("{}")
                .egress(JobRecord.Egress.UNENFORCED)
                .gitMetadata(JobRecord.GitMetadata.UNENFORCED)
                .budgetKey("worker:implementer").budgetSeconds(60)
                .budgetDerivation(new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                .status(JobRecord.Status.RUNNING).confinement(JobRecord.Confinement.UNAVAILABLE)
                .createdAt(SupervisorTestFixture.NOW).clampedValues(List.of());
        return pid == null ? builder.build()
                : builder.pid(pid).processStartedAt(SupervisorTestFixture.NOW)
                .spawnedAt(SupervisorTestFixture.NOW).build();
    }

    private void writeRunning(@Nullable Long pid) {
        try (var transaction = locks.openTransaction()) {
            jobs.writeAhead(transaction, running(null));
        }
        if (pid != null) {
            try (var transaction = locks.openTransaction()) {
                jobs.writePostSpawn(transaction, running(pid));
            }
        }
    }

    private void settle(long pid) {
        try (var transaction = locks.openTransaction()) {
            jobs.writeFate(transaction, running(pid).toBuilder().status(JobRecord.Status.ENDED)
                    .fate(JobRecord.Fate.SUCCEEDED).outcomeRow(2)
                    .endedAt(SupervisorTestFixture.NOW.plusSeconds(30)).childWallMs(30_000L).exitCode(0L).build());
        }
    }

    private void append(String line) {
        try {
            Files.writeString(jobs.logPath(generation.jobId()), line + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("The fixture log cannot be written", e);
        }
    }

    private static String text(String content) {
        return "{\"type\":\"assistant\",\"message\":{\"content\":\"" + content + "\"}}";
    }

    private void lease() {
        calls.waitOpened(generation.generationId(), task, generation.configurationDigest());
    }

    @Test
    @DisplayName("a pass pumps the lines a log gained exactly once, however many passes follow")
    void aPassPumpsEachLineOnce() throws Exception {
        writeRunning(null);
        lease();
        var journal = journal("pumped");
        var pass = pass(journal);
        append(text("first"));
        append(text("second"));

        pass.run();
        var afterFirst = journal.entries(task).size();
        append(text("third"));
        pass.run();
        pass.run();
        var entries = journal.entries(task);

        assertAll(
                () -> assertEquals(2, afterFirst, "the first pass appends the two lines the log holds"),
                () -> assertEquals(3, entries.size(), "three passes append each gained line once"),
                () -> assertEquals(List.of("first", "second", "third"),
                        entries.stream().map(ActivityEntry::text).toList(),
                        "the journal holds the three lines in order"));
    }

    @Test
    @DisplayName("a second pass over the same stores yields the same entries")
    void aSecondPassYieldsTheSameEntries() throws Exception {
        writeRunning(null);
        lease();
        append(text("first"));
        append(text("second"));
        append(text("third"));
        var first = journal("replay-first");
        var second = journal("replay-second");

        pass(first).run();
        pass(second).run();

        assertAll(
                () -> assertFalse(first.entries(task).isEmpty(), "the replay appended the mapped lines"),
                () -> assertEquals(first.entries(task), second.entries(task), "the replay appends the same entries"));
    }

    @Test
    @DisplayName("an exit after the first call is a loss and the end path runs once")
    void anExitAfterTheFirstCallIsALoss() throws Exception {
        var pid = ProcessHandle.current().pid();
        writeRunning(pid);
        lease();
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        var journal = journal("exit");
        var pass = pass(journal);
        pass.run();
        settle(pid);

        pass.run();
        var terminal = supervisor.generation(generation.generationId()).orElseThrow();
        pass.run();
        var entries = journal.entries(task);

        assertAll(
                () -> assertEquals(WorkerLifecycle.LOST, terminal.lifecycle(), "the exited worker is lost"),
                () -> assertEquals(1, fixture.writes.size(), "the end path writes one terminal state"),
                () -> assertEquals(1, fixture.fences.size(), "the end path fences the generation once"),
                () -> assertEquals(1, entries.size(), "the attempt's end is appended once"),
                () -> assertEquals(ActivityEntry.Kind.ATTEMPT_END, entries.getFirst().kind(),
                        "the appended entry is the attempt's end"),
                () -> assertEquals("exited_without_submit", entries.getFirst().end(),
                        "the exit ends the attempt without a submit"));
    }

    @Test
    @DisplayName("a pass over a task whose facts are gone appends nothing and does not throw")
    void aPassOverAClosedTaskAppendsNothing() throws Exception {
        writeRunning(null);
        lease();
        var journal = journal("closed");
        var pass = pass(journal);
        append(text("first"));
        openTasks.remove(task);

        assertDoesNotThrow(pass::run, "a closed task does not fail the pass");

        assertTrue(journal.entries(task).isEmpty(), "no entry is appended for the closed task");
    }

    @Test
    @DisplayName("the agent_started signal ends the worker once")
    void theAgentStartedSignalEndsTheWorkerOnce() throws Exception {
        child = new ProcessBuilder(SLEEP.toRealPath().toString(), "30").start();
        var executable = SLEEP.toRealPath();
        writeRunning(ProcessHandle.current().pid());
        lease();
        var detector = new AgentStartedDetector(environment, ProcessExecutables.system(),
                () -> List.of(enrolment(executable)), ending);
        var pass = new SupervisionPass(supervisor, calls, ending, recycling, tasks, detector,
                pumps(journal("agent")));

        pass.run();
        pass.run();

        assertAll(
                () -> assertEquals(1, fixture.startedAgents.size(), "one started agent raises one signal"),
                () -> assertEquals(Optional.of(EndCause.OFF_TRACK),
                        ending.requestedCause(generation.generationId()), "the signal ends the worker off track"),
                () -> assertNotNull(supervisor.generation(generation.generationId()).orElseThrow().leasedTask(),
                        "the end is requested, not yet completed"));
    }

    @Test
    @DisplayName("a pass over a supervisor with no generation writes nothing")
    void aPassWithoutGenerationsWritesNothing() throws Exception {
        var journal = journal("empty");
        var idle = new WorkerSupervisor(environment);
        var idleEnding = new WorkerEnding(environment, () -> false, () -> {
        });
        var idleRecycling = new WorkerRecycling(environment, idleEnding, idle::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), parameters);
        var idleCalls = new SupervisorCalls(idle, idleEnding, idleRecycling, parameters, _ -> RoleClass.WRITER);
        var pass = new SupervisionPass(idle, idleCalls, idleEnding, idleRecycling, tasks, silentDetector(),
                pumps(journal));

        pass.run();

        assertAll(
                () -> assertTrue(idle.generations().isEmpty(), "the supervisor holds no generation"),
                () -> assertTrue(fixture.writes.isEmpty(), "the pass writes no terminal state"),
                () -> assertTrue(fixture.fences.isEmpty(), "the pass fences no generation"),
                () -> assertTrue(journal.entries(task).isEmpty(), "the pass appends no activity entry"));
    }

    @Test
    @DisplayName("a pass over a terminal generation writes nothing")
    void aPassOverATerminalGenerationWritesNothing() throws Exception {
        var pid = ProcessHandle.current().pid();
        writeRunning(pid);
        lease();
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        var journal = journal("terminal");
        var pass = pass(journal);
        pass.run();
        settle(pid);
        pass.run();
        var writes = fixture.writes.size();
        var entries = journal.entries(task).size();

        pass.run();

        assertAll(
                () -> assertTrue(supervisor.generation(generation.generationId()).orElseThrow().terminal(),
                        "the generation is terminal after its end"),
                () -> assertEquals(writes, fixture.writes.size(), "a pass over it writes no further state"),
                () -> assertEquals(entries, journal.entries(task).size(), "a pass over it appends no entry"),
                () -> assertFalse(journal.entries(task).isEmpty(), "the attempt's end was appended before"));
    }

    @Test
    @DisplayName("every end cause maps onto the attempt end the journal records")
    void everyEndCauseMapsOntoItsAttemptEnd() {
        assertAll(
                () -> assertEquals(QueueTaskRecord.AttemptEnd.CLOSED,
                        SupervisionPass.attemptEnd(EndCause.TASK_DONE, null), "a served task closes its attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.CLOSED,
                        SupervisionPass.attemptEnd(EndCause.RECYCLE, null), "a recycled worker closes its attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.DRAIN,
                        SupervisionPass.attemptEnd(EndCause.DRAIN, null), "a drain marks the attempt drained"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.PAUSED,
                        SupervisionPass.attemptEnd(EndCause.PAUSE_AT_ONCE, null), "a pause marks the attempt paused"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.OFF_TRACK,
                        SupervisionPass.attemptEnd(EndCause.OFF_TRACK, null), "an off-track end marks the attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.START_FAILURE,
                        SupervisionPass.attemptEnd(EndCause.START_FAILURE, null), "a start failure marks the attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(EndCause.LOST, null), "a loss without a signal is a lost attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.EXITED_WITHOUT_SUBMIT,
                        SupervisionPass.attemptEnd(EndCause.LOST, signal(LossReason.EXIT)),
                        "an exit loss ends the attempt without its submit"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(EndCause.LOST, signal(LossReason.ACK_MISSED)),
                        "a missed acknowledgement is a lost attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(EndCause.LOST, signal(LossReason.SILENCE)),
                        "silence is a lost attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(null, null), "no requested end is the lost attempt"));
    }

    private static LossSignal signal(LossReason reason) {
        return LossSignal.builder().reason(reason).generationId("l-0123456789abcdef")
                .measuredAt(SupervisorTestFixture.NOW).deadline(SupervisorTestFixture.NOW).firstCallMade(true)
                .leasedTask(null).build();
    }

    private static HarnessEnrolment enrolment(Path executable) {
        return new HarnessEnrolment(SupervisorTestFixture.CLIENT, executable,
                HarnessVerifier.digestExecutable(executable).orElseThrow(), "1.5.0", SupervisorTestFixture.NOW,
                SupervisorTestFixture.NOW,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(),
                Map.of());
    }
}
