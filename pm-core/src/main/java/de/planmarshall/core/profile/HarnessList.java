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
import java.util.Map;
import java.util.Objects;

/**
 * The harness list of the onboarding's {@code harness.choose} step and of the machine's settings (PM-CLIENT-5, Harness
 * Profiles § Tokens, Not Money; State Catalogue § {@code harness.choose}): every harness of the scan with its version,
 * whether it is supported and may serve workers, whether it is the default, and what it reports of its token usage.
 * <p>
 * The list keeps the scan's order — the order of the harness ids — so a client shows one order whichever set it reads.
 * The usage coverage of a harness comes from the verification its enrolment recorded; a harness no verification recorded
 * is {@code not_reported}, never {@code complete}. Where the enrolment store could not be read, every entry carries
 * {@code harness_store_unreadable} instead of an empty map, because the refusal is a fact about the list and an empty map
 * is not.
 *
 * @param entries the entries, in the order of their harness ids
 * @since 0.1
 */
public record HarnessList(List<HarnessListEntry> entries) {

    /**
     * @throws NullPointerException     if the list is {@code null}
     * @throws IllegalArgumentException if two entries carry one harness id
     */
    public HarnessList {
        var ordered = new ArrayList<>(List.copyOf(entries));
        ordered.sort(Comparator.comparing(HarnessListEntry::harnessId));
        var ids = new HashSet<String>();
        for (var entry : ordered) {
            if (!ids.add(entry.harnessId())) {
                throw new IllegalArgumentException("Two list entries carry the harness id " + entry.harnessId());
            }
        }
        entries = List.copyOf(ordered);
    }

    /**
     * @param scan            the scan the list shows
     * @param recorded        the usage coverage per harness id, as the harness's verification recorded it
     * @param storeUnreadable whether the enrolment store could not be read
     * @return the list, in the order of the harness ids
     * @throws NullPointerException if a component is {@code null}
     */
    public static HarnessList of(HarnessScan scan, Map<String, HarnessUsageCoverage> recorded, boolean storeUnreadable) {
        Objects.requireNonNull(scan, "scan");
        Objects.requireNonNull(recorded, "recorded");
        var defaultHarness = scan.defaultHarness();
        var entries = new ArrayList<HarnessListEntry>();
        for (var candidate : scan.candidates()) {
            var coverage = storeUnreadable ? HarnessUsageCoverage.unreadable()
                    : recorded.getOrDefault(candidate.harnessId(), HarnessUsageCoverage.nothingRecorded());
            entries.add(HarnessListEntry.builder()
                    .harnessId(candidate.harnessId())
                    .displayName(candidate.displayName())
                    .version(candidate.version() == null ? "" : candidate.version())
                    .supported(candidate.supported())
                    .workerEligible(candidate.workerEligible())
                    .defaultHarness(defaultHarness.filter(candidate.harnessId()::equals).isPresent())
                    .usage(coverage)
                    .build());
        }
        return new HarnessList(entries);
    }

    /** @return the harness ids of the list, in the order of the list */
    public List<String> harnessIds() {
        return entries.stream().map(HarnessListEntry::harnessId).toList();
    }

    /**
     * @return the entries a client preselects, in the order of the list
     */
    public List<HarnessListEntry> preselected() {
        return entries.stream().filter(HarnessListEntry::selectable).toList();
    }
}
