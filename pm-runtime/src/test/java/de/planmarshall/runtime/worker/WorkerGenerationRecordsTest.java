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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link WorkerGeneration} builds the value its canonical constructor builds, including its
 * null refusal for the generation id. Every value is generated; the job-private working directory pair and the
 * unmeasured context are contract values, and the generation id is drawn from the generator artifact of
 * {@code pm-runtime}.
 */
@EnableGeneratorController
@DisplayName("WorkerGeneration builder")
class WorkerGenerationRecordsTest {

    @Test
    @DisplayName("the builder builds the worker generation the constructor builds")
    void workerGenerationBuilderEqualsConstructor() {
        var generationId = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        var role = Generators.letterStrings(1, 20).next();
        var harness = Generators.letterStrings(1, 20).next();
        var model = Generators.letterStrings(1, 20).next();
        var form = Generators.enumValues(RoleForm.class).next();
        var workdirKind = LaunchRequest.WorkdirKind.JOB_PRIVATE;
        var workdir = Path.of("/tmp", Generators.letterStrings(1, 20).next());
        var configurationDigest = RuntimeGenerators.sha256Digests().next();
        var startedAt = Instant.ofEpochSecond(Generators.longs(0, 1_900_000_000).next());
        var jobId = new JobId(generationId);
        var slot = new ModelSlots.Lease(generationId, role);
        var lifecycle = Generators.enumValues(WorkerLifecycle.class).next();
        var leasedTask = Generators.letterStrings(1, 40).next();
        var servedTasks = List.of(Generators.letterStrings(1, 40).next());
        var idleWakeups = Generators.longs(0, 1_000).next();
        var contextTokens = WorkerState.ContextTokens.NOT_MEASURED;

        var constructed = new WorkerGeneration(generationId, role, harness, model, form, workdirKind, null, workdir,
                configurationDigest, startedAt, jobId, slot, lifecycle, leasedTask, servedTasks, idleWakeups,
                contextTokens, null);
        var built = WorkerGeneration.builder()
                .generationId(generationId)
                .role(role)
                .harness(harness)
                .model(model)
                .form(form)
                .workdirKind(workdirKind)
                .planId(null)
                .workdir(workdir)
                .configurationDigest(configurationDigest)
                .startedAt(startedAt)
                .jobId(jobId)
                .slot(slot)
                .lifecycle(lifecycle)
                .leasedTask(leasedTask)
                .servedTasks(servedTasks)
                .idleWakeups(idleWakeups)
                .contextTokens(contextTokens)
                .worktreeMark(null)
                .build();

        assertEquals(constructed, built, "the builder builds the same worker generation");
    }

    @Test
    @DisplayName("the builder refuses a missing generation id like the constructor")
    void workerGenerationBuilderRefusesMissingGenerationId() {
        var refusal = assertThrows(NullPointerException.class, () -> WorkerGeneration.builder().build());

        assertEquals("generationId", refusal.getMessage(), "the refusal names the generation id");
    }
}
