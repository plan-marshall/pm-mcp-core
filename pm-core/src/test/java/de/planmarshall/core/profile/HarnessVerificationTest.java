/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.profile;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;

/**
 * The admission of a verification of an unknown harness (PM-CLIENT-5, Harness Profiles § Unknown Harness; State
 * Catalogue § {@code harness.add-consent} to § {@code harness.verify-profile}): the state, the consent kind that is never
 * remembered, the answer with the expected tokens of the stages and the draft that must validate as a profile before
 * anything runs.
 */
@EnableGeneratorController
@DisplayName("Verification of an unknown harness")
class HarnessVerificationTest {

    private static final long IDENTITY_TOKENS = Generators.longs(10, 500).next();
    private static final long USAGE_TOKENS = Generators.longs(1_000, 20_000).next();

    @Test
    @DisplayName("a yes with the expected tokens admits the validated draft")
    void yesWithTokensAdmitsTheValidatedDraft() {
        var profile = HarnessVerification.admit(HarnessVerification.VERIFY_STATE, ConsentKind.VERIFY_HARNESS_PROFILE,
                ConsentGate.Answer.YES, tokens(), ProfileFixtures.validBytes(), "draft/acme-cli.json",
                ProfileRules.none());

        assertEquals("acme-cli", ProfileValidator.clientId(profile), "The admitted draft is the validated one");
    }

    @Test
    @DisplayName("the verification runs only from harness.verify-profile")
    void verificationRunsOnlyFromItsOwnState() {
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(HarnessVerification.CONSENT_STATE,
                        ConsentKind.VERIFY_HARNESS_PROFILE, ConsentGate.Answer.YES, tokens(),
                        ProfileFixtures.validBytes(), "draft/acme-cli.json", ProfileRules.none()),
                "The consent state itself does not run the stages");

        assertEquals(HarnessVerificationRefusal.WRONG_STATE, refusal.code(), "The refusal names the state rule");
    }

    @Test
    @DisplayName("the verification is refused under another consent kind")
    void verificationRefusedUnderAnotherKind() {
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(HarnessVerification.VERIFY_STATE, ConsentKind.SCAN_HARNESSES,
                        ConsentGate.Answer.YES, tokens(), ProfileFixtures.validBytes(), "draft/acme-cli.json",
                        ProfileRules.none()),
                "A wider consent covers no verification");

        assertEquals(HarnessVerificationRefusal.WRONG_KIND, refusal.code(), "The refusal names the kind rule");
    }

    @Test
    @DisplayName("the consent kind is never remembered, so every run asks again")
    void consentKindIsNeverRemembered() {
        assertAll(
                () -> assertFalse(ConsentKind.VERIFY_HARNESS_PROFILE.rememberable(), "The kind is never remembered"),
                () -> assertEquals(ConsentKind.Remembered.NEVER, ConsentKind.VERIFY_HARNESS_PROFILE.remembered(),
                        "The kind is recorded as never remembered"),
                () -> assertEquals(HarnessVerificationRefusal.REMEMBERED,
                        assertThrows(HarnessVerificationRefusal.class,
                                () -> HarnessVerification.admit(HarnessVerification.VERIFY_STATE,
                                        ConsentKind.VERIFY_HARNESS_PROFILE, ConsentGate.Answer.YES_REMEMBER, tokens(),
                                        ProfileFixtures.validBytes(), "draft/acme-cli.json", ProfileRules.none()),
                                "A yes_remember is refused for this kind")
                                .code(),
                        "The refusal names the remembered consent"));
    }

    @Test
    @DisplayName("a no answer runs no stage")
    void noAnswerRunsNoStage() {
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(HarnessVerification.VERIFY_STATE,
                        ConsentKind.VERIFY_HARNESS_PROFILE, ConsentGate.Answer.NO, tokens(),
                        ProfileFixtures.validBytes(), "draft/acme-cli.json", ProfileRules.none()),
                "A declined consent admits nothing");

        assertEquals(HarnessVerificationRefusal.DECLINED, refusal.code(), "The refusal names the declined answer");
    }

    @Test
    @DisplayName("a consent without the expected tokens of its stages is refused")
    void consentWithoutStageTokensRefused() {
        assertAll(
                () -> assertEquals(HarnessVerificationRefusal.TOKENS_MISSING,
                        assertThrows(HarnessVerificationRefusal.class,
                                () -> HarnessVerification.admit(HarnessVerification.VERIFY_STATE,
                                        ConsentKind.VERIFY_HARNESS_PROFILE, ConsentGate.Answer.YES, Map.of(),
                                        ProfileFixtures.validBytes(), "draft/acme-cli.json", ProfileRules.none()),
                                "A consent without tokens is refused")
                                .code(),
                        "The refusal names the missing tokens"),
                () -> assertEquals(HarnessVerificationRefusal.TOKENS_MISSING,
                        assertThrows(HarnessVerificationRefusal.class,
                                () -> HarnessVerification.admit(HarnessVerification.VERIFY_STATE,
                                        ConsentKind.VERIFY_HARNESS_PROFILE, ConsentGate.Answer.YES,
                                        Map.of("no_such_stage", IDENTITY_TOKENS), ProfileFixtures.validBytes(),
                                        "draft/acme-cli.json", ProfileRules.none()),
                                "A stage outside the closed list is refused")
                                .code(),
                        "The refusal names the token rule"));
    }

    @Test
    @DisplayName("a draft that is not a profile is refused before anything runs")
    void invalidDraftRefusedBeforeAnythingRuns() {
        var invalid = ProfileFixtures.valid().remove("/schema_version").bytes();

        assertThrows(ProfileRefusal.class,
                () -> HarnessVerification.admit(HarnessVerification.VERIFY_STATE,
                        ConsentKind.VERIFY_HARNESS_PROFILE, ConsentGate.Answer.YES, tokens(), invalid,
                        "draft/acme-cli.json", ProfileRules.none()),
                "The draft is validated as a profile first");
    }

    private static Map<String, Long> tokens() {
        var tokens = new LinkedHashMap<String, Long>();
        tokens.put("identity", IDENTITY_TOKENS);
        tokens.put("usage", USAGE_TOKENS);
        return tokens;
    }
}
