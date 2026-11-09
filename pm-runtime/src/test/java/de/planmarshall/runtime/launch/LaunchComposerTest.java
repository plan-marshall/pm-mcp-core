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

import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.planIds;
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.shortWords;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
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
import de.planmarshall.core.store.JobId;
import de.planmarshall.runtime.security.JobTokens;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The composed launch vector, the tier and effort rendering, the zero-tool isolation and the worker block.
 *
 * <p>
 * The generation id, the enrolled harness version, the plan and envelope ids, the zero-tool prompt, the link and
 * parameter names, the operator-added client id, the unmapped tier and the endpoint origin and credential key are
 * generated. The literals that stay are the contract: the composed command-line format (flags, tier models, effort
 * values and the tool list the fixture profile renders), the worker block's member names and wire values
 * ({@code job_private}, {@code lost}, {@code writer}), the MCP config's server name {@code pm-job} and its token
 * variable {@code PM_MCP_JOB_TOKEN}, the runtime layout below the root ({@code run/jobs}, {@code runtime/bin/pm-mcp}),
 * the fixture role {@code implementer}, the skill URIs and the parameter type {@code text}, and the effort ranks the
 * narrowing is tested over.
 */
@EnableGeneratorController
@DisplayName("Launch composer")
class LaunchComposerTest {

    @TempDir
    Path root;

    private final SkillSource skills = LaunchFixtures.skills();

    private String generation;
    private String harnessVersion;

    @BeforeEach
    void setUp() {
        generation = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        harnessVersion = "1." + Generators.integers(0, 99).next() + "." + Generators.integers(0, 99).next();
    }

    @Test
    @DisplayName("a writer launch names the enrolled executable and renders the tier and the narrowed effort")
    void writerVector() {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        var argv = launch.argv();
        assertEquals(LaunchFixtures.enrolmentPath(root).toString(), argv.get(0),
                "the vector starts with the enrolled executable");
        assertTrue(argv.contains("claude-opus-4-1"), "the vector carries the tier's model");
        assertTrue(argv.contains("high"), "the vector carries the narrowed effort");
        assertFalse(argv.contains("max"), "the vector never carries the role's max effort");
        assertEquals("high", launch.effort(), "the launch's effort is the narrowed value");
        assertEquals("max", launch.effortNarrowedFrom(), "the launch names the effort it narrowed");
        assertTrue(argv.contains("--mcp-config"), "the writer vector names its MCP config");
        assertTrue(argv.contains("read_file,pm_state,pm_do,resources_list,resources_read"),
                "the vector carries the role's and resource tools");
        assertTrue(launch.mcpConfig().orElseThrow().contains("PM_MCP_JOB_TOKEN"),
                "the MCP config carries the job token variable");
    }

    @Test
    @DisplayName("the ephemeral flag is withheld for a role that may resume")
    void ephemeralWithheld() {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        assertFalse(launch.argv().stream().anyMatch(argument -> argument.contains("no-session-persistence")),
                "a resumable role keeps its session");
    }

    @Test
    @DisplayName("the ephemeral flag stays for a role that cannot resume")
    void ephemeralKept() {
        var role = roleWithoutResume(RoleClass.WRITER);
        var launch = compose(role, true, false);
        assertTrue(launch.argv().stream().anyMatch(argument -> argument.contains("no-session-persistence")),
                "a role that cannot resume runs ephemeral");
    }

    @Test
    @DisplayName("a read_only launch passes no mcp_config in its own template and no effort of the writer")
    void readOnlyVector() {
        var role = LaunchFixtures.role(RoleClass.READ_ONLY, "standard", null, RoleForm.WARM, skills, List.of(
                LaunchFixtures.ROLE_SKILL));
        var launch = compose(role, false, false);
        assertTrue(launch.argv().contains("claude-sonnet-4"), "the read-only vector renders its tier");
        assertFalse(launch.argv().contains("--effort"), "the read-only vector carries no effort");
        assertNull(launch.effort(), "the read-only launch has no effort");
        assertTrue(launch.mcpConfig().isPresent(), "the read-only launch still has an MCP config");
    }

    @Test
    @DisplayName("a zero_tool launch has no tool, no MCP entry and no mcp_config slot")
    void zeroToolVector() {
        var role = LaunchFixtures.role(RoleClass.ZERO_TOOL, "fast", null, RoleForm.FRESH, skills, List.of(
                LaunchFixtures.ROLE_SKILL));
        var launch = compose(role, true, false);
        assertTrue(launch.mcpConfig().isEmpty(), "a zero-tool launch has no MCP config");
        assertTrue(launch.mcpConfigFile().isEmpty(), "a zero-tool launch writes no MCP config file");
        assertFalse(launch.argv().contains("--mcp-config"), "the zero-tool vector names no MCP config");
        assertFalse(launch.argv().contains("--allowedTools"), "the zero-tool vector allows no tool");
        assertTrue(launch.argv().contains("--setting-sources"), "the zero-tool vector isolates the settings");
        assertTrue(launch.env().values().stream().noneMatch(value -> value.contains("PM_MCP_JOB_TOKEN")),
                "the zero-tool environment carries no job token");
        assertTrue(launch.workerBlock().member("job_token_sha256").orElseThrow().isNull(),
                "the zero-tool worker block has no token digest");
        assertTrue(launch.jobToken().isEmpty(), "no job token is minted for a zero-tool launch");
        assertTrue(launch.jobTokenSha256().isEmpty(), "no token digest exists for a zero-tool launch");
    }

    @Test
    @DisplayName("a zero_tool launch runs in the job-private cwd even when the request names a worktree")
    void zeroToolForcesJobPrivate() {
        var role = LaunchFixtures.role(RoleClass.ZERO_TOOL, "fast", null, RoleForm.FRESH, skills, List.of(
                LaunchFixtures.ROLE_SKILL));
        var launch = new LaunchComposer().compose(request(role, true, LaunchRequest.WorkdirKind.PLAN_WORKTREE, false));
        assertEquals(root.resolve("run/jobs").resolve(generation).resolve("cwd").toAbsolutePath().normalize(),
                launch.workingDirectory(), "the zero-tool launch runs in the private cwd");
        assertEquals("job_private", launch.workerBlock().member("workdir").orElseThrow().member("kind").orElseThrow()
                .text().orElseThrow(), "the worker block names the private workdir");
    }

    @Test
    @DisplayName("a warm launch drops the flag of an absent result schema instead of leaving it dangling")
    void absentSlotDropsItsFlag() {
        var launch = compose(LaunchFixtures.writer(skills), false, false);
        assertFalse(launch.argv().contains("--json-schema"), "an absent result schema drops its flag");
    }

    @Test
    @DisplayName("the job token is minted, only its digest enters the record and the block names the build")
    void tokenAndRecord() {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        assertEquals(JobTokens.sha256Hex(launch.jobToken()), launch.jobTokenSha256(),
                "the recorded digest is the token's SHA-256");
        assertNotEquals(launch.jobToken(), launch.jobTokenSha256(), "the token itself is never recorded");
        assertEquals(launch.jobTokenSha256(), launch.workerBlock().member("job_token_sha256").orElseThrow().text()
                .orElseThrow(), "the worker block carries the token digest");
        assertEquals(harnessVersion, launch.workerBlock().member("harness_version").orElseThrow().text()
                .orElseThrow(), "the worker block names the harness version");
        assertEquals(generation, launch.workerBlock().member("generation_id").orElseThrow().text().orElseThrow(),
                "the worker block names the generation");
        assertEquals("claude-opus-4-1", launch.workerBlock().member("model").orElseThrow().text().orElseThrow(),
                "the worker block names the rendered model");
        assertEquals(3, launch.workerBlock().member("idle_wakeups").orElseThrow().integerValue().orElseThrow(),
                "the worker block carries the effective idle wake-up bound");
    }

    @Test
    @DisplayName("an absent idle-wakeup bound is written as zero")
    void absentIdleWakeupsWritesZero() {
        var role = LaunchFixtures.roleWithoutIdleWakeups(RoleClass.WRITER, "frontier", "max", RoleForm.FRESH, skills,
                List.of(LaunchFixtures.ROLE_SKILL));

        var launch = compose(role, true, false);

        assertEquals(0, launch.workerBlock().member("idle_wakeups").orElseThrow().integerValue().orElseThrow(),
                "an absent idle wake-up bound is written as zero");
    }

    @Test
    @DisplayName("the prompt file is written with the prompt's digest")
    void promptFile() throws Exception {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        assertEquals(launch.prompt().text(), Files.readString(launch.promptFile(), StandardCharsets.UTF_8),
                "the prompt file holds the composed prompt");
        assertEquals(launch.prompt().digest(), launch.workerBlock().member("prompt_digest").orElseThrow().text()
                .orElseThrow(), "the worker block carries the prompt digest");
        assertTrue(launch.workerBlock().member("delivered_skills").orElseThrow().elements().size() >= 3,
                "the worker block lists the delivered skills");
    }

    @Test
    @DisplayName("the zero_tool launch runs in the job-private cwd")
    void zeroToolWorkingDirectory() {
        var role = LaunchFixtures.role(RoleClass.ZERO_TOOL, "fast", null, RoleForm.FRESH, skills, List.of(
                LaunchFixtures.ROLE_SKILL));
        var request = request(role, true, LaunchRequest.WorkdirKind.JOB_PRIVATE, false);
        var launch = new LaunchComposer().compose(request);
        assertEquals(root.resolve("run/jobs").resolve(generation).resolve("cwd").toAbsolutePath().normalize(),
                launch.workingDirectory(), "the zero-tool launch runs in the private cwd");
    }

    @Test
    @DisplayName("a writer launch carries only the profile's environment and the job relay as an MCP server")
    void extensionIsolation() {
        var launch = compose(LaunchFixtures.writer(skills), true, false);
        var config = JsonDocument.parse(launch.mcpConfig().orElseThrow().getBytes(
                StandardCharsets.UTF_8), "mcp");
        var servers = config.member("mcpServers").orElseThrow();
        var relay = servers.member("pm-job").orElseThrow();
        assertAll(
                () -> assertEquals(Map.of("DISABLE_TOOL_SEARCH", "1"), launch.env(),
                        "only the profile's environment is passed"),
                () -> assertEquals(Set.of("pm-job"), servers.members().keySet(),
                        "the job relay is the only MCP server"),
                () -> assertEquals(root.resolve("runtime/bin/pm-mcp").toAbsolutePath().normalize().toString(),
                        relay.member("command").orElseThrow().text().orElseThrow(),
                        "the relay runs the runtime's pm-mcp binary"),
                () -> assertTrue(relay.member("env").orElseThrow().members().keySet().contains("PM_MCP_JOB_TOKEN"),
                        "the relay's environment carries the token"),
                () -> assertFalse(launch.argv().contains("--setting-sources"),
                        "the writer vector does not isolate settings"));
    }

    @Test
    @DisplayName("a refusal block names the role's class and is marked a start failure")
    void refusalBlock() {
        var block = LaunchComposer.refusalBlock(LaunchFixtures.writer(skills), generation, LaunchFixtures.now());
        assertAll(
                () -> assertEquals("implementer", block.member("role").orElseThrow().text().orElseThrow(),
                        "the refusal block names the role"),
                () -> assertEquals("writer", block.member("class").orElseThrow().text().orElseThrow(),
                        "the refusal block names the role's class"),
                () -> assertEquals("lost", block.member("state").orElseThrow().text().orElseThrow(),
                        "the refused worker's state is lost"),
                () -> assertTrue(block.member("start_failure").orElseThrow().booleanValue().orElseThrow(),
                        "the refusal block marks a start failure"),
                () -> assertTrue(block.member("job_token_sha256").orElseThrow().isNull(),
                        "a refused worker has no token digest"));
    }

    @Test
    @DisplayName("an unmapped tier is passed through as the model identifier")
    void unmappedTier() {
        var model = "model-" + shortWords().next();
        assertEquals(model, LaunchComposer.renderModel(model, LaunchFixtures.block()),
                "an unmapped tier is passed through as model");
    }

    @Test
    @DisplayName("an effort narrows by its rank, never to a higher value")
    void effortNarrowsByRank() {
        assertAll(
                () -> assertEquals("low", LaunchComposer.narrow(List.of("low", "high"), "medium").value(),
                        "medium narrows down to low, never up"),
                () -> assertEquals("high", LaunchComposer.narrow(List.of("low", "high"), "max").value(),
                        "max narrows to the highest accepted value"),
                () -> assertEquals("medium", LaunchComposer.narrow(List.of("low", "medium"), "medium").value(),
                        "an accepted effort is kept as it is"),
                () -> assertNull(LaunchComposer.narrow(List.of(), "high").value(),
                        "no accepted effort narrows to no value"),
                () -> assertEquals("high", LaunchComposer.narrow(List.of(), "high").narrowedFrom(),
                        "the narrowing names the effort it dropped"),
                () -> assertNull(LaunchComposer.narrow(List.of("low"), null).value(),
                        "no requested effort narrows to no value"));
    }

    @Test
    @DisplayName("a harness the operator added from a verified profile is launched like a shipped one")
    void operatorAddedHarness() {
        var client = "c" + shortWords().next() + "-cli";
        var profile = LaunchFixtures.profileForClient(client);
        var block = HeadlessBlock.of(profile);
        var role = LaunchFixtures.roleWithHarness(client, skills);
        var jobPrivate = root.resolve("run/jobs").resolve(generation);
        var request = new LaunchRequest(role, block, LaunchFixtures.enrolment(LaunchFixtures.enrolmentPath(root),
                        LaunchFixtures.executableDigest(), harnessVersion), generation, root.resolve("runtime"),
                jobPrivate, LaunchRequest.WorkdirKind.PROJECT_CHECKOUT, null, root.resolve("worktree"),
                root.resolve("daemon.sock"), LaunchFixtures.SHIM, skills, true, false, List.of(), List.of(), null, 0,
                "", Optional.empty(), LaunchFixtures.clock());
        var launch = new LaunchComposer().compose(request);
        assertEquals(client, launch.workerBlock().member("harness").orElseThrow().text().orElseThrow(),
                "the worker block names the operator's harness");
        assertEquals(client, launch.harness(), "the launch names the operator's harness");
    }

    private WorkerLaunch compose(EffectiveRole role, boolean fresh, boolean freeText) {
        return new LaunchComposer().compose(request(role, fresh, LaunchRequest.WorkdirKind.PLAN_WORKTREE, freeText));
    }

    @Test
    @DisplayName("an endpoint-form enrolment composes no worker launch")
    void endpointEnrolmentComposesNoWorker() {
        var role = roleWithoutResume(RoleClass.ZERO_TOOL);
        var enrolled = HarnessEnrolment.endpoint("claude", "https://" + shortWords().next() + ".example.test",
                shortWords().next(), LaunchFixtures.clock().instant(), new HarnessEnrolment.UsageReport(
                        HarnessEnrolment.UsageSource.DECLARED, Map.of()));
        var request = request(role, true, LaunchRequest.WorkdirKind.PLAN_WORKTREE, false, enrolled);

        assertThrows(LaunchComposer.CompositionException.class, () -> new LaunchComposer().compose(request),
                "an endpoint enrolment composes no worker");
    }

    private LaunchRequest request(EffectiveRole role, boolean fresh, LaunchRequest.WorkdirKind kind,
            boolean freeText) {
        return request(role, fresh, kind, freeText, LaunchFixtures.enrolment(LaunchFixtures
                .enrolmentPath(root), LaunchFixtures.executableDigest(), harnessVersion));
    }

    private LaunchRequest request(EffectiveRole role, boolean fresh, LaunchRequest.WorkdirKind kind,
            boolean freeText, HarnessEnrolment enrolment) {
        var jobPrivate = root.resolve("run/jobs").resolve(generation);
        var workdir = kind == LaunchRequest.WorkdirKind.JOB_PRIVATE ? null : root.resolve("worktree");
        var planId = planIds().next();
        var zeroToolPrompt = Generators.letterStrings(2, 10).next() + " " + Generators.letterStrings(2, 10).next()
                + ".";
        return new LaunchRequest(role, LaunchFixtures.block(), enrolment, generation,
                root.resolve("runtime"), jobPrivate, kind, kind == LaunchRequest.WorkdirKind.PLAN_WORKTREE
                        ? planId : null, workdir, root.resolve("daemon.sock"), LaunchFixtures.SHIM, skills,
                fresh, freeText, List.of("skill://pm/state/outline/SKILL.md"), List.of(
                        "skill://pm/ext/domain-java/SKILL.md"), envelope(planId), 0, zeroToolPrompt,
                Optional.empty(), LaunchFixtures.clock());
    }

    private EffectiveRole roleWithoutResume(RoleClass roleClass) {
        var roleSkills = List.of(new RoleSkill(LaunchFixtures.ROLE_SKILL, skills.resolve(LaunchFixtures.ROLE_SKILL)
                .orElseThrow().digest()));
        return new EffectiveRole(LaunchFixtures.artifact(roleClass, "frontier", "max", RoleForm.FRESH, List.of(
                        LaunchFixtures.ROLE_SKILL)), "claude", "frontier", new EffortValue("max"), roleSkills, "1.5", null,
                new RoleArtifact.Questions(0, 0), Map.of(), 400_000, OptionalInt.of(3));
    }

    private static ContractEnvelope envelope(String planId) {
        return ContractEnvelope.of("plan", planId, "envelope-" + Generators.integers(1, 99).next(), List.of(
                        new LinkSchema(shortWords().next() + ":" + shortWords().next(),
                                List.of(new LinkParameter(shortWords().next(), "text", null)))),
                JsonDocument.parse("{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8), "schema"));
    }
}
