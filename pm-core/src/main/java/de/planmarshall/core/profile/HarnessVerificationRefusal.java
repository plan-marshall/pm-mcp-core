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

    /** The consent kind is rememberable, so a run could be authorised without being asked. */
    public static final String REMEMBERABLE_KIND = "harness_verify_consent_rememberable";

    /** The operator answered {@code no}. */
    public static final String DECLINED = "harness_verify_consent_declined";

    /** A {@code yes_remember} was answered, which the kind never offers. */
    public static final String REMEMBERED = "harness_verify_consent_remembered";

    /** The consent did not show the expected tokens of the stages it authorises. */
    public static final String TOKENS_MISSING = "harness_verify_stage_tokens_missing";

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
