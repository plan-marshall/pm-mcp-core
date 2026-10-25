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

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.launch.ContractEnvelope;
import de.planmarshall.runtime.launch.LaunchRequest;

/**
 * What one start needs beyond the supervisor's {@link SupervisorEnvironment} (PM-WORK-3, Worker Launch): the resolved
 * configuration of the role, the working directory the worker is fixed to, the contract envelope of a fresh worker's
 * one task, and the per-task prompt facts the caller derives from the task.
 *
 * @param role            the resolved effective role
 * @param workdirKind     the kind of the working directory fixed at the launch
 * @param planId          the plan of a plan-worktree launch; {@code null} otherwise
 * @param workdir         the working directory of a plan-worktree or project-checkout launch; {@code null} for the
 *                        private working directory of a {@code zero_tool} worker
 * @param envelope        the contract envelope of a fresh worker; {@code null} for a warm worker
 * @param writesFreeText  whether the task writes free text, derived from the link schemas
 * @param stateSkills     the skills of the task's state
 * @param domainSkills    the project's domain skills
 * @param zeroToolPrompt  the server-fixed prompt of a {@code zero_tool} role; {@code ""} for every other role
 * @param resumeSessionId the stored session a resumed generation continues; empty for a new session
 * @since 0.1
 */
public record WorkerStartRequest(EffectiveRole role, LaunchRequest.WorkdirKind workdirKind, String planId, Path workdir,
        ContractEnvelope envelope, boolean writesFreeText, List<String> stateSkills, List<String> domainSkills,
        String zeroToolPrompt, Optional<String> resumeSessionId) {

    /**
     * @throws NullPointerException     if a required component is {@code null}
     * @throws IllegalArgumentException if the working-directory pair is inconsistent, a fresh worker has no envelope, a
     *                                  fresh worker resumes a stored session, or a warmed worker carries an envelope
     */
    public WorkerStartRequest {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(workdirKind, "workdirKind");
        Objects.requireNonNull(zeroToolPrompt, "zeroToolPrompt");
        Objects.requireNonNull(resumeSessionId, "resumeSessionId");
        stateSkills = List.copyOf(Objects.requireNonNull(stateSkills, "stateSkills"));
        domainSkills = List.copyOf(Objects.requireNonNull(domainSkills, "domainSkills"));
        if ((workdirKind == LaunchRequest.WorkdirKind.PLAN_WORKTREE) != (planId != null)) {
            throw new IllegalArgumentException("Only a plan-worktree start names its plan");
        }
        if ((workdirKind == LaunchRequest.WorkdirKind.JOB_PRIVATE) != (workdir == null)) {
            throw new IllegalArgumentException("A %s start %s a working directory".formatted(workdirKind.wireName(),
                    workdirKind == LaunchRequest.WorkdirKind.JOB_PRIVATE ? "carries no" : "names"));
        }
        var fresh = role.form() == RoleForm.FRESH;
        if (fresh && !role.zeroTool() && envelope == null) {
            throw new IllegalArgumentException("A fresh worker carries the contract envelope of its one task");
        }
        if (!fresh && envelope != null) {
            throw new IllegalArgumentException("A warm worker receives its task's contract with the offer");
        }
        if (fresh && resumeSessionId.isPresent()) {
            throw new IllegalArgumentException("A resumed generation is a warm worker's");
        }
    }
}
