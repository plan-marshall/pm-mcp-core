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
 * The closed verdict of the verification-record gate (PM-CLIENT-5, Runtime check on load): a profile's claim is either
 * honoured, or refused with the {@link EligibilityRefusal} that names the reason, the stage and the profile.
 * <p>
 * The type has exactly two members, so a caller cannot read a third state into it: a claim that was not examined is
 * not a refusal, and a refusal always carries the fact that refused it. The supervisor reads
 * {@link #isEligible()} before a start; a report that names why a start was declined reads the refusal of
 * {@link Refused}.
 *
 * @since 0.1
 */
public sealed interface EligibilityVerdict permits EligibilityVerdict.Eligible, EligibilityVerdict.Refused {

    /**
     * @return the verdict of a profile whose claims the record established, or of one that makes no claim
     */
    static EligibilityVerdict eligible() {
        return new Eligible();
    }

    /**
     * @param refusal the fact that refused the claim
     * @return the refusing verdict
     * @throws NullPointerException if the refusal is {@code null}
     */
    static EligibilityVerdict refused(EligibilityRefusal refusal) {
        return new Refused(refusal);
    }

    /** @return whether the profile's claims are honoured */
    default boolean isEligible() {
        return this instanceof Eligible;
    }

    /** The profile's claims are honoured: it makes none, it is shipped, or its record established them. */
    record Eligible() implements EligibilityVerdict {
    }

    /**
     * The profile's claims are refused.
     *
     * @param refusal the fact that refused the claim
     * @since 0.1
     */
    record Refused(EligibilityRefusal refusal) implements EligibilityVerdict {

        /** @throws NullPointerException if the refusal is {@code null} */
        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }
}
