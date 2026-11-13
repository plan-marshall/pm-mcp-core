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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * The {@code harness.none} step of the machine onboarding (State Catalogue § {@code harness.none}): no usable harness,
 * so the step names how to install each known one and offers to add another. The onboarding rests here until the
 * operator acts: {@code recheck} returns to {@code harness.scan} and {@code add} opens {@code harness.add}.
 * <p>
 * The step is reached from two transitions: {@code harness.scan} with {@code none_found}, where the known harnesses are
 * every profile of the effective set and none was found, and {@code harness.probe} with {@code all_failed}, where every
 * chosen harness failed its probe. Both carry the install hint of the profile, so the operator can act without reading
 * the profiles.
 *
 * @param known the install hints of the known harnesses, in the order of their harness ids
 * @since 0.1
 */
public record HarnessNone(List<InstallHint> known) {

    /** The two actions of the step, in the order the catalogue names them. */
    public static final List<String> ACTIONS = List.of("recheck", "add");

    /**
     * How to install one known harness: its harness id and its profile's {@code headless.install_hint}.
     *
     * @param harnessId   the id of the profile
     * @param installHint the profile's install hint, rendered in the operator's interface language
     */
    public record InstallHint(String harnessId, String installHint) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the id or the hint is blank
         */
        public InstallHint {
            Objects.requireNonNull(harnessId, "harnessId");
            Objects.requireNonNull(installHint, "installHint");
            if (harnessId.isBlank() || installHint.isBlank()) {
                throw new IllegalArgumentException("An install hint names its harness and the hint");
            }
        }
    }

    /**
     * @throws NullPointerException     if the list is {@code null}
     * @throws IllegalArgumentException if two hints carry one harness id
     */
    public HarnessNone {
        var ordered = new ArrayList<>(List.copyOf(known));
        ordered.sort(Comparator.comparing(InstallHint::harnessId));
        var ids = new HashSet<String>();
        for (var hint : ordered) {
            if (!ids.add(hint.harnessId())) {
                throw new IllegalArgumentException("Two install hints carry the harness id " + hint.harnessId());
            }
        }
        known = List.copyOf(ordered);
    }

    /**
     * @param candidates the known harnesses, whether they were not found or failed their probe
     * @return the step that names how to install each of them
     */
    public static HarnessNone of(List<HarnessCandidate> candidates) {
        return new HarnessNone(candidates.stream()
                .map(candidate -> new InstallHint(candidate.harnessId(), candidate.installHint())).toList());
    }

    /** @return the actions the step offers, {@code recheck} then {@code add} */
    public List<String> actions() {
        return ACTIONS;
    }
}
