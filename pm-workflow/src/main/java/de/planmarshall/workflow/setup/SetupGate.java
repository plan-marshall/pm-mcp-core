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

import java.util.List;
import java.util.Objects;

/**
 * The setup gate of a start, as the machine scope's onboarding decides it (PM-WF-26, PM-SEC-8; Workflow DSL,
 * Waiting for the Operator, item 6 and Machine Scope, item 6). While the machine's first onboarding has not reached
 * {@code onboard.completed}, no plan, epic, or project onboarding starts: the gate refuses with
 * {@value #MACHINE_NOT_ONBOARDED}, naming the missing step and both clients. An act over the local API that needs a
 * step of the onboarding that is not done yet is refused with {@value #ONBOARDING_INCOMPLETE} and the HTTP status
 * {@value #CONFLICT}. The onboarding itself and the operator's own verbs are admitted in every state.
 * <p>
 * Once the first run has reached its resting state the gate admits every start and every act; a later reconfigure run
 * of one aspect never closes it again.
 *
 * @since 0.1
 */
public final class SetupGate {

    /** The reason of a start refused while the machine's first onboarding has not completed. */
    public static final String MACHINE_NOT_ONBOARDED = "machine_not_onboarded";

    /** The reason of an act refused because an onboarding step it needs is not done yet. */
    public static final String ONBOARDING_INCOMPLETE = "onboarding_incomplete";

    /** The HTTP status of a refusal over the local API: the request conflicts with the machine's state. */
    public static final int CONFLICT = 409;

    /** The operator's two clients, both named in a refusal so the operator knows where the onboarding goes on. */
    public static final List<String> CLIENTS = List.of("web", "cli");

    private SetupGate() {
    }

    /**
     * What the operator asks the scope to do, as the gate decides it.
     *
     * @since 0.1
     */
    public enum Act {
        /** Start a plan. */
        START_PLAN,
        /** Start an epic. */
        START_EPIC,
        /** Start the onboarding of a project. */
        START_PROJECT_ONBOARDING,
        /** A step of the machine's own onboarding. */
        ONBOARDING_STEP,
        /** An operator verb, such as cancelling or configuring the machine. */
        OPERATOR_VERB
    }

    /**
     * What the gate decided.
     *
     * @since 0.1
     */
    public sealed interface Decision permits Admitted, Refused {
    }

    /** The act is admitted. */
    public record Admitted() implements Decision {
    }

    /**
     * The act is refused.
     *
     * @param reason      {@value SetupGate#MACHINE_NOT_ONBOARDED} or {@value SetupGate#ONBOARDING_INCOMPLETE}
     * @param httpStatus  the status of the refusal over the local API
     * @param missingStep the step of the onboarding that has not completed
     * @param clients     the operator's clients, where the onboarding goes on
     * @since 0.1
     */
    public record Refused(String reason, int httpStatus, String missingStep, List<String> clients)
            implements Decision {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public Refused {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(missingStep, "missingStep");
            clients = List.copyOf(clients);
        }
    }

    private static final Admitted ADMITTED = new Admitted();

    /**
     * Decides an act of the operator against the machine's onboarding.
     *
     * @param act        what the operator asks for
     * @param onboarding the onboarding, read from the machine scope's status
     * @return the decision
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Decision decide(Act act, MachineOnboarding onboarding) {
        Objects.requireNonNull(act, "act");
        Objects.requireNonNull(onboarding, "onboarding");
        if (act == Act.ONBOARDING_STEP || act == Act.OPERATOR_VERB || onboarding.firstRunCompleted()) {
            return ADMITTED;
        }
        return new Refused(MACHINE_NOT_ONBOARDED, CONFLICT, onboarding.missingStep(), CLIENTS);
    }

    /**
     * Decides an act over the local API that needs one step of the machine's onboarding.
     *
     * @param neededStep the step the act needs
     * @param onboarding the onboarding, read from the machine scope's status
     * @return the decision
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Decision decideStep(String neededStep, MachineOnboarding onboarding) {
        Objects.requireNonNull(neededStep, "neededStep");
        Objects.requireNonNull(onboarding, "onboarding");
        if (onboarding.firstRunCompleted() || onboarding.completedSteps().contains(neededStep)) {
            return ADMITTED;
        }
        var missing = onboarding.pendingSteps().contains(neededStep) ? neededStep : onboarding.missingStep();
        return new Refused(ONBOARDING_INCOMPLETE, CONFLICT, missing, CLIENTS);
    }
}
