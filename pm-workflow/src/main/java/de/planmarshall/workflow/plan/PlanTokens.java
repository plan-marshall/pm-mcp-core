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

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import de.planmarshall.core.store.MetricsRow;
import de.planmarshall.core.store.UsageAggregate;

/**
 * The token facts of a running plan (PM-WF-14, Model Work § Usage Reporting &amp; Spend Classes): the tokens used so far,
 * with the split and the coverage every view shows.
 * <p>
 * The figure is derived on read from the plan's metrics rows through {@link UsageAggregate}, so the representation and
 * every other view show the same number. A figure is carried per population ({@code model_job}, {@code model_task}), the
 * only figures that may be compared or summed; the warm workers' {@code worker_idle} usage belongs to no plan and is
 * not part of this representation.
 * <p>
 * The representation carries usage only: there is no limit, no budget line, no projection and no comparison with one.
 * Nothing here bounds a plan's use.
 *
 * @param planId      the plan the figures belong to
 * @param populations the aggregate per population, each with the total, its split and its coverage
 * @since 0.1
 */
public record PlanTokens(String planId, SortedMap<UsageAggregate.Population, UsageAggregate> populations) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the plan id is blank
     */
    public PlanTokens {
        Objects.requireNonNull(planId, "planId");
        Objects.requireNonNull(populations, "populations");
        if (planId.isBlank()) {
            throw new IllegalArgumentException("'plan_id' must not be blank");
        }
        populations = Collections.unmodifiableSortedMap(new TreeMap<>(populations));
    }

    /**
     * @param planId the plan
     * @param rows   the task rows of the plan's metrics store
     * @return the token facts of the plan, derived on read
     */
    public static PlanTokens of(String planId, Collection<MetricsRow> rows) {
        return new PlanTokens(planId, UsageAggregate.byPopulation(rows));
    }

    /**
     * @param planId the plan
     * @return the token facts of a plan that has no metrics row yet
     */
    public static PlanTokens empty(String planId) {
        return new PlanTokens(planId, new TreeMap<>());
    }

    /**
     * @param population a population
     * @return the plan's figure of that population, empty when the plan has no row of it
     */
    public Optional<UsageAggregate> aggregate(UsageAggregate.Population population) {
        return Optional.ofNullable(populations.get(population));
    }

    /** @return every population's figure, each with the total, its split and its coverage */
    public Map<UsageAggregate.Population, UsageAggregate> byPopulation() {
        return populations;
    }
}
