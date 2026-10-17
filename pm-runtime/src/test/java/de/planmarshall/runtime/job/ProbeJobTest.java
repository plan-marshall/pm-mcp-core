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

import static de.planmarshall.runtime.job.JobEnrolmentFixture.sha256;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import de.planmarshall.runtime.job.ProbeCandidate.CheckoutRoot;
import de.planmarshall.runtime.job.ProbeCandidate.DigestedFile;
import de.planmarshall.runtime.job.ProbeCandidate.JobPrivate;

/**
 * Probe jobs of the subprocess runner (PM-EXEC-1, PM-EXEC-3, PM-EXEC-4) through the fake launcher {@link FakePmExec}:
 * a probe runs only with consent, its record and log lie in the store of its scope, an exit code it declares settles
 * it {@code succeeded}, any other end leaves its exit code to the step that asked, and a probe over its fixed budget
 * ends {@code timed_out} and is not started again. The probed program is a shell script whose first argument selects
 * what it does.
 */
@EnabledOnOs(OS.LINUX)
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Probe jobs of SubprocessJobRunner")
class ProbeJobTest {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Scope MACHINE = new Scope(Scope.Type.MACHINE, null);
    private static final String ENTRY_ID = "entry-one";
    private static final Map<String, String> ENVIRONMENT = Map.of("PATH", "/usr/bin:/bin");

    private static final String PROBE_SCRIPT = """
            #!/bin/sh
            case "$1" in
              version) echo "probe 1.0"; exit 0 ;;
              declared) echo "probe 1.0"; exit 3 ;;
              pwd) pwd; exit 0 ;;
              sleep) exec @SLEEP@ "$2" ;;
            esac
            exit 2
            """;

    private JobEnrolmentFixture fixture;
    private Path probe;
    private SubprocessJobRunner runner;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new JobEnrolmentFixture();
        probe = fixture.base.resolve("found/probe");
        Files.createDirectories(probe.getParent());
        Files.writeString(probe, PROBE_SCRIPT.replace("@SLEEP@", FakePmExec.tool("sleep").toString()));
        Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("rwx------"));
        var home = FakePmExec.install(fixture.base.resolve("home"), FakePmExec.Mode.REPORT);
        runner = SubprocessJobRunner.builder().base(fixture.base).lockManager(fixture.locks)
                .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                .termination(new TerminationProtocol(ProcessGroupSignals.system()))
                .environment(job -> ENVIRONMENT).probeEnvironment(candidate -> ENVIRONMENT)
                .parser((kind, output) -> {
                    throw new AssertionError("the output of a probe is left to the step that asked for it");
                }).primitives(request -> Optional.empty()).build();
    }

    @AfterEach
    void tearDown() {
        for (var jobId : runner.runningJobs()) {
            runner.cancel(jobId, CancelCause.ENGINE_CANCEL);
            runner.awaitSettled(jobId, SETTLE_TIMEOUT);
        }
        fixture.close();
    }

    private ProbeCandidate candidate(ProbeCandidate.Kind kind, Set<Integer> success, Scope scope,
            ProbeCandidate.WorkingDirectory workingDirectory, String... arguments) {
        return new ProbeCandidate(kind, new DigestedFile(probe, sha256(probe)), Optional.empty(), List.of(arguments),
                success, scope, workingDirectory);
    }

    private ProbeCandidate onMachine(Set<Integer> success, String... arguments) {
        return candidate(ProbeCandidate.Kind.VERSION, success, MACHINE, new JobPrivate(), arguments);
    }

    private JobId started(ProbeCandidate candidate) {
        var result = runner.startProbe(new JobRunner.ProbeRequest<>(candidate, true));
        return assertInstanceOf(JobRunner.StartResult.Started.class, result, "the probe should be started").jobId();
    }

    private JobRecord settled(JobId jobId) {
        return runner.awaitSettled(jobId, SETTLE_TIMEOUT)
                .orElseThrow(() -> new AssertionError("probe %s did not settle".formatted(jobId)));
    }

    @Test
    @DisplayName("an exit code the probe declares settles it succeeded, its record and log in the machine store")
    void declaredExitSucceeds() {
        var jobId = started(onMachine(Set.of(3), "declared"));

        var settled = settled(jobId);

        var store = JobRecordStore.ofMachine(fixture.base);
        assertAll(
                () -> assertEquals(JobId.Kind.PROBE, jobId.kind()),
                () -> assertEquals(Fate.SUCCEEDED, settled.fate()),
                () -> assertEquals(7, settled.outcomeRow()),
                () -> assertEquals(3L, settled.exitCode()),
                () -> assertEquals("probe:version", settled.budgetKey()),
                () -> assertEquals(ProbeBudget.VERSION_SECONDS, settled.budgetSeconds()),
                () -> assertEquals(MACHINE, settled.scope()),
                () -> assertEquals(Optional.of(settled), store.read(jobId)),
                () -> assertTrue(Files.isRegularFile(store.logPath(jobId)), "the log lies beside the record"),
                () -> assertTrue(store.logPath(jobId).startsWith(fixture.base.resolve("machine/jobs"))));
    }

    @Test
    @DisplayName("an exit code the probe does not declare leaves its exit code to the step that asked")
    void undeclaredExitIsLeftToTheStep() {
        var jobId = started(onMachine(Set.of(3), "version"));

        var settled = settled(jobId);

        assertAll(
                () -> assertEquals(Fate.INDETERMINATE, settled.fate()),
                () -> assertEquals(8, settled.outcomeRow()),
                () -> assertEquals(0L, settled.exitCode()));
    }

    @Test
    @DisplayName("a probe of a machine program runs in the empty cwd of its job-private directory")
    void jobPrivateWorkingDirectory() {
        var jobId = started(onMachine(Set.of(0), "pwd"));

        settled(jobId);

        var cwd = runner.jobPrivateCwd(jobId);
        var log = new JobLog(JobRecordStore.ofMachine(fixture.base).logPath(jobId));
        var lines = assertInstanceOf(JobLog.Lines.class, log.readLines(0));
        assertAll(
                () -> assertTrue(Files.isDirectory(cwd)),
                () -> assertEquals(fixture.base.resolve("run/jobs").resolve(jobId.value()).resolve("cwd"), cwd),
                () -> assertEquals(List.of(cwd.toString()), lines.lines()));
    }

    @Test
    @DisplayName("a probe of an onboarding entry runs in its checkout, its record and log in the entry's store")
    void entryStore() throws Exception {
        var checkout = Files.createDirectories(fixture.base.resolve("checkout"));
        Files.createDirectories(checkout.resolve(ProbeVerification.GIT));
        var entry = new Scope(Scope.Type.ENTRY, ENTRY_ID);
        var jobId = started(candidate(ProbeCandidate.Kind.SCRIPT, Set.of(0), entry, new CheckoutRoot(checkout),
                "pwd"));

        var settled = settled(jobId);

        var store = JobRecordStore.ofEntry(fixture.base, ENTRY_ID);
        assertAll(
                () -> assertEquals(Fate.SUCCEEDED, settled.fate()),
                () -> assertEquals("probe:script", settled.budgetKey()),
                () -> assertEquals(Optional.of(settled), store.read(jobId)),
                () -> assertTrue(store.logPath(jobId).startsWith(fixture.base.resolve("onboardings")
                        .resolve(ENTRY_ID).resolve("jobs"))),
                () -> assertTrue(JobRecordStore.ofMachine(fixture.base).records().isEmpty()));
    }

    @Test
    @DisplayName("a probe over its fixed budget ends timed_out and is not started again")
    void overBudget() {
        var jobId = started(onMachine(Set.of(0), "sleep", "120"));

        var settled = settled(jobId);

        var records = JobRecordStore.ofMachine(fixture.base).records();
        assertAll(
                () -> assertEquals(Fate.TIMED_OUT, settled.fate()),
                () -> assertEquals(3, settled.outcomeRow()),
                () -> assertTrue(settled.jobWallTime().orElseThrow().toSeconds() >= ProbeBudget.VERSION_SECONDS),
                () -> assertEquals(List.of(settled), records, "one record: the probe was not started again"),
                () -> assertTrue(runner.runningJobs().isEmpty()));
    }

    @Test
    @DisplayName("a probe without the consent of its kind is never started and leaves no record")
    void withoutConsent() {
        var result = runner.startProbe(new JobRunner.ProbeRequest<>(onMachine(Set.of(0), "version"), false));

        assertAll(
                () -> assertEquals(new JobRunner.StartResult.Refused(SubprocessJobRunner.CONSENT_REQUIRED), result),
                () -> assertTrue(JobRecordStore.ofMachine(fixture.base).records().isEmpty()),
                () -> assertFalse(Files.exists(fixture.base.resolve("run/jobs"))));
    }

    @Test
    @DisplayName("a probe whose executable changed after the scan is refused and recorded refused, nothing spawned")
    void digestMismatch() throws Exception {
        var candidate = onMachine(Set.of(0), "version");
        Files.writeString(probe, "#!/bin/sh\nexit 0\n");

        var result = runner.startProbe(new JobRunner.ProbeRequest<>(candidate, true));

        var refused = assertInstanceOf(JobRunner.StartResult.Refused.class, result);
        var records = JobRecordStore.ofMachine(fixture.base).records();
        assertAll(
                () -> assertEquals("executable_digest_mismatch", refused.reason()),
                () -> assertEquals(1, records.size()),
                () -> assertEquals(Status.REFUSED, records.getFirst().status()),
                () -> assertEquals(1, records.getFirst().outcomeRow()),
                () -> assertEquals("executable_digest_mismatch", records.getFirst().refusalReason()),
                () -> assertFalse(Files.exists(JobRecordStore.ofMachine(fixture.base)
                        .logPath(records.getFirst().jobId())), "nothing was spawned"),
                () -> assertTrue(runner.runningJobs().isEmpty()));
    }
}
