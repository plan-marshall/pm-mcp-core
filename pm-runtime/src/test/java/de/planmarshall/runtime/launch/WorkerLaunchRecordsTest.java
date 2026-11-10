/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link WorkerLaunch} builds the value its canonical constructor builds, including its null
 * refusal for the generation id. Every value is generated; the launch carries no MCP configuration file and no
 * narrowed effort, and its prompt is built from generated text and a generated skill.
 */
@EnableGeneratorController
@DisplayName("WorkerLaunch builder")
class WorkerLaunchRecordsTest {

    @Test
    @DisplayName("the builder builds the worker launch the constructor builds")
    void workerLaunchBuilderEqualsConstructor() {
        var generationId = Generators.letterStrings(4, 20).next();
        var role = Generators.letterStrings(1, 20).next();
        var roleClass = RoleClass.WRITER;
        var form = RoleForm.FRESH;
        var argv = List.of("claude", "--print");
        var env = Map.of("DISABLE_TOOL_SEARCH", "1");
        var workingDirectory = Path.of("/tmp", Generators.letterStrings(1, 12).next(), "worktree");
        var jobPrivateDirectory = Path.of("/tmp", Generators.letterStrings(1, 12).next(), "jobs", generationId);
        var promptFile = jobPrivateDirectory.resolve("prompt.md");
        var promptText = Generators.letterStrings(10, 40).next();
        var prompt = new LaunchPrompt(promptText, ResolvedSkill.sha256(promptText),
                List.of(RuntimeGenerators.resolvedSkills().next()), 0);
        var jobToken = Generators.letterStrings(10, 40).next();
        var jobTokenSha256 = RuntimeGenerators.sha256Digests().next();
        var harness = "claude";
        var harnessVersion = "1.5.0";
        var model = "claude-opus-4-1";
        var effort = "high";
        var workerBlock = JsonNode.object(Map.of("harness", JsonNode.string(harness)));

        var constructed = new WorkerLaunch(generationId, role, roleClass, form, argv, env, workingDirectory,
                jobPrivateDirectory, promptFile, prompt, null, null, jobToken, jobTokenSha256, harness, harnessVersion,
                model, effort, null, workerBlock);
        var built = WorkerLaunch.builder()
                .generationId(generationId)
                .role(role)
                .roleClass(roleClass)
                .form(form)
                .argv(argv)
                .env(env)
                .workingDirectory(workingDirectory)
                .jobPrivateDirectory(jobPrivateDirectory)
                .promptFile(promptFile)
                .prompt(prompt)
                .mcpConfigFile(null)
                .mcpConfig(null)
                .jobToken(jobToken)
                .jobTokenSha256(jobTokenSha256)
                .harness(harness)
                .harnessVersion(harnessVersion)
                .model(model)
                .effort(effort)
                .effortNarrowedFrom(null)
                .workerBlock(workerBlock)
                .build();

        assertEquals(constructed, built, "the builder builds the same worker launch");
    }

    @Test
    @DisplayName("the builder refuses a missing generation id like the constructor")
    void workerLaunchBuilderRefusesMissingGenerationId() {
        var refusal = assertThrows(NullPointerException.class, () -> WorkerLaunch.builder().build());

        assertEquals("generationId", refusal.getMessage(), "the refusal names the generation id");
    }
}
