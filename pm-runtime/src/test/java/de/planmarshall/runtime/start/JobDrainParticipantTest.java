/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.start;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import de.planmarshall.core.service.JobRunner;
import de.planmarshall.core.service.JobRunner.CancelCause;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.JobRecord;
import de.planmarshall.core.store.JobRecord.Fate;
import de.planmarshall.core.store.JobRecord.Scope;
import de.planmarshall.core.store.JobRecord.Status;
import de.planmarshall.core.store.JobRecordStore;
import de.planmarshall.runtime.job.FakePmExec;
import de.planmarshall.runtime.job.PmExecLauncher;
import de.planmarshall.runtime.job.ProbeCandidate;
import de.planmarshall.runtime.job.ProbeCandidate.DigestedFile;
import de.planmarshall.runtime.job.ProbeCandidate.JobPrivate;
import de.planmarshall.runtime.job.ProcessGroupSignals;
import de.planmarshall.runtime.job.SubprocessJobRunner;
import de.planmarshall.runtime.job.TerminationProtocol;
import de.planmarshall.runtime.job.VerifyNotResolvePipeline;
import de.planmarshall.runtime.test.TestBases;

/**
 * The drain participant of the job runner (PM-ARCH-7, PM-EXEC-4) over a real {@link SubprocessJobRunner}, the fake
 * launcher {@link FakePmExec} and the signals of the system. The jobs are probes of the machine scope, which need no
 * enrolment; the probed program is a shell script that sleeps for its first argument, a generated number of seconds.
 * The environment, the script and the outcome row are the contract of the launcher and the outcome derivation.
 */
@EnabledOnOs(OS.LINUX)
@EnableGeneratorController
@EnableTestLogger
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("Drain participant of the job runner")
class JobDrainParticipantTest {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Scope MACHINE = new Scope(Scope.Type.MACHINE, null);
    /** Contract literal: the search path of the job environment the launcher hands over. */
    private static final Map<String, String> ENVIRONMENT = Map.of("PATH", "/usr/bin:/bin");
    /** Contract literal: the outcome row of a job the drain killed. */
    private static final int KILLED_ROW = 4;
    private static final String PROBE_SCRIPT = """
            #!/bin/sh
            exec @SLEEP@ "$1"
            """;

    private Path base;
    private Path probe;
    private SubprocessJobRunner runner;
    private JobDrainParticipant participant;

    @BeforeEach
    void setUp() throws IOException {
        base = Files.createDirectories(TestBases.create("pmdrn")).toRealPath();
        var enrolment = new EnrolmentFixture(base);
        probe = base.resolve("found/probe");
        Files.createDirectories(probe.getParent());
        Files.writeString(probe, PROBE_SCRIPT.replace("@SLEEP@", FakePmExec.tool("sleep").toString()));
        Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("rwx------"));
        var home = FakePmExec.install(base.resolve("home"), FakePmExec.Mode.REPORT);
        runner = SubprocessJobRunner.builder().base(base).lockManager(enrolment.locks)
                .pipeline(new VerifyNotResolvePipeline(enrolment.check, (buildSystem, verb) -> Optional.empty()))
                .launcher(new PmExecLauncher(home)).termination(new TerminationProtocol(ProcessGroupSignals.system()))
                .environment((job, jobId) -> ENVIRONMENT).probeEnvironment((candidate, jobId) -> ENVIRONMENT)
                .parser((kind, output) -> {
                    throw new AssertionError("the output of a probe is left to the step that asked for it");
                }).primitives(request -> Optional.empty()).build();
        participant = new JobDrainParticipant(runner);
    }

    @AfterEach
    void tearDown() {
        try {
            for (var jobId : runner.runningJobs()) {
                runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
                runner.awaitSettled(jobId, SETTLE_TIMEOUT);
            }
            runner.settleQueued();
        } finally {
            TestBases.delete(base);
        }
    }

    /** @return a generated sleep that outlasts every wait of a test */
    private static String longSleep() {
        return String.valueOf(Generators.integers(120, 600).next());
    }

    /** @return a generated sleep that ends well within the settle timeout */
    private static String shortSleep() {
        return String.valueOf(Generators.integers(1, 2).next());
    }

    private JobId start(String seconds) {
        var candidate = new ProbeCandidate(ProbeCandidate.Kind.LOGIN, new DigestedFile(probe, sha256(probe)),
                null, List.of(seconds), Set.of(0), MACHINE, new JobPrivate());
        var result = runner.startProbe(new JobRunner.ProbeRequest<>(candidate, true));
        return assertInstanceOf(JobRunner.StartResult.Started.class, result, "the probe should be started").jobId();
    }

    private JobRecord storedRecord(JobId jobId) {
        return JobRecordStore.ofMachine(base).read(jobId).orElseThrow();
    }

    private static String sha256(Path file) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    @DisplayName("a job created during the drain is recorded queued and settled killed at exit without a signal")
    void jobCreatedDuringDrain() {
        participant.stopAdmitting();
        var jobId = start(longSleep());
        var whileDraining = storedRecord(jobId);
        var queuedJobs = runner.queuedJobs();

        participant.close();

        var settled = storedRecord(jobId);
        assertAll(
                () -> assertEquals(Status.QUEUED, whileDraining.status(), "the job must be queued while draining"),
                () -> assertEquals(Set.of(jobId), queuedJobs, "the job must be the only queued job"),
                () -> assertTrue(runner.runningJobs().isEmpty(), "a job queued in the drain never spawns"),
                () -> assertEquals(Status.ENDED, settled.status(), "the queued job must be ended at exit"),
                () -> assertEquals(Fate.KILLED, settled.fate(), "the queued job must be settled killed"),
                () -> assertEquals(KILLED_ROW, settled.outcomeRow(), "the queued job takes outcome row 4"),
                () -> assertNull(settled.signal(), "a job never spawned carries no signal"),
                () -> assertNull(settled.pid(), "a job never spawned carries no PID"),
                () -> assertNull(settled.spawnedAt(), "a job never spawned has no spawn instant"),
                () -> assertFalse(Files.exists(JobRecordStore.ofMachine(base).logPath(jobId)),
                        "a job never spawned has no job log"),
                () -> assertTrue(runner.queuedJobs().isEmpty(), "no job must stay queued after the exit"));
    }

    @Test
    @DisplayName("a job running past the drain timeout is terminated and killed, and no longer counts as running")
    void runningPastDrainTimeout() throws Exception {
        var jobId = start(longSleep());
        participant.stopAdmitting();

        var quiet = participant.awaitQuiet(Instant.now().plusMillis(200));
        var terminated = participant.terminateRemaining();

        var settled = storedRecord(jobId);
        assertAll(
                () -> assertFalse(quiet, "the job still runs at the drain timeout"),
                () -> assertEquals(List.of("job " + jobId), terminated, "the running job must be terminated"),
                () -> assertEquals(Status.ENDED, settled.status(), "the terminated job must be ended"),
                () -> assertEquals(Fate.KILLED, settled.fate(), "the terminated job must be settled killed"),
                () -> assertEquals(KILLED_ROW, settled.outcomeRow(), "the terminated job takes outcome row 4"),
                () -> assertTrue(Set.of("SIGTERM", "SIGKILL").contains(settled.signal()),
                        "the job must end by SIGTERM or SIGKILL"),
                () -> assertTrue(runner.runningJobs().isEmpty(), "the job's place among the running jobs is free"));
    }

    @Test
    @DisplayName("matched control: a job admitted before the drain finishes normally")
    void admittedBeforeDrainFinishes() throws Exception {
        var jobId = start(shortSleep());
        participant.stopAdmitting();

        var quiet = participant.awaitQuiet(Instant.now().plus(SETTLE_TIMEOUT));
        var terminated = participant.terminateRemaining();
        participant.close();

        var settled = storedRecord(jobId);
        assertAll(
                () -> assertTrue(quiet, "the job ended within the drain timeout"),
                () -> assertEquals(List.of(), terminated, "nothing is left to terminate after it ended"),
                () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the admitted job must have succeeded"),
                () -> assertEquals(0L, settled.exitCode(), "the admitted job must exit with code 0"),
                () -> assertNull(settled.signal(), "a job that ended by itself has no signal"));
    }

    @Test
    @DisplayName("a deadline already passed ends the wait at once while a job runs")
    void passedDeadline() throws Exception {
        start(longSleep());

        assertFalse(participant.awaitQuiet(Instant.now().minusSeconds(1)),
                "a passed deadline must not report quiet");
    }

    @Test
    @DisplayName("the participant is named jobs (the specified drain name), and a job is never idle")
    void nameAndIdle() {
        participant.endIdle();

        assertAll(
                () -> assertEquals(JobDrainParticipant.DRAIN_NAME, participant.name(),
                        "the participant must carry its drain name"),
                () -> assertEquals("jobs", participant.name(), "the drain name must be the specified jobs"),
                () -> assertEquals(JobDrainParticipant.TERMINATION_WAIT,
                        TerminationProtocol.GRACE_PERIOD.plusSeconds(10),
                        "the termination wait is the grace period plus 10 s"));
    }
}
