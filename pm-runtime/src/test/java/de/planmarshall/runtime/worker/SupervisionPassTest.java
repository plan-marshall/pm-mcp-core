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

import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.InstantSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

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
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.core.store.WorkerStateCodec;
import de.planmarshall.runtime.job.SettlementStep;
import de.planmarshall.runtime.job.WorkerTermination;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.lock.FileLockManager;
import de.planmarshall.runtime.log.RuntimeLogMessages;
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
@EnableTestLogger
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

    private final Map<String, Integer> openTasks = new HashMap<>();

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
        parameters = new WorkerParameters(true, 270, false, 600, 3, 300, false);
        recycling = new WorkerRecycling(environment, ending, supervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        calls = new SupervisorCalls(supervisor, ending, recycling, parameters, _ -> RoleClass.WRITER);
        role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 1);
        generation = started(supervisor);
        task = RuntimeGenerators.queueTaskIds().next();
        openTasks.put(task, 1);
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

    /** @return the {@code WorkerTaskActivity} double: the facts of the open tasks, by the attempt the test records */
    private WorkerTaskActivity tasks() {
        WorkerTaskActivity mock = createMock(WorkerTaskActivity.class);
        expect(mock.facts(anyString())).andAnswer(() -> {
            var asked = (String) getCurrentArguments()[0];
            var attempt = openTasks.get(asked);
            return attempt == null ? Optional.<WorkerTaskActivity.TaskActivity>empty()
                    : Optional.of(new WorkerTaskActivity.TaskActivity(attempt, role.artifact(), Set.of(), null, -1));
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

    private WorkerState workerState(WorkerGeneration gen) {
        return WorkerState.builder()
                .role(gen.role())
                .configurationDigest(gen.configurationDigest())
                .roleClass(WorkerState.RoleClass.WRITER)
                .form(WorkerState.Form.WARM)
                .harness(gen.harness())
                .harnessVersion("1.5.0")
                .model(gen.model())
                .generationId(gen.jobId())
                .workdir(new WorkerState.Workdir(WorkerState.Workdir.Kind.PROJECT_CHECKOUT, null))
                .promptDigest(fixture.sha)
                .jobTokenSha256(fixture.sha)
                .connectedAt(SupervisorTestFixture.NOW)
                .state(WorkerState.State.EXECUTING)
                .stateAt(SupervisorTestFixture.NOW)
                .servedTasks(List.of())
                .idleWakeups(0L)
                .contextTokens(WorkerState.ContextTokens.NOT_MEASURED)
                .deliveredSkills(List.of())
                .startFailure(false)
                .build();
    }

    private JobRecord running(WorkerGeneration gen) {
        return running(gen, workerState(gen));
    }

    private JobRecord running(WorkerGeneration gen, WorkerState worker) {
        return JobRecord.builder().jobId(gen.jobId()).execution(JobRecord.Execution.PROCESS)
                .scope(new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null))
                .worker(WorkerStateCodec.write(worker))
                .egress(JobRecord.Egress.UNENFORCED)
                .gitMetadata(JobRecord.GitMetadata.UNENFORCED)
                .budgetKey("worker:implementer").budgetSeconds(60)
                .budgetDerivation(new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null))
                .status(JobRecord.Status.RUNNING).confinement(JobRecord.Confinement.UNAVAILABLE)
                .createdAt(SupervisorTestFixture.NOW).clampedValues(List.of())
                .build();
    }

    private void writeRunning(WorkerGeneration gen) {
        try (var transaction = locks.openTransaction()) {
            jobs.writeAhead(transaction, running(gen));
        }
    }

    /** Settles the job the way the runner does: the fate and the terminal worker block in one record. */
    private void settle(WorkerGeneration gen) {
        settle(gen, JobRecord.Fate.SUCCEEDED, SupervisorTestFixture.NOW.plusSeconds(30));
    }

    /** Settles the job with the given fate, as the runner does, with the terminal worker block in the same record. */
    private void settle(WorkerGeneration gen, JobRecord.Fate fate, Instant endedAt) {
        try (var transaction = locks.openTransaction()) {
            var settled = settledRecord(gen, fate, endedAt);
            var current = WorkerStateCodec.read(jobs.recordPath(gen.jobId()), settled.worker());
            jobs.writeFate(transaction,
                    settled.withWorker(WorkerStateCodec.write(WorkerTermination.terminal(current, settled))));
        }
    }

    /** @return the settled record the runner hands to its settlement, with the worker block of the running record */
    private JobRecord settledRecord(WorkerGeneration gen, JobRecord.Fate fate, Instant endedAt) {
        return running(gen).toBuilder().status(JobRecord.Status.ENDED).fate(fate).outcomeRow(2)
                .spawnedAt(SupervisorTestFixture.NOW).endedAt(endedAt).childWallMs(30_000L).exitCode(0L).build();
    }

    /** Settles the job through the runner's own fate step, which derives the terminal block from the stored one. */
    private SettlementStep.Outcome settleThroughTheFateStep(WorkerGeneration gen, JobRecord.Fate fate) {
        return SettlementStep.fate(jobs, locks).run(new SettlementStep.Settled(
                settledRecord(gen, fate, SupervisorTestFixture.NOW.plusSeconds(30)), null));
    }

    /**
     * Settles the job of a generation whose worker exited before its first call, the way the runner does: the running
     * record carries a worker block without a connection, and the runner's fate step derives the terminal block — a
     * start failure — from it.
     * <p>
     * A scenario settles the generation that already holds the harness's start turn. A second start on the same
     * harness would wait the whole {@link HarnessStartSpacing#SPACING_BOUND} for a worker that neither called nor ended,
     * so the helper settles the turn's own generation and starts nothing.
     */
    private SettlementStep.Outcome settleAsExitBeforeTheFirstCall(WorkerGeneration gen) {
        try (var transaction = locks.openTransaction()) {
            jobs.writeAhead(transaction, running(gen, workerState(gen).toBuilder().connectedAt(null).build()));
        }
        return settleThroughTheFateStep(gen, JobRecord.Fate.FAILED);
    }

    /** Settles the job with a worker block the codec rejects: the settlement leaves such a block as it is. */
    private void settleWithUnreadableWorkerBlock(WorkerGeneration gen) {
        try (var transaction = locks.openTransaction()) {
            jobs.writeFate(transaction, running(gen).toBuilder().status(JobRecord.Status.ENDED)
                    .fate(JobRecord.Fate.SUCCEEDED).outcomeRow(2).spawnedAt(SupervisorTestFixture.NOW)
                    .endedAt(SupervisorTestFixture.NOW.plusSeconds(30)).childWallMs(30_000L).exitCode(0L)
                    .worker("{\"not\":\"a worker state\"}").build());
        }
    }

    /** Settles the job with the worker block of the running state: the end path's read finds no terminal state. */
    private void settleWithLiveWorkerBlock(WorkerGeneration gen) {
        try (var transaction = locks.openTransaction()) {
            jobs.writeFate(transaction, running(gen).toBuilder().status(JobRecord.Status.ENDED)
                    .fate(JobRecord.Fate.SUCCEEDED).outcomeRow(2).spawnedAt(SupervisorTestFixture.NOW)
                    .endedAt(SupervisorTestFixture.NOW.plusSeconds(30))
                    .childWallMs(30_000L).exitCode(0L).build());
        }
    }

    /** @return the worker block the settled record of the generation carries */
    private WorkerState recorded(WorkerGeneration gen) {
        return WorkerStateCodec.read(jobs.recordPath(gen.jobId()),
                jobs.read(gen.jobId()).orElseThrow().worker());
    }

    private void writeRunning(WorkerGeneration gen, @Nullable Long pid) {
        writeRunning(gen);
        if (pid != null) {
            try (var transaction = locks.openTransaction()) {
                jobs.writePostSpawn(transaction, running(gen).toBuilder().pid(pid)
                        .processStartedAt(SupervisorTestFixture.NOW).spawnedAt(SupervisorTestFixture.NOW).build());
            }
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
        writeRunning(generation);
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
        writeRunning(generation);
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
        writeRunning(generation, pid);
        lease();
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        var journal = journal("exit");
        var pass = pass(journal);
        pass.run();
        settle(generation);

        pass.run();
        var terminal = supervisor.generation(generation.generationId()).orElseThrow();
        pass.run();
        var entries = journal.entries(task);

        assertAll(
                () -> assertEquals(WorkerLifecycle.LOST, terminal.lifecycle(), "the exited worker is lost"),
                () -> assertTrue(fixture.writes.isEmpty(),
                        "the end path writes no worker state of a settled record"),
                () -> assertEquals(1, fixture.fences.size(), "the end path fences the generation once"),
                () -> assertTrue(recorded(generation).state().isTerminal(),
                        "the settled record carries the terminal worker block"),
                () -> assertEquals(1, entries.size(), "the attempt's end is appended once"),
                () -> assertEquals(ActivityEntry.Kind.ATTEMPT_END, entries.getFirst().kind(),
                        "the appended entry is the attempt's end"),
                () -> assertEquals("exited_without_submit", entries.getFirst().end(),
                        "the exit ends the attempt without a submit"));
    }

    @Test
    @DisplayName("a pass over a task whose facts are gone appends nothing and does not throw")
    void aPassOverAClosedTaskAppendsNothing() throws Exception {
        writeRunning(generation);
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
        writeRunning(generation, ProcessHandle.current().pid());
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
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
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
        writeRunning(generation, pid);
        lease();
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        var journal = journal("terminal");
        var pass = pass(journal);
        pass.run();
        settle(generation);
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
                        SupervisionPass.attemptEnd(EndCause.TASK_DONE, null, null),
                        "a served task closes its attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.CLOSED,
                        SupervisionPass.attemptEnd(EndCause.RECYCLE, null, null),
                        "a recycled worker closes its attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.DRAIN,
                        SupervisionPass.attemptEnd(EndCause.DRAIN, null, null), "a drain marks the attempt drained"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.PAUSED,
                        SupervisionPass.attemptEnd(EndCause.PAUSE_AT_ONCE, null, null),
                        "a pause marks the attempt paused"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.OFF_TRACK,
                        SupervisionPass.attemptEnd(EndCause.OFF_TRACK, null, null),
                        "an off-track end marks the attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.START_FAILURE,
                        SupervisionPass.attemptEnd(EndCause.START_FAILURE, null, null),
                        "a start failure marks the attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(EndCause.LOST, null, null),
                        "a loss without a signal is a lost attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.EXITED_WITHOUT_SUBMIT,
                        SupervisionPass.attemptEnd(EndCause.LOST, signal(LossReason.EXIT), null),
                        "an exit loss ends the attempt without its submit"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(EndCause.LOST, signal(LossReason.ACK_MISSED), null),
                        "a missed acknowledgement is a lost attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(EndCause.LOST, signal(LossReason.SILENCE), null),
                        "silence is a lost attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(null, null, null),
                        "no requested end and no recorded reason is the lost attempt"));
    }

    @Test
    @DisplayName("a worker that ended without a requested end is classified by the reason the record carries")
    void theRecordedReasonClassifiesAnUnrequestedEnd() {
        assertAll(
                () -> assertEquals(QueueTaskRecord.AttemptEnd.CLOSED,
                        SupervisionPass.attemptEnd(null, null, WorkerState.EndReason.TASK_DONE),
                        "a fresh worker that served its task closes its attempt"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.CLOSED,
                        SupervisionPass.attemptEnd(null, null, WorkerState.EndReason.TOKEN_BUDGET),
                        "a budget recycle closes the attempt of the served task"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.EXITED_WITHOUT_SUBMIT,
                        SupervisionPass.attemptEnd(null, null, WorkerState.EndReason.EXITED),
                        "an exit without a submit ends the attempt without one"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.BUDGET,
                        SupervisionPass.attemptEnd(null, null, WorkerState.EndReason.BUDGET),
                        "the worker budget ends the attempt by its budget"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.START_FAILURE,
                        SupervisionPass.attemptEnd(null, null, WorkerState.EndReason.START_FAILURE),
                        "a start failure marks the attempt as one"),
                () -> assertEquals(QueueTaskRecord.AttemptEnd.WORKER_LOST,
                        SupervisionPass.attemptEnd(null, null, WorkerState.EndReason.SILENCE),
                        "a silent worker's attempt is lost"));
    }

    @ParameterizedTest
    @EnumSource(LossReason.class)
    @DisplayName("an unrequested end a loss records takes the attempt end of that loss")
    void anUnrequestedLossEndTakesTheAttemptEndOfItsLoss(LossReason loss) {
        var attemptEnd = SupervisionPass.attemptEnd(null, null, loss.endReason());

        assertAll(
                () -> assertEquals(Optional.of(loss), LossReason.recording(loss.endReason()),
                        "the recorded end reason names its loss"),
                () -> assertEquals(loss.attemptEnd(), attemptEnd, "the loss decides the attempt's end"));
    }

    @ParameterizedTest
    @EnumSource(WorkerState.EndReason.class)
    @DisplayName("every recorded end reason of an unrequested end maps onto an attempt end")
    void everyRecordedEndReasonMapsOntoAnAttemptEnd(WorkerState.EndReason recorded) {
        var attemptEnd = assertDoesNotThrow(() -> SupervisionPass.attemptEnd(null, null, recorded),
                "the recorded end reason has an attempt end");

        assertEquals(LossReason.recording(recorded).map(LossReason::attemptEnd).orElse(attemptEnd), attemptEnd,
                "a loss end reason maps as its loss does");
    }

    @Test
    @DisplayName("a settled record ends through the project's real store writer and stays unchanged")
    void aSettledRecordEndsThroughTheRealStoreWriter() throws Exception {
        fixture.stateWriter = SupervisorEnvironment.WorkerStateWriter.over(fixture.base, fixture.project, locks);
        var realEnvironment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var realSupervisor = new WorkerSupervisor(realEnvironment);
        var realEnding = new WorkerEnding(realEnvironment, () -> false, () -> {
        });
        var realRecycling = new WorkerRecycling(realEnvironment, realEnding, realSupervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        var realCalls = new SupervisorCalls(realSupervisor, realEnding, realRecycling, parameters,
                _ -> RoleClass.WRITER);
        var started = ((WorkerSupervisor.StartOutcome.Started) realSupervisor
                .start(List.of(fixture.warmRequest(role, fixture.workdir))).getFirst()).generation();
        var leased = RuntimeGenerators.queueTaskIds().next();
        openTasks.put(leased, 1);
        writeRunning(started);
        realCalls.waitOpened(started.generationId(), leased, started.configurationDigest());
        settle(started);
        var before = recorded(started);
        var realPass = new SupervisionPass(realSupervisor, realCalls, realEnding, realRecycling, tasks,
                new AgentStartedDetector(realEnvironment, _ -> new ProcessExecutables.Absent(), List::of, realEnding),
                pumps(journal("real-writer")));

        realPass.run();

        assertAll(
                () -> assertTrue(realSupervisor.generation(started.generationId()).orElseThrow().terminal(),
                        "the pass completes the end of the settled record"),
                () -> assertEquals(before, recorded(started),
                        "the pass changes no member of the settled record"),
                () -> assertEquals(1, fixture.fences.size(), "the end path fences the generation once"),
                () -> assertTrue(fixture.jobTokens.revoked(started.generationId()),
                        "the end path revokes the job token once"),
                () -> assertEquals(1, fixture.released.stream()
                                .filter(lease -> lease.generationId().equals(started.generationId())).count(),
                        "the end path releases the model slot once"));
    }

    @Test
    @DisplayName("a worker that never calls is lost at the deadline of silent from spawn")
    void aWorkerThatNeverCallsIsLostAtItsSilenceDeadline() throws Exception {
        writeRunning(generation);
        var pass = pass(journal("silent"));

        pass.run();
        fixture.advance(Duration.ofSeconds(901));
        pass.run();

        assertAll(
                () -> assertEquals(Optional.of(EndCause.LOST),
                        ending.requestedCause(generation.generationId()),
                        "the silence deadline ends the generation as lost"),
                () -> assertFalse(calls.detection(generation).firstCallMade(),
                        "the worker made no call before its deadline"),
                () -> assertEquals(1, fixture.cancels.size(), "the runner is asked to cancel the worker once"));
    }

    @Test
    @DisplayName("a failed end is reported and its retry keeps the cause and repeats nothing")
    void aFailedEndIsReportedAndItsRetryKeepsItsCause() throws Exception {
        var firstDirectory = Files.createDirectories(base.resolve("first-worktree"));
        var otherDirectory = Files.createDirectories(base.resolve("second-worktree"));
        var failed = new AtomicBoolean();
        var ownEnvironment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var ownSupervisor = new WorkerSupervisor(ownEnvironment);
        // The end fails once after its effects: the successor answer is what a store failure of an end reaches last,
        // so the retry has to keep the claim and repeat nothing.
        var ownEnding = new WorkerEnding(ownEnvironment, () -> {
            if (!failed.getAndSet(true)) {
                throw new UncheckedIOException("the end's store is unavailable", new IOException("unavailable"));
            }
            return false;
        }, () -> {
        });
        var ownRecycling = new WorkerRecycling(ownEnvironment, ownEnding, ownSupervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        var ownCalls = new SupervisorCalls(ownSupervisor, ownEnding, ownRecycling, parameters, _ -> RoleClass.WRITER);
        role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 3);
        var first = ((WorkerSupervisor.StartOutcome.Started) ownSupervisor
                .start(List.of(fixture.warmRequest(role, firstDirectory))).getFirst()).generation();
        var second = ((WorkerSupervisor.StartOutcome.Started) ownSupervisor
                .start(List.of(fixture.warmRequest(role, otherDirectory))).getFirst()).generation();
        var firstTask = RuntimeGenerators.queueTaskIds().next();
        openTasks.put(firstTask, 1);
        writeRunning(first);
        writeRunning(second);
        ownCalls.waitOpened(first.generationId(), firstTask, first.configurationDigest());
        ownEnding.end(first, EndCause.DRAIN);
        settleWithLiveWorkerBlock(first);
        settle(second);
        var journal = journal("failed-end");
        var ownPass = new SupervisionPass(ownSupervisor, ownCalls, ownEnding, ownRecycling, tasks,
                new AgentStartedDetector(ownEnvironment, _ -> new ProcessExecutables.Absent(), List::of, ownEnding),
                pumps(journal));

        ownPass.run();

        var firstAttempt = journal.entries(firstTask);
        assertAll(
                () -> assertTrue(ownSupervisor.generation(second.generationId()).orElseThrow().terminal(),
                        "the later generation ends in the same pass"),
                () -> assertFalse(ownSupervisor.generation(first.generationId()).orElseThrow().terminal(),
                        "the failed generation stays for the next pass"),
                () -> assertEquals(1, released(first), "the failed end releases the slot once"),
                () -> assertEquals(1, firstAttempt.size(), "the attempt's end is appended once"),
                () -> assertEquals("drain", firstAttempt.getFirst().end(),
                        "the attempt keeps the cause the supervisor requested"));

        ownPass.run();

        assertAll(
                () -> assertTrue(ownSupervisor.generation(first.generationId()).orElseThrow().terminal(),
                        "the retried end completes the generation"),
                () -> assertEquals(1, released(first), "the retry releases the slot no second time"),
                () -> assertEquals(1, fenced(first), "the retry fences the generation no second time"),
                () -> assertEquals(1, journal.entries(firstTask).size(), "the retry appends no second attempt end"),
                () -> assertEquals("drain", journal.entries(firstTask).getFirst().end(),
                        "the retried attempt keeps the DRAIN cause"));
    }

    @Test
    @DisplayName("a call is dispatched while another thread is inside the end path")
    void aCallIsDispatchedWhileTheEndRuns() throws Exception {
        var inside = new CountDownLatch(1);
        var gate = new CountDownLatch(1);
        fixture.protocol = blockingProtocol(inside, gate);
        var blockedEnvironment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var blockedSupervisor = new WorkerSupervisor(blockedEnvironment);
        var blockedEnding = new WorkerEnding(blockedEnvironment, () -> false, () -> {
        });
        var blockedRecycling = new WorkerRecycling(blockedEnvironment, blockedEnding, List::of,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        var blockedCalls = new SupervisorCalls(blockedSupervisor, blockedEnding, blockedRecycling, parameters,
                _ -> RoleClass.WRITER);
        var blocked = ((WorkerSupervisor.StartOutcome.Started) blockedSupervisor
                .start(List.of(fixture.warmRequest(role, fixture.workdir))).getFirst()).generation();
        writeRunning(blocked);
        settle(blocked);
        var blockedPass = new SupervisionPass(blockedSupervisor, blockedCalls, blockedEnding, blockedRecycling, tasks,
                new AgentStartedDetector(blockedEnvironment, _ -> new ProcessExecutables.Absent(), List::of,
                        blockedEnding),
                pumps(journal("blocked-end")));
        var runner = new Thread(blockedPass::run);

        runner.start();
        assertTrue(inside.await(5, TimeUnit.SECONDS), "the end path reached the fence");
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> blockedCalls.detection(blocked),
                "a call is dispatched while the end work runs");
        gate.countDown();
        runner.join(TimeUnit.SECONDS.toMillis(5));

        assertFalse(runner.isAlive(), "the end path ended after the fence returned");
    }

    @Test
    @DisplayName("a fresh worker that served its task ends without a loss")
    void aFreshWorkerThatServedItsTaskEndsWithoutALoss() throws Exception {
        role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        generation = ((WorkerSupervisor.StartOutcome.Started) supervisor
                .start(List.of(fixture.freshRequest(role))).getFirst()).generation();
        task = RuntimeGenerators.queueTaskIds().next();
        openTasks.put(task, 1);
        writeRunning(generation);
        calls.waitOpened(generation.generationId(), task, generation.configurationDigest());
        var journal = journal("fresh");
        var pass = pass(journal);
        append(text("done"));
        pass.run();
        calls.taskClosed(generation.generationId(), task);
        pass.run();
        settle(generation);

        pass.run();

        var entries = journal.entries(task);
        assertAll(
                () -> assertEquals(WorkerLifecycle.ENDED,
                        supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                        "the fresh worker ends instead of being lost"),
                () -> assertEquals("task_done", recorded(generation).endReason().wireName(),
                        "the settled record carries the task-done end"),
                () -> assertEquals(ActivityEntry.Kind.ATTEMPT_END, entries.getLast().kind(),
                        "the journal ends the attempt"),
                () -> assertEquals("closed", entries.getLast().end(),
                        "the served task closes its attempt without a loss"),
                () -> assertTrue(fixture.cancels.isEmpty(),
                        "no cancellation reaches the runner of the served worker"));
    }

    @Test
    @DisplayName("a deadline is dispatched while an end runs, and the ending generation fires none")
    void aDeadlineIsDispatchedWhileTheEndRuns() throws Exception {
        var inside = new CountDownLatch(1);
        var gate = new CountDownLatch(1);
        fixture.protocol = blockingProtocol(inside, gate);
        var blockedEnvironment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var blockedSupervisor = new WorkerSupervisor(blockedEnvironment);
        var blockedEnding = new WorkerEnding(blockedEnvironment, () -> false, () -> {
        });
        var blockedRecycling = new WorkerRecycling(blockedEnvironment, blockedEnding, blockedSupervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        var blockedCalls = new SupervisorCalls(blockedSupervisor, blockedEnding, blockedRecycling, parameters,
                _ -> RoleClass.WRITER);
        role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 2);
        var endingGeneration = ((WorkerSupervisor.StartOutcome.Started) blockedSupervisor
                .start(List.of(fixture.warmRequest(role, fixture.workdir))).getFirst()).generation();
        var silent = ((WorkerSupervisor.StartOutcome.Started) blockedSupervisor
                .start(List.of(fixture.warmRequest(role, Files.createDirectories(base.resolve("silent")))))
                .getFirst()).generation();
        writeRunning(endingGeneration);
        writeRunning(silent);
        settle(endingGeneration);
        // Both detections exist before the pass runs, so the clock move below meets the silent generation's deadline.
        blockedCalls.detection(silent);
        var blockedPass = new SupervisionPass(blockedSupervisor, blockedCalls, blockedEnding, blockedRecycling, tasks,
                new AgentStartedDetector(blockedEnvironment, _ -> new ProcessExecutables.Absent(), List::of,
                        blockedEnding),
                pumps(journal("blocked-deadline")));
        var runner = new Thread(blockedPass::run);

        runner.start();
        assertTrue(inside.await(5, TimeUnit.SECONDS), "the end path reached the fence");
        var cancelsBefore = fixture.cancels.size();
        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> fixture.advance(Duration.ofSeconds(901)),
                "a deadline is dispatched mid-end");
        gate.countDown();
        runner.join(TimeUnit.SECONDS.toMillis(5));

        assertAll(
                () -> assertEquals(cancelsBefore + 1, fixture.cancels.size(),
                        "only the silent generation's deadline forces a process"),
                () -> assertEquals(Optional.of(EndCause.LOST), blockedEnding.requestedCause(silent.generationId()),
                        "the deadline ends the silent generation as lost"),
                () -> assertFalse(runner.isAlive(), "the end path ended after the fence returned"));
    }

    @Test
    @DisplayName("an unrequested end that failed once keeps its cause absent on the retry")
    void anUnrequestedEndStaysUnrequestedOnItsRetry() throws Exception {
        var failing = new AtomicBoolean();
        failing.set(true);
        var ownEnding = new WorkerEnding(environment, () -> {
            if (failing.getAndSet(false)) {
                throw new IllegalStateException("the work queue cannot be read");
            }
            return false;
        }, () -> {
        });
        var ownRecycling = new WorkerRecycling(environment, ownEnding, supervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        var ownCalls = new SupervisorCalls(supervisor, ownEnding, ownRecycling, parameters, _ -> RoleClass.WRITER);
        writeRunning(generation);
        ownCalls.waitOpened(generation.generationId(), task, generation.configurationDigest());
        ownCalls.taskClosed(generation.generationId(), task);
        settle(generation);
        var ownPass = new SupervisionPass(supervisor, ownCalls, ownEnding, ownRecycling, tasks, silentDetector(),
                pumps(journal("retry-unrequested")));

        ownPass.run();
        ownPass.run();

        assertAll(
                () -> assertEquals(WorkerLifecycle.ENDED,
                        supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                        "the retried unrequested end keeps the record's end"),
                () -> assertTrue(fixture.writes.isEmpty(), "neither run writes a terminal state"),
                () -> assertEquals(1, fixture.fences.size(), "the retry fences the generation no second time"));
    }

    @Test
    @DisplayName("a settled record whose worker block is unreadable still completes its end")
    void anUnreadableWorkerBlockDoesNotBlockTheEnd() throws Exception {
        fixture.stateWriter = SupervisorEnvironment.WorkerStateWriter.over(fixture.base, fixture.project, locks);
        var realEnvironment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var realSupervisor = new WorkerSupervisor(realEnvironment);
        var realEnding = new WorkerEnding(realEnvironment, () -> false, () -> {
        });
        var realRecycling = new WorkerRecycling(realEnvironment, realEnding, realSupervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        var realCalls = new SupervisorCalls(realSupervisor, realEnding, realRecycling, parameters,
                _ -> RoleClass.WRITER);
        var started = ((WorkerSupervisor.StartOutcome.Started) realSupervisor
                .start(List.of(fixture.warmRequest(role, fixture.workdir))).getFirst()).generation();
        writeRunning(started);
        settleWithUnreadableWorkerBlock(started);
        var before = jobs.read(started.jobId()).orElseThrow();
        var realPass = new SupervisionPass(realSupervisor, realCalls, realEnding, realRecycling, tasks,
                new AgentStartedDetector(realEnvironment, _ -> new ProcessExecutables.Absent(), List::of, realEnding),
                pumps(journal("unreadable-block")));

        realPass.run();

        assertAll(
                () -> assertTrue(realSupervisor.generation(started.generationId()).orElseThrow().terminal(),
                        "the pass completes the end of the unreadable record"),
                () -> assertEquals(1, fixture.fences.size(), "the end path fences the generation once"),
                () -> assertTrue(fixture.jobTokens.revoked(started.generationId()),
                        "the end path revokes the job token"),
                () -> assertEquals(1, released(started), "the end path releases the model slot once"),
                () -> assertEquals(before, jobs.read(started.jobId()).orElseThrow(),
                        "the pass changes no member of the settled record"));
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                RuntimeLogMessages.WARN.WORKER_TERMINAL_STATE_UNREADABLE.resolveIdentifierString());
    }

    @Test
    @DisplayName("each attempt of a warm worker keeps its own lines and its own end")
    void eachAttemptKeepsItsOwnLinesAndEnd() throws Exception {
        var secondTask = RuntimeGenerators.queueTaskIds().next();
        writeRunning(generation);
        lease();
        var journal = journal("two-attempts");
        var pass = pass(journal);
        append(text("first"));
        pass.run();
        append(text("tail"));
        calls.taskClosed(generation.generationId(), task);
        pass.run();
        openTasks.put(secondTask, 2);
        calls.waitOpened(generation.generationId(), secondTask, generation.configurationDigest());
        append(text("second"));
        pass.run();

        var first = journal.entries(task);
        var second = journal.entries(secondTask);

        assertAll(
                () -> assertEquals(List.of("first", "tail"),
                        first.stream().filter(entry -> entry.kind() == ActivityEntry.Kind.TEXT)
                                .map(ActivityEntry::text).toList(),
                        "the tail the worker wrote belongs to the first task"),
                () -> assertEquals(Set.of(1), first.stream().map(ActivityEntry::attempt).collect(Collectors.toSet()),
                        "every entry of the first task names its attempt"),
                () -> assertEquals("closed", first.getLast().end(), "the closed attempt gets its end entry"),
                () -> assertEquals(List.of("second"),
                        second.stream().filter(entry -> entry.kind() == ActivityEntry.Kind.TEXT)
                                .map(ActivityEntry::text).toList(),
                        "the second task's line belongs to the second task"),
                () -> assertEquals(Set.of(2), second.stream().map(ActivityEntry::attempt).collect(Collectors.toSet()),
                        "every entry of the second task names its attempt"));
    }

    @Test
    @DisplayName("a task closed while the end work runs stays served in the registered terminal generation")
    void aTaskClosedDuringTheEndStaysServed() throws Exception {
        var inside = new CountDownLatch(1);
        var gate = new CountDownLatch(1);
        fixture.protocol = blockingProtocol(inside, gate);
        var blockedEnvironment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var blockedSupervisor = new WorkerSupervisor(blockedEnvironment);
        var blockedEnding = new WorkerEnding(blockedEnvironment, () -> false, () -> {
        });
        var blockedRecycling = new WorkerRecycling(blockedEnvironment, blockedEnding, blockedSupervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        var blockedCalls = new SupervisorCalls(blockedSupervisor, blockedEnding, blockedRecycling, parameters,
                _ -> RoleClass.WRITER);
        var ended = ((WorkerSupervisor.StartOutcome.Started) blockedSupervisor
                .start(List.of(fixture.warmRequest(role, fixture.workdir))).getFirst()).generation();
        writeRunning(ended);
        blockedCalls.waitOpened(ended.generationId(), task, ended.configurationDigest());
        settle(ended);
        var blockedPass = new SupervisionPass(blockedSupervisor, blockedCalls, blockedEnding, blockedRecycling, tasks,
                new AgentStartedDetector(blockedEnvironment, _ -> new ProcessExecutables.Absent(), List::of,
                        blockedEnding),
                pumps(journal("closed-during-end")));
        var runner = new Thread(blockedPass::run);

        runner.start();
        assertTrue(inside.await(5, TimeUnit.SECONDS), "the end path reached the fence");
        blockedCalls.taskClosed(ended.generationId(), task);
        gate.countDown();
        runner.join(TimeUnit.SECONDS.toMillis(5));

        var registered = blockedSupervisor.generation(ended.generationId()).orElseThrow();
        assertAll(
                () -> assertFalse(runner.isAlive(), "the end path ended after the fence returned"),
                () -> assertTrue(registered.terminal(), "the registered generation is terminal"),
                () -> assertEquals(List.of(task), registered.servedTasks(),
                        "the task closed meanwhile stays served"),
                () -> assertFalse(blockedEnding.endRequested(ended.generationId()),
                        "the request is gone once the end is recorded"));
    }

    @Test
    @DisplayName("a generation of a role with no class is reported, and a later generation still advances")
    void aRoleWithNoClassFailsOnlyItsGeneration() throws Exception {
        var otherRole = fixture.role("reviewer", RoleClass.WRITER, RoleForm.WARM, 1);
        var later = ((WorkerSupervisor.StartOutcome.Started) supervisor
                .start(List.of(fixture.warmRequest(otherRole, fixture.workdir))).getFirst()).generation();
        var ownCalls = new SupervisorCalls(supervisor, ending, recycling, parameters,
                name -> name.equals(otherRole.artifact().name()) ? RoleClass.WRITER : null);
        writeRunning(generation);
        writeRunning(later);
        settle(later);
        var ownPass = new SupervisionPass(supervisor, ownCalls, ending, recycling, tasks, silentDetector(),
                pumps(journal("no-class")));

        assertDoesNotThrow(ownPass::run, "a role with no class does not abort the pass");

        assertAll(
                () -> assertTrue(supervisor.generation(later.generationId()).orElseThrow().terminal(),
                        "the later generation still completes its end"),
                () -> assertFalse(supervisor.generation(generation.generationId()).orElseThrow().terminal(),
                        "the unclassified generation stays as it was"));
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                RuntimeLogMessages.WARN.WORKER_ADVANCE_FAILED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                "The role '" + generation.role() + "' has no class");
    }

    @Test
    @DisplayName("a failing idle yield is reported beside a failing generation, and neither replaces the other")
    void aFailingYieldIsReportedBesideAFailingGeneration() throws Exception {
        var yieldFailure = Generators.letterStrings(8, 24).next();
        fixture.slots = failingYield(yieldFailure);
        var ownEnvironment = fixture.environment(SupervisorEnvironment.StartGuard.open(),
                SupervisorEnvironment.EligibilityGate.open());
        var ownSupervisor = new WorkerSupervisor(ownEnvironment);
        var ownEnding = new WorkerEnding(ownEnvironment, () -> false, () -> {
        });
        var ownRecycling = new WorkerRecycling(ownEnvironment, ownEnding, ownSupervisor::generations,
                _ -> Optional.of(new RoleArtifact.Recycling(null, 20_000, null)), taskId -> tasks.facts(taskId),
                parameters);
        var unclassifiedCalls = new SupervisorCalls(ownSupervisor, ownEnding, ownRecycling, parameters, _ -> null);
        var unclassified = started(ownSupervisor);
        writeRunning(unclassified);
        var ownPass = new SupervisionPass(ownSupervisor, unclassifiedCalls, ownEnding, ownRecycling, tasks,
                new AgentStartedDetector(ownEnvironment, _ -> new ProcessExecutables.Absent(), List::of, ownEnding),
                pumps(journal("failing-yield")));

        assertDoesNotThrow(ownPass::run, "the failing yield does not abort the pass");

        assertFalse(ownSupervisor.generation(unclassified.generationId()).orElseThrow().terminal(),
                "the failed generation stays as it was");
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                RuntimeLogMessages.WARN.WORKER_ADVANCE_FAILED.resolveIdentifierString());
        LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                RuntimeLogMessages.WARN.WORKER_IDLE_YIELD_FAILED.resolveIdentifierString());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, yieldFailure);
    }

    @Test
    @DisplayName("a job settled after its task closed gives the held tail and the attempt's end to that task")
    void aJobSettledAfterItsTaskClosedEndsTheClosedAttempt() throws Exception {
        writeRunning(generation);
        lease();
        var journal = journal("closed-then-settled");
        var pass = pass(journal);
        append(text("first"));
        pass.run();
        append(text("tail"));
        calls.taskClosed(generation.generationId(), task);
        settle(generation);

        pass.run();

        var entries = journal.entries(task);
        assertAll(
                () -> assertEquals(List.of("first", "tail"),
                        entries.stream().filter(entry -> entry.kind() == ActivityEntry.Kind.TEXT)
                                .map(ActivityEntry::text).toList(),
                        "the held tail belongs to the closed task"),
                () -> assertEquals(ActivityEntry.Kind.ATTEMPT_END, entries.getLast().kind(),
                        "the closed task's journal ends the attempt"),
                () -> assertEquals("closed", entries.getLast().end(), "the closed attempt ends as closed"),
                () -> assertTrue(supervisor.generation(generation.generationId()).orElseThrow().terminal(),
                        "the settled generation reaches its end"));
    }

    @Test
    @DisplayName("a record the runner's fate step settled completes the end the record names")
    void aRecordSettledByTheFateStepCompletesItsEnd() throws Exception {
        writeRunning(generation);
        lease();
        var journal = journal("fate-step");
        var pass = pass(journal);
        append(text("done"));
        pass.run();
        calls.taskClosed(generation.generationId(), task);
        pass.run();
        var fate = settleThroughTheFateStep(generation, JobRecord.Fate.SUCCEEDED);

        pass.run();

        var entries = journal.entries(task);
        assertAll(
                () -> assertInstanceOf(SettlementStep.Outcome.Done.class, fate,
                        "the runner's fate step records the fate"),
                () -> assertEquals(WorkerState.EndReason.TASK_DONE, recorded(generation).endReason(),
                        "the fate step writes the task-done end"),
                () -> assertEquals(WorkerLifecycle.ENDED,
                        supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                        "the pass ends the worker the record names"),
                () -> assertEquals("closed", entries.getLast().end(), "the served task closes its attempt"),
                () -> assertTrue(fixture.writes.isEmpty(), "the pass writes no worker state of the record"));
    }

    @Test
    @DisplayName("an exit before the first call is settled as a start failure, and no wait runs for the start spacing")
    void anExitBeforeTheFirstCallIsSettledWithoutASpacingWait() throws Exception {
        // A tenth of the compiled bound: a run that waited for the spacing could not finish inside it.
        var margin = HarnessStartSpacing.SPACING_BOUND.dividedBy(10);
        var outcome = assertTimeoutPreemptively(margin, () -> settleAsExitBeforeTheFirstCall(generation),
                "the settlement runs far below the spacing bound");
        var settled = jobs.read(generation.jobId()).orElseThrow();
        var terminal = recorded(generation);
        assertTimeoutPreemptively(margin, () -> pass(journal("exit-before-first-call")).run(),
                "the pass ends the generation far below the bound");
        var owner = Generators.letterStrings(8, 24).next();

        assertTimeoutPreemptively(margin, () -> fixture.spacing.awaitTurn(generation.harness(), owner),
                "the harness's turn is free once the end ran");
        assertAll(
                () -> assertInstanceOf(SettlementStep.Outcome.Done.class, outcome,
                        "the runner's fate step records the fate"),
                () -> assertEquals(JobRecord.Status.ENDED, settled.status(), "the job record is settled as ended"),
                () -> assertTrue(terminal.state().isTerminal(), "the worker block of the record is terminal"),
                () -> assertTrue(terminal.startFailure(), "the worker block carries the start-failure flag"),
                () -> assertEquals(WorkerState.EndReason.START_FAILURE, terminal.endReason(),
                        "the worker block's end reason is the start failure"),
                () -> assertEquals(WorkerLifecycle.START_FAILED,
                        supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                        "the pass ends the generation as a start failure"));
    }

    @Test
    @DisplayName("the lines a log gained before the exit are appended before the attempt end, which is appended once")
    void theTailIsAppendedBeforeTheAttemptEnd() throws Exception {
        var first = Generators.letterStrings(4, 16).next();
        var tail = List.of(Generators.letterStrings(4, 16).next(), Generators.letterStrings(4, 16).next());
        writeRunning(generation, ProcessHandle.current().pid());
        lease();
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        var journal = journal("tail-before-end");
        var pass = pass(journal);
        append(text(first));
        pass.run();
        tail.forEach(line -> append(text(line)));
        settle(generation);

        pass.run();
        pass.run();

        var entries = journal.entries(task);
        assertAll(
                () -> assertEquals(List.of(ActivityEntry.Kind.TEXT, ActivityEntry.Kind.TEXT, ActivityEntry.Kind.TEXT,
                        ActivityEntry.Kind.ATTEMPT_END), entries.stream().map(ActivityEntry::kind).toList(),
                        "the tail precedes the one attempt end"),
                () -> assertEquals(List.of(first, tail.get(0), tail.get(1)),
                        entries.stream().filter(entry -> entry.kind() == ActivityEntry.Kind.TEXT)
                                .map(ActivityEntry::text).toList(),
                        "the journal holds the lines in log order"),
                // The wire name of the attempt end is the contract the journal records.
                () -> assertEquals("exited_without_submit", entries.getLast().end(),
                        "the exit ends the attempt without a submit"));
    }

    @ParameterizedTest
    @CsvSource({"false, START_FAILED, start_failure", "true, LOST, exited"})
    @DisplayName("a settled worker is a start failure only without its first call (end names are wire contract)")
    void aSettledWorkerIsAStartFailureOnlyWithoutItsFirstCall(boolean called, WorkerLifecycle lifecycle,
            String endName) throws Exception {
        if (called) {
            writeRunning(generation);
            calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
            settleThroughTheFateStep(generation, JobRecord.Fate.FAILED);
        } else {
            settleAsExitBeforeTheFirstCall(generation);
        }

        pass(journal("start-failure-" + called)).run();

        assertEquals(lifecycle, supervisor.generation(generation.generationId()).orElseThrow().lifecycle(),
                "the first call decides the generation's end");
        LogAsserts.assertLogMessagePresent(TestLogLevel.INFO, RuntimeLogMessages.INFO.WORKER_GENERATION_ENDED
                .format(generation.generationId(), generation.role(), endName));
    }

    @Test
    @DisplayName("a requested end names the attempt end, not the loss of the exit nor the reason the record carries")
    void aRequestedEndWinsOverTheLossAndTheRecordedReason() throws Exception {
        writeRunning(generation, ProcessHandle.current().pid());
        lease();
        calls.callHeartbeat(generation.generationId(), fixture.clock.instant());
        var journal = journal("requested-wins");
        var pass = pass(journal);
        pass.run();
        ending.end(generation, EndCause.DRAIN);
        settle(generation);

        pass.run();

        var entries = journal.entries(task);
        assertAll(
                () -> assertEquals(WorkerState.EndReason.TASK_DONE, recorded(generation).endReason(),
                        "the settled record carries its own end reason"),
                () -> assertEquals(1, entries.stream().filter(entry -> entry.kind() == ActivityEntry.Kind.ATTEMPT_END)
                        .count(), "the attempt's end is appended once"),
                // The wire name of the drain's attempt end is the contract the journal records.
                () -> assertEquals("drain", entries.getLast().end(),
                        "the requested drain names the attempt end"));
    }

    @Test
    @DisplayName("a zero_tool worker without a connection is ended by its exit and its budget alone")
    void aZeroToolWorkerIsEndedByItsExitAndItsBudget() throws Exception {
        // The pass learns a role's class only through the calls' classifier and the record's worker block, so the
        // generation the fixture can launch is classified zero_tool there; no MCP connection is ever made.
        role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var zeroTool = ((WorkerSupervisor.StartOutcome.Started) supervisor
                .start(List.of(fixture.freshRequest(role))).getFirst()).generation();
        var zeroToolCalls = new SupervisorCalls(supervisor, ending, recycling, parameters, _ -> RoleClass.ZERO_TOOL);
        var unconnected = workerState(zeroTool).toBuilder().roleClass(WorkerState.RoleClass.ZERO_TOOL)
                .form(WorkerState.Form.FRESH).connectedAt(null).jobTokenSha256(null).build();
        try (var transaction = locks.openTransaction()) {
            jobs.writeAhead(transaction, running(zeroTool, unconnected));
        }
        var pass = new SupervisionPass(supervisor, zeroToolCalls, ending, recycling, tasks, silentDetector(),
                pumps(journal("zero-tool")));
        pass.run();
        fixture.advance(Duration.ofSeconds(901));
        pass.run();
        var requestedBeforeTheExit = ending.requestedCause(zeroTool.generationId());
        settleThroughTheFateStep(zeroTool, JobRecord.Fate.TIMED_OUT);

        pass.run();

        assertAll(
                () -> assertEquals(Optional.empty(), requestedBeforeTheExit,
                        "no silence deadline ends a zero_tool worker"),
                () -> assertTrue(fixture.cancels.isEmpty(), "no cancellation reaches the zero_tool worker"),
                () -> assertEquals(WorkerLifecycle.ENDED,
                        supervisor.generation(zeroTool.generationId()).orElseThrow().lifecycle(),
                        "the exit ends the zero_tool worker"),
                () -> assertEquals(WorkerState.EndReason.BUDGET, recorded(zeroTool).endReason(),
                        "the budget names the zero_tool worker's end"),
                () -> assertFalse(recorded(zeroTool).startFailure(), "an exit without a call is no start failure"));
    }

    /** @return the admission double whose claims are granted and whose read of a waiting role fails */
    private static ModelSlots failingYield(String message) {
        ModelSlots mock = createMock(ModelSlots.class);
        expect(mock.claim(anyString(), anyString())).andAnswer(() -> Optional.of(new ModelSlots.Lease(
                (String) getCurrentArguments()[0], (String) getCurrentArguments()[1]))).anyTimes();
        expect(mock.nextWaitingRole()).andThrow(new IllegalStateException(message)).anyTimes();
        replay(mock);
        return mock;
    }

    private int released(WorkerGeneration gen) {
        return (int) fixture.released.stream()
                .filter(lease -> lease.generationId().equals(gen.generationId())).count();
    }

    private int fenced(WorkerGeneration gen) {
        return (int) fixture.fences.stream().filter(gen.generationId()::equals).count();
    }

    private static WorkerProtocol blockingProtocol(CountDownLatch inside, CountDownLatch gate) {
        WorkerProtocol mock = createMock(WorkerProtocol.class);
        mock.fence(anyString());
        expectLastCall().andAnswer(() -> {
            inside.countDown();
            try {
                gate.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).anyTimes();
        mock.offTrack(anyObject(WorkerProtocol.OffTrackReport.class));
        expectLastCall().andAnswer(() -> null).anyTimes();
        mock.holdEnded(anyObject(WorkerProtocol.HoldEndedReport.class));
        expectLastCall().andAnswer(() -> null).anyTimes();
        mock.agentStarted(anyString(), anyString(), anyInt(), anyString(), anyObject(Instant.class));
        expectLastCall().andAnswer(() -> null).anyTimes();
        mock.closeUnavailable(anyString(), anyString());
        expectLastCall().andAnswer(() -> null).anyTimes();
        replay(mock);
        return mock;
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
