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
 * enrolment; the probed program is a shell script that sleeps for its first argument.
 */
@EnabledOnOs(OS.LINUX)
@EnableTestLogger
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@DisplayName("Drain participant of the job runner")
class JobDrainParticipantTest {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Scope MACHINE = new Scope(Scope.Type.MACHINE, null);
    private static final Map<String, String> ENVIRONMENT = Map.of("PATH", "/usr/bin:/bin");
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
                .environment(job -> ENVIRONMENT).probeEnvironment(candidate -> ENVIRONMENT)
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

    private JobId start(String seconds) {
        var candidate = new ProbeCandidate(ProbeCandidate.Kind.LOGIN, new DigestedFile(probe, sha256(probe)),
                Optional.empty(), List.of(seconds), Set.of(0), MACHINE, new JobPrivate());
        var result = runner.startProbe(new JobRunner.ProbeRequest<>(candidate, true));
        return assertInstanceOf(JobRunner.StartResult.Started.class, result, "the probe should be started").jobId();
    }

    private JobRecord record(JobId jobId) {
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
        var jobId = start("120");
        var whileDraining = record(jobId);
        var queuedJobs = runner.queuedJobs();

        participant.close();

        var settled = record(jobId);
        assertAll(
                () -> assertEquals(Status.QUEUED, whileDraining.status()),
                () -> assertEquals(Set.of(jobId), queuedJobs),
                () -> assertTrue(runner.runningJobs().isEmpty(), "never spawned"),
                () -> assertEquals(Status.ENDED, settled.status()),
                () -> assertEquals(Fate.KILLED, settled.fate()),
                () -> assertEquals(4, settled.outcomeRow(), "outcome row 4"),
                () -> assertNull(settled.signal(), "no signal"),
                () -> assertNull(settled.pid(), "never spawned"),
                () -> assertNull(settled.spawnedAt(), "never spawned"),
                () -> assertFalse(Files.exists(JobRecordStore.ofMachine(base).logPath(jobId)), "no job log"),
                () -> assertTrue(runner.queuedJobs().isEmpty()));
    }

    @Test
    @DisplayName("a job running past the drain timeout is terminated and killed, and no longer counts as running")
    void runningPastDrainTimeout() throws InterruptedException {
        var jobId = start("120");
        participant.stopAdmitting();

        var quiet = participant.awaitQuiet(Instant.now().plusMillis(200));
        var terminated = participant.terminateRemaining();

        var settled = record(jobId);
        assertAll(
                () -> assertFalse(quiet, "the job still runs at the drain timeout"),
                () -> assertEquals(List.of("job " + jobId), terminated),
                () -> assertEquals(Status.ENDED, settled.status()),
                () -> assertEquals(Fate.KILLED, settled.fate()),
                () -> assertEquals(4, settled.outcomeRow(), "outcome row 4"),
                () -> assertTrue(Set.of("SIGTERM", "SIGKILL").contains(settled.signal()), settled::signal),
                () -> assertTrue(runner.runningJobs().isEmpty(), "the job's place among the running jobs is free"));
    }

    @Test
    @DisplayName("matched control: a job admitted before the drain finishes normally")
    void admittedBeforeDrainFinishes() throws InterruptedException {
        var jobId = start("1");
        participant.stopAdmitting();

        var quiet = participant.awaitQuiet(Instant.now().plus(SETTLE_TIMEOUT));
        var terminated = participant.terminateRemaining();
        participant.close();

        var settled = record(jobId);
        assertAll(
                () -> assertTrue(quiet, "the job ended within the drain timeout"),
                () -> assertEquals(List.of(), terminated),
                () -> assertEquals(Fate.SUCCEEDED, settled.fate()),
                () -> assertEquals(0L, settled.exitCode()),
                () -> assertNull(settled.signal()));
    }

    @Test
    @DisplayName("a deadline already passed ends the wait at once while a job runs")
    void passedDeadline() throws InterruptedException {
        start("120");

        assertFalse(participant.awaitQuiet(Instant.now().minusSeconds(1)));
    }

    @Test
    @DisplayName("the participant is named jobs, and a job is never idle")
    void nameAndIdle() {
        participant.endIdle();

        assertAll(
                () -> assertEquals(JobDrainParticipant.NAME, participant.name()),
                () -> assertEquals("jobs", participant.name()),
                () -> assertEquals(TerminationProtocol.GRACE_PERIOD.plusSeconds(10),
                        JobDrainParticipant.TERMINATION_WAIT));
    }
}
