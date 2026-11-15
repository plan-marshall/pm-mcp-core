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
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.WorkerState.ContextTokens;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The recycling decision of warm workers (PM-WORK-3, Recycling Warm Workers): a wait answer of a generation holding a
 * lease is no idle wake-up and its matched control is recycled, the token budget reads the latest turn's context and
 * never a run total and never before the first closed task, the tasks per generation and a changed configuration digest
 * recycle after the task, a changed digest clears the guard even when the task bound recycles in the same call, the idle
 * yield frees a slot for another role, and every recycle logs once with its cause.
 * <p>
 * The queue task, the changed digest, the alternative worktree and the two role names are generated in the test's
 * {@code @BeforeEach}; the token budget, the idle bound and the parameters are the values the contract fixes.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Worker recycling")
class WorkerRecyclingTest {

    private static final int TOKEN_BUDGET = 20_000;

    private static final int IDLE_WAKEUPS = 3;

    private static final WorkerParameters PARAMETERS =
            new WorkerParameters(true, OptionalInt.of(270), false, 600, IDLE_WAKEUPS, 300, false);

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private final List<String> cleared = new CopyOnWriteArrayList<>();

    private SupervisorEnvironment.StartGuard guard;

    private SupervisorEnvironment environment;

    private WorkerSupervisor supervisor;

    private WorkerEnding ending;

    private WorkerRecycling recycling;

    private String firstTask;

    private String changedDigest;

    private String otherWorktree;

    private String waitingRole;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        firstTask = RuntimeGenerators.queueTaskIds().next();
        changedDigest = RuntimeGenerators.sha256Digests().next();
        otherWorktree = RuntimeGenerators.words().next();
        waitingRole = "role-" + RuntimeGenerators.words().next();
        guard = guard();
        environment = fixture.environment(guard, OPEN_GATE);
        supervisor = new WorkerSupervisor(environment);
        ending = new WorkerEnding(environment, () -> false, () -> {
        });
        recycling = recycling(new RoleArtifact.Recycling(null, TOKEN_BUDGET, null));
    }

    @Test
    @DisplayName("a wait answer of a generation holding a lease counts no idle wake-up and recycles nothing")
    void leasedWaitIsNoIdleWakeUp() {
        var generation = warm().withLifecycle(WorkerLifecycle.EXECUTING).withLeasedTask(firstTask)
                .withIdleWakeups(IDLE_WAKEUPS - 1L);

        var result = recycling.waitAgain(generation);

        assertAll(
                () -> assertEquals(IDLE_WAKEUPS - 1L, result.idleWakeups(), "the leased answer counts no wake-up"),
                () -> assertNull(result.recycleCause(), "a consulting or holding worker is not idle"),
                () -> assertFalse(ending.endRequested(generation.generationId()), "a leased wait requests no end"));
    }

    @Test
    @DisplayName("the matched control without a lease is recycled at the idle wake-ups")
    void unleasedWaitRecyclesAtTheBound() {
        var generation = warm().withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(IDLE_WAKEUPS - 1L);

        var result = recycling.waitAgain(generation);

        assertAll(
                () -> assertEquals(IDLE_WAKEUPS, result.idleWakeups(),
                        "the unleased wait counts the idle wake-up"),
                () -> assertEquals(RecycleCause.IDLE, result.recycleCause(),
                        "the idle bound recycles the generation"),
                () -> assertEquals(Optional.of(EndCause.RECYCLE), ending.requestedCause(generation.generationId()),
                        "the recycle requests the recycle end"));
    }

    @Test
    @DisplayName("a lower role idle bound recycles earlier and a higher one never raises the host's")
    void roleIdleBoundOnlyLowersTheHostValue() throws Exception {
        recycling = recycling(new RoleArtifact.Recycling(1, TOKEN_BUDGET, null));
        var generation = warm().withLifecycle(WorkerLifecycle.WAITING);

        var lower = recycling.waitAgain(generation);

        recycling = recycling(new RoleArtifact.Recycling(null, TOKEN_BUDGET, null));
        var other = warm(Files.createDirectories(base.resolve(otherWorktree)))
                .withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(1L);
        var raised = recycling.waitAgain(other);

        assertAll(
                () -> assertEquals(RecycleCause.IDLE, lower.recycleCause(), "the role lowers the bound"),
                () -> assertEquals(1L, lower.idleWakeups(), "the lower role bound counts one wake-up"),
                () -> assertEquals(2L, raised.idleWakeups(), "the role cannot raise the host's bound"),
                () -> assertNull(raised.recycleCause(), "no recycle cause is recorded at the host's bound"));
    }

    @Test
    @DisplayName("the token budget reads the latest turn's context and never a run total")
    void tokenBudgetReadsTheLatestTurnContext() {
        var closed = warm().withLifecycle(WorkerLifecycle.WAITING).withServedTasks(List.of(firstTask));

        assertAll(
                () -> assertTrue(recycling.afterTurnCompleted(closed).isEmpty(),
                        "an unmeasured context stays unmeasured here"),
                () -> assertTrue(recycling.afterTurnCompleted(
                                closed.withContextTokens(ContextTokens.measured(TOKEN_BUDGET - 1L))).isEmpty(),
                        "a context below the budget recycles nothing"),
                () -> assertEquals(Optional.of(RecycleCause.TOKEN_BUDGET), recycling.afterTurnCompleted(
                                closed.withContextTokens(ContextTokens.measured(TOKEN_BUDGET))),
                        "the measured budget recycles at the total"));
    }

    @Test
    @DisplayName("a start context over the budget recycles only after the first closed task")
    void startContextRecyclesOnlyAfterTheFirstTask() {
        var executing = warm().withLifecycle(WorkerLifecycle.EXECUTING).withLeasedTask(firstTask)
                .withContextTokens(ContextTokens.measured(TOKEN_BUDGET * 20L));
        var afterClose = executing.withLifecycle(WorkerLifecycle.WAITING).withLeasedTask(null)
                .withServedTasks(List.of(firstTask));

        assertAll(
                () -> assertTrue(recycling.afterTurnCompleted(executing).isEmpty(),
                        "a start context alone does not recycle"),
                () -> assertTrue(recycling.afterTurnCompleted(executing.withServedTasks(List.of(firstTask))).isEmpty(),
                        "a task the worker still leases is not recycled at the budget"),
                () -> assertEquals(Optional.of(RecycleCause.TOKEN_BUDGET), recycling.afterTurnCompleted(afterClose),
                        "the closed task lets the budget recycle"));
    }

    @Test
    @DisplayName("a worker holding its task for an answer leaves the hold at the budget")
    void holdingWorkerRecyclesAtTheBudget() {
        var holding = warm().withLifecycle(WorkerLifecycle.HOLDING).withLeasedTask(firstTask)
                .withContextTokens(ContextTokens.measured(TOKEN_BUDGET));

        assertEquals(Optional.of(RecycleCause.TOKEN_BUDGET), recycling.afterTurnCompleted(holding),
                "a holding worker leaves its hold at the budget");
    }

    @Test
    @DisplayName("a changed configuration digest recycles after the task and clears the start-failure guard")
    void changedConfigurationRecyclesAfterTheTask() {
        var closed = warm().withLifecycle(WorkerLifecycle.WAITING).withServedTasks(List.of(firstTask))
                .withContextTokens(ContextTokens.NOT_MEASURED);

        assertAll(
                () -> assertTrue(recycling.afterTaskClosed(closed, closed.configurationDigest()).isEmpty(),
                        "an unchanged digest recycles nothing"),
                () -> assertEquals(Optional.of(RecycleCause.CONFIGURATION_CHANGED),
                        recycling.afterTaskClosed(closed, changedDigest),
                        "a changed digest recycles after the task"),
                () -> assertEquals(List.of("implementer@claude"), cleared,
                        "the guard of the role on its harness is cleared"));
    }

    @Test
    @DisplayName("a generation that closed the role's tasks per generation is recycled")
    void tasksPerGenerationRecycles() {
        recycling = recycling(new RoleArtifact.Recycling(null, TOKEN_BUDGET, 1));
        var closed = warm().withLifecycle(WorkerLifecycle.WAITING).withServedTasks(List.of(firstTask));

        assertAll(
                () -> assertEquals(Optional.of(RecycleCause.TASKS_PER_GENERATION),
                        recycling.afterTaskClosed(closed, closed.configurationDigest()),
                        "one closed task reaches the per-generation bound"),
                () -> assertTrue(recycling.afterTaskClosed(closed.withServedTasks(List.of()),
                                closed.configurationDigest()).isEmpty(),
                        "a generation that closed no task reached no bound"));
    }

    @Test
    @DisplayName("a changed configuration clears the guard even when the generation reached its tasks")
    void aChangedConfigurationClearsTheGuardAtTheTaskBound() {
        recycling = recycling(new RoleArtifact.Recycling(null, TOKEN_BUDGET, 1));
        var closed = warm().withLifecycle(WorkerLifecycle.WAITING).withServedTasks(List.of(firstTask));

        var cause = recycling.afterTaskClosed(closed, changedDigest);

        assertAll(
                () -> assertEquals(Optional.of(RecycleCause.TASKS_PER_GENERATION), cause,
                        "the task bound still recycles the generation"),
                () -> assertEquals(List.of("implementer@claude"), cleared,
                        "a changed configuration clears the guard here"));
    }

    @Test
    @DisplayName("the idle yield frees the slot of the longest-idle warm worker for another role")
    void idleYieldFreesASlotForAnotherRole() {
        var generation = registered(warm().withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(5L));
        fixture.waitingRole = waitingRole;

        var cause = recycling.yieldIdleSlot();
        var requested = ending.requestedCause(generation.generationId());
        ending.exited(generation);

        assertAll(
                () -> assertEquals(Optional.of(RecycleCause.IDLE_YIELD), cause,
                        "the idle yield recycles the longest idle worker"),
                () -> assertEquals(Optional.of(EndCause.RECYCLE), requested,
                        "the yield requests the recycle end"),
                () -> assertEquals(List.of(generation.slot()), fixture.released,
                        "the slot of the idle worker is released for the waiting role"));
    }

    @Test
    @DisplayName("no idle yield recycles a worker of the waiting role or one holding a lease")
    void idleYieldSparesTheWaitingRoleAndLeases() {
        var generation = registered(warm().withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(5L));

        fixture.waitingRole = "implementer";
        var sameRole = recycling.yieldIdleSlot();

        fixture.waitingRole = waitingRole;
        registered(generation.withLeasedTask(firstTask));
        var leased = recycling.yieldIdleSlot();

        fixture.waitingRole = null;
        var nobodyWaits = recycling.yieldIdleSlot();

        assertAll(
                () -> assertTrue(sameRole.isEmpty(), "a worker of the waiting role frees nothing for it"),
                () -> assertTrue(leased.isEmpty(), "a worker holding a lease is never yielded"),
                () -> assertTrue(nobodyWaits.isEmpty(), "no yield without a waiting role"),
                () -> assertFalse(ending.endRequested(generation.generationId()),
                        "no idle yield requests an end here"));
    }

    @Test
    @DisplayName("every recycle emits exactly one record naming its cause")
    void everyRecycleLogsOnce() {
        var generation = warm().withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(IDLE_WAKEUPS - 1L);

        var first = recycling.waitAgain(generation);
        var second = recycling.waitAgain(generation);

        assertAll(
                () -> assertEquals(RecycleCause.IDLE, first.recycleCause(),
                        "the first wait recycles for the idle bound"),
                () -> assertNull(second.recycleCause(), "a generation is recycled once"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.WORKER_GENERATION_RECYCLED.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "recycled for 'idle'"));
    }

    private WorkerRecycling recycling(RoleArtifact.Recycling bounds) {
        return new WorkerRecycling(environment, ending, supervisor::generations, role -> Optional.of(bounds),
                PARAMETERS);
    }

    private WorkerGeneration warm() {
        return warm(fixture.workdir);
    }

    private WorkerGeneration warm(Path workdir) {
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 2);
        var outcome = supervisor.start(List.of(fixture.warmRequest(role, workdir))).getFirst();
        return assertInstanceOf(StartOutcome.Started.class, outcome, "the recycling test starts a warm worker")
                .generation();
    }

    private WorkerGeneration registered(WorkerGeneration generation) {
        supervisor.replaced(generation);
        return generation;
    }

    /** @return an EasyMock guard that never holds and records every clear of a role on its harness */
    private SupervisorEnvironment.StartGuard guard() {
        SupervisorEnvironment.StartGuard mock = createMock(SupervisorEnvironment.StartGuard.class);
        expect(mock.holds(anyObject(String.class), anyObject(String.class))).andReturn(false).anyTimes();
        mock.clear(anyObject(String.class), anyObject(String.class));
        expectLastCall().andAnswer(() -> {
            cleared.add(getCurrentArguments()[0] + "@" + getCurrentArguments()[1]);
            return null;
        }).anyTimes();
        replay(mock);
        return mock;
    }
}
