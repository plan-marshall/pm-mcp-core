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
import java.util.Optional;

import lombok.experimental.UtilityClass;

/**
 * The start of a project's onboarding: it asks the machine onboarding's guard before anything of the requested checkout
 * is read (State Catalogue § Project Onboarding; Credentials &amp; Enrolment § Onboarding a Project as One Process,
 * PM-EXT-8, PM-WF-26). While the machine's first onboarding has not reached {@code onboard.completed}, the request is
 * refused with {@value SetupGate#MACHINE_NOT_ONBOARDED}, the HTTP status {@value SetupGate#CONFLICT}, the missing step
 * and the operator's two clients, and the checkout is never inspected.
 * <p>
 * The guard is {@link SetupGate#decide(SetupGate.Act, MachineOnboarding)}; this type adds no second gate. It wires the
 * call into the start and keeps the order the specification requires: the machine's state is decided first, so a
 * refusal cannot be masked by anything the checkout would have answered.
 *
 * @since 0.1
 */
@UtilityClass
public final class ProjectOnboardingStart {

    /**
     * The decision of one start request.
     *
     * @since 0.1
     */
    public sealed interface Decision permits Admitted, MachineNotOnboarded, CheckoutRefused {
    }

    /** The onboarding may open its entry for the checkout. */
    public record Admitted() implements Decision {
    }

    /**
     * The machine's first onboarding has not completed, so no project onboarding starts.
     *
     * @param reason      {@value SetupGate#MACHINE_NOT_ONBOARDED}
     * @param httpStatus  the status of the refusal over the local API
     * @param missingStep the step of the machine's onboarding that has not completed
     * @param clients     the operator's clients, where the onboarding goes on
     * @since 0.1
     */
    public record MachineNotOnboarded(String reason, int httpStatus, String missingStep, List<String> clients)
            implements Decision {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public MachineNotOnboarded {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(missingStep, "missingStep");
            clients = List.copyOf(clients);
        }
    }

    /**
     * The checkout itself cannot be onboarded, as the inspection of the checkout found it.
     *
     * @param reason the refusal's reason code, for example {@code enrolment_root_conflict}
     * @param detail what the inspection saw, for the operator
     * @since 0.1
     */
    public record CheckoutRefused(String reason, String detail) implements Decision {

        /**
         * @throws NullPointerException if a component is {@code null}
         */
        public CheckoutRefused {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(detail, "detail");
        }
    }

    /**
     * The inspection of the requested checkout, run only after the machine's guard admitted the start.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface CheckoutInspection {

        /**
         * @return the refusal of the checkout, or empty when the checkout may be onboarded
         */
        Optional<CheckoutRefused> inspect();
    }

    /**
     * Decides the start of a project's onboarding: the machine's guard first, the checkout after it.
     *
     * @param onboarding the machine's onboarding, read from the machine scope's status
     * @param checkout   the inspection of the requested checkout
     * @return the decision
     * @throws NullPointerException if an argument is {@code null}
     */
    public static Decision start(MachineOnboarding onboarding, CheckoutInspection checkout) {
        Objects.requireNonNull(onboarding, "onboarding");
        Objects.requireNonNull(checkout, "checkout");
        var gate = SetupGate.decide(SetupGate.Act.START_PROJECT_ONBOARDING, onboarding);
        if (gate instanceof SetupGate.Refused refused) {
            return new MachineNotOnboarded(refused.reason(), refused.httpStatus(), refused.missingStep(),
                    refused.clients());
        }
        return checkout.inspect().map(Decision.class::cast).orElseGet(Admitted::new);
    }
}
