/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.setup;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.WorkflowStatus;

/**
 * The start of a project's onboarding asks the machine onboarding's guard before the requested checkout is inspected
 * (State Catalogue § Project Onboarding; PM-EXT-8, PM-WF-26): while the machine's first run has not completed, the
 * start is refused {@code machine_not_onboarded} with 409, the missing step and both clients, and the checkout is never
 * read; once the machine is onboarded, the checkout decides.
 */
@EnableGeneratorController
@DisplayName("Project onboarding start")
class ProjectOnboardingStartTest {

    /** A reason code and a detail of the checkout inspection, kept literally as the contract they are. */
    private static final String ROOT_CONFLICT = "enrolment_root_conflict";

    private static final String INSPECTED_DETAIL = "another enrolment names the checkout";

    private static final String STEPS = """
            [{"step": "language", "state": "done"},
             {"step": "harnesses", "state": "open"},
             {"step": "models", "state": "open"}]""";

    @Test
    @DisplayName("Should refuse the start before the machine onboarding and never inspect the checkout")
    void startsOnlyAfterTheMachineOnboarding() {
        var inspected = new AtomicBoolean();
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.harnesses", "\"first_run\"", STEPS));

        var decision = ProjectOnboardingStart.start(onboarding, () -> {
            inspected.set(true);
            return Optional.empty();
        });

        var refused = assertInstanceOf(ProjectOnboardingStart.MachineNotOnboarded.class, decision,
                "The start is refused while the machine is not onboarded");
        assertAll(
                () -> assertEquals(SetupGate.MACHINE_NOT_ONBOARDED, refused.reason(),
                        "The refusal reports machine_not_onboarded"),
                () -> assertEquals(SetupGate.CONFLICT, refused.httpStatus(),
                        "The machine refusal maps to HTTP 409"),
                () -> assertEquals("harnesses", refused.missingStep(),
                        "The refusal names the missing machine step"),
                () -> assertEquals(List.of("web", "cli"), refused.clients(),
                        "The refusal names both operator clients"),
                () -> assertFalse(inspected.get(),
                        "The checkout is not inspected before the guard admits"));
    }

    @Test
    @DisplayName("Should admit the start and inspect the checkout once the machine is onboarded")
    void inspectsTheCheckoutAfterTheGuardAdmits() {
        var inspections = new AtomicInteger();
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.completed", null, STEPS));

        var decision = ProjectOnboardingStart.start(onboarding, () -> {
            inspections.incrementAndGet();
            return Optional.empty();
        });

        assertAll(
                () -> assertInstanceOf(ProjectOnboardingStart.Admitted.class, decision,
                        "An onboarded machine admits the start"),
                () -> assertEquals(1, inspections.get(), "The checkout is inspected exactly once"));
    }

    @Test
    @DisplayName("Should carry the checkout's own refusal after the machine guard admitted")
    void carriesTheCheckoutRefusal() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.completed", null, STEPS));

        var decision = ProjectOnboardingStart.start(onboarding,
                () -> Optional.of(new ProjectOnboardingStart.CheckoutRefused(ROOT_CONFLICT, INSPECTED_DETAIL)));

        var refused = assertInstanceOf(ProjectOnboardingStart.CheckoutRefused.class, decision,
                "A refused checkout is reported as the checkout's own refusal");
        assertAll(
                () -> assertEquals(ROOT_CONFLICT, refused.reason(), "The checkout refusal keeps its reason code"),
                () -> assertEquals(INSPECTED_DETAIL, refused.detail(), "The checkout refusal keeps its detail"));
    }

    @Test
    @DisplayName("Should keep the machine refusal even when the checkout would be refused too")
    void machineRefusalWinsOverTheCheckout() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.mode", "\"first_run\"", STEPS));

        var decision = ProjectOnboardingStart.start(onboarding,
                () -> Optional.of(new ProjectOnboardingStart.CheckoutRefused(ROOT_CONFLICT, INSPECTED_DETAIL)));

        assertInstanceOf(ProjectOnboardingStart.MachineNotOnboarded.class, decision,
                "The machine's refusal is not masked by the checkout");
    }

    private static WorkflowStatus machineStatus(String state, String onboardRun, String steps) {
        var retained = new LinkedHashMap<String, String>();
        if (onboardRun != null) {
            retained.put("onboard_run", onboardRun);
        }
        retained.put("onboard_steps", steps);
        return WorkflowStatus.builder()
                .scopeType(JournalScopeType.MACHINE)
                .phase("machine")
                .state(state)
                .callStack(List.of())
                .policyCounters(Map.of())
                .waiverRequests(List.of())
                .retryCounters(RetryCounters.empty())
                .activeJobs(Map.of())
                .activeTasks(Map.of())
                .updatedAt(Instant.parse("2026-10-26T10:00:00Z"))
                .retained(retained)
                .build();
    }
}
