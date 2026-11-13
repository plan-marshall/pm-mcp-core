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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The result of the login probe of the chosen harnesses (PM-CLIENT-5, Harness Profiles § Login Probe; State Catalogue
 * § {@code harness.probe}): the harnesses the operator chose and the ones that failed their probe.
 * <p>
 * The step's transition is derived from the two sets and nothing else: every chosen harness passed leads to
 * {@code harness.confirm}, at least one failed beside a passed one leads to {@code harness.blocked} where the operator
 * may leave the harness out, and every chosen harness failing leads to {@code harness.none} — no usable harness is left,
 * so the onboarding rests there like it does after a scan that found nothing. The sets are compared as sets, so a
 * duplicated id never turns a pass into a failure; two identical ids in one list are refused.
 * <p>
 * One rule of the next step lives here because it is derivable from the sets: {@code harness.blocked} offers
 * {@code leave_out} for any chosen harness but the last one, so the step can never be left without a harness to go on
 * with. {@link #canLeaveOut} answers it.
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
     * @throws IllegalArgumentException if nothing was chosen, if a harness id is empty, if one list names a harness
     *                                  twice, or if a failed harness was not chosen
     */
    public HarnessProbe {
        chosen = List.copyOf(Objects.requireNonNull(chosen, "chosen"));
        failed = List.copyOf(Objects.requireNonNull(failed, "failed"));
        if (chosen.isEmpty()) {
            throw new IllegalArgumentException("The probe runs on at least one chosen harness");
        }
        requireDistinct(chosen, "chosen");
        requireDistinct(failed, "failed");
        if (!new HashSet<>(chosen).containsAll(failed)) {
            throw new IllegalArgumentException("A failed harness is one of the chosen ones: " + failed);
        }
    }

    /** @return the transition of the step */
    public Outcome outcome() {
        var failedIds = Set.copyOf(failed);
        if (failedIds.isEmpty()) {
            return Outcome.ALL_PASSED;
        }
        return failedIds.size() == Set.copyOf(chosen).size() ? Outcome.ALL_FAILED : Outcome.ONE_FAILED;
    }

    /** @return whether no usable harness is left, so the onboarding rests in {@code harness.none} */
    public boolean restsInNone() {
        return outcome() == Outcome.ALL_FAILED;
    }

    /**
     * @param harnessId the harness the operator wants to leave out at {@code harness.blocked}
     * @return whether it may be left out: it is one of the chosen harnesses and not the last of them
     */
    public boolean canLeaveOut(String harnessId) {
        return chosen.contains(harnessId) && chosen.size() > 1;
    }

    /**
     * @param candidates the known harnesses, with their install hints
     * @return the {@code harness.none} wait that names the failed harnesses' install hints, for a probe whose every
     *         harness failed; empty where a usable harness is left
     */
    public Optional<HarnessNone> none(List<HarnessCandidate> candidates) {
        if (!restsInNone()) {
            return Optional.empty();
        }
        var failedIds = Set.copyOf(failed);
        var known = new ArrayList<HarnessCandidate>();
        for (var candidate : candidates) {
            if (failedIds.contains(candidate.harnessId())) {
                known.add(candidate);
            }
        }
        return Optional.of(HarnessNone.of(known));
    }

    private static void requireDistinct(List<String> ids, String name) {
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new IllegalArgumentException("A harness is named twice in '" + name + "': " + ids);
        }
        if (ids.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("Every '" + name + "' harness id is non-blank");
        }
    }
}
