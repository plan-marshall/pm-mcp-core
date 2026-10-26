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
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.profile.JsonNode;
import de.planmarshall.core.profile.LoadedProfile;
import de.planmarshall.core.profile.ProfileSetKind;
import de.planmarshall.core.profile.WorkerParameters;
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
import de.planmarshall.core.store.WorkerState;
import de.planmarshall.runtime.job.WorkerStart;
import de.planmarshall.runtime.launch.ContractEnvelope;
import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.launch.HarnessEnrolmentStore;
import de.planmarshall.runtime.launch.HarnessVerifier;
import de.planmarshall.runtime.launch.JobTokenStore;
import de.planmarshall.runtime.launch.LaunchComposer;
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.launch.LinkParameter;
import de.planmarshall.runtime.launch.LinkSchema;
import de.planmarshall.runtime.launch.PromptComposer;
import de.planmarshall.runtime.launch.ResolvedSkill;
import de.planmarshall.runtime.launch.SkillSource;
import de.planmarshall.runtime.lock.FileLockManager;

/**
 * The supervisor test fixture: the real launch contracts over a temporary base, a fake job starter that records the
 * composed starts and ends, a fake model-slot admission, a recording state writer and the one order list the end path's
 * collaborators append to.
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

    /** The generation a bare loss detector names. */
    static final String GENERATION = "l-0123456789abcdef";

    final Path base;

    final Path workdir;

    final FakeStarter starter = new FakeStarter();

    final FakeSlots slots = new FakeSlots();

    final RecordingProtocol protocol = new RecordingProtocol();

    final RecordingStateWriter stateWriter = new RecordingStateWriter();

    final JobTokenStore jobTokens = new JobTokenStore();

    final List<AuditEvent> events = new CopyOnWriteArrayList<>();

    final List<String> order = new CopyOnWriteArrayList<>();

    final List<LossSignal> signals = new CopyOnWriteArrayList<>();

    final ProjectEnrolment enrolment;

    final LoadedProfile profile;

    /** Whether the fixture harness is enrolled; a test that wants a refused launch turns it off. */
    boolean enrol = true;

    /** The virtual clock every deadline is measured against; a scenario moves it explicitly. */
    final VirtualClock clock = new VirtualClock(NOW);

    /** The timers of the environment; a scenario drives them through the same virtual clock. */
    WorkerTimers timers = clock;

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
            store.enrol(new HarnessEnrolment(CLIENT, base.resolve("bin").resolve(CLIENT), SHA, "1.5.0", NOW, NOW,
                    new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(),
                    Map.of()));
        }
        var verifier = new HarnessVerifier(store, file -> Optional.of(SHA), (file, argv, pattern) -> Optional.of(
                "1.5.0"), (evidence, file) -> true, events::add, clock);
        return new SupervisorEnvironment(PROJECT, enrolment, base, base.resolve("home"), base.resolve("runtime"),
                base.resolve("run").resolve("pm.sock"), SHIM, skillSource, profile, verifier, new LaunchComposer(),
                starter, stateWriter, jobTokens, slots, protocol, events::add, clock, timers,
                new FileLockManager(event -> {
                }), guard, gate);
    }

    /** @return a role of the fixture harness with the given class, form and count */
    EffectiveRole role(RoleClass roleClass, RoleForm form, int count) {
        return role(roleClass, form, count, Map.of());
    }

    /** @return a role of the fixture harness with the given class, form, count and off-track settings */
    EffectiveRole role(RoleClass roleClass, RoleForm form, int count,
            Map<RoleArtifact.OffTrackSignal, RoleArtifact.OffTrackSetting> offTrack) {
        var recycling = form == RoleForm.WARM ? new RoleArtifact.Recycling(null, 20000, null) : null;
        var tools = roleClass == RoleClass.ZERO_TOOL ? List.<String>of() : List.of("read_file", "pm_state", "pm_do");
        var artifact = new RoleArtifact(1, "implementer", "the implementer", roleClass, tools, CLIENT,
                "claude-sonnet-4", new EffortValue("high"), form, count, List.of(ROLE_SKILL), recycling, 60, null,
                new RoleArtifact.Questions(0, 0), offTrack);
        var resolved = List.of(new RoleSkill(ROLE_SKILL, skills().resolve(ROLE_SKILL).orElseThrow().digest()));
        return new EffectiveRole(artifact, CLIENT, "claude-sonnet-4", new EffortValue("high"), resolved, "1.5",
                Optional.empty(), new RoleArtifact.Questions(0, 0), offTrack, 400_000, 3);
    }

    /** @return the contract envelope of one fresh task */
    ContractEnvelope envelope() {
        return ContractEnvelope.of("plan", PLAN, "plan",
                List.of(new LinkSchema("submit", List.of(new LinkParameter("outline", "text", null)))),
                JsonNode.object(Map.of()));
    }

    /** @return a fresh start request in a plan worktree with the fixture envelope */
    WorkerStartRequest freshRequest(EffectiveRole role) {
        return new WorkerStartRequest(role, LaunchRequest.WorkdirKind.PLAN_WORKTREE,
                PLAN, workdir, envelope(), false, List.of(STATE_SKILL), List.of(DOMAIN_SKILL), "",
                Optional.empty());
    }

    /** @return a warm start request in the given plan worktree */
    WorkerStartRequest warmRequest(EffectiveRole role, Path directory) {
        return new WorkerStartRequest(role, LaunchRequest.WorkdirKind.PLAN_WORKTREE,
                PLAN, directory, null, false, List.of(), List.of(), "", Optional.empty());
    }

    /** @return a supervisor over the fixture environment with an open guard and gate */
    WorkerSupervisor supervisor() {
        return supervisor(SupervisorEnvironment.StartGuard.open(), SupervisorEnvironment.EligibilityGate.open());
    }

    /**
     * @param guard the start-failure guard the supervisor consults
     * @param gate  the verification-record gate the supervisor consults
     * @return a supervisor over the fixture environment
     */
    WorkerSupervisor supervisor(SupervisorEnvironment.StartGuard guard,
            SupervisorEnvironment.EligibilityGate gate) {
        return new WorkerSupervisor(environment(guard, gate));
    }

    /**
     * @param roleClass  the class of the role
     * @param parameters the harness's worker values
     * @return a loss detector over the virtual clock and the fixture's signal sink
     */
    LossDetection detection(RoleClass roleClass, WorkerParameters parameters) {
        return new LossDetection(GENERATION, roleClass, parameters, clock, clock, signals::add);
    }

    /**
     * @param duration the time to move the virtual clock forward
     */
    void advance(Duration duration) {
        clock.advance(duration);
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
    final class FakeStarter implements SupervisorEnvironment.WorkerStarter {

        final List<WorkerStart> starts = new CopyOnWriteArrayList<>();

        final List<WorkerStart.Refusal> refusals = new CopyOnWriteArrayList<>();

        final List<JobRunner.CancelCause> cancels = new CopyOnWriteArrayList<>();

        @Override
        public JobRunner.StartResult startWorker(WorkerStart request) {
            starts.add(request);
            return new JobRunner.StartResult.Started(JobId.parse(request.launch().generationId()).orElseThrow());
        }

        @Override
        public JobId refuseWorker(WorkerStart.Refusal refusal) {
            refusals.add(refusal);
            return JobId.parse(refusal.generationId()).orElseThrow();
        }

        @Override
        public boolean cancel(JobId jobId, JobRunner.CancelCause cause) {
            cancels.add(cause);
            order.add("cancel:" + jobId.value());
            return true;
        }
    }

    /**
     * A model-slot admission that grants while {@link #grant} is {@code true} and records the releases and whether the
     * job token was revoked before one.
     */
    final class FakeSlots implements ModelSlots {

        boolean grant = true;

        /** The role of the head of the FIFO; {@code null} while no worker waits for a slot. */
        String waitingRole;

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
            if (!jobTokens.revoked(lease.generationId())) {
                order.add("release-before-revoke");
            }
            order.add("release");
        }

        @Override
        public Optional<String> nextWaitingRole() {
            return Optional.ofNullable(waitingRole);
        }

        @Override
        public void releasePlan(String planId) {
            releasedPlans.add(planId);
        }
    }

    /** The task protocol boundary, which records the fence, the started agent and whether the token was already revoked. */
    final class RecordingProtocol implements WorkerProtocol {

        final List<String> fences = new CopyOnWriteArrayList<>();

        final List<Boolean> revokedAtFence = new CopyOnWriteArrayList<>();

        final List<AgentStarted> startedAgents = new CopyOnWriteArrayList<>();

        final List<String> closedUnavailable = new CopyOnWriteArrayList<>();

        @Override
        public void fence(String generationId) {
            fences.add(generationId);
            revokedAtFence.add(jobTokens.revoked(generationId));
            order.add("fence");
        }

        @Override
        public void agentStarted(String generationId, String queueTaskId, int attempt, String executable, Instant at) {
            startedAgents.add(new AgentStarted(generationId, queueTaskId, attempt, executable, at));
            order.add("agent_started");
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            closedUnavailable.add(role + "/" + harness);
        }
    }

    /**
     * One reported {@code agent_started} signal.
     *
     * @param generationId the worker's generation
     * @param queueTaskId  the queue task the generation holds
     * @param attempt      the attempt of that task
     * @param executable   the executable's path
     * @param at           the instant the process started
     */
    record AgentStarted(String generationId, String queueTaskId, int attempt, String executable, Instant at) {
    }

    /** The terminal-state writer, recording the write and whether the slot was released before it. */
    final class RecordingStateWriter implements SupervisorEnvironment.WorkerStateWriter {

        final List<Write> writes = new CopyOnWriteArrayList<>();

        @Override
        public void write(String generationId, WorkerState.State state, Instant at, WorkerState.EndReason endReason,
                boolean startFailure) {
            writes.add(new Write(generationId, state, at, endReason, startFailure,
                    slots.released.stream().anyMatch(lease -> lease.generationId().equals(generationId))));
            order.add("write");
        }
    }

    /**
     * One recorded terminal-state write.
     *
     * @param generationId  the generation
     * @param state         the terminal worker state
     * @param at            the instant of the transition
     * @param endReason     the end reason
     * @param startFailure  whether the worker exited before its first call
     * @param slotReleased  whether the model slot was released before this write
     */
    record Write(String generationId, WorkerState.State state, Instant at, WorkerState.EndReason endReason,
    boolean startFailure, boolean slotReleased) {
    }

    private static final String USER = System.getProperty("user.name");

    private static SecretKey key() {
        return new SecretKeySpec("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8), KEY_ALGORITHM);
    }

    private static final String KEY_ALGORITHM = "HmacSHA256";

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
