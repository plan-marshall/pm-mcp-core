/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.epic;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import de.planmarshall.core.store.MetricsRow;
import de.planmarshall.core.store.UsageAggregate;
import de.planmarshall.workflow.plan.PlanTokens;

/**
 * The token facts of an epic (PM-WF-14, Model Work § Usage Reporting &amp; Spend Classes): the sum of its plans' figures
 * and of its own tasks, with the split and the coverage.
 * <p>
 * An epic's own tasks are the ones outside a plan's store — decompose, landing and review — recorded in the epic's
 * metrics store with {@code phase: null}. The sum is formed per population, the only figures that may be summed; a floor
 * in any part makes the epic's figure a floor. Every figure is derived on read through {@link UsageAggregate}, so the
 * epic and every other view show the same number.
 * <p>
 * The representation carries usage only: there is no limit, no budget line, no projection and no comparison with one.
 *
 * @param epicId      the epic the figures belong to
 * @param populations the aggregate per population, each with the total, its split and its coverage
 * @since 0.1
 */
public record EpicTokens(String epicId, SortedMap<UsageAggregate.Population, UsageAggregate> populations) {

    /**
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the epic id is blank
     */
    public EpicTokens {
        Objects.requireNonNull(epicId, "epicId");
        Objects.requireNonNull(populations, "populations");
        if (epicId.isBlank()) {
            throw new IllegalArgumentException("'epic_id' must not be blank");
        }
        populations = Collections.unmodifiableSortedMap(new TreeMap<>(populations));
    }

    /**
     * @param epicId   the epic
     * @param ownRows  the epic's own task rows, from its metrics store
     * @param plans    the token facts of the epic's plans, the archived ones included
     * @return the token facts of the epic: its plans' figures plus its own rows
     */
    public static EpicTokens of(String epicId, Collection<MetricsRow> ownRows, Collection<PlanTokens> plans) {
        Objects.requireNonNull(ownRows, "ownRows");
        return empty(epicId).withOwnRows(ownRows).withPlans(plans);
    }

    /**
     * @param epicId the epic
     * @return the token facts of an epic that has neither a plan figure nor an own row
     */
    public static EpicTokens empty(String epicId) {
        return new EpicTokens(epicId, new TreeMap<>());
    }

    /**
     * @param population a population
     * @return the epic's figure of that population, empty when neither it nor a plan has a row of it
     */
    public Optional<UsageAggregate> aggregate(UsageAggregate.Population population) {
        return Optional.ofNullable(populations.get(population));
    }

    /** @return every population's figure, each with the total, its split and its coverage */
    public SortedMap<UsageAggregate.Population, UsageAggregate> byPopulation() {
        return populations;
    }

    /**
     * @param plans further plans of the epic
     * @return this representation with those plans' figures added
     */
    public EpicTokens withPlans(Collection<PlanTokens> plans) {
        Objects.requireNonNull(plans, "plans");
        var merged = new TreeMap<>(populations);
        for (var plan : plans) {
            plan.populations().forEach((population, aggregate) ->
                    merged.merge(population, aggregate, UsageAggregate::plus));
        }
        return new EpicTokens(epicId, merged);
    }

    /**
     * @param ownRows the epic's own task rows
     * @return this representation with the epic's own rows added
     */
    public EpicTokens withOwnRows(Collection<MetricsRow> ownRows) {
        var merged = new TreeMap<>(populations);
        UsageAggregate.byPopulation(ownRows).forEach((population, aggregate) ->
                merged.merge(population, aggregate, UsageAggregate::plus));
        return new EpicTokens(epicId, merged);
    }
}
