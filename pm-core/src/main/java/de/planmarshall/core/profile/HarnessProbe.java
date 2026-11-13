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

import java.util.List;
import java.util.Objects;

/**
 * The result of the login probe of the chosen harnesses (PM-CLIENT-5, Harness Profiles § Login Probe; State Catalogue
 * § {@code harness.probe}): the harnesses the operator chose and the ones that failed their probe.
 * <p>
 * The step's transition is derived from the two sets and nothing else: every chosen harness passed leads to
 * {@code harness.confirm}, at least one failed beside a passed one leads to {@code harness.blocked} where the operator
 * may leave the harness out, and every chosen harness failing leads to {@code harness.none} — no usable harness is left,
 * so the onboarding rests there like it does after a scan that found nothing. The last chosen harness that passed is
 * never left out, which is why the blocked transition needs a passed harness to exist.
 *
 * @param chosen the harnesses the operator chose, by harness id
 * @param failed the chosen harnesses whose probe failed, by harness id
 * @since 0.1
 */
public record HarnessProbe(List<String> chosen, List<String> failed) {

    /** The transition of {@code harness.probe}, as the catalogue names it. */
    public enum Outcome {
        /** Every chosen harness passed: the step goes on to {@code harness.confirm}. */
        ALL_PASSED("all_passed", "harness.confirm"),
        /** At least one passed and at least one failed: the step goes to {@code harness.blocked}. */
        ONE_FAILED("one_failed", "harness.blocked"),
        /** Every chosen harness failed: the step goes to {@code harness.none}. */
        ALL_FAILED("all_failed", "harness.none");

        private final String wireName;
        private final String nextState;

        Outcome(String wireName, String nextState) {
            this.wireName = wireName;
            this.nextState = nextState;
        }

        /** @return the value of the transition */
        public String wireName() {
            return wireName;
        }

        /** @return the state the onboarding goes on to */
        public String nextState() {
            return nextState;
        }
    }

    /**
     * @throws NullPointerException     if a list is {@code null}
     * @throws IllegalArgumentException if nothing was chosen, or a failed harness was not chosen
     */
    public HarnessProbe {
        chosen = List.copyOf(Objects.requireNonNull(chosen, "chosen"));
        failed = List.copyOf(Objects.requireNonNull(failed, "failed"));
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("The probe runs on at least one chosen harness");
        }
        if (!chosen.containsAll(failed)) {
            throw new IllegalArgumentException("A failed harness is one of the chosen ones: " + failed);
        }
    }

    /** @return the transition of the step */
    public Outcome outcome() {
        if (failed.isEmpty()) {
            return Outcome.ALL_PASSED;
        }
        return failed.size() == chosen.size() ? Outcome.ALL_FAILED : Outcome.ONE_FAILED;
    }

    /**
     * @return whether no usable harness is left, so the onboarding rests in {@code harness.none}
     */
    public boolean restsInNone() {
        return outcome() == Outcome.ALL_FAILED;
    }
}
