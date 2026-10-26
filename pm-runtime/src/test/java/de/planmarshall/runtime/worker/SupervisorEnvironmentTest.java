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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.WorkerState;

/**
 * The terminal-state construction of the worker-state writer (PM-WORK-3, Job Record): the worker block's invariants
 * refuse a terminal state whose end reason is still absent and a leased task in a terminal state, so the state, its
 * instant, the end reason, the start-failure flag and the cleared task must reach the record together.
 */
@DisplayName("The terminal worker state")
class SupervisorEnvironmentTest {

    private static final Instant CONNECTED_AT = Instant.parse("2026-10-26T10:00:00Z");

    private static final Instant ENDED_AT = CONNECTED_AT.plusSeconds(60);

    private static final String TASK = "qt-0123456789abcdef";

    private static final String DIGEST = "a".repeat(64);

    @Test
    @DisplayName("is built in one construction, with the leased task cleared")
    void terminalStateIsBuiltInOneConstruction() {
        var leased = leasedWorker();

        var ended = SupervisorEnvironment.terminal(leased, WorkerState.State.ENDED, ENDED_AT,
                WorkerState.EndReason.EXITED, false);

        assertAll(
                () -> assertEquals(WorkerState.State.ENDED, ended.state()),
                () -> assertEquals(ENDED_AT, ended.stateAt()),
                () -> assertEquals(WorkerState.EndReason.EXITED, ended.endReason()),
                () -> assertFalse(ended.startFailure(), "an exit after the first call is no start failure"),
                () -> assertNull(ended.task(), "the terminal state holds no task"));
    }

    @Test
    @DisplayName("carries a start failure of a worker that never connected")
    void startFailureIsTerminal() {
        var starting = leasedWorker().toBuilder().connectedAt(null).state(WorkerState.State.WAITING).task(null).build();

        var failed = SupervisorEnvironment.terminal(starting, WorkerState.State.ENDED, ENDED_AT,
                WorkerState.EndReason.START_FAILURE, true);

        assertAll(
                () -> assertEquals(WorkerState.EndReason.START_FAILURE, failed.endReason()),
                () -> assertTrue(failed.startFailure(), "the flag and the end reason agree"));
    }

    private static WorkerState leasedWorker() {
        return WorkerState.builder()
                .role("implementer")
                .configurationDigest(DIGEST)
                .roleClass(WorkerState.RoleClass.WRITER)
                .form(WorkerState.Form.WARM)
                .harness("claude")
                .harnessVersion("1.0.0")
                .model("a-model")
                .generationId(new JobId("l-00000000000000ff"))
                .workdir(new WorkerState.Workdir(WorkerState.Workdir.Kind.PROJECT_CHECKOUT, null))
                .promptDigest(DIGEST)
                .jobTokenSha256(DIGEST)
                .connectedAt(CONNECTED_AT)
                .state(WorkerState.State.EXECUTING)
                .stateAt(CONNECTED_AT)
                .task(TASK)
                .servedTasks(List.of())
                .idleWakeups(0L)
                .contextTokens(WorkerState.ContextTokens.NOT_MEASURED)
                .deliveredSkills(List.of())
                .startFailure(false)
                .build();
    }
}
