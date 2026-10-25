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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.RoleSkill;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.runtime.job.WorkerStart;
import de.planmarshall.runtime.launch.ContractEnvelope;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessEnrolmentStore;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.launch.JobTokenStore;
import de.planmarshall.runtime.launch.LaunchComposer;
import de.planmarshall.runtime.launch.LinkParameter;
import de.planmarshall.runtime.launch.LinkSchema;
import de.planmarshall.runtime.launch.PromptComposer;
import de.planmarshall.runtime.launch.ResolvedSkill;
import de.planmarshall.runtime.launch.SkillSource;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The supervisor test fixture: the real launch contracts over a temporary base, a fake job starter, a fake model-slot
 * admission and the recording collaborators the start path and the log assertions read.
 */
final class SupervisorTestFixture {

    static final String PROJECT = "sample-project";

    static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    static final String SHA = "a".repeat(64);

    static final String CLIENT = "claude";

    static final String ROLE_SKILL = "skill://pm/role/implementer/SKILL.md";

    static final String PLAN = "plan-30-supervisor";

    static final String SHIM = "Skills of plan-marshall arrive in-band or are named by a skill://pm/ URI.";

    static final String STATE_SKILL = "skill://pm/state/outline/SKILL.md";

    static final String DOMAIN_SKILL = "skill://pm/ext/domain-java/SKILL.md";

    final Path base;

    final Path workdir;

    final FakeStarter starter = new FakeStarter();

    final FakeSlots slots = new FakeSlots();

    final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    final ProjectEnrolment enrolment;

    final LoadedProfile profile;

    /** Whether the fixture harness is enrolled; a test that wants a refused launch turns it off. */
    boolean enrol = true;

    private final InstantSource clock = InstantSource.fixed(NOW);

    SupervisorTestFixture(Path base) throws IOException {
        this.base = base;
        Files.createDirectories(base.resolve("run"));
        Files.createDirectories(base.resolve("home"));
        workdir = Files.createDirectories(base.resolve("worktree"));
        enrolment = new ProjectEnrolment(PROJECT, workdir.toString(), NOW, false, null, null, List.of(), List.of(),
                ProjectEnrolment.Build.defaults(), Map.of(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(),
                List.of(), new TreeMap<>());
        profile = profile();
    }

    /** @return the loaded profile of the fixture harness, as the launch contract reads it */
    LoadedProfile profile() {
        var bytes = document().getBytes(StandardCharsets.UTF_8);
        return new LoadedProfile(CLIENT, ProfileSetKind.SHIPPED, JsonDocument.parse(bytes, "profile"), bytes,
                LoadedProfile.State.VERIFIED, null);
    }

    /** @return the supervisor environment over the fixture, with the given guard and gate */
    SupervisorEnvironment environment(SupervisorEnvironment.StartGuard guard,
            SupervisorEnvironment.EligibilityGate gate) {
        return environment(guard, gate, skills());
    }

    /** @return the supervisor environment over the fixture, with the given guard, gate and skill source */
    SupervisorEnvironment environment(SupervisorEnvironment.StartGuard guard,
            SupervisorEnvironment.EligibilityGate gate, SkillSource skillSource) {
        var key = key();
        var store = new HarnessEnrolmentStore(base.resolve("harness"), () -> key,
                new FileLockManager(event -> {
                }), USER);
        if (enrol) {
            store.enrol(new HarnessEnrolment(CLIENT,
                    base.resolve("bin").resolve(CLIENT), SHA, "1.5.0", NOW, NOW,
                    new HarnessEnrolment.UsageReport(
                            HarnessEnrolment.UsageSource.DECLARED, Map.of()),
                    Map.of(), Map.of()));
        }
        var verifier = new HarnessVerifier(store, file -> Optional.of(SHA),
                (file, argv, pattern) -> Optional.of("1.5.0"), (evidence, file) -> true, events::add, clock);
        return new SupervisorEnvironment(PROJECT, enrolment, base, base.resolve("home"), base.resolve("runtime"),
                base.resolve("run").resolve("pm.sock"), SHIM, skillSource, profile, verifier, new LaunchComposer(), starter,
                new JobTokenStore(), slots, new NoProtocol(), events::add, clock,
                (at, action) -> () -> {
                }, guard, gate);
    }

    /** @return a role of the fixture harness with the given class, form and count */
    EffectiveRole role(RoleClass roleClass, RoleForm form, int count) {
        var recycling = form == RoleForm.WARM ? new RoleArtifact.Recycling(null, 20000, null) : null;
        var tools = roleClass == RoleClass.ZERO_TOOL ? List.<String>of() : List.of("read_file", "pm_state", "pm_do");
        var artifact = new RoleArtifact(1, "implementer", "the implementer", roleClass, tools, CLIENT,
                "claude-sonnet-4", new EffortValue("high"), form, count, List.of(ROLE_SKILL), recycling, 60, null,
                new RoleArtifact.Questions(0, 0), Map.of());
        var resolved = List.of(new RoleSkill(ROLE_SKILL, skills().resolve(ROLE_SKILL).orElseThrow().digest()));
        return new EffectiveRole(artifact, CLIENT, "claude-sonnet-4", new EffortValue("high"), resolved, "1.5",
                Optional.empty(), new RoleArtifact.Questions(0, 0), Map.of(), 400_000, 3);
    }

    /** @return the contract envelope of one fresh task */
    ContractEnvelope envelope() {
        return ContractEnvelope.of("plan", PLAN, "plan",
                List.of(new LinkSchema("submit", List.of(new LinkParameter("outline", "text", null)))),
                de.planmarshall.core.profile.JsonNode.object(Map.of()));
    }

    /** @return a fresh start request in a plan worktree with the fixture envelope */
    WorkerStartRequest freshRequest(EffectiveRole role) {
        return new WorkerStartRequest(role, de.planmarshall.runtime.launch.LaunchRequest.WorkdirKind.PLAN_WORKTREE,
                PLAN, workdir, envelope(), false, List.of(STATE_SKILL), List.of(DOMAIN_SKILL), "",
                Optional.empty());
    }

    /** @return a warm start request in the given plan worktree */
    WorkerStartRequest warmRequest(EffectiveRole role, Path directory) {
        return new WorkerStartRequest(role, de.planmarshall.runtime.launch.LaunchRequest.WorkdirKind.PLAN_WORKTREE,
                PLAN, directory, null, false, List.of(), List.of(), "", Optional.empty());
    }

    /** @return the skills the launch prompt reads */
    SkillSource skills() {
        var skills = new LinkedHashMap<String, String>();
        skills.put(PromptComposer.CORE, "Core conduct: act deliberately.");
        skills.put(PromptComposer.TASK_PROTOCOL, "Task protocol: pm_wait, pm_state, pm_do.");
        skills.put(PromptComposer.WRITING, "Writing: use the project's model language.");
        skills.put(ROLE_SKILL, "Role implementer: implement production code.");
        skills.put(STATE_SKILL, "Outline state.");
        skills.put(DOMAIN_SKILL, "Java domain.");
        var resolved = new LinkedHashMap<String, ResolvedSkill>();
        skills.forEach((uri, text) -> resolved.put(uri, ResolvedSkill.of(uri, text)));
        return uri -> Optional.ofNullable(resolved.get(uri));
    }

    /**
     * A job starter that records the calls and answers the request's own generation, so no process is spawned.
     */
    static final class FakeStarter implements SupervisorEnvironment.WorkerStarter {

        final List<WorkerStart> starts = new CopyOnWriteArrayList<>();

        final List<WorkerStart.Refusal> refusals = new CopyOnWriteArrayList<>();

        @Override
        public JobRunner.StartResult startWorker(WorkerStart request) {
            starts.add(request);
            return new JobRunner.StartResult.Started(
                    JobId.parse(request.launch().generationId()).orElseThrow());
        }

        @Override
        public JobId refuseWorker(WorkerStart.Refusal refusal) {
            refusals.add(refusal);
            return JobId.parse(refusal.generationId()).orElseThrow();
        }
    }

    /**
     * A model-slot admission that grants while {@link #grant} is {@code true} and records the releases.
     */
    static final class FakeSlots implements ModelSlots {

        boolean grant = true;

        final List<Lease> claimed = new CopyOnWriteArrayList<>();

        final List<Lease> released = new CopyOnWriteArrayList<>();

        final List<String> releasedPlans = new CopyOnWriteArrayList<>();

        @Override
        public Optional<Lease> claim(String generationId, String role) {
            if (!grant) {
                return Optional.empty();
            }
            var lease = new Lease(generationId, role);
            claimed.add(lease);
            return Optional.of(lease);
        }

        @Override
        public void release(Lease lease) {
            released.add(lease);
        }

        @Override
        public Optional<String> nextWaitingRole() {
            return Optional.empty();
        }

        @Override
        public void releasePlan(String planId) {
            releasedPlans.add(planId);
        }
    }

    /** The task protocol boundary, which the start path does not call. */
    static final class NoProtocol implements WorkerProtocol {

        @Override
        public void fence(String generationId) {
            // the start path never fences
        }

        @Override
        public void agentStarted(String generationId, String queueTaskId, int attempt, String executable, Instant at) {
            // detection is another step's contract
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            // the guard is another step's contract
        }
    }

    private static final String USER = System.getProperty("user.name");

    private static SecretKey key() {
        return new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    private static String document() {
        return """
                {
                  "schema_version": 1,
                  "client_id": "claude",
                  "identity": { "version_probe": ["--version"], "version_pattern": "(\\\\d+\\\\.\\\\d+\\\\.\\\\d+)" },
                  "headless": {
                    "executable": "claude",
                    "argv": ["-p", "--model", "{{model}}", "--effort", "{{effort}}",
                             "--prompt-file", "{{prompt_file}}", "--mcp-config", "{{mcp_config}}",
                             "--json-schema", "{{result_schema}}", "--allowedTools", "{{tools}}",
                             "--agents", "{{agent}}"],
                    "classes": {
                      "writer": { "args": [], "tool_set": "enforced" },
                      "read_only": { "args": [], "tool_set": "enforced" },
                      "zero_tool": { "args": ["--setting-sources", "", "--max-turns", "1"] }
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
                                      "resume": { "argv": ["--resume", "{{session_id}}", "-p",
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
}
