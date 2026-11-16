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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.core.profile.JsonDocument;
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
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.core.store.ProjectEnrolment.HostToolName;
import de.planmarshall.runtime.launch.JobTokenStore;
import de.planmarshall.runtime.launch.LaunchPrompt;
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
        runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(fixture.locks)
                .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                .termination(new TerminationProtocol(ProcessGroupSignals.system())).environment(new WorkerEnvironment())
                .probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                .parser((kind, output) -> new JobEvidence.ParseResult(true, 0, false, null))
                .primitives(request -> Optional.empty()).jobTokens(tokens).build();
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
    void workerEnforced() throws IOException {
        FakePmExec.install(home, FakePmExec.Mode.REPORT_GIT_METADATA);

        var settled = settleWorker();

        assertEquals(GitMetadata.ENFORCED, settled.gitMetadata(), "the report's value is copied into the record");
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
    @DisplayName("a worker whose launcher writes no report records unenforced")
    void workerWithoutReport() throws IOException {
        FakePmExec.install(home, FakePmExec.Mode.NO_REPORT);

        var settled = settleWorker();

        assertEquals(GitMetadata.UNENFORCED, settled.gitMetadata(), "a missing report leaves the honest value");
    }

    @Test
    @DisplayName("a git job records no git_metadata though its report carries it")
    void gitJobRecordsNone() throws IOException {
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

    /** @return a generated version of the form major.minor.patch */
    private static String version() {
        return Generators.integers(0, 99).next() + "." + Generators.integers(0, 99).next() + "."
                + Generators.integers(0, 99).next();
    }

    /** The environment of the test worker: the allowlist of the fixture. */
    private static final class WorkerEnvironment implements SubprocessJobRunner.Environment {

        @Override
        public Map<String, String> of(VerifiedJob job, JobId jobId) {
            return ENVIRONMENT;
        }

        @Override
        public Map<String, String> ofWorker(ProjectEnrolment enrolment, JobId jobId,
                Map<String, String> harnessSwitches, @Nullable String jobToken, @Nullable String workerDataVariable,
                @Nullable String egressProxy) {
            return ENVIRONMENT;
        }
    }
}
