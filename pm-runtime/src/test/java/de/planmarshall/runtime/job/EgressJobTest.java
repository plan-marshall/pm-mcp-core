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

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
import java.util.OptionalInt;
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
import de.planmarshall.core.store.JobRecord.Egress;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.runtime.launch.JobTokenStore;
import de.planmarshall.runtime.launch.LaunchPrompt;
import de.planmarshall.runtime.launch.LaunchRefusal;
import de.planmarshall.runtime.launch.ResolvedSkill;
import de.planmarshall.runtime.launch.WorkerLaunch;
import de.planmarshall.runtime.security.JobTokens;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The {@code egress} of a job record (PM-SEC-5, Job Runtime § Worker Egress Proxy): a worker records {@code unenforced}
 * until a launch report states the platform enforcement, and a refused worker records {@code unenforced} too. The records
 * of the other kinds are pinned where their fixtures live: a `git` and a `build` job in {@code SubprocessJobRunnerTest}
 * and a probe in {@code ProbeJobTest}.
 * <p>
 * The launcher is {@link FakePmExec}. It applies no network enforcement, so every worker record of this fixture is
 * {@code unenforced}. Contract literals that stay: the modes of the launcher, the host {@code PATH}, the harness
 * {@code claude}, the tool {@code read_file}, the effort {@code high} and the role bounds. The generation id, the job
 * token, the role name, the model and the versions are generated.
 */
@EnabledOnOs(OS.LINUX)
@EnableGeneratorController
@DisplayName("egress of a job record")
class EgressJobTest {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Scope PROJECT = new Scope(Scope.Type.PROJECT, null);
    private static final Map<String, String> ENVIRONMENT = Map.of("PATH", "/usr/bin:/bin");
    private static final BudgetDerivation FLOOR = new BudgetDerivation(BudgetDerivation.Kind.FLOOR_DEFAULT, null);

    private static final String WORKER_SCRIPT = """
            #!/bin/sh
            echo done
            exit 0
            """;

    private JobEnrolmentFixture fixture;
    private Path home;
    private Path script;
    private SubprocessJobRunner runner;
    private final List<JobRecord> atLaunch = new CopyOnWriteArrayList<>();
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
        runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(fixture.locks)
                .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                .termination(new TerminationProtocol(ProcessGroupSignals.system())).environment(environment())
                .probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                .parser((kind, output) -> new JobEvidence.ParseResult(true, 0, false, null))
                .primitives(request -> Optional.empty()).jobTokens(new JobTokenStore()).build();
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
    @DisplayName("a started worker records unenforced at every write point")
    void startedWorkerRecordsUnenforced() {
        var result = runner.startWorker(start());
        var jobId = assertInstanceOf(JobRunner.StartResult.Started.class, result, "the worker should be started")
                .jobId();
        var settled = runner.awaitSettled(jobId, SETTLE_TIMEOUT).orElseThrow();
        var stored = JobRecordStore.ofProject(fixture.base, fixture.project).read(jobId).orElseThrow();

        assertAll("the honest value is unenforced",
                () -> assertEquals(Egress.UNENFORCED, settled.egress(),
                        "the fate record states unenforced"),
                () -> assertEquals(Egress.UNENFORCED, stored.egress(),
                        "the stored fate record states unenforced"),
                () -> assertEquals(List.of(Egress.UNENFORCED),
                        atLaunch.stream().map(JobRecord::egress).distinct().toList(),
                        "the write-ahead record states unenforced"));
    }

    @Test
    @DisplayName("a worker's record with its PID keeps the honest value")
    void postSpawnRecordKeepsUnenforced() {
        var result = runner.startWorker(start());
        var jobId = assertInstanceOf(JobRunner.StartResult.Started.class, result, "the worker should be started")
                .jobId();
        var settled = runner.awaitSettled(jobId, SETTLE_TIMEOUT).orElseThrow();

        assertAll("the post-spawn replace derives from the record",
                () -> assertTrue(settled.pid() != null, "the settled record carries the post-spawn PID"),
                () -> assertEquals(Egress.UNENFORCED, settled.egress(),
                        "the record that carries the PID keeps the member"));
    }

    @Test
    @DisplayName("a refused worker records unenforced")
    void refusedWorkerRecordsUnenforced() {
        var jobId = runner.refuseWorker(new WorkerStart.Refusal(generation, role(), fixture.enrolment(), PROJECT,
                "worker:" + role, budgetSeconds, FLOOR, workerBlock(), LaunchRefusal.HARNESS_NOT_ENROLLED,
                Generators.letterStrings(8, 64).next()));

        var stored = runner.recordOf(jobId).orElseThrow();

        assertEquals(Egress.UNENFORCED, stored.egress(),
                "a refused worker records the platform state");
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
        return new WorkerStart(launch, fixture.enrolment(), PROJECT, "worker:" + role, budgetSeconds, FLOOR,
                List.of(), null);
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
                new RoleArtifact.Questions(0, 0), Map.of(), 400_000, OptionalInt.of(3));
    }

    /** @return a generated version of the form major.minor.patch */
    private static String version() {
        return Generators.integers(0, 99).next() + "." + Generators.integers(0, 99).next() + "."
                + Generators.integers(0, 99).next();
    }

    /** The environment of the test worker: the allowlist of the fixture, as an EasyMock double. */
    private SubprocessJobRunner.Environment environment() {
        SubprocessJobRunner.Environment environment = createMock(SubprocessJobRunner.Environment.class);
        expect(environment.of(anyObject(), anyObject())).andReturn(ENVIRONMENT).anyTimes();
        expect(environment.ofWorker(anyObject(), anyObject(), anyObject(), anyObject(), anyObject(), anyObject()))
                .andAnswer(() -> {
                    atLaunch.addAll(JobRecordStore.ofProject(fixture.base, fixture.project).records());
                    return ENVIRONMENT;
                }).anyTimes();
        replay(environment);
        return environment;
    }
}
