/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The engine port that reads a question record of a scope (PM-ARCH-4, bullets 4 and 5: a question state rests on the
 * persisted record, never on the memory of a running engine). The engine reads the record a {@code question} state
 * waits on to learn whether it is still open or resolved, and with which closed resolution outcome.
 * <p>
 * The read is tri-state: a record that cannot be read (absent, unparseable, of another class) is the explicit result
 * {@link QuestionRead.Unreadable}, never taken for an open record, so a broken store is never mistaken for a question
 * still awaiting its answer.
 * <p>
 * The question store of {@code pm-runtime} implements the port; no class of {@code pm-core} does (PM-IMPL-5).
 *
 * @since 0.1
 */
public interface QuestionRecordReader {

    /**
     * Reads a question record.
     *
     * @param request the scope and question to read
     * @return the record open, resolved with its outcome, or unreadable
     */
    QuestionRead read(ReadRequest request);

    /**
     * What the engine asks the port to read: one question record of one scope.
     *
     * @param scopeId the identifier of the plan or epic the record belongs to; empty for the workspace scope
     * @param questionId the identifier of the question, {@code ^Q-[0-9]{3,6}$}
     * @since 0.1
     */
    record ReadRequest(Optional<String> scopeId, String questionId) {

        private static final Pattern QUESTION_ID = Pattern.compile("^Q-[0-9]{3,6}$");

        /**
         * @throws NullPointerException if a component is {@code null}
         * @throws IllegalArgumentException if the question identifier does not match {@code ^Q-[0-9]{3,6}$}
         */
        public ReadRequest {
            Objects.requireNonNull(scopeId, "scopeId");
            Objects.requireNonNull(questionId, "questionId");
            if (!QUESTION_ID.matcher(questionId).matches()) {
                throw new IllegalArgumentException("question_id does not match ^Q-[0-9]{3,6}$: " + questionId);
            }
        }
    }

    /**
     * The three results of one read.
     *
     * @since 0.1
     */
    sealed interface QuestionRead permits QuestionRead.Open, QuestionRead.Resolved, QuestionRead.Unreadable {

        /**
         * The record was read and is not resolved yet; the question state keeps resting.
         *
         * @since 0.1
         */
        record Open() implements QuestionRead {
        }

        /**
         * The record was read and is resolved; the question state takes the transition of the outcome.
         *
         * @param outcome the closed resolution outcome of the record's class, for example {@code answered}
         * @since 0.1
         */
        record Resolved(String outcome) implements QuestionRead {

            /**
             * @throws NullPointerException if the outcome is {@code null}
             * @throws IllegalArgumentException if the outcome is blank
             */
            public Resolved {
                requireNonBlank(outcome, "outcome");
            }
        }

        /**
         * The record could not be read; whether it is open or resolved is unknown.
         *
         * @param reason why the record could not be read
         * @since 0.1
         */
        record Unreadable(String reason) implements QuestionRead {

            /**
             * @throws NullPointerException if the reason is {@code null}
             * @throws IllegalArgumentException if the reason is blank
             */
            public Unreadable {
                requireNonBlank(reason, "reason");
            }
        }
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
