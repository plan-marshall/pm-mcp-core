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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.WorkflowStatus;

/**
 * The setup gate of the machine scope's onboarding (PM-WF-26; Workflow DSL, Machine Scope, item 6): while the first run
 * has not reached {@code onboard.completed}, no plan, epic or project onboarding starts, an act that needs a missing
 * step is refused {@code onboarding_incomplete} with 409, and the onboarding itself and the operator's verbs stay
 * open. Once the first run reached its resting state, every act is admitted.
 */
@DisplayName("Machine onboarding setup gate")
class SetupGateTest {

    private static final String STEPS = """
            [{"step": "language", "state": "done"},
             {"step": "harnesses", "state": "open"},
             {"step": "models", "state": "open"}]""";

    @Test
    @DisplayName("Should report the first step that is not done")
    void pendingStepsAreRead() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.harnesses", "\"first_run\""));

        assertAll(
                () -> assertFalse(onboarding.firstRunCompleted()),
                () -> assertEquals(List.of("harnesses", "models"), onboarding.pendingSteps()),
                () -> assertEquals("harnesses", onboarding.missingStep()));
    }

    @Test
    @DisplayName("Should refuse every start with machine_not_onboarded while the first run is incomplete")
    void startsAreRefused() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.mode", "\"first_run\""));

        for (var act : List.of(SetupGate.Act.START_PLAN, SetupGate.Act.START_EPIC,
                SetupGate.Act.START_PROJECT_ONBOARDING)) {
            var refused = assertInstanceOf(SetupGate.Refused.class, SetupGate.decide(act, onboarding), act::name);
            assertAll(
                    () -> assertEquals(SetupGate.MACHINE_NOT_ONBOARDED, refused.reason()),
                    () -> assertEquals(409, refused.httpStatus()),
                    () -> assertEquals("harnesses", refused.missingStep()),
                    () -> assertEquals(List.of("web", "cli"), refused.clients()));
        }
    }

    @Test
    @DisplayName("Should keep the onboarding and the operator's verbs open while the first run is incomplete")
    void onboardingAndOperatorVerbsStayOpen() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.mode", "\"first_run\""));

        assertAll(
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.ONBOARDING_STEP, onboarding)),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.OPERATOR_VERB, onboarding)));
    }

    @Test
    @DisplayName("Should refuse an act that needs a missing step with onboarding_incomplete and 409")
    void missingStepActIsRefused() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.harnesses", "\"first_run\""));

        var refused = assertInstanceOf(SetupGate.Refused.class, SetupGate.decideStep("models", onboarding));

        assertAll(
                () -> assertEquals(SetupGate.ONBOARDING_INCOMPLETE, refused.reason()),
                () -> assertEquals(409, refused.httpStatus()),
                () -> assertEquals("models", refused.missingStep()),
                () -> assertEquals(List.of("web", "cli"), refused.clients()));
    }

    @Test
    @DisplayName("Should admit every act once the first run reached onboard.completed")
    void completedOnboardingAdmitsEverything() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.completed", null));

        assertAll(
                () -> assertTrue(onboarding.firstRunCompleted()),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.START_PLAN, onboarding)),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.START_EPIC, onboarding)),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.START_PROJECT_ONBOARDING, onboarding)),
                () -> assertInstanceOf(SetupGate.Admitted.class, SetupGate.decideStep("models", onboarding)));
    }

    @Test
    @DisplayName("Should admit an act whose step is done while another step is still pending")
    void doneStepIsAdmitted() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.harnesses", "\"first_run\""));

        assertInstanceOf(SetupGate.Admitted.class, SetupGate.decideStep("language", onboarding));
    }

    @Test
    @DisplayName("Should stay open during a later reconfigure run of one aspect")
    void reconfigureRunStaysOpen() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("language.choose", "\"reconfigure\""));

        assertAll(
                () -> assertTrue(onboarding.firstRunCompleted()),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.START_PLAN, onboarding)));
    }

    @Test
    @DisplayName("Should refuse a status that is not the machine scope's")
    void foreignScopeIsRefused() {
        var plan = machineStatus("onboard.completed", null).toBuilder()
                .scopeType(JournalScopeType.PLAN)
                .scopeId("some-plan")
                .build();

        assertThrows(IllegalArgumentException.class, () -> MachineOnboarding.fromStatus(plan));
    }

    private static WorkflowStatus machineStatus(String state, String onboardRun) {
        var retained = new LinkedHashMap<String, String>();
        if (onboardRun != null) {
            retained.put("onboard_run", onboardRun);
        }
        retained.put("onboard_steps", STEPS);
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
