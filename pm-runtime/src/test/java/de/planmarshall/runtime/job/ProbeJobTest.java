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
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.words;
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
 * <p>
 * Contract literals that stay: the modes of the probe script, the launcher's exit code 127, the outcome rows, the
 * budget keys, the store layout below the base ({@code machine/jobs}, {@code onboardings}, {@code run/jobs},
 * {@code cwd}), the reason codes and the sleeps of the script, which only have to outlive the budget or the refresh.
 * The host {@code PATH} the fake launcher runs with is a fixture path. The exit code the probe declares, the entry id,
 * the link names and the changed probe content are generated.
 */
@EnabledOnOs(OS.LINUX)
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Probe jobs of SubprocessJobRunner")
class ProbeJobTest {

    private static final Duration SETTLE_TIMEOUT = Duration.ofSeconds(30);
    private static final Scope MACHINE = new Scope(Scope.Type.MACHINE, null);

    private static final String PROBE_SCRIPT = """
            #!/bin/sh
            case "$1" in
              version) echo "probe 1.0"; exit 0 ;;
              declared) echo "probe 1.0"; exit @DECLARED@ ;;
              declared127) echo "probe 1.0"; exit 127 ;;
              pwd) pwd; exit 0 ;;
              detach) @SETSID@ @SLEEP@ 120 & echo $! > @PIDFILE@; @SLEEP@ 1.5; exit 0 ;;
              sleep) exec @SLEEP@ "$2" ;;
            esac
            exit 2
            """;

    private JobEnrolmentFixture fixture;
    private Path probe;
    private Path home;
    private SubprocessJobRunner runner;
    private Map<String, String> environment;

    /** The exit code the probe's mode {@code declared} ends with: not 0, 2 or the launcher's 127. */
    private int declared;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new JobEnrolmentFixture();
        declared = Generators.integers(3, 126).next();
        // The host PATH the fake launcher needs to run: a fixture path.
        environment = Map.of("PATH", "/usr/bin:/bin");
        probe = fixture.base.resolve("found/probe");
        Files.createDirectories(probe.getParent());
        installProbe(fixture.base.resolve("detached.pid"));
        home = FakePmExec.install(fixture.base.resolve("home"), FakePmExec.Mode.REPORT);
        runner = runner(fixture.base);
    }

    private void installProbe(Path pidFile) throws IOException {
        Files.writeString(probe, PROBE_SCRIPT.replace("@SLEEP@", FakePmExec.tool("sleep").toString())
                .replace("@SETSID@", FakePmExec.tool("setsid").toString())
                .replace("@PIDFILE@", pidFile.toString())
                .replace("@DECLARED@", Integer.toString(declared)));
        Files.setPosixFilePermissions(probe, PosixFilePermissions.fromString("rwx------"));
    }

    private SubprocessJobRunner runner(Path base) {
        return SubprocessJobRunner.builder().base(base).lockManager(fixture.locks)
                .pipeline(fixture.pipeline()).launcher(new PmExecLauncher(home))
                .termination(new TerminationProtocol(ProcessGroupSignals.system()))
                .environment((job, jobId) -> environment).probeEnvironment((candidate, jobId) -> environment)
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
        var jobId = started(onMachine(Set.of(declared), "declared"));

        var settled = settled(jobId);

        var store = JobRecordStore.ofMachine(fixture.base);
        assertAll(
                () -> assertEquals(JobId.Kind.PROBE, jobId.kind(), "the job id is of the probe kind"),
                () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "a declared exit code succeeds"),
                () -> assertEquals(7, settled.outcomeRow(), "the success is row 7"),
                () -> assertEquals((long) declared, settled.exitCode(), "the declared exit code is recorded"),
                () -> assertEquals("probe:version", settled.budgetKey(), "the budget key is probe:version"),
                () -> assertEquals(ProbeBudget.VERSION_SECONDS, settled.budgetSeconds(),
                        "the budget is the version probe's"),
                () -> assertEquals(MACHINE, settled.scope(), "the probe runs in the machine scope"),
                () -> assertEquals(Optional.of(settled), store.read(jobId), "the record lies in the machine store"),
                () -> assertTrue(Files.isRegularFile(store.logPath(jobId)), "the log lies beside the record"),
                () -> assertTrue(store.logPath(jobId).startsWith(fixture.base.resolve("machine/jobs")),
                        "the log lies below machine/jobs"));
    }

    @Test
    @DisplayName("an exit code the probe does not declare leaves its exit code to the step that asked")
    void undeclaredExitIsLeftToTheStep() {
        var jobId = started(onMachine(Set.of(declared), "version"));

        var settled = settled(jobId);

        assertAll(
                () -> assertEquals(Fate.INDETERMINATE, settled.fate(), "an undeclared exit is indeterminate"),
                () -> assertEquals(8, settled.outcomeRow(), "the undeclared exit is row 8"),
                () -> assertEquals(0L, settled.exitCode(), "the probe's exit code is recorded"));
    }

    @Test
    @DisplayName("a launcher failure that exits 127 does not settle a probe that declares 127 succeeded")
    void launcherFailureIsNotADeclaredSuccess() throws Exception {
        FakePmExec.install(home, FakePmExec.Mode.EXIT_127);

        var jobId = started(onMachine(Set.of(0, 127), "version"));

        var settled = settled(jobId);

        assertAll(
                () -> assertEquals(Status.ENDED, settled.status(), "the probe record has ended"),
                () -> assertEquals(Fate.FAILED, settled.fate(), "the launcher never started the probe"),
                () -> assertEquals(10, settled.outcomeRow(), "row 7 would read the declared code of a job not started"),
                () -> assertEquals(127L, settled.exitCode(), "the launcher's 127 is recorded"));
    }

    @Test
    @DisplayName("matched control: a probe that ends with the 127 it declares, after its launcher reported, succeeded")
    void declared127FromTheProbe() {
        var jobId = started(onMachine(Set.of(0, 127), "declared127"));

        var settled = settled(jobId);

        assertAll(
                () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the probe's own 127 succeeds"),
                () -> assertEquals(7, settled.outcomeRow(), "the success is row 7"),
                () -> assertEquals(127L, settled.exitCode(), "the probe's 127 is recorded"));
    }

    @Test
    @DisplayName("a descendant that left the process group is ended with the probe")
    void descendantOutsideTheGroupIsEnded() throws Exception {
        var pidFile = fixture.base.resolve("detached.pid");
        var jobId = started(onMachine(Set.of(0), "detach"));

        var settled = settled(jobId);
        var descendant = Long.parseLong(Files.readString(pidFile).trim());
        awaitDead(descendant);

        assertAll(
                () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the detaching probe succeeds"),
                () -> assertTrue(settled.jobWallTime().orElseThrow().toMillis() >= 1000,
                        "the probe ran long enough to be refreshed"),
                () -> assertFalse(alive(descendant), "the descendant is not left running"));
    }

    /** Waits until a PID is no longer alive, so a killed process is not seen as a zombie for one instant. */
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

    @Test
    @DisplayName("matched control: a probe that starts no descendant leaves nothing to end")
    void noDescendantToEnd() {
        var jobId = started(onMachine(Set.of(0), "version"));

        var settled = settled(jobId);

        assertAll(
                () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the plain probe succeeds"),
                () -> assertFalse(Files.exists(fixture.base.resolve("detached.pid")),
                        "no descendant was started"));
    }

    @Test
    @DisplayName("a probe whose launcher cannot be started settles failed, its declared 127 does not apply")
    void missingLauncherIsNotADeclaredSuccess() throws Exception {
        Files.delete(home.resolve("bin/pm-exec"));

        var jobId = started(onMachine(Set.of(0, 127), "version"));

        var settled = settled(jobId);

        assertAll(
                () -> assertEquals(Status.ENDED, settled.status(), "the probe record has ended"),
                () -> assertEquals(Fate.FAILED, settled.fate(), "a missing launcher fails the probe"),
                () -> assertEquals(10, settled.outcomeRow(), "the failure is row 10"),
                () -> assertNull(settled.exitCode(), "no exit was observed"));
    }

    @Test
    @DisplayName("a probe of a machine program runs in the empty cwd of its job-private directory")
    void jobPrivateWorkingDirectory() {
        var jobId = started(onMachine(Set.of(0), "pwd"));

        settled(jobId);

        var cwd = runner.jobPrivateCwd(jobId);
        var log = new JobLog(JobRecordStore.ofMachine(fixture.base).logPath(jobId));
        var lines = assertInstanceOf(JobLog.Lines.class, log.readLines(0), "the probe log answers lines");
        assertAll(
                () -> assertTrue(Files.isDirectory(cwd), "the job-private cwd exists"),
                () -> assertEquals(fixture.base.resolve("run/jobs").resolve(jobId.value()).resolve("cwd"), cwd,
                        "the cwd lies in the job-private directory"),
                () -> assertEquals(List.of(cwd.toString()), lines.lines(), "the probe printed its cwd"));
    }

    @Test
    @DisplayName("a base reached through a symbolic link runs the probe in the job-private cwd below the real base")
    void symlinkedBase() throws Exception {
        var linkedBase = Files.createSymbolicLink(fixture.base.resolve("linked-" + words().next()), fixture.base);
        runner = runner(linkedBase);
        var jobId = started(onMachine(Set.of(0), "pwd"));

        var settled = settled(jobId);

        var cwd = runner.jobPrivateCwd(jobId);
        var log = new JobLog(JobRecordStore.ofMachine(fixture.base).logPath(jobId));
        var lines = assertInstanceOf(JobLog.Lines.class, log.readLines(0), "the probe log answers lines");
        assertAll(
                () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the probe settles as succeeded"),
                () -> assertEquals(7, settled.outcomeRow(), "the success is row 7"),
                () -> assertEquals(fixture.base.resolve("run/jobs").resolve(jobId.value()).resolve("cwd"), cwd,
                        "the cwd lies below the real base"),
                () -> assertEquals(List.of(cwd.toString()), lines.lines(), "the probe printed the real cwd"));
    }

    @Test
    @DisplayName("matched control: a symbolic link below the base, out of the job-private directory, is refused")
    void symlinkBelowBaseRefused() throws Exception {
        var elsewhere = Files.createDirectories(fixture.base.resolve("elsewhere-" + words().next()));
        Files.createSymbolicLink(fixture.base.resolve("run"), elsewhere);

        var result = runner.startProbe(new JobRunner.ProbeRequest<>(onMachine(Set.of(0), "pwd"), true));

        var refused = assertInstanceOf(JobRunner.StartResult.Refused.class, result, "the probe is refused");
        var records = JobRecordStore.ofMachine(fixture.base).records();
        assertAll(
                () -> assertEquals("path_traversal_rejected", refused.reason(),
                        "the refusal is path_traversal_rejected"),
                () -> assertEquals(1, records.size(), "one refusal record is written"),
                () -> assertEquals(Fate.REFUSED, records.getFirst().fate(), "the record's fate is refused"),
                () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"));
    }

    @Test
    @DisplayName("a probe of an onboarding entry runs in its checkout, its record and log in the entry's store")
    void entryStore() throws Exception {
        var entryId = "entry-" + words().next();
        var checkout = Files.createDirectories(fixture.base.resolve("checkout"));
        Files.createDirectories(checkout.resolve(ProbeVerification.GIT));
        var entry = new Scope(Scope.Type.ENTRY, entryId);
        var jobId = started(candidate(ProbeCandidate.Kind.SCRIPT, Set.of(0), entry, new CheckoutRoot(checkout),
                "pwd"));

        var settled = settled(jobId);

        var store = JobRecordStore.ofEntry(fixture.base, entryId);
        assertAll(
                () -> assertEquals(Fate.SUCCEEDED, settled.fate(), "the entry probe succeeds"),
                () -> assertEquals("probe:script", settled.budgetKey(), "the budget key is probe:script"),
                () -> assertEquals(Optional.of(settled), store.read(jobId), "the record lies in the entry's store"),
                () -> assertTrue(store.logPath(jobId).startsWith(fixture.base.resolve("onboardings")
                        .resolve(entryId).resolve("jobs")), "the log lies below the entry's jobs"),
                () -> assertTrue(JobRecordStore.ofMachine(fixture.base).records().isEmpty(),
                        "the machine store holds no record"));
    }

    @Test
    @DisplayName("a probe over its fixed budget ends timed_out and is not started again")
    void overBudget() {
        var jobId = started(onMachine(Set.of(0), "sleep", "120"));

        var settled = settled(jobId);

        var records = JobRecordStore.ofMachine(fixture.base).records();
        assertAll(
                () -> assertEquals(Fate.TIMED_OUT, settled.fate(), "the probe over budget timed out"),
                () -> assertEquals(3, settled.outcomeRow(), "the timeout is row 3"),
                () -> assertTrue(settled.jobWallTime().orElseThrow().toSeconds() >= ProbeBudget.VERSION_SECONDS,
                        "the probe ran its whole budget"),
                () -> assertEquals(List.of(settled), records, "one record: the probe was not started again"),
                () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"));
    }

    @Test
    @DisplayName("a probe without the consent of its kind is never started and leaves no record")
    void withoutConsent() {
        var result = runner.startProbe(new JobRunner.ProbeRequest<>(onMachine(Set.of(0), "version"), false));

        assertAll(
                () -> assertEquals(new JobRunner.StartResult.Refused(SubprocessJobRunner.CONSENT_REQUIRED), result,
                        "the probe is refused for consent"),
                () -> assertTrue(JobRecordStore.ofMachine(fixture.base).records().isEmpty(),
                        "no record is written"),
                () -> assertFalse(Files.exists(fixture.base.resolve("run/jobs")),
                        "no job-private directory is made"));
    }

    @Test
    @DisplayName("a probe whose executable changed after the scan is refused and recorded refused, nothing spawned")
    void digestMismatch() throws Exception {
        var candidate = onMachine(Set.of(0), "version");
        Files.writeString(probe, "#!/bin/sh\n# " + Generators.letterStrings(1, 64).next() + "\nexit 0\n");

        var result = runner.startProbe(new JobRunner.ProbeRequest<>(candidate, true));

        var refused = assertInstanceOf(JobRunner.StartResult.Refused.class, result, "the changed probe is refused");
        var records = JobRecordStore.ofMachine(fixture.base).records();
        assertAll(
                () -> assertEquals("executable_digest_mismatch", refused.reason(),
                        "the refusal is executable_digest_mismatch"),
                () -> assertEquals(1, records.size(), "one refusal record is written"),
                () -> assertEquals(Status.REFUSED, records.getFirst().status(), "the record's status is refused"),
                () -> assertEquals(1, records.getFirst().outcomeRow(), "the refusal is row 1"),
                () -> assertEquals("executable_digest_mismatch", records.getFirst().refusalReason(),
                        "the record names the refusal reason"),
                () -> assertFalse(Files.exists(JobRecordStore.ofMachine(fixture.base)
                        .logPath(records.getFirst().jobId())), "no job log shows nothing was spawned"),
                () -> assertTrue(runner.runningJobs().isEmpty(), "no job is left running"));
    }
}
