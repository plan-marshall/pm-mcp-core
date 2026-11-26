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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.profile.WorkerParameters;
import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;

/**
 * The interleavings of the start spacing (PM-WORK-3, Starts of One Harness, Start-Failure Guard): a start must not wait
 * on the spacing while it holds the supervisor's monitor, a release must belong to the start that owns the turn, and
 * each of the three release sites must release the turn it owns.
 * <p>
 * No test sleeps. Latches order the two threads of the monitor scenario, and the bound is short so a missed release
 * shows as the wait of the next start rather than as a passed assertion; the bound of a scenario that asserts a release
 * is long, so only a release can let the next start through in the asserted window.
 * <p>
 * The failure detail is generated; the harness client id, the role and the profile are the fixture's contracts.
 */
@EnableGeneratorController
@DisplayName("Start spacing interleavings")
class StartSpacingInterleavingTest {

    private static final SupervisorEnvironment.StartGuard OPEN_GUARD = SupervisorEnvironment.StartGuard.open();

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    /** The bound of a scenario that asserts a release: long enough that only a release lets a start through. */
    private static final Duration LONG_BOUND = Duration.ofSeconds(5);

    /** The bound of a scenario that asserts a wait: short enough to keep the test fast, long enough to observe. */
    private static final Duration SHORT_BOUND = Duration.ofMillis(300);

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    private String failure;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
        failure = Generators.letterStrings(3, 40).next();
    }

    @Test
    @DisplayName("a start waits outside the supervisor monitor, so the pending worker's first call releases it")
    void aStartWaitsOutsideTheSupervisorMonitor() throws Exception {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        var supervisor = supervisor(LONG_BOUND);
        var calls = calls(supervisor);
        var first = started(supervisor.start(List.of(fixture.freshRequest(role))).getFirst());

        fixture.claims = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var waitingStart = new Thread(() -> {
            supervisor.start(List.of(fixture.freshRequest(role)));
            secondStarted.countDown();
        }, "second-start");
        waitingStart.start();
        var reachedTheClaim = fixture.claims.await(2, TimeUnit.SECONDS);
        var firstCallReturned = new CountDownLatch(1);
        var firstCall = new Thread(() -> {
            calls.callHeartbeat(first.generationId(), fixture.clock.instant());
            firstCallReturned.countDown();
        }, "first-call");
        firstCall.start();

        try {
            assertAll(
                    () -> assertTrue(reachedTheClaim, "the waiting start claims its slot first"),
                    () -> assertTrue(firstCallReturned.await(2, TimeUnit.SECONDS),
                            "the first call completes while a start waits"),
                    () -> assertTrue(secondStarted.await(2, TimeUnit.SECONDS),
                            "the first call releases the waiting start"),
                    () -> assertEquals(2, fixture.starts.size(), "both starts spawn a worker"));
        } finally {
            waitingStart.join(LONG_BOUND.toMillis());
            firstCall.join(LONG_BOUND.toMillis());
        }
    }

    @Test
    @DisplayName("the end of a generation that is not the pending owner releases nothing")
    void anEndOfANonOwnerReleasesNothing() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 4);
        var environment = environment(SHORT_BOUND);
        var supervisor = new WorkerSupervisor(environment);
        var calls = calls(supervisor);
        var ending = ending(environment);
        var first = started(supervisor.start(List.of(fixture.freshRequest(role))).getFirst());
        calls.callHeartbeat(first.generationId(), fixture.clock.instant());

        started(supervisor.start(List.of(fixture.freshRequest(role))).getFirst());
        ending.end(first, EndCause.RECYCLE);
        var startedAt = System.nanoTime();
        var third = started(supervisor.start(List.of(fixture.freshRequest(role))).getFirst());
        var waited = Duration.ofNanos(System.nanoTime() - startedAt);
        ending.end(third, EndCause.RECYCLE);
        var waitedAfterTheOwnerEnd = waitedToStart(supervisor, role);

        assertAll(
                () -> assertTrue(waited.compareTo(Duration.ofMillis(200)) >= 0,
                        "a non-owner's end releases nothing"),
                () -> assertTrue(waitedAfterTheOwnerEnd.compareTo(Duration.ofMillis(500)) < 0,
                        "the owner's own end releases at once"),
                () -> assertEquals(4, fixture.starts.size(), "each start of the scenario spawns a worker"));
    }

    @Test
    @DisplayName("after the bound the next start owns the turn and the former owner's late end releases nothing")
    void theBoundHandsTheTurnToTheNextStart() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 3);
        var environment = environment(SHORT_BOUND);
        var supervisor = new WorkerSupervisor(environment);
        var ending = ending(environment);
        var first = started(supervisor.start(List.of(fixture.freshRequest(role))).getFirst());

        var waitedAtTheBound = waitedToStart(supervisor, role);
        ending.end(first, EndCause.RECYCLE);
        var waitedAfterTheLateEnd = waitedToStart(supervisor, role);

        assertAll(
                () -> assertTrue(waitedAtTheBound.compareTo(Duration.ofMillis(150)) >= 0,
                        "the next start waits its bound for the turn"),
                () -> assertTrue(waitedAfterTheLateEnd.compareTo(Duration.ofMillis(200)) >= 0,
                        "the former owner's late end releases nothing"),
                () -> assertEquals(3, fixture.starts.size(), "each start of the scenario spawns a worker"));
    }

    @Test
    @DisplayName("the worker's first call releases the harness's turn")
    void theFirstCallReleasesTheTurn() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        var supervisor = supervisor(LONG_BOUND);
        var calls = calls(supervisor);
        var first = started(supervisor.start(List.of(fixture.freshRequest(role))).getFirst());

        calls.callHeartbeat(first.generationId(), fixture.clock.instant());
        var waited = waitedToStart(supervisor, role);

        assertTrue(waited.compareTo(Duration.ofMillis(500)) < 0, "the first call releases the turn at once");
    }

    @Test
    @DisplayName("the end of the pending worker releases the harness's turn")
    void theEndOfThePendingWorkerReleasesTheTurn() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        var environment = environment(LONG_BOUND);
        var supervisor = new WorkerSupervisor(environment);
        var ending = ending(environment);
        var first = started(supervisor.start(List.of(fixture.freshRequest(role))).getFirst());

        ending.end(first, EndCause.RECYCLE);
        var waited = waitedToStart(supervisor, role);

        assertTrue(waited.compareTo(Duration.ofMillis(500)) < 0, "the pending worker's end releases the turn");
    }

    @Test
    @DisplayName("a start the runner refuses releases the harness's turn")
    void aRefusedStartReleasesTheTurn() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        fixture.refuseStart = true;
        var supervisor = supervisor(LONG_BOUND);

        var refused = supervisor.start(List.of(fixture.freshRequest(role))).getFirst();
        fixture.refuseStart = false;
        var waited = waitedToStart(supervisor, role);

        assertAll(
                () -> assertEquals(WorkerStartRefusal.LAUNCH_REFUSED,
                        assertInstanceOf(WorkerSupervisor.StartOutcome.Declined.class, refused,
                                "the runner's refusal declines the start").reason(),
                        "a refused runner declines the start"),
                () -> assertTrue(waited.compareTo(Duration.ofMillis(500)) < 0,
                        "the refused start releases the turn at once"));
    }

    @Test
    @DisplayName("a runner that throws releases the harness's turn")
    void aThrowingRunnerReleasesTheTurn() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        fixture.startFailure = new IllegalStateException(failure);
        var supervisor = supervisor(SHORT_BOUND);

        var thrown = assertThrows(IllegalStateException.class,
                () -> supervisor.start(List.of(fixture.freshRequest(role))),
                "a throwing runner propagates its failure");
        fixture.startFailure = null;
        var waited = waitedToStart(supervisor, role);

        assertAll(
                () -> assertEquals(failure, thrown.getMessage(), "the runner's own failure propagates"),
                () -> assertTrue(waited.compareTo(Duration.ofMillis(200)) < 0,
                        "the throwing runner releases the turn"));
    }

    /** @return a supervisor over the fixture environment with the given spacing bound */
    private WorkerSupervisor supervisor(Duration bound) {
        return new WorkerSupervisor(environment(bound));
    }

    /** @return the fixture environment with the given spacing bound */
    private SupervisorEnvironment environment(Duration bound) {
        fixture.spacing = new HarnessStartSpacing(bound);
        return fixture.environment(OPEN_GUARD, OPEN_GATE);
    }

    /** @return the calls of a supervisor, over the environment the supervisor holds */
    private SupervisorCalls calls(WorkerSupervisor supervisor) {
        return calls(supervisor.environment(), supervisor);
    }

    private SupervisorCalls calls(SupervisorEnvironment environment, WorkerSupervisor supervisor) {
        var parameters = new WorkerParameters(true, 270, false, 600, 3, 300, false);
        var ending = ending(environment);
        var recycling = new WorkerRecycling(environment, ending, supervisor::generations, _ -> Optional.empty(),
                fixture.tasks, parameters);
        return new SupervisorCalls(supervisor, ending, recycling, parameters, _ -> RoleClass.WRITER);
    }

    private static WorkerEnding ending(SupervisorEnvironment environment) {
        return new WorkerEnding(environment, () -> false, () -> {
        });
    }

    private Duration waitedToStart(WorkerSupervisor supervisor, EffectiveRole role) {
        var startedAt = System.nanoTime();
        supervisor.start(List.of(fixture.freshRequest(role)));
        return Duration.ofNanos(System.nanoTime() - startedAt);
    }

    private static WorkerGeneration started(WorkerSupervisor.StartOutcome outcome) {
        return assertInstanceOf(WorkerSupervisor.StartOutcome.Started.class, outcome,
                "the start is answered with its generation").generation();
    }
}
