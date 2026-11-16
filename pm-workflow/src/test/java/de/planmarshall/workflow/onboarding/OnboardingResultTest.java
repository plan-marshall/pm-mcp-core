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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The proposal and result vocabulary of the automatic run (CLI &amp; Security § Automatic Onboarding): the closed aspect
 * order, the closed reason list with its message keys, and the result's split into the values the run decided and the
 * items it could not decide.
 */
@DisplayName("The automatic run's vocabulary")
class OnboardingResultTest {

    private static OnboardingProposal item(OnboardingAspect aspect, String key, String value, ProposalReason reason) {
        return OnboardingProposal.builder()
                .aspect(aspect)
                .key(key)
                .value(value)
                .reason(reason)
                .check("✓ " + key + " " + value)
                .build();
    }

    @Test
    @DisplayName("the aspects keep the fixed order of the guided and the automatic run")
    void aspectOrder() {
        assertAll(
                () -> assertEquals(List.of("language", "harnesses", "models", "role-assignment", "credential-store",
                        "providers", "plan-defaults"),
                        java.util.Arrays.stream(OnboardingAspect.values()).map(OnboardingAspect::wireName).toList(),
                        "The aspects are the specification's, in its order"),
                () -> assertEquals(Optional.of(OnboardingAspect.HARNESSES), OnboardingAspect.LANGUAGE.next(),
                        "The language step is followed by the harnesses"),
                () -> assertEquals(Optional.of(OnboardingAspect.PROVIDERS), OnboardingAspect.CREDENTIAL_STORE.next(),
                        "The providers follow the credential store"),
                () -> assertEquals(Optional.of(OnboardingAspect.PLAN_DEFAULTS), OnboardingAspect.PROVIDERS.next(),
                        "The plan defaults follow the providers"),
                () -> assertEquals(Optional.empty(), OnboardingAspect.PLAN_DEFAULTS.next(),
                        "The last aspect has no successor"),
                () -> assertEquals(Optional.of(OnboardingAspect.MODELS), OnboardingAspect.of("models"),
                        "An aspect is read by its name"),
                () -> assertEquals(Optional.empty(), OnboardingAspect.of("speech"), "An unknown name has no aspect"));
    }

    @Test
    @DisplayName("the reasons are the closed list and each carries its message key")
    void reasons() {
        assertAll(
                () -> assertEquals(List.of("system", "only_supported", "all_supported", "all_found", "default",
                        "available", "tool_logged_in", "asked"),
                        java.util.Arrays.stream(ProposalReason.values()).map(ProposalReason::wireName).toList(),
                        "The reasons are the specification's closed list"),
                () -> assertEquals("onboard.auto.reason.tool_logged_in", ProposalReason.TOOL_LOGGED_IN.messageKey(),
                        "A reason is a message key of the catalogue"),
                () -> assertEquals(Optional.of(ProposalReason.ASKED), ProposalReason.of("asked"),
                        "A reason is read by its value"),
                () -> assertEquals(Optional.empty(), ProposalReason.of("guessed"),
                        "A value outside the list has no reason"));
    }

    @Test
    @DisplayName("the result separates the values it decided from the items it could not decide")
    void decidedAndUndecided() {
        var result = AutoOnboardingResult.builder()
                .items(List.of(
                        item(OnboardingAspect.LANGUAGE, "language.interface", "de", ProposalReason.SYSTEM),
                        item(OnboardingAspect.HARNESSES, "harness.001", "claude", ProposalReason.ALL_SUPPORTED),
                        item(OnboardingAspect.HARNESSES, "harness.002", "codex", ProposalReason.ASKED),
                        item(OnboardingAspect.CREDENTIAL_STORE, "credentials.store", "os",
                                ProposalReason.AVAILABLE)))
                .build();

        assertAll(
                () -> assertEquals(3, result.decided().size(), "The run decided three of the four values"),
                () -> assertEquals(1, result.undecided().size(), "One item was asked of the operator"),
                () -> assertTrue(result.undecided().getFirst().asked(), "The asked item says so"),
                () -> assertEquals(Optional.of("claude"), result.value(OnboardingAspect.HARNESSES, "harness.001"),
                        "A value is read by its aspect and key"),
                () -> assertEquals(Optional.empty(), result.value(OnboardingAspect.PROVIDERS, "providers"),
                        "A key the run never recorded has no value"),
                () -> assertEquals(2, result.of(OnboardingAspect.HARNESSES).size(),
                        "The items of one aspect are kept together"),
                () -> assertTrue(AutoOnboardingResult.empty().items().isEmpty(), "An empty result carries no items"));
    }

    @Test
    @DisplayName("a trade item whose reason is not asked is a decided value")
    void askedIsTheOnlyUndecidedReason() {
        var result = AutoOnboardingResult.builder()
                .items(List.of(item(OnboardingAspect.MODELS, "model.x", "opus", ProposalReason.DEFAULT)))
                .build();

        assertAll(
                () -> assertFalse(result.decided().getFirst().asked(), "A default is decided, not asked"),
                () -> assertTrue(result.undecided().isEmpty(), "Nothing was asked of the operator"));
    }
}
