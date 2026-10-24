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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.runtime.security.JobTokens;

/** The composed launch vector, the tier and effort rendering, the zero-tool isolation and the worker block. */
@DisplayName("Launch composer")
class LaunchComposerTest {

    private static final String GENERATION = "l-0123456789abcdef";

    @TempDir
    Path root;

    private final SkillSource skills = LaunchFixtures.skills();

    @Test
    @DisplayName("a writer launch names the enrolled executable and renders the tier and the narrowed effort")
    void writerVector() {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        var argv = launch.argv();
        assertEquals(LaunchFixtures.enrolmentPath(root).toString(), argv.get(0));
        assertTrue(argv.contains("claude-opus-4-1"));
        assertTrue(argv.contains("high"));
        assertFalse(argv.contains("max"));
        assertEquals("high", launch.effort());
        assertEquals("max", launch.effortNarrowedFrom());
        assertTrue(argv.contains("--mcp-config"));
        assertTrue(argv.contains("read_file,pm_state,pm_do"));
        assertTrue(launch.mcpConfig().orElseThrow().contains("PM_MCP_JOB_TOKEN"));
    }

    @Test
    @DisplayName("the ephemeral flag is withheld for a role that may resume")
    void ephemeralWithheld() {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        assertFalse(launch.argv().stream().anyMatch(argument -> argument.contains("no-session-persistence")));
    }

    @Test
    @DisplayName("the ephemeral flag stays for a role that cannot resume")
    void ephemeralKept() {
        var role = roleWithoutResume(RoleClass.WRITER);
        var launch = compose(role, true, false);
        assertTrue(launch.argv().stream().anyMatch(argument -> argument.contains("no-session-persistence")));
    }

    @Test
    @DisplayName("a read_only launch passes no mcp_config in its own template and no effort of the writer")
    void readOnlyVector() {
        var role = LaunchFixtures.role(RoleClass.READ_ONLY, "standard", null, RoleForm.WARM, skills, List.of(
                LaunchFixtures.ROLE_SKILL));
        var launch = compose(role, false, false);
        assertTrue(launch.argv().contains("claude-sonnet-4"));
        assertFalse(launch.argv().contains("--effort"));
        assertNull(launch.effort());
        assertTrue(launch.mcpConfig().isPresent());
    }

    @Test
    @DisplayName("a zero_tool launch has no tool, no MCP entry and no mcp_config slot")
    void zeroToolVector() {
        var role = LaunchFixtures.role(RoleClass.ZERO_TOOL, "fast", null, RoleForm.FRESH, skills, List.of(
                LaunchFixtures.ROLE_SKILL));
        var launch = compose(role, true, false);
        assertTrue(launch.mcpConfig().isEmpty());
        assertTrue(launch.mcpConfigFile().isEmpty());
        assertFalse(launch.argv().contains("--mcp-config"));
        assertFalse(launch.argv().contains("--allowedTools"));
        assertTrue(launch.argv().contains("--setting-sources"));
        assertTrue(launch.env().values().stream().noneMatch(value -> value.contains("PM_MCP_JOB_TOKEN")));
        assertTrue(launch.workerBlock().member("job_token_sha256").orElseThrow().isNull());
    }

    @Test
    @DisplayName("the job token is minted, only its digest enters the record and the block names the build")
    void tokenAndRecord() {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        assertEquals(JobTokens.sha256Hex(launch.jobToken()), launch.jobTokenSha256());
        assertNotEquals(launch.jobToken(), launch.jobTokenSha256());
        assertEquals(launch.jobTokenSha256(), launch.workerBlock().member("job_token_sha256").orElseThrow().text()
                .orElseThrow());
        assertEquals("1.5.0", launch.workerBlock().member("harness_version").orElseThrow().text().orElseThrow());
        assertEquals(GENERATION, launch.workerBlock().member("generation_id").orElseThrow().text().orElseThrow());
        assertEquals("claude-opus-4-1", launch.workerBlock().member("model").orElseThrow().text().orElseThrow());
    }

    @Test
    @DisplayName("the prompt file is written with the prompt's digest")
    void promptFile() throws Exception {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        assertEquals(launch.prompt().text(), Files.readString(launch.promptFile(), StandardCharsets.UTF_8));
        assertEquals(launch.prompt().digest(), launch.workerBlock().member("prompt_digest").orElseThrow().text()
                .orElseThrow());
        assertTrue(launch.workerBlock().member("delivered_skills").orElseThrow().elements().size() >= 3);
    }

    @Test
    @DisplayName("the zero_tool launch runs in the job-private cwd")
    void zeroToolWorkingDirectory() {
        var role = LaunchFixtures.role(RoleClass.ZERO_TOOL, "fast", null, RoleForm.FRESH, skills, List.of(
                LaunchFixtures.ROLE_SKILL));
        var request = request(role, true, LaunchRequest.WorkdirKind.JOB_PRIVATE, false);
        var launch = new LaunchComposer().compose(request);
        assertEquals(root.resolve("run/jobs").resolve(GENERATION).resolve("cwd").toAbsolutePath().normalize(),
                launch.workingDirectory());
    }

    private WorkerLaunch compose(EffectiveRole role, boolean fresh, boolean freeText) {
        return new LaunchComposer().compose(request(role, fresh, LaunchRequest.WorkdirKind.PLAN_WORKTREE, freeText));
    }

    private LaunchRequest request(EffectiveRole role, boolean fresh, LaunchRequest.WorkdirKind kind,
            boolean freeText) {
        var jobPrivate = root.resolve("run/jobs").resolve(GENERATION);
        var workdir = kind == LaunchRequest.WorkdirKind.JOB_PRIVATE ? null : root.resolve("worktree");
        return new LaunchRequest(role, LaunchFixtures.block(), LaunchFixtures.enrolment(LaunchFixtures
                        .enrolmentPath(root), LaunchFixtures.EXECUTABLE_DIGEST, "1.5.0"), GENERATION,
                root.resolve("runtime"), jobPrivate, kind, kind == LaunchRequest.WorkdirKind.PLAN_WORKTREE
                        ? "29-worker-launch" : null, workdir, root.resolve("daemon.sock"), LaunchFixtures.SHIM, skills,
                fresh, freeText, List.of("skill://pm/state/outline/SKILL.md"), List.of(
                        "skill://pm/ext/domain-java/SKILL.md"), envelope(), 0, "Fixed zero-tool prompt",
                Optional.empty(), LaunchFixtures.clock());
    }

    private EffectiveRole roleWithoutResume(RoleClass roleClass) {
        var roleSkills = List.of(new RoleSkill(LaunchFixtures.ROLE_SKILL, skills.resolve(LaunchFixtures.ROLE_SKILL)
                .orElseThrow().digest()));
        return new EffectiveRole(LaunchFixtures.artifact(roleClass, "frontier", "max", RoleForm.FRESH, List.of(
                LaunchFixtures.ROLE_SKILL)), "claude", "frontier", new EffortValue("max"), roleSkills, "1.5", Optional
                .empty(), new RoleArtifact.Questions(0, 0), Map.of(), 400_000, 3);
    }

    private static ContractEnvelope envelope() {
        return ContractEnvelope.of("plan", "29-worker-launch", "envelope-3", List.of(new LinkSchema("submit:task",
                List.of(new LinkParameter("summary", "text", null)))), JsonDocument.parse(
                "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8), "schema"));
    }
}
