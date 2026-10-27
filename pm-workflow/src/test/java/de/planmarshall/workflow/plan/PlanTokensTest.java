/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.plan;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.AttemptEnd;
import de.planmarshall.core.store.HarnessUsage;
import de.planmarshall.core.store.Measurement;
import de.planmarshall.core.store.MetricsRow;
import de.planmarshall.core.store.UsageAggregate;
import de.planmarshall.workflow.epic.EpicTokens;

/**
 * The token facts of a running plan and of an epic (PM-WF-14, Model Work § Usage Reporting &amp; Spend Classes): the
 * total with its split and coverage derived on read, and no limit, budget line or projection.
 */
@DisplayName("Token facts of a plan and an epic")
class PlanTokensTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-27T10:00:00Z");

    private static HarnessUsage usage(long input, long output, long cacheRead, long cacheCreation, long reasoning) {
        return new HarnessUsage(Measurement.measured(input), Measurement.measured(output),
                Measurement.measured(cacheRead), Measurement.measured(cacheCreation), Measurement.measured(reasoning),
                Measurement.measured(1), Optional.empty(), Optional.empty());
    }

    private static MetricsRow row(String task, HarnessUsage usage) {
        return MetricsRow.builder().queueTaskId("qt-" + "0".repeat(15) + task).attempt(1).role("writer")
                .jobId("l-0123456789abcdef").generationId("l-0123456789abcdef").harness("opencode")
                .harnessVersion("1.2.3").model("deepseek-v4.1-flash").effort("high")
                .form(MetricsRow.Form.FRESH).outcome(MetricsRow.Outcome.SUBMITTED).attemptEnd(AttemptEnd.CLOSED)
                .causeClass(MetricsRow.CauseClass.BENIGN).spendClass(MetricsRow.SpendClass.SUBMITTED)
                .population(MetricsRow.Population.MODEL_JOB).usage(usage).durationMs(1_000L)
                .durationBasis(MetricsRow.DurationBasis.PROCESS).recordedAt(RECORDED_AT).build();
    }

    @Test
    @DisplayName("a plan's token facts carry the total, its split and its coverage")
    void planCarriesTotalSplitAndCoverage() {
        var tokens = PlanTokens.of("plan-a", List.of(row("1", usage(100, 40, 10, 5, 0)),
                row("2", usage(200, 60, 0, 0, 10))));

        var aggregate = tokens.aggregate(UsageAggregate.Population.MODEL_JOB).orElseThrow();
        assertAll(() -> assertEquals(OptionalLong.of(425), aggregate.totalTokens()),
                () -> assertEquals(OptionalLong.of(315), aggregate.tokensIn()),
                () -> assertEquals(OptionalLong.of(110), aggregate.tokensOut()),
                () -> assertEquals(2, aggregate.rows()), () -> assertFalse(aggregate.totalIsFloor()),
                () -> assertEquals("plan-a", tokens.planId()));
    }

    @Test
    @DisplayName("a plan with no metrics row has no figure")
    void emptyPlanHasNoFigure() {
        var tokens = PlanTokens.empty("plan-a");

        assertAll(() -> assertTrue(tokens.aggregate(UsageAggregate.Population.MODEL_JOB).isEmpty()),
                () -> assertTrue(tokens.byPopulation().isEmpty()));
    }

    @Test
    @DisplayName("an epic sums its plans and its own rows, and a floor in a part makes the whole a floor")
    void epicSumsPlansAndOwnRows() {
        var first = PlanTokens.of("plan-a", List.of(row("1", usage(100, 40, 0, 0, 0))));
        var second = PlanTokens.of("plan-b", List.of(row("2", usage(200, 60, 0, 0, 0))));
        var refused = MetricsRow.refused("qt-" + "0".repeat(15) + "3", "writer", null,
                MetricsRow.Outcome.INFEASIBLE, MetricsRow.SpendClass.UNMEASURED, MetricsRow.Population.MODEL_JOB,
                RECORDED_AT);
        var epic = EpicTokens.of("epic-1", List.of(refused), List.of(first, second));

        var aggregate = epic.aggregate(UsageAggregate.Population.MODEL_JOB).orElseThrow();
        assertAll(() -> assertEquals(OptionalLong.of(400), aggregate.totalTokens()),
                () -> assertEquals(3, aggregate.populationCount()), () -> assertEquals(1, aggregate.rowsUnmeasured()),
                () -> assertTrue(aggregate.totalIsFloor()), () -> assertEquals("epic-1", epic.epicId()));
    }

    @Test
    @DisplayName("neither the plan nor the epic representation carries a limit, a budget line or a projection")
    void carriesNoLimitBudgetOrProjection() {
        var forbidden = List.of("limit", "budget", "projection", "anchor", "comparison", "remaining");
        var components = List.of(Arrays.stream(PlanTokens.class.getRecordComponents())
                        .map(component -> component.getName()).toList(),
                Arrays.stream(EpicTokens.class.getRecordComponents()).map(component -> component.getName()).toList());

        assertTrue(components.stream().flatMap(List::stream)
                .noneMatch(name -> forbidden.stream().anyMatch(name::contains)));
    }
}
