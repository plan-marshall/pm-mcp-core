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
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;
import de.planmarshall.core.profile.HarnessVerification.Request;

/**
 * The admission of a verification of an unknown harness (PM-CLIENT-5, Harness Profiles § Unknown Harness; State
 * Catalogue § {@code harness.add-consent} to § {@code harness.verify-profile}): the state, the consent kind that is never
 * remembered, the answer with the expected tokens of exactly the stages the caller declares as the run's, and the draft
 * that must validate as a profile before anything runs.
 */
@EnableGeneratorController
@DisplayName("Verification of an unknown harness")
class HarnessVerificationTest {

    private static final List<String> STAGES = List.of("identity", "launch", "usage");
    private static final long IDENTITY_TOKENS = Generators.longs(10, 500).next();
    private static final long LAUNCH_TOKENS = Generators.longs(500, 5_000).next();
    private static final long USAGE_TOKENS = Generators.longs(1_000, 20_000).next();

    @Test
    @DisplayName("a yes with the expected tokens admits the validated draft")
    void yesWithTokensAdmitsTheValidatedDraft() {
        var profile = HarnessVerification.admit(request(ConsentGate.Answer.YES, STAGES, tokens()));
        var clientId = ProfileValidator.clientId(profile);

        assertEquals("acme-cli", clientId, "The admitted draft is the validated one");
    }

    @Test
    @DisplayName("the verification runs only from harness.verify-profile")
    void verificationRunsOnlyFromItsOwnState() {
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(new Request(HarnessVerification.CONSENT_STATE,
                        ConsentKind.VERIFY_HARNESS_PROFILE, ConsentGate.Answer.YES, STAGES, tokens(),
                        draft(), "draft/acme-cli.json", ProfileRules.none())),
                "The consent state itself does not run the stages");

        assertEquals(HarnessVerificationRefusal.WRONG_STATE, refusal.code(), "The refusal names the state rule");
    }

    @Test
    @DisplayName("the verification is refused under another consent kind")
    void verificationRefusedUnderAnotherKind() {
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(new Request(HarnessVerification.VERIFY_STATE,
                        ConsentKind.SCAN_HARNESSES, ConsentGate.Answer.YES, STAGES, tokens(),
                        draft(), "draft/acme-cli.json", ProfileRules.none())),
                "A wider consent covers no verification");

        assertEquals(HarnessVerificationRefusal.WRONG_KIND, refusal.code(), "The refusal names the kind rule");
    }

    @Test
    @DisplayName("the consent kind is never remembered, so every run asks again")
    void consentKindIsNeverRemembered() {
        var rememberable = ConsentKind.VERIFY_HARNESS_PROFILE.rememberable();
        var remembered = ConsentKind.VERIFY_HARNESS_PROFILE.remembered();
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request(ConsentGate.Answer.YES_REMEMBER, STAGES, tokens())),
                "A yes_remember is refused for this kind");

        assertAll(
                () -> assertFalse(rememberable, "The kind is never remembered"),
                () -> assertEquals(ConsentKind.Remembered.NEVER, remembered,
                        "The kind is recorded as never remembered"),
                () -> assertEquals(HarnessVerificationRefusal.REMEMBERED, refusal.code(),
                        "The refusal names the remembered consent"));
    }

    @Test
    @DisplayName("a no answer runs no stage")
    void noAnswerRunsNoStage() {
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request(ConsentGate.Answer.NO, STAGES, tokens())),
                "A declined consent admits nothing");

        assertEquals(HarnessVerificationRefusal.DECLINED, refusal.code(), "The refusal names the declined answer");
    }

    @Test
    @DisplayName("the tokens must cover exactly the stages the caller declared")
    void tokensCoverExactlyTheDeclaredStages() {
        var subset = new LinkedHashMap<String, Long>();
        subset.put("identity", IDENTITY_TOKENS);
        var extra = new LinkedHashMap<>(tokens());
        extra.put("egress", IDENTITY_TOKENS);

        var subsetRefusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request(ConsentGate.Answer.YES, STAGES, subset)),
                "A consent that showed a subset prices stages it never showed");
        var extraRefusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request(ConsentGate.Answer.YES, STAGES, extra)),
                "A token of a stage the run does not execute is refused");
        var emptyRefusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request(ConsentGate.Answer.YES, STAGES, Map.of())),
                "A consent without tokens is refused");

        assertAll(
                () -> assertEquals(HarnessVerificationRefusal.TOKENS_MISSING, subsetRefusal.code(),
                        "The subset refusal names the token rule"),
                () -> assertEquals(HarnessVerificationRefusal.TOKENS_MISSING, extraRefusal.code(),
                        "The extra refusal names the token rule"),
                () -> assertEquals(HarnessVerificationRefusal.TOKENS_MISSING, emptyRefusal.code(),
                        "The empty refusal names the missing tokens"));
    }

    @Test
    @DisplayName("a stage outside the closed list is refused")
    void unknownStageRefused() {
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request(ConsentGate.Answer.YES, List.of("no_such_stage"),
                        Map.of("no_such_stage", IDENTITY_TOKENS))),
                "A stage outside the verification's closed list is refused");

        assertEquals(HarnessVerificationRefusal.TOKENS_MISSING, refusal.code(), "The refusal names the token rule");
    }

    @Test
    @DisplayName("a draft that is not a profile is refused before anything runs")
    void invalidDraftRefusedBeforeAnythingRuns() {
        var invalid = new String(ProfileFixtures.valid().remove("/schema_version").bytes(), StandardCharsets.UTF_8);
        var request = new Request(HarnessVerification.VERIFY_STATE, ConsentKind.VERIFY_HARNESS_PROFILE,
                ConsentGate.Answer.YES, STAGES, tokens(), invalid, "draft/acme-cli.json", ProfileRules.none());

        assertThrows(ProfileRefusal.class, () -> HarnessVerification.admit(request),
                "The draft is validated as a profile first");
    }

    @Test
    @DisplayName("a missing draft name or rule set is refused before the draft is read")
    void missingDraftNameOrRulesRefused() {
        assertAll(
                () -> assertThrows(NullPointerException.class,
                        () -> new Request(HarnessVerification.VERIFY_STATE, ConsentKind.VERIFY_HARNESS_PROFILE,
                                ConsentGate.Answer.YES, STAGES, tokens(), draft(), null, ProfileRules.none()),
                        "A verification names the draft it validates"),
                () -> assertThrows(NullPointerException.class,
                        () -> new Request(HarnessVerification.VERIFY_STATE, ConsentKind.VERIFY_HARNESS_PROFILE,
                                ConsentGate.Answer.YES, STAGES, tokens(), draft(), "draft/acme-cli.json", null),
                        "A verification names the rules it validates with"),
                () -> assertDoesNotThrow(
                        () -> HarnessVerification.admit(request(ConsentGate.Answer.YES, STAGES, tokens())),
                        "The complete admission still passes"));
    }

    private static Request request(ConsentGate.Answer answer, List<String> stages, Map<String, Long> stageTokens) {
        return new Request(HarnessVerification.VERIFY_STATE, ConsentKind.VERIFY_HARNESS_PROFILE, answer, stages,
                stageTokens, draft(), "draft/acme-cli.json", ProfileRules.none());
    }

    private static String draft() {
        return new String(ProfileFixtures.validBytes(), StandardCharsets.UTF_8);
    }

    private static Map<String, Long> tokens() {
        var tokens = new LinkedHashMap<String, Long>();
        tokens.put("identity", IDENTITY_TOKENS);
        tokens.put("launch", LAUNCH_TOKENS);
        tokens.put("usage", USAGE_TOKENS);
        return tokens;
    }
}
