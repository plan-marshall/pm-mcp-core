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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.core.JsonToken;
import de.planmarshall.core.store.StrictJson;
import de.planmarshall.core.store.WorkflowStatus;

/**
 * The onboarding of the machine as the setup gate reads it from the machine scope's status (PM-WF-26,
 * Workflow DSL, Machine Scope). The machine is configured once the first run of its onboarding has reached
 * {@value #RESTING_STATE}; a later run of one aspect, entered from the machine's settings, never closes the gate
 * again.
 * <p>
 * The first run is the run the unit records in {@code onboard_run} as {@code first_run}; a reconfigure run records
 * {@code reconfigure} and is only possible after the resting state was reached, so it means the machine is
 * configured. The steps and their state are kept in the {@code onboard_steps} member of the status, whose closed
 * values the schema names; the first step that is not {@code done} is the missing step a refusal names.
 *
 * @param firstRunCompleted whether the machine's first onboarding has reached {@value #RESTING_STATE}
 * @param state             the current state of the machine scope
 * @param pendingSteps      the onboarding steps that are not done, in their order; never empty while the first run is
 *                          incomplete
 * @since 0.1
 */
public record MachineOnboarding(boolean firstRunCompleted, String state, List<String> pendingSteps) {

    /** The resting state of the machine unit: the onboarding's first run is complete here. */
    public static final String RESTING_STATE = "onboard.completed";

    private static final String ONBOARD_RUN = "onboard_run";
    private static final String ONBOARD_STEPS = "onboard_steps";
    private static final String RECONFIGURE = "\"reconfigure\"";
    private static final String DONE = "done";
    private static final String STEP = "step";
    private static final String STEP_STATE = "state";

    /**
     * @param firstRunCompleted whether the first run completed
     * @param state             the current state of the machine scope
     * @param pendingSteps      the steps that are not done
     * @throws NullPointerException if the state is {@code null}
     */
    public MachineOnboarding {
        Objects.requireNonNull(state, "state");
        pendingSteps = List.copyOf(pendingSteps);
        if (!firstRunCompleted && pendingSteps.isEmpty()) {
            throw new IllegalArgumentException(
                    "An incomplete machine onboarding names at least one pending step");
        }
    }

    /**
     * Reads the onboarding from the machine scope's status.
     *
     * @param status the status of the machine scope
     * @return the onboarding
     * @throws IllegalArgumentException if the status is not the status of the machine scope, or its
     *                                  {@code onboard_steps} is not the array of step objects the schema admits
     */
    public static MachineOnboarding fromStatus(WorkflowStatus status) {
        Objects.requireNonNull(status, "status");
        if (!"machine".equals(status.scopeType().wireName())) {
            throw new IllegalArgumentException(
                    "The onboarding of the machine is read from the machine scope, not from the %s scope"
                            .formatted(status.scopeType().wireName()));
        }
        var reconfigure = RECONFIGURE.equals(trimmed(status.retained().get(ONBOARD_RUN)));
        var completed = RESTING_STATE.equals(status.state()) || reconfigure;
        var steps = readSteps(status.retained().get(ONBOARD_STEPS));
        var pending = new ArrayList<String>();
        for (var step : steps) {
            if (!DONE.equals(step.state()) && !pending.contains(step.step())) {
                pending.add(step.step());
            }
        }
        if (!completed && pending.isEmpty()) {
            pending.add(status.state());
        }
        return new MachineOnboarding(completed, status.state(), pending);
    }

    /**
     * @return the missing step a refusal names: the first step that is not done, or the current state of the machine
     *         scope when the status carries no step list; {@code null} when the first run completed
     */
    public String missingStep() {
        return pendingSteps.isEmpty() ? null : pendingSteps.getFirst();
    }

    private static String trimmed(String raw) {
        return raw == null ? null : raw.trim();
    }

    private static List<Step> readSteps(String raw) {
        if (raw == null || "null".equals(raw.trim())) {
            return List.of();
        }
        var steps = new ArrayList<Step>();
        try (var parser = StrictJson.factory().createParser(raw)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalArgumentException("'onboard_steps' is an array of step objects: " + raw);
            }
            var token = parser.nextToken();
            while (token == JsonToken.START_OBJECT) {
                String step = null;
                String state = null;
                while (parser.nextToken() != JsonToken.END_OBJECT) {
                    var name = parser.currentName();
                    parser.nextToken();
                    switch (name) {
                        case STEP -> step = parser.getText();
                        case STEP_STATE -> state = parser.getText();
                        default -> parser.skipChildren();
                    }
                }
                if (step == null || state == null) {
                    throw new IllegalArgumentException("A step of 'onboard_steps' names its step and its state: " + raw);
                }
                steps.add(new Step(step, state));
                token = parser.nextToken();
            }
            if (token != JsonToken.END_ARRAY) {
                throw new IllegalArgumentException("'onboard_steps' holds only step objects: " + raw);
            }
            if (parser.nextToken() != null) {
                throw new IllegalArgumentException("'onboard_steps' is one array, nothing after it: " + raw);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("'onboard_steps' is not JSON: " + e.getMessage(), e);
        }
        return steps;
    }

    /**
     * One step of the machine's onboarding.
     *
     * @param step  the sub-workflow id of the step
     * @param state {@code open}, {@code done} or {@code skipped}
     * @since 0.1
     */
    public record Step(String step, String state) {

        /** @throws NullPointerException if a component is {@code null} */
        public Step {
            Objects.requireNonNull(step, STEP);
            Objects.requireNonNull(state, STEP_STATE);
        }
    }
}
