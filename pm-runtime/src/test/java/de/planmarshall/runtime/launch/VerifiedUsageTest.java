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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.profile.ProfileStages;
import de.planmarshall.core.profile.ProfileStages.StartContext;
import de.planmarshall.core.profile.ProfileStages.UsageResult;
import de.planmarshall.core.profile.ProfileStages.UsageStatus;
import de.planmarshall.runtime.launch.HarnessEnrolment.UsageReport;

/**
 * The seam from the profile verification's stage {@code usage} to the enrolment's usage report (PM-CLIENT-5, Harness
 * Profiles § Tokens, Not Money): the mapped figure names, the cost left out, the start context carried, and the repeated
 * evaluation a new harness's or version's figures are trusted after.
 */
@EnableGeneratorController
@DisplayName("Verified usage handed to the enrolment")
class VerifiedUsageTest {

    private static final String MODEL = Generators.letterStrings(5, 12).next();
    private static final OptionalLong START_TOKENS = OptionalLong.of(Generators.longs(1_000, 9_000).next());

    @Test
    @DisplayName("the install path maps the stage figures and carries the start context")
    void installMapsFiguresAndStartContext() {
        var context = new StartContext("writer", MODEL, START_TOKENS);
        var report = VerifiedUsage.install(List.of(withStartContext(context), result(UsageStatus.REPORTED),
                result(UsageStatus.REPORTED)));

        assertAll(
                () -> assertEquals("reported", report.figures().get("input"), "the input figure is reported"),
                () -> assertEquals("reported", report.figures().get("output"), "the output figure is reported"),
                () -> assertFalse(report.figures().containsKey("cost_usd"), "the cost is never part of the report"),
                () -> assertEquals(List.of(new UsageReport.StartContext("writer", MODEL, START_TOKENS)),
                        report.startContext(), "the start context crosses the seam"),
                () -> assertEquals(HarnessEnrolment.UsageSource.VERIFIED, report.source(),
                        "a verified report names its source"));
    }

    @Test
    @DisplayName("a single evaluation is not trusted yet")
    void singleEvaluationIsNotTrusted() {
        var single = List.of(result(UsageStatus.REPORTED));

        assertAll(
                () -> assertFalse(VerifiedUsage.trusted(single), "One run trusts no figure"),
                () -> assertEquals(UsageTrustRefusal.NOT_REPEATED,
                        assertThrows(UsageTrustRefusal.class, () -> VerifiedUsage.install(single),
                                "One run installs no report").code(),
                        "the refusal names the missing repetition"));
    }

    @Test
    @DisplayName("two agreeing evaluations make the figures trusted")
    void twoAgreeingEvaluationsAreTrusted() {
        var evaluations = List.of(result(UsageStatus.REPORTED), result(UsageStatus.REPORTED));

        assertAll(
                () -> assertTrue(VerifiedUsage.trusted(evaluations), "Two agreeing runs trust the figures"),
                () -> assertEquals("reported", VerifiedUsage.install(evaluations).figures().get("input"),
                        "The trusted report carries the figure"));
    }

    @Test
    @DisplayName("two disagreeing evaluations leave the figures untrusted")
    void twoDisagreeingEvaluationsAreRefused() {
        var evaluations = List.of(result(UsageStatus.REPORTED), result(UsageStatus.MISSING));

        assertAll(
                () -> assertFalse(VerifiedUsage.trusted(evaluations), "Disagreeing runs trust no figure"),
                () -> assertEquals(UsageTrustRefusal.DISAGREES,
                        assertThrows(UsageTrustRefusal.class, () -> VerifiedUsage.install(evaluations),
                                "Disagreeing runs install no report").code(),
                        "the refusal names the disagreement"));
    }

    private static UsageResult result(UsageStatus status) {
        var figures = new LinkedHashMap<String, UsageStatus>();
        ProfileStages.USAGE_FIGURES.forEach(figure -> figures.put(figure, status));
        var parsed = new LinkedHashMap<String, BigDecimal>();
        parsed.put("input_tokens", BigDecimal.valueOf(Generators.longs(1, 1_000).next()));
        return new UsageResult(figures, parsed);
    }

    private static UsageResult withStartContext(StartContext context) {
        return new UsageResult(result(UsageStatus.REPORTED).figures(), Map.of(), List.of(context));
    }
}
