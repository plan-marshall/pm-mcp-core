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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * split and the coverage a view shows, derived on read from the rows and never persisted. The usage figures, the ids
 * within their grammar ({@code qt-} and {@code l-} with 16 hex digits), the role, model, harness and instant are
 * generated; the expected figures are computed from them: the total is the five token components, the input side the
 * input and both cache components, the output side the output and reasoning.
 */
@EnableGeneratorController
@DisplayName("Usage aggregate")
class UsageAggregateTest {

    private Instant recordedAt;
    private String role;
    private String jobId;
    private String harness;
    private String harnessVersion;
    private String model;
    private String effort;
    private long queueTaskNumber;

    @BeforeEach
    void createRowData() {
        recordedAt = Instant.ofEpochSecond(Generators.longs(1_700_000_000L, 1_800_000_000L).next());
        role = lowerName();
        jobId = "l-" + HexFormat.of().toHexDigits(Generators.longs(0L, Long.MAX_VALUE).next());
        harness = lowerName();
        harnessVersion = Generators.integers(0, 9).next() + "." + Generators.integers(0, 99).next() + "."
                + Generators.integers(0, 99).next();
        model = lowerName() + "-" + lowerName();
        effort = lowerName();
        queueTaskNumber = Generators.longs(0L, Long.MAX_VALUE - 10).next();
    }

    private static String lowerName() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static long figure() {
        return Generators.longs(1L, 10_000L).next();
    }

    /** @return the n-th distinct queue task id of this test, in the grammar {@code qt-} and 16 hex digits */
    private String taskId(int n) {
        return "qt-" + HexFormat.of().toHexDigits(queueTaskNumber + n);
    }

    private static HarnessUsage usage(long input, long output, long cacheRead, long cacheCreation, long reasoning) {
        return new HarnessUsage(Measurement.measured(input), Measurement.measured(output),
                Measurement.measured(cacheRead), Measurement.measured(cacheCreation), Measurement.measured(reasoning),
                Measurement.measured(Generators.integers(0, 50).next()), Optional.empty(), Optional.empty());
    }

    private MetricsRow row(String id, HarnessUsage usage) {
        return row(id, usage, Population.MODEL_JOB, Phase.PHASE_5_EXECUTE, role, CauseClass.BENIGN,
                SpendClass.SUBMITTED);
    }

    private MetricsRow row(String id, HarnessUsage usage, Population population, Phase phase, String rowRole,
            CauseClass causeClass, SpendClass spendClass) {
        return MetricsRow.builder().queueTaskId(id).attempt(1).role(rowRole).jobId(jobId)
                .generationId(jobId).harness(harness).harnessVersion(harnessVersion)
                .model(model).effort(effort).form(Form.FRESH).phase(phase).outcome(Outcome.SUBMITTED)
                .attemptEnd(AttemptEnd.CLOSED).causeClass(causeClass).spendClass(spendClass).population(population)
                .usage(usage).durationMs(Generators.longs(1L, 100_000L).next()).durationBasis(DurationBasis.PROCESS)
                .recordedAt(recordedAt).build();
    }

    private MetricsRow refused(String id) {
        return MetricsRow.refused(id, role, Phase.PHASE_5_EXECUTE, Outcome.LOST, SpendClass.UNMEASURED,
                Population.MODEL_JOB, recordedAt);
    }

    @Test
    @DisplayName("sums the measured rows into the total and its split")
    void sumsTotalAndSplit() {
        long in1 = figure(), out1 = figure(), read1 = figure(), creation1 = figure(), reasoning1 = figure();
        long in2 = figure(), out2 = figure();
        var aggregate = UsageAggregate.of(List.of(row(taskId(1), usage(in1, out1, read1, creation1, reasoning1)),
                row(taskId(2), usage(in2, out2, 0, 0, 0))));
        assertAll(
                () -> assertEquals(OptionalLong.of(in1 + out1 + read1 + creation1 + reasoning1 + in2 + out2),
                        aggregate.totalTokens(), "the total should sum every component"),
                () -> assertEquals(OptionalLong.of(in1 + read1 + creation1 + in2), aggregate.tokensIn(),
                        "the input side should sum input and cache"),
                () -> assertEquals(OptionalLong.of(out1 + reasoning1 + out2), aggregate.tokensOut(),
                        "the output side should sum output and reasoning"),
                () -> assertEquals(2, aggregate.rows(), "both rows should be counted"),
                () -> assertEquals(0, aggregate.rowsUnmeasured(), "no row should be unmeasured"),
                () -> assertEquals(2, aggregate.populationCount(), "the population should count two rows"),
                () -> assertFalse(aggregate.totalIsFloor(), "the total should not be a floor"),
                () -> assertTrue(aggregate.excluded().isEmpty(), "no row should be excluded"),
                () -> assertEquals(Optional.of(UsageAggregate.Population.MODEL_JOB), aggregate.population(),
                        "the population should be model_job"));
    }

    @Test
    @DisplayName("a not-applicable component counts 0 and the row stays measured")
    void notApplicableCountsZero() {
        long input = figure(), output = figure(), cacheRead = figure();
        var usage = new HarnessUsage(Measurement.measured(input), Measurement.measured(output),
                Measurement.measured(cacheRead), Measurement.notApplicable(), Measurement.notApplicable(),
                Measurement.measured(1), Optional.empty(), Optional.empty());
        var aggregate = UsageAggregate.of(List.of(row(taskId(1), usage)));
        assertAll(
                () -> assertEquals(OptionalLong.of(input + output + cacheRead), aggregate.totalTokens(),
                        "not-applicable should count zero in the total"),
                () -> assertEquals(OptionalLong.of(input + cacheRead), aggregate.tokensIn(),
                        "not-applicable should count zero on input"),
                () -> assertEquals(OptionalLong.of(output), aggregate.tokensOut(),
                        "not-applicable should count zero on output"),
                () -> assertEquals(1, aggregate.rows(), "the row should be counted"),
                () -> assertFalse(aggregate.totalIsFloor(), "the total should not be a floor"));
    }

    @Test
    @DisplayName("an unmeasured component leaves the total a floor and its split unmeasured")
    void unmeasuredMarksFloor() {
        long input = figure(), output = figure(), cacheRead = figure(), creation = figure();
        var usage = new HarnessUsage(Measurement.measured(input), Measurement.measured(output),
                Measurement.measured(cacheRead), Measurement.measured(creation), Measurement.unmeasured(),
                Measurement.measured(1), Optional.empty(), Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS));
        var aggregate = UsageAggregate.of(List.of(row(taskId(1), usage),
                row(taskId(2), usage(input, output, cacheRead, creation, 0))));
        assertAll(
                () -> assertEquals(OptionalLong.of(input + output + cacheRead + creation), aggregate.totalTokens(),
                        "the total should hold the measured row only"),
                () -> assertEquals(OptionalLong.of(2 * (input + cacheRead + creation)), aggregate.tokensIn(),
                        "the measured input side should sum both rows"),
                () -> assertEquals(OptionalLong.empty(), aggregate.tokensOut(),
                        "the output side should be unmeasured"),
                () -> assertEquals(1, aggregate.rowsUnmeasured(), "one row should be unmeasured"),
                () -> assertEquals(1, aggregate.unmeasuredReasons().get(UnmeasuredReason.NOT_REPORTED_BY_HARNESS),
                        "the reason should be counted once"),
                () -> assertTrue(aggregate.totalIsFloor(), "the total should be a floor"));
    }

    @Test
    @DisplayName("an empty population has no total")
    void emptyPopulationHasNoTotal() {
        var aggregate = UsageAggregate.of(UsageAggregate.Population.MODEL_JOB, List.of());
        assertAll(
                () -> assertEquals(OptionalLong.empty(), aggregate.totalTokens(), "an empty total should be absent"),
                () -> assertEquals(OptionalLong.empty(), aggregate.tokensIn(), "an empty input should be absent"),
                () -> assertEquals(OptionalLong.empty(), aggregate.tokensOut(), "an empty output should be absent"),
                () -> assertEquals(0, aggregate.populationCount(), "the population should count no row"),
                () -> assertFalse(aggregate.totalIsFloor(), "an empty total should be no floor"));
    }

    @Test
    @DisplayName("a population with no measured row has no total, never a fabricated zero")
    void refusedRowCountsUnmeasured() {
        var aggregate = UsageAggregate.of(List.of(refused(taskId(1))));
        assertAll(
                () -> assertEquals(OptionalLong.empty(), aggregate.totalTokens(), "the total should be absent"),
                () -> assertEquals(1, aggregate.rowsUnmeasured(), "the refused row should be unmeasured"),
                () -> assertTrue(aggregate.totalIsFloor(), "the total should be a floor"),
                () -> assertTrue(aggregate.unmeasuredReasons().isEmpty(), "no unmeasured reason should be counted"));
    }

    @Test
    @DisplayName("refuses to mix the populations in one figure")
    void refusesMixedPopulations() {
        var fresh = row(taskId(1), usage(figure(), figure(), figure(), figure(), figure()));
        var warm = row(taskId(2), usage(figure(), figure(), figure(), figure(), figure()), Population.MODEL_TASK,
                Phase.PHASE_5_EXECUTE, role, CauseClass.BENIGN, SpendClass.SUBMITTED);
        var rows = List.of(fresh, warm);
        var thrown = assertThrows(IllegalArgumentException.class, () -> UsageAggregate.of(rows),
                "mixed populations should be refused");
        // the phrase of the refusal message is its contract
        assertTrue(thrown.getMessage().contains("one population"), "the refusal should name one population");
    }

    @Test
    @DisplayName("partitions a mixed store by population")
    void partitionsByPopulation() {
        long freshIn = figure(), freshOut = figure(), warmIn = figure(), warmOut = figure();
        var fresh = row(taskId(1), usage(freshIn, freshOut, 0, 0, 0));
        var warm = row(taskId(2), usage(warmIn, warmOut, 0, 0, 0), Population.MODEL_TASK, Phase.PHASE_5_EXECUTE,
                role, CauseClass.BENIGN, SpendClass.SUBMITTED);
        var byPopulation = UsageAggregate.byPopulation(List.of(fresh, warm));
        assertAll(
                () -> assertEquals(OptionalLong.of(freshIn + freshOut),
                        byPopulation.get(UsageAggregate.Population.MODEL_JOB).totalTokens(),
                        "model_job should hold the fresh row"),
                () -> assertEquals(OptionalLong.of(warmIn + warmOut),
                        byPopulation.get(UsageAggregate.Population.MODEL_TASK).totalTokens(),
                        "model_task should hold the warm row"));
    }

    @Test
    @DisplayName("groups per phase, role, cause, spend class and model with harness")
    void groupsByDimension() {
        long executeTotal = 2 * figure();
        long finalizeTotal = 2 * figure();
        var reviewer = role + "x";
        var execute = row(taskId(1), usage(executeTotal / 2, executeTotal / 2, 0, 0, 0));
        var finalize = row(taskId(2), usage(finalizeTotal / 2, finalizeTotal / 2, 0, 0, 0), Population.MODEL_JOB,
                Phase.PHASE_6_FINALIZE, reviewer, CauseClass.RETURNED_WITH_FINDINGS, SpendClass.FINDINGS_ONLY);
        var rows = List.of(execute, finalize);
        assertAll(
                () -> assertEquals(executeTotal, UsageAggregate.byPhase(rows).get(Phase.PHASE_5_EXECUTE)
                        .totalTokens().getAsLong(), "the execute phase should hold its row"),
                () -> assertEquals(2, UsageAggregate.byRole(rows).size(), "there should be one group per role"),
                () -> assertEquals(OptionalLong.of(executeTotal), UsageAggregate.byRole(rows).get(role).totalTokens(),
                        "the role group should hold its row"),
                () -> assertEquals(OptionalLong.of(executeTotal), UsageAggregate.byCauseClass(rows)
                        .get(CauseClass.BENIGN).totalTokens(), "the benign cause should hold its row"),
                () -> assertEquals(OptionalLong.of(finalizeTotal), UsageAggregate.bySpendClass(rows)
                        .get(SpendClass.FINDINGS_ONLY).totalTokens(), "the findings spend should hold its row"),
                () -> assertEquals(OptionalLong.of(executeTotal + finalizeTotal), UsageAggregate.byModelAndHarness(rows)
                        .get(new ModelHarness(model, harness, harnessVersion)).totalTokens(),
                        "the model and harness should hold both rows"));
    }

    @Test
    @DisplayName("reasoning reported apart and included in the output give the same unified figure")
    void reasoningApartAndIncludedAgree() {
        long input = figure(), output = figure(), reasoning = figure();
        var apart = new HarnessUsage(Measurement.measured(input), Measurement.measured(output),
                Measurement.measured(0), Measurement.notApplicable(), Measurement.measured(reasoning),
                Measurement.measured(1), Optional.empty(), Optional.empty());
        var included = new HarnessUsage(Measurement.measured(input), Measurement.measured(output + reasoning),
                Measurement.measured(0), Measurement.notApplicable(), Measurement.notApplicable(),
                Measurement.measured(1), Optional.empty(), Optional.empty());

        assertAll(
                () -> assertEquals(apart.totalTokens(), included.totalTokens(), "both totals should agree"),
                () -> assertEquals(apart.tokensOut(), included.tokensOut(), "both output sides should agree"),
                () -> assertEquals(OptionalLong.of(input + output + reasoning), apart.totalTokens(),
                        "the total should include the reasoning"));
    }

    @Test
    @DisplayName("a side whose rows are all unmeasured is the identity of the total, never a partial split")
    void sumsWithAnAllUnmeasuredSide() {
        long floorIn = figure(), floorOut = figure(), measuredIn = figure(), measuredOut = figure();
        var unmeasured = new HarnessUsage(Measurement.measured(floorIn), Measurement.measured(floorOut),
                Measurement.measured(0), Measurement.measured(0), Measurement.unmeasured(), Measurement.measured(1),
                Optional.empty(), Optional.of(UnmeasuredReason.NOT_REPORTED_BY_HARNESS));
        var floor = UsageAggregate.of(List.of(row(taskId(1), unmeasured)));
        var measured = UsageAggregate.of(List.of(row(taskId(2), usage(measuredIn, measuredOut, 0, 0, 0))));

        var sum = floor.plus(measured);
        assertAll(
                () -> assertEquals(OptionalLong.of(measuredIn + measuredOut), sum.totalTokens(),
                        "the total should hold the measured side"),
                () -> assertEquals(OptionalLong.of(floorIn + measuredIn), sum.tokensIn(),
                        "the input side should sum both sides"),
                () -> assertEquals(OptionalLong.empty(), sum.tokensOut(), "the output side should be unmeasured"),
                () -> assertEquals(2, sum.populationCount(), "the sum should count two rows"),
                () -> assertEquals(1, sum.rowsUnmeasured(), "one row should be unmeasured"),
                () -> assertTrue(sum.totalIsFloor(), "the summed total should be a floor"));
    }

    @Test
    @DisplayName("sums two aggregates of one population and keeps the floor")
    void sumsAggregates() {
        long firstIn = figure(), firstOut = figure(), secondIn = figure(), secondOut = figure();
        var first = UsageAggregate.of(List.of(row(taskId(1), usage(firstIn, firstOut, 0, 0, 0))));
        var second = UsageAggregate.of(List.of(row(taskId(2), usage(secondIn, secondOut, 0, 0, 0))));
        var sum = first.plus(second);
        assertAll(
                () -> assertEquals(OptionalLong.of(firstIn + firstOut + secondIn + secondOut), sum.totalTokens(),
                        "the total should sum both aggregates"),
                () -> assertEquals(OptionalLong.of(firstIn + secondIn), sum.tokensIn(),
                        "the input side should sum both aggregates"),
                () -> assertEquals(OptionalLong.of(firstOut + secondOut), sum.tokensOut(),
                        "the output side should sum both aggregates"),
                () -> assertEquals(2, sum.populationCount(), "the sum should count two rows"),
                () -> assertFalse(sum.totalIsFloor(), "the sum should not be a floor"));
    }

    @Test
    @DisplayName("refuses to sum two populations")
    void refusesSumOfPopulations() {
        var fresh = UsageAggregate.of(List.of(row(taskId(1), usage(figure(), figure(), 0, 0, 0))));
        var warm = UsageAggregate.of(UsageAggregate.Population.MODEL_TASK,
                List.of(usage(figure(), figure(), 0, 0, 0)));
        assertThrows(IllegalArgumentException.class, () -> fresh.plus(warm),
                "a sum of two populations should be refused");
    }
}
