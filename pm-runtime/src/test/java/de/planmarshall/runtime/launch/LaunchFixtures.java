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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleSkill;

/** The fixture profile, role and enrolment of the launch tests. */
final class LaunchFixtures {

    static final String SHIM = "Skills of plan-marshall arrive in-band or are named by a skill://pm/ URI.";

    static final Instant NOW = Instant.parse("2026-10-24T10:00:00Z");

    static final String EXECUTABLE_DIGEST = "a".repeat(64);

    private LaunchFixtures() {
    }

    static LoadedProfile profile() {
        return profile(document());
    }

    static LoadedProfile profile(String document) {
        var bytes = document.getBytes(StandardCharsets.UTF_8);
        return new LoadedProfile("claude", ProfileSetKind.SHIPPED, JsonDocument.parse(bytes, "profile"), bytes,
                LoadedProfile.State.VERIFIED, null);
    }

    static String document() {
        return """
                {
                  "schema_version": 1,
                  "client_id": "claude",
                  "identity": { "version_probe": ["--version"], "version_pattern": "(\\\\d+\\\\.\\\\d+\\\\.\\\\d+)" },
                  "headless": {
                    "executable": "claude",
                    "argv": ["claude", "-p", "--no-session-persistence", "--model", "{{model}}",
                             "--effort", "{{effort}}", "--prompt-file", "{{prompt_file}}",
                             "--mcp-config", "{{mcp_config}}", "--json-schema", "{{result_schema}}",
                             "--allowedTools", "{{tools}}", "--agents", "{{agent}}"],
                    "classes": {
                      "writer": { "args": ["claude", "-p", "--model", "{{model}}", "--effort", "{{effort}}",
                                            "--prompt-file", "{{prompt_file}}", "--mcp-config", "{{mcp_config}}",
                                            "--json-schema", "{{result_schema}}", "--allowedTools", "{{tools}}",
                                            "--agents", "{{agent}}", "--no-session-persistence"],
                                  "tool_set": "enforced" },
                      "read_only": { "args": ["claude", "-p", "--model", "{{model}}", "--prompt-file",
                                              "{{prompt_file}}", "--mcp-config", "{{mcp_config}}",
                                              "--json-schema", "{{result_schema}}", "--allowedTools", "{{tools}}",
                                              "--agents", "{{agent}}", "--no-session-persistence"],
                                     "tool_set": "enforced" },
                      "zero_tool": { "args": ["claude", "-p", "--model", "{{model}}", "--prompt-file",
                                              "{{prompt_file}}", "--setting-sources", "", "--max-turns", "1",
                                              "--no-session-persistence"] }
                    },
                    "agent_mode": "inline",
                    "mcp_config": { "delivery": "slot", "exclusive": true },
                    "env": { "DISABLE_TOOL_SEARCH": "1" },
                    "output": { "format": "stream_json", "result_pointer": "/result" },
                    "model_tiers": { "frontier": "claude-opus-4-1", "standard": "claude-sonnet-4",
                                     "fast": "claude-haiku-3" },
                    "model_efforts": { "*": ["low", "medium", "high"] },
                    "resource_tools": ["resources_list", "resources_read"],
                    "writable_paths": ["~/.claude.json", "~/.claude/sessions"],
                    "data_home": null,
                    "session_residue": "ephemeral",
                    "resumability": { "hold": false,
                                      "resume": { "argv": ["claude", "--resume", "{{session_id}}", "-p",
                                                           "--prompt-file", "{{prompt_file}}"],
                                                  "max_age_seconds": 3600 } },
                    "capabilities": ["result_schema", "ephemeral", "project_settings_control",
                                     "extension_isolation"],
                    "update_evidence": { "method": "code_signature", "signer": "Example Inc" },
                    "supported_versions": ">=1.0.0 <2.0.0"
                  }
                }
                """;
    }

    static HeadlessBlock block() {
        return HeadlessBlock.of(profile());
    }

    static RoleArtifact artifact(RoleClass roleClass, String model, String effort, RoleForm form,
            List<String> skills) {
        var tools = roleClass == RoleClass.ZERO_TOOL
                ? List.<String>of()
                : List.of("read_file", "pm_state", "pm_do");
        return new RoleArtifact(1, "implementer", "the implementer", roleClass, tools, "claude", model,
                new EffortValue(effort), form, 1, skills, null, 60, null, new RoleArtifact.Questions(0, 0),
                Map.of());
    }

    static EffectiveRole role(RoleClass roleClass, String model, String effort, RoleForm form, SkillSource skills,
            List<String> skillUris) {
        var resolved = skillUris.stream().map(uri -> new RoleSkill(uri, skills.resolve(uri).orElseThrow().digest()))
                .toList();
        return new EffectiveRole(artifact(roleClass, model, effort, form, skillUris), "claude", model, new EffortValue(
                effort), resolved, "1.5", Optional.empty(), new RoleArtifact.Questions(0, 3600), Map.of(), 400_000, 3);
    }

    static EffectiveRole writer(SkillSource skills) {
        return role(RoleClass.WRITER, "frontier", "max", RoleForm.FRESH, skills, List.of(ROLE_SKILL));
    }

    static final String ROLE_SKILL = "skill://pm/role/implementer/SKILL.md";

    static SkillSource skills() {
        var skills = new LinkedHashMap<String, String>();
        skills.put(PromptComposer.CORE, "Core conduct: act deliberately.");
        skills.put(PromptComposer.TASK_PROTOCOL, "Task protocol: pm_wait, pm_state, pm_do.");
        skills.put(PromptComposer.WRITING, "Writing: use the project's model language.");
        skills.put(ROLE_SKILL, "Role implementer: implement production code.");
        skills.put("skill://pm/state/outline/SKILL.md", "Outline state.");
        skills.put("skill://pm/ext/domain-java/SKILL.md", "Java domain.");
        var resolved = new LinkedHashMap<String, ResolvedSkill>();
        skills.forEach((uri, text) -> resolved.put(uri, ResolvedSkill.of(uri, text)));
        return uri -> Optional.ofNullable(resolved.get(uri));
    }

    static HarnessEnrolment enrolment(Path executable, String sha256, String version) {
        return new HarnessEnrolment("claude", executable, sha256, version, NOW, NOW,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED,
                        Map.of("input", "reported", "output", "reported")),
                Map.of("text", "reported", "turn", "reported"), Map.of());
    }

    static InstantSource clock() {
        return InstantSource.fixed(NOW);
    }

    /**
     * @param root a temporary root
     * @return the path of the fixture executable, which the composer never reads
     */
    static Path enrolmentPath(Path root) {
        return root.resolve("bin").resolve("claude");
    }
}
