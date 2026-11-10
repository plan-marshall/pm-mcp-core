/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.testing.generators.CoreGenerators;
import de.planmarshall.runtime.launch.LaunchPrompt;
import de.planmarshall.runtime.launch.LaunchRefusal;
import de.planmarshall.runtime.launch.ResolvedSkill;
import de.planmarshall.runtime.launch.WorkerLaunch;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builders of {@link WorkerStart} and {@link WorkerStart.Refusal} build the same value as the
 * canonical constructors, including their null refusal for the launch and the generation id. Every value is generated;
 * the project scope, the floor-default derivation and the empty {@code worker} block are contract values, and the
 * enrolment and the job id are drawn from the generator artifacts of {@code pm-core} and {@code pm-runtime}.
 */
@EnableGeneratorController
@DisplayName("WorkerStart record builders")
class WorkerStartRecordsTest {

    @Test
    @DisplayName("the worker start builder builds the worker start the constructor builds")
    void workerStartBuilderEqualsConstructor() {
        var launch = workerLaunch();
        var enrolment = CoreGenerators.projectEnrolments().next();
        var scope = new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null);
        var budgetKey = "worker:" + Generators.letterStrings(1, 16).next();
        var budgetSeconds = Generators.longs(1, 86_400).next();
        var budgetDerivation = new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null);
        var writablePaths = List.of(Path.of("/tmp", Generators.letterStrings(1, 20).next()));
        var egressProxy = "http://127.0.0.1:" + Generators.integers(1024, 65_535).next();

        var constructed = new WorkerStart(launch, enrolment, scope, budgetKey, budgetSeconds, budgetDerivation,
                writablePaths, egressProxy);
        var built = WorkerStart.builder()
                .launch(launch)
                .enrolment(enrolment)
                .scope(scope)
                .budgetKey(budgetKey)
                .budgetSeconds(budgetSeconds)
                .budgetDerivation(budgetDerivation)
                .writablePaths(writablePaths)
                .egressProxy(egressProxy)
                .build();

        assertEquals(constructed, built, "the builder builds the same worker start");
    }

    @Test
    @DisplayName("the worker start builder refuses a missing launch like the constructor")
    void workerStartBuilderRefusesMissingLaunch() {
        var refusal = assertThrows(NullPointerException.class, () -> WorkerStart.builder().build());

        assertEquals("launch", refusal.getMessage(), "the refusal names the missing launch");
    }

    @Test
    @DisplayName("the refusal builder builds the worker refusal the constructor builds")
    void refusalBuilderEqualsConstructor() {
        var generationId = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        var role = effectiveRole();
        var enrolment = CoreGenerators.projectEnrolments().next();
        var scope = new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null);
        var budgetKey = "worker:" + Generators.letterStrings(1, 16).next();
        var budgetSeconds = Generators.longs(1, 86_400).next();
        var budgetDerivation = new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.OPERATOR, null);
        var workerBlock = "{\"role\":\"" + Generators.letterStrings(1, 16).next() + "\"}";
        var reason = Generators.enumValues(LaunchRefusal.class).next();
        var detail = Generators.letterStrings(1, 40).next();

        var constructed = new WorkerStart.Refusal(generationId, role, enrolment, scope, budgetKey, budgetSeconds,
                budgetDerivation, workerBlock, reason, detail);
        var built = WorkerStart.Refusal.builder()
                .generationId(generationId)
                .role(role)
                .enrolment(enrolment)
                .scope(scope)
                .budgetKey(budgetKey)
                .budgetSeconds(budgetSeconds)
                .budgetDerivation(budgetDerivation)
                .workerBlock(workerBlock)
                .reason(reason)
                .detail(detail)
                .build();

        assertEquals(constructed, built, "the builder builds the same worker refusal");
    }

    @Test
    @DisplayName("the refusal builder refuses a missing generation id like the constructor")
    void refusalBuilderRefusesMissingGenerationId() {
        var enrolment = CoreGenerators.projectEnrolments().next();
        var scope = new JobRecord.Scope(JobRecord.Scope.Type.PROJECT, null);
        var reason = Generators.enumValues(LaunchRefusal.class).next();

        var builder = WorkerStart.Refusal.builder()
                .role(effectiveRole())
                .enrolment(enrolment)
                .scope(scope)
                .budgetKey("worker:" + Generators.letterStrings(1, 16).next())
                .budgetDerivation(new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.OPERATOR, null))
                .workerBlock("{}")
                .reason(reason)
                .detail(Generators.letterStrings(1, 40).next());
        var refusal = assertThrows(NullPointerException.class, builder::build);

        assertEquals("generationId", refusal.getMessage(), "the refusal names the generation id");
    }

    private static WorkerLaunch workerLaunch() {
        var generationId = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        var role = Generators.letterStrings(4, 16).next();
        var text = Generators.letterStrings(1, 40).next();
        var prompt = new LaunchPrompt(text, ResolvedSkill.sha256(text), List.of(), 0);
        return new WorkerLaunch(generationId, role, RoleClass.WRITER, RoleForm.FRESH,
                List.of(Path.of("/usr/bin", role).toString()), Map.of("PATH", "/usr/bin:/bin"),
                Path.of("/tmp", role), Path.of("/tmp", role, "job"), Path.of("/tmp", role, "prompt.md"), prompt, null,
                null, Generators.letterStrings(16, 32).next(), Generators.letterStrings(64, 64).next(), "claude",
                "1.0.0", "frontier", null, null,
                JsonDocument.parse("{}".getBytes(StandardCharsets.UTF_8), "worker"));
    }

    private static EffectiveRole effectiveRole() {
        var name = Generators.letterStrings(4, 16).next();
        var artifact = new RoleArtifact(1, name, Generators.letterStrings(4, 32).next(), RoleClass.WRITER,
                List.of("read_file"), "claude", "frontier", new EffortValue("high"), RoleForm.FRESH, 1,
                List.of("skill://pm/role/" + name + "/SKILL.md"), null, 60, null, new RoleArtifact.Questions(0, 0),
                Map.of());
        return new EffectiveRole(artifact, "claude", "frontier", new EffortValue("high"), List.of(), "1.0", null,
                new RoleArtifact.Questions(0, 0), Map.of(), 400_000, OptionalInt.of(3));
    }
}
