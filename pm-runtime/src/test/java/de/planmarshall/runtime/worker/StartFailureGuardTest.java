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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.nio.file.Path;
import java.time.Instant;
import java.time.InstantSource;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.WorkerState.ContextTokens;
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The start-failure guard (PM-WORK-3, Start-Failure Guard): the count of consecutive start failures, the hold, the
 * reset on a first call, the two clears, the probe and its backoff including a declined probe, the rebuild from the
 * generation registry, and the bounded redacted report.
 * <p>
 * The project, the role, the two harness names, the failure text, the generation ids, the model name and the digest are
 * generated in the {@code @BeforeEach}; the threshold, the backoff and the redaction shapes ({@code pmj_...}, an e-mail
 * address and a URI with user information) are the contracts the test proves.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("Start-failure guard")
class StartFailureGuardTest {

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private final List<AuditEvent> audits = new CopyOnWriteArrayList<>();

    private final List<String> closed = new CopyOnWriteArrayList<>();

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

    private final WorkerProtocol protocol = new RecordingProtocol();

    private String project;

    private String role;

    private String harness;

    private String otherHarness;

    private String otherRole;

    private String failure;

    private String model;

    private String digest;

    @BeforeEach
    void draw() {
        project = RuntimeGenerators.words().next();
        role = RuntimeGenerators.words().next();
        harness = RuntimeGenerators.words().next();
        otherHarness = harness + "-other";
        otherRole = role + "-other";
        failure = Generators.letterStrings(3, 40).next();
        model = RuntimeGenerators.words().next();
        digest = RuntimeGenerators.sha256Digests().next();
    }

    @Test
    @DisplayName("three consecutive failures hold the guard and close the role's tasks")
    void threeFailuresHold() {
        var guard = guard();

        guard.startFailed(role, harness, failure);
        guard.startFailed(role, harness, failure);
        var heldBefore = guard.holds(role, harness);
        guard.startFailed(role, harness, failure);

        assertAll(
                () -> assertFalse(heldBefore, "two failures do not hold yet"),
                () -> assertTrue(guard.holds(role, harness), "the third failure holds"),
                () -> assertEquals(List.of(role + "/" + harness), closed,
                        "the hold closes the role's queued and new tasks"),
                () -> assertEquals(3, audits.size(), "one audit record per counted failure"),
                () -> assertEquals(AuditEvent.WORKER_START_FAILED, audits.getFirst().event()),
                () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.WORKER_START_FAILED.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "(consecutive failure 3)"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        WARN.WORKER_START_GUARD_HELD.resolveIdentifierString()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "holds after 3 consecutive failures"));
    }

    @Test
    @DisplayName("a first call resets the count")
    void aFirstCallResetsTheCount() {
        var guard = guard();

        guard.startFailed(role, harness, failure);
        guard.startFailed(role, harness, failure);
        guard.firstCall(role, harness);
        guard.startFailed(role, harness, failure);

        assertAll(
                () -> assertFalse(guard.holds(role, harness), "the reset count starts over"),
                () -> assertEquals(3, audits.size(), "the reset does not drop the audit trail"));
    }

    @Test
    @DisplayName("a changed role configuration and a new enrolment each clear the hold")
    void aChangedConfigurationAndANewEnrolmentClear() {
        var changedConfiguration = guard();
        hold(changedConfiguration);
        changedConfiguration.clear(role, harness);

        var newEnrolment = guard();
        hold(newEnrolment);
        newEnrolment.clear(role, harness);

        assertAll(
                () -> assertFalse(changedConfiguration.holds(role, harness)),
                () -> assertFalse(newEnrolment.holds(role, harness)));
    }

    @Test
    @DisplayName("a declined probe restarts the backoff instead of holding forever")
    void aDeclinedProbeRestartsTheBackoff() {
        var guard = guard();
        hold(guard);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF));
        var probeAdmitted = !guard.holds(role, harness);

        guard.probeDeclined(role, harness);
        var heldWhileTheBackoffRuns = guard.holds(role, harness);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF.multipliedBy(2)).minusSeconds(1));
        var stillHeld = guard.holds(role, harness);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF.multipliedBy(2)));
        var nextProbeAdmitted = !guard.holds(role, harness);

        assertAll(
                () -> assertTrue(probeAdmitted, "the backoff admits the probe"),
                () -> assertTrue(heldWhileTheBackoffRuns, "a declined probe holds again"),
                () -> assertTrue(stillHeld, "the restarted backoff is not due yet"),
                () -> assertTrue(nextProbeAdmitted, "the restarted backoff admits the next probe"),
                () -> assertEquals(1, closed.size(), "a decline is not a start failure and closes nothing"),
                () -> assertEquals(3, audits.size(), "a decline counts no failure"));
    }

    @Test
    @DisplayName("the probe backoff admits one start, and the probe's first call clears the guard")
    void theProbeClearsTheGuard() {
        var guard = guard();
        hold(guard);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF));

        var probeAdmitted = !guard.holds(role, harness);
        var secondStartHeld = guard.holds(role, harness);
        guard.firstCall(role, harness);

        assertAll(
                () -> assertTrue(probeAdmitted, "the backoff admits the probe"),
                () -> assertTrue(secondStartHeld, "only one start is the probe"),
                () -> assertFalse(guard.holds(role, harness), "the probe's first call clears the guard"));
    }

    @Test
    @DisplayName("a failed probe restarts the backoff")
    void aFailedProbeRestartsTheBackoff() {
        var guard = guard();
        hold(guard);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF));
        guard.holds(role, harness);

        guard.startFailed(role, harness, failure);
        var heldBeforeTheRestart = guard.holds(role, harness);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF.multipliedBy(2)).minusSeconds(1));
        var stillHeld = guard.holds(role, harness);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF.multipliedBy(2)));
        var nextProbeAdmitted = !guard.holds(role, harness);

        assertAll(
                () -> assertTrue(heldBeforeTheRestart, "the failed probe holds again"),
                () -> assertTrue(stillHeld, "the restarted backoff is not due yet"),
                () -> assertTrue(nextProbeAdmitted, "the restarted backoff admits the next probe"),
                () -> assertEquals(2, closed.size(), "the hold and the failed probe each close the tasks"),
                () -> assertEquals(4, audits.size(), "the failed probe is a counted failure too"));
    }

    @Test
    @DisplayName("a guard rebuilt from the registry resumes the count of failed start generations")
    void aRebuildResumesTheCount() {
        var twoFailures = List.of(generation(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(),
                WorkerLifecycle.START_FAILED),
                generation(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(), WorkerLifecycle.START_FAILED));
        var rebuilt = new StartFailureGuard(project, () -> twoFailures, protocol, audits::add, clock());
        var heldBeforeTheThird = rebuilt.holds(role, harness);
        rebuilt.startFailed(role, harness, failure);
        var heldOnTheThird = rebuilt.holds(role, harness);

        var atThreshold = new StartFailureGuard(project,
                () -> List.of(generation(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(), WorkerLifecycle.START_FAILED),
                        generation(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(), WorkerLifecycle.START_FAILED),
                        generation(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(), WorkerLifecycle.START_FAILED)),
                protocol, audits::add, clock());

        assertAll(
                () -> assertFalse(heldBeforeTheThird, "two resumed failures do not hold yet"),
                () -> assertTrue(heldOnTheThird, "the third consecutive failure holds"),
                () -> assertTrue(atThreshold.holds(role, harness), "three recorded failures hold on the rebuild"),
                () -> assertEquals(1, closed.size(), "only the hold closes the role's tasks"),
                () -> assertEquals(1, audits.size(), "the resumed failures are not reported again"));
    }

    @Test
    @DisplayName("a clear retires the failed generations, so a rebuilt guard does not recreate the hold")
    void aClearSurvivesTheRebuild() {
        var failed = new ArrayList<>(List.of(
                generation(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(), WorkerLifecycle.START_FAILED),
                generation(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(), WorkerLifecycle.START_FAILED),
                generation(RuntimeGenerators.jobIds(JobId.Kind.LLM).next(), WorkerLifecycle.START_FAILED)));
        var built = new StartFailureGuard(project, () -> List.copyOf(failed), protocol, audits::add, clock(),
                (role, harness) -> failed.removeIf(generation -> generation.lifecycle() == WorkerLifecycle.START_FAILED
                        && generation.role().equals(role) && generation.harness().equals(harness)));
        var heldBefore = built.holds(role, harness);

        built.clear(role, harness);
        var rebuilt = new StartFailureGuard(project, () -> List.copyOf(failed), protocol, audits::add, clock());

        assertAll(
                () -> assertTrue(heldBefore, "three recorded failures hold"),
                () -> assertTrue(failed.isEmpty(), "the clear retires the failed generations"),
                () -> assertFalse(rebuilt.holds(role, harness), "the rebuilt guard does not recreate the hold"));
    }

    @Test
    @DisplayName("the report is the tail of the failed worker's output, bounded")
    void theReportIsBoundedToItsTail() {
        var guard = guard();
        var output = Generators.letterStrings(5_000, 5_000).next();

        guard.startFailed(role, harness, output);
        var detail = (String) audits.getFirst().details().get(AuditEvent.DETAIL_HARNESS_ERROR);

        assertAll(
                () -> assertEquals(StartFailureReport.MAX_DETAIL, detail.length(), "the report is bounded"),
                () -> assertTrue(output.endsWith(detail), "the report keeps the tail of the output"));
    }

    @Test
    @DisplayName("the report redacts a minted token, an e-mail address and the URI user information")
    void theReportIsRedacted() {
        var guard = guard();
        var token = "pmj_" + "b".repeat(43);
        var email = "operator@example.com";
        var uri = "https://alice:s3cr3t@harness.example/v1";

        guard.startFailed(role, harness, "login for " + email + " at " + uri + " with " + token);
        var detail = (String) audits.getFirst().details().get(AuditEvent.DETAIL_HARNESS_ERROR);

        assertAll(
                () -> assertFalse(detail.contains(token), "a minted job token is redacted"),
                () -> assertFalse(detail.contains(email), "an e-mail address is redacted"),
                () -> assertFalse(detail.contains("alice:s3cr3t"), "the URI user information is redacted"),
                () -> assertTrue(detail.contains("https://harness.example/v1"), "the URI itself stays readable"));
    }

    @Test
    @DisplayName("a start without output still reports")
    void aStartWithoutOutputStillReports() {
        var guard = guard();

        guard.startFailed(role, harness, " \n\t ");

        assertEquals(StartFailureReport.NO_OUTPUT,
                audits.getFirst().details().get(AuditEvent.DETAIL_HARNESS_ERROR));
    }

    @Test
    @DisplayName("a role on another harness counts on its own")
    void anotherHarnessCountsOnItsOwn() {
        var guard = guard();

        guard.startFailed(role, harness, failure);
        guard.startFailed(role, harness, failure);
        guard.startFailed(role, harness, failure);

        assertAll(
                () -> assertTrue(guard.holds(role, harness)),
                () -> assertFalse(guard.holds(role, otherHarness), "another harness is untouched"),
                () -> assertFalse(guard.holds(otherRole, harness), "another role is untouched"));
    }

    private StartFailureGuard guard() {
        return new StartFailureGuard(project, List::of, protocol, audits::add, clock());
    }

    private WorkerGeneration generation(String generationId, WorkerLifecycle lifecycle) {
        return new WorkerGeneration(generationId, role, harness, model, RoleForm.FRESH,
                LaunchRequest.WorkdirKind.JOB_PRIVATE, null, Path.of("work"), "sha256:" + digest, NOW,
                JobId.random(JobId.Kind.LLM), new ModelSlots.Lease(generationId, role), lifecycle, null,
                List.of(), 0L, ContextTokens.NOT_MEASURED, null);
    }

    private InstantSource clock() {
        return now::get;
    }

    private void hold(StartFailureGuard guard) {
        guard.startFailed(role, harness, failure);
        guard.startFailed(role, harness, failure);
        guard.startFailed(role, harness, failure);
    }

    /** The task protocol boundary, which records the roles whose tasks the guard closed. */
    private final class RecordingProtocol implements WorkerProtocol {

        @Override
        public void fence(String generationId) {
            // the guard never fences
        }

        @Override
        public void agentStarted(String generationId, String queueTaskId, int attempt, String executable, Instant at) {
            // detection is another step's contract
        }

        @Override
        public void offTrack(OffTrackReport report) {
            // the guard raises no activity signal
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            closed.add(role + "/" + harness);
        }
    }
}
