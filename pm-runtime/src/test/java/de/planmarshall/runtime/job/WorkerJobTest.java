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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.ProjectEnrolment;
import de.planmarshall.runtime.launch.JobTokenStore;
import de.planmarshall.runtime.launch.LaunchPrompt;
import de.planmarshall.runtime.launch.LaunchRefusal;
import de.planmarshall.runtime.launch.ResolvedSkill;
import de.planmarshall.runtime.launch.WorkerLaunch;
import de.planmarshall.runtime.security.JobTokens;
import de.planmarshall.runtime.security.Secrets;

/**
 * A worker started through the subprocess runner (PM-WORK-3, Job Record): the generation id is the job id, the record
 * carries the {@code worker} block, the job token is revoked with the fate, and a descendant that leaves the process
 * group is ended with the worker.
 */
@EnabledOnOs(OS.LINUX)
@DisplayName("Worker jobs of SubprocessJobRunner")
class WorkerJobTest {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Scope PROJECT = new Scope(Scope.Type.PROJECT, null);
    private static final String GENERATION = "l-0123456789abcdef";
    private static final String TOKEN = "job-token";
    private static final Map<String, String> ENVIRONMENT = Map.of("PATH", "/usr/bin:/bin");

    private static final String WORKER_SCRIPT = """
            #!/bin/sh
            case "$1" in
              ok) echo done; exit 0 ;;
              detach) @SETSID@ @SLEEP@ 120 & echo $! > @PIDFILE@; @SLEEP@ 1.5; exit 0 ;;
            esac
            exit 2
            """;

    private JobEnrolmentFixture fixture;
    private Path home;
    private Path script;
    private JobTokenStore tokens;
    private SubprocessJobRunner runner;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new JobEnrolmentFixture();
        script = fixture.base.resolve("worker/run");
        Files.createDirectories(script.getParent());
        Files.writeString(script, WORKER_SCRIPT.replace("@SLEEP@", FakePmExec.tool("sleep").toString())
                .replace("@SETSID@", FakePmExec.tool("setsid").toString()).replace("@PIDFILE@", fixture.base.resolve(
                "detached.pid").toString()));
        Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwx------"));
        home = FakePmExec.install(fixture.base.resolve("home"), FakePmExec.Mode.REPORT);
        tokens = new JobTokenStore();
        runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(fixture.locks)
                .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                .termination(new TerminationProtocol(ProcessGroupSignals.system())).environment(new WorkerEnvironment())
                .probeEnvironment((candidate, jobId) -> ENVIRONMENT).parser((kind, output) -> new JobEvidence.ParseResult(
                true, 0, false, Optional.empty())).primitives(request -> Optional.empty()).jobTokens(tokens)
                .build();
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
    @DisplayName("a worker is recorded as kind llm with its worker block and its token revoked")
    void startedWorker() {
        var result = runner.startWorker(start("ok"));
        var jobId = assertInstanceOf(JobRunner.StartResult.Started.class, result, "the worker should be started")
                .jobId();
        var settled = runner.awaitSettled(jobId, SETTLE_TIMEOUT).orElseThrow();
        assertAll(
                () -> assertEquals(JobId.Kind.LLM, jobId.kind()),
                () -> assertEquals(Status.ENDED, settled.status()),
                () -> assertEquals(Fate.SUCCEEDED, settled.fate()),
                () -> assertTrue(settled.worker().contains("implementer")),
                () -> assertTrue(tokens.resolve(Secrets.sha256(TOKEN)).isEmpty(), "the token is revoked with the fate"));
    }

    @Test
    @DisplayName("a descendant that left the process group is ended with the worker")
    void descendantOutsideTheGroupIsEnded() throws Exception {
        var pidFile = fixture.base.resolve("detached.pid");
        var result = runner.startWorker(start("detach"));
        var jobId = assertInstanceOf(JobRunner.StartResult.Started.class, result).jobId();
        var settled = runner.awaitSettled(jobId, SETTLE_TIMEOUT).orElseThrow();
        var descendant = Long.parseLong(Files.readString(pidFile).trim());
        awaitDead(descendant);
        assertAll(
                () -> assertEquals(Fate.SUCCEEDED, settled.fate()),
                () -> assertFalse(alive(descendant), "the descendant is not left running: " + descendant));
    }

    @Test
    @DisplayName("a refused worker records its fate, its refusal reason and its worker block")
    void refusedWorker() {
        var jobId = runner.refuseWorker(new WorkerStart.Refusal(GENERATION, role(), fixture.enrolment(), PROJECT,
                "worker:implementer", 600, new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT,
                        null), "{\"role\":\"implementer\"}", LaunchRefusal.HARNESS_NOT_ENROLLED,
                "the harness 'claude' is not enrolled"));
        var settled = runner.recordOf(jobId).orElseThrow();
        assertAll(
                () -> assertEquals(Status.REFUSED, settled.status()),
                () -> assertEquals(Fate.REFUSED, settled.fate()),
                () -> assertEquals(LaunchRefusal.HARNESS_NOT_ENROLLED.code(), settled.refusalReason()),
                () -> assertTrue(settled.worker().contains("implementer")));
    }

    private WorkerStart start(String mode) {
        var prompt = new LaunchPrompt("prompt", ResolvedSkill.sha256("prompt"), List.of(), 6);
        var launch = new WorkerLaunch(GENERATION, "implementer", RoleClass.WRITER,
                RoleForm.FRESH, List.of(script.toString(), mode), ENVIRONMENT, script
                        .getParent(), fixture.base.resolve("run/jobs").resolve(GENERATION), script, prompt, Optional
                        .empty(), Optional.empty(), TOKEN, JobTokens.sha256Hex(TOKEN), "claude", "1.5.0", "m",
                null, null, JsonDocument.parse("{\"role\":\"implementer\"}".getBytes(
                        StandardCharsets.UTF_8), "worker"));
        return new WorkerStart(launch, fixture.enrolment(), PROJECT, "worker:implementer", 600,
                new JobRecord.BudgetDerivation(JobRecord.BudgetDerivation.Kind.FLOOR_DEFAULT, null), List.of(),
                Optional.empty());
    }

    private static EffectiveRole role() {
        var artifact = new RoleArtifact(1, "implementer", "the implementer",
                RoleClass.WRITER, List.of("read_file"), "claude", "frontier",
                new EffortValue("high"), RoleForm.FRESH, 1,
                List.of("skill://pm/role/implementer/SKILL.md"), null, 60, null, new RoleArtifact
                        .Questions(0, 0), Map.of());
        return new EffectiveRole(artifact, "claude", "frontier",
                new EffortValue("high"), List.of(), "1.5", Optional.empty(),
                new RoleArtifact.Questions(0, 0), Map.of(), 400_000, 3);
    }

    private static void awaitDead(long pid) {
        var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (alive(pid)) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("the descendant %d is still alive".formatted(pid));
            }
            Thread.onSpinWait();
        }
    }

    private static boolean alive(long pid) {
        return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
    }

    /** The environment of the test worker: the allowlist of the fixture. */
    private static final class WorkerEnvironment implements SubprocessJobRunner.Environment {

        @Override
        public Map<String, String> of(VerifiedJob job, JobId jobId) {
            return ENVIRONMENT;
        }

        @Override
        public Map<String, String> ofWorker(ProjectEnrolment enrolment, JobId jobId,
                Map<String, String> harnessSwitches, Optional<String> jobToken, Optional<String> workerDataVariable,
                Optional<String> egressProxy) {
            return ENVIRONMENT;
        }
    }
}
