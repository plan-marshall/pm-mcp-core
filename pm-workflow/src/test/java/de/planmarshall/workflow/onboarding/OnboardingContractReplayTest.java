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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/**
 * The replay of the {@code onboarding-contract} scenarios over the router (State Catalogue § Machine Onboarding,
 * {@code onboard.next}, {@code onboard.auto-result}, {@code settings.choose}): each scenario is one named row, the run
 * kind and the origin it walks, and the outcomes the specification gives it. The scenarios are the contract's, replayed
 * here as the engine half; the units that emit them are resources of {@code plan-marshall-mcp}.
 */
@DisplayName("Onboarding contract replay")
class OnboardingContractReplayTest {

    @Test
    @DisplayName("the autonomous run takes every aspect in order and ends in the result")
    void autoRunReplaysInOrder() {
        var walked = new ArrayList<OnboardingAspect>();
        walked.add(OnboardingAspect.values()[0]);
        var route = OnboardingRouter.returned(OnboardRun.AUTO, OnboardingAspect.values()[0], null);
        while (route.target() == OnboardingRouter.Target.ASPECT) {
            walked.add(route.aspect());
            route = OnboardingRouter.returned(route.run(), route.aspect(), null);
        }
        var last = route;

        assertAll(
                () -> assertEquals(Arrays.asList(OnboardingAspect.values()), walked,
                        "The run takes every aspect in the fixed order, once each"),
                () -> assertEquals(OnboardingRouter.Target.RESULT, last.target(),
                        "The last aspect of the automatic run opens the result"),
                () -> assertEquals(OnboardRun.AUTO, OnboardingRouter.returned(OnboardRun.AUTO,
                        OnboardingAspect.LANGUAGE, null).run(), "An aspect is called with the run it belongs to"));
    }

    @Test
    @DisplayName("the guided first run ends in onboard.completed after the last aspect")
    void firstRunEndsCompleted() {
        var route = OnboardingRouter.returned(OnboardRun.FIRST_RUN, OnboardingAspect.PLAN_DEFAULTS, null);

        assertEquals(OnboardingRouter.Target.COMPLETED, route.target(),
                "The first run completes after the last aspect");
    }

    @Test
    @DisplayName("a change on the result calls that one aspect as a later run and returns to the result")
    void changeOnResult() {
        var result = AutoOnboardingResult.builder()
                .items(List.of(OnboardingProposal.builder()
                        .aspect(OnboardingAspect.MODELS)
                        .key("model.x")
                        .value("opus")
                        .reason(ProposalReason.DEFAULT)
                        .check("✓ model.x")
                        .build()))
                .build();

        var changed = OnboardingRouter.change(result, OnboardingAspect.MODELS);
        var returned = OnboardingRouter.returned(changed.run(), changed.aspect(),
                OnboardingRouter.OpenedFrom.RESULT);

        assertAll(
                () -> assertEquals(OnboardingRouter.Target.ASPECT, changed.target(),
                        "The change calls an aspect"),
                () -> assertEquals(OnboardingAspect.MODELS, changed.aspect(), "The change names that one aspect"),
                () -> assertEquals(OnboardRun.RECONFIGURE, changed.run(), "The aspect runs as a later run"),
                () -> assertEquals(OnboardingRouter.Target.RESULT, returned.target(),
                        "The later run returns to the result it was opened from"));
    }

    @Test
    @DisplayName("a later run opened from the settings returns to the settings")
    void laterRunFromSettings() {
        var returned = OnboardingRouter.returned(OnboardRun.RECONFIGURE, OnboardingAspect.HARNESSES,
                OnboardingRouter.OpenedFrom.SETTINGS);

        assertAll(
                () -> assertEquals(OnboardingRouter.Target.SETTINGS, returned.target(),
                        "The later run returns to the settings"),
                () -> assertEquals(Optional.of(OnboardingRouter.Target.COMPLETED),
                        OnboardingRouter.afterSettings(true).map(OnboardingRouter.Route::target),
                        "The settings end in onboard.completed"),
                () -> assertTrue(OnboardingRouter.afterSettings(false).isEmpty(),
                        "A link inside the settings stays there"),
                () -> assertEquals(Optional.of(OnboardingRouter.Target.COMPLETED),
                        OnboardingRouter.afterResult(true).map(OnboardingRouter.Route::target),
                        "The result ends in onboard.completed"));
    }

    @Test
    @DisplayName("a change naming an aspect the result does not carry is refused")
    void changeWithoutItem() {
        var refused = assertThrows(IllegalArgumentException.class,
                () -> OnboardingRouter.change(AutoOnboardingResult.empty(), OnboardingAspect.PROVIDERS),
                "A change needs an item to change");

        assertTrue(refused.getMessage().contains("providers"), "The refusal names the aspect");
    }

    @Test
    @DisplayName("a later run without its origin, and another run with one, are refused")
    void refusedOrigins() {
        var withoutOrigin = assertThrows(IllegalArgumentException.class,
                () -> OnboardingRouter.returned(OnboardRun.RECONFIGURE, OnboardingAspect.MODELS, null),
                "A later run names where it was opened from");
        var withOrigin = assertThrows(IllegalArgumentException.class,
                () -> OnboardingRouter.returned(OnboardRun.AUTO, OnboardingAspect.MODELS,
                        OnboardingRouter.OpenedFrom.SETTINGS),
                "Only a later run is opened from a state");

        assertAll(
                () -> assertTrue(withoutOrigin.getMessage().contains("opened from"),
                        "The refusal names the missing origin"),
                () -> assertTrue(withOrigin.getMessage().contains("auto"), "The refusal names the run"));
    }

    @Test
    @DisplayName("the scenarios of the contract replay to the targets the state catalogue names")
    void scenarios() {
        record Case(String name, OnboardRun run, OnboardingRouter.OpenedFrom openedFrom, OnboardingAspect returned,
        OnboardingRouter.Target expected) {
        }
        var cases = List.of(
                new Case("auto-run-first-aspect", OnboardRun.AUTO, null, OnboardingAspect.LANGUAGE,
                        OnboardingRouter.Target.ASPECT),
                new Case("auto-run-last-aspect", OnboardRun.AUTO, null, OnboardingAspect.PLAN_DEFAULTS,
                        OnboardingRouter.Target.RESULT),
                new Case("first-run-last-aspect", OnboardRun.FIRST_RUN, null, OnboardingAspect.PLAN_DEFAULTS,
                        OnboardingRouter.Target.COMPLETED),
                new Case("later-run-from-settings", OnboardRun.RECONFIGURE, OnboardingRouter.OpenedFrom.SETTINGS,
                        OnboardingAspect.PROVIDERS, OnboardingRouter.Target.SETTINGS),
                new Case("later-run-from-result", OnboardRun.RECONFIGURE, OnboardingRouter.OpenedFrom.RESULT,
                        OnboardingAspect.MODELS, OnboardingRouter.Target.RESULT));

        assertAll(cases.stream()
                .map(one -> (Executable) () -> assertEquals(one.expected(),
                        OnboardingRouter.returned(one.run(), one.returned(), one.openedFrom()).target(),
                        one.name() + " targets")));
    }
}
