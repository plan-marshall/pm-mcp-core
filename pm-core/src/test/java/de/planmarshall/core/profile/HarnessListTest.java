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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.HarnessList.Key;
import de.planmarshall.core.profile.ProfileStages.StartContext;
import de.planmarshall.core.profile.ProfileStages.UsageStatus;

/**
 * The harness list and the usage coverage it shows (PM-CLIENT-5, Harness Profiles § Harness Scan, § Tokens, Not Money;
 * State Catalogue § {@code harness.choose}): every harness with its path and version, the coverage its verification
 * recorded for that version, the start context the usage stage measured, and {@code harness_store_unreadable} instead of
 * an empty map where the enrolment store could not be read.
 */
@EnableGeneratorController
@DisplayName("Harness list and usage coverage")
class HarnessListTest {

    private static final String AAA_ID = "h-aaa" + Generators.letterStrings(2, 4).next().toLowerCase(Locale.ROOT);
    private static final String BBB_ID = "h-bbb" + Generators.letterStrings(2, 4).next().toLowerCase(Locale.ROOT);
    private static final String HINT = "install " + Generators.letterStrings(4, 8).next();
    private static final String VERSION = Generators.integers(1, 9).next() + ".0.0";
    private static final String OTHER_VERSION = VERSION + "-other";
    private static final String MODEL = Generators.letterStrings(5, 12).next();
    private static final OptionalLong START_TOKENS = OptionalLong.of(Generators.longs(1_000, 9_000).next());

    @Test
    @DisplayName("coverage is complete when every figure is reported or not applicable")
    void coverageCompleteForEveryReportedFigure() {
        var coverage = HarnessUsageCoverage.of(usage(UsageStatus.REPORTED));
        var status = coverage.status();
        var namesMissing = coverage.namesMissingFigures();

        assertAll(
                () -> assertEquals(HarnessUsageCoverage.Status.COMPLETE, status,
                        "Complete coverage for every reported figure"),
                () -> assertFalse(namesMissing, "Nothing is missing in complete coverage"));
    }

    @Test
    @DisplayName("coverage is partial and names the figure the harness does not report")
    void coveragePartialNamesTheMissingFigure() {
        var figures = allFigures(UsageStatus.REPORTED);
        var missing = ProfileStages.REPORT_FIGURES.get(2);
        figures.put(missing, UsageStatus.MISSING);
        var coverage = HarnessUsageCoverage.of(new ProfileStages.UsageResult(figures, Map.of()));
        var status = coverage.status();
        var missingFigures = coverage.missingFigures();

        assertAll(
                () -> assertEquals(HarnessUsageCoverage.Status.PARTIAL, status,
                        "Partial coverage when one report figure is missing"),
                () -> assertEquals(List.of(missing), missingFigures, "The missing figure is named"));
    }

    @Test
    @DisplayName("coverage is not_reported when the harness reports no figure")
    void coverageNotReportedWithoutAnyFigure() {
        var coverage = HarnessUsageCoverage.of(usage(UsageStatus.MISSING));
        var status = coverage.status();

        assertEquals(HarnessUsageCoverage.Status.NOT_REPORTED, status, "No figure is reported by the harness");
    }

    @Test
    @DisplayName("the start context the usage stage measured travels beside the coverage")
    void startContextTravelsBesideCoverage() {
        var context = new StartContext("writer", MODEL, START_TOKENS);
        var coverage = HarnessUsageCoverage.of(
                new ProfileStages.UsageResult(allFigures(UsageStatus.REPORTED), Map.of(), List.of(context)));
        var startContext = coverage.startContext();

        assertEquals(List.of(context), startContext, "The start context is carried");
    }

    @Test
    @DisplayName("the missing figures are report figures named in schema order")
    void missingFiguresAreReportFiguresInSchemaOrder() {
        var notAFigure = List.of("cost_usd");
        var outOfOrder = List.of(ProfileStages.REPORT_FIGURES.get(3), ProfileStages.REPORT_FIGURES.get(1));

        assertAll(
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new HarnessUsageCoverage(HarnessUsageCoverage.Status.PARTIAL, notAFigure, List.of()),
                        "A figure outside the usage report is refused"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new HarnessUsageCoverage(HarnessUsageCoverage.Status.PARTIAL, outOfOrder, List.of()),
                        "The missing figures are in schema order"));
    }

    @Test
    @DisplayName("an unreadable store is shown instead of an empty map")
    void unreadableStoreShownInsteadOfEmptyMap() {
        var coverage = HarnessUsageCoverage.unreadable();
        var wireName = coverage.status().wireName();
        var unreadableStore = coverage.unreadableStore();
        var missingFigures = coverage.missingFigures();
        var startContext = coverage.startContext();

        assertAll(
                () -> assertEquals("harness_store_unreadable", wireName,
                        "The refusal is the status, not an empty map"),
                () -> assertTrue(unreadableStore, "The coverage reports the unreadable store"),
                () -> assertTrue(missingFigures.isEmpty(), "A refusal names no missing figure"),
                () -> assertTrue(startContext.isEmpty(), "A refusal carries no start context"));
    }

    @Test
    @DisplayName("the list follows the harness id order and marks the default")
    void listFollowsHarnessIdOrderAndMarksTheDefault() {
        var list = HarnessList.of(scan(), Map.of(), false);
        var first = list.entries().getFirst();
        var second = list.entries().get(1);
        var ids = list.harnessIds();
        var preselected = list.preselected().stream().map(HarnessListEntry::harnessId).toList();
        var path = first.executablePath();
        var hint = first.installHint();
        var firstDefault = first.defaultHarness();
        var secondDefault = second.defaultHarness();

        assertAll(
                () -> assertEquals(List.of(AAA_ID, BBB_ID), ids, "The list follows the id order"),
                () -> assertTrue(firstDefault, "The first preselected is the default"),
                () -> assertFalse(secondDefault, "The other entry is not the default"),
                () -> assertEquals("/opt/" + AAA_ID + "/bin/run", path,
                        "A found row carries the path the scan found"),
                () -> assertEquals(HINT, hint, "A row carries the install hint"),
                () -> assertEquals(List.of(AAA_ID, BBB_ID), preselected,
                        "Every supported eligible row is preselected"));
    }

    @Test
    @DisplayName("a harness no verification recorded is not reported")
    void unrecordedHarnessIsNotReported() {
        var recorded = Map.of(new Key(AAA_ID, VERSION), HarnessUsageCoverage.of(usage(UsageStatus.REPORTED)));
        var list = HarnessList.of(scan(), recorded, false);
        var recordedStatus = list.entries().getFirst().usage().status();
        var unrecordedStatus = list.entries().get(1).usage().status();

        assertAll(
                () -> assertEquals(HarnessUsageCoverage.Status.COMPLETE, recordedStatus,
                        "The recorded harness shows its coverage"),
                () -> assertEquals(HarnessUsageCoverage.Status.NOT_REPORTED, unrecordedStatus,
                        "A harness without a record is not reported"));
    }

    @Test
    @DisplayName("a coverage recorded for another version is not shown")
    void coverageOfAnotherVersionIsNotShown() {
        var recorded = Map.of(new Key(AAA_ID, OTHER_VERSION),
                HarnessUsageCoverage.of(usage(UsageStatus.REPORTED)));
        var list = HarnessList.of(scan(), recorded, false);
        var status = list.entries().getFirst().usage().status();

        assertEquals(HarnessUsageCoverage.Status.NOT_REPORTED, status, "Another version's coverage is not shown");
    }

    @Test
    @DisplayName("an unreadable store shows harness_store_unreadable at every entry")
    void unreadableStoreShownAtEveryEntry() {
        var recorded = Map.of(new Key(AAA_ID, VERSION), HarnessUsageCoverage.of(usage(UsageStatus.REPORTED)));
        var list = HarnessList.of(scan(), recorded, true);
        var everyRowUnreadable = list.entries().stream().allMatch(entry -> entry.usage().unreadableStore());

        assertTrue(everyRowUnreadable, "Every row shows the store refusal");
    }

    private static HarnessScan scan() {
        return new HarnessScan(List.of(candidate(BBB_ID), candidate(AAA_ID)));
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

    private static ProfileStages.UsageResult usage(UsageStatus status) {
        return new ProfileStages.UsageResult(allFigures(status), Map.of());
    }

    private static Map<String, UsageStatus> allFigures(UsageStatus status) {
        var figures = new LinkedHashMap<String, UsageStatus>();
        ProfileStages.REPORT_FIGURES.forEach(figure -> figures.put(figure, status));
        return figures;
    }
}
