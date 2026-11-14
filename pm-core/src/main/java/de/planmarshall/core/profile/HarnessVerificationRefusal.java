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

/**
 * The refusal of one attempt to verify an unknown harness (PM-CLIENT-5, Harness Profiles § Unknown Harness; State
 * Catalogue § {@code harness.add-consent} and § {@code harness.verify-profile}): the step is reached only from its
 * consent step, only through the consent kind that is never remembered, and only with a draft that validates as a
 * profile.
 * <p>
 * The {@link #code()} is the value a client shows and a test asserts; the message names the state, the kind or the
 * member the refusal is about, so an operator can act on it without reading the code.
 *
 * @since 0.1
 */
public final class HarnessVerificationRefusal extends RuntimeException {

    /** The step the verification is asked from is not {@code harness.verify-profile}. */
    public static final String WRONG_STATE = "harness_verify_state_refused";

    /** The consent asked is not the kind {@code verify_harness_profile}. */
    public static final String WRONG_KIND = "harness_verify_consent_kind";

    /** The operator answered {@code no}. */
    public static final String DECLINED = "harness_verify_consent_declined";

    /** A {@code yes_remember} was answered, which the kind never offers. */
    public static final String REMEMBERED = "harness_verify_consent_remembered";

    /** The run names no stage at all. */
    public static final String STAGES_MISSING = "harness_verify_stages_missing";

    /** One stage of the run is named twice. */
    public static final String STAGE_DUPLICATED = "harness_verify_stage_duplicated";

    /** One stage of the run is not a stage of the profile verification. */
    public static final String STAGE_UNKNOWN = "harness_verify_stage_unknown";

    /** The consent did not price exactly the stages the run declares. */
    public static final String TOKENS_MISSING = "harness_verify_stage_tokens_missing";

    /** A stage's expected tokens are not a non-negative count. */
    public static final String TOKEN_NOT_A_COUNT = "harness_verify_stage_token_not_a_count";

    private final String code;

    /**
     * @param code    the refusal code
     * @param message what the refusal names
     */
    public HarnessVerificationRefusal(String code, String message) {
        super(message);
        this.code = code;
    }

    /** @return the code of the refusal, a value of this class's constants */
    public String code() {
        return code;
    }
}
