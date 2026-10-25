/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.launch;

import java.util.Objects;

/**
 * Why a worker launch was refused before its spawn, with the act that resolves it (PM-CLIENT-2, Harness Eligibility &amp;
 * Launch Refusals). The refusal reason is the job record's {@code refusal_reason}; the act is shown in both clients and
 * names the command the operator runs, so a held role is never left with an unexplained refusal.
 *
 * @since 0.1
 */
public enum LaunchRefusal {

    /** The harness is not enrolled on this machine. */
    HARNESS_NOT_ENROLLED("harness_not_enrolled",
        "enrol the harness with `pm-operator enrol-harness %s` or the onboarding's `harness.enrol`"),

    /** The executable changed without the update evidence of its profile. */
    HARNESS_UPDATE_UNVERIFIED("harness_update_unverified",
            "verify the changed executable and enrol the harness again with `pm-operator enrol-harness %s`"),

    /** The probed version lies outside the profile's supported range. */
    HARNESS_VERSION_UNSUPPORTED("harness_version_unsupported",
            "enrol a build within the supported range with `pm-operator enrol-harness %s`"),

    /** The harness enrolment store failed its HMAC check. */
    HARNESS_TAMPERED("harness_tampered",
            "enrol the harness again with `pm-operator enrol-harness %s`, which rewrites the store and its sidecar"),

    /** The enrolment store is verified but does not read as the store. */
    HARNESS_STORE_UNREADABLE("harness_store_unreadable",
            "enrol the harness again with `pm-operator enrol-harness %s` to rewrite the unreadable store"),

    /** The launch prompt cannot be composed. */
    PROMPT_COMPOSITION_FAILED("prompt_composition_failed",
            "restore the skill or the schema the prompt reads and launch the task again");

    private final String code;
    private final String act;

    LaunchRefusal(String code, String act) {
        this.code = code;
        this.act = act;
    }

    /** @return the reason code of the job record */
    public String code() {
        return code;
    }

    /**
     * @param clientId the harness the refusal concerns
     * @return the act that resolves it, as both clients show it
     */
    public String resolvingAct(String clientId) {
        return act.formatted(Objects.requireNonNull(clientId, "clientId"));
    }
}
