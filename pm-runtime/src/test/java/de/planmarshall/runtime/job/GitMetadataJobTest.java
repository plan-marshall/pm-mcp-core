/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.core.profile.JsonDocument;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.EffortValue;
import de.planmarshall.core.role.RoleArtifact;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.service.JobRunner.CancelCause;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.BudgetDerivation;
import de.planmarshall.core.store.JobRecord.GitMetadata;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.runtime.launch.JobTokenStore;
import de.planmarshall.runtime.launch.LaunchPrompt;
import de.planmarshall.runtime.launch.LaunchRefusal;
import de.planmarshall.runtime.launch.ResolvedSkill;
import de.planmarshall.runtime.launch.WorkerLaunch;
import de.planmarshall.runtime.security.JobTokens;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The {@code git_metadata} of a job record (PM-SEC-5, PM-EXEC-4): a worker records the launcher's statement, and
 * {@code unenforced} until a report says otherwise; a job of another kind records none, whatever its report says.
 * <p>
 * The launcher is {@link FakePmExec}. Contract literals that stay: the modes of the launcher, the host {@code PATH},
 * the harness {@code claude}, the tool {@code read_file}, the effort {@code high}, the budget key and the role
 * bounds. The generation id, the job token, the role name, the model and the versions are generated.
 */
@EnabledOnOs(OS.LINUX)
@EnableGeneratorController
@EnableTestLogger
@DisplayName("git_metadata of a job record")
class GitMetadataJobTest {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Scope PROJECT = new Scope(Scope.Type.PROJECT, null);
    private static final Scope WORKSPACE = new Scope(Scope.Type.WORKSPACE, null);
    private static final Map<String, String> ENVIRONMENT = Map.of("PATH", "/usr/bin:/bin");
    private static final String BUDGET_KEY = "git:status";
    private static final BudgetDerivation FLOOR = new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null);

    private static final String WORKER_SCRIPT = """
            #!/bin/sh
            echo done
            exit 0
            """;

    private JobEnrolmentFixture fixture;
    private Path home;
    private Path script;
    private JobTokenStore tokens;
    private SubprocessJobRunner runner;
    private GitMetadataGuard guard;
    private final List<GitMetadataChangeSink.Change> changes = new CopyOnWriteArrayList<>();
    private String generation;
    private String token;
    private String role;
    private int budgetSeconds;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new JobEnrolmentFixture();
        generation = RuntimeGenerators.jobIds(JobId.Kind.LLM).next();
        token = Generators.letterStrings(16, 32).next();
        role = Generators.letterStrings(4, 16).next().toLowerCase(Locale.ROOT);
        budgetSeconds = Generators.integers(60, 3600).next();
        script = fixture.base.resolve("worker/run");
        Files.createDirectories(script.getParent());
        Files.writeString(script, WORKER_SCRIPT);
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        home = FakePmExec.install(fixture.base.resolve("home"), FakePmExec.Mode.REPORT);
        tokens = new JobTokenStore();
        guard = new GitMetadataGuard();
        runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(fixture.locks)
                .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                .termination(new TerminationProtocol(ProcessGroupSignals.system())).environment(environment())
                .probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                .parser((kind, output) -> new JobEvidence.ParseResult(true, 0, false, null))
                .primitives(request -> Optional.empty()).jobTokens(tokens).audit(fixture.sink)
                .gitMetadataGuard(guard).gitMetadataSink(changes::add).build();
    }

    @AfterEach
    void tearDown() {
        for (var jobId : runner.runningJobs()) {
            runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
            runner.awaitSettled(jobId, SETTLE_TIMEOUT);
        }
        fixture.close();
    }

    @Test
    @DisplayName("a worker whose launcher reports enforced records enforced")
    void workerEnforced() throws Exception {
        FakePmExec.install(home, FakePmExec.Mode.REPORT_GIT_METADATA);

        var settled = settleWorker();

        assertEquals(GitMetadata.ENFORCED, settled.gitMetadata(), "the report's value is copied into the record");
    }

    @Test
    @DisplayName("the recorded git metadata depends on the report and the read-only path")
    void theRecordedValueNeedsBothTheReportAndThePath() {
        assertAll("only a reported value above a sent path is enforced",
                () -> assertEquals(GitMetadata.ENFORCED,
                        SubprocessJobRunner.gitMetadataFor(GitMetadata.ENFORCED, true),
                        "a reported enforced above a sent path stays enforced"),
                () -> assertEquals(GitMetadata.UNENFORCED,
                        SubprocessJobRunner.gitMetadataFor(GitMetadata.ENFORCED, false),
                        "no sent path refuses the enforced claim"),
                () -> assertEquals(GitMetadata.UNENFORCED,
                        SubprocessJobRunner.gitMetadataFor(GitMetadata.UNENFORCED, true),
                        "a reported unenforced stays unenforced"),
                () -> assertEquals(GitMetadata.UNENFORCED,
                        SubprocessJobRunner.gitMetadataFor(null, true),
                        "a report without the member records unenforced"));
    }

    @Test
    @DisplayName("a worker whose report names no member records unenforced")
    void workerWithoutMember() {
        var settled = settleWorker();

        assertAll("the honest value is unenforced",
                () -> assertEquals(GitMetadata.UNENFORCED, settled.gitMetadata(),
                        "a worker record never says enforced before a report does"),
                () -> assertEquals(GitMetadata.UNENFORCED,
                        runner.recordOf(settled.jobId()).orElseThrow().gitMetadata(),
                        "the fate record keeps the honest value"));
    }

    @Test
    @DisplayName("a failing audit sink is logged and the change is still reported")
    void failingAuditIsLogged() throws Exception {
        var config = fixture.root.resolve(".git/config");
        Files.writeString(config, "[core]\n\trepositoryformatversion = 0\n");
        writeGitTool();
        fixture.enrol();
        Files.writeString(script, "#!/bin/sh\necho '[core] fsmonitor = attacker' >> " + config + "\nexit 0\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        var failingAudit = SubprocessJobRunner.builder().base(fixture.base).lockManager(fixture.locks)
                .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                .termination(new TerminationProtocol(ProcessGroupSignals.system())).environment(environment())
                .probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                .parser((kind, output) -> new JobEvidence.ParseResult(true, 0, false, null))
                .primitives(request -> Optional.empty()).jobTokens(tokens)
                .audit(_ -> {
                    throw new IllegalStateException("the audit sink refused the record");
                })
                .gitMetadataGuard(guard).gitMetadataSink(changes::add).build();

        var result = failingAudit.startWorker(start());
        var jobId = assertInstanceOf(JobRunner.StartResult.Started.class, result, "the worker should be started")
                .jobId();
        failingAudit.awaitSettled(jobId, SETTLE_TIMEOUT).orElseThrow();

        assertAll("A failing audit sink is logged",
                () -> assertEquals(1, changes.size(), "the sink is still called"),
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.GIT_METADATA_CHANGE_NOT_REPORTED.resolveIdentifierString()));
    }

    @Test
    @DisplayName("a worker whose launcher writes no report records unenforced")
    void workerWithoutReport() throws Exception {
        FakePmExec.install(home, FakePmExec.Mode.NO_REPORT);

        var result = runner.startWorker(start());
        var jobId = assertInstanceOf(JobRunner.StartResult.Started.class, result, "the worker should be started")
                .jobId();
        var written = runner.recordOf(jobId).orElseThrow();
        var settled = runner.awaitSettled(jobId, SETTLE_TIMEOUT).orElseThrow();

        assertAll("A missing report leaves the honest value",
                () -> assertEquals(GitMetadata.UNENFORCED, written.gitMetadata(),
                        "the first record read states unenforced"),
                () -> assertEquals(GitMetadata.UNENFORCED, settled.gitMetadata(),
                        "the fate record states unenforced"));
    }

    @Test
    @DisplayName("a refused worker records unenforced, never enforced")
    void refusedWorkerRecordsUnenforced() {
        var jobId = runner.refuseWorker(new WorkerStart.Refusal(generation, role(), fixture.enrolment(), PROJECT,
                "worker:" + role, budgetSeconds, new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null),
                workerBlock(), LaunchRefusal.HARNESS_NOT_ENROLLED, Generators.letterStrings(8, 64).next()));

        var stored = runner.recordOf(jobId).orElseThrow();

        assertEquals(GitMetadata.UNENFORCED, stored.gitMetadata(), "the refused record states unenforced");
    }

    @Test
    @DisplayName("a git job records no git_metadata though its report carries it")
    void gitJobRecordsNone() throws Exception {
        Files.writeString(fixture.tools.get(HostToolName.GIT), "#!/bin/sh\nexit 0\n");
        fixture.enrol();
        FakePmExec.install(home, FakePmExec.Mode.REPORT_GIT_METADATA);
        var request = fixture.request(fixture.root.toString(),
                new JobRequest.HostToolCall(HostToolName.GIT, List.of("status")));

        var result = runner.start(new JobRunner.StartRequest<>(JobId.Kind.GIT, WORKSPACE, BUDGET_KEY, 30, FLOOR,
                request, null));

        var jobId = assertInstanceOf(JobRunner.StartResult.Started.class, result, "the git job should start")
                .jobId();
        var settled = runner.awaitSettled(jobId, SETTLE_TIMEOUT).orElseThrow();
        assertAll("a non-worker records no git metadata",
                () -> assertEquals(JobId.Kind.GIT, jobId.kind(), "the job is a git job"),
                () -> assertNull(settled.gitMetadata(), "the report's member is not recorded"));
    }

    @Test
    @DisplayName("a worker that changed the git metadata refuses the next git job until it is rebaselined")
    void workerChangeRefusesTheNextGitJob() throws Exception {
        var config = fixture.root.resolve(".git/config");
        Files.writeString(config, "[core]\n\trepositoryformatversion = 0\n");
        writeGitTool();
        fixture.enrol();
        Files.writeString(script, "#!/bin/sh\necho '[core] fsmonitor = attacker' >> " + config + "\nexit 0\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));

        var worker = settleWorker();

        var changeEvent = fixture.events.stream().filter(event -> SubprocessJobRunner.GIT_METADATA_CHANGED.equals(event.event())).findFirst()
                .orElseThrow(() -> new AssertionError("no git metadata audit record"));
        assertAll("The worker's change is seen",
                () -> assertEquals(GitMetadata.UNENFORCED, worker.gitMetadata(), "the honest value is recorded"),
                () -> assertEquals(1, changes.size(), "the sink is called once"),
                () -> assertEquals(GitMetadataChangeSink.Point.WORKER_END, changes.getFirst().point(),
                        "the point is the worker's end"),
                () -> assertTrue(changes.getFirst().parts().contains(".git/config"),
                        "the changed part is named"),
                () -> assertEquals(fixture.root.toString(), changeEvent.details().get(SubprocessJobRunner.DETAIL_PROJECT),
                        "the audit record names the project"),
                () -> assertEquals(GitMetadataChangeSink.Point.WORKER_END.wireName(),
                        changeEvent.details().get(SubprocessJobRunner.DETAIL_POINT),
                        "the audit record names the point"),
                () -> assertEquals(worker.jobId().value(),
                        changeEvent.details().get(SubprocessJobRunner.DETAIL_JOB_ID),
                        "the audit record names the job"),
                () -> assertTrue(((Collection<?>) changeEvent.details()
                        .get(SubprocessJobRunner.DETAIL_PARTS)).contains(".git/config"),
                        "the audit record names the parts"),
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.GIT_METADATA_CHANGED.resolveIdentifierString()),
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        GitMetadataChangeSink.Point.WORKER_END.wireName()));

        var refused = runner.start(gitJob());

        var refusedId = assertInstanceOf(JobRunner.StartResult.Refused.class, refused,
                "the git job is refused").reason();
        assertEquals(SubprocessJobRunner.GIT_METADATA_CHANGED, refusedId, "the refusal names the change");
        var refusedRecord = JobRecordStore.ofProject(fixture.base, fixture.project).records().stream()
                .filter(stored -> stored.kind() == JobId.Kind.GIT)
                .max(Comparator.comparing(JobRecord::createdAt)).orElseThrow();
        assertAll("The refused git job is recorded",
                () -> assertEquals(Status.REFUSED, refusedRecord.status(), "the record is refused"),
                () -> assertEquals(SubprocessJobRunner.GIT_METADATA_CHANGED, refusedRecord.refusalReason(),
                        "the record names the refusal reason"),
                () -> assertNull(refusedRecord.gitMetadata(), "a git job records no git metadata"));

        guard.rebaseline(fixture.root);
        var started = assertInstanceOf(JobRunner.StartResult.Started.class, runner.start(gitJob()),
                "after rebaseline the git job runs again");
        runner.awaitSettled(started.jobId(), SETTLE_TIMEOUT).orElseThrow();
    }

    @Test
    @DisplayName("a worker that changed only tracked source leaves the digest unchanged")
    void trackedSourceChangeDoesNotRefuse() throws Exception {
        var tracked = fixture.root.resolve("README.md");
        Files.writeString(tracked, Generators.letterStrings(1, 32).next());
        writeGitTool();
        fixture.enrol();
        Files.writeString(script, "#!/bin/sh\necho changed >> " + tracked + "\nexit 0\n");
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));

        var worker = settleWorker();
        var started = assertInstanceOf(JobRunner.StartResult.Started.class, runner.start(gitJob()),
                "a tracked source change leaves the git job running");

        assertAll("The matched control",
                () -> assertTrue(worker.status().isSettled(), "the worker settled by itself"),
                () -> assertTrue(changes.isEmpty(), "the sink is not called for a tracked change"),
                () -> assertTrue(runner.awaitSettled(started.jobId(), SETTLE_TIMEOUT).isPresent(),
                        "the git job settles after the control"));
    }

    /** Overwrites the fixture's git tool with an enrolled script that succeeds. */
    private void writeGitTool() throws IOException {
        Files.writeString(fixture.tools.get(HostToolName.GIT), "#!/bin/sh\nexit 0\n");
    }

    private JobRunner.StartRequest<JobRequest> gitJob() {
        var request = fixture.request(fixture.root.toString(),
                new JobRequest.HostToolCall(HostToolName.GIT, List.of("status")));
        return new JobRunner.StartRequest<>(JobId.Kind.GIT, WORKSPACE, BUDGET_KEY, 30, FLOOR, request, null);
    }

    private JobRecord settleWorker() {
        var result = runner.startWorker(start());
        var jobId = assertInstanceOf(JobRunner.StartResult.Started.class, result, "the worker should be started")
                .jobId();
        return runner.awaitSettled(jobId, SETTLE_TIMEOUT).orElseThrow();
    }

    private WorkerStart start() {
        var promptText = Generators.letterStrings(8, 64).next();
        var prompt = new LaunchPrompt(promptText, ResolvedSkill.sha256(promptText), List.of(),
                promptText.getBytes(StandardCharsets.UTF_8).length);
        var launch = new WorkerLaunch(generation, role, RoleClass.WRITER,
                RoleForm.FRESH, List.of(script.toString()), ENVIRONMENT, script.getParent(),
                fixture.base.resolve("run/jobs").resolve(generation), script, prompt, null, null, token,
                JobTokens.sha256Hex(token), "claude", version(), Generators.letterStrings(1, 16).next(), null, null,
                JsonDocument.parse(workerBlock().getBytes(StandardCharsets.UTF_8), "worker"));
        return new WorkerStart(launch, fixture.enrolment(), PROJECT, "worker:" + role, budgetSeconds,
                new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null), List.of(), null);
    }

    private String workerBlock() {
        return "{\"role\":\"" + role + "\"}";
    }

    /** @return the effective role of the refused worker */
    private EffectiveRole role() {
        var artifact = new RoleArtifact(1, role, Generators.letterStrings(4, 32).next(), RoleClass.WRITER,
                List.of("read_file"), "claude", "frontier", new EffortValue("high"), RoleForm.FRESH, 1,
                List.of("skill://pm/role/" + role + "/SKILL.md"), null, 60, null, new RoleArtifact.Questions(0, 0),
                Map.of());
        return new EffectiveRole(artifact, "claude", "frontier", new EffortValue("high"), List.of(),
                Generators.integers(0, 99).next() + "." + Generators.integers(0, 99).next(), null,
                new RoleArtifact.Questions(0, 0), Map.of(), 400_000, 3);
    }

    /** @return a generated version of the form major.minor.patch */
    private static String version() {
        return Generators.integers(0, 99).next() + "." + Generators.integers(0, 99).next() + "."
                + Generators.integers(0, 99).next();
    }

    /** The environment of the test worker: the allowlist of the fixture, as an EasyMock double. */
    private static SubprocessJobRunner.Environment environment() {
        SubprocessJobRunner.Environment environment = createMock(SubprocessJobRunner.Environment.class);
        expect(environment.of(anyObject(), anyObject())).andReturn(ENVIRONMENT).anyTimes();
        expect(environment.ofWorker(anyObject(), anyObject(), anyObject(), anyObject(), anyObject(), anyObject()))
                .andReturn(ENVIRONMENT).anyTimes();
        replay(environment);
        return environment;
    }
}
