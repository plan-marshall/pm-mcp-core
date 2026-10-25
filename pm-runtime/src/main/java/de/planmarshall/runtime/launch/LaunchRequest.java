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
import java.time.InstantSource;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.EffectiveRole;

/**
 * What the composer needs to compose one worker launch. Everything in it is server-owned: the effective configuration
 * of the role, the harness's profile facts and verified enrolment, the job-private directory, the prompt's skills, and
 * the contract envelope of a fresh worker's one task.
 *
 * @param role                the effective configuration of the role
 * @param block               the launch contract of the harness's profile
 * @param enrolment           the verified enrolment of the harness build
 * @param generationId        the worker's generation, equal to its job id
 * @param runtimeHome         the runtime's installation home, which holds the {@code pm-mcp} binary
 * @param jobPrivateDirectory the job-private directory {@code <base>/run/jobs/<job_id>/}
 * @param workdirKind         the kind of the working directory
 * @param planId              the plan of a plan-worktree launch; {@code null} otherwise
 * @param workdir             the working directory of a plan-worktree or project-checkout launch;
 *                            {@code null} for a job-private launch
 * @param relaySocket         the daemon's socket the relay connects to
 * @param shimParagraph       the paragraph of the shim skill of the client bundle
 * @param skills              where the prompt's skills are read
 * @param fresh               whether the worker is fresh
 * @param writesFreeText      whether the task writes free text, derived from the link schemas
 * @param stateSkills         the skills of the task's state
 * @param domainSkills        the project's domain skills
 * @param envelope            the contract envelope of a fresh worker; {@code null} for a warm worker
 * @param inBandBoundChars    the in-band bound; {@code 0} for no bound
 * @param zeroToolPrompt      the server-fixed prompt of a {@code zero_tool} role
 * @param resumeSessionId     the stored session a resumed generation continues; empty for a new session
 * @param clock               the clock of the spawn instants
 * @since 0.1
 */
public record LaunchRequest(EffectiveRole role, HeadlessBlock block, HarnessEnrolment enrolment, String generationId,
Path runtimeHome, Path jobPrivateDirectory, WorkdirKind workdirKind, String planId, Path workdir,
Path relaySocket, String shimParagraph, SkillSource skills, boolean fresh, boolean writesFreeText,
List<String> stateSkills, List<String> domainSkills, ContractEnvelope envelope, int inBandBoundChars,
String zeroToolPrompt, Optional<String> resumeSessionId, InstantSource clock) {

    /** Copies the lists and validates the working-directory pair. */
    public LaunchRequest {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(block, "block");
        Objects.requireNonNull(enrolment, "enrolment");
        Objects.requireNonNull(generationId, "generationId");
        Objects.requireNonNull(runtimeHome, "runtimeHome");
        Objects.requireNonNull(jobPrivateDirectory, "jobPrivateDirectory");
        Objects.requireNonNull(workdirKind, "workdirKind");
        Objects.requireNonNull(relaySocket, "relaySocket");
        Objects.requireNonNull(shimParagraph, "shimParagraph");
        Objects.requireNonNull(skills, "skills");
        stateSkills = List.copyOf(Objects.requireNonNull(stateSkills, "stateSkills"));
        domainSkills = List.copyOf(Objects.requireNonNull(domainSkills, "domainSkills"));
        Objects.requireNonNull(zeroToolPrompt, "zeroToolPrompt");
        Objects.requireNonNull(resumeSessionId, "resumeSessionId");
        Objects.requireNonNull(clock, "clock");
        if (workdirKind == WorkdirKind.JOB_PRIVATE ? workdir != null : workdir == null) {
            throw new IllegalArgumentException("A %s launch %s a working directory".formatted(workdirKind
                    .wireName(), workdirKind == WorkdirKind.JOB_PRIVATE ? "carries no" : "names"));
        }
        if ((workdirKind == WorkdirKind.PLAN_WORKTREE) != (planId != null)) {
            throw new IllegalArgumentException("Only a plan-worktree launch names its plan");
        }
    }

    /** The kind of the working directory fixed at the launch. */
    public enum WorkdirKind {
        /** A plan worktree. */
        PLAN_WORKTREE("plan_worktree"),
        /** The enrolled project checkout. */
        PROJECT_CHECKOUT("project_checkout"),
        /** The empty {@code cwd/} of the job-private directory. */
        JOB_PRIVATE("job_private");

        private final String wireName;

        WorkdirKind(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the kind in the worker block */
        public String wireName() {
            return wireName;
        }
    }
}
