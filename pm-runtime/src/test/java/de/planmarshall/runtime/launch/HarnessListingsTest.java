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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.HarnessCandidate;
import de.planmarshall.core.profile.HarnessList;
import de.planmarshall.core.profile.HarnessListEntry;
import de.planmarshall.core.profile.HarnessScan;
import de.planmarshall.core.profile.HarnessUsageCoverage;
import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.runtime.launch.HarnessEnrolment.UsageReport;
import de.planmarshall.runtime.launch.HarnessEnrolment.UsageSource;

/**
 * The bridge from the enrolment store to the harness list (PM-CLIENT-5, Harness Profiles § Tokens, Not Money): a harness
 * the scan found shows the coverage its enrolment recorded for that version, the store's figure names are mapped to the
 * profile schema's, the start context crosses over, and a coverage of another version is never shown.
 */
@EnableGeneratorController
@DisplayName("Harness list from the enrolments")
class HarnessListingsTest {

    private static final String AAA_ID = "h-aaa" + Generators.letterStrings(2, 4).next().toLowerCase(Locale.ROOT);
    private static final String BBB_ID = "h-bbb" + Generators.letterStrings(2, 4).next().toLowerCase(Locale.ROOT);
    private static final String HINT = "install " + Generators.letterStrings(4, 8).next();
    private static final String VERSION = Generators.integers(1, 9).next() + ".0.0";
    private static final String OTHER_VERSION = VERSION + "-other";
    private static final String ORPHAN_ID = "h-zzz" + Generators.letterStrings(2, 4).next().toLowerCase(Locale.ROOT);
    private static final String MODEL = Generators.letterStrings(5, 12).next();
    private static final OptionalLong START_TOKENS = OptionalLong.of(Generators.longs(1_000, 9_000).next());

    @Test
    @DisplayName("the enrolment's report becomes the row's coverage")
    void enrolmentReportBecomesTheRowsCoverage() {
        var list = HarnessListings.of(scan(), Map.of(AAA_ID, enrolment(VERSION, complete())), false);

        assertAll(
                () -> assertEquals(HarnessUsageCoverage.Status.COMPLETE, row(list, AAA_ID).usage().status(),
                        "The verified harness shows its recorded coverage"),
                () -> assertEquals(List.of(new ProfileStages.StartContext("writer", MODEL, START_TOKENS)),
                        row(list, AAA_ID).usage().startContext(), "The start context crosses over"),
                () -> assertEquals(HarnessUsageCoverage.Status.NOT_REPORTED, row(list, BBB_ID).usage().status(),
                        "A harness without an enrolment is not reported"));
    }

    @Test
    @DisplayName("a partial report names the missing figure in the schema's vocabulary")
    void partialReportNamesTheMissingFigure() {
        var list = HarnessListings.of(scan(), Map.of(AAA_ID, enrolment(VERSION, partial("input"))), false);

        assertEquals(List.of("input_tokens"), row(list, AAA_ID).usage().missingFigures(),
                "The store's figure name is mapped back to the profile's");
    }

    @Test
    @DisplayName("a coverage recorded for another version is not shown")
    void coverageOfAnotherVersionIsNotShown() {
        var list = HarnessListings.of(scan(), Map.of(AAA_ID, enrolment(OTHER_VERSION, complete())), false);

        assertEquals(HarnessUsageCoverage.Status.NOT_REPORTED, row(list, AAA_ID).usage().status(),
                "The other version's coverage is not shown");
    }

    @Test
    @DisplayName("an enrolled harness the scan did not find is shown as missing")
    void enrolledHarnessMissingFromTheScanIsShown() {
        var list = HarnessListings.of(scan(), Map.of(ORPHAN_ID, orphan()), false);
        var orphan = list.entries().stream().filter(entry -> entry.harnessId().equals(ORPHAN_ID)).findFirst();

        assertAll(
                () -> assertTrue(orphan.isPresent(), "The enrolled harness stays in the list"),
                () -> assertFalse(orphan.orElseThrow().selectable(), "A harness nothing found is never selectable"),
                () -> assertEquals(VERSION, orphan.orElseThrow().version(), "The row keeps the enrolled version"));
    }

    @Test
    @DisplayName("an unreadable store shows the refusal at every row")
    void unreadableStoreShowsTheRefusalAtEveryRow() {
        var list = HarnessListings.of(scan(), Map.of(AAA_ID, enrolment(VERSION, complete())), true);

        assertTrue(list.entries().stream().allMatch(entry -> entry.usage().unreadableStore()),
                "Every row shows the store refusal");
    }

    private static HarnessListEntry row(HarnessList list, String harnessId) {
        return list.entries().stream().filter(entry -> entry.harnessId().equals(harnessId)).findFirst().orElseThrow();
    }

    private static HarnessScan scan() {
        return new HarnessScan(List.of(candidate(AAA_ID), candidate(BBB_ID)));
    }

    private static HarnessCandidate candidate(String harnessId) {
        return HarnessCandidate.builder()
                .harnessId(harnessId)
                .displayName("Harness " + harnessId)
                .found(true)
                .version(VERSION)
                .executablePath("/opt/" + harnessId + "/bin/run")
                .supported(true)
                .workerEligible(true)
                .installHint(HINT)
                .build();
    }

    private static HarnessEnrolment enrolment(String version, UsageReport report) {
        return enrolment(AAA_ID, version, report);
    }

    private static HarnessEnrolment orphan() {
        return enrolment(ORPHAN_ID, VERSION, complete());
    }

    private static HarnessEnrolment enrolment(String harnessId, String version, UsageReport report) {
        var now = Instant.parse("2026-11-13T10:00:00Z");
        return HarnessEnrolment.builder()
                .clientId(harnessId)
                .executablePath(Path.of("/opt/" + harnessId + "/bin/run"))
                .sha256("0".repeat(64))
                .version(version)
                .enrolledAt(now)
                .updatedAt(now)
                .usageReport(report)
                .events(Map.of())
                .weights(Map.of())
                .build();
    }

    private static UsageReport complete() {
        var figures = new LinkedHashMap<String, String>();
        List.of("input", "output", "cache_read", "cache_creation", "reasoning", "tool_uses")
                .forEach(figure -> figures.put(figure, "reported"));
        return UsageReport.of(UsageSource.VERIFIED, figures,
                List.of(new UsageReport.StartContext("writer", MODEL, START_TOKENS)));
    }

    private static UsageReport partial(String missing) {
        var figures = new LinkedHashMap<String, String>();
        List.of("input", "output", "cache_read", "cache_creation", "reasoning", "tool_uses")
                .forEach(figure -> figures.put(figure, figure.equals(missing) ? "missing" : "reported"));
        return UsageReport.of(UsageSource.VERIFIED, figures, List.of());
    }
}
