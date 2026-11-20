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
import java.util.Locale;
import java.util.Map;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.planmarshall.core.store.JournalScopeType;
import de.planmarshall.core.store.RetryCounters;
import de.planmarshall.core.store.WorkflowStatus;

/**
 * The setup gate of the machine scope's onboarding (PM-WF-26; Workflow DSL, Machine Scope, item 6): while the first run
 * has not reached {@code onboard.completed}, no plan, epic or project onboarding starts, an act that needs a missing
 * step is refused {@code onboarding_incomplete} with 409, and the onboarding itself and the operator's verbs stay
 * open. Once the first run reached its resting state, every act is admitted.
 */
@EnableGeneratorController
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
                () -> assertFalse(onboarding.firstRunCompleted(), "The first run has not completed yet"),
                () -> assertEquals(List.of("harnesses", "models"), onboarding.pendingSteps(),
                        "The open steps are reported in order"),
                () -> assertEquals("harnesses", onboarding.missingStep().orElseThrow(),
                        "The first pending step is the missing one"));
    }

    @Test
    @DisplayName("Should refuse every start with machine_not_onboarded while the first run is incomplete")
    void startsAreRefused() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.mode", "\"first_run\""));

        for (var act : List.of(SetupGate.Act.START_PLAN, SetupGate.Act.START_EPIC,
                SetupGate.Act.START_PROJECT_ONBOARDING)) {
            var refused = assertInstanceOf(SetupGate.Refused.class, SetupGate.decide(act, onboarding),
                    "the act is refused before machine onboarding");
            assertAll(
                    () -> assertEquals(SetupGate.MACHINE_NOT_ONBOARDED, refused.reason(),
                            "The refusal reports machine_not_onboarded"),
                    () -> assertEquals(409, refused.httpStatus(), "The refusal maps to HTTP 409"),
                    () -> assertEquals("harnesses", refused.missingStep(), "The refusal names the missing step"),
                    () -> assertEquals(List.of("web", "cli"), refused.clients(),
                            "The refusal names the available clients"));
        }
    }

    @Test
    @DisplayName("Should keep the onboarding and the operator's verbs open while the first run is incomplete")
    void onboardingAndOperatorVerbsStayOpen() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.mode", "\"first_run\""));

        assertAll(
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.ONBOARDING_STEP, onboarding),
                        "An onboarding step stays open before completion"),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.OPERATOR_VERB, onboarding),
                        "An operator verb stays open before completion"));
    }

    @Test
    @DisplayName("Should refuse an act that needs a missing step with onboarding_incomplete and 409")
    void missingStepActIsRefused() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.harnesses", "\"first_run\""));

        var refused = assertInstanceOf(SetupGate.Refused.class, SetupGate.decideStep("models", onboarding),
                "An act needing a missing step is refused");

        assertAll(
                () -> assertEquals(SetupGate.ONBOARDING_INCOMPLETE, refused.reason(),
                        "The refusal reports onboarding_incomplete"),
                () -> assertEquals(409, refused.httpStatus(), "The incomplete refusal maps to HTTP 409"),
                () -> assertEquals("models", refused.missingStep(), "The refusal names the pending step"),
                () -> assertEquals(List.of("web", "cli"), refused.clients(), "The refusal names the clients"));
    }

    @Test
    @DisplayName("Should admit every act once the first run reached onboard.completed")
    void completedOnboardingAdmitsEverything() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.completed", null));

        assertAll(
                () -> assertTrue(onboarding.firstRunCompleted(), "The completed first run is recognised"),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.START_PLAN, onboarding),
                        "A start plan is admitted after completion"),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.START_EPIC, onboarding),
                        "A start epic is admitted after completion"),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.START_PROJECT_ONBOARDING, onboarding),
                        "A project onboarding is admitted after completion"),
                () -> assertInstanceOf(SetupGate.Admitted.class, SetupGate.decideStep("models", onboarding),
                        "A step act is admitted after completion"));
    }

    @Test
    @DisplayName("Should admit an act whose step is done while another step is still pending")
    void doneStepIsAdmitted() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("onboard.harnesses", "\"first_run\""));

        assertInstanceOf(SetupGate.Admitted.class, SetupGate.decideStep("language", onboarding),
                "An act whose step is done is admitted");
    }

    @Test
    @DisplayName("Should refuse an act whose step is unknown, and one with no step list at all")
    void unknownStepAndMissingListAreRefused() {
        var withList = MachineOnboarding.fromStatus(machineStatus("onboard.harnesses", "\"first_run\""));
        var withoutList = MachineOnboarding.fromStatus(machineStatusWithoutSteps("onboard.mode", "\"first_run\""));

        var unknownStep = "unknown-" + Generators.letterStrings(1, 8).next();
        assertAll(
                () -> assertInstanceOf(SetupGate.Refused.class, SetupGate.decideStep(unknownStep, withList),
                        "An unknown step is refused"),
                () -> assertInstanceOf(SetupGate.Refused.class, SetupGate.decideStep("language", withoutList),
                        "An act without a step list is refused"));
    }

    @Test
    @DisplayName("Should stay open during a later reconfigure run of one aspect")
    void reconfigureRunStaysOpen() {
        var onboarding = MachineOnboarding.fromStatus(machineStatus("language.choose", "\"reconfigure\""));

        assertAll(
                () -> assertTrue(onboarding.firstRunCompleted(), "A reconfigure run counts as completed"),
                () -> assertInstanceOf(SetupGate.Admitted.class,
                        SetupGate.decide(SetupGate.Act.START_PLAN, onboarding),
                        "A start plan stays open during reconfigure"));
    }

    @Test
    @DisplayName("Should refuse a status that is not the machine scope's")
    void foreignScopeIsRefused() {
        var plan = machineStatus("onboard.completed", null).toBuilder()
                .scopeType(JournalScopeType.PLAN)
                .scopeId("scope-" + Generators.letterStrings(3, 8).next().toLowerCase(Locale.ROOT))
                .build();

        assertThrows(IllegalArgumentException.class, () -> MachineOnboarding.fromStatus(plan),
                "A status from a foreign scope is refused");
    }

    private static WorkflowStatus machineStatusWithoutSteps(String state, String onboardRun) {
        return status(state, onboardRun, null);
    }

    private static WorkflowStatus machineStatus(String state, String onboardRun) {
        return status(state, onboardRun, STEPS);
    }

    private static WorkflowStatus status(String state, String onboardRun, String steps) {
        var retained = new LinkedHashMap<String, String>();
        if (onboardRun != null) {
            retained.put("onboard_run", onboardRun);
        }
        if (steps != null) {
            retained.put("onboard_steps", steps);
        }
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
