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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

import de.planmarshall.core.profile.HarnessCandidate;
import de.planmarshall.core.profile.HarnessList;
import de.planmarshall.core.profile.HarnessListEntry;
import de.planmarshall.core.profile.HarnessScan;
import de.planmarshall.core.profile.HarnessUsageCoverage;
import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.runtime.launch.HarnessEnrolment.UsageReport;

/**
 * The bridge from the enrolment store to the harness list (PM-CLIENT-5, Harness Profiles § Tokens, Not Money; Store
 * Schemas § Harness Enrolment Store): the list values belong to {@code core.profile}, the enrolments to this package, and
 * this is the one place that reads one into the other.
 * <p>
 * The coverage is filed under the harness <em>and</em> the version the enrolment records, so a report measured on one
 * version is never shown beside another. The report's figure names are the store's ({@code input}, {@code output}); the
 * list speaks the profile schema's ({@code input_tokens}, {@code output_tokens}), so the two names are mapped here — the
 * inverse of the mapping {@link UsageReport#of} applies on the way in.
 * <p>
 * An unreadable store is passed as the flag rather than read here: the caller holds the lock and knows whether its read
 * was refused, and {@link HarnessList#of} turns the flag into {@code harness_store_unreadable} at every row.
 *
 * @since 0.1
 */
@UtilityClass
public class HarnessListings {

    /**
     * @param scan            the scan the list shows
     * @param enrolments      the enrolled harnesses by harness id, as the caller read them from the store
     * @param storeUnreadable whether the enrolment store could not be read
     * @return the scan's rows, with each harness's recorded usage coverage at the version it was enrolled with, plus a
     *         row for every enrolment whose harness the scan has no candidate for
     * @throws NullPointerException if a component is {@code null}
     */
    public static HarnessList of(HarnessScan scan, Map<String, HarnessEnrolment> enrolments, boolean storeUnreadable) {
        var recorded = new LinkedHashMap<HarnessList.Key, HarnessUsageCoverage>();
        enrolments.values().forEach(enrolment -> recorded.put(
                new HarnessList.Key(enrolment.harnessId(), enrolment.version()),
                coverage(enrolment.usageReport())));
        var list = HarnessList.of(scan, recorded, storeUnreadable);
        var missing = enrolments.values().stream()
                .filter(enrolment -> !foundInScan(scan, enrolment.harnessId()))
                .toList();
        if (missing.isEmpty()) {
            return list;
        }
        var replaced = missing.stream().map(HarnessEnrolment::harnessId).collect(Collectors.toSet());
        var entries = list.entries().stream().filter(entry -> !replaced.contains(entry.harnessId()))
                .collect(Collectors.toCollection(ArrayList::new));
        missing.forEach(enrolment -> entries.add(missingRow(scan, enrolment, storeUnreadable)));
        return new HarnessList(entries);
    }

    /**
     * @param scan      the scan
     * @param harnessId the id of the enrolled harness
     * @return whether the scan found the harness; a candidate the scan lists as not found is a harness the scan no
     *         longer finds, which the list shows as missing instead
     */
    private static boolean foundInScan(HarnessScan scan, String harnessId) {
        return scan.candidates().stream()
                .filter(candidate -> candidate.harnessId().equals(harnessId))
                .anyMatch(HarnessCandidate::found);
    }

    /**
     * A harness that is still enrolled but that the scan no longer finds (State Catalogue § {@code harness.choose}: "an
     * enrolled one no longer found as missing"), either because its profile left the set or because its executable is
     * gone: shown with the version and path its enrolment records, marked unsupported, so the operator sees it and can
     * remove it. Its display name is the scan's where the profile still lists one, otherwise its harness id, because the
     * enrolment keeps no display name.
     */
    private static HarnessListEntry missingRow(HarnessScan scan, HarnessEnrolment enrolment, boolean storeUnreadable) {
        return HarnessListEntry.builder()
                .harnessId(enrolment.harnessId())
                .displayName(displayNameOf(scan, enrolment))
                .version(enrolment.version())
                .executablePath(enrolment.executablePath().toString())
                .installHint("install the harness again, or remove its enrolment")
                .supported(false)
                .workerEligible(false)
                .defaultHarness(false)
                .usage(storeUnreadable ? HarnessUsageCoverage.unreadable() : coverage(enrolment.usageReport()))
                .build();
    }

    private static String displayNameOf(HarnessScan scan, HarnessEnrolment enrolment) {
        return scan.candidates().stream()
                .filter(candidate -> candidate.harnessId().equals(enrolment.harnessId()))
                .map(HarnessCandidate::displayName)
                .findFirst()
                .orElse(enrolment.harnessId());
    }

    /**
     * @param report the usage report an enrolment keeps
     * @return the coverage the list shows for that harness and version
     */
    public static HarnessUsageCoverage coverage(UsageReport report) {
        var figures = new LinkedHashMap<String, String>();
        report.figures().forEach((figure, status) -> figures.put(stageName(figure), status));
        var missing = ProfileStages.REPORT_FIGURES.stream()
                .filter(figure -> "missing".equals(figures.getOrDefault(figure, "missing")))
                .toList();
        var status = status(missing);
        var startContext = report.startContext().stream()
                .map(context -> new ProfileStages.StartContext(context.workerClass(), context.model(),
                        context.tokens()))
                .toList();
        return new HarnessUsageCoverage(status, status == HarnessUsageCoverage.Status.PARTIAL ? missing : List.of(),
                startContext);
    }

    private static HarnessUsageCoverage.Status status(List<String> missing) {
        if (missing.size() == ProfileStages.REPORT_FIGURES.size()) {
            return HarnessUsageCoverage.Status.NOT_REPORTED;
        }
        return missing.isEmpty() ? HarnessUsageCoverage.Status.COMPLETE : HarnessUsageCoverage.Status.PARTIAL;
    }

    private static String stageName(String reportFigure) {
        return switch (reportFigure) {
            case "input" -> "input_tokens";
            case "output" -> "output_tokens";
            default -> reportFigure;
        };
    }
}
