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
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import java.io.Serializable;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.WorkerState.ContextTokens;
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The value contract of {@link WorkerGeneration}, whose hand-written {@code equals}/{@code hashCode} compare the
 * generation id alone: two snapshots of one generation are the same generation whatever state each records, and two
 * generations are never equal.
 * <p>
 * The identifiers, the model, the digest and the worktree name are generated; the instant, the lifecycle states and the
 * workdir kind are the contracts of the value.
 */
@EnableGeneratorController
@DisplayName("WorkerGeneration value contract")
class WorkerGenerationContractTest implements ShouldHandleObjectContracts<WorkerGeneration> {

    private static final Instant STARTED_AT = Instant.parse("2026-10-25T10:00:00Z");

    private String generationId;

    private String role;

    private String harness;

    private String model;

    private String digest;

    private Path workdir;

    @BeforeEach
    void draw() {
        generationId = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        role = RuntimeGenerators.words().next();
        harness = RuntimeGenerators.words().next();
        model = RuntimeGenerators.words().next();
        digest = RuntimeGenerators.sha256Digests().next();
        workdir = Path.of(RuntimeGenerators.words().next());
    }

    @Override
    public WorkerGeneration getUnderTest() {
        return generation(WorkerLifecycle.STARTING, List.of(), 0L, null);
    }

    @Test
    @DisplayName("two snapshots of one generation are equal whatever state each records")
    void equalityIgnoresTheRecordedState() {
        var starting = generation(WorkerLifecycle.STARTING, List.of(), 0L, null);
        var executing = generation(WorkerLifecycle.EXECUTING, List.of("qt-" + Generators.integers(1, 99).next()), 4L,
                "qt-" + Generators.integers(1, 99).next());

        assertAll(
                () -> assertEquals(starting, executing, "one generation is equal to its later snapshot"),
                () -> assertEquals(starting.hashCode(), executing.hashCode(),
                        "equal generations carry one hash code"));
    }

    @Test
    @DisplayName("two generations with different ids are never equal")
    void equalityNeedsTheGenerationId() {
        var other = new WorkerGeneration(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(), role, harness, model,
                RoleForm.FRESH, LaunchRequest.WorkdirKind.JOB_PRIVATE, null, workdir, digest, STARTED_AT,
                JobId.random(JobId.Kind.LLM), new ModelSlots.Lease(generationId, role), WorkerLifecycle.STARTING, null,
                List.of(), 0L, ContextTokens.NOT_MEASURED, null);

        assertNotEquals(getUnderTest(), other, "a different id is a different generation");
    }

    @Override
    @Test
    @DisplayName("the generation is deliberately not Serializable")
    public void shouldImplementSerializable() {
        assertFalse(Serializable.class.isAssignableFrom(WorkerGeneration.class),
                "A generation is written as a job record, not Java-serialized");
    }

    private WorkerGeneration generation(WorkerLifecycle lifecycle, List<String> servedTasks, long idleWakeups,
            String leasedTask) {
        return new WorkerGeneration(generationId, role, harness, model, RoleForm.FRESH,
                LaunchRequest.WorkdirKind.JOB_PRIVATE, null, workdir, digest, STARTED_AT,
                JobId.random(JobId.Kind.LLM), new ModelSlots.Lease(generationId, role), lifecycle, leasedTask,
                servedTasks, idleWakeups, ContextTokens.NOT_MEASURED, null);
    }
}
