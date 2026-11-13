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
import java.util.Optional;
import java.util.Set;

/**
 * The result of one harness scan (PM-CLIENT-5, Harness Profiles § Harness Scan; State Catalogue § {@code harness.choose}):
 * every profile of the effective profile set with what the scan found of it, listed and preselected in the order of the
 * harness ids.
 * <p>
 * The scan decides three things the onboarding's {@code harness.choose} step shows. The list is in the order of the
 * {@code harness_id}, never in the order the profile set happened to read, so both clients show one order. Every
 * supported worker-eligible harness is preselected, and the first of them in that order is the default. The scan also
 * decides whether the step continues at all: a scan that found nothing goes to {@code harness.none}, which
 * {@link #none()} renders with each known harness's install hint.
 *
 * @param candidates the candidates, in the order of their harness ids
 * @since 0.1
 */
public record HarnessScan(List<HarnessCandidate> candidates) {

    /** The outcome of a scan, as the onboarding's transition names it. */
    public enum Outcome {
        /** At least one executable of the profile set was found. */
        FOUND("found"),
        /** No executable of the profile set was found. */
        NONE_FOUND("none_found");

        private final String wireName;

        Outcome(String wireName) {
            this.wireName = wireName;
        }

        /** @return the value of the transition */
        public String wireName() {
            return wireName;
        }
    }

    /**
     * @throws NullPointerException     if the list is {@code null}
     * @throws IllegalArgumentException if two candidates carry one harness id
     */
    public HarnessScan {
        var ordered = new ArrayList<>(List.copyOf(candidates));
        ordered.sort(Comparator.comparing(HarnessCandidate::harnessId));
        var ids = new HashSet<String>();
        for (var candidate : ordered) {
            if (!ids.add(candidate.harnessId())) {
                throw new IllegalArgumentException("Two candidates carry the harness id " + candidate.harnessId());
            }
        }
        candidates = List.copyOf(ordered);
    }

    /** @return the harness ids of the candidates, in the order of the list */
    public List<String> harnessIds() {
        return candidates.stream().map(HarnessCandidate::harnessId).toList();
    }

    /** @return the candidates a client preselects, in the order of the list */
    public List<HarnessCandidate> preselected() {
        return candidates.stream().filter(HarnessCandidate::selectable).toList();
    }

    /**
     * @return the default harness, the first preselected one in the order of the harness ids; empty where nothing is
     *         selectable
     */
    public Optional<String> defaultHarness() {
        return preselected().stream().map(HarnessCandidate::harnessId).findFirst();
    }

    /** @return whether any candidate was found at all */
    public boolean found() {
        return candidates.stream().anyMatch(HarnessCandidate::found);
    }

    /** @return {@code found} where anything was found, {@code none_found} otherwise */
    public Outcome outcome() {
        return found() ? Outcome.FOUND : Outcome.NONE_FOUND;
    }

    /**
     * @return the {@code harness.none} wait for a scan that found nothing; empty where the scan found something, because
     *         the step then goes on to {@code harness.choose}
     */
    public Optional<HarnessNone> none() {
        return found() ? Optional.empty() : Optional.of(HarnessNone.of(candidates));
    }

    /**
     * @return the ids of the candidates, as a set
     */
    public Set<String> knownIds() {
        return Set.copyOf(harnessIds());
    }
}
