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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

import de.planmarshall.core.store.HarnessUsage.UnmeasuredReason;
import de.planmarshall.core.store.MetricsRow.CauseClass;
import de.planmarshall.core.store.MetricsRow.Phase;
import de.planmarshall.core.store.MetricsRow.SpendClass;

/**
 * One aggregate of the metrics rows (PM-WF-14, PM-WORK-3, Store Schemas § Metrics Store, Aggregates): the tokens used
 * with their split, and the coverage those figures rest on.
 * <p>
 * An aggregate is never persisted: it is derived on read from the rows of a {@code metrics.json}, and the views derive
 * it through this one type so that every client shows the same figure. It carries the total with its split
 * ({@link #tokensIn()} is input, cache read and cache creation; {@link #tokensOut()} is output and reasoning, each
 * derived by {@link HarnessUsage}) and the coverage: how many rows the figure rests on, how many were unmeasured with
 * which reason, and whether the figure is a floor.
 * <p>
 * A total over a population with unmeasured rows is a floor ({@link #totalIsFloor()}), shown as <em>at least</em> its
 * value and never as exact. {@link #totalTokens()} is empty when the population is empty ({@code population_count == 0})
 * and never a fabricated {@code 0}: a figure without a measured row is unmeasured. The split is empty when any row of the
 * population left a component of it unmeasured, because a partial sum is never recorded.
 * <p>
 * Sums and relations are formed only between figures of one population (Store Schemas § Metrics Store, Populations), so
 * a set of rows of different populations is refused rather than silently mixed. An epic's figure is the sum of its
 * plans' aggregates and its own rows, formed with {@link #plus(UsageAggregate)}.
 *
 * @param population         the population the figures count; empty for an aggregate over no row
 * @param totalTokens        the summed total tokens, empty when the population is empty
 * @param tokensIn           the summed input split, empty when the population is empty or a component is unmeasured
 * @param tokensOut          the summed output split, empty when the population is empty or a component is unmeasured
 * @param rows               the rows the figure rests on: the measured rows plus the excluded ones
 * @param rowsUnmeasured     the rows whose total is unmeasured
 * @param unmeasuredReasons  how many unmeasured rows name each reason
 * @param populationCount    every row of the population
 * @param excluded           the rows excluded from the figure, by class
 * @param totalIsFloor       whether the figure is a lower bound because a row of the population is unmeasured
 * @since 0.1
 */
public record UsageAggregate(Optional<Population> population, OptionalLong totalTokens, OptionalLong tokensIn,
OptionalLong tokensOut, long rows, long rowsUnmeasured, Map<UnmeasuredReason, Long> unmeasuredReasons,
long populationCount, SortedMap<String, Long> excluded, boolean totalIsFloor) {

    /** The population of a usage figure (Store Schemas § Metrics Store, Populations). The set is closed. */
    public enum Population {
        /** A fresh worker's whole run. */
        MODEL_JOB("model_job"),
        /** One task a warm worker served. */
        MODEL_TASK("model_task"),
        /** A warm worker's turns that served no task. */
        WORKER_IDLE("worker_idle");

        private final String wireName;

        Population(String wireName) {
            this.wireName = wireName;
        }

        /** @return the name of the value in a store or a view */
        public String wireName() {
            return wireName;
        }

        /**
         * @param row the population of a task row
         * @return the aggregate population of that row
         */
        static Population of(MetricsRow.Population row) {
            return switch (row) {
                case MODEL_JOB -> MODEL_JOB;
                case MODEL_TASK -> MODEL_TASK;
            };
        }
    }

    /**
     * One figure's model and harness, the dimension the operator compares harnesses by (Store Schemas § Metrics Store,
     * What the Operator Sees). The harness version is part of the key so that a version change shows as its own row.
     *
     * @param model          the model passed to the harness; {@code null} only for a refused row
     * @param harness        the worker's harness; {@code null} only for a refused row
     * @param harnessVersion the harness version probed at the worker's spawn; {@code null} only for a refused row
     * @since 0.1
     */
    public record ModelHarness(String model, String harness, String harnessVersion)
            implements Comparable<ModelHarness> {

        @Override
        public int compareTo(ModelHarness other) {
            var byModel = compareNullsFirst(model, other.model);
            if (byModel != 0) {
                return byModel;
            }
            var byHarness = compareNullsFirst(harness, other.harness);
            return byHarness != 0 ? byHarness : compareNullsFirst(harnessVersion, other.harnessVersion);
        }

        private static int compareNullsFirst(String left, String right) {
            if (left == null) {
                return right == null ? 0 : -1;
            }
            return right == null ? 1 : left.compareTo(right);
        }
    }

    /**
     * @throws IllegalArgumentException if a count is negative, a count exceeds the population, or the floor marker
     *                                  disagrees with the unmeasured count
     */
    public UsageAggregate {
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(totalTokens, "totalTokens");
        Objects.requireNonNull(tokensIn, "tokensIn");
        Objects.requireNonNull(tokensOut, "tokensOut");
        if (rows < 0 || rowsUnmeasured < 0 || populationCount < 0) {
            throw new IllegalArgumentException("A count of an aggregate is not negative");
        }
        if (rowsUnmeasured > populationCount) {
            throw new IllegalArgumentException("'rows_unmeasured' exceeds 'population_count'");
        }
        if (rows + rowsUnmeasured > populationCount) {
            throw new IllegalArgumentException("'rows' and 'rows_unmeasured' exceed 'population_count'");
        }
        if (totalIsFloor != (rowsUnmeasured > 0)) {
            throw new IllegalArgumentException("'total_is_floor' is true exactly while 'rows_unmeasured' is above 0");
        }
        if (populationCount == 0 && totalTokens.isPresent()) {
            throw new IllegalArgumentException("An empty population has no total");
        }
        if (populationCount > 0 && totalTokens.isEmpty()) {
            throw new IllegalArgumentException("A population with rows has a total, exact or a floor");
        }
        var reasons = new EnumMap<UnmeasuredReason, Long>(UnmeasuredReason.class);
        reasons.putAll(Objects.requireNonNull(unmeasuredReasons, "unmeasuredReasons"));
        unmeasuredReasons = Collections.unmodifiableMap(reasons);
        excluded = Collections.unmodifiableSortedMap(new TreeMap<>(Objects.requireNonNull(excluded, "excluded")));
    }

    /**
     * @param population the population
     * @param usages     the usages of its rows
     * @return the aggregate of the rows, with the total and its split
     */
    public static UsageAggregate of(Population population, List<HarnessUsage> usages) {
        Objects.requireNonNull(population, "population");
        Objects.requireNonNull(usages, "usages");
        long total = 0;
        long in = 0;
        long out = 0;
        var unmeasured = 0;
        var counted = 0;
        var inMeasured = true;
        var outMeasured = true;
        var reasons = new EnumMap<UnmeasuredReason, Long>(UnmeasuredReason.class);
        for (var usage : usages) {
            counted++;
            var rowTotal = usage.totalTokens();
            if (rowTotal.isPresent()) {
                total += rowTotal.getAsLong();
            } else {
                unmeasured++;
                usage.unmeasuredReason().ifPresent(reason -> reasons.merge(reason, 1L, Long::sum));
            }
            var rowIn = usage.tokensIn();
            var rowOut = usage.tokensOut();
            if (rowIn.isPresent()) {
                in += rowIn.getAsLong();
            } else {
                inMeasured = false;
            }
            if (rowOut.isPresent()) {
                out += rowOut.getAsLong();
            } else {
                outMeasured = false;
            }
        }
        var populationCount = counted;
        var floor = unmeasured > 0;
        return new UsageAggregate(Optional.of(population),
                populationCount == 0 ? OptionalLong.empty() : OptionalLong.of(total),
                populationCount == 0 || !inMeasured ? OptionalLong.empty() : OptionalLong.of(in),
                populationCount == 0 || !outMeasured ? OptionalLong.empty() : OptionalLong.of(out),
                populationCount - unmeasured, unmeasured, reasons, populationCount, new TreeMap<>(), floor);
    }

    /**
     * @param rows the task rows of one metrics store, or of several stores of one population
     * @return the aggregate of the rows
     * @throws IllegalArgumentException if the rows are of different populations
     */
    public static UsageAggregate of(Collection<MetricsRow> rows) {
        Objects.requireNonNull(rows, "rows");
        var population = populationOf(rows);
        return of(population, rows.stream().map(MetricsRow::usage).toList());
    }

    /**
     * @param rows the worker-idle rows of the workspace metrics store
     * @return the aggregate of the warm workers' idle usage, population {@code worker_idle}
     */
    public static UsageAggregate ofIdle(Collection<WorkerIdleRow> rows) {
        Objects.requireNonNull(rows, "rows");
        return of(Population.WORKER_IDLE, rows.stream().map(WorkerIdleRow::usage).toList());
    }

    /**
     * @param other another aggregate of the same population
     * @return the sum of both figures: an epic's figure from its plans' and its own rows
     * @throws IllegalArgumentException if the populations differ
     */
    public UsageAggregate plus(UsageAggregate other) {
        Objects.requireNonNull(other, "other");
        if (!population.equals(other.population)) {
            throw new IllegalArgumentException(
                    "Sums are formed only between figures of one population: %s and %s".formatted(population, other.population));
        }
        var counted = populationCount + other.populationCount;
        var unmeasured = rowsUnmeasured + other.rowsUnmeasured;
        var reasons = new EnumMap<UnmeasuredReason, Long>(UnmeasuredReason.class);
        reasons.putAll(unmeasuredReasons);
        other.unmeasuredReasons.forEach((reason, count) -> reasons.merge(reason, count, Long::sum));
        var excludedRows = new TreeMap<>(excluded);
        other.excluded.forEach((kind, count) -> excludedRows.merge(kind, count, Long::sum));
        return new UsageAggregate(population, sum(totalTokens, other.totalTokens), sum(tokensIn, other.tokensIn),
                sum(tokensOut, other.tokensOut), rows + other.rows, unmeasured, reasons, counted, excludedRows,
                unmeasured > 0);
    }

    /**
     * @param rows the task rows of one plan
     * @return the aggregate per phase of the rows; the rows must be of one population
     */
    public static SortedMap<Phase, UsageAggregate> byPhase(Collection<MetricsRow> rows) {
        return grouped(rows, MetricsRow::phase, Phase::compareTo);
    }

    /**
     * @param rows the task rows of one plan
     * @return the aggregate per role of the rows; the rows must be of one population
     */
    public static SortedMap<String, UsageAggregate> byRole(Collection<MetricsRow> rows) {
        return grouped(rows, MetricsRow::role, String::compareTo);
    }

    /**
     * @param rows the task rows of one plan
     * @return the aggregate per model and harness of the rows; the rows must be of one population
     */
    public static SortedMap<ModelHarness, UsageAggregate> byModelAndHarness(Collection<MetricsRow> rows) {
        return grouped(rows, row -> new ModelHarness(row.model(), row.harness(), row.harnessVersion()),
                ModelHarness::compareTo);
    }

    /**
     * @param rows the task rows of one plan
     * @return the aggregate per cause class of the rows; the rows must be of one population
     */
    public static SortedMap<CauseClass, UsageAggregate> byCauseClass(Collection<MetricsRow> rows) {
        return grouped(rows, MetricsRow::causeClass, CauseClass::compareTo);
    }

    /**
     * @param rows the task rows of one plan
     * @return the aggregate per spend class of the rows; the rows must be of one population
     */
    public static SortedMap<SpendClass, UsageAggregate> bySpendClass(Collection<MetricsRow> rows) {
        return grouped(rows, MetricsRow::spendClass, SpendClass::compareTo);
    }

    /**
     * @param rows the task rows of one plan or epic store, of both populations
     * @return the aggregate per population of the rows, the only figures that may be summed together
     */
    public static SortedMap<Population, UsageAggregate> byPopulation(Collection<MetricsRow> rows) {
        return grouped(rows, row -> Population.of(row.population()), Population::compareTo);
    }

    private static OptionalLong sum(OptionalLong left, OptionalLong right) {
        if (left.isEmpty() || right.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(left.getAsLong() + right.getAsLong());
    }

    private static Population populationOf(Collection<MetricsRow> rows) {
        var populations = rows.stream().map(row -> Population.of(row.population())).distinct().toList();
        if (populations.size() > 1) {
            throw new IllegalArgumentException(
                    "Sums are formed only between figures of one population: %s".formatted(populations));
        }
        return populations.isEmpty() ? Population.MODEL_JOB : populations.getFirst();
    }

    private static <K> SortedMap<K, UsageAggregate> grouped(Collection<MetricsRow> rows,
            Function<MetricsRow, K> dimension, java.util.Comparator<K> order) {
        Objects.requireNonNull(rows, "rows");
        var groups = new LinkedHashMap<K, List<MetricsRow>>();
        for (var row : rows) {
            groups.computeIfAbsent(dimension.apply(row), key -> new ArrayList<>()).add(row);
        }
        var result = new TreeMap<K, UsageAggregate>(order);
        groups.forEach((key, groupRows) -> result.put(key, of(groupRows)));
        return Collections.unmodifiableSortedMap(result);
    }
}
