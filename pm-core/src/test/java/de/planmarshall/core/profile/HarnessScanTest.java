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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import de.planmarshall.core.profile.HarnessProbe.Outcome;

/**
 * The harness scan and the step it leads to (PM-CLIENT-5, Harness Profiles § Harness Scan; State Catalogue
 * § {@code harness.choose}, § {@code harness.probe}, § {@code harness.blocked} and § {@code harness.none}): the list
 * follows the harness ids, every supported worker-eligible harness is preselected with the first of them the default, a
 * run with no usable harness left rests in {@code harness.none}, and the last chosen harness cannot be left out.
 */
@EnableGeneratorController
@DisplayName("Harness scan of the machine onboarding")
class HarnessScanTest {

    private static final String AAA_ID = "h-aaa" + Generators.letterStrings(2, 4).next().toLowerCase(Locale.ROOT);
    private static final String BBB_ID = "h-bbb" + Generators.letterStrings(2, 4).next().toLowerCase(Locale.ROOT);
    private static final String HINT = "install " + Generators.letterStrings(4, 8).next();
    private static final String VERSION = Generators.integers(1, 9).next() + ".0.0";

    @Test
    @DisplayName("the candidates are listed in the order of their harness ids")
    void candidatesListedByHarnessId() {
        var scan = new HarnessScan(List.of(found(BBB_ID), found(AAA_ID)));

        assertEquals(List.of(AAA_ID, BBB_ID), scan.harnessIds(), "The candidates follow the harness id order");
    }

    @Test
    @DisplayName("only found supported worker-eligible harnesses are preselected")
    void onlySelectableCandidatesPreselected() {
        var scan = new HarnessScan(List.of(found(BBB_ID), candidate("h-zz", true, VERSION, false, false),
                candidate("h-aa", true, VERSION, true, false), candidate("h-mm", false, null, false, false)));

        assertEquals(List.of(BBB_ID), scan.preselected().stream().map(HarnessCandidate::harnessId).toList(),
                "Only one candidate may be selected");
    }

    @Test
    @DisplayName("the default is the first preselected harness in id order")
    void defaultIsFirstPreselected() {
        var scan = new HarnessScan(List.of(found(BBB_ID), found(AAA_ID)));

        assertEquals(AAA_ID, scan.defaultHarness().orElseThrow(), "The default follows the id order");
    }

    @Test
    @DisplayName("a scan that found nothing continues at harness.none")
    void scanWithoutFoundHarnessGoesToNone() {
        var scan = new HarnessScan(List.of(candidate(AAA_ID, false, null, false, false)));

        assertAll(
                () -> assertEquals(HarnessScan.Outcome.NONE_FOUND, scan.outcome(),
                        "A scan without a found harness is none_found"),
                () -> assertTrue(scan.none().isPresent(), "The step renders the harness.none wait"),
                () -> assertTrue(scan.defaultHarness().isEmpty(), "No harness becomes the default"));
    }

    @Test
    @DisplayName("a scan that found a harness continues at harness.choose")
    void scanWithFoundHarnessGoesToChoose() {
        var scan = new HarnessScan(List.of(found(AAA_ID)));

        assertAll(
                () -> assertEquals(HarnessScan.Outcome.FOUND, scan.outcome(), "A found harness is the found outcome"),
                () -> assertFalse(scan.none().isPresent(), "No harness.none wait is rendered"));
    }

    @Test
    @DisplayName("harness.none names how to install each known harness")
    void noneNamesEveryInstallHint() {
        var scan = new HarnessScan(List.of(candidate(BBB_ID, false, null, false, false),
                candidate(AAA_ID, false, null, false, false)));
        var none = scan.none().orElseThrow();

        assertAll(
                () -> assertEquals(List.of("recheck", "add"), none.actions(), "The wait offers recheck and add"),
                () -> assertEquals(AAA_ID, none.known().get(0).harnessId(), "The hints follow the id order"),
                () -> assertEquals(HINT, none.known().get(0).installHint(), "The hint is the profile's own"));
    }

    @ParameterizedTest(name = "probe of {0} chosen with {1} failed answers {2}")
    @CsvSource({"3,0,ALL_PASSED", "3,1,ONE_FAILED", "3,3,ALL_FAILED", "1,0,ALL_PASSED", "1,1,ALL_FAILED"})
    @DisplayName("the probe transition follows the chosen and failed harnesses")
    void probeTransitionFollowsTheTwoSets(int chosen, int failed, Outcome expected) {
        var probe = new HarnessProbe(ids(chosen), ids(failed));

        assertEquals(expected, probe.outcome(), "The transition counts the failed harnesses");
    }

    @Test
    @DisplayName("a probe that every chosen harness failed rests in harness.none")
    void probeWithEveryHarnessFailingRestsInNone() {
        var allFailed = new HarnessProbe(List.of(AAA_ID, BBB_ID), List.of(AAA_ID, BBB_ID));
        var oneFailed = new HarnessProbe(List.of(AAA_ID, BBB_ID), List.of(BBB_ID));

        assertAll(
                () -> assertTrue(allFailed.restsInNone(), "No usable harness is left after the probe"),
                () -> assertEquals("harness.none", allFailed.outcome().nextState(), "The step rests in harness.none"),
                () -> assertEquals("harness.blocked", oneFailed.outcome().nextState(), "A partly failed probe blocks"),
                () -> assertEquals(List.of(AAA_ID, BBB_ID),
                        allFailed.none(List.of(found(AAA_ID), found(BBB_ID))).orElseThrow().known().stream()
                                .map(HarnessNone.InstallHint::harnessId).toList(),
                        "The failed probe's wait names the install hints"),
                () -> assertTrue(oneFailed.none(List.of(found(AAA_ID), found(BBB_ID))).isEmpty(),
                        "A usable harness leaves no harness.none wait"));
    }

    @Test
    @DisplayName("the probe compares sets, so a duplicated id cannot flip the transition")
    void probeComparesSetsNotListSizes() {
        assertThrows(IllegalArgumentException.class,
                () -> new HarnessProbe(List.of(AAA_ID, BBB_ID), List.of(AAA_ID, AAA_ID)),
                "A harness named twice among the failed is refused");
    }

    @Test
    @DisplayName("the last chosen harness cannot be left out")
    void lastChosenHarnessCannotBeLeftOut() {
        var probe = new HarnessProbe(List.of(AAA_ID, BBB_ID), List.of(BBB_ID));
        var single = new HarnessProbe(List.of(AAA_ID), List.of(AAA_ID));

        assertAll(
                () -> assertTrue(probe.canLeaveOut(BBB_ID), "A failed harness beside a passed one may be left out"),
                () -> assertFalse(probe.canLeaveOut(AAA_ID), "The harness that passed is never left out"),
                () -> assertFalse(single.canLeaveOut(AAA_ID), "The last chosen harness cannot be left out"),
                () -> assertFalse(probe.canLeaveOut("h-other"), "A harness that was not chosen cannot be left out"));
    }

    @Test
    @DisplayName("the harness.none wait refuses a failed harness without a profile")
    void noneRefusesAFailedHarnessWithoutProfile() {
        var probe = new HarnessProbe(List.of(AAA_ID, BBB_ID), List.of(AAA_ID, BBB_ID));

        assertThrows(IllegalArgumentException.class, () -> probe.none(List.of(found(AAA_ID))),
                "A failed harness's install hint must be nameable");
    }

    @Test
    @DisplayName("a profile that is offered for workers must be found and supported")
    void candidateRefusesWorkerEligibilityWithoutSupport() {
        assertAll(
                () -> assertThrows(IllegalArgumentException.class, () -> candidate(AAA_ID, false, null, true, true),
                        "An unfound harness cannot serve workers"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> candidate(AAA_ID, true, VERSION, false, true),
                        "An unsupported harness cannot serve workers"),
                () -> assertThrows(IllegalArgumentException.class, () -> candidate(AAA_ID, false, VERSION, false, false),
                        "A harness that was not found has no version"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> HarnessCandidate.builder()
                                .harnessId(AAA_ID)
                                .displayName("Harness")
                                .found(true)
                                .version(VERSION)
                                .executablePath(null)
                                .supported(true)
                                .workerEligible(true)
                                .installHint(HINT)
                                .build(),
                        "A found harness names the path it was found at"));
    }

    @Test
    @DisplayName("two profiles of one harness id are refused")
    void scanRefusesDuplicateHarnessIds() {
        assertThrows(IllegalArgumentException.class, () -> new HarnessScan(List.of(found(AAA_ID), found(AAA_ID))),
                "Two candidates of one harness id are refused");
    }

    private static HarnessCandidate found(String harnessId) {
        return candidate(harnessId, true, VERSION, true, true);
    }

    private static HarnessCandidate candidate(String harnessId, boolean found, String version, boolean supported,
            boolean workerEligible) {
        return HarnessCandidate.builder()
                .harnessId(harnessId)
                .displayName("Harness " + harnessId)
                .found(found)
                .version(version)
                .executablePath(found ? "/opt/" + harnessId + "/bin/run" : null)
                .supported(supported)
                .workerEligible(workerEligible)
                .installHint(HINT)
                .build();
    }

    private static List<String> ids(int count) {
        var ids = new ArrayList<String>();
        for (var index = 0; index < count; index++) {
            ids.add("h-" + index);
        }
        return ids;
    }
}
