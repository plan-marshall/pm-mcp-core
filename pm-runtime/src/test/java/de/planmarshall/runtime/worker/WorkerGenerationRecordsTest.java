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
 * null refusal for the generation id. Every value is drawn or fixed, and none sits at a builder default: the plan id,
 * the worktree mark and the wakeup count are drawn non-null and non-zero, so a builder line the test omits either
 * shows in a component that differs or is refused by the record itself. The unmeasured context and the workdir kind
 * are contract values, and the generation id and the plan id are drawn from the generator artifact of
 * {@code pm-runtime}. The record's own {@code equals} compares the generation id alone, so the comparison reads every
 * record component through its accessor: {@link Class#getRecordComponents()} names all eighteen, and each accessor is
 * invoked on the constructed and on the built value, so a component the builder drops or reorders is visible.
 */
@EnableGeneratorController
@DisplayName("WorkerGeneration builder")
class WorkerGenerationRecordsTest {

    @Test
    @DisplayName("the builder builds the worker generation the constructor builds")
    void workerGenerationBuilderEqualsConstructor() throws Exception {
        var generationId = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        var role = Generators.letterStrings(1, 20).next();
        var harness = Generators.letterStrings(1, 20).next();
        var model = Generators.letterStrings(1, 20).next();
        var form = Generators.enumValues(RoleForm.class).next();
        var workdirKind = LaunchRequest.WorkdirKind.PLAN_WORKTREE;
        var planId = RuntimeGenerators.planIds().next();
        var workdir = Path.of("/tmp", Generators.letterStrings(1, 20).next());
        var configurationDigest = RuntimeGenerators.sha256Digests().next();
        var startedAt = Instant.ofEpochSecond(Generators.longs(0, 1_900_000_000).next());
        var jobId = new JobId(generationId);
        var slot = new ModelSlots.Lease(generationId, role);
        var lifecycle = Generators.enumValues(WorkerLifecycle.class).next();
        var leasedTask = Generators.letterStrings(1, 40).next();
        var servedTasks = List.of(Generators.letterStrings(1, 40).next());
        var idleWakeups = Generators.longs(1, 1_000).next();
        var contextTokens = WorkerState.ContextTokens.NOT_MEASURED;
        var worktreeMark = new WorkerState.WorktreeMark(RuntimeGenerators.sha256Digests().next(),
                List.of(Generators.letterStrings(1, 20).next()), startedAt);

        var constructed = new WorkerGeneration(generationId, role, harness, model, form, workdirKind, planId, workdir,
                configurationDigest, startedAt, jobId, slot, lifecycle, leasedTask, servedTasks, idleWakeups,
                contextTokens, worktreeMark);
        var built = WorkerGeneration.builder()
                .generationId(generationId)
                .role(role)
                .harness(harness)
                .model(model)
                .form(form)
                .workdirKind(workdirKind)
                .planId(planId)
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
                .worktreeMark(worktreeMark)
                .build();

        assertEquals(constructed, built, "the builder builds the same worker generation");
        var components = WorkerGeneration.class.getRecordComponents();
        assertEquals(18, components.length, "the generation holds eighteen components");
        for (var component : components) {
            assertEquals(component.getAccessor().invoke(constructed), component.getAccessor().invoke(built),
                    "the builder wires every component of the record");
        }
    }

    @Test
    @DisplayName("the builder refuses a missing generation id like the constructor")
    void workerGenerationBuilderRefusesMissingGenerationId() {
        var builder = WorkerGeneration.builder();
        var refusal = assertThrows(NullPointerException.class, builder::build,
                "the builder names the missing generation");

        assertEquals("generationId", refusal.getMessage(), "the refusal names the generation id");
    }
}
