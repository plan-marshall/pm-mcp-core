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

import static org.easymock.EasyMock.anyBoolean;
import static org.easymock.EasyMock.anyInt;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;

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
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.easymock.Capture;
import org.easymock.CaptureType;

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
import de.planmarshall.core.service.AuditSink;
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
import de.planmarshall.runtime.test.AuditSinkDoubles;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The supervisor test fixture: the real launch contracts over a temporary base, an EasyMock job starter that records the
 * composed starts and ends, an EasyMock model-slot admission, an EasyMock state writer and the one order list the end
 * path's collaborators append to.
 * <p>
 * The identifiers of one fixture — its project, plan, digest and generation — are drawn when the fixture is constructed;
 * a test that builds it in its {@code @BeforeEach} draws them under the per-test seed, and a test that builds it in the
 * body draws them at that point. The remaining literals are fixture contracts and naming: the client id {@code claude}
 * and its executable, the {@code skill://} fixture URIs, the shim line, the role and model names, the harness version and
 * the profile document, which is a wire format the launch contract parses.
 */
final class SupervisorTestFixture {

    static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    static final String CLIENT = "claude";

    static final String ROLE_SKILL = "skill://pm/role/implementer/SKILL.md";

    static final String SHIM = "Skills of plan-marshall arrive in-band or are named by a skill://pm/ URI.";

    static final String STATE_SKILL = "skill://pm/state/outline/SKILL.md";

    static final String DOMAIN_SKILL = "skill://pm/ext/domain-java/SKILL.md";

    final String project = RuntimeGenerators.projectIds().next();

    final String plan = RuntimeGenerators.planIds().next();

    final String sha = RuntimeGenerators.sha256Digests().next();

    /** The generation a bare loss detector names; a job-id shape, so it parses as one. */
    final String generation = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();

    final Path base;

    final Path workdir;

    /** The worker starts the starter was asked for, in order. */
    final List<WorkerStart> starts = new CopyOnWriteArrayList<>();

    /** The refusals the starter answered, in order. */
    final List<WorkerStart.Refusal> refusals = new CopyOnWriteArrayList<>();

    /** The cancel causes the starter was asked for, in order. */
    final List<JobRunner.CancelCause> cancels = new CopyOnWriteArrayList<>();

    /** The failure the starter raises instead of answering; a test drives a start path that throws. */
    RuntimeException startFailure;

    /** The model-slot leases the admission granted, in order. */
    final List<ModelSlots.Lease> claimed = new CopyOnWriteArrayList<>();

    /** The model-slot leases the admission released, in order. */
    final List<ModelSlots.Lease> released = new CopyOnWriteArrayList<>();

    /** The generations whose slot was withdrawn, in order. */
    final List<String> withdrawn = new CopyOnWriteArrayList<>();

    /** The plans whose slots were released, in order. */
    final List<String> releasedPlans = new CopyOnWriteArrayList<>();

    /** Whether the admission grants a slot; a test turns it off for the refused path. */
    boolean grant = true;

    /** The role of the head of the FIFO; {@code null} while no worker waits for a slot. */
    String waitingRole;

    /** The generations the protocol fenced, in order. */
    final List<String> fences = new CopyOnWriteArrayList<>();

    /** Whether the job token was already revoked at each fence, in order. */
    final List<Boolean> revokedAtFence = new CopyOnWriteArrayList<>();

    /** The {@code agent_started} signals the protocol reported, in order. */
    final List<AgentStarted> startedAgents = new CopyOnWriteArrayList<>();

    /** The role and harness of every closed-unavailable report, in order. */
    final List<String> closedUnavailable = new CopyOnWriteArrayList<>();

    /** The terminal-state writes, in order. */
    final List<Write> writes = new CopyOnWriteArrayList<>();

    final JobTokenStore jobTokens = new JobTokenStore();

    final Capture<AuditEvent> events = newCapture(CaptureType.ALL);

    final AuditSink eventSink = AuditSinkDoubles.capturing(events);

    final List<String> order = new CopyOnWriteArrayList<>();

    final List<LossSignal> signals = new CopyOnWriteArrayList<>();

    final SupervisorEnvironment.WorkerStarter starter = starter();

    final ModelSlots slots = slots();

    final WorkerProtocol protocol = protocol();

    final SupervisorEnvironment.WorkerStateWriter stateWriter = stateWriter();

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
        enrolment = new ProjectEnrolment(project, workdir.toString(), NOW, false, null, null, List.of(), List.of(),
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
            store.enrol(new HarnessEnrolment(CLIENT, base.resolve("bin").resolve(CLIENT), sha, "1.5.0", NOW, NOW,
                    new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(),
                    Map.of()));
        }
        var verifier = new HarnessVerifier(store, file -> Optional.of(sha), (file, argv, pattern) -> Optional.of(
                "1.5.0"), (evidence, file) -> true, eventSink, clock);
        return new SupervisorEnvironment(project, enrolment, base, base.resolve("home"), base.resolve("runtime"),
                base.resolve("run").resolve("pm.sock"), SHIM, skillSource, profile, verifier, new LaunchComposer(),
                starter, stateWriter, jobTokens, slots, protocol, eventSink, clock, timers,
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
                null, new RoleArtifact.Questions(0, 0), offTrack, 400_000, OptionalInt.of(3));
    }

    /** @return the contract envelope of one fresh task */
    ContractEnvelope envelope() {
        return ContractEnvelope.of("plan", plan, "plan",
                List.of(new LinkSchema("submit", List.of(new LinkParameter("outline", "text", null)))),
                JsonNode.object(Map.of()));
    }

    /** @return a fresh start request in a plan worktree with the fixture envelope */
    WorkerStartRequest freshRequest(EffectiveRole role) {
        return new WorkerStartRequest(role, LaunchRequest.WorkdirKind.PLAN_WORKTREE,
                plan, workdir, envelope(), false, List.of(STATE_SKILL), List.of(DOMAIN_SKILL), "",
                null);
    }

    /** @return a warm start request in the given plan worktree */
    WorkerStartRequest warmRequest(EffectiveRole role, Path directory) {
        return new WorkerStartRequest(role, LaunchRequest.WorkdirKind.PLAN_WORKTREE,
                plan, directory, null, false, List.of(), List.of(), "", null);
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
        return new LossDetection(generation, roleClass, parameters, clock, clock, signals::add);
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

    private SupervisorEnvironment.WorkerStarter starter() {
        SupervisorEnvironment.WorkerStarter mock = createMock(SupervisorEnvironment.WorkerStarter.class);
        expect(mock.startWorker(anyObject(WorkerStart.class))).andAnswer(() -> {
            var request = (WorkerStart) getCurrentArguments()[0];
            starts.add(request);
            if (startFailure != null) {
                throw startFailure;
            }
            return new JobRunner.StartResult.Started(JobId.parse(request.launch().generationId()).orElseThrow());
        }).anyTimes();
        expect(mock.refuseWorker(anyObject(WorkerStart.Refusal.class))).andAnswer(() -> {
            var refusal = (WorkerStart.Refusal) getCurrentArguments()[0];
            refusals.add(refusal);
            return JobId.parse(refusal.generationId()).orElseThrow();
        }).anyTimes();
        expect(mock.cancel(anyObject(JobId.class), anyObject(JobRunner.CancelCause.class))).andAnswer(() -> {
            var arguments = getCurrentArguments();
            cancels.add((JobRunner.CancelCause) arguments[1]);
            order.add("cancel:" + ((JobId) arguments[0]).value());
            return true;
        }).anyTimes();
        replay(mock);
        return mock;
    }

    private ModelSlots slots() {
        ModelSlots mock = createMock(ModelSlots.class);
        expect(mock.claim(anyObject(String.class), anyObject(String.class))).andAnswer(() -> {
            if (!grant) {
                return Optional.empty();
            }
            var arguments = getCurrentArguments();
            var lease = new ModelSlots.Lease((String) arguments[0], (String) arguments[1]);
            claimed.add(lease);
            return Optional.of(lease);
        }).anyTimes();
        expect(mock.nextWaitingRole()).andAnswer(() -> Optional.ofNullable(waitingRole)).anyTimes();
        mock.withdraw(anyObject(String.class));
        expectLastCall().andAnswer(() -> {
            withdrawn.add((String) getCurrentArguments()[0]);
            return null;
        }).anyTimes();
        mock.release(anyObject(ModelSlots.Lease.class));
        expectLastCall().andAnswer(() -> {
            var lease = (ModelSlots.Lease) getCurrentArguments()[0];
            released.add(lease);
            if (!jobTokens.revoked(lease.generationId())) {
                order.add("release-before-revoke");
            }
            order.add("release");
            return null;
        }).anyTimes();
        mock.releasePlan(anyObject(String.class));
        expectLastCall().andAnswer(() -> {
            releasedPlans.add((String) getCurrentArguments()[0]);
            return null;
        }).anyTimes();
        replay(mock);
        return mock;
    }

    private WorkerProtocol protocol() {
        WorkerProtocol mock = createMock(WorkerProtocol.class);
        mock.fence(anyObject(String.class));
        expectLastCall().andAnswer(() -> {
            var generation = (String) getCurrentArguments()[0];
            fences.add(generation);
            revokedAtFence.add(jobTokens.revoked(generation));
            order.add("fence");
            return null;
        }).anyTimes();
        mock.agentStarted(anyObject(String.class), anyObject(String.class), anyInt(), anyObject(String.class),
                anyObject(Instant.class));
        expectLastCall().andAnswer(() -> {
            var arguments = getCurrentArguments();
            startedAgents.add(new AgentStarted((String) arguments[0], (String) arguments[1], (Integer) arguments[2],
                    (String) arguments[3], (Instant) arguments[4]));
            order.add("agent_started");
            return null;
        }).anyTimes();
        mock.offTrack(anyObject(WorkerProtocol.OffTrackReport.class));
        expectLastCall().andAnswer(() -> {
            order.add("off_track");
            return null;
        }).anyTimes();
        mock.closeUnavailable(anyObject(String.class), anyObject(String.class));
        expectLastCall().andAnswer(() -> {
            closedUnavailable.add(getCurrentArguments()[0] + "/" + getCurrentArguments()[1]);
            return null;
        }).anyTimes();
        replay(mock);
        return mock;
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

    private SupervisorEnvironment.WorkerStateWriter stateWriter() {
        SupervisorEnvironment.WorkerStateWriter mock = createMock(SupervisorEnvironment.WorkerStateWriter.class);
        mock.write(anyObject(String.class), anyObject(WorkerState.State.class), anyObject(Instant.class),
                anyObject(WorkerState.EndReason.class), anyBoolean());
        expectLastCall().andAnswer(() -> {
            var arguments = getCurrentArguments();
            var generation = (String) arguments[0];
            writes.add(new Write(generation, (WorkerState.State) arguments[1], (Instant) arguments[2],
                    (WorkerState.EndReason) arguments[3], (Boolean) arguments[4],
                    released.stream().anyMatch(lease -> lease.generationId().equals(generation))));
            order.add("write");
            return null;
        }).anyTimes();
        replay(mock);
        return mock;
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
