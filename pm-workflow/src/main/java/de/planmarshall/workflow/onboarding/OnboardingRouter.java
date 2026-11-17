/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.onboarding;

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.config.OnboardingAspect;
import lombok.Builder;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The engine half of the onboarding's router, {@code onboard.next} (State Catalogue § Machine Onboarding): it reads the
 * {@code onboard_run}, the aspect that returned and, for a later run, where that run was opened from, and answers where
 * the run goes next.
 * <ul>
 * <li>the first run takes the aspects in their fixed order and ends in {@code onboard.completed} after the last;</li>
 * <li>the automatic run takes them in the same order and its last aspect opens {@code onboard.auto-result};</li>
 * <li>a {@code change} on {@code auto.result} calls that one aspect as a later run ({@code onboard_run: reconfigure}),
 * and its return leads back to the result;</li>
 * <li>a later run opened from {@code settings.choose} returns there.</li>
 * </ul>
 * The router is a value: it reads no state and writes none, and a call it cannot make — a change naming an aspect the
 * result does not carry — is refused with the reason, never guessed.
 *
 * @since 0.1
 */
@UtilityClass
public class OnboardingRouter {

    /** The wire name of the automatic run's result, shared by the target and the opening state. */
    private static final String RESULT = "result";

    /** The wire name of the settings, shared by the target and the opening state. */
    private static final String SETTINGS = "settings";

    /** The wire name of an aspect call, shared by the target and the change check. */
    private static final String ASPECT = "aspect";

    /** The states the router answers with: an aspect call, or one of the three states a run ends in. */
    public enum Target {
        /** Call the aspect the route names. */
        ASPECT(OnboardingRouter.ASPECT),
        /** {@code onboard.completed}: the guided first run reached its last aspect. */
        COMPLETED("completed"),
        /** {@code onboard.auto-result}: the automatic run reached its last aspect. */
        RESULT(OnboardingRouter.RESULT),
        /** {@code settings.choose}: a later run returned. */
        SETTINGS(OnboardingRouter.SETTINGS);

        private final String wireName;

        Target(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the outcome name the state catalogue gives the target
         */
        public String wireName() {
            return wireName;
        }
    }

    /** Where a later run was opened from, which decides where its return leads. */
    public enum OpenedFrom {
        /** {@code settings.choose} ({@code reconfigure:<aspect>}); the return leads there. */
        SETTINGS(OnboardingRouter.SETTINGS),
        /** {@code auto.result} ({@code change:<aspect>}); the return leads back to the result. */
        RESULT(OnboardingRouter.RESULT);

        private final String wireName;

        OpenedFrom(String wireName) {
            this.wireName = wireName;
        }

        /**
         * @return the name of the opening state's family
         */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * Where one step of the run goes.
     *
     * @param target the call or the state the run reaches
     * @param aspect the aspect to call, for {@link Target#ASPECT}; {@code null} for the other targets
     * @param run    the run the aspect is called with, for {@link Target#ASPECT}; {@code null} otherwise
     * @since 0.1
     */
    @Builder
    public record Route(Target target, @Nullable OnboardingAspect aspect, @Nullable OnboardRun run) {

        /**
         * Refuses an aspect route without its aspect and a run, and a non-aspect route that names one.
         *
         * @param target the target
         * @param aspect the aspect, {@code null} for a non-aspect target
         * @param run    the run, {@code null} for a non-aspect target
         * @throws NullPointerException     if the target is {@code null}
         * @throws IllegalArgumentException if the target and the aspect or the run disagree
         */
        public Route {
            Objects.requireNonNull(target, "target");
            if (target == Target.ASPECT && (aspect == null || run == null)) {
                throw new IllegalArgumentException("an aspect route names its aspect and its run");
            }
            if (target != Target.ASPECT && (aspect != null || run != null)) {
                throw new IllegalArgumentException("the target '%s' calls no aspect".formatted(target.wireName()));
            }
        }

        static Route aspect(OnboardingAspect aspect, OnboardRun run) {
            return new Route(Target.ASPECT, Objects.requireNonNull(aspect, ASPECT),
                    Objects.requireNonNull(run, "run"));
        }

        static Route to(Target target) {
            return new Route(target, null, null);
        }
    }

    /**
     * The {@code change} of {@code auto.result}: the operator opened one aspect to change it, so it is called as a later
     * run with its current values as the proposal, and its return leads back to the result.
     *
     * @param result the automatic run's result
     * @param aspect the aspect the operator opened
     * @return the aspect's call as {@code onboard_run: reconfigure}
     * @throws IllegalArgumentException if the result carries no item of the aspect, so there is nothing to change
     */
    public static Route change(AutoOnboardingResult result, OnboardingAspect aspect) {
        Objects.requireNonNull(result, RESULT);
        Objects.requireNonNull(aspect, ASPECT);
        if (result.of(aspect).isEmpty()) {
            throw new IllegalArgumentException(
                    "the automatic run's result carries no item of the aspect '%s'".formatted(aspect.wireName()));
        }
        return Route.aspect(aspect, OnboardRun.RECONFIGURE);
    }

    /**
     * Where the run goes after one aspect returned.
     *
     * @param run        the run the aspect was called with
     * @param returned   the aspect that returned {@code done}
     * @param openedFrom where a later run was opened from; {@code null} for the first and the automatic run
     * @return the next aspect, or the state the run ends in
     * @throws IllegalArgumentException if {@code reconfigure} names no opening state, or another run names one
     */
    public static Route returned(OnboardRun run, OnboardingAspect returned, @Nullable OpenedFrom openedFrom) {
        Objects.requireNonNull(run, "run");
        Objects.requireNonNull(returned, "returned");
        if (run == OnboardRun.RECONFIGURE) {
            if (openedFrom == null) {
                throw new IllegalArgumentException("a later run names where it was opened from");
            }
            return Route.to(openedFrom == OpenedFrom.RESULT ? Target.RESULT : Target.SETTINGS);
        }
        if (openedFrom != null) {
            throw new IllegalArgumentException("the run '%s' is opened from no settings state"
                    .formatted(run.wireName()));
        }
        var next = returned.next();
        if (next.isPresent()) {
            return Route.aspect(next.get(), run);
        }
        return Route.to(run == OnboardRun.AUTO ? Target.RESULT : Target.COMPLETED);
    }

    /**
     * Where the settings go after the operator's {@code done}.
     *
     * @param done whether the operator finished the settings
     * @return {@code onboard.completed} for {@code done}; empty for the links that stay in the settings
     */
    public static Optional<Route> afterSettings(boolean done) {
        return done ? Optional.of(Route.to(Target.COMPLETED)) : Optional.empty();
    }

    /**
     * Where the automatic run's result goes after the operator's {@code done}.
     *
     * @param done whether the operator finished the result
     * @return {@code onboard.completed} for {@code done}; empty for the {@code change} links, which the router answers
     *         with {@link #change(AutoOnboardingResult, OnboardingAspect)}
     */
    public static Optional<Route> afterResult(boolean done) {
        return done ? Optional.of(Route.to(Target.COMPLETED)) : Optional.empty();
    }
}
