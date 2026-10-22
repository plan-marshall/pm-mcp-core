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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import de.planmarshall.core.profile.EnrolledHarness;

/**
 * Validates a role against its effective values, its harness's client profile and the effective model catalogue
 * (PM-WORK-1, Validation against Schema and Client Profile; PM-CLIENT-2).
 * <p>
 * The checks run in the order of the specification's table: the harness is known and enrolled, the profile is eligible
 * and runs the role's class, the model accepts a restricted tool set where the class is restricted, the effort is one the
 * harness accepts for the model, the model is allowed by the effective catalogue (never for the steward), the tools stay
 * within the class's set and name every PM-MCP tool of the class, a {@code zero_tool} role is fresh, and every skill
 * resolves. A failing check refuses the role with its {@link RoleValidationReason}; the role counts as undefined and never
 * falls back.
 *
 * @since 0.1
 */
public final class RoleValidation {

    /** The PM-MCP tools every role of the class names (PM-WORK-1, Tool Sets). */
    private static final Set<String> REQUIRED_TOOLS = Set.of("pm_wait", "pm_state", "pm_do", "pm_skill",
            "pm_skill_file");

    private static final Set<String> WRITER_TOOLS = Set.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file",
            "pm_build", "pm_lsp");

    private static final Set<String> READ_ONLY_TOOLS = Set.of("pm_wait", "pm_state", "pm_do", "pm_skill", "pm_skill_file",
            "pm_lsp");

    /** Harness tools that no role may name: they start a sub-agent, ask the user, or reach the web. */
    private static final Set<String> FORBIDDEN_TOOLS = Set.of("agent", "task", "spawn_agent", "webfetch", "websearch",
            "web_search", "web_fetch");

    private RoleValidation() {
    }

    /**
     * @param artifact    the validated artifact
     * @param harness     the effective harness
     * @param model       the effective model
     * @param effort      the effective effort
     * @param profile     the facts of the harness's profile, or empty when no profile has the harness
     * @param enrolled    the enrolled build of the harness, or empty when it is not enrolled
     * @param catalogue   the effective model catalogue
     * @param environment the port that resolves the project's skills
     * @param steward     whether the role is the steward, for which {@code model_not_allowed} never applies
     * @return the resolved skills of the role
     * @throws RoleRefusal with the reason of the failing row of the table
     */
    public static List<RoleSkill> check(RoleArtifact artifact, String harness, String model, EffortValue effort,
            Optional<HarnessProfile> profile, Optional<EnrolledHarness> enrolled, Optional<ModelCatalogue> catalogue,
            RoleEnvironment environment, boolean steward) {
        var facts = profile.orElseThrow(() -> invalid(RoleValidationReason.HARNESS_UNKNOWN, artifact,
                "no client profile has the client id '%s'".formatted(harness)));
        enrolled.orElseThrow(() -> invalid(RoleValidationReason.HARNESS_UNKNOWN, artifact,
                "the harness '%s' is not enrolled".formatted(harness)));
        if (!facts.eligible()) {
            throw invalid(RoleValidationReason.HARNESS_NOT_ELIGIBLE, artifact,
                    "the profile of '%s' is not worker-eligible".formatted(harness));
        }
        if (!facts.runs(artifact.roleClass())) {
            throw invalid(RoleValidationReason.HARNESS_CLASS_NOT_RUNNABLE, artifact,
                    "the profile of '%s' runs no %s role".formatted(harness, artifact.roleClass().wireName()));
        }
        if (restricted(facts, artifact.roleClass()) && !facts.allowsRestrictedModel(model)) {
            throw invalid(RoleValidationReason.MODEL_RESTRICTED, artifact,
                    "the model '%s' accepts no restricted tool set on '%s'".formatted(model, harness));
        }
        if (!effortSupported(facts, model, effort)) {
            throw invalid(RoleValidationReason.EFFORT_UNSUPPORTED, artifact,
                    "the harness '%s' does not accept the effort %s for '%s'"
                            .formatted(harness, effort.value() == null ? "null" : effort.value(), model));
        }
        if (!steward) {
            catalogue.ifPresent(allowed -> requireModelAllowed(allowed, artifact, harness, model));
        }
        tools(artifact, facts);
        if (artifact.zeroTool() && artifact.form() == RoleForm.WARM) {
            throw invalid(RoleValidationReason.FORM_NOT_PERMITTED, artifact, "a zero_tool role is always fresh");
        }
        return skills(artifact, environment);
    }

    private static boolean restricted(HarnessProfile profile, RoleClass roleClass) {
        if (!profile.restrictedModelsDeclared()) {
            return false;
        }
        if (roleClass == RoleClass.ZERO_TOOL) {
            return true;
        }
        return profile.toolSet(roleClass).filter(HarnessProfile.ToolSet.ENFORCED::equals).isPresent();
    }

    private static boolean effortSupported(HarnessProfile profile, String model, EffortValue effort) {
        var values = profile.efforts(model);
        var value = effort.value();
        if (value == null) {
            return true;
        }
        if (values.isEmpty()) {
            return false;
        }
        if (values.contains(value)) {
            return true;
        }
        return value.compareTo(values.getFirst()) > 0;
    }

    private static void requireModelAllowed(ModelCatalogue catalogue, RoleArtifact artifact, String harness,
            String model) {
        if (!catalogue.allows(harness, model)) {
            throw invalid(RoleValidationReason.MODEL_NOT_ALLOWED, artifact,
                    "the effective model catalogue does not allow '%s' on '%s'".formatted(model, harness));
        }
    }

    private static void tools(RoleArtifact artifact, HarnessProfile profile) {
        var tools = artifact.tools();
        if (artifact.zeroTool()) {
            if (!tools.isEmpty()) {
                throw invalid(RoleValidationReason.TOOL_NOT_PERMITTED, artifact,
                        "a zero_tool role names the tool '%s'".formatted(tools.getFirst()));
            }
            return;
        }
        var allowed = artifact.roleClass() == RoleClass.WRITER ? WRITER_TOOLS : READ_ONLY_TOOLS;
        for (var required : REQUIRED_TOOLS) {
            if (!tools.contains(required)) {
                throw invalid(RoleValidationReason.TOOL_NOT_PERMITTED, artifact,
                        "the role omits the PM-MCP tool '%s'".formatted(required));
            }
        }
        var askUser = profile.askUserTool() == null ? "" : profile.askUserTool();
        for (var tool : tools) {
            if (tool.equals(askUser)) {
                throw invalid(RoleValidationReason.TOOL_NOT_PERMITTED, artifact,
                        "the role names the tool that asks the user, '%s'".formatted(tool));
            }
            if (FORBIDDEN_TOOLS.contains(tool.toLowerCase(Locale.ROOT))) {
                throw invalid(RoleValidationReason.TOOL_NOT_PERMITTED, artifact,
                        "the role names the forbidden harness tool '%s'".formatted(tool));
            }
            if (tool.startsWith("pm_") && !allowed.contains(tool)) {
                throw invalid(RoleValidationReason.TOOL_NOT_PERMITTED, artifact,
                        "the PM-MCP tool '%s' is outside the %s tool set"
                                .formatted(tool, artifact.roleClass().wireName()));
            }
        }
    }

    private static List<RoleSkill> skills(RoleArtifact artifact, RoleEnvironment environment) {
        var first = artifact.roleSkill();
        if (!first.startsWith("skill://pm/role/") && !first.startsWith("skill://pm/ext/")) {
            throw invalid(RoleValidationReason.SKILL_UNRESOLVED, artifact,
                    "the first skill '%s' is neither a role skill nor an extension skill".formatted(first));
        }
        var resolved = new ArrayList<RoleSkill>();
        for (var uri : artifact.skills()) {
            resolved.add(environment.resolveSkill(uri).orElseThrow(() -> invalid(
                    RoleValidationReason.SKILL_UNRESOLVED, artifact, "the skill '%s' does not resolve".formatted(uri))));
        }
        return List.copyOf(resolved);
    }

    private static RoleRefusal invalid(RoleValidationReason reason, RoleArtifact artifact, String detail) {
        return RoleRefusal.invalid(reason, artifact.name(), "", detail);
    }
}
