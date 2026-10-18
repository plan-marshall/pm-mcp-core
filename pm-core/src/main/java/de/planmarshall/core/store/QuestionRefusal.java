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

import java.util.Objects;

/**
 * Why the question log refused to append a line (PM-WF-12, PM-IMPL-2): a value the store returns, never an exception.
 * When it is returned, nothing was added to the transaction.
 * <p>
 * The log holds two invariants of a question under the lock of its scope: a question is closed by exactly one line,
 * so a second closing line is refused {@link AlreadyAnswered}, and an answer names the revision it answers, so an
 * answer to another revision than the current one is refused {@link Conflict} with the current revision. Both
 * invariants are decided from the lines the log holds, so a log with a line that was refused when read cannot decide
 * them: every append to it is refused {@link Unreadable}, naming the first refused line. Whether an answer fits its
 * part is checked by the engine, not here.
 *
 * @since 0.1
 */
public sealed interface QuestionRefusal
        permits QuestionRefusal.AlreadyAnswered, QuestionRefusal.Conflict, QuestionRefusal.Unreadable {

    /** @return the question the refused line was about */
    String questionId();

    /**
     * @return the code of the refusal; {@code already_answered} and {@code conflict} are codes the local API reports,
     *         {@code unreadable} has no entry in the API's closed list of error codes yet, so the API has to map it
     */
    String code();

    /**
     * The question is closed already: it has an {@code answered}, {@code withdrawn} or {@code cancelled} line, or the
     * appended lines close it twice.
     *
     * @param questionId the question
     * @since 0.1
     */
    record AlreadyAnswered(String questionId) implements QuestionRefusal {

        /** The error code of the refusal. */
        public static final String CODE = "already_answered";

        /** @throws NullPointerException if the question id is {@code null} */
        public AlreadyAnswered {
            Objects.requireNonNull(questionId, "questionId");
        }

        @Override
        public String code() {
            return CODE;
        }
    }

    /**
     * The answer names another revision than the current one of its decision.
     *
     * @param questionId      the question
     * @param currentRevision the current revision of the decision, which a new answer names
     * @since 0.1
     */
    record Conflict(String questionId, int currentRevision) implements QuestionRefusal {

        /** The error code of the refusal. */
        public static final String CODE = "conflict";

        /**
         * @throws NullPointerException     if the question id is {@code null}
         * @throws IllegalArgumentException if the revision is less than 1
         */
        public Conflict {
            Objects.requireNonNull(questionId, "questionId");
            if (currentRevision < 1) {
                throw new IllegalArgumentException("A revision counts from 1: " + currentRevision);
            }
        }

        @Override
        public String code() {
            return CODE;
        }
    }

    /**
     * The log holds a line that was refused when read (unreadable, or of another {@code format_version}). A refused
     * line names no question the log can read, so it may be the closing line or the revision of any question, the
     * question of the append included; the state of the questions is unknown and nothing is appended until the
     * operator has dealt with the line.
     *
     * @param questionId  the question of the first line of the refused append
     * @param refusedLine the line number of the first refused line of the log, counted from 1
     * @since 0.1
     */
    record Unreadable(String questionId, long refusedLine) implements QuestionRefusal {

        /** The error code of the refusal. */
        public static final String CODE = "unreadable";

        /**
         * @throws NullPointerException     if the question id is {@code null}
         * @throws IllegalArgumentException if the line number is less than 1
         */
        public Unreadable {
            Objects.requireNonNull(questionId, "questionId");
            if (refusedLine < 1) {
                throw new IllegalArgumentException("Line numbers count from 1: " + refusedLine);
            }
        }

        @Override
        public String code() {
            return CODE;
        }
    }
}
