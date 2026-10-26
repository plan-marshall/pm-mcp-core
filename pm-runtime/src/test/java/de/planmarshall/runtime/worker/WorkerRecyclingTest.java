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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The recycling decision of warm workers (PM-WORK-3, Recycling Warm Workers): a wait answer of a generation holding a
 * lease is no idle wake-up and its matched control is recycled, the token budget reads the latest turn's context and
 * never a run total and never before the first closed task, the tasks per generation and a changed configuration digest
 * recycle after the task, a changed digest clears the guard even when the task bound recycles in the same call, the idle
 * yield frees a slot for another role, and every recycle logs once with its cause.
 */
@EnableTestLogger
@DisplayName("Worker recycling")
class WorkerRecyclingTest {

    private static final String FIRST_TASK = "qt-0123456789abcdef";

    private static final String CHANGED_DIGEST = "6d2f1b3c4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef";

    private static final int TOKEN_BUDGET = 20_000;

    private static final int IDLE_WAKEUPS = 3;

    private static final WorkerParameters PARAMETERS =
            new WorkerParameters(true, OptionalInt.of(270), false, 600, IDLE_WAKEUPS, 300, false);

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private RecordingGuard guard;

    private SupervisorEnvironment environment;

    private WorkerSupervisor supervisor;

    private WorkerEnding ending;

    private WorkerRecycling recycling;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        guard = new RecordingGuard();
        environment = fixture.environment(guard, OPEN_GATE);
        supervisor = new WorkerSupervisor(environment);
        ending = new WorkerEnding(environment, () -> false, () -> {
        });
        recycling = recycling(new RoleArtifact.Recycling(null, TOKEN_BUDGET, null));
    }

    @Test
    @DisplayName("a wait answer of a generation holding a lease counts no idle wake-up and recycles nothing")
    void leasedWaitIsNoIdleWakeUp() {
        var generation = warm().withLifecycle(WorkerLifecycle.EXECUTING).withLeasedTask(FIRST_TASK)
                .withIdleWakeups(IDLE_WAKEUPS - 1L);

        var result = recycling.waitAgain(generation);

        assertAll(
                () -> assertEquals(IDLE_WAKEUPS - 1L, result.idleWakeups(), "the leased answer counts no wake-up"),
                () -> assertTrue(result.recycleCause().isEmpty(), "a consulting or holding worker is not idle"),
                () -> assertFalse(ending.endRequested(generation.generationId())));
    }

    @Test
    @DisplayName("the matched control without a lease is recycled at the idle wake-ups")
    void unleasedWaitRecyclesAtTheBound() {
        var generation = warm().withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(IDLE_WAKEUPS - 1L);

        var result = recycling.waitAgain(generation);

        assertAll(
                () -> assertEquals(IDLE_WAKEUPS, result.idleWakeups()),
                () -> assertEquals(Optional.of(RecycleCause.IDLE), result.recycleCause()),
                () -> assertEquals(Optional.of(EndCause.RECYCLE), ending.requestedCause(generation.generationId())));
    }

    @Test
    @DisplayName("a lower role idle bound recycles earlier and a higher one never raises the host's")
    void roleIdleBoundOnlyLowersTheHostValue() throws Exception {
        recycling = recycling(new RoleArtifact.Recycling(1, TOKEN_BUDGET, null));
        var generation = warm().withLifecycle(WorkerLifecycle.WAITING);

        var lower = recycling.waitAgain(generation);

        recycling = recycling(new RoleArtifact.Recycling(null, TOKEN_BUDGET, null));
        var other = warm(Files.createDirectories(base.resolve("other-worktree")))
                .withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(1L);
        var raised = recycling.waitAgain(other);

        assertAll(
                () -> assertEquals(Optional.of(RecycleCause.IDLE), lower.recycleCause(), "the role lowers the bound"),
                () -> assertEquals(1L, lower.idleWakeups()),
                () -> assertEquals(2L, raised.idleWakeups(), "the role cannot raise the host's bound"),
                () -> assertTrue(raised.recycleCause().isEmpty()));
    }

    @Test
    @DisplayName("the token budget reads the latest turn's context and never a run total")
    void tokenBudgetReadsTheLatestTurnContext() {
        var closed = warm().withLifecycle(WorkerLifecycle.WAITING).withServedTasks(List.of(FIRST_TASK));

        assertAll(
                () -> assertTrue(recycling.afterTurnCompleted(closed).isEmpty(),
                        "an unmeasured context stays unmeasured, whatever the run total is"),
                () -> assertTrue(recycling.afterTurnCompleted(
                        closed.withContextTokens(ContextTokens.measured(TOKEN_BUDGET - 1L))).isEmpty()),
                () -> assertEquals(Optional.of(RecycleCause.TOKEN_BUDGET), recycling.afterTurnCompleted(
                        closed.withContextTokens(ContextTokens.measured(TOKEN_BUDGET)))));
    }

    @Test
    @DisplayName("a start context over the budget recycles only after the first closed task")
    void startContextRecyclesOnlyAfterTheFirstTask() {
        var executing = warm().withLifecycle(WorkerLifecycle.EXECUTING).withLeasedTask(FIRST_TASK)
                .withContextTokens(ContextTokens.measured(TOKEN_BUDGET * 20L));
        var afterClose = executing.withLifecycle(WorkerLifecycle.WAITING).withLeasedTask(null)
                .withServedTasks(List.of(FIRST_TASK));

        assertAll(
                () -> assertTrue(recycling.afterTurnCompleted(executing).isEmpty(),
                        "the context a worker starts with is no reason to end its first task"),
                () -> assertTrue(recycling.afterTurnCompleted(executing.withServedTasks(List.of(FIRST_TASK))).isEmpty(),
                        "a task the worker still leases is not recycled at the budget"),
                () -> assertEquals(Optional.of(RecycleCause.TOKEN_BUDGET), recycling.afterTurnCompleted(afterClose),
                        "the closed task ends the lease, so the budget recycles the next wait"));
    }

    @Test
    @DisplayName("a worker holding its task for an answer leaves the hold at the budget")
    void holdingWorkerRecyclesAtTheBudget() {
        var holding = warm().withLifecycle(WorkerLifecycle.HOLDING).withLeasedTask(FIRST_TASK)
                .withContextTokens(ContextTokens.measured(TOKEN_BUDGET));

        assertEquals(Optional.of(RecycleCause.TOKEN_BUDGET), recycling.afterTurnCompleted(holding));
    }

    @Test
    @DisplayName("a changed configuration digest recycles after the task and clears the start-failure guard")
    void changedConfigurationRecyclesAfterTheTask() {
        var closed = warm().withLifecycle(WorkerLifecycle.WAITING).withServedTasks(List.of(FIRST_TASK))
                .withContextTokens(ContextTokens.NOT_MEASURED);

        assertAll(
                () -> assertTrue(recycling.afterTaskClosed(closed, closed.configurationDigest()).isEmpty(),
                        "an unchanged digest recycles nothing"),
                () -> assertEquals(Optional.of(RecycleCause.CONFIGURATION_CHANGED),
                        recycling.afterTaskClosed(closed, CHANGED_DIGEST)),
                () -> assertEquals(List.of("implementer@claude"), guard.cleared,
                        "the guard of the role on its harness is cleared"));
    }

    @Test
    @DisplayName("a generation that closed the role's tasks per generation is recycled")
    void tasksPerGenerationRecycles() {
        recycling = recycling(new RoleArtifact.Recycling(null, TOKEN_BUDGET, 1));
        var closed = warm().withLifecycle(WorkerLifecycle.WAITING).withServedTasks(List.of(FIRST_TASK));

        assertAll(
                () -> assertEquals(Optional.of(RecycleCause.TASKS_PER_GENERATION),
                        recycling.afterTaskClosed(closed, closed.configurationDigest())),
                () -> assertTrue(recycling.afterTaskClosed(closed.withServedTasks(List.of()),
                                closed.configurationDigest()).isEmpty(),
                        "a generation that closed no task reached no bound"));
    }

    @Test
    @DisplayName("a changed configuration clears the guard even when the generation reached its tasks")
    void aChangedConfigurationClearsTheGuardAtTheTaskBound() {
        recycling = recycling(new RoleArtifact.Recycling(null, TOKEN_BUDGET, 1));
        var closed = warm().withLifecycle(WorkerLifecycle.WAITING).withServedTasks(List.of(FIRST_TASK));

        var cause = recycling.afterTaskClosed(closed, CHANGED_DIGEST);

        assertAll(
                () -> assertEquals(Optional.of(RecycleCause.TASKS_PER_GENERATION), cause,
                        "the task bound still recycles the generation"),
                () -> assertEquals(List.of("implementer@claude"), guard.cleared,
                        "the changed configuration clears the guard in the same recycle"));
    }

    @Test
    @DisplayName("the idle yield frees the slot of the longest-idle warm worker for another role")
    void idleYieldFreesASlotForAnotherRole() {
        var generation = registered(warm().withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(5L));
        fixture.slots.waitingRole = "reviewer";

        var cause = recycling.yieldIdleSlot();
        var requested = ending.requestedCause(generation.generationId());
        ending.exited(generation);

        assertAll(
                () -> assertEquals(Optional.of(RecycleCause.IDLE_YIELD), cause),
                () -> assertEquals(Optional.of(EndCause.RECYCLE), requested),
                () -> assertEquals(List.of(generation.slot()), fixture.slots.released,
                        "the slot of the idle worker is released for the waiting role"));
    }

    @Test
    @DisplayName("no idle yield recycles a worker of the waiting role or one holding a lease")
    void idleYieldSparesTheWaitingRoleAndLeases() {
        var generation = registered(warm().withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(5L));

        fixture.slots.waitingRole = "implementer";
        var sameRole = recycling.yieldIdleSlot();

        fixture.slots.waitingRole = "reviewer";
        registered(generation.withLeasedTask(FIRST_TASK));
        var leased = recycling.yieldIdleSlot();

        fixture.slots.waitingRole = null;
        var nobodyWaits = recycling.yieldIdleSlot();

        assertAll(
                () -> assertTrue(sameRole.isEmpty(), "a worker of the waiting role frees nothing for it"),
                () -> assertTrue(leased.isEmpty(), "a worker holding a lease is never yielded"),
                () -> assertTrue(nobodyWaits.isEmpty()),
                () -> assertFalse(ending.endRequested(generation.generationId())));
    }

    @Test
    @DisplayName("every recycle emits exactly one record naming its cause")
    void everyRecycleLogsOnce() {
        var generation = warm().withLifecycle(WorkerLifecycle.WAITING).withIdleWakeups(IDLE_WAKEUPS - 1L);

        var first = recycling.waitAgain(generation);
        var second = recycling.waitAgain(generation);

        assertAll(
                () -> assertEquals(Optional.of(RecycleCause.IDLE), first.recycleCause()),
                () -> assertTrue(second.recycleCause().isEmpty(), "a generation is recycled once"),
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
        return assertInstanceOf(StartOutcome.Started.class, outcome).generation();
    }

    private WorkerGeneration registered(WorkerGeneration generation) {
        supervisor.replaced(generation);
        return generation;
    }

    /** The start-failure guard of the test: it never holds and records every clear of a role on its harness. */
    private static final class RecordingGuard implements SupervisorEnvironment.StartGuard {

        private final List<String> cleared = new CopyOnWriteArrayList<>();

        @Override
        public boolean holds(String role, String harness) {
            return false;
        }

        @Override
        public void clear(String role, String harness) {
            cleared.add(role + "@" + harness);
        }
    }
}
