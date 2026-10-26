/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import de.planmarshall.core.role.RoleForm;

/**
 * The offer the setup or the operator's {@code roles qualify} shows before a grading costs model tokens (Model Work
 * § Grading at Install): the items and forms of the run and the expected tokens it will spend. The grading runs only on
 * the operator's {@link GradingConsent#GRANTED}.
 *
 * @param role           the role to grade
 * @param topics         the topics the role's corpus defines
 * @param items          the number of items offered, over every topic
 * @param forms          the forms the run grades
 * @param runsPerItem    how often each item is offered
 * @param judgeItems     the number of judge instrument-check items
 * @param expectedTokens the expected tokens of the whole run
 * @since 0.1
 */
public record GradingOffer(String role, List<Topic> topics, int items, List<RoleForm> forms, int runsPerItem,
int judgeItems, long expectedTokens) {

    /**
     * @throws IllegalArgumentException if a count is negative or no form is offered
     */
    public GradingOffer {
        Objects.requireNonNull(role, "role");
        topics = List.copyOf(Objects.requireNonNull(topics, "topics"));
        forms = List.copyOf(Objects.requireNonNull(forms, "forms"));
        if (forms.isEmpty()) {
            throw new IllegalArgumentException("A grading offers at least one form");
        }
        if (items < 0 || judgeItems < 0 || runsPerItem < 1 || expectedTokens < 0) {
            throw new IllegalArgumentException("A grading offer counts what it runs: " + items);
        }
    }

    /**
     * Estimates the tokens a plan spends: a compiled per-item estimate for every offered item and run, plus the judge's
     * oracle and blind scorings of every judge item.
     *
     * @param plan a grading plan
     * @return the expected tokens
     */
    public static long expectedTokens(GradingPlan plan) {
        Objects.requireNonNull(plan, "plan");
        var forms = forms(plan);
        var offered = (long) items(plan) * forms.size() * plan.profile().runsPerItem();
        return offered * ExpectedTokens.PER_ITEM
                + (long) plan.judge().items().size() * 2L * ExpectedTokens.PER_JUDGE;
    }

    /**
     * @param plan a grading plan
     * @return the offer of that plan
     */
    public static GradingOffer of(GradingPlan plan) {
        Objects.requireNonNull(plan, "plan");
        var topics = new ArrayList<>(roleTopics(plan).topics());
        return new GradingOffer(plan.role().artifact().name(), topics, items(plan), forms(plan),
                plan.profile().runsPerItem(), plan.judge().items().size(), expectedTokens(plan));
    }

    private static List<RoleForm> forms(GradingPlan plan) {
        return plan.role().zeroTool() ? List.of(RoleForm.FRESH) : List.of(RoleForm.FRESH, RoleForm.WARM);
    }

    private static RoleTopics roleTopics(GradingPlan plan) {
        return RoleTopics.of(plan.role().artifact().name(), plan.role().roleClass(), plan.items());
    }

    private static int items(GradingPlan plan) {
        var total = 0;
        for (var topic : roleTopics(plan).topics()) {
            total += RoleTopics.items(plan.items(), topic, plan.role().artifact().name()).size();
        }
        return total;
    }

    /**
     * The compiled token estimates of an offer. They bound the offer shown to the operator; the record keeps the
     * measured tokens.
     *
     * @since 0.1
     */
    public static final class ExpectedTokens {

        /** The compiled expected tokens of one offered item and run. */
        public static final long PER_ITEM = 4_000;

        /** The compiled expected tokens of one judge task. */
        public static final long PER_JUDGE = 2_000;

        private ExpectedTokens() {
        }
    }
}
