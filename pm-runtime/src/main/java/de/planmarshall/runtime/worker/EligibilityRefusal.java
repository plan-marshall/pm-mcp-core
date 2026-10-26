/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.util.Objects;

/**
 * Why the verification-record gate refused a profile's claim (PM-CLIENT-5, Runtime check on load), and what decided it:
 * the closed reason, the stage that failed when a stage decided the refusal, and the profile the verdict is about.
 * <p>
 * The reason set is closed and each reason names the fact the record did not establish: the record is absent, a stage
 * failed, a stage did not run, the record was written on another operating system or architecture, or the stage the
 * claim rests on was never recorded at all. The stage is {@code null} when no single stage decided the refusal (an
 * absent record, another platform).
 *
 * @param reason   why the claim was refused
 * @param stage    the stage that decided the refusal; {@code null} when no single stage is to blame
 * @param clientId the client id of the profile the record belongs to
 * @since 0.1
 */
public record EligibilityRefusal(Reason reason, String stage, String clientId) {

    /** The closed reasons a claim is refused. */
    public enum Reason {

        /** The profile carries a claim, but it has no verification record. */
        RECORD_ABSENT("record_absent"),

        /** A stage the claim rests on was recorded as {@code failed}. */
        STAGE_FAILED("stage_failed"),

        /** A stage the claim rests on was recorded as {@code not_run}. */
        STAGE_NOT_RUN("stage_not_run"),

        /** The record was written on another operating system or architecture. */
        OTHER_PLATFORM("other_platform"),

        /** A stage the claim rests on is absent from the record altogether. */
        CLAIM_STAGE_ABSENT("claim_stage_absent");

        private final String wireName;

        Reason(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the reason in a representation */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * @throws NullPointerException if the reason or the client id is {@code null}
     */
    public EligibilityRefusal {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(clientId, "clientId");
    }
}
