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

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;

/**
 * One composed worker launch, before anything of it is spawned (PM-WORK-1, Launch Contract): the argument vector, the
 * environment, the working directory and the job-private directory, the prompt file and its digest, the per-call MCP
 * configuration, the minted job token with the digest the job record keeps, and the {@code worker} block the job record
 * carries. Nothing in it is read at launch from a file a model can write.
 *
 * @param generationId     the worker's generation, equal to its job id
 * @param role             the role the worker serves
 * @param roleClass        the class of the role
 * @param form             the worker's form
 * @param argv             the composed argument vector; its first element is the enrolled executable
 * @param env              the launch environment of the harness
 * @param workingDirectory the working directory fixed at the launch
 * @param jobPrivateDirectory the job-private directory {@code <base>/run/jobs/<job_id>/}
 * @param promptFile       the composed prompt file
 * @param prompt           the composed prompt with its digest and delivered skills
 * @param mcpConfigFile    the per-call MCP configuration when the profile delivers it as a file
 * @param mcpConfig        the per-call MCP configuration as JSON; empty for a {@code zero_tool} worker
 * @param jobToken         the minted job token; never stored
 * @param jobTokenSha256   the digest of the job token, as the job record keeps it
 * @param harness          the client id of the harness
 * @param harnessVersion   the version probed at the spawn
 * @param model            the model passed to the harness
 * @param effort           the effort passed after narrowing; {@code null} when none was passed
 * @param effortNarrowedFrom the role's effective effort when it was narrowed; {@code null} otherwise
 * @param workerBlock      the {@code worker} block of the job record
 * @since 0.1
 */
public record WorkerLaunch(String generationId, String role, RoleClass roleClass, RoleForm form, List<String> argv,
Map<String, String> env, Path workingDirectory, Path jobPrivateDirectory, Path promptFile, LaunchPrompt prompt,
Optional<Path> mcpConfigFile, Optional<String> mcpConfig, String jobToken, String jobTokenSha256, String harness,
String harnessVersion, String model, String effort, String effortNarrowedFrom, JsonNode workerBlock) {

    /** Copies the collections. */
    public WorkerLaunch {
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(roleClass, "roleClass");
        Objects.requireNonNull(form, "form");
        argv = List.copyOf(Objects.requireNonNull(argv, "argv"));
        if (argv.isEmpty()) {
            throw new IllegalArgumentException("A worker launch has at least the executable");
        }
        env = Map.copyOf(Objects.requireNonNull(env, "env"));
        Objects.requireNonNull(workingDirectory, "workingDirectory");
        Objects.requireNonNull(jobPrivateDirectory, "jobPrivateDirectory");
        Objects.requireNonNull(promptFile, "promptFile");
        Objects.requireNonNull(prompt, "prompt");
        Objects.requireNonNull(mcpConfigFile, "mcpConfigFile");
        Objects.requireNonNull(mcpConfig, "mcpConfig");
        Objects.requireNonNull(jobToken, "jobToken");
        Objects.requireNonNull(jobTokenSha256, "jobTokenSha256");
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(harnessVersion, "harnessVersion");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(workerBlock, "workerBlock");
    }

    /** @return the delivered skills of the launch prompt, which start the generation's delivery record */
    public List<ResolvedSkill> deliveredSkills() {
        return prompt.delivered();
    }
}
