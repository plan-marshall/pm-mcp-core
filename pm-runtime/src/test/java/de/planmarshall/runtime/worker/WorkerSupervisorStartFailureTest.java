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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.role.RoleClass;
import de.planmarshall.core.role.RoleForm;

/**
 * The start-failure wiring of a refused enrolment store (PM-WORK-3, Start-Failure Guard): a launch the runtime refuses
 * because the harness store is unreadable or tampered is a start failure of the role on that harness, so the guard
 * counts it and holds the role's starts at its threshold (Store Schemas § Unreadable Store and § Tampered File).
 * <p>
 * The store document name {@code harnesses.json} is the specification's contract, and the refusal code
 * {@code harness_tampered} is the profile grammar's.
 */
@DisplayName("Worker supervisor start failure")
class WorkerSupervisorStartFailureTest {

    private static final SupervisorEnvironment.EligibilityGate OPEN_GATE =
            SupervisorEnvironment.EligibilityGate.open();

    private static final String STORE_FILE = "harnesses.json";

    @TempDir
    Path base;

    private SupervisorTestFixture fixture;

    @BeforeEach
    void setUp() throws IOException {
        fixture = new SupervisorTestFixture(base);
    }

    @Test
    @DisplayName("a launch refused on the harness store counts one start failure")
    void storeRefusalCountsOneStartFailure() throws IOException {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = guard();
        var supervisor = new WorkerSupervisor(fixture.environment(guard, OPEN_GATE));
        tamperStore();

        supervisor.start(List.of(fixture.freshRequest(role)));

        assertAll(
                () -> assertEquals(1, fixture.refusals.size(), "the refused launch is recorded once"),
                () -> assertEquals("harness_tampered", fixture.refusals.getFirst().reason().code(),
                        "the refusal names the tampered harness store"),
                () -> assertFalse(guard.holds(role.artifact().name(), role.harness()),
                        "one refused launch does not hold the guard"));
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
    @DisplayName("the open guard reports a refusal but never holds")
    void openGuardCountsNothing() {
        var role = fixture.role(RoleClass.WRITER, RoleForm.FRESH, 1);
        var guard = SupervisorEnvironment.StartGuard.open();

        guard.startFailed(role.artifact().name(), role.harness(), "the harness store is unreadable");
        var held = guard.holds(role.artifact().name(), role.harness());

        assertFalse(held, "the open guard never holds the role");
    }

    private StartFailureGuard guard() {
        return new StartFailureGuard(fixture.project, List::of, fixture.protocol, fixture.eventSink, fixture.clock);
    }

    /** Makes the enrolment store fail its sidecar check, which the verifier refuses as {@code harness_tampered}. */
    private void tamperStore() throws IOException {
        Files.writeString(base.resolve("harness").resolve(STORE_FILE), "{}", StandardCharsets.UTF_8);
    }
}
