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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.JobId;
import de.planmarshall.core.store.WorkerState.ContextTokens;
import de.planmarshall.runtime.launch.LaunchRequest;

/**
 * The start-failure guard (PM-WORK-3, Start-Failure Guard): the count of consecutive start failures, the hold, the
 * reset on a first call, the two clears, the probe and its backoff including a declined probe, the rebuild from the
 * generation registry, and the bounded redacted report.
 */
@EnableTestLogger
@DisplayName("Start-failure guard")
class StartFailureGuardTest {

    private static final String PROJECT = "sample-project";

    private static final String ROLE = "implementer";

    private static final String HARNESS = "claude";

    private static final Instant NOW = Instant.parse("2026-10-25T10:00:00Z");

    private static final String FAILURE = "your account is at its usage limit";

    private final List<AuditEvent> audits = new CopyOnWriteArrayList<>();

    private final List<String> closed = new CopyOnWriteArrayList<>();

    private final AtomicReference<Instant> now = new AtomicReference<>(NOW);

    private final WorkerProtocol protocol = new RecordingProtocol();

    @Test
    @DisplayName("three consecutive failures hold the guard and close the role's tasks")
    void threeFailuresHold() {
        var guard = guard();

        guard.startFailed(ROLE, HARNESS, FAILURE);
        guard.startFailed(ROLE, HARNESS, FAILURE);
        var heldBefore = guard.holds(ROLE, HARNESS);
        guard.startFailed(ROLE, HARNESS, FAILURE);

        assertAll(
                () -> assertFalse(heldBefore, "two failures do not hold yet"),
                () -> assertTrue(guard.holds(ROLE, HARNESS), "the third failure holds"),
                () -> assertEquals(List.of(ROLE + "/" + HARNESS), closed,
                        "the hold closes the role's queued and new tasks"),
                () -> assertEquals(3, audits.size(), "one audit record per counted failure"),
                () -> assertEquals(AuditEvent.WORKER_START_FAILED, audits.getFirst().event()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "(consecutive failure 3)"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.WARN,
                        "holds after 3 consecutive failures"));
    }

    @Test
    @DisplayName("a first call resets the count")
    void aFirstCallResetsTheCount() {
        var guard = guard();

        guard.startFailed(ROLE, HARNESS, FAILURE);
        guard.startFailed(ROLE, HARNESS, FAILURE);
        guard.firstCall(ROLE, HARNESS);
        guard.startFailed(ROLE, HARNESS, FAILURE);

        assertAll(
                () -> assertFalse(guard.holds(ROLE, HARNESS), "the reset count starts over"),
                () -> assertEquals(3, audits.size(), "the reset does not drop the audit trail"));
    }

    @Test
    @DisplayName("a changed role configuration and a new enrolment each clear the hold")
    void aChangedConfigurationAndANewEnrolmentClear() {
        var changedConfiguration = guard();
        hold(changedConfiguration);
        changedConfiguration.clear(ROLE, HARNESS);

        var newEnrolment = guard();
        hold(newEnrolment);
        newEnrolment.clear(ROLE, HARNESS);

        assertAll(
                () -> assertFalse(changedConfiguration.holds(ROLE, HARNESS)),
                () -> assertFalse(newEnrolment.holds(ROLE, HARNESS)));
    }

    @Test
    @DisplayName("a declined probe restarts the backoff instead of holding forever")
    void aDeclinedProbeRestartsTheBackoff() {
        var guard = guard();
        hold(guard);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF));
        var probeAdmitted = !guard.holds(ROLE, HARNESS);

        guard.probeDeclined(ROLE, HARNESS);
        var heldWhileTheBackoffRuns = guard.holds(ROLE, HARNESS);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF.multipliedBy(2)).minusSeconds(1));
        var stillHeld = guard.holds(ROLE, HARNESS);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF.multipliedBy(2)));
        var nextProbeAdmitted = !guard.holds(ROLE, HARNESS);

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

        var probeAdmitted = !guard.holds(ROLE, HARNESS);
        var secondStartHeld = guard.holds(ROLE, HARNESS);
        guard.firstCall(ROLE, HARNESS);

        assertAll(
                () -> assertTrue(probeAdmitted, "the backoff admits the probe"),
                () -> assertTrue(secondStartHeld, "only one start is the probe"),
                () -> assertFalse(guard.holds(ROLE, HARNESS), "the probe's first call clears the guard"));
    }

    @Test
    @DisplayName("a failed probe restarts the backoff")
    void aFailedProbeRestartsTheBackoff() {
        var guard = guard();
        hold(guard);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF));
        guard.holds(ROLE, HARNESS);

        guard.startFailed(ROLE, HARNESS, FAILURE);
        var heldBeforeTheRestart = guard.holds(ROLE, HARNESS);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF.multipliedBy(2)).minusSeconds(1));
        var stillHeld = guard.holds(ROLE, HARNESS);
        now.set(NOW.plus(StartFailureGuard.PROBE_BACKOFF.multipliedBy(2)));
        var nextProbeAdmitted = !guard.holds(ROLE, HARNESS);

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
        var twoFailures = List.of(generation("l-0000000000000001", WorkerLifecycle.START_FAILED),
                generation("l-0000000000000002", WorkerLifecycle.START_FAILED));
        var rebuilt = new StartFailureGuard(PROJECT, () -> twoFailures, protocol, audits::add, clock());
        var heldBeforeTheThird = rebuilt.holds(ROLE, HARNESS);
        rebuilt.startFailed(ROLE, HARNESS, FAILURE);
        var heldOnTheThird = rebuilt.holds(ROLE, HARNESS);

        var atThreshold = new StartFailureGuard(PROJECT,
                () -> List.of(generation("l-0000000000000003", WorkerLifecycle.START_FAILED),
                        generation("l-0000000000000004", WorkerLifecycle.START_FAILED),
                        generation("l-0000000000000005", WorkerLifecycle.START_FAILED)),
                protocol, audits::add, clock());

        assertAll(
                () -> assertFalse(heldBeforeTheThird, "two resumed failures do not hold yet"),
                () -> assertTrue(heldOnTheThird, "the third consecutive failure holds"),
                () -> assertTrue(atThreshold.holds(ROLE, HARNESS), "three recorded failures hold on the rebuild"),
                () -> assertEquals(1, closed.size(), "only the hold closes the role's tasks"),
                () -> assertEquals(1, audits.size(), "the resumed failures are not reported again"));
    }

    @Test
    @DisplayName("a clear retires the failed generations, so a rebuilt guard does not recreate the hold")
    void aClearSurvivesTheRebuild() {
        var failed = new ArrayList<>(List.of(
                generation("l-0000000000000011", WorkerLifecycle.START_FAILED),
                generation("l-0000000000000012", WorkerLifecycle.START_FAILED),
                generation("l-0000000000000013", WorkerLifecycle.START_FAILED)));
        var built = new StartFailureGuard(PROJECT, () -> List.copyOf(failed), protocol, audits::add, clock(),
                (role, harness) -> failed.removeIf(generation -> generation.lifecycle() == WorkerLifecycle.START_FAILED
                        && generation.role().equals(role) && generation.harness().equals(harness)));
        var heldBefore = built.holds(ROLE, HARNESS);

        built.clear(ROLE, HARNESS);
        var rebuilt = new StartFailureGuard(PROJECT, () -> List.copyOf(failed), protocol, audits::add, clock());

        assertAll(
                () -> assertTrue(heldBefore, "three recorded failures hold"),
                () -> assertTrue(failed.isEmpty(), "the clear retires the failed generations"),
                () -> assertFalse(rebuilt.holds(ROLE, HARNESS), "the rebuilt guard does not recreate the hold"));
    }

    @Test
    @DisplayName("the report is the tail of the failed worker's output, bounded")
    void theReportIsBoundedToItsTail() {
        var guard = guard();
        var output = "y".repeat(5_000);

        guard.startFailed(ROLE, HARNESS, output);
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

        guard.startFailed(ROLE, HARNESS, "login for " + email + " at " + uri + " with " + token);
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

        guard.startFailed(ROLE, HARNESS, " \n\t ");

        assertEquals(StartFailureReport.NO_OUTPUT,
                audits.getFirst().details().get(AuditEvent.DETAIL_HARNESS_ERROR));
    }

    @Test
    @DisplayName("a role on another harness counts on its own")
    void anotherHarnessCountsOnItsOwn() {
        var guard = guard();

        guard.startFailed(ROLE, HARNESS, FAILURE);
        guard.startFailed(ROLE, HARNESS, FAILURE);
        guard.startFailed(ROLE, HARNESS, FAILURE);

        assertAll(
                () -> assertTrue(guard.holds(ROLE, HARNESS)),
                () -> assertFalse(guard.holds(ROLE, "codex"), "another harness is untouched"),
                () -> assertFalse(guard.holds("reviewer", HARNESS), "another role is untouched"));
    }

    private StartFailureGuard guard() {
        return new StartFailureGuard(PROJECT, List::of, protocol, audits::add, clock());
    }

    private static WorkerGeneration generation(String generationId, WorkerLifecycle lifecycle) {
        return new WorkerGeneration(generationId, ROLE, HARNESS, "a-model", RoleForm.FRESH,
                LaunchRequest.WorkdirKind.JOB_PRIVATE, null, Path.of("work"), "sha256:" + "0".repeat(64), NOW,
                JobId.random(JobId.Kind.LLM), new ModelSlots.Lease(generationId, ROLE), lifecycle, null,
                List.of(), 0L, ContextTokens.NOT_MEASURED, null);
    }

    private InstantSource clock() {
        return now::get;
    }

    private static void hold(StartFailureGuard guard) {
        guard.startFailed(ROLE, HARNESS, FAILURE);
        guard.startFailed(ROLE, HARNESS, FAILURE);
        guard.startFailed(ROLE, HARNESS, FAILURE);
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
        public void offTrack(String generationId, String queueTaskId, int attempt, String signal, String action,
                double measurement, double threshold, Instant at) {
            // the guard raises no activity signal
        }

        @Override
        public void closeUnavailable(String role, String harness) {
            closed.add(role + "/" + harness);
        }
    }
}
