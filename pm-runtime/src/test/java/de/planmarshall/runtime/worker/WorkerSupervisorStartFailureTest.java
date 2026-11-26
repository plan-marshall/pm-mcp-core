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

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.runtime.launch.LaunchRefusal;
import de.planmarshall.runtime.launch.LaunchRequest;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * The start-failure wiring of a refused launch (PM-WORK-3, Start-Failure Guard): a launch the runtime refuses before its
 * spawn counts one failure of the role on the harness, and the two paths that refuse without a verifier — a launch the
 * composer cannot compose and a start the runner refuses — count the same way. A refusal of the update evidence and the
 * supervisor's own declines count nothing (Store Schemas § Harness Enrolment Store; Model Work § Start-Failure Guard).
 * <p>
 * The store document name {@code harnesses.json}, the refusal codes and the version range are the specification's
 * contracts; the digests are generated.
 */
@EnableGeneratorController
@DisplayName("Worker supervisor start failure")
class WorkerSupervisorStartFailureTest {

    private static final SupervisorEnvironment.StartGuard OPEN_GUARD = SupervisorEnvironment.StartGuard.open();

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    private static final String STORE_FILE = "harnesses.json";

    /** The version outside the fixture profile's range {@code >=1.0.0 <2.0.0}. */
    private static final String UNSUPPORTED_VERSION = "2.0.0";

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private String failure;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        failure = RuntimeGenerators.words().next();
    }

    @ParameterizedTest
    @EnumSource(value = LaunchRefusal.class,
            names = {"HARNESS_NOT_ENROLLED", "HARNESS_VERSION_UNSUPPORTED", "HARNESS_TAMPERED",
                    "HARNESS_STORE_UNREADABLE"})
    @DisplayName("a launch refused on the enrolment store counts one start failure")
    void anEnrolmentStoreRefusalCountsOneStartFailure(LaunchRefusal reason) throws IOException {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = guard();
        var supervisor = supervisorRefusing(reason, guard);

        supervisor.start(List.of(fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(1, fixture.refusals.size(), "the refused launch is recorded once"),
                () -> assertEquals(reason.code(), fixture.refusals.getFirst().reason().code(),
                        "the refusal names the refused reason"),
                () -> assertEquals(1, countedFailures(), "the refusal counts one start failure"),
                () -> assertFalse(guard.holds(role.artifact().name(), role.harness()),
                        "one refused launch does not hold the guard"));
    }

    @Test
    @DisplayName("a launch refused on its update evidence counts no start failure")
    void anUpdateEvidenceRefusalCountsNothing() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = guard();
        fixture.executableDigest = Optional.of(otherDigest());
        fixture.evidencePasses = false;
        var supervisor = new WorkerSupervisor(fixture.environment(guard, OPEN_GATE));

        supervisor.start(List.of(fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(LaunchRefusal.HARNESS_UPDATE_UNVERIFIED.code(),
                        fixture.refusals.getFirst().reason().code(), "the refusal names the unverified update"),
                () -> assertEquals(0, countedFailures(), "an unverified update counts no start failure"));
    }

    @Test
    @DisplayName("a launch the composer cannot compose counts one start failure")
    void aCompositionRefusalCountsOneStartFailure() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = guard();
        var supervisor = new WorkerSupervisor(fixture.environment(guard, OPEN_GATE));
        var request = new WorkerStartRequest(role, LaunchRequest.WorkdirKind.PLAN_WORKTREE, fixture.plan,
                fixture.workdir, fixture.envelope(), false, List.of("skill://pm/state/unknown/SKILL.md"), List.of(),
                "", null);

        supervisor.start(List.of(request));

        assertAll(
                () -> assertEquals(1, fixture.refusals.size(), "the refused composition is recorded once"),
                () -> assertEquals(LaunchRefusal.PROMPT_COMPOSITION_FAILED.code(),
                        fixture.refusals.getFirst().reason().code(), "the refusal names the composition"),
                () -> assertEquals(1, countedFailures(), "the composition refusal counts one failure"));
    }

    @Test
    @DisplayName("a start the runner refuses counts one start failure")
    void aRunnerRefusalCountsOneStartFailure() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = guard();
        fixture.refuseStart = true;
        var supervisor = new WorkerSupervisor(fixture.environment(guard, OPEN_GATE));

        var outcome = supervisor.start(List.of(fixture.freshRequest(role))).getFirst();

        assertAll(
                () -> assertEquals(WorkerStartRefusal.LAUNCH_REFUSED,
                        ((WorkerSupervisor.StartOutcome.Declined) outcome).reason(),
                        "the runner's refusal declines the start"),
                () -> assertEquals(1, countedFailures(), "the runner's refusal counts one failure"),
                () -> assertTrue(fixture.refusals.isEmpty(), "the runner records its own refusal"));
    }

    @Test
    @DisplayName("a start failure the guard throws on still records the refusal and releases the slot")
    void aThrowingCountStillRecordsTheRefusal() throws IOException {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var supervisor = new WorkerSupervisor(fixture.environment(throwingGuard(failure), OPEN_GATE));
        tamperStore();

        var thrown = assertThrows(IllegalStateException.class,
                () -> supervisor.start(List.of(fixture.freshRequest(role))),
                "the guard's own failure propagates");

        assertAll(
                () -> assertEquals(failure, thrown.getMessage(), "the guard's own failure propagates"),
                () -> assertEquals(1, fixture.refusals.size(), "the refusal is recorded before the count"),
                () -> assertEquals(1, fixture.released.size(), "the model slot is released before the count"));
    }

    @Test
    @DisplayName("a batch stops at the request after which the guard holds")
    void aBatchStopsWhenTheGuardHolds() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 3);
        var guard = guard();
        for (var failure = 1; failure <= StartFailureGuard.HOLD_THRESHOLD; failure++) {
            guard.startFailed(role.artifact().name(), role.harness(), RuntimeGenerators.words().next());
        }
        fixture.advance(StartFailureGuard.PROBE_BACKOFF);
        fixture.spacing = new HarnessStartSpacing(Duration.ofMillis(200));
        var supervisor = new WorkerSupervisor(fixture.environment(guard, OPEN_GATE));

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role), fixture.freshRequest(role),
                fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(1, outcomes.size(), "the hold stops the batch after one start"),
                () -> assertEquals(1, fixture.starts.size(), "only the probe of the batch starts"),
                () -> assertEquals(WorkerLifecycle.STARTING,
                        ((WorkerSupervisor.StartOutcome.Started) outcomes.getFirst()).generation().lifecycle(),
                        "the probe that started is the batch's only worker"));
    }

    @Test
    @DisplayName("three launches refused on the harness store hold the role's starts")
    void threeStoreRefusalsHoldTheRole() throws IOException {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = guard();
        var supervisor = new WorkerSupervisor(fixture.environment(guard, OPEN_GATE));
        tamperStore();

        supervisor.start(List.of(fixture.freshRequest(role)));
        supervisor.start(List.of(fixture.freshRequest(role)));
        supervisor.start(List.of(fixture.freshRequest(role)));
        var held = guard.holds(role.artifact().name(), role.harness());

        assertAll(
                () -> assertEquals(3, fixture.refusals.size(), "every refused launch is recorded"),
                () -> assertTrue(held, "the third refused launch holds the guard"));
    }

    @Test
    @DisplayName("a start waits for the harness's pending start, at most the spacing bound")
    void aStartWaitsForThePendingStartOfItsHarness() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        fixture.spacing = new HarnessStartSpacing(Duration.ofMillis(200));
        var supervisor = new WorkerSupervisor(fixture.environment(OPEN_GUARD, OPEN_GATE));
        supervisor.start(List.of(fixture.freshRequest(role)));

        var started = System.nanoTime();
        supervisor.start(List.of(fixture.freshRequest(role)));
        var waited = Duration.ofNanos(System.nanoTime() - started);

        assertAll(
                () -> assertEquals(2, fixture.starts.size(), "both starts spawn a worker"),
                () -> assertTrue(waited.compareTo(Duration.ofMillis(150)) >= 0,
                        "the second start waits the spacing bound"),
                () -> assertTrue(waited.compareTo(Duration.ofSeconds(5)) < 0,
                        "the wait is the fixture's bound, not the compiled one"));
    }

    @Test
    @DisplayName("the open guard reports a refusal but never holds")
    void openGuardCountsNothing() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = SupervisorEnvironment.StartGuard.open();

        guard.startFailed(role.artifact().name(), role.harness(), "the harness store is unreadable");
        var held = guard.holds(role.artifact().name(), role.harness());

        assertFalse(held, "the open guard never holds the role");
    }

    private WorkerSupervisor supervisorRefusing(LaunchRefusal reason, StartFailureGuard guard) throws IOException {
        prepareBeforeTheEnrolment(reason);
        var supervisor = new WorkerSupervisor(fixture.environment(guard, OPEN_GATE));
        breakTheStore(reason);
        return supervisor;
    }

    private void prepareBeforeTheEnrolment(LaunchRefusal reason) {
        switch (reason) {
            case HARNESS_NOT_ENROLLED -> fixture.enrol = false;
            case HARNESS_VERSION_UNSUPPORTED -> {
                fixture.executableDigest = Optional.of(otherDigest());
                fixture.probedVersion = UNSUPPORTED_VERSION;
            }
            default -> {
                // the store refusals need the store the enrolment writes first
            }
        }
    }

    private void breakTheStore(LaunchRefusal reason) throws IOException {
        switch (reason) {
            case HARNESS_TAMPERED -> tamperStore();
            case HARNESS_STORE_UNREADABLE -> fixture.writeUnreadableStore();
            default -> {
                // a verifier refusal of another reason needs the store as it is
            }
        }
    }

    /** @return the start failures the guard counted, read from its audit records */
    private long countedFailures() {
        return fixture.events.getValues().stream()
                .filter(event -> event.event() == AuditEvent.WORKER_START_FAILED)
                .count();
    }

    private StartFailureGuard guard() {
        return new StartFailureGuard(fixture.project, List::of, fixture.protocol, fixture.eventSink, fixture.clock);
    }

    private static SupervisorEnvironment.StartGuard throwingGuard(String message) {
        SupervisorEnvironment.StartGuard mock = createMock(SupervisorEnvironment.StartGuard.class);
        expect(mock.holds(anyString(), anyString())).andReturn(false).anyTimes();
        expect(mock.startFailed(anyString(), anyString(), anyString())).andThrow(new IllegalStateException(message))
                .anyTimes();
        mock.probeDeclined(anyString(), anyString());
        expectLastCall().anyTimes();
        replay(mock);
        return mock;
    }

    private String otherDigest() {
        var digest = RuntimeGenerators.sha256Digests().next();
        return digest.equals(fixture.sha) ? RuntimeGenerators.sha256Digests().next() : digest;
    }

    /** Makes the enrolment store fail its sidecar check, which the verifier refuses as {@code harness_tampered}. */
    private void tamperStore() throws IOException {
        Files.writeString(base.resolve("harness").resolve(STORE_FILE), "{}", StandardCharsets.UTF_8);
    }
}
