/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import lombok.Builder;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.role.RoleForm;
import de.planmarshall.core.role.corpus.ImportedItem;
import de.planmarshall.core.role.qualification.Checkout;
import de.planmarshall.core.role.qualification.TokenTotal;

/**
 * The port through which the grading adapter offers one corpus item as a task of the task protocol (Model Work § Grading).
 * <p>
 * The adapter never writes the queue itself: a task is offered to the protocol of the project, which serves it to the
 * worker generation the adapter started, waits for the worker's {@code submit} or for the end of its attempt, and answers
 * the submitted answer with the tokens and the time it measured. The port exists because the protocol lives above
 * {@code pm-runtime} (the assembly supplies its implementation), exactly as the launch contract lives below the grading
 * engine.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface ItemTaskOffers {

    /**
     * Offers one item's task and waits for its answer.
     *
     * @param task the task to offer
     * @return the submitted answer; empty when the task closed without one, which leaves the item ungraded
     */
    Optional<Submitted> offer(Task task);

    /**
     * One item offered as a task to one generation.
     *
     * @param role         the role whose task it is
     * @param generationId the generation the adapter started, or the running warm generation that serves the item
     * @param item         the corpus item the task asks
     * @param form         the form the item is graded in
     * @param checkout     where the item must run
     * @param workdir      the working directory the worker was launched in; {@code null} for a zero-tool worker
     * @since 0.1
     */
    @Builder
    record Task(String role, String generationId, ImportedItem item, RoleForm form, Checkout checkout,
    @Nullable Path workdir) {

        /**
         * @throws NullPointerException if a required component is {@code null}
         */
        public Task {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(generationId, "generationId");
            Objects.requireNonNull(item, "item");
            Objects.requireNonNull(form, "form");
            Objects.requireNonNull(checkout, "checkout");
        }
    }

    /**
     * The answer one offered task submitted.
     *
     * @param answer      the submitted answer, as the task protocol returned it
     * @param totalTokens the tokens the answer used
     * @param durationMs  how long the answer took
     * @since 0.1
     */
    record Submitted(String answer, TokenTotal totalTokens, long durationMs) {

        /**
         * @throws NullPointerException     if a required component is {@code null}
         * @throws IllegalArgumentException if the duration is negative
         */
        public Submitted {
            Objects.requireNonNull(answer, "answer");
            Objects.requireNonNull(totalTokens, "totalTokens");
            if (durationMs < 0) {
                throw new IllegalArgumentException("'durationMs' must not be negative: " + durationMs);
            }
        }
    }
}
