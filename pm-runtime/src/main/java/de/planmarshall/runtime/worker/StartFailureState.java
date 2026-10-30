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

import java.time.Instant;
import java.util.Objects;
import lombok.With;
import org.jspecify.annotations.Nullable;

/**
 * The state of one role-and-harness pair of the {@link StartFailureGuard} (PM-WORK-3, Start-Failure Guard): the
 * consecutive start failures the guard counted, whether it holds, the instant its next start is the probe, whether that
 * probe is in flight, and the last failure text the report bounded and redacted.
 * <p>
 * The value is a snapshot, not a mutable holder: the guard writes the successor value through the {@code @With}
 * methods, so a caller never observes half a transition. The state of a pair that counted no failure is {@link #OPEN};
 * one that counted failures but does not hold has no probe due, and one that holds owes both the due instant and, while
 * a probe runs, the in-flight flag that keeps every later start held. The count is kept per supervisor and rebuilt
 * from the supervisor's generation registry when the guard is constructed.
 *
 * @param consecutiveFailures the consecutive start failures counted, never negative
 * @param held               whether the guard holds the role's starts on the harness
 * @param probeDue           the instant the next start is one probe; {@code null} exactly while the guard does not hold
 * @param probeInFlight      whether the one probe started and has not settled yet
 * @param lastFailure        the bounded, redacted error text of the last counted failure; {@code null} while none is
 *                           counted here
 * @since 0.1
 */
@With
public record StartFailureState(int consecutiveFailures, boolean held, @Nullable Instant probeDue,
boolean probeInFlight, @Nullable String lastFailure) {

    /** The state of a role and harness that counted no failure: open, with nothing due. */
    public static final StartFailureState OPEN = new StartFailureState(0, false, null, false, null);

    /**
     * @throws IllegalArgumentException if the count is negative, the hold and the probe instant do not exclude each
     *                                  other, a probe is in flight without a hold, or the failure text is empty
     */
    public StartFailureState {
        if (consecutiveFailures < 0) {
            throw new IllegalArgumentException("'consecutiveFailures' must not be negative: " + consecutiveFailures);
        }
        if (held && probeDue == null) {
            throw new IllegalArgumentException("A holding guard owes the instant of its probe");
        }
        if (!held && probeDue != null) {
            throw new IllegalArgumentException("An open guard has no probe instant");
        }
        if (probeInFlight && !held) {
            throw new IllegalArgumentException("Only a holding guard runs a probe");
        }
        if (lastFailure != null && lastFailure.isEmpty()) {
            throw new IllegalArgumentException("'lastFailure' is empty or absent, never blank");
        }
    }

    /**
     * The identity of the pair this state belongs to: the role and the client id of its harness.
     *
     * @param role    the role
     * @param harness the client id of the harness
     * @since 0.1
     */
    public record Key(String role, String harness) {

        /**
         * @throws NullPointerException if a value is {@code null}
         */
        public Key {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(harness, "harness");
        }
    }
}
