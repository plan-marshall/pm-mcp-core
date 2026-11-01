/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The effective configuration of one role (PM-WORK-1, Role Assignment): the artifact's other members, and the harness,
 * model and effort taken from the shipped artifact, the machine artifact, the machine's role assignment, the project
 * artifact, the project's role assignment and the plan's override, each later level overriding only what it names.
 * <p>
 * The effective values are read afresh at every composition, task creation and offer ({@link RoleCatalog}); a running
 * generation keeps the configuration digest it was started with. The continuation bounds are cut to what the harness
 * declares, an absent off-track signal takes its compiled threshold and the action {@code show}, and the context budget
 * falls back to the harness and then to the compiled {@value #COMPILED_CONTEXT_BUDGET}.
 *
 * @param artifact           the effective artifact
 * @param harness            the effective harness
 * @param model              the effective model
 * @param effort             the effective effort
 * @param skills             the role's skills as the project resolves them
 * @param harnessVersion     the major.minor of the enrolled harness version
 * @param weightsIdentity    the weights identity of the model where the harness reports one
 * @param questions          the continuation bounds cut to the harness's resumability; {@code 0} is ineffective
 * @param offTrack           every off-track signal with its effective threshold and action
 * @param contextBudgetTokens the effective context budget of a worker
 * @param idleWakeups        the effective idle wakeups of a warm worker; {@code null} when neither sets it
 * @since 0.1
 */
public record EffectiveRole(RoleArtifact artifact, String harness, String model, EffortValue effort,
List<RoleSkill> skills, String harnessVersion, Optional<String> weightsIdentity,
RoleArtifact.Questions questions, Map<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting> offTrack,
int contextBudgetTokens, Integer idleWakeups) {

    /** The compiled context budget when neither the artifact nor the harness sets one. */
    public static final int COMPILED_CONTEXT_BUDGET = 400_000;

    /** Copies the collections. */
    public EffectiveRole {
        Objects.requireNonNull(artifact, "artifact");
        Objects.requireNonNull(harness, "harness");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(effort, "effort");
        Objects.requireNonNull(questions, "questions");
        Objects.requireNonNull(weightsIdentity, "weightsIdentity");
        skills = List.copyOf(skills);
        offTrack = Collections.unmodifiableMap(new LinkedHashMap<>(offTrack));
    }

    /**
     * Builds the effective role from its effective values and the harness's facts.
     *
     * @param artifact        the effective artifact
     * @param harness         the effective harness
     * @param model           the effective model
     * @param effort          the effective effort
     * @param skills          the resolved skills
     * @param harnessVersion  the major.minor of the enrolled harness version
     * @param weightsIdentity the weights identity of the model, where reported
     * @param profile         the facts of the harness's profile
     * @return the effective role
     */
    public static EffectiveRole of(RoleArtifact artifact, String harness, String model, EffortValue effort,
            List<RoleSkill> skills, String harnessVersion, Optional<String> weightsIdentity, HarnessProfile profile) {
        return new EffectiveRole(artifact, harness, model, effort, skills, harnessVersion, weightsIdentity,
                questions(artifact, profile), offTrack(artifact), contextBudget(artifact, profile),
                idleWakeups(artifact, profile).orElse(null));
    }

    /** @return whether the role has no tool and no PM-MCP connection */
    public boolean zeroTool() {
        return artifact.zeroTool();
    }

    /** @return what the role may do */
    public RoleClass roleClass() {
        return artifact.roleClass();
    }

    /** @return the worker form of the role */
    public RoleForm form() {
        return artifact.form();
    }

    /** @return the configuration digest of the role */
    public String configurationDigest() {
        return RoleDigests.configuration(this);
    }

    /** @return the qualification digest of the role */
    public String qualificationDigest() {
        return RoleDigests.qualification(this);
    }

    /**
     * @param role     the role's artifact
     * @param profile  the facts of the harness's profile
     * @return the continuation bounds, cut to what the harness declares
     */
    private static RoleArtifact.Questions questions(RoleArtifact role, HarnessProfile profile) {
        var declared = role.questionBounds().orElse(new RoleArtifact.Questions(0, 0));
        var resumability = profile.resumability();
        var hold = resumability.hold() && resumability.maxHoldSeconds() != null
                ? Math.min(declared.holdSeconds(), resumability.maxHoldSeconds())
                : 0;
        var resume = resumability.resume() && resumability.maxResumeAgeSeconds() != null
                ? Math.min(declared.resumeWithinSeconds(), resumability.maxResumeAgeSeconds())
                : 0;
        return new RoleArtifact.Questions(hold, resume);
    }

    private static Map<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting> offTrack(RoleArtifact role) {
        var settings = new LinkedHashMap<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting>();
        for (var signal : RoleArtifact.OffTrackSignal.values()) {
            settings.put(signal, role.offTrack(signal)
                    .orElse(new RoleArtifact.OffTrackSetting(null, RoleArtifact.OffTrackAction.SHOW)));
        }
        return settings;
    }

    private static int contextBudget(RoleArtifact role, HarnessProfile profile) {
        return role.declaredContextBudget()
                .or(() -> Optional.ofNullable(profile.contextBudgetTokens()))
                .orElse(COMPILED_CONTEXT_BUDGET);
    }

    private static Optional<Integer> idleWakeups(RoleArtifact role, HarnessProfile profile) {
        return role.recyclingBounds()
                .map(RoleArtifact.Recycling::idleWakeups)
                .or(() -> Optional.ofNullable(profile.idleWakeups()));
    }
}
