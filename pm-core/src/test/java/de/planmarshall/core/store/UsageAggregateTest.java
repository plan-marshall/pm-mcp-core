/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;
import de.planmarshall.core.store.MetricsRow.CauseClass;
import de.planmarshall.core.store.MetricsRow.DurationBasis;
import de.planmarshall.core.store.MetricsRow.Form;
import de.planmarshall.core.store.MetricsRow.Outcome;
import de.planmarshall.core.store.MetricsRow.Phase;
import de.planmarshall.core.store.MetricsRow.Population;
import de.planmarshall.core.store.MetricsRow.SpendClass;
import de.planmarshall.core.store.UsageAggregate.ModelHarness;

/**
 * The aggregate of the metrics rows (PM-WF-14, PM-WORK-3, Store Schemas § Metrics Store, Aggregates): the total with its
 * split and the coverage a view shows, derived on read from the rows and never persisted.
 */
@DisplayName("Usage aggregate")
class UsageAggregateTest {

    private static final Instant RECORDED_AT = Instant.parse("2026-10-22T10:00:00Z");

    private static HarnessUsage usage(long input, long output, long cacheRead, long cacheCreation, long reasoning) {
        return new HarnessUsage(Measurement.measured(input), Measurement.measured(output),
                Measurement.measured(cacheRead), Measurement.measured(cacheCreation), Measurement.measured(reasoning),
                Measurement.measured(7), Optional.empty(), Optional.empty());
    }

    private static MetricsRow row(String id, HarnessUsage usage) {
        return row(id, usage, Population.MODEL_JOB, Phase.PHASE_5_EXECUTE, "writer", CauseClass.BENIGN,
                SpendClass.SUBMITTED);
    }

    private static MetricsRow row(String id, HarnessUsage usage, Population population, Phase phase, String role,
            CauseClass causeClass, SpendClass spendClass) {
        return MetricsRow.builder().queueTaskId(id).attempt(1).role(role).jobId("l-0123456789abcdef")
                .generationId("l-0123456789abcdef").harness("opencode").harnessVersion("1.2.3")
                .model("deepseek-v4.1-flash").effort("high").form(Form.FRESH).phase(phase).outcome(Outcome.SUBMITTED)
                .attemptEnd(AttemptEnd.CLOSED).causeClass(causeClass).spendClass(spendClass).population(population)
                .usage(usage).durationMs(1_000L).durationBasis(DurationBasis.PROCESS).recordedAt(RECORDED_AT).build();
    }

    private static MetricsRow refused(String id) {
        return MetricsRow.refused(id, "writer", Phase.PHASE_5_EXECUTE, Outcome.LOST, SpendClass.UNMEASURED,
                Population.MODEL_JOB, RECORDED_AT);
    }

    @Test
    @DisplayName("sums the measured rows into the total and its split")
    void sumsTotalAndSplit() {
        var aggregate = UsageAggregate.of(List.of(row("qt-0000000000000001", usage(1200, 340, 5000, 800, 150)),
                row("qt-0000000000000002", usage(100, 60, 0, 0, 0))));
        assertAll(() -> assertEquals(OptionalLong.of(7650), aggregate.totalTokens()),
                () -> assertEquals(OptionalLong.of(7100), aggregate.tokensIn()),
                () -> assertEquals(OptionalLong.of(550), aggregate.tokensOut()),
                () -> assertEquals(2, aggregate.rows()), () -> assertEquals(0, aggregate.rowsUnmeasured()),
                () -> assertEquals(2, aggregate.populationCount()), () -> assertFalse(aggregate.totalIsFloor()),
                () -> assertTrue(aggregate.excluded().isEmpty()),
                () -> assertEquals(Optional.of(UsageAggregate.Population.MODEL_JOB), aggregate.population()));
    }

    @Test
    @DisplayName("a not-applicable component counts 0 and the row stays measured")
    void notApplicableCountsZero() {
        var usage = new HarnessUsage(Measurement.measured(100), Measurement.measured(40), Measurement.measured(10),
                Measurement.notApplicable(), Measurement.notApplicable(), Measurement.measured(1), Optional.empty(),
                Optional.empty());
        var aggregate = UsageAggregate.of(List.of(row("qt-0000000000000001", usage)));
        assertAll(() -> assertEquals(OptionalLong.of(150), aggregate.totalTokens()),
                () -> assertEquals(OptionalLong.of(110), aggregate.tokensIn()),
                () -> assertEquals(OptionalLong.of(40), aggregate.tokensOut()),
                () -> assertEquals(1, aggregate.rows()), () -> assertFalse(aggregate.totalIsFloor()));
    }

    @Test
    @DisplayName("an unmeasured component leaves the total a floor and its split unmeasured")
    void unmeasuredMarksFloor() {
        var usage = new HarnessUsage(Measurement.measured(100), Measurement.measured(40), Measurement.measured(10),
                Measurement.measured(5), Measurement.unmeasured(), Measurement.measured(1), Optional.empty(),
                Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS));
        var aggregate = UsageAggregate.of(List.of(row("qt-0000000000000001", usage),
                row("qt-0000000000000002", usage(100, 40, 10, 5, 0))));
        assertAll(() -> assertEquals(OptionalLong.of(155), aggregate.totalTokens()),
                () -> assertEquals(OptionalLong.of(230), aggregate.tokensIn()),
                () -> assertEquals(OptionalLong.empty(), aggregate.tokensOut()),
                () -> assertEquals(1, aggregate.rowsUnmeasured()),
                () -> assertEquals(1, aggregate.unmeasuredReasons().get(UnmeasuredReason.NOT_REPORTED_BY_HARNESS)),
                () -> assertTrue(aggregate.totalIsFloor()));
    }

    @Test
    @DisplayName("an empty population has no total")
    void emptyPopulationHasNoTotal() {
        var aggregate = UsageAggregate.of(UsageAggregate.Population.MODEL_JOB, List.of());
        assertAll(() -> assertEquals(OptionalLong.empty(), aggregate.totalTokens()),
                () -> assertEquals(OptionalLong.empty(), aggregate.tokensIn()),
                () -> assertEquals(OptionalLong.empty(), aggregate.tokensOut()),
                () -> assertEquals(0, aggregate.populationCount()), () -> assertFalse(aggregate.totalIsFloor()));
    }

    @Test
    @DisplayName("a refused row counts unmeasured without a reason")
    void refusedRowCountsUnmeasured() {
        var aggregate = UsageAggregate.of(List.of(refused("qt-0000000000000001")));
        assertAll(() -> assertEquals(OptionalLong.of(0), aggregate.totalTokens()),
                () -> assertEquals(1, aggregate.rowsUnmeasured()), () -> assertTrue(aggregate.totalIsFloor()),
                () -> assertTrue(aggregate.unmeasuredReasons().isEmpty()));
    }

    @Test
    @DisplayName("refuses to mix the populations in one figure")
    void refusesMixedPopulations() {
        var fresh = row("qt-0000000000000001", usage(1, 1, 1, 1, 1));
        var warm = row("qt-0000000000000002", usage(1, 1, 1, 1, 1), Population.MODEL_TASK, Phase.PHASE_5_EXECUTE,
                "writer", CauseClass.BENIGN, SpendClass.SUBMITTED);
        var thrown = assertThrows(IllegalArgumentException.class, () -> UsageAggregate.of(List.of(fresh, warm)));
        assertTrue(thrown.getMessage().contains("one population"));
    }

    @Test
    @DisplayName("partitions a mixed store by population")
    void partitionsByPopulation() {
        var fresh = row("qt-0000000000000001", usage(1, 1, 0, 0, 0));
        var warm = row("qt-0000000000000002", usage(2, 2, 0, 0, 0), Population.MODEL_TASK, Phase.PHASE_5_EXECUTE,
                "writer", CauseClass.BENIGN, SpendClass.SUBMITTED);
        var byPopulation = UsageAggregate.byPopulation(List.of(fresh, warm));
        assertAll(() -> assertEquals(OptionalLong.of(2),
                        byPopulation.get(UsageAggregate.Population.MODEL_JOB).totalTokens()),
                () -> assertEquals(OptionalLong.of(4),
                        byPopulation.get(UsageAggregate.Population.MODEL_TASK).totalTokens()));
    }

    @Test
    @DisplayName("groups per phase, role, cause, spend class and model with harness")
    void groupsByDimension() {
        var execute = row("qt-0000000000000001", usage(1, 1, 0, 0, 0));
        var finalize = row("qt-0000000000000002", usage(2, 2, 0, 0, 0), Population.MODEL_JOB,
                Phase.PHASE_6_FINALIZE, "reviewer", CauseClass.RETURNED_WITH_FINDINGS, SpendClass.FINDINGS_ONLY);
        assertAll(
                () -> assertEquals(2, UsageAggregate.byPhase(List.of(execute, finalize)).get(Phase.PHASE_5_EXECUTE)
                        .totalTokens().getAsLong()),
                () -> assertEquals(2, UsageAggregate.byRole(List.of(execute, finalize)).size()),
                () -> assertEquals(OptionalLong.of(2),
                        UsageAggregate.byRole(List.of(execute, finalize)).get("writer").totalTokens()),
                () -> assertEquals(OptionalLong.of(2), UsageAggregate.byCauseClass(List.of(execute, finalize))
                        .get(CauseClass.BENIGN).totalTokens()),
                () -> assertEquals(OptionalLong.of(4), UsageAggregate.bySpendClass(List.of(execute, finalize))
                        .get(SpendClass.FINDINGS_ONLY).totalTokens()),
                () -> assertEquals(OptionalLong.of(6), UsageAggregate.byModelAndHarness(List.of(execute, finalize))
                        .get(new ModelHarness("deepseek-v4.1-flash", "opencode", "1.2.3")).totalTokens()));
    }

    @Test
    @DisplayName("sums two aggregates of one population and keeps the floor")
    void sumsAggregates() {
        var first = UsageAggregate.of(List.of(row("qt-0000000000000001", usage(10, 5, 0, 0, 0))));
        var second = UsageAggregate.of(List.of(row("qt-0000000000000002", usage(20, 10, 0, 0, 0))));
        var sum = first.plus(second);
        assertAll(() -> assertEquals(OptionalLong.of(45), sum.totalTokens()),
                () -> assertEquals(OptionalLong.of(30), sum.tokensIn()),
                () -> assertEquals(OptionalLong.of(15), sum.tokensOut()), () -> assertEquals(2, sum.populationCount()),
                () -> assertFalse(sum.totalIsFloor()));
    }

    @Test
    @DisplayName("refuses to sum two populations")
    void refusesSumOfPopulations() {
        var fresh = UsageAggregate.of(List.of(row("qt-0000000000000001", usage(1, 1, 0, 0, 0))));
        var warm = UsageAggregate.of(UsageAggregate.Population.MODEL_TASK,
                List.of(usage(1, 1, 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> fresh.plus(warm));
    }
}
