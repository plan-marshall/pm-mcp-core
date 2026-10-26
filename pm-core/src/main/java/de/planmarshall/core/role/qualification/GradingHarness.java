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

import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The port through which a grading run offers one item to the configuration it grades (Model Work § Grading): the task
 * protocol of the role's configuration implements it. The run never starts a worker itself, so the grading engine stays
 * independent of the harness's launch contract.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface GradingHarness {

    /**
     * @param offered one item offered in one form and run
     * @return the submitted answer; empty when the task closed without one, which leaves the item ungraded
     */
    Optional<GradedAnswer> answer(Offered offered);

    /**
     * One item offered to the harness.
     *
     * @param item     the corpus item
     * @param form     the form it is offered in
     * @param checkout where it must run
     * @param run      the run of the item, from {@code 1}; above one for the screener's repeated runs
     * @since 0.1
     */
    record Offered(ImportedItem item, RoleForm form, Checkout checkout, int run) {

        /**
         * @throws IllegalArgumentException if the run is below one
         */
        public Offered {
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(form, "form");
            Objects.requireNonNull(checkout, "checkout");
            if (run < 1) {
                throw new IllegalArgumentException("'run' starts at 1: " + run);
            }
        }
    }

    /**
     * The answer of one offered item, with what its task measured.
     *
     * @param answer     the submitted answer
     * @param totalTokens the tokens the answer used
     * @param durationMs how long the answer took
     * @since 0.1
     */
    record GradedAnswer(String answer, TokenTotal totalTokens, long durationMs) {

        /**
         * @throws IllegalArgumentException if the duration is negative
         */
        public GradedAnswer {
            Objects.requireNonNull(answer, "answer");
            Objects.requireNonNull(totalTokens, "totalTokens");
            if (durationMs < 0) {
                throw new IllegalArgumentException("'durationMs' must not be negative: " + durationMs);
            }
        }
    }
}
