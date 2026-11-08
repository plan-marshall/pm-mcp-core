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

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The terminal-state construction of the worker-state writer (PM-WORK-3, Job Record): the worker block's invariants
 * refuse a terminal state whose end reason is still absent and a leased task in a terminal state, so the state, its
 * instant, the end reason, the start-failure flag and the cleared task must reach the record together.
 * <p>
 * The task id and both digests are generated; the instants, the state and the end reason are the contracts.
 */
@EnableGeneratorController
@DisplayName("The terminal worker state")
class SupervisorEnvironmentTest {

    private static final Instant CONNECTED_AT = Instant.parse("2026-10-26T10:00:00Z");

    private static final Instant ENDED_AT = CONNECTED_AT.plusSeconds(60);

    private String task;

    private String digest;

    private String roleName;

    private String harnessName;

    private String modelName;

    @BeforeEach
    void draw() {
        task = RuntimeGenerators.queueTaskIds().next();
        digest = RuntimeGenerators.sha256Digests().next();
        roleName = RuntimeGenerators.words().next();
        harnessName = RuntimeGenerators.words().next();
        modelName = RuntimeGenerators.words().next();
    }

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

    private WorkerState leasedWorker() {
        return WorkerState.builder()
                .role(roleName)
                .configurationDigest(digest)
                .roleClass(WorkerState.RoleClass.WRITER)
                .form(WorkerState.Form.WARM)
                .harness(harnessName)
                .harnessVersion("1.0.0")
                .model(modelName)
                .generationId(new JobId(RuntimeGenerators.jobIds(JobId.Kind.LLM).next()))
                .workdir(new WorkerState.Workdir(WorkerState.Workdir.Kind.PROJECT_CHECKOUT, null))
                .promptDigest(digest)
                .jobTokenSha256(digest)
                .connectedAt(CONNECTED_AT)
                .state(WorkerState.State.EXECUTING)
                .stateAt(CONNECTED_AT)
                .task(task)
                .servedTasks(List.of())
                .idleWakeups(0L)
                .contextTokens(WorkerState.ContextTokens.NOT_MEASURED)
                .deliveredSkills(List.of())
                .startFailure(false)
                .build();
    }
}
