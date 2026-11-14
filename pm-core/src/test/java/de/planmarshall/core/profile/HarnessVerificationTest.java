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
        var request = request(ConsentGate.Answer.YES, STAGES, tokens());
        var profile = HarnessVerification.admit(request);
        var clientId = ProfileValidator.clientId(profile);

        assertEquals("acme-cli", clientId, "The admitted draft is the validated one");
    }

    @Test
    @DisplayName("the verification runs only from harness.verify-profile")
    void verificationRunsOnlyFromItsOwnState() {
        var request = requestFrom(HarnessVerification.CONSENT_STATE);
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request), "The consent state itself does not run the stages");

        assertEquals(HarnessVerificationRefusal.WRONG_STATE, refusal.code(), "The refusal names the state rule");
    }

    @Test
    @DisplayName("the verification is refused under another consent kind")
    void verificationRefusedUnderAnotherKind() {
        var request = requestFrom(HarnessVerification.VERIFY_STATE, ConsentKind.SCAN_HARNESSES);
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request), "A wider consent covers no verification");

        assertEquals(HarnessVerificationRefusal.WRONG_KIND, refusal.code(), "The refusal names the kind rule");
    }

    @Test
    @DisplayName("the consent kind is never remembered, so every run asks again")
    void consentKindIsNeverRemembered() {
        var rememberable = ConsentKind.VERIFY_HARNESS_PROFILE.rememberable();
        var remembered = ConsentKind.VERIFY_HARNESS_PROFILE.remembered();
        var request = request(ConsentGate.Answer.YES_REMEMBER, STAGES, tokens());
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request), "A yes_remember is refused for this kind");

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
        var request = request(ConsentGate.Answer.NO, STAGES, tokens());
        var refusal = assertThrows(HarnessVerificationRefusal.class,
                () -> HarnessVerification.admit(request), "A declined consent admits nothing");

        assertEquals(HarnessVerificationRefusal.DECLINED, refusal.code(), "The refusal names the declined answer");
    }

    @Test
    @DisplayName("the tokens must cover exactly the stages the caller declared")
    void tokensCoverExactlyTheDeclaredStages() {
        var subset = new LinkedHashMap<String, Long>();
        subset.put("identity", IDENTITY_TOKENS);
        var extra = new LinkedHashMap<>(tokens());
        extra.put("egress", IDENTITY_TOKENS);

        var subsetRefusal = refusalOf(request(ConsentGate.Answer.YES, STAGES, subset),
                "A consent that showed a subset prices stages it never showed");
        var extraRefusal = refusalOf(request(ConsentGate.Answer.YES, STAGES, extra),
                "A token of a stage the run does not execute is refused");
        var emptyRefusal = refusalOf(request(ConsentGate.Answer.YES, STAGES, Map.of()),
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
        var request = request(ConsentGate.Answer.YES, List.of("no_such_stage"),
                Map.of("no_such_stage", IDENTITY_TOKENS));
        var refusal = refusalOf(request, "A stage outside the verification's closed list is refused");

        assertEquals(HarnessVerificationRefusal.STAGE_UNKNOWN, refusal.code(),
                "An unknown stage names its own refusal");
    }

    @Test
    @DisplayName("a run that names no stage is refused")
    void emptyStageListRefused() {
        var request = request(ConsentGate.Answer.YES, List.of(), Map.of());
        var refusal = refusalOf(request, "A verification without a stage cannot be admitted");

        assertEquals(HarnessVerificationRefusal.STAGES_MISSING, refusal.code(),
                "An empty run names at least one stage");
    }

    @Test
    @DisplayName("a stage named twice in the run is refused")
    void duplicatedStageRefused() {
        var twice = List.of("identity", "identity");
        var request = request(ConsentGate.Answer.YES, twice, Map.of("identity", IDENTITY_TOKENS));
        var refusal = refusalOf(request, "A stage named twice cannot be admitted");

        assertEquals(HarnessVerificationRefusal.STAGE_DUPLICATED, refusal.code(),
                "A duplicated stage is refused as such");
    }

    @Test
    @DisplayName("a stage token that is not a count is refused")
    void tokenNotACountRefused() {
        var negative = new LinkedHashMap<String, Long>();
        negative.put("identity", -1L);
        var request = request(ConsentGate.Answer.YES, List.of("identity"), negative);
        var refusal = refusalOf(request, "A negative token count cannot be admitted");

        assertEquals(HarnessVerificationRefusal.TOKEN_NOT_A_COUNT, refusal.code(),
                "A negative token count is refused as such");
    }

    @Test
    @DisplayName("a draft that is not a profile is refused before anything runs")
    void invalidDraftRefusedBeforeAnythingRuns() {
        var invalid = new String(ProfileFixtures.valid().remove("/schema_version").bytes(), StandardCharsets.UTF_8);
        var request = requestBuilder(ConsentGate.Answer.YES, STAGES, tokens()).draft(invalid).build();

        assertThrows(ProfileRefusal.class, () -> HarnessVerification.admit(request),
                "The draft is validated as a profile first");
    }

    @Test
    @DisplayName("a missing draft name or rule set is refused before the draft is read")
    void missingDraftNameOrRulesRefused() {
        var stageTokens = tokens();
        var complete = request(ConsentGate.Answer.YES, STAGES, stageTokens);
        var withoutName = requestBuilder(ConsentGate.Answer.YES, STAGES, stageTokens);
        var withoutRules = requestBuilder(ConsentGate.Answer.YES, STAGES, stageTokens);

        assertAll(
                () -> assertThrows(NullPointerException.class, () -> withoutName.draftName(null).build(),
                        "A verification names the draft it validates"),
                () -> assertThrows(NullPointerException.class, () -> withoutRules.rules(null).build(),
                        "A verification names the rules it validates with"),
                () -> assertDoesNotThrow(() -> HarnessVerification.admit(complete),
                        "The complete admission still passes"));
    }

    private static HarnessVerificationRefusal refusalOf(Request request, String message) {
        return assertThrows(HarnessVerificationRefusal.class, () -> HarnessVerification.admit(request), message);
    }

    private static Request request(ConsentGate.Answer answer, List<String> stages, Map<String, Long> stageTokens) {
        return requestBuilder(answer, stages, stageTokens).build();
    }

    private static Request.RequestBuilder requestBuilder(ConsentGate.Answer answer, List<String> stages,
            Map<String, Long> stageTokens) {
        return Request.builder()
                .state(HarnessVerification.VERIFY_STATE)
                .kind(ConsentKind.VERIFY_HARNESS_PROFILE)
                .answer(answer)
                .stages(stages)
                .stageTokens(stageTokens)
                .draft(draft())
                .draftName("draft/acme-cli.json")
                .rules(ProfileRules.none());
    }

    private static Request requestFrom(String state) {
        return requestFrom(state, ConsentKind.VERIFY_HARNESS_PROFILE);
    }

    private static Request requestFrom(String state, ConsentKind kind) {
        return requestBuilder(ConsentGate.Answer.YES, STAGES, tokens()).state(state).kind(kind).build();
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
