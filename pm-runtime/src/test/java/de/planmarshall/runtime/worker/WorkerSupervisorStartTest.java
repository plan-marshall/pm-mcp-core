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

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.EffectiveRole;
import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;
import de.planmarshall.runtime.worker.WorkerSupervisor.StartOutcome;

/**
 * The supervisor's start decision (PM-WORK-3, PM-WF-14): the fresh and warm forms, the role's count, the three refusals
 * it decides itself, the probe a throwing start settles, and the one log line a successful start writes.
 */
@EnableTestLogger
@DisplayName("Worker supervisor start decision")
class WorkerSupervisorStartTest {

    private static final SupervisorEnvironment.StartGuard OPEN_GUARD = SupervisorEnvironment.StartGuard.open();

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
    }

    @Test
    @DisplayName("a fresh role starts one worker per queued task, up to its count")
    void freshStartsOnePerTaskUpToTheCount() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        var supervisor = supervisor(OPEN_GUARD, OPEN_GATE);

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role), fixture.freshRequest(role),
                fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(2, outcomes.size(), "the third task waits for the next free worker"),
                () -> assertEquals(2, started(outcomes)),
                () -> assertEquals(2, fixture.starter.starts.size()),
                () -> assertTrue(fixture.starter.refusals.isEmpty()));
    }

    @Test
    @DisplayName("a warm role starts one worker per working directory and none without queued work")
    void warmStartsOnePerDirectory() throws Exception {
        var role = fixture.role(RoleClass.WRITER, RoleForm.WARM, 3);
        var other = Files.createDirectories(base.resolve("other-worktree"));
        var supervisor = supervisor(OPEN_GUARD, OPEN_GATE);

        var outcomes = supervisor.start(List.of(fixture.warmRequest(role, fixture.workdir),
                fixture.warmRequest(role, fixture.workdir), fixture.warmRequest(role, other)));

        assertAll(
                () -> assertEquals(2, outcomes.size(), "one worker serves each directory"),
                () -> assertEquals(2, started(outcomes)),
                () -> assertEquals(List.of(), supervisor.start(List.of()), "no queued work starts nothing"));
    }

    @Test
    @DisplayName("the count comes from the role artifact")
    void countComesFromTheArtifact() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var supervisor = supervisor(OPEN_GUARD, OPEN_GATE);

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role), fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(1, outcomes.size()),
                () -> assertEquals(1, started(outcomes)),
                () -> assertEquals(1, fixture.starter.starts.size()));
    }

    @Test
    @DisplayName("a held start-failure guard starts no worker")
    void guardHeldStartsNothing() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        var supervisor = supervisor((guardRole, harness) -> true, OPEN_GATE);

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(WorkerStartRefusal.ROLE_GUARD_HELD, declined(outcomes)),
                () -> assertTrue(fixture.starter.starts.isEmpty()),
                () -> assertTrue(fixture.slots.claimed.isEmpty(), "the guard is consulted before the slot"));
    }

    @Test
    @DisplayName("a start that throws after consuming the probe restarts the backoff instead of stranding the guard")
    void aThrowingStartRestartsTheProbeBackoff() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = new StartFailureGuard(SupervisorTestFixture.PROJECT, List::of, fixture.protocol,
                fixture.events::add, fixture.clock);
        hold(guard, role);
        fixture.advance(StartFailureGuard.PROBE_BACKOFF);
        fixture.starter.startFailure = new IllegalStateException("the job runtime is gone");
        var supervisor = supervisor(guard, OPEN_GATE);
        var requests = List.of(fixture.freshRequest(role));

        var thrown = assertThrows(IllegalStateException.class, () -> supervisor.start(requests));
        var heldWhileTheRestartedBackoffRuns = guard.holds(role.artifact().name(), role.harness());
        fixture.advance(StartFailureGuard.PROBE_BACKOFF);
        var nextProbeAdmitted = !guard.holds(role.artifact().name(), role.harness());

        assertAll(
                () -> assertEquals("the job runtime is gone", thrown.getMessage(),
                        "the start's own failure propagates"),
                () -> assertTrue(heldWhileTheRestartedBackoffRuns,
                        "the throw releases the consumed probe and restarts its backoff"),
                () -> assertTrue(nextProbeAdmitted, "the restarted backoff admits the next probe"),
                () -> assertTrue(supervisor.generations().isEmpty(), "the throwing start registers no generation"),
                () -> assertEquals(1, fixture.slots.released.size(), "the throwing start returns its model slot"));
    }

    @Test
    @DisplayName("a refusing verification-record gate starts no worker")
    void gateRefusedStartsNothing() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        var supervisor = supervisor(OPEN_GUARD, (gateRole, harness) -> false);

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(WorkerStartRefusal.HARNESS_INELIGIBLE, declined(outcomes)),
                () -> assertTrue(fixture.starter.starts.isEmpty()),
                () -> assertTrue(fixture.slots.claimed.isEmpty()));
    }

    @Test
    @DisplayName("no claimable model slot starts no worker")
    void noModelSlotStartsNothing() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        fixture.slots.grant = false;
        var supervisor = supervisor(OPEN_GUARD, OPEN_GATE);

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(WorkerStartRefusal.NO_MODEL_SLOT, declined(outcomes)),
                () -> assertTrue(fixture.starter.starts.isEmpty()),
                () -> assertTrue(fixture.starter.refusals.isEmpty()));
    }

    @Test
    @DisplayName("a launch the runtime refuses is recorded as the refused job and declines the start")
    void refusedLaunchIsRecorded() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 2);
        fixture.enrol = false;
        var supervisor = supervisor(OPEN_GUARD, OPEN_GATE);

        var outcomes = supervisor.start(List.of(fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(WorkerStartRefusal.LAUNCH_REFUSED, declined(outcomes)),
                () -> assertEquals(1, fixture.starter.refusals.size()),
                () -> assertEquals("harness_not_enrolled", fixture.starter.refusals.getFirst().reason().code()),
                () -> assertTrue(fixture.starter.refusals.getFirst().workerBlock().contains("generation_id"),
                        "the refused record carries the worker block"),
                () -> assertEquals(1, fixture.slots.released.size(), "the refused start returns its slot"));
    }

    @Test
    @DisplayName("a successful start writes exactly one generation-started record")
    void successfulStartLogsOnce() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var supervisor = supervisor(OPEN_GUARD, OPEN_GATE);

        var outcome = supervisor.start(List.of(fixture.freshRequest(role))).getFirst();

        assertAll(
                () -> assertInstanceOf(StartOutcome.Started.class, outcome),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        "started on harness 'claude'"));
    }

    private WorkerSupervisor supervisor(SupervisorEnvironment.StartGuard guard,
            SupervisorEnvironment.EligibilityGate gate) {
        return new WorkerSupervisor(fixture.environment(guard, gate));
    }

    private static long started(List<StartOutcome> outcomes) {
        return outcomes.stream().filter(StartOutcome.Started.class::isInstance).count();
    }

    private static void hold(StartFailureGuard guard, EffectiveRole role) {
        for (var failure = 1; failure <= StartFailureGuard.HOLD_THRESHOLD; failure++) {
            guard.startFailed(role.artifact().name(), role.harness(), "the start failed");
        }
    }

    private static WorkerStartRefusal declined(List<StartOutcome> outcomes) {
        var outcome = assertInstanceOf(StartOutcome.Declined.class, outcomes.getFirst());
        return outcome.reason();
    }
}
