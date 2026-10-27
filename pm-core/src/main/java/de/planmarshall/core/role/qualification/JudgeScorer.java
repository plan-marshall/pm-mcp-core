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

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * The port through which a grading run sends a rubric answer to a task of the judge role (Model Work § Grading): the
 * model caller of the judge implements it. The prompt carries the item's question, facts and reference with the answer
 * in-band; a blind scoring withholds the reference.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface JudgeScorer {

    /**
     * @param prompt what the judge is asked
     * @return the structured score; empty when the judge task closed {@code lost}, which leaves the answer ungraded
     */
    Optional<JudgeScore> score(JudgePrompt prompt);

    /**
     * One judge task.
     *
     * @param itemId    the item the answer belongs to
     * @param question  the item's question
     * @param facts     the item's facts
     * @param reference the reference answer; {@code null} for the blind scoring, where it is withheld
     * @param answer    the answer to score
     * @since 0.1
     */
    record JudgePrompt(String itemId, String question, String facts, String reference, String answer) {

        /**
         * @throws NullPointerException if a required value is {@code null}
         */
        public JudgePrompt {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(question, "question");
            Objects.requireNonNull(facts, "facts");
            Objects.requireNonNull(answer, "answer");
        }
    }

    /**
     * The structured result of one judge task.
     *
     * @param score      the item's score, from {@code 0} to {@code 1}
     * @param totalTokens the tokens the judge task used
     * @param durationMs how long the task took
     * @since 0.1
     */
    record JudgeScore(BigDecimal score, TokenTotal totalTokens, long durationMs) {

        /**
         * @throws IllegalArgumentException if the score lies outside {@code 0} to {@code 1}, or the duration is negative
         */
        public JudgeScore {
            Objects.requireNonNull(score, "score");
            Objects.requireNonNull(totalTokens, "totalTokens");
            if (score.signum() < 0 || score.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("A score lies between 0 and 1: " + score);
            }
            if (durationMs < 0) {
                throw new IllegalArgumentException("'durationMs' must not be negative: " + durationMs);
            }
        }
    }
}
