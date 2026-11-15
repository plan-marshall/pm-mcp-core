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

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.Builder;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.config.ConsentGate;
import de.planmarshall.core.config.ConsentKind;

/**
 * The admission of a verification of an unknown harness (PM-CLIENT-5, Harness Profiles § Unknown Harness; State
 * Catalogue § {@code harness.add} to § {@code harness.verify-profile}): the draft is validated as a profile, and only
 * then does anything run.
 * <p>
 * The verification is a sub-workflow the onboarding reaches in exactly one way: {@code harness.add} submits the draft,
 * {@code harness.add-consent} asks the consent kind {@code verify_harness_profile} stating the expected tokens of the
 * stages the run will execute, and only a {@code yes} enters {@code harness.verify-profile}. The kind is never
 * remembered, so the question is asked before every verification, in a first run and in a reconfiguration alike: a
 * {@code yes_remember} is refused here rather than read from the store, and no {@code yes_remember} link exists for the
 * kind. {@link #admit} is the one place that decides all of it, so a caller cannot reach the stages by another path.
 * <p>
 * The tokens must cover <em>exactly</em> the stages the caller declared as the run's, and each of them once: a consent
 * that showed a subset would authorise stages it never priced, and one that showed a stage the run does not execute
 * prices nothing. The list is the run's own precondition — the gate cannot see the run, so it holds the caller to the
 * list it states.
 * <p>
 * Validating the draft is part of the admission, not a step before it: a draft that breaks the profile schema — an
 * unknown member, an out-of-range value, a path rule — is refused {@link ProfileRefusal} before the first stage starts,
 * so the operator fixes the form instead of watching a run fail.
 *
 * @since 0.1
 */
@UtilityClass
public class HarnessVerification {

    /** The consent state of an unknown harness, as the state catalogue names it. */
    public static final String CONSENT_STATE = "harness.add-consent";

    /** The only state the verification runs from. */
    public static final String VERIFY_STATE = "harness.verify-profile";

    /**
     * One verification of an unknown harness, as its caller states it.
     *
     * @param state       the state the verification was asked from
     * @param kind        the consent kind the caller asked for
     * @param answer      the operator's answer to that consent
     * @param stages      the stages the caller declared as the run's, in the order they run; the admission treats the
     *                    list as the run's own precondition — it validates that every name is a stage of the profile
     *                    verification and that the tokens price exactly this list, and it cannot know a list the caller
     *                    does not state
     * @param stageTokens the expected tokens per stage, as the consent showed them
     * @param draft       the draft profile document, as text
     * @param draftName   the draft, as a refusal names it
     * @param rules       what the installation knows beyond the draft
     * @since 0.1
     */
    @Builder
    public record Request(String state, ConsentKind kind, ConsentGate.Answer answer, List<String> stages,
    Map<String, Long> stageTokens, String draft, String draftName, ProfileRules rules) {

        /**
         * @throws NullPointerException if a required component is {@code null}
         */
        public Request {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(answer, "answer");
            stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
            stageTokens = Collections.unmodifiableMap(
                    new LinkedHashMap<>(Objects.requireNonNull(stageTokens, "stageTokens")));
            Objects.requireNonNull(draft, "draft");
            Objects.requireNonNull(draftName, "draftName");
            Objects.requireNonNull(rules, "rules");
        }
    }

    /**
     * @param request the verification the caller asks for
     * @return the validated draft
     * @throws HarnessVerificationRefusal if the state is not {@code harness.verify-profile}
     *                                    ({@code harness_verify_state_refused}), the consent is not the kind
     *                                    {@code verify_harness_profile} ({@code harness_verify_consent_kind}), the
     *                                    answer was {@code no} ({@code harness_verify_consent_declined}) or a
     *                                    {@code yes_remember} ({@code harness_verify_consent_remembered}), the run names
     *                                    no stage ({@code harness_verify_stages_missing}), names one twice
     *                                    ({@code harness_verify_stage_duplicated}) or names one that is not a stage of
     *                                    the profile verification ({@code harness_verify_stage_unknown}), the shown
     *                                    tokens do not price exactly that list
     *                                    ({@code harness_verify_stage_tokens_missing}), or a stage's tokens are not a
     *                                    non-negative count ({@code harness_verify_stage_token_not_a_count})
     * @throws ProfileRefusal             if the draft does not validate as a profile
     */
    public static JsonNode admit(Request request) {
        requireState(request.state());
        requireKind(request.kind());
        requireAnswer(request.answer());
        requireStages(request.stages());
        requireStageTokens(request.stages(), request.stageTokens());
        return ProfileValidator.validate(request.draft().getBytes(StandardCharsets.UTF_8), request.draftName(),
                request.rules());
    }

    private static void requireState(String state) {
        if (!VERIFY_STATE.equals(state)) {
            throw new HarnessVerificationRefusal(HarnessVerificationRefusal.WRONG_STATE,
                    "The verification requires the state '%s', not '%s'".formatted(VERIFY_STATE, state));
        }
    }

    private static void requireKind(ConsentKind kind) {
        if (kind != ConsentKind.VERIFY_HARNESS_PROFILE) {
            throw new HarnessVerificationRefusal(HarnessVerificationRefusal.WRONG_KIND,
                    "The verification is asked under the consent kind '%s', not '%s'"
                            .formatted(ConsentKind.VERIFY_HARNESS_PROFILE.wireName(), kind.wireName()));
        }
    }

    private static void requireAnswer(ConsentGate.Answer answer) {
        if (answer == ConsentGate.Answer.NO) {
            throw new HarnessVerificationRefusal(HarnessVerificationRefusal.DECLINED,
                    "The operator answered 'no', so no stage runs and the form stays");
        }
        if (answer == ConsentGate.Answer.YES_REMEMBER) {
            throw new HarnessVerificationRefusal(HarnessVerificationRefusal.REMEMBERED,
                    "The consent kind '%s' is never remembered, so a 'yes_remember' is refused"
                            .formatted(ConsentKind.VERIFY_HARNESS_PROFILE.wireName()));
        }
    }

    private static void requireStages(List<String> stages) {
        if (stages.isEmpty()) {
            throw new HarnessVerificationRefusal(HarnessVerificationRefusal.STAGES_MISSING,
                    "A verification runs at least one stage");
        }
        if (new HashSet<>(stages).size() != stages.size()) {
            throw new HarnessVerificationRefusal(HarnessVerificationRefusal.STAGE_DUPLICATED,
                    "A stage is named twice in the run: " + stages);
        }
        for (var stage : stages) {
            if (!VerificationRecord.STAGE_NAMES.contains(stage)) {
                throw new HarnessVerificationRefusal(HarnessVerificationRefusal.STAGE_UNKNOWN,
                        "Not a stage of the profile verification: " + stage);
            }
        }
    }

    private static void requireStageTokens(List<String> stages, Map<String, Long> stageTokens) {
        if (!new HashSet<>(stages).equals(stageTokens.keySet())) {
            throw new HarnessVerificationRefusal(HarnessVerificationRefusal.TOKENS_MISSING,
                    "The consent prices exactly the stage list the caller declared: %s, not %s"
                            .formatted(stages, stageTokens.keySet()));
        }
        for (var entry : stageTokens.entrySet()) {
            var tokens = entry.getValue();
            if (tokens == null || tokens < 0) {
                throw new HarnessVerificationRefusal(HarnessVerificationRefusal.TOKEN_NOT_A_COUNT,
                        "The expected tokens of the stage '%s' are a non-negative count".formatted(entry.getKey()));
            }
        }
    }
}
