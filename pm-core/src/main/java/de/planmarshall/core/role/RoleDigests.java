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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.planmarshall.core.profile.CanonicalJson;
import de.planmarshall.core.profile.JsonNode;

/**
 * The two digests of a role (PM-WORK-1, Role Artifact Schema), both computed over the role's <em>effective</em> values so
 * that a changed assignment or plan override changes them.
 * <p>
 * The <em>configuration digest</em> is the SHA-256 of the canonical JSON (RFC 8785) of the effective artifact with
 * {@code description} removed and its {@code harness}, {@code model} and {@code effort} replaced by the effective values,
 * together with the digests of its skills and the major.minor of the enrolled harness version. It identifies the
 * configuration a worker generation is started with, which offers and recycling compare.
 * <p>
 * The <em>qualification digest</em> is the SHA-256 of the canonical JSON of exactly the values the grading measures: the
 * effective {@code harness}, {@code model} and {@code effort}, the artifact's {@code tools} and {@code class}, the skill
 * digests, the harness major.minor, and, where the harness reports one, the model's weights identity. It identifies the
 * configuration a qualification record is bound to.
 * <p>
 * A change of {@code count}, {@code recycling}, {@code budget_floor_seconds}, {@code context_budget_tokens},
 * {@code questions}, {@code off_track} or {@code form} changes only the configuration digest; a change of
 * {@code description} changes neither.
 *
 * @since 0.1
 */
public final class RoleDigests {

    private static final HexFormat HEX = HexFormat.of();

    private RoleDigests() {
    }

    /**
     * @param role an effective role
     * @return the configuration digest, 64 lower-case hexadecimal digits
     */
    public static String configuration(EffectiveRole role) {
        var input = new LinkedHashMap<String, JsonNode>();
        input.put("artifact", artifact(role));
        input.put("harness_version", JsonNode.string(role.harnessVersion()));
        input.put("skill_digests", skillDigests(role.skills()));
        return digest(JsonNode.object(input));
    }

    /**
     * @param role an effective role
     * @return the qualification digest, 64 lower-case hexadecimal digits
     */
    public static String qualification(EffectiveRole role) {
        var configuration = new LinkedHashMap<String, JsonNode>();
        configuration.put("harness", JsonNode.string(role.harness()));
        configuration.put("harness_version", JsonNode.string(role.harnessVersion()));
        configuration.put("model", JsonNode.string(role.model()));
        configuration.put("effort", effort(role.effort()));
        configuration.put("tools", strings(role.artifact().tools()));
        configuration.put("class", JsonNode.string(role.roleClass().wireName()));
        configuration.put("skill_digests", skillDigests(role.skills()));
        var input = new LinkedHashMap<String, JsonNode>();
        input.put("configuration", JsonNode.object(configuration));
        role.weightsIdentity().ifPresent(identity -> input.put("weights_identity", JsonNode.string(identity)));
        return digest(JsonNode.object(input));
    }

    /**
     * @param role an effective role
     * @return the effective artifact as canonical JSON sees it, without its description
     */
    private static JsonNode artifact(EffectiveRole role) {
        var artifact = role.artifact();
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("format_version", JsonNode.integer(artifact.formatVersion()));
        members.put("name", JsonNode.string(artifact.name()));
        members.put("class", JsonNode.string(artifact.roleClass().wireName()));
        members.put("tools", strings(artifact.tools()));
        members.put("harness", JsonNode.string(role.harness()));
        members.put("model", JsonNode.string(role.model()));
        members.put("effort", effort(role.effort()));
        members.put("form", JsonNode.string(artifact.form().wireName()));
        members.put("count", JsonNode.integer(artifact.count()));
        members.put("skills", strings(artifact.skills()));
        artifact.recyclingBounds().ifPresent(recycling -> members.put("recycling", recycling(recycling)));
        members.put("budget_floor_seconds", JsonNode.integer(artifact.budgetFloorSeconds()));
        artifact.declaredContextBudget()
                .ifPresent(budget -> members.put("context_budget_tokens", JsonNode.integer(budget)));
        artifact.questionBounds().ifPresent(questions -> members.put("questions", questions(questions)));
        if (!artifact.offTrack().isEmpty()) {
            members.put("off_track", offTrack(artifact.offTrack()));
        }
        return JsonNode.object(members);
    }

    private static JsonNode recycling(RoleArtifact.Recycling recycling) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("idle_wakeups", recycling.idleWakeups() == null
                ? JsonNode.nullValue()
                : JsonNode.integer(recycling.idleWakeups()));
        members.put("token_budget", JsonNode.integer(recycling.tokenBudget()));
        members.put("tasks_per_generation", recycling.tasksPerGeneration() == null
                ? JsonNode.nullValue()
                : JsonNode.integer(recycling.tasksPerGeneration()));
        return JsonNode.object(members);
    }

    private static JsonNode questions(RoleArtifact.Questions questions) {
        var members = new LinkedHashMap<String, JsonNode>();
        members.put("hold_seconds", JsonNode.integer(questions.holdSeconds()));
        members.put("resume_within_seconds", JsonNode.integer(questions.resumeWithinSeconds()));
        return JsonNode.object(members);
    }

    private static JsonNode offTrack(Map<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting> offTrack) {
        var settings = new LinkedHashMap<String, JsonNode>();
        for (var entry : new TreeMap<>(offTrack).entrySet()) {
            var members = new LinkedHashMap<String, JsonNode>();
            var threshold = entry.getValue().threshold();
            if (threshold != null) {
                members.put("threshold", JsonNode.decimal(BigDecimal.valueOf(threshold)));
            }
            members.put("action", JsonNode.string(entry.getValue().action().wireName()));
            settings.put(entry.getKey().wireName(), JsonNode.object(members));
        }
        return JsonNode.object(settings);
    }

    private static JsonNode skillDigests(List<RoleSkill> skills) {
        var digests = new LinkedHashMap<String, JsonNode>();
        for (var skill : skills) {
            digests.put(skill.uri(), JsonNode.string(skill.digest()));
        }
        return JsonNode.object(digests);
    }

    private static JsonNode effort(EffortValue effort) {
        return effort.value() == null ? JsonNode.nullValue() : JsonNode.string(effort.value());
    }

    private static JsonNode strings(List<String> values) {
        return JsonNode.array(values.stream().map(JsonNode::string).toList());
    }

    private static String digest(JsonNode input) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(CanonicalJson.write(input).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("The JDK provides no SHA-256", e);
        }
    }
}
