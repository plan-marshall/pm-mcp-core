/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.analysis;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.findings.FindingScope;

/**
 * The builders of {@link AnalysisResult} build the values the canonical constructors build, and a missing required
 * component is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Analysis result builders")
class AnalysisResultBuilderContractTest {

    private static final String COMMIT_SHA = "0000000000000000000000000000000000000000";

    @Test
    @DisplayName("the result builder builds the constructor's value and refuses alike")
    void shouldBuildResultLikeTheConstructorAndRefuseAlike() {
        var provider = "p" + Generators.letterStrings(3, 10).next().toLowerCase(Locale.ROOT);
        var target = FindingScope.commit(COMMIT_SHA);

        var canonical = new AnalysisResult(AnalysisResult.FORMAT_VERSION, provider, target, AnalysisStatus.RUNNING,
                null, AnalysisResult.CountStatus.MEASURED, 0L, 0L, false, null, Instant.EPOCH);
        var built = AnalysisResult.builder().formatVersion(canonical.formatVersion())
                .provider(canonical.provider()).target(canonical.target()).status(canonical.status())
                .gate(canonical.gate()).countStatus(canonical.countStatus())
                .newCodeIssueCount(canonical.newCodeIssueCount()).issuesFetched(canonical.issuesFetched())
                .workPerformed(canonical.workPerformed()).analysisRef(canonical.analysisRef())
                .observedAt(canonical.observedAt()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new AnalysisResult(AnalysisResult.FORMAT_VERSION, null, target,
                                    AnalysisStatus.RUNNING, null, AnalysisResult.CountStatus.MEASURED, 0L, 0L,
                                    false, null, Instant.EPOCH),
                            "the constructor refuses a null provider");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> AnalysisResult.builder().formatVersion(AnalysisResult.FORMAT_VERSION)
                                    .target(target).status(AnalysisStatus.RUNNING)
                                    .countStatus(AnalysisResult.CountStatus.MEASURED).issuesFetched(0L)
                                    .observedAt(Instant.EPOCH).build(),
                            "the builder refuses a missing provider");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent provider with the same message");
                });
    }

    @Test
    @DisplayName("the condition builder builds the constructor's value and refuses alike")
    void shouldBuildConditionLikeTheConstructorAndRefuseAlike() {
        var metric = Generators.letterStrings(1, 10).next();
        var threshold = Generators.letterStrings(1, 10).next();

        var canonical = new AnalysisResult.Condition(metric, AnalysisResult.Comparator.LT, threshold, null,
                AnalysisResult.GateResult.PASSED);
        var built = AnalysisResult.Condition.builder().metric(canonical.metric())
                .comparator(canonical.comparator()).threshold(canonical.threshold()).actual(canonical.actual())
                .result(canonical.result()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new AnalysisResult.Condition(null, AnalysisResult.Comparator.LT, threshold, null,
                                    AnalysisResult.GateResult.PASSED),
                            "the constructor refuses a null metric");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> AnalysisResult.Condition.builder().comparator(AnalysisResult.Comparator.LT)
                                    .threshold(threshold).result(AnalysisResult.GateResult.PASSED).build(),
                            "the builder refuses a missing metric");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent metric with the same message");
                });
    }
}
